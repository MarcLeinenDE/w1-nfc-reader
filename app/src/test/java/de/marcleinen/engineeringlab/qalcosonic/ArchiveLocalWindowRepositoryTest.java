package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveLocalWindowRepositoryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @Test public void realStoresSelectShiftedLocalHistoryAndContainedStatistics() {
        insertArchive(1L, "2026-09-09 08:00", 35_300L);
        insertArchive(2L, "2026-09-09 09:00", 38_900L);
        insertArchive(3L, "2026-09-09 10:00", 42_500L);
        insertArchive(4L, "2026-09-09 11:00", 46_100L);
        insertArchive(5L, "2026-09-09 12:00", 49_700L);

        long center = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli();
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    "M1",
                    "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    center));
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            "M1",
                            center - 100L,
                            center + 100L,
                            "2026-09-09 14:00",
                            "04 6D 00 00",
                            false,
                            false,
                            50_000L),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        }

        LocalDateTime start = LocalDateTime.of(2026, 9, 9, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 9, 13, 0);
        try (ArchiveUtcRepository repository = new ArchiveUtcRepository(context)) {
            ArchiveLocalWindowSelection.Result history = repository.selectLocal(
                    "M1",
                    ArchiveFamilyPeriod.Family.HOUR,
                    start,
                    end,
                    null,
                    null,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
            ArchiveLocalWindowSelection.Result statistics = repository.selectLocal(
                    "M1",
                    ArchiveFamilyPeriod.Family.HOUR,
                    start,
                    end,
                    null,
                    null,
                    ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

            assertTrue(history.windowResolved());
            assertEquals(UtcCoverage.Status.PARTIAL_EDGES, history.coverage.status);
            assertEquals(4, history.periods.size());
            assertEquals(2, statistics.periods.size());
            assertEquals("OT:38900", statistics.periods.get(0).source.occurrenceKey);
            assertEquals("OT:42500", statistics.periods.get(1).source.occurrenceKey);
        }
    }

    @Test public void missingStoredMeterZoneFailsClosedEvenWhenPhoneHasAZone() {
        insertArchive(1L, "2026-09-09 10:00", 42_500L);

        try (ArchiveUtcRepository repository = new ArchiveUtcRepository(context)) {
            ArchiveLocalWindowSelection.Result result = repository.selectLocal(
                    "M1",
                    ArchiveFamilyPeriod.Family.HOUR,
                    LocalDateTime.of(2026, 9, 9, 10, 0),
                    LocalDateTime.of(2026, 9, 9, 11, 0),
                    null,
                    null,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(LocalTimeWindowResolver.Status.ZONE_MISSING, result.window.status);
            assertTrue(result.periods.isEmpty());
        }
    }

    private void insertArchive(long id, String timestamp, long onTimeSeconds) {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", ArchiveFamilyPeriod.Family.HOUR.name());
            values.put("logger_timestamp", timestamp);
            values.put("occurrence_key", "OT:" + onTimeSeconds);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTimeSeconds);
            values.put("raw_type_f_hex", "04 6D 00 00");
            values.put("type_f_iv", 0);
            values.put("type_f_su", 1);
            values.put("retrieved_at_utc", "2026-09-09T12:00:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-09-09T12:00:00Z");
            values.put("first_retrieved_at_ms", 1L);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "structure");
            values.put("content_fingerprint", "content-" + id);
            values.put("last_content_fingerprint", "content-" + id);
            values.put("last_structural_fingerprint", "structure");
            values.put("last_source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("last_validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("total_volume", 100.0 + id);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    ArchiveFamilyStore.TABLE_PERIODS,
                    null,
                    values);
            assertTrue(inserted > 0L);
        }
    }
}
