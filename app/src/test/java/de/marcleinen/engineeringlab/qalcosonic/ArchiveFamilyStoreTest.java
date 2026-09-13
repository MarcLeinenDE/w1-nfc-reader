package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveFamilyStoreTest {
    private Context context;
    private ArchiveFamilyStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        store = new ArchiveFamilyStore(context);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
    }

    @Test public void sameTimestampAcrossFamiliesRemainsThreeCanonicalPeriods() {
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.HOUR, "2026-08-01 00:00", "1.000 m3", "h")));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.DAY, "2026-08-01 00:00", "2.000 m3", "d")));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH, "2026-08-01 00:00", "3.000 m3", "m")));

        List<ArchiveFamilyStore.StoredPeriod> rows = store.getPeriods("M1", null);
        assertEquals(3, rows.size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.HOUR).size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.DAY).size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.MONTH).size());
    }

    @Test public void repeatedRawTimestampWithDistinctOnTimeCreatesDistinctOccurrences() {
        String timestamp = "2026-10-25 02:00";
        ArchiveFamilyPeriod first = periodWithOnTime(ArchiveFamilyPeriod.Family.HOUR,
                timestamp, "100.000 m3", "100000 s", "dst-a");
        ArchiveFamilyPeriod second = periodWithOnTime(ArchiveFamilyPeriod.Family.HOUR,
                timestamp, "100.100 m3", "103600 s", "dst-b");

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", first));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", second));

        List<ArchiveFamilyStore.StoredPeriod> rows = store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR);
        assertEquals(2, rows.size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.HOUR).size());
        assertEquals("OT:100000", rows.get(0).occurrenceKey);
        assertEquals(Long.valueOf(100000L), rows.get(0).onTimeSeconds);
        assertEquals("OT:103600", rows.get(1).occurrenceKey);
        assertEquals(Long.valueOf(103600L), rows.get(1).onTimeSeconds);
    }

    @Test public void v1MigrationPreservesIdsCountersConflictsAndPromotesExactOnTime() {
        store.close();
        store = null;
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        createLegacyV1Database();

        store = new ArchiveFamilyStore(context);
        assertEquals(ArchiveFamilyStore.DB_VERSION, store.getReadableDatabase().getVersion());
        List<ArchiveFamilyStore.StoredPeriod> rows = store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR);
        assertEquals(1, rows.size());
        ArchiveFamilyStore.StoredPeriod row = rows.get(0);
        assertEquals(41L, row.id);
        assertEquals("OT:100000", row.occurrenceKey);
        assertEquals(Long.valueOf(100000L), row.onTimeSeconds);
        assertNull(row.rawTypeFHex);
        assertNull(row.typeFIv);
        assertNull(row.typeFSu);
        assertEquals(7, row.observationCount);
        assertEquals(4, row.identicalContentConfirmations);
        assertEquals(2, row.structuralConfirmations);
        assertEquals(3, row.provenanceConfirmations);
        assertEquals(1, row.revisionCount);
        assertEquals(ArchiveFamilyStore.CONFLICT_CONTENT, row.conflictFlags);
        assertEquals(1, store.conflictCount("M1", ArchiveFamilyPeriod.Family.HOUR, "2026-10-25 02:00"));

        try (Cursor c = store.getReadableDatabase().query(ArchiveFamilyStore.TABLE_CONFLICTS,
                new String[]{"id", "archive_period_id", "occurrence_key", "on_time_seconds"},
                null, null, null, null, null)) {
            assertTrue(c.moveToFirst());
            assertEquals(9L, c.getLong(0));
            assertEquals(41L, c.getLong(1));
            assertEquals("OT:100000", c.getString(2));
            assertEquals(100000L, c.getLong(3));
        }

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", periodWithOnTime(ArchiveFamilyPeriod.Family.HOUR,
                        "2026-10-25 02:00", "101.000 m3", "103600 s", "second-occurrence")));
        assertEquals(2, store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR).size());
    }

    @Test public void presentationNumberDoesNotTreatUnitSuffixDigitAsMeasurement() {
        assertEquals("0", ArchiveFamilyStore.measurementNumber("0 m3"));
        assertEquals("203.518", ArchiveFamilyStore.measurementNumber("203.518 m3"));
        assertEquals("1.931", ArchiveFamilyStore.measurementNumber("1,931m3"));

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH,
                        "2024-09-01 00:00", "0 m3", "zero")));
        assertEquals("0", store.getPeriods("M1", ArchiveFamilyPeriod.Family.MONTH).get(0).totalVolume);
    }

    @Test public void identicalResyncAggregatesConfirmationWithoutConflictRows() {
        ArchiveFamilyPeriod first = period(ArchiveFamilyPeriod.Family.MONTH,
                "2026-08-01 00:00", "196.668 m3", "custom-A");
        ArchiveFamilyPeriod second = new ArchiveFamilyPeriod(first.family, first.loggerTimestamp,
                "2026-08-31T13:00:00Z", first.structuralFingerprint, first.source,
                first.validation, first.values);
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", first));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL, store.upsert("M1", second));

        ArchiveFamilyStore.StoredPeriod row = store.getPeriods("M1", ArchiveFamilyPeriod.Family.MONTH).get(0);
        assertEquals(2, row.observationCount);
        assertEquals(1, row.identicalContentConfirmations);
        assertEquals(0, row.revisionCount);
        assertEquals(0, store.conflictCount("M1", ArchiveFamilyPeriod.Family.MONTH, first.loggerTimestamp));
        assertEquals("42", row.extraValues.get("future:custom-A"));
    }

    @Test public void changedFutureExtraValueIsConflictAndCanonicalEvidenceStays() {
        ArchiveFamilyPeriod first = period(ArchiveFamilyPeriod.Family.HOUR,
                "2026-08-31 12:00", "200.000 m3", "x");
        ArchiveNormalizedValues changedValues = ArchiveNormalizedValues.builder()
                .totalVolume("200.000 m3")
                .extra("future:x", "99")
                .build();
        ArchiveFamilyPeriod changed = new ArchiveFamilyPeriod(ArchiveFamilyPeriod.Family.HOUR,
                "2026-08-31 12:00", "2026-08-31T13:00:00Z", "FP-HOUR",
                "NFC_ARCHIVE", "COMPLETE", changedValues);

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", first));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED, store.upsert("M1", changed));
        ArchiveFamilyStore.StoredPeriod canonical = store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals("42", canonical.extraValues.get("future:x"));
        assertEquals(2, canonical.observationCount);
        assertEquals(1, canonical.revisionCount);
        assertTrue((canonical.conflictFlags & ArchiveFamilyStore.CONFLICT_CONTENT) != 0);
        assertEquals(1, store.conflictCount("M1", ArchiveFamilyPeriod.Family.HOUR, "2026-08-31 12:00"));
    }

    private void createLegacyV1Database() {
        SQLiteDatabase db = context.openOrCreateDatabase(ArchiveFamilyStore.DB_NAME, Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE archive_periods ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,meter_id TEXT NOT NULL,archive_family TEXT NOT NULL,"
                + "logger_timestamp TEXT NOT NULL,logger_time_basis TEXT NOT NULL,retrieved_at_utc TEXT NOT NULL,"
                + "retrieved_at_ms INTEGER NOT NULL,first_retrieved_at_utc TEXT NOT NULL,first_retrieved_at_ms INTEGER NOT NULL,"
                + "source TEXT NOT NULL,validation TEXT NOT NULL,structural_fingerprint TEXT NOT NULL,content_fingerprint TEXT NOT NULL,"
                + "observation_count INTEGER NOT NULL DEFAULT 1,identical_content_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "structural_confirmations INTEGER NOT NULL DEFAULT 0,provenance_confirmations INTEGER NOT NULL DEFAULT 0,"
                + "revision_count INTEGER NOT NULL DEFAULT 0,conflict_flags INTEGER NOT NULL DEFAULT 0,"
                + "last_content_fingerprint TEXT NOT NULL,last_structural_fingerprint TEXT NOT NULL,last_source TEXT NOT NULL,"
                + "last_validation TEXT NOT NULL,total_volume TEXT,positive_volume TEXT,reverse_volume TEXT,tariff1_volume TEXT,"
                + "max_flow TEXT,max_flow_at TEXT,min_flow TEXT,min_flow_at TEXT,flow TEXT,max_temperature TEXT,max_temperature_at TEXT,"
                + "min_temperature TEXT,min_temperature_at TEXT,temperature TEXT,external_temperature TEXT,battery_percent TEXT,"
                + "error_flags TEXT,on_time TEXT,operating_time TEXT,extra_values TEXT NOT NULL DEFAULT '',"
                + "UNIQUE(meter_id, archive_family, logger_timestamp))");
        db.execSQL("CREATE INDEX idx_archive_family_logger ON archive_periods (meter_id, archive_family, logger_timestamp DESC)");
        db.execSQL("CREATE INDEX idx_archive_all_logger ON archive_periods (meter_id, logger_timestamp DESC, archive_family)");
        db.execSQL("CREATE TABLE archive_conflicts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,archive_period_id INTEGER NOT NULL,observed_at_utc TEXT NOT NULL,"
                + "observed_at_ms INTEGER NOT NULL,source TEXT NOT NULL,validation TEXT NOT NULL,structural_fingerprint TEXT NOT NULL,"
                + "content_fingerprint TEXT NOT NULL,content_changed INTEGER NOT NULL,structure_changed INTEGER NOT NULL,"
                + "provenance_changed INTEGER NOT NULL,total_volume TEXT,positive_volume TEXT,reverse_volume TEXT,tariff1_volume TEXT,"
                + "max_flow TEXT,max_flow_at TEXT,min_flow TEXT,min_flow_at TEXT,flow TEXT,max_temperature TEXT,max_temperature_at TEXT,"
                + "min_temperature TEXT,min_temperature_at TEXT,temperature TEXT,external_temperature TEXT,battery_percent TEXT,"
                + "error_flags TEXT,on_time TEXT,operating_time TEXT,extra_values TEXT NOT NULL DEFAULT '',"
                + "FOREIGN KEY(archive_period_id) REFERENCES archive_periods(id))");
        db.execSQL("CREATE INDEX idx_archive_conflict_period_time ON archive_conflicts (archive_period_id, observed_at_ms, id)");

        ContentValues period = new ContentValues();
        period.put("id", 41); period.put("meter_id", "M1"); period.put("archive_family", "HOUR");
        period.put("logger_timestamp", "2026-10-25 02:00"); period.put("logger_time_basis", "METER_LOCAL");
        period.put("retrieved_at_utc", "2026-10-25T03:30:00Z"); period.put("retrieved_at_ms", 1792899000000L);
        period.put("first_retrieved_at_utc", "2026-10-25T02:30:00Z"); period.put("first_retrieved_at_ms", 1792895400000L);
        period.put("source", "NFC_ARCHIVE"); period.put("validation", "COMPLETE");
        period.put("structural_fingerprint", "FP-HOUR"); period.put("content_fingerprint", "CFP-A");
        period.put("observation_count", 7); period.put("identical_content_confirmations", 4);
        period.put("structural_confirmations", 2); period.put("provenance_confirmations", 3);
        period.put("revision_count", 1); period.put("conflict_flags", ArchiveFamilyStore.CONFLICT_CONTENT);
        period.put("last_content_fingerprint", "CFP-B"); period.put("last_structural_fingerprint", "FP-HOUR");
        period.put("last_source", "NFC_ARCHIVE"); period.put("last_validation", "COMPLETE");
        period.put("total_volume", "100.000 m3"); period.put("on_time", "100000 s"); period.put("extra_values", "");
        db.insertOrThrow("archive_periods", null, period);

        ContentValues conflict = new ContentValues();
        conflict.put("id", 9); conflict.put("archive_period_id", 41);
        conflict.put("observed_at_utc", "2026-10-25T03:30:00Z"); conflict.put("observed_at_ms", 1792899000000L);
        conflict.put("source", "NFC_ARCHIVE"); conflict.put("validation", "COMPLETE");
        conflict.put("structural_fingerprint", "FP-HOUR"); conflict.put("content_fingerprint", "CFP-B");
        conflict.put("content_changed", 1); conflict.put("structure_changed", 0); conflict.put("provenance_changed", 0);
        conflict.put("total_volume", "100.001 m3"); conflict.put("on_time", "100000 s"); conflict.put("extra_values", "");
        db.insertOrThrow("archive_conflicts", null, conflict);
        db.setVersion(1);
        db.close();
    }

    private static ArchiveFamilyPeriod period(ArchiveFamilyPeriod.Family family, String timestamp,
                                               String total, String extraName) {
        ArchiveNormalizedValues values = ArchiveNormalizedValues.builder()
                .totalVolume(total)
                .batteryPercent("91 %")
                .extra("future:" + extraName, "42")
                .build();
        return new ArchiveFamilyPeriod(family, timestamp, "2026-08-31T12:00:00Z",
                family == ArchiveFamilyPeriod.Family.HOUR ? "FP-HOUR" : "FP-" + family.name(),
                "NFC_ARCHIVE", "COMPLETE", values);
    }

    private static ArchiveFamilyPeriod periodWithOnTime(ArchiveFamilyPeriod.Family family, String timestamp,
                                                         String total, String onTime, String extraName) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder()
                .totalVolume(total)
                .batteryPercent("91 %")
                .extra("future:" + extraName, "42");
        values.onTime = onTime;
        return new ArchiveFamilyPeriod(family, timestamp, "2026-08-31T12:00:00Z",
                family == ArchiveFamilyPeriod.Family.HOUR ? "FP-HOUR" : "FP-" + family.name(),
                "NFC_ARCHIVE", "COMPLETE", values.build());
    }
}
