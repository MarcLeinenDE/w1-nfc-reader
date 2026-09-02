package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** User-controlled CSV export and versioned lossless *.qw1backup phone-migration format. */
final class DataPortability {
    static final int BACKUP_SCHEMA = 1;
    private static final int CSV_SCHEMA = 1;
    private static final int MAX_BACKUP_BYTES = 50 * 1024 * 1024;
    private static final String MANIFEST = "manifest.json";
    private static final String DATA = "data.json";

    static final class BackupPreview {
        final String createdUtc;
        final String activeMeterId;
        final int liveReadings;
        final int archivePeriods;
        final int replacementTransitions;
        final byte[] bytes;

        BackupPreview(String createdUtc, String activeMeterId, int liveReadings, int archivePeriods,
                      int replacementTransitions, byte[] bytes) {
            this.createdUtc = createdUtc;
            this.activeMeterId = activeMeterId;
            this.liveReadings = liveReadings;
            this.archivePeriods = archivePeriods;
            this.replacementTransitions = replacementTransitions;
            this.bytes = bytes;
        }
    }

    private DataPortability() { }

    static void writeCsv(Context context, OutputStream output) throws IOException {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        char delimiter = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? ';' : ',';
        NumberFormat numbers = NumberFormat.getNumberInstance(locale);
        numbers.setGroupingUsed(false);
        numbers.setMinimumFractionDigits(0);
        numbers.setMaximumFractionDigits(3);
        DateFormat deviceDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);

        List<ExportRow> rows = exportRows(context);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        appendCsv(csv, delimiter, "schema_version", "meter_id", "record_type", "primary_time",
                "device_time", "meter_time", "total_m3", "consumption_m3", "battery_percent",
                "status", "raw_status", "provenance", "partial");
        for (ExportRow row : rows) {
            appendCsv(csv, delimiter,
                    Integer.toString(CSV_SCHEMA), row.meterId, row.type, row.primaryTime,
                    row.deviceTimeMs <= 0 ? "" : deviceDate.format(new Date(row.deviceTimeMs)),
                    row.meterTime,
                    row.totalM3 == null ? "" : numbers.format(row.totalM3),
                    row.consumptionM3 == null ? "" : numbers.format(row.consumptionM3),
                    row.batteryPercent == null ? "" : Integer.toString(row.batteryPercent),
                    row.status, row.rawStatus, row.provenance, row.partial ? "true" : "false");
        }
        output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    static void writeBackup(Context context, OutputStream output) throws IOException, JSONException {
        JSONObject data = buildData(context);
        byte[] dataBytes = data.toString().getBytes(StandardCharsets.UTF_8);
        String createdUtc = utcNow();
        JSONObject manifest = new JSONObject();
        manifest.put("format", "QALCOSONIC_W1_BACKUP");
        manifest.put("schema_version", BACKUP_SCHEMA);
        manifest.put("created_utc", createdUtc);
        manifest.put("app_version", BuildConfig.VERSION_NAME);
        manifest.put("data_entry", DATA);
        manifest.put("data_sha256", sha256(dataBytes));
        manifest.put("live_readings", data.getJSONArray("live_readings").length());
        manifest.put("archive_periods", data.getJSONArray("archive_periods").length());
        manifest.put("archive_conflicts", data.getJSONArray("archive_conflicts").length());
        manifest.put("replacement_transitions",
                data.getJSONObject("meter_lifecycle").getJSONArray("transitions").length());

        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output), StandardCharsets.UTF_8);
        writeEntry(zip, MANIFEST, manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        writeEntry(zip, DATA, dataBytes);
        zip.finish();
        zip.flush();
    }

    static BackupPreview inspectBackup(InputStream input) throws IOException, JSONException {
        byte[] bytes = readLimited(input, MAX_BACKUP_BYTES);
        ParsedBackup parsed = parseBackup(bytes);
        JSONObject lifecycle = parsed.data.getJSONObject("meter_lifecycle");
        String active = lifecycle.isNull("active_meter_id") ? "—" : lifecycle.optString("active_meter_id", "—");
        return new BackupPreview(parsed.manifest.getString("created_utc"), active,
                parsed.data.getJSONArray("live_readings").length(),
                parsed.data.getJSONArray("archive_periods").length(),
                lifecycle.getJSONArray("transitions").length(), bytes);
    }

    static void restoreBackup(Context context, byte[] backupBytes) throws IOException, JSONException {
        ParsedBackup incoming = parseBackup(backupBytes);
        JSONObject before = buildData(context);
        try {
            mergeData(context, incoming.data);
        } catch (Exception error) {
            try {
                clearNormalData(context, false);
                replaceData(context, before);
            } catch (Exception rollback) {
                error.addSuppressed(rollback);
            }
            if (error instanceof IOException) throw (IOException) error;
            if (error instanceof JSONException) throw (JSONException) error;
            throw new IOException("BACKUP_IMPORT_FAILED", error);
        }
    }

    /** Clears meter-owned product data but leaves UI language/theme preferences untouched. */
    static void clearMeterData(Context context) {
        clearNormalData(context, false);
    }

    private static JSONObject buildData(Context context) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema_version", BACKUP_SCHEMA);
        MeterLifecycleStore lifecycle = new MeterLifecycleStore(context);
        root.put("meter_lifecycle", lifecycle.exportJson());
        root.put("live_readings", exportLiveReadings(context));
        root.put("archive_periods", exportArchivePeriods(context));
        root.put("archive_conflicts", exportArchiveConflicts(context));
        root.put("live_freshness", new LiveReadMetadataStore(context).exportJson());
        root.put("live_details", new LiveDetailMetadataStore(context).exportJson());
        root.put("history_sync", new HistorySyncMetadataStore(context).exportJson());
        JSONObject preferences = new JSONObject();
        preferences.put("theme", UiPreferences.getTheme(context));
        preferences.put("language", UiPreferences.getSelectedLanguageTag(context));
        root.put("preferences", preferences);
        return root;
    }

    private static JSONArray exportLiveReadings(Context context) throws JSONException {
        JSONArray out = new JSONArray();
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            for (String meterId : store.getMeterIds()) {
                for (MeterHistoryStore.Reading reading : store.getReadings(meterId, 0L)) {
                    JSONObject row = new JSONObject();
                    row.put("meter_id", reading.meterId);
                    row.put("read_at_ms", reading.readAtMs);
                    put(row, "meter_time", reading.meterTime);
                    row.put("total_m3", reading.totalM3);
                    put(row, "positive_m3", reading.positiveM3);
                    put(row, "negative_m3", reading.negativeM3);
                    put(row, "flow_m3h", reading.flowM3h);
                    put(row, "water_temperature_c", reading.waterTemperatureC);
                    put(row, "ambient_temperature_c", reading.ambientTemperatureC);
                    put(row, "battery_percent", reading.batteryPercent);
                    row.put("alarm_codes", reading.alarmCodes);
                    out.put(row);
                }
            }
        }
        return out;
    }

    private static JSONArray exportArchivePeriods(Context context) throws JSONException {
        JSONArray out = new JSONArray();
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = store.getReadableDatabase();
            try (Cursor c = db.query(ArchiveFamilyStore.TABLE_PERIODS, null, null, null,
                    null, null, "meter_id, archive_family, logger_timestamp")) {
                while (c.moveToNext()) out.put(cursorToJson(c));
            }
        }
        return out;
    }

    private static JSONArray exportArchiveConflicts(Context context) throws JSONException {
        JSONArray out = new JSONArray();
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = store.getReadableDatabase();
            String sql = "SELECT p.meter_id AS meter_id,p.archive_family AS archive_family,"
                    + "p.logger_timestamp AS logger_timestamp,c.* FROM " + ArchiveFamilyStore.TABLE_CONFLICTS
                    + " c JOIN " + ArchiveFamilyStore.TABLE_PERIODS
                    + " p ON p.id=c.archive_period_id ORDER BY c.observed_at_ms,c.id";
            try (Cursor c = db.rawQuery(sql, null)) {
                while (c.moveToNext()) {
                    JSONObject row = cursorToJson(c);
                    row.remove("id");
                    row.remove("archive_period_id");
                    out.put(row);
                }
            }
        }
        return out;
    }

    private static void mergeData(Context context, JSONObject data) throws JSONException, IOException {
        validateData(data);
        mergeLiveReadings(context, data.getJSONArray("live_readings"));
        mergeArchivePeriods(context, data.getJSONArray("archive_periods"));
        mergeArchiveConflicts(context, data.getJSONArray("archive_conflicts"));
        mergeLifecycle(context, data.getJSONObject("meter_lifecycle"));
        mergePortableMetadata(context, data);
    }

    private static void replaceData(Context context, JSONObject data) throws JSONException, IOException {
        validateData(data);
        clearNormalData(context, true);
        mergeLiveReadings(context, data.getJSONArray("live_readings"));
        mergeArchivePeriods(context, data.getJSONArray("archive_periods"));
        mergeArchiveConflicts(context, data.getJSONArray("archive_conflicts"));
        new MeterLifecycleStore(context).restoreJson(data.getJSONObject("meter_lifecycle"));
        new LiveReadMetadataStore(context).restoreJson(data.getJSONObject("live_freshness"));
        new LiveDetailMetadataStore(context).restoreJson(data.getJSONObject("live_details"));
        new HistorySyncMetadataStore(context).restoreJson(data.getJSONObject("history_sync"));
        restorePreferences(context, data.optJSONObject("preferences"));
    }

    private static void mergeLiveReadings(Context context, JSONArray rows) throws JSONException {
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String meter = requiredString(row, "meter_id");
                    long at = row.getLong("read_at_ms");
                    double total = row.getDouble("total_m3");
                    try (Cursor c = db.query(MeterHistoryStore.TABLE_READINGS, new String[]{"id"},
                            "meter_id=? AND read_at_ms=? AND ABS(total_m3-?) < 0.0000001",
                            new String[]{meter, Long.toString(at), Double.toString(total)},
                            null, null, null, "1")) {
                        if (c.moveToFirst()) continue;
                    }
                    ContentValues v = new ContentValues();
                    v.put("meter_id", meter); v.put("read_at_ms", at); v.put("total_m3", total);
                    put(v, "meter_time", nullableString(row, "meter_time"));
                    put(v, "positive_m3", nullableDouble(row, "positive_m3"));
                    put(v, "negative_m3", nullableDouble(row, "negative_m3"));
                    put(v, "flow_m3h", nullableDouble(row, "flow_m3h"));
                    put(v, "water_temp_c", nullableDouble(row, "water_temperature_c"));
                    put(v, "ambient_temp_c", nullableDouble(row, "ambient_temperature_c"));
                    put(v, "battery_percent", nullableInt(row, "battery_percent"));
                    v.put("alarm_codes", row.optString("alarm_codes", ""));
                    db.insertOrThrow(MeterHistoryStore.TABLE_READINGS, null, v);
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    private static void mergeArchivePeriods(Context context, JSONArray rows) throws JSONException {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < rows.length(); i++) mergeArchivePeriod(db, rows.getJSONObject(i));
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    private static void mergeArchivePeriod(SQLiteDatabase db, JSONObject row) throws JSONException {
        String meter = requiredString(row, "meter_id");
        String family = requiredString(row, "archive_family");
        String logger = requiredString(row, "logger_timestamp");
        try (Cursor c = db.query(ArchiveFamilyStore.TABLE_PERIODS, null,
                "meter_id=? AND archive_family=? AND logger_timestamp=?",
                new String[]{meter, family, logger}, null, null, null, "1")) {
            if (!c.moveToFirst()) {
                ContentValues insert = jsonToContentValues(row, periodColumns(), true);
                db.insertOrThrow(ArchiveFamilyStore.TABLE_PERIODS, null, insert);
                return;
            }
            long id = c.getLong(c.getColumnIndexOrThrow("id"));
            String existingFingerprint = c.getString(c.getColumnIndexOrThrow("content_fingerprint"));
            String importedFingerprint = requiredString(row, "content_fingerprint");
            if (existingFingerprint.equals(importedFingerprint)) {
                ContentValues update = new ContentValues();
                update.put("observation_count", Math.max(c.getInt(c.getColumnIndexOrThrow("observation_count")), row.optInt("observation_count", 1)));
                update.put("identical_content_confirmations", Math.max(c.getInt(c.getColumnIndexOrThrow("identical_content_confirmations")), row.optInt("identical_content_confirmations", 0)));
                update.put("structural_confirmations", Math.max(c.getInt(c.getColumnIndexOrThrow("structural_confirmations")), row.optInt("structural_confirmations", 0)));
                update.put("provenance_confirmations", Math.max(c.getInt(c.getColumnIndexOrThrow("provenance_confirmations")), row.optInt("provenance_confirmations", 0)));
                update.put("revision_count", Math.max(c.getInt(c.getColumnIndexOrThrow("revision_count")), row.optInt("revision_count", 0)));
                update.put("conflict_flags", c.getInt(c.getColumnIndexOrThrow("conflict_flags")) | row.optInt("conflict_flags", 0));
                long existingRetrieved = c.getLong(c.getColumnIndexOrThrow("retrieved_at_ms"));
                long importedRetrieved = row.getLong("retrieved_at_ms");
                if (importedRetrieved > existingRetrieved) {
                    update.put("retrieved_at_ms", importedRetrieved);
                    update.put("retrieved_at_utc", requiredString(row, "retrieved_at_utc"));
                    update.put("last_content_fingerprint", importedFingerprint);
                    update.put("last_structural_fingerprint", requiredString(row, "last_structural_fingerprint"));
                    update.put("last_source", requiredString(row, "last_source"));
                    update.put("last_validation", requiredString(row, "last_validation"));
                }
                long existingFirst = c.getLong(c.getColumnIndexOrThrow("first_retrieved_at_ms"));
                long importedFirst = row.getLong("first_retrieved_at_ms");
                if (importedFirst < existingFirst) {
                    update.put("first_retrieved_at_ms", importedFirst);
                    update.put("first_retrieved_at_utc", requiredString(row, "first_retrieved_at_utc"));
                }
                db.update(ArchiveFamilyStore.TABLE_PERIODS, update, "id=?", new String[]{Long.toString(id)});
            } else {
                int flags = c.getInt(c.getColumnIndexOrThrow("conflict_flags")) | ArchiveFamilyStore.CONFLICT_CONTENT;
                ContentValues update = new ContentValues();
                update.put("conflict_flags", flags);
                update.put("revision_count", Math.max(c.getInt(c.getColumnIndexOrThrow("revision_count")) + 1,
                        row.optInt("revision_count", 0)));
                db.update(ArchiveFamilyStore.TABLE_PERIODS, update, "id=?", new String[]{Long.toString(id)});
                insertConflictFromPeriod(db, id, row);
            }
        }
    }

    private static void mergeArchiveConflicts(Context context, JSONArray rows) throws JSONException {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    long periodId = findPeriodId(db, requiredString(row, "meter_id"),
                            requiredString(row, "archive_family"), requiredString(row, "logger_timestamp"));
                    if (periodId <= 0L) throw new JSONException("conflict without archive period");
                    long observed = row.getLong("observed_at_ms");
                    String fingerprint = requiredString(row, "content_fingerprint");
                    try (Cursor c = db.query(ArchiveFamilyStore.TABLE_CONFLICTS, new String[]{"id"},
                            "archive_period_id=? AND observed_at_ms=? AND content_fingerprint=?",
                            new String[]{Long.toString(periodId), Long.toString(observed), fingerprint},
                            null, null, null, "1")) {
                        if (c.moveToFirst()) continue;
                    }
                    ContentValues v = jsonToContentValues(row, conflictColumns(), true);
                    v.put("archive_period_id", periodId);
                    db.insertOrThrow(ArchiveFamilyStore.TABLE_CONFLICTS, null, v);
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    private static void mergeLifecycle(Context context, JSONObject imported) throws JSONException {
        MeterLifecycleStore store = new MeterLifecycleStore(context);
        String currentActive = store.activeMeterId();
        String importedActive = imported.isNull("active_meter_id") ? null : imported.optString("active_meter_id", null);
        if (currentActive == null) {
            store.restoreJson(imported);
            return;
        }
        if (importedActive == null || currentActive.equals(importedActive)) return;
        // Do not silently replace a user's current active meter during a merge import. Historical
        // readings/archive data were already merged, but the current active identity remains local.
    }

    private static void mergePortableMetadata(Context context, JSONObject data) throws JSONException {
        // If the local installation has no current lifecycle, the imported metadata can safely
        // become current. Otherwise keep current freshness/settings and only merge durable history.
        MeterLifecycleStore lifecycle = new MeterLifecycleStore(context);
        String importedActive = data.getJSONObject("meter_lifecycle").isNull("active_meter_id")
                ? null : data.getJSONObject("meter_lifecycle").optString("active_meter_id", null);
        if (importedActive != null && importedActive.equals(lifecycle.activeMeterId())) {
            new LiveReadMetadataStore(context).restoreJson(data.getJSONObject("live_freshness"));
            new LiveDetailMetadataStore(context).restoreJson(data.getJSONObject("live_details"));
            new HistorySyncMetadataStore(context).restoreJson(data.getJSONObject("history_sync"));
            restorePreferences(context, data.optJSONObject("preferences"));
        }
    }

    private static void restorePreferences(Context context, JSONObject preferences) {
        if (preferences == null) return;
        String theme = preferences.optString("theme", UiPreferences.THEME_SYSTEM);
        String language = preferences.optString("language", UiPreferences.LANGUAGE_SYSTEM);
        if (!(UiPreferences.THEME_SYSTEM.equals(theme) || UiPreferences.THEME_LIGHT.equals(theme)
                || UiPreferences.THEME_DARK.equals(theme))) theme = UiPreferences.THEME_SYSTEM;
        if (!(UiPreferences.LANGUAGE_SYSTEM.equals(language)
                || UiPreferences.getSupportedLocaleTags(context).contains(language))) {
            language = UiPreferences.LANGUAGE_SYSTEM;
        }
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE).edit()
                .putString("theme", theme).putString("language", language).apply();
    }

    private static void clearNormalData(Context context, boolean includePreferences) {
        try (MeterHistoryStore live = new MeterHistoryStore(context)) { live.deleteAll(); }
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = archive.getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(ArchiveFamilyStore.TABLE_CONFLICTS, null, null);
                db.delete(ArchiveFamilyStore.TABLE_PERIODS, null, null);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        new MeterLifecycleStore(context).clear();
        new LiveReadMetadataStore(context).clear();
        new LiveDetailMetadataStore(context).clear();
        new HistorySyncMetadataStore(context).clear();
        if (includePreferences) context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static ParsedBackup parseBackup(byte[] bytes) throws IOException, JSONException {
        Map<String, byte[]> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8);
        ZipEntry entry;
        int total = 0;
        byte[] buffer = new byte[8192];
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (!(MANIFEST.equals(name) || DATA.equals(name))) continue;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int n;
            while ((n = zip.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BACKUP_BYTES) throw new IOException("BACKUP_TOO_LARGE");
                out.write(buffer, 0, n);
            }
            if (entries.put(name, out.toByteArray()) != null) throw new IOException("DUPLICATE_BACKUP_ENTRY");
        }
        if (!entries.containsKey(MANIFEST) || !entries.containsKey(DATA)) throw new IOException("BACKUP_ENTRY_MISSING");
        JSONObject manifest = new JSONObject(new String(entries.get(MANIFEST), StandardCharsets.UTF_8));
        if (!"QALCOSONIC_W1_BACKUP".equals(manifest.optString("format"))) throw new IOException("BACKUP_FORMAT_UNSUPPORTED");
        if (manifest.optInt("schema_version", -1) != BACKUP_SCHEMA) throw new IOException("BACKUP_VERSION_UNSUPPORTED");
        byte[] dataBytes = entries.get(DATA);
        if (!sha256(dataBytes).equalsIgnoreCase(manifest.optString("data_sha256"))) throw new IOException("BACKUP_CHECKSUM_MISMATCH");
        JSONObject data = new JSONObject(new String(dataBytes, StandardCharsets.UTF_8));
        validateData(data);
        return new ParsedBackup(manifest, data);
    }

    private static void validateData(JSONObject data) throws JSONException {
        if (data.optInt("schema_version", -1) != BACKUP_SCHEMA) throw new JSONException("unsupported data schema");
        data.getJSONObject("meter_lifecycle");
        data.getJSONArray("live_readings");
        data.getJSONArray("archive_periods");
        data.getJSONArray("archive_conflicts");
        data.getJSONObject("live_freshness");
        data.getJSONObject("live_details");
        data.getJSONObject("history_sync");
    }

    private static List<ExportRow> exportRows(Context context) {
        List<ExportRow> out = new ArrayList<>();
        List<WaterUsageAnalytics.Point> points = new ArrayList<>();
        Map<String, ExportRow> byIdentity = new HashMap<>();
        try (MeterHistoryStore liveStore = new MeterHistoryStore(context);
             ArchiveFamilyStore archiveStore = new ArchiveFamilyStore(context)) {
            Set<String> meters = new LinkedHashSet<>(liveStore.getMeterIds());
            meters.addAll(new MeterLifecycleStore(context).chainMeterIds());
            SQLiteDatabase archiveDb = archiveStore.getReadableDatabase();
            try (Cursor c = archiveDb.rawQuery("SELECT DISTINCT meter_id FROM " + ArchiveFamilyStore.TABLE_PERIODS, null)) {
                while (c.moveToNext()) meters.add(c.getString(0));
            }
            for (String meter : meters) {
                for (MeterHistoryStore.Reading reading : liveStore.getReadings(meter, 0L)) {
                    String identity = "LIVE|" + reading.id;
                    WaterUsageAnalytics.Point p = new WaterUsageAnalytics.Point(identity, meter,
                            localMinute(reading.readAtMs), reading.readAtMs,
                            HistorySemanticTimeline.Granularity.LIVE, reading.totalM3);
                    points.add(p);
                    ExportRow row = new ExportRow(meter, "LIVE", localMinute(reading.readAtMs),
                            reading.readAtMs, reading.meterTime, reading.totalM3, reading.batteryPercent,
                            MeterStatusPresentation.localizedAlarmCodes(context, reading.alarmCodes), "",
                            context.getString(R.string.m3_provenance_live), false);
                    byIdentity.put(identity, row);
                    out.add(row);
                }
                for (ArchiveFamilyStore.StoredPeriod period : archiveStore.getPeriods(meter, null)) {
                    Double total = parseMeasurement(period.totalVolume);
                    long sort = parseMeterLocal(period.loggerTimestamp);
                    if (total == null || sort <= 0L) continue;
                    String identity = period.family.name() + "|" + meter + "|" + period.loggerTimestamp;
                    WaterUsageAnalytics.Point p = new WaterUsageAnalytics.Point(identity, meter,
                            period.loggerTimestamp, sort, granularity(period.family), total);
                    points.add(p);
                    MeterStatusPresentation.Historical status = MeterStatusPresentation.historical(period.errorFlags);
                    ExportRow row = new ExportRow(meter, period.family.name(), period.loggerTimestamp,
                            period.retrievedAtMs, "", total, parseInteger(period.batteryPercent),
                            status.hasAnyStatus() ? status.summary(context) : "", status.hasAnyStatus() ? status.raw : "",
                            context.getString(R.string.m3_provenance_archive), false);
                    byIdentity.put(identity, row);
                    out.add(row);
                }
            }
        }
        for (WaterUsageAnalytics.HistoryDelta delta : WaterUsageAnalytics.historyNewestFirst(points)) {
            ExportRow row = byIdentity.get(delta.point.identity);
            if (row != null) row.consumptionM3 = delta.consumptionSincePreviousM3;
        }
        for (MeterLifecycleStore.Transition transition : new MeterLifecycleStore(context).transitions()) {
            out.add(new ExportRow(transition.successorMeterId, "METER_REPLACEMENT",
                    localMinute(transition.confirmedAtMs), transition.confirmedAtMs, "", null, null,
                    context.getString(R.string.m3_meter_replacement_event), "",
                    transition.predecessorMeterId + " -> " + transition.successorMeterId, true));
        }
        out.sort((a,b) -> Long.compare(a.deviceTimeMs, b.deviceTimeMs));
        return out;
    }

    private static void appendCsv(StringBuilder out, char delimiter, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(delimiter);
            String value = values[i] == null ? "" : values[i];
            out.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }

    private static JSONObject cursorToJson(Cursor c) throws JSONException {
        JSONObject row = new JSONObject();
        for (String column : c.getColumnNames()) {
            int index = c.getColumnIndexOrThrow(column);
            if (c.isNull(index)) row.put(column, JSONObject.NULL);
            else {
                switch (c.getType(index)) {
                    case Cursor.FIELD_TYPE_INTEGER: row.put(column, c.getLong(index)); break;
                    case Cursor.FIELD_TYPE_FLOAT: row.put(column, c.getDouble(index)); break;
                    default: row.put(column, c.getString(index)); break;
                }
            }
        }
        return row;
    }

    private static ContentValues jsonToContentValues(JSONObject row, String[] columns, boolean requireNonNullColumns) throws JSONException {
        ContentValues v = new ContentValues();
        for (String column : columns) {
            if (!row.has(column) || row.isNull(column)) {
                if (requireNonNullColumns && isRequiredArchiveColumn(column)) throw new JSONException("missing required " + column);
                v.putNull(column);
                continue;
            }
            Object value = row.get(column);
            if (value instanceof Integer) v.put(column, (Integer) value);
            else if (value instanceof Long) v.put(column, (Long) value);
            else if (value instanceof Number) v.put(column, ((Number) value).doubleValue());
            else v.put(column, value.toString());
        }
        return v;
    }

    private static boolean isRequiredArchiveColumn(String c) {
        return "meter_id".equals(c) || "archive_family".equals(c) || "logger_timestamp".equals(c)
                || "logger_time_basis".equals(c) || "retrieved_at_utc".equals(c) || "retrieved_at_ms".equals(c)
                || "first_retrieved_at_utc".equals(c) || "first_retrieved_at_ms".equals(c)
                || "source".equals(c) || "validation".equals(c) || "structural_fingerprint".equals(c)
                || "content_fingerprint".equals(c) || "observation_count".equals(c)
                || "identical_content_confirmations".equals(c) || "structural_confirmations".equals(c)
                || "provenance_confirmations".equals(c) || "revision_count".equals(c) || "conflict_flags".equals(c)
                || "last_content_fingerprint".equals(c) || "last_structural_fingerprint".equals(c)
                || "last_source".equals(c) || "last_validation".equals(c) || "extra_values".equals(c)
                || "observed_at_utc".equals(c) || "observed_at_ms".equals(c)
                || "content_changed".equals(c) || "structure_changed".equals(c) || "provenance_changed".equals(c);
    }

    private static String[] periodColumns() {
        return new String[]{"meter_id","archive_family","logger_timestamp","logger_time_basis","retrieved_at_utc",
                "retrieved_at_ms","first_retrieved_at_utc","first_retrieved_at_ms","source","validation",
                "structural_fingerprint","content_fingerprint","observation_count","identical_content_confirmations",
                "structural_confirmations","provenance_confirmations","revision_count","conflict_flags",
                "last_content_fingerprint","last_structural_fingerprint","last_source","last_validation","total_volume",
                "positive_volume","reverse_volume","tariff1_volume","max_flow","max_flow_at","min_flow","min_flow_at",
                "flow","max_temperature","max_temperature_at","min_temperature","min_temperature_at","temperature",
                "external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values"};
    }

    private static String[] conflictColumns() {
        return new String[]{"observed_at_utc","observed_at_ms","source","validation","structural_fingerprint",
                "content_fingerprint","content_changed","structure_changed","provenance_changed","total_volume",
                "positive_volume","reverse_volume","tariff1_volume","max_flow","max_flow_at","min_flow","min_flow_at",
                "flow","max_temperature","max_temperature_at","min_temperature","min_temperature_at","temperature",
                "external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values"};
    }

    private static long findPeriodId(SQLiteDatabase db, String meter, String family, String logger) {
        try (Cursor c = db.query(ArchiveFamilyStore.TABLE_PERIODS, new String[]{"id"},
                "meter_id=? AND archive_family=? AND logger_timestamp=?", new String[]{meter,family,logger},
                null,null,null,"1")) { return c.moveToFirst() ? c.getLong(0) : -1L; }
    }

    private static void insertConflictFromPeriod(SQLiteDatabase db, long periodId, JSONObject row) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("archive_period_id", periodId);
        v.put("observed_at_utc", requiredString(row, "retrieved_at_utc"));
        v.put("observed_at_ms", row.getLong("retrieved_at_ms"));
        v.put("source", requiredString(row, "source"));
        v.put("validation", requiredString(row, "validation"));
        v.put("structural_fingerprint", requiredString(row, "structural_fingerprint"));
        v.put("content_fingerprint", requiredString(row, "content_fingerprint"));
        v.put("content_changed", 1); v.put("structure_changed", 0); v.put("provenance_changed", 0);
        copyNullable(row, v, "total_volume","positive_volume","reverse_volume","tariff1_volume","max_flow","max_flow_at",
                "min_flow","min_flow_at","flow","max_temperature","max_temperature_at","min_temperature","min_temperature_at",
                "temperature","external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values");
        db.insertOrThrow(ArchiveFamilyStore.TABLE_CONFLICTS, null, v);
    }

    private static void copyNullable(JSONObject source, ContentValues target, String... keys) throws JSONException {
        for (String key : keys) {
            if (!source.has(key) || source.isNull(key)) target.putNull(key);
            else target.put(key, source.get(key).toString());
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > limit) throw new IOException("BACKUP_TOO_LARGE");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.US, "%02x", b & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String utcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static long parseMeterLocal(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        f.setLenient(false);
        try { Date date = f.parse(value); return date == null ? 0L : date.getTime(); }
        catch (ParseException e) { return 0L; }
    }

    private static String localMinute(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }

    private static Double parseMeasurement(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replace(',', '.').replaceAll("[^0-9+\\-.]", "");
        if (cleaned.isEmpty()) return null;
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInteger(String value) {
        Double d = parseMeasurement(value);
        return d == null ? null : (int)Math.round(d);
    }

    private static HistorySemanticTimeline.Granularity granularity(ArchiveFamilyPeriod.Family family) {
        switch (family) {
            case HOUR: return HistorySemanticTimeline.Granularity.HOUR;
            case DAY: return HistorySemanticTimeline.Granularity.DAY;
            case YEAR: return HistorySemanticTimeline.Granularity.YEAR;
            case MONTH:
            default: return HistorySemanticTimeline.Granularity.MONTH;
        }
    }

    private static void put(JSONObject object, String key, Object value) throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }
    private static void put(ContentValues values, String key, Object value) {
        if (value == null) values.putNull(key);
        else if (value instanceof Double) values.put(key, (Double)value);
        else if (value instanceof Integer) values.put(key, (Integer)value);
        else if (value instanceof Long) values.put(key, (Long)value);
        else values.put(key, value.toString());
    }
    private static String requiredString(JSONObject row, String key) throws JSONException {
        String value = row.getString(key);
        if (value.trim().isEmpty()) throw new JSONException("empty " + key);
        return value;
    }
    private static String nullableString(JSONObject row, String key) { return !row.has(key) || row.isNull(key) ? null : row.optString(key, null); }
    private static Double nullableDouble(JSONObject row, String key) { return !row.has(key) || row.isNull(key) ? null : row.optDouble(key); }
    private static Integer nullableInt(JSONObject row, String key) { return !row.has(key) || row.isNull(key) ? null : row.optInt(key); }

    private static final class ParsedBackup {
        final JSONObject manifest; final JSONObject data;
        ParsedBackup(JSONObject manifest, JSONObject data) { this.manifest=manifest; this.data=data; }
    }

    private static final class ExportRow {
        final String meterId, type, primaryTime, meterTime, status, rawStatus, provenance;
        final long deviceTimeMs;
        final Double totalM3;
        Double consumptionM3;
        final Integer batteryPercent;
        final boolean partial;
        ExportRow(String meterId, String type, String primaryTime, long deviceTimeMs, String meterTime,
                  Double totalM3, Integer batteryPercent, String status, String rawStatus,
                  String provenance, boolean partial) {
            this.meterId=meterId; this.type=type; this.primaryTime=primaryTime; this.deviceTimeMs=deviceTimeMs;
            this.meterTime=meterTime; this.totalM3=totalM3; this.batteryPercent=batteryPercent;
            this.status=status; this.rawStatus=rawStatus; this.provenance=provenance; this.partial=partial;
        }
    }
}
