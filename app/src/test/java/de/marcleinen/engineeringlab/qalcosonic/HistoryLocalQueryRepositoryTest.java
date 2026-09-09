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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLocalQueryRepositoryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void localSelectionJoinsBackToOccurrenceSafeMeasurementRows() {
        insertArchive(1L, "2026-09-09 08:00", 35_300L, 101.0);
        insertArchive(2L, "2026-09-09 09:00", 38_900L, 102.0);
        insertArchive(3L, "2026-09-09 10:00", 42_500L, 103.0);
        insertArchive(4L, "2026-09-09 11:00", 46_100L, 104.0);
        insertArchive(5L, "2026-09-09 12:00", 49_700L, 105.0);
        installTimeModel();

        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 10:00",
                "2026-09-09 13:00",
                1L,
                2L,
                0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result history = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    window,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
            HistoryLocalQueryRepository.Result statistics = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    window,
                    ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

            assertTrue(history.boundedWindow);
            assertEquals(4, history.rows.size());
            assertEquals(102.0, history.rows.get(0).observation.totalM3, 0.000001);
            assertTrue(history.rows.get(0).observation.identity.endsWith("|OT:38900"));
            assertTrue(history.rows.get(3).observation.identity.endsWith("|OT:49700"));
            assertNotNull(HistoryResolvedTimeToken.parse(history.rows.get(0).observation.timestamp));
            assertEquals("2026-09-09 09:00", history.rows.get(0).observation.meterTime);
            assertEquals(UtcCoverage.Status.PARTIAL_EDGES,
                    history.meterStates.get(0).coverageStatus);

            assertEquals(2, statistics.rows.size());
            assertTrue(statistics.rows.get(0).observation.identity.endsWith("|OT:42500"));
            assertTrue(statistics.rows.get(1).observation.identity.endsWith("|OT:46100"));
            assertTrue(statistics.rows.get(0).time.fullyContained);
            assertTrue(statistics.rows.get(1).time.fullyContained);

            List<HistoryStatisticsRepository.Observation> analyticsRows = statistics.observations();
            assertEquals(3, analyticsRows.size());
            assertTrue(analyticsRows.get(0).contextOnly);
            HistoryStatisticsAnalytics.ConsumptionSummary consumption =
                    HistoryStatisticsAnalytics.consumption(analyticsRows);
            assertEquals(2, consumption.availableBuckets);
            assertEquals(2.0, consumption.total, 0.000001);
        }
    }

    @Test public void allPeriodsUsesResolvedUtcTimelineInsteadOfRawLoggerSort() {
        insertArchive(1L, "2026-09-09 08:00", 35_300L, 101.0);
        insertArchive(2L, "2026-09-09 09:00", 38_900L, 102.0);
        insertArchive(3L, "2026-09-09 10:00", 42_500L, 103.0);
        installTimeModel();
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.YEAR);
        navigator.setAllPeriods(true);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    navigator.window(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertFalse(result.boundedWindow);
            assertEquals(2, result.rows.size());
            assertNotNull(HistoryResolvedTimeToken.parse(result.rows.get(0).observation.timestamp));
            assertTrue(result.rows.get(0).observation.sortMs < result.rows.get(1).observation.sortMs);
        }
    }

    @Test public void meterWithoutZoneProducesStateButNoGuessedRows() {
        insertArchive(1L, "2026-09-09 10:00", 42_500L, 103.0);
        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 10:00",
                "2026-09-09 11:00",
                1L,
                2L,
                0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    window,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(0, result.rows.size());
            assertEquals(1, result.meterStates.size());
            assertEquals(LocalTimeWindowResolver.Status.ZONE_MISSING,
                    result.meterStates.get(0).windowStatus);
        }
    }

    private void installTimeModel() {
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
    }

    private void insertArchive(
            long id,
            String timestamp,
            long onTimeSeconds,
            double totalVolume) {
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
            values.put("total_volume", totalVolume);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    ArchiveFamilyStore.TABLE_PERIODS,
                    null,
                    values);
            assertTrue(inserted > 0L);
        }
    }
}
