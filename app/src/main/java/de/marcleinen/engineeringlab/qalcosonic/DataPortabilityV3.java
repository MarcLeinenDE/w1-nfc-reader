package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * v2.1 lossless backup facade.
 *
 * <p>The proven v2 core portability implementation remains the compatibility engine for all
 * pre-v2.1 product data. Schema 3 wraps that payload with the durable per-meter time model and an
 * occurrence-safe archive envelope. The envelope is staged before the archive DB migration so
 * future repeated raw wall-clock periods and typed time evidence cannot be collapsed by the old
 * schema-2 identity during backup/restore evolution.</p>
 */
final class DataPortabilityV3 {
    static final int BACKUP_SCHEMA = 3;
    static final int LEGACY_BACKUP_SCHEMA = 2;

    private static final int MAX_BACKUP_BYTES = 50 * 1024 * 1024;
    private static final String FORMAT = "QALCOSONIC_W1_BACKUP";
    private static final String MANIFEST = "manifest.json";
    private static final String DATA = "data.json";
    private static final String TIME_MODEL = "meter_time_model";
    private static final String ARCHIVE_PERIODS_V2 = "archive_periods_v2";
    private static final String ARCHIVE_CONFLICTS_V2 = "archive_conflicts_v2";

    private DataPortabilityV3() { }

    static void writeBackup(Context context, OutputStream output) throws IOException, JSONException {
        byte[] legacyBytes = writeLegacyBackup(context);
        ParsedBackup legacy = parse(legacyBytes);
        if (legacy.schemaVersion != LEGACY_BACKUP_SCHEMA) {
            throw new IOException("LEGACY_BACKUP_SCHEMA_UNEXPECTED");
        }

        JSONObject data = cloneJson(legacy.data);
        data.put("schema_version", BACKUP_SCHEMA);
        JSONObject preferences = data.getJSONObject("preferences");
        preferences.put("time_basis", UiPreferences.getTimeBasis(context).name());
        JSONObject timeModel;
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            timeModel = timeStore.exportJson();
        } finally {
            timeStore.close();
        }
        data.put(TIME_MODEL, timeModel);

        JSONArray archiveV2 = buildArchivePeriodsV2(data.getJSONArray("archive_periods"));
        JSONArray conflictsV2 = buildArchiveConflictsV2(
                data.getJSONArray("archive_conflicts"), archiveV2);
        data.put(ARCHIVE_PERIODS_V2, archiveV2);
        data.put(ARCHIVE_CONFLICTS_V2, conflictsV2);
        validateArchiveEnvelope(data);

        byte[] dataBytes = data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject manifest = cloneJson(legacy.manifest);
        manifest.put("schema_version", BACKUP_SCHEMA);
        manifest.put("data_sha256", sha256(dataBytes));
        manifest.put("meter_time_profiles", timeModel.getJSONArray("profiles").length());
        manifest.put("meter_time_anchors", timeModel.getJSONArray("anchors").length());
        manifest.put("archive_periods_v2", archiveV2.length());
        manifest.put("archive_conflicts_v2", conflictsV2.length());

        writeZip(output, manifest, dataBytes);
    }

    static DataPortability.BackupPreview inspectBackup(InputStream input)
            throws IOException, JSONException {
        byte[] bytes = readLimited(input, MAX_BACKUP_BYTES);
        ParsedBackup parsed = parse(bytes);
        if (parsed.schemaVersion == LEGACY_BACKUP_SCHEMA) {
            return DataPortability.inspectBackup(new ByteArrayInputStream(bytes));
        }
        if (parsed.schemaVersion != BACKUP_SCHEMA) throw new IOException("BACKUP_VERSION_UNSUPPORTED");

        byte[] legacyCore = downgradeToLegacyCore(parsed);
        DataPortability.BackupPreview core = DataPortability.inspectBackup(
                new ByteArrayInputStream(legacyCore));
        return new DataPortability.BackupPreview(
                core.createdUtc,
                core.activeMeterId,
                core.liveReadings,
                parsed.data.getJSONArray(ARCHIVE_PERIODS_V2).length(),
                core.replacementTransitions,
                countFamily(parsed.data.getJSONArray(ARCHIVE_PERIODS_V2), ArchiveFamilyPeriod.Family.HOUR),
                countFamily(parsed.data.getJSONArray(ARCHIVE_PERIODS_V2), ArchiveFamilyPeriod.Family.DAY),
                countFamily(parsed.data.getJSONArray(ARCHIVE_PERIODS_V2), ArchiveFamilyPeriod.Family.MONTH),
                core.hourBaselineState,
                core.dayBaselineState,
                core.monthBaselineState,
                bytes);
    }

    static void restoreBackup(Context context, byte[] backupBytes) throws IOException, JSONException {
        ParsedBackup incoming = parse(backupBytes);
        if (incoming.schemaVersion == LEGACY_BACKUP_SCHEMA) {
            // Legacy data contains no trustworthy per-meter ZoneId/SU/anchor evidence and no v2.1
            // time-basis preference. Restore the proven core only and deliberately preserve the
            // receiving installation's v2.1-only state.
            DataPortability.restoreBackup(context, backupBytes);
            return;
        }
        if (incoming.schemaVersion != BACKUP_SCHEMA) throw new IOException("BACKUP_VERSION_UNSUPPORTED");

        byte[] coreBefore = writeLegacyBackup(context);
        AppTimeBasis timeBasisBefore = UiPreferences.getTimeBasis(context);
        JSONObject timeBefore;
        MeterTimeModelStore store = new MeterTimeModelStore(context);
        try {
            timeBefore = store.exportJson();
        } finally {
            store.close();
        }

        try {
            // Until meter_archive.db v2 lands, the proven schema-2 core remains the physical archive
            // restore path. The occurrence-safe envelope is already validated and retained in the
            // backup format so the DB migration can switch restore to it without another format bump.
            DataPortability.restoreBackup(context, downgradeToLegacyCore(incoming));
            MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
            try {
                timeStore.mergeJson(incoming.data.getJSONObject(TIME_MODEL));
            } finally {
                timeStore.close();
            }
            UiPreferences.setTimeBasis(context, portableTimeBasis(incoming.data));
        } catch (Exception error) {
            try {
                DataPortability.clearMeterData(context);
                MeterTimeModelStore rollbackStore = new MeterTimeModelStore(context);
                try {
                    rollbackStore.clear();
                } finally {
                    rollbackStore.close();
                }
                DataPortability.restoreBackup(context, coreBefore);
                MeterTimeModelStore restoreStore = new MeterTimeModelStore(context);
                try {
                    restoreStore.restoreJson(timeBefore);
                } finally {
                    restoreStore.close();
                }
                UiPreferences.setTimeBasis(context, timeBasisBefore);
            } catch (Exception rollback) {
                error.addSuppressed(rollback);
            }
            if (error instanceof IOException) throw (IOException) error;
            if (error instanceof JSONException) throw (JSONException) error;
            throw new IOException("BACKUP_IMPORT_FAILED", error);
        }
    }

    /** Full product-data clear for v2.1 callers. UI language/theme/time basis remain untouched. */
    static void clearMeterData(Context context) {
        DataPortability.clearMeterData(context);
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            timeStore.clear();
        } finally {
            timeStore.close();
        }
    }

    private static byte[] writeLegacyBackup(Context context) throws IOException, JSONException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        return out.toByteArray();
    }

    private static byte[] downgradeToLegacyCore(ParsedBackup parsed)
            throws IOException, JSONException {
        if (parsed.schemaVersion != BACKUP_SCHEMA) {
            throw new IOException("SCHEMA3_REQUIRED_FOR_DOWNGRADE");
        }
        JSONObject data = cloneJson(parsed.data);
        data.remove(TIME_MODEL);
        data.remove(ARCHIVE_PERIODS_V2);
        data.remove(ARCHIVE_CONFLICTS_V2);
        data.put("schema_version", LEGACY_BACKUP_SCHEMA);
        JSONObject preferences = data.optJSONObject("preferences");
        if (preferences != null) preferences.remove("time_basis");
        byte[] dataBytes = data.toString().getBytes(StandardCharsets.UTF_8);

        JSONObject manifest = cloneJson(parsed.manifest);
        manifest.put("schema_version", LEGACY_BACKUP_SCHEMA);
        manifest.remove("meter_time_profiles");
        manifest.remove("meter_time_anchors");
        manifest.remove("archive_periods_v2");
        manifest.remove("archive_conflicts_v2");
        manifest.put("data_sha256", sha256(dataBytes));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeZip(out, manifest, dataBytes);
        return out.toByteArray();
    }

    private static ParsedBackup parse(byte[] bytes) throws IOException, JSONException {
        Map<String, byte[]> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8);
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
            if (entries.put(name, out.toByteArray()) != null) {
                throw new IOException("DUPLICATE_BACKUP_ENTRY");
            }
        }
        if (!entries.containsKey(MANIFEST) || !entries.containsKey(DATA)) {
            throw new IOException("BACKUP_ENTRY_MISSING");
        }

        JSONObject manifest = new JSONObject(
                new String(entries.get(MANIFEST), StandardCharsets.UTF_8));
        if (!FORMAT.equals(manifest.optString("format"))) throw new IOException("BACKUP_FORMAT_UNSUPPORTED");
        int schema = manifest.optInt("schema_version", -1);
        if (schema != LEGACY_BACKUP_SCHEMA && schema != BACKUP_SCHEMA) {
            throw new IOException("BACKUP_VERSION_UNSUPPORTED");
        }
        if (!DATA.equals(manifest.optString("data_entry"))) throw new IOException("BACKUP_DATA_ENTRY_UNSUPPORTED");

        byte[] dataBytes = entries.get(DATA);
        if (!sha256(dataBytes).equalsIgnoreCase(manifest.optString("data_sha256"))) {
            throw new IOException("BACKUP_CHECKSUM_MISMATCH");
        }
        JSONObject data = new JSONObject(new String(dataBytes, StandardCharsets.UTF_8));
        if (data.optInt("schema_version", -1) != schema) {
            throw new IOException("BACKUP_SCHEMA_MISMATCH");
        }
        if (schema == BACKUP_SCHEMA) {
            validateTimeModel(data.getJSONObject(TIME_MODEL));
            portableTimeBasis(data);
            // Early schema-3 development backups predate the occurrence-safe archive envelope.
            // Upgrade them in-memory from their still-present legacy archive arrays.
            if (!data.has(ARCHIVE_PERIODS_V2)) {
                data.put(ARCHIVE_PERIODS_V2,
                        buildArchivePeriodsV2(data.getJSONArray("archive_periods")));
            }
            if (!data.has(ARCHIVE_CONFLICTS_V2)) {
                data.put(ARCHIVE_CONFLICTS_V2,
                        buildArchiveConflictsV2(
                                data.getJSONArray("archive_conflicts"),
                                data.getJSONArray(ARCHIVE_PERIODS_V2)));
            }
            validateArchiveEnvelope(data);
        }
        return new ParsedBackup(schema, manifest, data);
    }

    private static JSONArray buildArchivePeriodsV2(JSONArray legacyPeriods) throws JSONException {
        JSONArray out = new JSONArray();
        for (int i = 0; i < legacyPeriods.length(); i++) {
            JSONObject row = cloneJson(legacyPeriods.getJSONObject(i));
            String onTimeDisplay = nullableString(row, "on_time");
            Long onTimeSeconds = ArchiveOccurrenceKey.parseDurationSeconds(onTimeDisplay);
            row.put("occurrence_key", ArchiveOccurrenceKey.fromEvidence(onTimeSeconds, null));
            if (onTimeSeconds == null) row.put("on_time_seconds", JSONObject.NULL);
            else row.put("on_time_seconds", onTimeSeconds.longValue());
            row.put("raw_type_f_hex", JSONObject.NULL);
            row.put("type_f_iv", JSONObject.NULL);
            row.put("type_f_su", JSONObject.NULL);
            out.put(row);
        }
        return out;
    }

    private static JSONArray buildArchiveConflictsV2(
            JSONArray legacyConflicts,
            JSONArray periodsV2) throws JSONException {
        Map<String, String> occurrenceByLegacyKey = new HashMap<>();
        for (int i = 0; i < periodsV2.length(); i++) {
            JSONObject row = periodsV2.getJSONObject(i);
            occurrenceByLegacyKey.put(legacyArchiveKey(row), requiredString(row, "occurrence_key"));
        }

        JSONArray out = new JSONArray();
        for (int i = 0; i < legacyConflicts.length(); i++) {
            JSONObject row = cloneJson(legacyConflicts.getJSONObject(i));
            String occurrence = occurrenceByLegacyKey.get(legacyArchiveKey(row));
            if (occurrence == null) throw new JSONException("archive conflict without canonical occurrence");
            row.put("occurrence_key", occurrence);
            out.put(row);
        }
        return out;
    }

    private static void validateArchiveEnvelope(JSONObject data) throws JSONException {
        JSONArray periods = data.getJSONArray(ARCHIVE_PERIODS_V2);
        JSONArray conflicts = data.getJSONArray(ARCHIVE_CONFLICTS_V2);
        Set<String> nativeKeys = new HashSet<>();
        for (int i = 0; i < periods.length(); i++) {
            JSONObject row = periods.getJSONObject(i);
            requiredString(row, "meter_id");
            String family = requiredString(row, "archive_family");
            try {
                ArchiveFamilyPeriod.Family.valueOf(family);
            } catch (IllegalArgumentException error) {
                throw new JSONException("invalid archive family " + family);
            }
            requiredString(row, "logger_timestamp");
            String occurrence = requiredString(row, "occurrence_key");
            validateOccurrence(row, occurrence);
            String key = archiveV2Key(row);
            if (!nativeKeys.add(key)) throw new JSONException("duplicate archive occurrence " + key);
        }
        for (int i = 0; i < conflicts.length(); i++) {
            JSONObject row = conflicts.getJSONObject(i);
            String key = archiveV2Key(row);
            if (!nativeKeys.contains(key)) {
                throw new JSONException("archive conflict without period occurrence " + key);
            }
        }
    }

    private static void validateOccurrence(JSONObject row, String occurrence) throws JSONException {
        if (ArchiveOccurrenceKey.LEGACY.equals(occurrence)) return;
        if (occurrence.startsWith(ArchiveOccurrenceKey.ON_TIME_PREFIX)) {
            long keySeconds;
            try {
                keySeconds = Long.parseLong(occurrence.substring(ArchiveOccurrenceKey.ON_TIME_PREFIX.length()));
            } catch (RuntimeException error) {
                throw new JSONException("invalid ON_TIME occurrence " + occurrence);
            }
            if (keySeconds < 0L || !row.has("on_time_seconds") || row.isNull("on_time_seconds")
                    || row.getLong("on_time_seconds") != keySeconds) {
                throw new JSONException("ON_TIME occurrence mismatch " + occurrence);
            }
            return;
        }
        if (occurrence.startsWith(ArchiveOccurrenceKey.TYPE_F_PREFIX)) {
            String keyHex = occurrence.substring(ArchiveOccurrenceKey.TYPE_F_PREFIX.length());
            String rawHex = nullableString(row, "raw_type_f_hex");
            String normalized = ArchiveOccurrenceKey.normalizeTypeFHex(rawHex);
            if (normalized == null || !normalized.equals(keyHex)) {
                throw new JSONException("Type-F occurrence mismatch " + occurrence);
            }
            return;
        }
        throw new JSONException("invalid occurrence key " + occurrence);
    }

    private static String archiveV2Key(JSONObject row) throws JSONException {
        return requiredString(row, "meter_id") + "|"
                + requiredString(row, "archive_family") + "|"
                + requiredString(row, "logger_timestamp") + "|"
                + requiredString(row, "occurrence_key");
    }

    private static String legacyArchiveKey(JSONObject row) throws JSONException {
        return requiredString(row, "meter_id") + "|"
                + requiredString(row, "archive_family") + "|"
                + requiredString(row, "logger_timestamp");
    }

    private static int countFamily(JSONArray rows, ArchiveFamilyPeriod.Family family)
            throws JSONException {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            if (family.name().equals(requiredString(rows.getJSONObject(i), "archive_family"))) count++;
        }
        return count;
    }

    private static String requiredString(JSONObject row, String key) throws JSONException {
        String value = row.getString(key);
        if (value == null || value.trim().isEmpty()) throw new JSONException("missing " + key);
        return value.trim();
    }

    private static String nullableString(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return null;
        String value = row.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static AppTimeBasis portableTimeBasis(JSONObject data) throws JSONException {
        JSONObject preferences = data.getJSONObject("preferences");
        String raw = preferences.getString("time_basis");
        try {
            return AppTimeBasis.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw new JSONException("invalid time_basis " + raw);
        }
    }

    private static void validateTimeModel(JSONObject timeModel) throws JSONException {
        if (timeModel.optInt("schema_version", -1) != MeterTimeModelStore.JSON_SCHEMA) {
            throw new JSONException("unsupported meter time model schema");
        }
        JSONArray profiles = timeModel.getJSONArray("profiles");
        JSONArray anchors = timeModel.getJSONArray("anchors");
        // Validate every portable object now so malformed backups fail at preview/inspect time,
        // before the user confirms a restore.
        for (int i = 0; i < profiles.length(); i++) {
            MeterTimeModelStore.Profile.fromJson(profiles.getJSONObject(i));
        }
        for (int i = 0; i < anchors.length(); i++) {
            MeterTimeModelStore.AnchorRecord.fromJson(anchors.getJSONObject(i));
        }
    }

    private static JSONObject cloneJson(JSONObject source) throws JSONException {
        return new JSONObject(source.toString());
    }

    private static void writeZip(OutputStream output, JSONObject manifest, byte[] dataBytes)
            throws IOException, JSONException {
        ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(output), StandardCharsets.UTF_8);
        writeEntry(zip, MANIFEST, manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        writeEntry(zip, DATA, dataBytes);
        zip.finish();
        zip.flush();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException("BACKUP_TOO_LARGE");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(java.util.Locale.US, "%02x", b & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class ParsedBackup {
        final int schemaVersion;
        final JSONObject manifest;
        final JSONObject data;

        ParsedBackup(int schemaVersion, JSONObject manifest, JSONObject data) {
            this.schemaVersion = schemaVersion;
            this.manifest = manifest;
            this.data = data;
        }
    }
}
