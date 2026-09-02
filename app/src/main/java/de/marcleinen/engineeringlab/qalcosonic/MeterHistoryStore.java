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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Local-only History for successful live readouts and validated archive periods.
 *
 * <p>The original v1 {@code readings} table remains unchanged. Archive periods use deterministic
 * identity {@code meter_id + archive_family + logger_timestamp}. DB v3 adds confirmation/revision
 * metadata plus one normalized observation row per retrieval so changed historical content cannot
 * silently overwrite the evidence that was seen before. NFC UID, ST25 transport, mailbox contents,
 * raw M-Bus frames and diagnostic traces are never stored here.</p>
 */
final class MeterHistoryStore extends SQLiteOpenHelper
        implements MonthlyArchivePersistenceCoordinator.Store {
    static final long DUPLICATE_WINDOW_MS = 5L * 60L * 1000L;
    private static final double DUPLICATE_READING_EPSILON_M3 = 0.0005;

    static final String DB_NAME = "meter_history.db";
    static final int DB_VERSION = 3;
    static final String TABLE_READINGS = "readings";
    static final String TABLE_ARCHIVE = "archive_periods";
    static final String TABLE_ARCHIVE_OBSERVATIONS = "archive_observations";
    static final String ARCHIVE_FAMILY_MONTH = "MONTH";

    static final int ARCHIVE_CONFLICT_CONTENT = 1;
    static final int ARCHIVE_CONFLICT_STRUCTURE = 1 << 1;
    static final int ARCHIVE_CONFLICT_PROVENANCE = 1 << 2;

    MeterHistoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createReadingsSchema(db);
        createArchiveSchemaV3(db);
        createArchiveObservationSchema(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1 || oldVersion > newVersion || newVersion > DB_VERSION) {
            throw new IllegalStateException("Unsupported meter history DB migration "
                    + oldVersion + " -> " + newVersion);
        }
        int version = oldVersion;
        if (version == 1 && newVersion >= 2) {
            // Non-destructive v1 -> v2 migration. The physically used live-read table is not
            // renamed, rewritten or copied; only the archive table/indexes are added.
            createArchiveSchemaV2(db);
            version = 2;
        }
        if (version == 2 && newVersion >= 3) {
            migrateArchiveSchemaV2ToV3(db);
            version = 3;
        }
        if (version != newVersion) {
            throw new IllegalStateException("Unsupported meter history DB migration "
                    + oldVersion + " -> " + newVersion);
        }
    }

    private static void createReadingsSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_READINGS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "read_at_ms INTEGER NOT NULL,"
                + "meter_time TEXT,"
                + "total_m3 REAL NOT NULL,"
                + "positive_m3 REAL,"
                + "negative_m3 REAL,"
                + "flow_m3h REAL,"
                + "water_temp_c REAL,"
                + "ambient_temp_c REAL,"
                + "battery_percent INTEGER,"
                + "alarm_codes TEXT"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_readings_meter_time ON " + TABLE_READINGS
                + " (meter_id, read_at_ms DESC)");
    }

    /** Exact archive schema introduced by 0.7.24 / DB v2, retained for staged migration. */
    private static void createArchiveSchemaV2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ARCHIVE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "archive_family TEXT NOT NULL,"
                + "logger_timestamp TEXT NOT NULL,"
                + "retrieved_at_utc TEXT NOT NULL,"
                + "retrieved_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + normalizedArchiveColumnsSql()
                + "UNIQUE(meter_id, archive_family, logger_timestamp)"
                + ")");
        createArchiveIndexes(db);
    }

    private static void createArchiveSchemaV3(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ARCHIVE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "archive_family TEXT NOT NULL,"
                + "logger_timestamp TEXT NOT NULL,"
                + "retrieved_at_utc TEXT NOT NULL,"
                + "retrieved_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + normalizedArchiveColumnsSql()
                + "first_retrieved_at_utc TEXT NOT NULL,"
                + "first_retrieved_at_ms INTEGER NOT NULL,"
                + "observation_count INTEGER NOT NULL DEFAULT 1,"
                + "identical_content_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "structural_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "provenance_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "revision_count INTEGER NOT NULL DEFAULT 0,"
                + "conflict_flags INTEGER NOT NULL DEFAULT 0,"
                + "last_content_fingerprint TEXT,"
                + "last_structural_fingerprint TEXT NOT NULL,"
                + "last_source TEXT NOT NULL,"
                + "last_validation TEXT NOT NULL,"
                + "UNIQUE(meter_id, archive_family, logger_timestamp)"
                + ")");
        createArchiveIndexes(db);
    }

    private static String normalizedArchiveColumnsSql() {
        return "total_volume TEXT,"
                + "positive_volume TEXT,"
                + "reverse_volume TEXT,"
                + "tariff1_volume TEXT,"
                + "max_flow TEXT,"
                + "max_flow_at TEXT,"
                + "min_flow TEXT,"
                + "min_flow_at TEXT,"
                + "flow TEXT,"
                + "max_temperature TEXT,"
                + "max_temperature_at TEXT,"
                + "min_temperature TEXT,"
                + "min_temperature_at TEXT,"
                + "temperature TEXT,"
                + "external_temperature TEXT,"
                + "battery_percent TEXT,"
                + "error_flags TEXT,"
                + "on_time TEXT,"
                + "operating_time TEXT,"
                + "unmodeled_semantics TEXT,";
    }

    private static void createArchiveIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_archive_meter_logger ON " + TABLE_ARCHIVE
                + " (meter_id, archive_family, logger_timestamp DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_archive_meter_retrieved ON " + TABLE_ARCHIVE
                + " (meter_id, retrieved_at_ms DESC)");
    }

    private static void createArchiveObservationSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ARCHIVE_OBSERVATIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "archive_period_id INTEGER NOT NULL,"
                + "observed_at_utc TEXT NOT NULL,"
                + "observed_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + "content_fingerprint TEXT,"
                + "content_changed INTEGER NOT NULL DEFAULT 0,"
                + "structure_changed INTEGER NOT NULL DEFAULT 0,"
                + "provenance_changed INTEGER NOT NULL DEFAULT 0,"
                + normalizedArchiveColumnsSql()
                + "FOREIGN KEY(archive_period_id) REFERENCES " + TABLE_ARCHIVE + "(id)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_archive_observation_period_time ON "
                + TABLE_ARCHIVE_OBSERVATIONS + " (archive_period_id, observed_at_ms, id)");
    }

    private static void migrateArchiveSchemaV2ToV3(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN first_retrieved_at_utc TEXT");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN first_retrieved_at_ms INTEGER");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN observation_count INTEGER NOT NULL DEFAULT 1");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN identical_content_confirmations INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN structural_confirmations INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN provenance_confirmations INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN revision_count INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE
                + " ADD COLUMN conflict_flags INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN last_content_fingerprint TEXT");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN last_structural_fingerprint TEXT");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN last_source TEXT");
        db.execSQL("ALTER TABLE " + TABLE_ARCHIVE + " ADD COLUMN last_validation TEXT");

        db.execSQL("UPDATE " + TABLE_ARCHIVE + " SET "
                + "first_retrieved_at_utc = retrieved_at_utc,"
                + "first_retrieved_at_ms = retrieved_at_ms,"
                + "observation_count = 1,"
                + "identical_content_confirmations = 0,"
                + "structural_confirmations = 0,"
                + "provenance_confirmations = 0,"
                + "revision_count = 0,"
                + "conflict_flags = 0,"
                + "last_structural_fingerprint = structural_fingerprint,"
                + "last_source = source,"
                + "last_validation = validation");

        createArchiveObservationSchema(db);
        db.execSQL("INSERT INTO " + TABLE_ARCHIVE_OBSERVATIONS + " ("
                + "archive_period_id,observed_at_utc,observed_at_ms,source,validation,"
                + "structural_fingerprint,content_fingerprint,content_changed,structure_changed,"
                + "provenance_changed,total_volume,positive_volume,reverse_volume,tariff1_volume,"
                + "max_flow,max_flow_at,min_flow,min_flow_at,flow,max_temperature,max_temperature_at,"
                + "min_temperature,min_temperature_at,temperature,external_temperature,battery_percent,"
                + "error_flags,on_time,operating_time,unmodeled_semantics) SELECT "
                + "id,retrieved_at_utc,retrieved_at_ms,source,validation,structural_fingerprint,NULL,"
                + "0,0,0,total_volume,positive_volume,reverse_volume,tariff1_volume,max_flow,max_flow_at,"
                + "min_flow,min_flow_at,flow,max_temperature,max_temperature_at,min_temperature,"
                + "min_temperature_at,temperature,external_temperature,battery_percent,error_flags,"
                + "on_time,operating_time,unmodeled_semantics FROM " + TABLE_ARCHIVE);
    }

    boolean insertSuccessful(MbusParser.MeterData meter, long readAtMs) {
        if (meter == null || meter.meterId == null || meter.meterId.isEmpty()
                || meter.waterUsageM3 == null || readAtMs <= 0L) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Reading latest = getLatestForMeter(db, meter.meterId);
            if (latest != null
                    && readAtMs >= latest.readAtMs
                    && readAtMs - latest.readAtMs <= DUPLICATE_WINDOW_MS
                    && Math.abs(meter.waterUsageM3 - latest.totalM3) < DUPLICATE_READING_EPSILON_M3) {
                db.setTransactionSuccessful();
                return false;
            }

            ContentValues values = new ContentValues();
            values.put("meter_id", meter.meterId);
            values.put("read_at_ms", readAtMs);
            putNullable(values, "meter_time", meter.timepoint);
            values.put("total_m3", meter.waterUsageM3);
            putNullable(values, "positive_m3", meter.positiveUsageM3);
            putNullable(values, "negative_m3", meter.negativeUsageM3);
            putNullable(values, "flow_m3h", meter.flowM3h);
            putNullable(values, "water_temp_c", meter.waterTemperatureC);
            putNullable(values, "ambient_temp_c", meter.externalTemperatureC);
            putNullable(values, "battery_percent", meter.batteryPercent);
            values.put("alarm_codes", serializeAlarms(meter.getErrors()));

            db.insertOrThrow(TABLE_READINGS, null, values);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public MonthlyArchivePersistenceCoordinator.WriteOutcome upsertMonthlyArchive(
            String meterIdentity, MonthlyArchivePeriod period) {
        validateMonthlyArchive(meterIdentity, period);
        ArchivePeriodSnapshot snapshot = period.snapshot;
        long retrievedAtMs = parseRetrievedAtUtc(period.retrievedAtUtc);
        String contentFingerprint = contentFingerprint(snapshot);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ArchiveReading existing = getArchiveByKey(
                    db, meterIdentity, ARCHIVE_FAMILY_MONTH, period.loggerTimestamp);
            if (existing == null) {
                ContentValues values = canonicalArchiveValues(
                        meterIdentity, period, retrievedAtMs, contentFingerprint);
                long periodId = db.insertOrThrow(TABLE_ARCHIVE, null, values);
                insertArchiveObservation(db, periodId, period, retrievedAtMs, contentFingerprint,
                        false, false, false);
                db.setTransactionSuccessful();
                return MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED;
            }

            boolean contentSame = archiveContentMatches(existing, snapshot);
            boolean structureSame = period.structuralFingerprint.equals(existing.structuralFingerprint);
            boolean provenanceSame = period.source.equals(existing.source)
                    && period.validation.equals(existing.validation);

            int newConflictFlags = existing.conflictFlags;
            if (!contentSame) newConflictFlags |= ARCHIVE_CONFLICT_CONTENT;
            if (!structureSame) newConflictFlags |= ARCHIVE_CONFLICT_STRUCTURE;
            if (!provenanceSame) newConflictFlags |= ARCHIVE_CONFLICT_PROVENANCE;

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
            metadata.put("conflict_flags", newConflictFlags);

            db.update(TABLE_ARCHIVE, metadata, "id = ?", new String[]{Long.toString(existing.id)});
            insertArchiveObservation(db, existing.id, period, retrievedAtMs, contentFingerprint,
                    !contentSame, !structureSame, !provenanceSame);
            db.setTransactionSuccessful();

            return newConflictFlags == existing.conflictFlags
                    && contentSame && structureSame && provenanceSame
                    ? MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL
                    : MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED;
        } finally {
            db.endTransaction();
        }
    }

    private static ContentValues canonicalArchiveValues(
            String meterIdentity,
            MonthlyArchivePeriod period,
            long retrievedAtMs,
            String contentFingerprint) {
        ArchivePeriodSnapshot snapshot = period.snapshot;
        ContentValues values = new ContentValues();
        values.put("meter_id", meterIdentity);
        values.put("archive_family", ARCHIVE_FAMILY_MONTH);
        values.put("logger_timestamp", period.loggerTimestamp);
        values.put("retrieved_at_utc", period.retrievedAtUtc);
        values.put("retrieved_at_ms", retrievedAtMs);
        values.put("source", period.source);
        values.put("validation", period.validation);
        values.put("structural_fingerprint", period.structuralFingerprint);
        putSnapshotValues(values, snapshot);
        values.put("first_retrieved_at_utc", period.retrievedAtUtc);
        values.put("first_retrieved_at_ms", retrievedAtMs);
        values.put("observation_count", 1);
        values.put("identical_content_confirmations", 0);
        values.put("structural_confirmations", 0);
        values.put("provenance_confirmations", 0);
        values.put("revision_count", 0);
        values.put("conflict_flags", 0);
        values.put("last_content_fingerprint", contentFingerprint);
        values.put("last_structural_fingerprint", period.structuralFingerprint);
        values.put("last_source", period.source);
        values.put("last_validation", period.validation);
        return values;
    }

    private static void insertArchiveObservation(
            SQLiteDatabase db,
            long periodId,
            MonthlyArchivePeriod period,
            long retrievedAtMs,
            String contentFingerprint,
            boolean contentChanged,
            boolean structureChanged,
            boolean provenanceChanged) {
        ContentValues values = new ContentValues();
        values.put("archive_period_id", periodId);
        values.put("observed_at_utc", period.retrievedAtUtc);
        values.put("observed_at_ms", retrievedAtMs);
        values.put("source", period.source);
        values.put("validation", period.validation);
        values.put("structural_fingerprint", period.structuralFingerprint);
        values.put("content_fingerprint", contentFingerprint);
        values.put("content_changed", contentChanged ? 1 : 0);
        values.put("structure_changed", structureChanged ? 1 : 0);
        values.put("provenance_changed", provenanceChanged ? 1 : 0);
        putSnapshotValues(values, period.snapshot);
        db.insertOrThrow(TABLE_ARCHIVE_OBSERVATIONS, null, values);
    }

    private static void putSnapshotValues(ContentValues values, ArchivePeriodSnapshot snapshot) {
        putNullable(values, "total_volume", snapshot.totalVolume);
        putNullable(values, "positive_volume", snapshot.positiveVolume);
        putNullable(values, "reverse_volume", snapshot.reverseVolume);
        putNullable(values, "tariff1_volume", snapshot.tariff1Volume);
        putNullable(values, "max_flow", snapshot.maxFlow);
        putNullable(values, "max_flow_at", snapshot.maxFlowAt);
        putNullable(values, "min_flow", snapshot.minFlow);
        putNullable(values, "min_flow_at", snapshot.minFlowAt);
        putNullable(values, "flow", snapshot.flow);
        putNullable(values, "max_temperature", snapshot.maxTemperature);
        putNullable(values, "max_temperature_at", snapshot.maxTemperatureAt);
        putNullable(values, "min_temperature", snapshot.minTemperature);
        putNullable(values, "min_temperature_at", snapshot.minTemperatureAt);
        putNullable(values, "temperature", snapshot.temperature);
        putNullable(values, "external_temperature", snapshot.externalTemperature);
        putNullable(values, "battery_percent", snapshot.batteryPercent);
        putNullable(values, "error_flags", snapshot.errorFlags);
        putNullable(values, "on_time", snapshot.onTime);
        putNullable(values, "operating_time", snapshot.operatingTime);
        values.put("unmodeled_semantics", serializeTextList(snapshot.unmodeledSemantics));
    }

    String getLatestMeterId() {
        List<String> ids = getMeterIds();
        return ids.isEmpty() ? null : ids.get(0);
    }

    List<String> getMeterIds() {
        SQLiteDatabase db = getReadableDatabase();
        List<String> result = new ArrayList<>();
        String sql = "SELECT meter_id, MAX(latest_ms) AS latest_ms FROM ("
                + "SELECT meter_id, MAX(read_at_ms) AS latest_ms FROM " + TABLE_READINGS
                + " GROUP BY meter_id UNION ALL "
                + "SELECT meter_id, MAX(retrieved_at_ms) AS latest_ms FROM " + TABLE_ARCHIVE
                + " GROUP BY meter_id) GROUP BY meter_id ORDER BY latest_ms DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    List<Reading> getReadings(String meterId, long sinceMs) {
        List<Reading> result = new ArrayList<>();
        if (meterId == null || meterId.isEmpty()) return result;

        String selection = "meter_id = ?";
        List<String> args = new ArrayList<>();
        args.add(meterId);
        if (sinceMs > 0L) {
            selection += " AND read_at_ms >= ?";
            args.add(Long.toString(sinceMs));
        }

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_READINGS,
                readingProjection(),
                selection,
                args.toArray(new String[0]),
                null,
                null,
                "read_at_ms DESC")) {
            while (cursor.moveToNext()) result.add(readReading(cursor));
        }
        return result;
    }

    @Override
    public List<String> getKnownMonthlyTimestamps(String meterIdentity) {
        List<String> result = new ArrayList<>();
        if (meterIdentity == null || meterIdentity.isEmpty()) return result;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_ARCHIVE,
                new String[]{"logger_timestamp"},
                "meter_id = ? AND archive_family = ?",
                new String[]{meterIdentity, ARCHIVE_FAMILY_MONTH},
                null,
                null,
                "logger_timestamp DESC")) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    List<ArchiveReading> getMonthlyArchivePeriods(String meterId, String sinceLoggerTimestamp) {
        List<ArchiveReading> result = new ArrayList<>();
        if (meterId == null || meterId.isEmpty()) return result;
        String selection = "meter_id = ? AND archive_family = ?";
        List<String> args = new ArrayList<>();
        args.add(meterId);
        args.add(ARCHIVE_FAMILY_MONTH);
        if (sinceLoggerTimestamp != null && !sinceLoggerTimestamp.isEmpty()) {
            selection += " AND logger_timestamp >= ?";
            args.add(sinceLoggerTimestamp);
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_ARCHIVE,
                archiveProjection(),
                selection,
                args.toArray(new String[0]),
                null,
                null,
                "logger_timestamp DESC")) {
            while (cursor.moveToNext()) result.add(readArchive(cursor));
        }
        return result;
    }

    List<ArchiveObservation> getMonthlyArchiveObservations(
            String meterId, String loggerTimestamp) {
        List<ArchiveObservation> result = new ArrayList<>();
        if (meterId == null || meterId.isEmpty()
                || loggerTimestamp == null || loggerTimestamp.isEmpty()) return result;
        SQLiteDatabase db = getReadableDatabase();
        ArchiveReading period = getArchiveByKey(
                db, meterId, ARCHIVE_FAMILY_MONTH, loggerTimestamp);
        if (period == null) return result;
        try (Cursor cursor = db.query(
                TABLE_ARCHIVE_OBSERVATIONS,
                archiveObservationProjection(),
                "archive_period_id = ?",
                new String[]{Long.toString(period.id)},
                null,
                null,
                "observed_at_ms ASC, id ASC")) {
            while (cursor.moveToNext()) result.add(readArchiveObservation(cursor));
        }
        return result;
    }

    int deleteMeter(String meterId) {
        if (meterId == null || meterId.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ARCHIVE_OBSERVATIONS,
                    "archive_period_id IN (SELECT id FROM " + TABLE_ARCHIVE + " WHERE meter_id = ?)",
                    new String[]{meterId});
            int deleted = db.delete(TABLE_READINGS, "meter_id = ?", new String[]{meterId});
            deleted += db.delete(TABLE_ARCHIVE, "meter_id = ?", new String[]{meterId});
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
        }
    }

    int deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ARCHIVE_OBSERVATIONS, null, null);
            int deleted = db.delete(TABLE_READINGS, null, null);
            deleted += db.delete(TABLE_ARCHIVE, null, null);
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
        }
    }

    private Reading getLatestForMeter(SQLiteDatabase db, String meterId) {
        try (Cursor cursor = db.query(
                TABLE_READINGS,
                readingProjection(),
                "meter_id = ?",
                new String[]{meterId},
                null,
                null,
                "read_at_ms DESC",
                "1")) {
            return cursor.moveToFirst() ? readReading(cursor) : null;
        }
    }

    private ArchiveReading getArchiveByKey(
            SQLiteDatabase db, String meterId, String archiveFamily, String loggerTimestamp) {
        try (Cursor cursor = db.query(
                TABLE_ARCHIVE,
                archiveProjection(),
                "meter_id = ? AND archive_family = ? AND logger_timestamp = ?",
                new String[]{meterId, archiveFamily, loggerTimestamp},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst() ? readArchive(cursor) : null;
        }
    }

    private static String[] readingProjection() {
        return new String[]{
                "id", "meter_id", "read_at_ms", "meter_time", "total_m3", "positive_m3",
                "negative_m3", "flow_m3h", "water_temp_c", "ambient_temp_c",
                "battery_percent", "alarm_codes"
        };
    }

    private static String[] archiveProjection() {
        return new String[]{
                "id", "meter_id", "archive_family", "logger_timestamp", "retrieved_at_utc",
                "retrieved_at_ms", "source", "validation", "structural_fingerprint",
                "total_volume", "positive_volume", "reverse_volume", "tariff1_volume",
                "max_flow", "max_flow_at", "min_flow", "min_flow_at", "flow",
                "max_temperature", "max_temperature_at", "min_temperature", "min_temperature_at",
                "temperature", "external_temperature", "battery_percent", "error_flags",
                "on_time", "operating_time", "unmodeled_semantics", "first_retrieved_at_utc",
                "first_retrieved_at_ms", "observation_count", "identical_content_confirmations",
                "structural_confirmations", "provenance_confirmations", "revision_count",
                "conflict_flags", "last_content_fingerprint", "last_structural_fingerprint",
                "last_source", "last_validation"
        };
    }

    private static String[] archiveObservationProjection() {
        return new String[]{
                "id", "archive_period_id", "observed_at_utc", "observed_at_ms", "source",
                "validation", "structural_fingerprint", "content_fingerprint", "content_changed",
                "structure_changed", "provenance_changed", "total_volume", "positive_volume",
                "reverse_volume", "tariff1_volume", "max_flow", "max_flow_at", "min_flow",
                "min_flow_at", "flow", "max_temperature", "max_temperature_at", "min_temperature",
                "min_temperature_at", "temperature", "external_temperature", "battery_percent",
                "error_flags", "on_time", "operating_time", "unmodeled_semantics"
        };
    }

    private static Reading readReading(Cursor cursor) {
        return new Reading(
                cursor.getLong(0), cursor.getString(1), cursor.getLong(2), nullableString(cursor, 3),
                cursor.getDouble(4), nullableDouble(cursor, 5), nullableDouble(cursor, 6),
                nullableDouble(cursor, 7), nullableDouble(cursor, 8), nullableDouble(cursor, 9),
                nullableInteger(cursor, 10), nullableString(cursor, 11));
    }

    private static ArchiveReading readArchive(Cursor cursor) {
        return new ArchiveReading(
                cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                cursor.getString(4), cursor.getLong(5), cursor.getString(6), cursor.getString(7),
                cursor.getString(8), nullableString(cursor, 9), nullableString(cursor, 10),
                nullableString(cursor, 11), nullableString(cursor, 12), nullableString(cursor, 13),
                nullableString(cursor, 14), nullableString(cursor, 15), nullableString(cursor, 16),
                nullableString(cursor, 17), nullableString(cursor, 18), nullableString(cursor, 19),
                nullableString(cursor, 20), nullableString(cursor, 21), nullableString(cursor, 22),
                nullableString(cursor, 23), nullableString(cursor, 24), nullableString(cursor, 25),
                nullableString(cursor, 26), nullableString(cursor, 27), nullableString(cursor, 28),
                nullableString(cursor, 29), nullableLong(cursor, 30, cursor.getLong(5)),
                cursor.getInt(31), cursor.getInt(32), cursor.getInt(33), cursor.getInt(34),
                cursor.getInt(35), cursor.getInt(36), nullableString(cursor, 37),
                nullableString(cursor, 38), nullableString(cursor, 39), nullableString(cursor, 40));
    }

    private static ArchiveObservation readArchiveObservation(Cursor cursor) {
        return new ArchiveObservation(
                cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getLong(3),
                cursor.getString(4), cursor.getString(5), cursor.getString(6), nullableString(cursor, 7),
                cursor.getInt(8) != 0, cursor.getInt(9) != 0, cursor.getInt(10) != 0,
                nullableString(cursor, 11), nullableString(cursor, 12), nullableString(cursor, 13),
                nullableString(cursor, 14), nullableString(cursor, 15), nullableString(cursor, 16),
                nullableString(cursor, 17), nullableString(cursor, 18), nullableString(cursor, 19),
                nullableString(cursor, 20), nullableString(cursor, 21), nullableString(cursor, 22),
                nullableString(cursor, 23), nullableString(cursor, 24), nullableString(cursor, 25),
                nullableString(cursor, 26), nullableString(cursor, 27), nullableString(cursor, 28),
                nullableString(cursor, 29), nullableString(cursor, 30));
    }

    private static boolean archiveContentMatches(
            ArchiveReading existing, ArchivePeriodSnapshot snapshot) {
        return Objects.equals(existing.totalVolume, snapshot.totalVolume)
                && Objects.equals(existing.positiveVolume, snapshot.positiveVolume)
                && Objects.equals(existing.reverseVolume, snapshot.reverseVolume)
                && Objects.equals(existing.tariff1Volume, snapshot.tariff1Volume)
                && Objects.equals(existing.maxFlow, snapshot.maxFlow)
                && Objects.equals(existing.maxFlowAt, snapshot.maxFlowAt)
                && Objects.equals(existing.minFlow, snapshot.minFlow)
                && Objects.equals(existing.minFlowAt, snapshot.minFlowAt)
                && Objects.equals(existing.flow, snapshot.flow)
                && Objects.equals(existing.maxTemperature, snapshot.maxTemperature)
                && Objects.equals(existing.maxTemperatureAt, snapshot.maxTemperatureAt)
                && Objects.equals(existing.minTemperature, snapshot.minTemperature)
                && Objects.equals(existing.minTemperatureAt, snapshot.minTemperatureAt)
                && Objects.equals(existing.temperature, snapshot.temperature)
                && Objects.equals(existing.externalTemperature, snapshot.externalTemperature)
                && Objects.equals(existing.batteryPercent, snapshot.batteryPercent)
                && Objects.equals(existing.errorFlags, snapshot.errorFlags)
                && Objects.equals(existing.onTime, snapshot.onTime)
                && Objects.equals(existing.operatingTime, snapshot.operatingTime)
                && Objects.equals(existing.unmodeledSemantics,
                serializeTextList(snapshot.unmodeledSemantics));
    }

    private static String contentFingerprint(ArchivePeriodSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digestPart(digest, snapshot.totalVolume);
            digestPart(digest, snapshot.positiveVolume);
            digestPart(digest, snapshot.reverseVolume);
            digestPart(digest, snapshot.tariff1Volume);
            digestPart(digest, snapshot.maxFlow);
            digestPart(digest, snapshot.maxFlowAt);
            digestPart(digest, snapshot.minFlow);
            digestPart(digest, snapshot.minFlowAt);
            digestPart(digest, snapshot.flow);
            digestPart(digest, snapshot.maxTemperature);
            digestPart(digest, snapshot.maxTemperatureAt);
            digestPart(digest, snapshot.minTemperature);
            digestPart(digest, snapshot.minTemperatureAt);
            digestPart(digest, snapshot.temperature);
            digestPart(digest, snapshot.externalTemperature);
            digestPart(digest, snapshot.batteryPercent);
            digestPart(digest, snapshot.errorFlags);
            digestPart(digest, snapshot.onTime);
            digestPart(digest, snapshot.operatingTime);
            digestPart(digest, serializeTextList(snapshot.unmodeledSemantics));
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) out.append(String.format(Locale.US, "%02x", value & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void digestPart(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0xFF);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) ((bytes.length >>> 24) & 0xFF));
        digest.update((byte) ((bytes.length >>> 16) & 0xFF));
        digest.update((byte) ((bytes.length >>> 8) & 0xFF));
        digest.update((byte) (bytes.length & 0xFF));
        digest.update(bytes);
    }

    private static void validateMonthlyArchive(String meterIdentity, MonthlyArchivePeriod period) {
        if (meterIdentity == null || meterIdentity.trim().isEmpty()) {
            throw new IllegalArgumentException("meterIdentity required");
        }
        if (period == null || period.snapshot == null) {
            throw new IllegalArgumentException("Monthly period snapshot required");
        }
        if (period.snapshot.periodType != ArchivePeriodSnapshot.PeriodType.MONTH) {
            throw new IllegalArgumentException("Monthly snapshot required");
        }
        if (period.retrievedAtUtc == null || period.retrievedAtUtc.trim().isEmpty()) {
            throw new IllegalArgumentException("retrievedAtUtc required for persistence");
        }
        if (!period.loggerTimestamp.equals(period.snapshot.loggerDateTime)) {
            throw new IllegalArgumentException("logger timestamp mismatch");
        }
        if (period.snapshot.retrievedAtUtc != null
                && !period.retrievedAtUtc.equals(period.snapshot.retrievedAtUtc)) {
            throw new IllegalArgumentException("retrieval timestamp mismatch");
        }
    }

    private static long parseRetrievedAtUtc(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = format.parse(value);
            if (date == null) throw new IllegalArgumentException("invalid retrievedAtUtc");
            return date.getTime();
        } catch (ParseException error) {
            throw new IllegalArgumentException("invalid retrievedAtUtc: " + value, error);
        }
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullable(ContentValues values, String key, Double value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullable(ContentValues values, String key, Integer value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static String nullableString(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static long nullableLong(Cursor cursor, int index, long fallback) {
        return cursor.isNull(index) ? fallback : cursor.getLong(index);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private static Integer nullableInteger(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static String serializeAlarms(List<MbusParser.MeterAlarm> alarms) {
        if (alarms == null || alarms.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (MbusParser.MeterAlarm alarm : alarms) {
            if (out.length() > 0) out.append(',');
            out.append(alarm.name());
        }
        return out.toString();
    }

    private static String serializeTextList(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(value.replace("\\", "\\\\").replace("\n", "\\n"));
        }
        return out.toString();
    }

    static final class Reading {
        final long id;
        final String meterId;
        final long readAtMs;
        final String meterTime;
        final double totalM3;
        final Double positiveM3;
        final Double negativeM3;
        final Double flowM3h;
        final Double waterTemperatureC;
        final Double ambientTemperatureC;
        final Integer batteryPercent;
        final String alarmCodes;

        Reading(long id, String meterId, long readAtMs, String meterTime, double totalM3,
                Double positiveM3, Double negativeM3, Double flowM3h, Double waterTemperatureC,
                Double ambientTemperatureC, Integer batteryPercent, String alarmCodes) {
            this.id = id;
            this.meterId = meterId;
            this.readAtMs = readAtMs;
            this.meterTime = meterTime;
            this.totalM3 = totalM3;
            this.positiveM3 = positiveM3;
            this.negativeM3 = negativeM3;
            this.flowM3h = flowM3h;
            this.waterTemperatureC = waterTemperatureC;
            this.ambientTemperatureC = ambientTemperatureC;
            this.batteryPercent = batteryPercent;
            this.alarmCodes = alarmCodes == null ? "" : alarmCodes;
        }

        boolean hasAlarms() { return !alarmCodes.isEmpty(); }
    }

    static final class ArchiveReading {
        final long id;
        final String meterId;
        final String archiveFamily;
        final String loggerTimestamp;
        /** Latest chronological retrieval timestamp retained for existing UI/provenance callers. */
        final String retrievedAtUtc;
        final long retrievedAtMs;
        /** Canonical first-observed provenance/content baseline; conflicts do not overwrite it. */
        final String source;
        final String validation;
        final String structuralFingerprint;
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
        final String unmodeledSemantics;
        final String firstRetrievedAtUtc;
        final long firstRetrievedAtMs;
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

        ArchiveReading(long id, String meterId, String archiveFamily, String loggerTimestamp,
                       String retrievedAtUtc, long retrievedAtMs, String source, String validation,
                       String structuralFingerprint, String totalVolume, String positiveVolume,
                       String reverseVolume, String tariff1Volume, String maxFlow, String maxFlowAt,
                       String minFlow, String minFlowAt, String flow, String maxTemperature,
                       String maxTemperatureAt, String minTemperature, String minTemperatureAt,
                       String temperature, String externalTemperature, String batteryPercent,
                       String errorFlags, String onTime, String operatingTime,
                       String unmodeledSemantics, String firstRetrievedAtUtc, long firstRetrievedAtMs,
                       int observationCount, int identicalContentConfirmations,
                       int structuralConfirmations, int provenanceConfirmations, int revisionCount,
                       int conflictFlags, String lastContentFingerprint,
                       String lastStructuralFingerprint, String lastSource, String lastValidation) {
            this.id = id;
            this.meterId = meterId;
            this.archiveFamily = archiveFamily;
            this.loggerTimestamp = loggerTimestamp;
            this.retrievedAtUtc = retrievedAtUtc;
            this.retrievedAtMs = retrievedAtMs;
            this.source = source;
            this.validation = validation;
            this.structuralFingerprint = structuralFingerprint;
            this.totalVolume = totalVolume;
            this.positiveVolume = positiveVolume;
            this.reverseVolume = reverseVolume;
            this.tariff1Volume = tariff1Volume;
            this.maxFlow = maxFlow;
            this.maxFlowAt = maxFlowAt;
            this.minFlow = minFlow;
            this.minFlowAt = minFlowAt;
            this.flow = flow;
            this.maxTemperature = maxTemperature;
            this.maxTemperatureAt = maxTemperatureAt;
            this.minTemperature = minTemperature;
            this.minTemperatureAt = minTemperatureAt;
            this.temperature = temperature;
            this.externalTemperature = externalTemperature;
            this.batteryPercent = batteryPercent;
            this.errorFlags = errorFlags;
            this.onTime = onTime;
            this.operatingTime = operatingTime;
            this.unmodeledSemantics = unmodeledSemantics == null ? "" : unmodeledSemantics;
            this.firstRetrievedAtUtc = firstRetrievedAtUtc == null ? retrievedAtUtc : firstRetrievedAtUtc;
            this.firstRetrievedAtMs = firstRetrievedAtMs;
            this.observationCount = observationCount;
            this.identicalContentConfirmations = identicalContentConfirmations;
            this.structuralConfirmations = structuralConfirmations;
            this.provenanceConfirmations = provenanceConfirmations;
            this.revisionCount = revisionCount;
            this.conflictFlags = conflictFlags;
            this.lastContentFingerprint = lastContentFingerprint;
            this.lastStructuralFingerprint = lastStructuralFingerprint == null
                    ? structuralFingerprint : lastStructuralFingerprint;
            this.lastSource = lastSource == null ? source : lastSource;
            this.lastValidation = lastValidation == null ? validation : lastValidation;
        }

        boolean hasHistoricalErrorFlags() {
            if (errorFlags == null || errorFlags.trim().isEmpty()) return false;
            String normalized = errorFlags.trim().toLowerCase(Locale.US);
            return !("0".equals(normalized) || "0x00000000".equals(normalized));
        }

        boolean contentStayedIdentical() {
            return (conflictFlags & ARCHIVE_CONFLICT_CONTENT) == 0;
        }

        boolean hasContentConflict() {
            return (conflictFlags & ARCHIVE_CONFLICT_CONTENT) != 0;
        }

        boolean hasStructuralConflict() {
            return (conflictFlags & ARCHIVE_CONFLICT_STRUCTURE) != 0;
        }

        boolean hasProvenanceConflict() {
            return (conflictFlags & ARCHIVE_CONFLICT_PROVENANCE) != 0;
        }
    }

    static final class ArchiveObservation {
        final long id;
        final long archivePeriodId;
        final String observedAtUtc;
        final long observedAtMs;
        final String source;
        final String validation;
        final String structuralFingerprint;
        final String contentFingerprint;
        final boolean contentChanged;
        final boolean structureChanged;
        final boolean provenanceChanged;
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
        final String unmodeledSemantics;

        ArchiveObservation(long id, long archivePeriodId, String observedAtUtc, long observedAtMs,
                           String source, String validation, String structuralFingerprint,
                           String contentFingerprint, boolean contentChanged, boolean structureChanged,
                           boolean provenanceChanged, String totalVolume, String positiveVolume,
                           String reverseVolume, String tariff1Volume, String maxFlow, String maxFlowAt,
                           String minFlow, String minFlowAt, String flow, String maxTemperature,
                           String maxTemperatureAt, String minTemperature, String minTemperatureAt,
                           String temperature, String externalTemperature, String batteryPercent,
                           String errorFlags, String onTime, String operatingTime,
                           String unmodeledSemantics) {
            this.id = id;
            this.archivePeriodId = archivePeriodId;
            this.observedAtUtc = observedAtUtc;
            this.observedAtMs = observedAtMs;
            this.source = source;
            this.validation = validation;
            this.structuralFingerprint = structuralFingerprint;
            this.contentFingerprint = contentFingerprint;
            this.contentChanged = contentChanged;
            this.structureChanged = structureChanged;
            this.provenanceChanged = provenanceChanged;
            this.totalVolume = totalVolume;
            this.positiveVolume = positiveVolume;
            this.reverseVolume = reverseVolume;
            this.tariff1Volume = tariff1Volume;
            this.maxFlow = maxFlow;
            this.maxFlowAt = maxFlowAt;
            this.minFlow = minFlow;
            this.minFlowAt = minFlowAt;
            this.flow = flow;
            this.maxTemperature = maxTemperature;
            this.maxTemperatureAt = maxTemperatureAt;
            this.minTemperature = minTemperature;
            this.minTemperatureAt = minTemperatureAt;
            this.temperature = temperature;
            this.externalTemperature = externalTemperature;
            this.batteryPercent = batteryPercent;
            this.errorFlags = errorFlags;
            this.onTime = onTime;
            this.operatingTime = operatingTime;
            this.unmodeledSemantics = unmodeledSemantics == null ? "" : unmodeledSemantics;
        }
    }
}
