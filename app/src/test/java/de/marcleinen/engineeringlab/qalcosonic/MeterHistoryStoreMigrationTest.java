package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class MeterHistoryStoreMigrationTest {
    private static final String MONTHLY_FP =
            "2bce7a12cfee64cf8f255093af4e76b8d437b367f6cfbbc183e414bcad54e382";

    private Context context;
    private MeterHistoryStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
    }

    @After
    public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
    }

    @Test
    public void v1ToV3PreservesLiveHistoryAndAddsArchiveTables() {
        createLegacyV1DatabaseWithReading();

        store = new MeterHistoryStore(context);
        assertEquals(3, store.getWritableDatabase().getVersion());

        List<MeterHistoryStore.Reading> live = store.getReadings("METER-OLD", 0L);
        assertEquals(1, live.size());
        assertEquals(123456789L, live.get(0).readAtMs);
        assertEquals(42.125, live.get(0).totalM3, 0.000001);
        assertEquals("2026-08-31 12:00", live.get(0).meterTime);
        assertEquals(Integer.valueOf(91), live.get(0).batteryPercent);
        assertTrue(store.getKnownMonthlyTimestamps("METER-OLD").isEmpty());

        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsertMonthlyArchive("METER-OLD", monthlyPeriod("2026-08-31T12:23:55Z")));
        List<MeterHistoryStore.ArchiveReading> archive =
                store.getMonthlyArchivePeriods("METER-OLD", null);
        assertEquals(1, archive.size());
        assertEquals("2026-08-01 00:00", archive.get(0).loggerTimestamp);
        assertEquals("196.668 m3", archive.get(0).totalVolume);
        assertEquals("0x00000000", archive.get(0).errorFlags);
        assertEquals(1, archive.get(0).observationCount);
        assertEquals(1, store.getMonthlyArchiveObservations(
                "METER-OLD", "2026-08-01 00:00").size());
    }

    @Test
    public void v2ToV3BackfillsExistingArchiveAsFirstObservation() {
        createLegacyV2DatabaseWithArchive();

        store = new MeterHistoryStore(context);
        assertEquals(3, store.getWritableDatabase().getVersion());

        MeterHistoryStore.ArchiveReading archive =
                store.getMonthlyArchivePeriods("METER-V2", null).get(0);
        assertEquals("2026-08-31T12:23:55Z", archive.firstRetrievedAtUtc);
        assertEquals(1788179035000L, archive.firstRetrievedAtMs);
        assertEquals(1, archive.observationCount);
        assertEquals(0, archive.identicalContentConfirmations);
        assertEquals(0, archive.revisionCount);
        assertTrue(archive.contentStayedIdentical());
        assertEquals(MONTHLY_FP, archive.lastStructuralFingerprint);
        assertEquals(MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE, archive.lastSource);
        assertEquals(MonthlyArchivePeriod.VALIDATION_COMPLETE, archive.lastValidation);

        List<MeterHistoryStore.ArchiveObservation> observations =
                store.getMonthlyArchiveObservations("METER-V2", "2026-08-01 00:00");
        assertEquals(1, observations.size());
        assertEquals("2026-08-31T12:23:55Z", observations.get(0).observedAtUtc);
        assertEquals("196.668 m3", observations.get(0).totalVolume);
        assertFalse(observations.get(0).contentChanged);
        assertFalse(observations.get(0).structureChanged);
        assertFalse(observations.get(0).provenanceChanged);
    }

    @Test
    public void repeatedIdenticalObservationBecomesConfirmationWithoutDuplicateCanonicalRow() {
        store = new MeterHistoryStore(context);

        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsertMonthlyArchive("METER-1", monthlyPeriod("2026-08-31T12:23:55Z")));
        MeterHistoryStore.ArchiveReading first =
                store.getMonthlyArchivePeriods("METER-1", null).get(0);

        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL,
                store.upsertMonthlyArchive("METER-1", monthlyPeriod("2026-08-31T13:23:55Z")));
        List<MeterHistoryStore.ArchiveReading> rows =
                store.getMonthlyArchivePeriods("METER-1", null);

        assertEquals(1, rows.size());
        MeterHistoryStore.ArchiveReading row = rows.get(0);
        assertEquals(first.id, row.id);
        assertEquals("2026-08-31T12:23:55Z", row.firstRetrievedAtUtc);
        assertEquals("2026-08-31T13:23:55Z", row.retrievedAtUtc);
        assertEquals(2, row.observationCount);
        assertEquals(1, row.identicalContentConfirmations);
        assertEquals(1, row.structuralConfirmations);
        assertEquals(1, row.provenanceConfirmations);
        assertEquals(0, row.revisionCount);
        assertTrue(row.contentStayedIdentical());
        assertNotNull(row.lastContentFingerprint);
        assertEquals(2, store.getMonthlyArchiveObservations(
                "METER-1", "2026-08-01 00:00").size());
        assertEquals(1, store.getKnownMonthlyTimestamps("METER-1").size());
    }

    @Test
    public void changedNormalizedContentRecordsConflictWithoutOverwritingCanonicalEvidence() {
        store = new MeterHistoryStore(context);

        MonthlyArchivePeriod firstPeriod = monthlyPeriod("2026-08-31T12:23:55Z");
        MonthlyArchivePeriod changedPeriod = monthlyPeriod("2026-08-31T13:23:55Z", true);
        assertNotEquals(firstPeriod.snapshot.totalVolume, changedPeriod.snapshot.totalVolume);

        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsertMonthlyArchive("METER-1", firstPeriod));
        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED,
                store.upsertMonthlyArchive("METER-1", changedPeriod));

        MeterHistoryStore.ArchiveReading row =
                store.getMonthlyArchivePeriods("METER-1", null).get(0);
        assertEquals(firstPeriod.snapshot.totalVolume, row.totalVolume);
        assertNotEquals(changedPeriod.snapshot.totalVolume, row.totalVolume);
        assertEquals(2, row.observationCount);
        assertEquals(0, row.identicalContentConfirmations);
        assertEquals(1, row.structuralConfirmations);
        assertEquals(1, row.provenanceConfirmations);
        assertEquals(1, row.revisionCount);
        assertFalse(row.contentStayedIdentical());
        assertTrue(row.hasContentConflict());
        assertFalse(row.hasStructuralConflict());
        assertFalse(row.hasProvenanceConflict());

        List<MeterHistoryStore.ArchiveObservation> observations =
                store.getMonthlyArchiveObservations("METER-1", "2026-08-01 00:00");
        assertEquals(2, observations.size());
        assertEquals(firstPeriod.snapshot.totalVolume, observations.get(0).totalVolume);
        assertEquals(changedPeriod.snapshot.totalVolume, observations.get(1).totalVolume);
        assertTrue(observations.get(1).contentChanged);
        assertFalse(observations.get(1).structureChanged);
        assertFalse(observations.get(1).provenanceChanged);
    }

    @Test
    public void meterEnumerationAndDeleteCoverLiveArchiveAndObservationTables() {
        createLegacyV1DatabaseWithReading();
        store = new MeterHistoryStore(context);
        store.getWritableDatabase();
        assertEquals(MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsertMonthlyArchive("METER-ARCHIVE", monthlyPeriod("2026-08-31T13:23:55Z")));
        assertEquals(1, store.getMonthlyArchiveObservations(
                "METER-ARCHIVE", "2026-08-01 00:00").size());

        List<String> meters = store.getMeterIds();
        assertEquals(2, meters.size());
        assertTrue(meters.contains("METER-OLD"));
        assertTrue(meters.contains("METER-ARCHIVE"));

        assertEquals(1, store.deleteMeter("METER-ARCHIVE"));
        assertTrue(store.getMonthlyArchivePeriods("METER-ARCHIVE", null).isEmpty());
        assertTrue(store.getMonthlyArchiveObservations(
                "METER-ARCHIVE", "2026-08-01 00:00").isEmpty());
        assertEquals(1, store.getReadings("METER-OLD", 0L).size());

        assertNotEquals(0, store.deleteAll());
        assertTrue(store.getMeterIds().isEmpty());
    }

    private void createLegacyV1DatabaseWithReading() {
        SQLiteDatabase db = context.openOrCreateDatabase(
                MeterHistoryStore.DB_NAME, Context.MODE_PRIVATE, null);
        createLegacyReadingsSchema(db);

        ContentValues values = new ContentValues();
        values.put("meter_id", "METER-OLD");
        values.put("read_at_ms", 123456789L);
        values.put("meter_time", "2026-08-31 12:00");
        values.put("total_m3", 42.125);
        values.put("positive_m3", 42.125);
        values.put("negative_m3", 0.0);
        values.put("flow_m3h", 0.25);
        values.put("water_temp_c", 20.5);
        values.put("ambient_temp_c", 21.0);
        values.put("battery_percent", 91);
        values.put("alarm_codes", "");
        db.insertOrThrow("readings", null, values);
        db.setVersion(1);
        db.close();
    }

    private void createLegacyV2DatabaseWithArchive() {
        SQLiteDatabase db = context.openOrCreateDatabase(
                MeterHistoryStore.DB_NAME, Context.MODE_PRIVATE, null);
        createLegacyReadingsSchema(db);
        db.execSQL("CREATE TABLE archive_periods ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "archive_family TEXT NOT NULL,"
                + "logger_timestamp TEXT NOT NULL,"
                + "retrieved_at_utc TEXT NOT NULL,"
                + "retrieved_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "structural_fingerprint TEXT NOT NULL,"
                + "total_volume TEXT,positive_volume TEXT,reverse_volume TEXT,tariff1_volume TEXT,"
                + "max_flow TEXT,max_flow_at TEXT,min_flow TEXT,min_flow_at TEXT,flow TEXT,"
                + "max_temperature TEXT,max_temperature_at TEXT,min_temperature TEXT,min_temperature_at TEXT,"
                + "temperature TEXT,external_temperature TEXT,battery_percent TEXT,error_flags TEXT,"
                + "on_time TEXT,operating_time TEXT,unmodeled_semantics TEXT,"
                + "UNIQUE(meter_id, archive_family, logger_timestamp))");
        db.execSQL("CREATE INDEX idx_archive_meter_logger ON archive_periods "
                + "(meter_id, archive_family, logger_timestamp DESC)");
        db.execSQL("CREATE INDEX idx_archive_meter_retrieved ON archive_periods "
                + "(meter_id, retrieved_at_ms DESC)");

        MonthlyArchivePeriod period = monthlyPeriod("2026-08-31T12:23:55Z");
        ArchivePeriodSnapshot s = period.snapshot;
        ContentValues values = new ContentValues();
        values.put("meter_id", "METER-V2");
        values.put("archive_family", MeterHistoryStore.ARCHIVE_FAMILY_MONTH);
        values.put("logger_timestamp", period.loggerTimestamp);
        values.put("retrieved_at_utc", period.retrievedAtUtc);
        values.put("retrieved_at_ms", 1788179035000L);
        values.put("source", period.source);
        values.put("validation", period.validation);
        values.put("structural_fingerprint", period.structuralFingerprint);
        put(values, "total_volume", s.totalVolume);
        put(values, "positive_volume", s.positiveVolume);
        put(values, "reverse_volume", s.reverseVolume);
        put(values, "tariff1_volume", s.tariff1Volume);
        put(values, "max_flow", s.maxFlow);
        put(values, "max_flow_at", s.maxFlowAt);
        put(values, "min_flow", s.minFlow);
        put(values, "min_flow_at", s.minFlowAt);
        put(values, "flow", s.flow);
        put(values, "max_temperature", s.maxTemperature);
        put(values, "max_temperature_at", s.maxTemperatureAt);
        put(values, "min_temperature", s.minTemperature);
        put(values, "min_temperature_at", s.minTemperatureAt);
        put(values, "temperature", s.temperature);
        put(values, "external_temperature", s.externalTemperature);
        put(values, "battery_percent", s.batteryPercent);
        put(values, "error_flags", s.errorFlags);
        put(values, "on_time", s.onTime);
        put(values, "operating_time", s.operatingTime);
        values.put("unmodeled_semantics", "");
        db.insertOrThrow("archive_periods", null, values);
        db.setVersion(2);
        db.close();
    }

    private static void createLegacyReadingsSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE readings ("
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
        db.execSQL("CREATE INDEX idx_readings_meter_time ON readings (meter_id, read_at_ms DESC)");
    }

    private static void put(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static MonthlyArchivePeriod monthlyPeriod(String retrievedAtUtc) {
        return monthlyPeriod(retrievedAtUtc, false);
    }

    private static MonthlyArchivePeriod monthlyPeriod(String retrievedAtUtc, boolean changedVolume) {
        byte[] records = realCapturePatternRecords();
        if (changedVolume) records[27] = (byte) ((records[27] & 0xFF) + 1);
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(records));
        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                ArchivePeriodSnapshot.PeriodType.MONTH, inspection, retrievedAtUtc);
        return new MonthlyArchivePeriod(
                snapshot.loggerDateTime,
                retrievedAtUtc,
                MONTHLY_FP,
                snapshot);
    }

    private static byte[] realCapturePatternRecords() {
        return bytes(
                0x04, 0x6D, 0x00, 0x00, 0x41, 0x38,
                0x04, 0x20, 0xC8, 0x66, 0x9F, 0x04,
                0x34, 0xFD, 0x17, 0x00, 0x00, 0x00, 0x00,
                0x04, 0x24, 0x80, 0xDE, 0x28, 0x00,
                0x04, 0x13, 0x3C, 0x00, 0x03, 0x00,
                0x04, 0x93, 0x3B, 0x3C, 0x00, 0x03, 0x00,
                0x04, 0x93, 0x3C, 0x01, 0x00, 0x00, 0x00,
                0x81, 0x10, 0x13, 0x12,
                0x12, 0x3B, 0xBF, 0x03,
                0x14, 0xBB, 0x6D, 0x19, 0x10, 0x5C, 0x37,
                0x22, 0x3B, 0x00, 0x00,
                0x24, 0xBB, 0x6D, 0x00, 0x00, 0x5F, 0x37,
                0x02, 0x3B, 0x08, 0x00,
                0x12, 0x59, 0x5B, 0x09,
                0x14, 0xD9, 0x6D, 0x1B, 0x05, 0x54, 0x37,
                0x22, 0x59, 0x52, 0x07,
                0x24, 0xD9, 0x6D, 0x2E, 0x12, 0x4C, 0x37,
                0x02, 0x59, 0xC4, 0x08,
                0x02, 0x66, 0xDC, 0x00,
                0x02, 0xFD, 0x74, 0x59, 0x00);
    }

    private static byte[] ci72Frame(byte[] records) {
        int l = 15 + records.length;
        byte[] frame = new byte[l + 6];
        frame[0] = 0x68;
        frame[1] = (byte) l;
        frame[2] = (byte) l;
        frame[3] = 0x68;
        frame[4] = 0x08;
        frame[5] = (byte) 0xFE;
        frame[6] = 0x72;
        frame[13] = 0x10;
        frame[14] = 0x07;
        System.arraycopy(records, 0, frame, 19, records.length);
        int checksumIndex = 4 + l;
        int checksum = 0;
        for (int i = 4; i < checksumIndex; i++) checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        frame[checksumIndex] = (byte) checksum;
        frame[checksumIndex + 1] = 0x16;
        return frame;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
