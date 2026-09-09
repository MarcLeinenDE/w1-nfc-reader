package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical archive database shared by Hour/Day/Month/Year families.
 *
 * <p>Live NFC observations intentionally remain in meter_history.db. Archive data has different
 * identity/revision semantics and therefore uses this dedicated, family-neutral database. The UI
 * combines both sources through the semantic History layer.</p>
 */
final class ArchiveFamilyStore extends SQLiteOpenHelper implements ArchivePersistenceCoordinator.Store {
    static final String DB_NAME = "meter_archive.db";
    static final int DB_VERSION = 2;
    static final String TABLE_PERIODS = "archive_periods";
    static final String TABLE_CONFLICTS = "archive_conflicts";

    static final int CONFLICT_CONTENT = 1;
    static final int CONFLICT_STRUCTURE = 1 << 1;
    static final int CONFLICT_PROVENANCE = 1 << 2;
    private static final String V1_PERIODS = "archive_periods_v1";
    private static final String V1_CONFLICTS = "archive_conflicts_v1";
    private static final Pattern LEADING_NUMBER = Pattern.compile("^[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    ArchiveFamilyStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createPeriodsTable(db);
        createConflictsTable(db);
        createIndexes(db);
    }

    private static void createPeriodsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PERIODS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "archive_family TEXT NOT NULL,"
                + "logger_timestamp TEXT NOT NULL,"
                + "occurrence_key TEXT NOT NULL DEFAULT '" + ArchiveOccurrenceKey.LEGACY + "',"
                + "logger_time_basis TEXT NOT NULL,"
                + "on_time_seconds INTEGER,"
                + "raw_type_f_hex TEXT,"
                + "type_f_iv INTEGER,"
                + "type_f_su INTEGER,"
                + "retrieved_at_utc TEXT NOT NULL,"
                + "retrieved_at_ms INTEGER NOT NULL,"
                + "first_retrieved_at_utc TEXT NOT NULL,"
                + "first_retrieved_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + "content_fingerprint TEXT NOT NULL,"
                + "observation_count INTEGER NOT NULL DEFAULT 1,"
                + "identical_content_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "structural_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "provenance_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "revision_count INTEGER NOT NULL DEFAULT 0,"
                + "conflict_flags INTEGER NOT NULL DEFAULT 0,"
                + "last_content_fingerprint TEXT NOT NULL,"
                + "last_structural_fingerprint TEXT NOT NULL,"
                + "last_source TEXT NOT NULL,"
                + "last_validation TEXT NOT NULL,"
                + normalizedColumnsSql()
                + "extra_values TEXT NOT NULL DEFAULT '',"
                + "UNIQUE(meter_id, archive_family, logger_timestamp, occurrence_key)"
                + ")");
    }

    private static void createConflictsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CONFLICTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "archive_period_id INTEGER NOT NULL,"
                + "occurrence_key TEXT NOT NULL DEFAULT '" + ArchiveOccurrenceKey.LEGACY + "',"
                + "on_time_seconds INTEGER,"
                + "raw_type_f_hex TEXT,"
                + "type_f_iv INTEGER,"
                + "type_f_su INTEGER,"
                + "observed_at_utc TEXT NOT NULL,"
                + "observed_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + "content_fingerprint TEXT NOT NULL,"
                + "content_changed INTEGER NOT NULL,"
                + "structure_changed INTEGER NOT NULL,"
                + "provenance_changed INTEGER NOT NULL,"
                + normalizedColumnsSql()
                + "extra_values TEXT NOT NULL DEFAULT '',"
                + "FOREIGN KEY(archive_period_id) REFERENCES " + TABLE_PERIODS + "(id)"
                + ")");
    }

    private static void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX idx_archive_family_logger ON " + TABLE_PERIODS
                + " (meter_id, archive_family, logger_timestamp DESC, occurrence_key)");
        db.execSQL("CREATE INDEX idx_archive_all_logger ON " + TABLE_PERIODS
                + " (meter_id, logger_timestamp DESC, archive_family, occurrence_key)");
        db.execSQL("CREATE INDEX idx_archive_conflict_period_time ON " + TABLE_CONFLICTS
                + " (archive_period_id, observed_at_ms, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        int version = oldVersion;
        if (version == 1 && newVersion >= 2) {
            migrateV1ToV2(db);
            version = 2;
        }
        if (version != newVersion) {
            throw new IllegalStateException("Unsupported archive DB migration " + oldVersion + " -> " + newVersion);
        }
    }

    private static void migrateV1ToV2(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE_CONFLICTS + " RENAME TO " + V1_CONFLICTS);
        db.execSQL("ALTER TABLE " + TABLE_PERIODS + " RENAME TO " + V1_PERIODS);
        createPeriodsTable(db);
        createConflictsTable(db);

        String normalized = normalizedColumnNames();
        db.execSQL("INSERT INTO " + TABLE_PERIODS + " ("
                + "id,meter_id,archive_family,logger_timestamp,occurrence_key,logger_time_basis,"
                + "on_time_seconds,raw_type_f_hex,type_f_iv,type_f_su,"
                + "retrieved_at_utc,retrieved_at_ms,first_retrieved_at_utc,first_retrieved_at_ms,"
                + "source,validation,structural_fingerprint,content_fingerprint,observation_count,"
                + "identical_content_confirmations,structural_confirmations,provenance_confirmations,"
                + "revision_count,conflict_flags,last_content_fingerprint,last_structural_fingerprint,"
                + "last_source,last_validation," + normalized + ",extra_values) SELECT "
                + "id,meter_id,archive_family,logger_timestamp,'" + ArchiveOccurrenceKey.LEGACY + "',logger_time_basis,"
                + "NULL,NULL,NULL,NULL,retrieved_at_utc,retrieved_at_ms,first_retrieved_at_utc,first_retrieved_at_ms,"
                + "source,validation,structural_fingerprint,content_fingerprint,observation_count,"
                + "identical_content_confirmations,structural_confirmations,provenance_confirmations,"
                + "revision_count,conflict_flags,last_content_fingerprint,last_structural_fingerprint,"
                + "last_source,last_validation," + normalized + ",extra_values FROM " + V1_PERIODS);

        try (Cursor cursor = db.query(TABLE_PERIODS, new String[]{"id", "on_time"},
                null, null, null, null, "id")) {
            while (cursor.moveToNext()) {
                Long seconds = ArchiveOccurrenceKey.parseDurationSeconds(nullable(cursor, 1));
                if (seconds == null) continue;
                ContentValues update = new ContentValues();
                update.put("occurrence_key", ArchiveOccurrenceKey.fromEvidence(seconds, null));
                update.put("on_time_seconds", seconds);
                db.update(TABLE_PERIODS, update, "id=?", new String[]{Long.toString(cursor.getLong(0))});
            }
        }

        db.execSQL("INSERT INTO " + TABLE_CONFLICTS + " ("
                + "id,archive_period_id,occurrence_key,on_time_seconds,raw_type_f_hex,type_f_iv,type_f_su,"
                + "observed_at_utc,observed_at_ms,source,validation,structural_fingerprint,content_fingerprint,"
                + "content_changed,structure_changed,provenance_changed," + normalized + ",extra_values) SELECT "
                + "id,archive_period_id,'" + ArchiveOccurrenceKey.LEGACY + "',NULL,NULL,NULL,NULL,"
                + "observed_at_utc,observed_at_ms,source,validation,structural_fingerprint,content_fingerprint,"
                + "content_changed,structure_changed,provenance_changed," + normalized + ",extra_values FROM " + V1_CONFLICTS);
        db.execSQL("UPDATE " + TABLE_CONFLICTS + " SET occurrence_key=(SELECT occurrence_key FROM "
                + TABLE_PERIODS + " WHERE " + TABLE_PERIODS + ".id=" + TABLE_CONFLICTS + ".archive_period_id)");
        try (Cursor cursor = db.query(TABLE_CONFLICTS, new String[]{"id", "on_time"},
                null, null, null, null, "id")) {
            while (cursor.moveToNext()) {
                Long seconds = ArchiveOccurrenceKey.parseDurationSeconds(nullable(cursor, 1));
                if (seconds == null) continue;
                ContentValues update = new ContentValues();
                update.put("on_time_seconds", seconds);
                db.update(TABLE_CONFLICTS, update, "id=?", new String[]{Long.toString(cursor.getLong(0))});
            }
        }

        db.execSQL("DROP TABLE " + V1_CONFLICTS);
        db.execSQL("DROP TABLE " + V1_PERIODS);
        createIndexes(db);
    }

    @Override public ArchivePersistenceCoordinator.WriteOutcome upsert(String meterId, ArchiveFamilyPeriod period) {
        requireMeter(meterId);
        Objects.requireNonNull(period, "period");
        long retrievedAtMs = parseRetrievedAtUtc(period.retrievedAtUtc);
        String extras = encodeExtraValues(period.values.extraValues);
        String contentFingerprint = contentFingerprint(period.values, extras);
        Long onTimeSeconds = ArchiveOccurrenceKey.parseDurationSeconds(period.values.onTime);
        String occurrenceKey = ArchiveOccurrenceKey.fromEvidence(onTimeSeconds, null);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            StoredPeriod existing = getByKey(db, meterId, period.family, period.loggerTimestamp, occurrenceKey);
            if (existing == null) {
                ContentValues values = canonicalValues(meterId, period, retrievedAtMs, contentFingerprint,
                        extras, occurrenceKey, onTimeSeconds);
                db.insertOrThrow(TABLE_PERIODS, null, values);
                db.setTransactionSuccessful();
                return ArchivePersistenceCoordinator.WriteOutcome.INSERTED;
            }

            boolean contentSame = contentFingerprint.equals(existing.contentFingerprint);
            boolean structureSame = period.structuralFingerprint.equals(existing.structuralFingerprint);
            boolean provenanceSame = period.source.equals(existing.source)
                    && period.validation.equals(existing.validation);
            int conflictFlags = existing.conflictFlags;
            if (!contentSame) conflictFlags |= CONFLICT_CONTENT;
            if (!structureSame) conflictFlags |= CONFLICT_STRUCTURE;
            if (!provenanceSame) conflictFlags |= CONFLICT_PROVENANCE;

            ContentValues metadata = new ContentValues();
            if (retrievedAtMs < existing.firstRetrievedAtMs) {
                metadata.put("first_retrieved_at_utc", period.retrievedAtUtc);
                metadata.put("first_retrieved_at_ms", retrievedAtMs);
            }
            if (retrievedAtMs >= existing.retrievedAtMs) {
                metadata.put("retrieved_at_utc", period.retrievedAtUtc);
                metadata.put("retrieved_at_ms", retrievedAtMs);
                metadata.put("last_content_fingerprint", contentFingerprint);
                metadata.put("last_structural_fingerprint", period.structuralFingerprint);
                metadata.put("last_source", period.source);
                metadata.put("last_validation", period.validation);
            }
            metadata.put("observation_count", existing.observationCount + 1);
            metadata.put("identical_content_confirmations",
                    existing.identicalContentConfirmations + (contentSame ? 1 : 0));
            metadata.put("structural_confirmations",
                    existing.structuralConfirmations + (structureSame ? 1 : 0));
            metadata.put("provenance_confirmations",
                    existing.provenanceConfirmations + (provenanceSame ? 1 : 0));
            metadata.put("revision_count", existing.revisionCount + (contentSame ? 0 : 1));
            metadata.put("conflict_flags", conflictFlags);
            db.update(TABLE_PERIODS, metadata, "id = ?", new String[]{Long.toString(existing.id)});

            if (!contentSame || !structureSame || !provenanceSame) {
                ContentValues conflict = observationValues(period, retrievedAtMs, contentFingerprint,
                        extras, occurrenceKey, onTimeSeconds);
                conflict.put("archive_period_id", existing.id);
                conflict.put("content_changed", contentSame ? 0 : 1);
                conflict.put("structure_changed", structureSame ? 0 : 1);
                conflict.put("provenance_changed", provenanceSame ? 0 : 1);
                db.insertOrThrow(TABLE_CONFLICTS, null, conflict);
            }
            db.setTransactionSuccessful();
            return contentSame && structureSame && provenanceSame
                    ? ArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL
                    : ArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED;
        } finally {
            db.endTransaction();
        }
    }

    List<StoredPeriod> getPeriods(String meterId, ArchiveFamilyPeriod.Family family) {
        List<StoredPeriod> result = new ArrayList<>();
        if (meterId == null || meterId.trim().isEmpty()) return result;
        String selection = "meter_id = ?";
        List<String> args = new ArrayList<>();
        args.add(meterId.trim());
        if (family != null) {
            selection += " AND archive_family = ?";
            args.add(family.name());
        }
        try (Cursor cursor = getReadableDatabase().query(TABLE_PERIODS, projection(), selection,
                args.toArray(new String[0]), null, null,
                "logger_timestamp DESC, archive_family ASC, occurrence_key ASC, id ASC")) {
            while (cursor.moveToNext()) result.add(read(cursor));
        }
        return result;
    }

    List<String> getKnownTimestamps(String meterId, ArchiveFamilyPeriod.Family family) {
        List<String> result = new ArrayList<>();
        if (meterId == null || meterId.trim().isEmpty() || family == null) return result;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT logger_timestamp FROM " + TABLE_PERIODS
                        + " WHERE meter_id=? AND archive_family=? ORDER BY logger_timestamp DESC",
                new String[]{meterId.trim(), family.name()})) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    int conflictCount(String meterId, ArchiveFamilyPeriod.Family family, String loggerTimestamp) {
        if (meterId == null || family == null || loggerTimestamp == null) return 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_CONFLICTS + " c JOIN " + TABLE_PERIODS
                        + " p ON p.id=c.archive_period_id WHERE p.meter_id=? AND p.archive_family=?"
                        + " AND p.logger_timestamp=?",
                new String[]{meterId.trim(), family.name(), loggerTimestamp})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    int deleteMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_CONFLICTS, "archive_period_id IN (SELECT id FROM " + TABLE_PERIODS
                    + " WHERE meter_id = ?)", new String[]{meterId.trim()});
            int deleted = db.delete(TABLE_PERIODS, "meter_id = ?", new String[]{meterId.trim()});
            db.setTransactionSuccessful();
            return deleted;
        } finally { db.endTransaction(); }
    }

    private static ContentValues canonicalValues(String meterId, ArchiveFamilyPeriod period,
                                                  long retrievedAtMs, String contentFingerprint,
                                                  String extras, String occurrenceKey, Long onTimeSeconds) {
        ContentValues v = baseValueColumns(period, contentFingerprint, extras, occurrenceKey, onTimeSeconds);
        v.put("retrieved_at_utc", period.retrievedAtUtc);
        v.put("retrieved_at_ms", retrievedAtMs);
        v.put("meter_id", meterId.trim());
        v.put("archive_family", period.family.name());
        v.put("logger_timestamp", period.loggerTimestamp);
        v.put("logger_time_basis", period.loggerTimeBasis);
        v.put("first_retrieved_at_utc", period.retrievedAtUtc);
        v.put("first_retrieved_at_ms", retrievedAtMs);
        v.put("observation_count", 1);
        v.put("identical_content_confirmations", 0);
        v.put("structural_confirmations", 0);
        v.put("provenance_confirmations", 0);
        v.put("revision_count", 0);
        v.put("conflict_flags", 0);
        v.put("last_content_fingerprint", contentFingerprint);
        v.put("last_structural_fingerprint", period.structuralFingerprint);
        v.put("last_source", period.source);
        v.put("last_validation", period.validation);
        return v;
    }

    private static ContentValues observationValues(ArchiveFamilyPeriod period, long retrievedAtMs,
                                                    String contentFingerprint, String extras,
                                                    String occurrenceKey, Long onTimeSeconds) {
        ContentValues v = baseValueColumns(period, contentFingerprint, extras, occurrenceKey, onTimeSeconds);
        v.put("observed_at_utc", period.retrievedAtUtc);
        v.put("observed_at_ms", retrievedAtMs);
        return v;
    }

    private static ContentValues baseValueColumns(ArchiveFamilyPeriod period,
                                                   String contentFingerprint, String extras,
                                                   String occurrenceKey, Long onTimeSeconds) {
        ContentValues v = new ContentValues();
        v.put("occurrence_key", occurrenceKey);
        if (onTimeSeconds == null) v.putNull("on_time_seconds"); else v.put("on_time_seconds", onTimeSeconds);
        v.putNull("raw_type_f_hex");
        v.putNull("type_f_iv");
        v.putNull("type_f_su");
        v.put("source", period.source);
        v.put("validation", period.validation);
        v.put("structural_fingerprint", period.structuralFingerprint);
        v.put("content_fingerprint", contentFingerprint);
        putNormalized(v, period.values);
        v.put("extra_values", extras);
        return v;
    }

    private static void putNormalized(ContentValues v, ArchiveNormalizedValues n) {
        put(v, "total_volume", n.totalVolume);
        put(v, "positive_volume", n.positiveVolume);
        put(v, "reverse_volume", n.reverseVolume);
        put(v, "tariff1_volume", n.tariff1Volume);
        put(v, "max_flow", n.maxFlow);
        put(v, "max_flow_at", n.maxFlowAt);
        put(v, "min_flow", n.minFlow);
        put(v, "min_flow_at", n.minFlowAt);
        put(v, "flow", n.flow);
        put(v, "max_temperature", n.maxTemperature);
        put(v, "max_temperature_at", n.maxTemperatureAt);
        put(v, "min_temperature", n.minTemperature);
        put(v, "min_temperature_at", n.minTemperatureAt);
        put(v, "temperature", n.temperature);
        put(v, "external_temperature", n.externalTemperature);
        put(v, "battery_percent", n.batteryPercent);
        put(v, "error_flags", n.errorFlags);
        put(v, "on_time", n.onTime);
        put(v, "operating_time", n.operatingTime);
    }

    private static void put(ContentValues v, String key, String value) {
        if (value == null) v.putNull(key); else v.put(key, value);
    }

    private static String normalizedColumnsSql() {
        return "total_volume TEXT,positive_volume TEXT,reverse_volume TEXT,tariff1_volume TEXT,"
                + "max_flow TEXT,max_flow_at TEXT,min_flow TEXT,min_flow_at TEXT,flow TEXT,"
                + "max_temperature TEXT,max_temperature_at TEXT,min_temperature TEXT,min_temperature_at TEXT,"
                + "temperature TEXT,external_temperature TEXT,battery_percent TEXT,error_flags TEXT,"
                + "on_time TEXT,operating_time TEXT,";
    }

    private static String normalizedColumnNames() {
        return "total_volume,positive_volume,reverse_volume,tariff1_volume,max_flow,max_flow_at,"
                + "min_flow,min_flow_at,flow,max_temperature,max_temperature_at,min_temperature,min_temperature_at,"
                + "temperature,external_temperature,battery_percent,error_flags,on_time,operating_time";
    }

    private StoredPeriod getByKey(SQLiteDatabase db, String meterId, ArchiveFamilyPeriod.Family family,
                                  String loggerTimestamp, String occurrenceKey) {
        if (meterId == null || family == null || loggerTimestamp == null || occurrenceKey == null) return null;
        try (Cursor cursor = db.query(TABLE_PERIODS, projection(),
                "meter_id = ? AND archive_family = ? AND logger_timestamp = ? AND occurrence_key = ?",
                new String[]{meterId.trim(), family.name(), loggerTimestamp, occurrenceKey},
                null, null, null, "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    private static String[] projection() {
        return new String[]{"id","meter_id","archive_family","logger_timestamp","occurrence_key","logger_time_basis",
                "on_time_seconds","raw_type_f_hex","type_f_iv","type_f_su","retrieved_at_utc","retrieved_at_ms",
                "first_retrieved_at_utc","first_retrieved_at_ms","source","validation","structural_fingerprint",
                "content_fingerprint","observation_count","identical_content_confirmations","structural_confirmations",
                "provenance_confirmations","revision_count","conflict_flags","last_content_fingerprint",
                "last_structural_fingerprint","last_source","last_validation","total_volume","positive_volume",
                "reverse_volume","tariff1_volume","max_flow","max_flow_at","min_flow","min_flow_at","flow",
                "max_temperature","max_temperature_at","min_temperature","min_temperature_at","temperature",
                "external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values"};
    }

    private static StoredPeriod read(Cursor c) {
        return new StoredPeriod(c.getLong(0), c.getString(1),
                ArchiveFamilyPeriod.Family.valueOf(c.getString(2)), c.getString(3), c.getString(4), c.getString(5),
                nullableLong(c,6), nullable(c,7), nullableInt(c,8), nullableInt(c,9), c.getString(10), c.getLong(11),
                c.getString(12), c.getLong(13), c.getString(14), c.getString(15), c.getString(16), c.getString(17),
                c.getInt(18), c.getInt(19), c.getInt(20), c.getInt(21), c.getInt(22), c.getInt(23), c.getString(24),
                c.getString(25), c.getString(26), c.getString(27), measurementNumber(nullable(c,28)), nullable(c,29),
                nullable(c,30), nullable(c,31), nullable(c,32), nullable(c,33), nullable(c,34), nullable(c,35),
                nullable(c,36), nullable(c,37), nullable(c,38), nullable(c,39), nullable(c,40), nullable(c,41),
                nullable(c,42), nullable(c,43), nullable(c,44), nullable(c,45), nullable(c,46),
                decodeExtraValues(c.getString(47)));
    }

    private static String nullable(Cursor c, int index) { return c.isNull(index) ? null : c.getString(index); }
    private static Long nullableLong(Cursor c, int index) { return c.isNull(index) ? null : c.getLong(index); }
    private static Integer nullableInt(Cursor c, int index) { return c.isNull(index) ? null : c.getInt(index); }

    static String measurementNumber(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace(',', '.');
        Matcher matcher = LEADING_NUMBER.matcher(normalized);
        return matcher.find() ? matcher.group() : null;
    }

    static final class StoredPeriod {
        final long id;
        final String meterId;
        final ArchiveFamilyPeriod.Family family;
        final String loggerTimestamp;
        final String occurrenceKey;
        final String loggerTimeBasis;
        final Long onTimeSeconds;
        final String rawTypeFHex;
        final Integer typeFIv;
        final Integer typeFSu;
        final String retrievedAtUtc;
        final long retrievedAtMs;
        final String firstRetrievedAtUtc;
        final long firstRetrievedAtMs;
        final String source;
        final String validation;
        final String structuralFingerprint;
        final String contentFingerprint;
        final int observationCount;
        final int identicalContentConfirmations;
        final int structuralConfirmations;
        final int provenanceConfirmations;
        final int revisionCount;
        final int conflictFlags;
        final String lastContentFingerprint;
        final String lastStructuralFingerprint;
        final String lastSource;
        final String lastValidation;
        final String totalVolume;
        final String positiveVolume;
        final String reverseVolume;
        final String tariff1Volume;
        final String maxFlow;
        final String maxFlowAt;
        final String minFlow;
        final String minFlowAt;
        final String flow;
        final String maxTemperature;
        final String maxTemperatureAt;
        final String minTemperature;
        final String minTemperatureAt;
        final String temperature;
        final String externalTemperature;
        final String batteryPercent;
        final String errorFlags;
        final String onTime;
        final String operatingTime;
        final Map<String,String> extraValues;

        StoredPeriod(long id, String meterId, ArchiveFamilyPeriod.Family family, String loggerTimestamp,
                     String occurrenceKey, String loggerTimeBasis, Long onTimeSeconds, String rawTypeFHex,
                     Integer typeFIv, Integer typeFSu, String retrievedAtUtc, long retrievedAtMs,
                     String firstRetrievedAtUtc, long firstRetrievedAtMs, String source, String validation,
                     String structuralFingerprint, String contentFingerprint, int observationCount,
                     int identicalContentConfirmations, int structuralConfirmations,
                     int provenanceConfirmations, int revisionCount, int conflictFlags,
                     String lastContentFingerprint, String lastStructuralFingerprint, String lastSource,
                     String lastValidation, String totalVolume, String positiveVolume, String reverseVolume,
                     String tariff1Volume, String maxFlow, String maxFlowAt, String minFlow, String minFlowAt,
                     String flow, String maxTemperature, String maxTemperatureAt, String minTemperature,
                     String minTemperatureAt, String temperature, String externalTemperature,
                     String batteryPercent, String errorFlags, String onTime, String operatingTime,
                     Map<String,String> extraValues) {
            this.id=id; this.meterId=meterId; this.family=family; this.loggerTimestamp=loggerTimestamp;
            this.occurrenceKey=occurrenceKey; this.loggerTimeBasis=loggerTimeBasis; this.onTimeSeconds=onTimeSeconds;
            this.rawTypeFHex=rawTypeFHex; this.typeFIv=typeFIv; this.typeFSu=typeFSu;
            this.retrievedAtUtc=retrievedAtUtc; this.retrievedAtMs=retrievedAtMs;
            this.firstRetrievedAtUtc=firstRetrievedAtUtc; this.firstRetrievedAtMs=firstRetrievedAtMs;
            this.source=source; this.validation=validation; this.structuralFingerprint=structuralFingerprint;
            this.contentFingerprint=contentFingerprint; this.observationCount=observationCount;
            this.identicalContentConfirmations=identicalContentConfirmations;
            this.structuralConfirmations=structuralConfirmations; this.provenanceConfirmations=provenanceConfirmations;
            this.revisionCount=revisionCount; this.conflictFlags=conflictFlags;
            this.lastContentFingerprint=lastContentFingerprint; this.lastStructuralFingerprint=lastStructuralFingerprint;
            this.lastSource=lastSource; this.lastValidation=lastValidation; this.totalVolume=totalVolume;
            this.positiveVolume=positiveVolume; this.reverseVolume=reverseVolume; this.tariff1Volume=tariff1Volume;
            this.maxFlow=maxFlow; this.maxFlowAt=maxFlowAt; this.minFlow=minFlow; this.minFlowAt=minFlowAt;
            this.flow=flow; this.maxTemperature=maxTemperature; this.maxTemperatureAt=maxTemperatureAt;
            this.minTemperature=minTemperature; this.minTemperatureAt=minTemperatureAt;
            this.temperature=temperature; this.externalTemperature=externalTemperature;
            this.batteryPercent=batteryPercent; this.errorFlags=errorFlags; this.onTime=onTime;
            this.operatingTime=operatingTime; this.extraValues=extraValues;
        }
    }

    private static String contentFingerprint(ArchiveNormalizedValues n, String extras) {
        StringBuilder s = new StringBuilder();
        append(s,n.totalVolume); append(s,n.positiveVolume); append(s,n.reverseVolume); append(s,n.tariff1Volume);
        append(s,n.maxFlow); append(s,n.maxFlowAt); append(s,n.minFlow); append(s,n.minFlowAt); append(s,n.flow);
        append(s,n.maxTemperature); append(s,n.maxTemperatureAt); append(s,n.minTemperature); append(s,n.minTemperatureAt);
        append(s,n.temperature); append(s,n.externalTemperature); append(s,n.batteryPercent); append(s,n.errorFlags);
        append(s,n.onTime); append(s,n.operatingTime); append(s,extras);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void append(StringBuilder s, String value) {
        String v = value == null ? "<null>" : value;
        s.append(v.length()).append(':').append(v).append('|');
    }

    private static long parseRetrievedAtUtc(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try { Date d = format.parse(value); if (d == null) throw new ParseException(value,0); return d.getTime(); }
        catch (ParseException e) { throw new IllegalArgumentException("invalid retrievedAtUtc " + value, e); }
    }

    static String encodeExtraValues(Map<String,String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort(String::compareTo);
        StringBuilder out = new StringBuilder();
        for (String key : keys) {
            out.append(escape(key)).append('\t').append(escape(values.get(key))).append('\n');
        }
        return out.toString();
    }

    static Map<String,String> decodeExtraValues(String encoded) {
        Map<String,String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return result;
        String[] lines = encoded.split("\\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int tab = findUnescapedTab(line);
            if (tab < 0) continue;
            result.put(unescape(line.substring(0,tab)), unescape(line.substring(tab+1)));
        }
        return result;
    }

    private static int findUnescapedTab(String line) {
        boolean escaped = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (escaped) { escaped=false; continue; }
            if (c=='\\') { escaped=true; continue; }
            if (c=='\t') return i;
        }
        return -1;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n");
    }

    private static String unescape(String value) {
        StringBuilder out=new StringBuilder(); boolean escaped=false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (!escaped && c=='\\') { escaped=true; continue; }
            if (escaped) { out.append(c=='t'?'\t':c=='n'?'\n':c); escaped=false; }
            else out.append(c);
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static void requireMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) throw new IllegalArgumentException("meterId required");
    }
}
