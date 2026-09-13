package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryStatisticsRepositoryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void hourRangeUsesStartBoundaryAsContextAndIncludesDayEndBoundary() {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-05 23:00", "100.000");
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 00:00", "100.100");
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 01:00", "100.250");
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-07 00:00", "101.000");
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-07 01:00", "101.100");
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", "100.100");
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setDate(2026, 8, 6);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context, true)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.HOUR, navigator.window(), true);

            assertEquals(3, rows.size());
            assertEquals("2026-09-06 00:00", rows.get(0).timestamp);
            assertTrue(rows.get(0).contextOnly);
            assertEquals("2026-09-06 01:00", rows.get(1).timestamp);
            assertFalse(rows.get(1).contextOnly);
            assertEquals("2026-09-07 00:00", rows.get(2).timestamp);
            assertFalse(rows.get(2).contextOnly);
            for (HistoryStatisticsRepository.Observation row : rows) {
                assertEquals(HistorySemanticTimeline.Granularity.HOUR, row.granularity);
            }
        }
    }

    @Test public void selectedSeptemberMonthUsesOctoberFirstBoundaryAndNotSeptemberFirstAsData() {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-08-01 00:00", "190.000");
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-09-01 00:00", "196.000");
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-10-01 00:00", "203.000");
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-11-01 00:00", "210.000");
        }

        HistoryPeriodNavigator month = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);
        month.setDate(2026, 8, 15);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context, true)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.MONTH, month.window(), true);

            assertEquals(2, rows.size());
            assertEquals("2026-09-01 00:00", rows.get(0).timestamp);
            assertTrue(rows.get(0).contextOnly);
            assertEquals("2026-10-01 00:00", rows.get(1).timestamp);
            assertFalse(rows.get(1).contextOnly);
        }
    }

    @Test public void statisticsUsesNaturalResolutionAndPeriodEndBoundaryAsSelectedData() {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-08-31 00:00", "200.000");
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-09-01 00:00", "200.300");
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-09-02 00:00", "200.750");
            // Deliberate gap: no Day rows between Sep 2 and Sep 30.
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-09-30 00:00", "209.500");
            insert(store, ArchiveFamilyPeriod.Family.DAY, "2026-10-01 00:00", "210.000");
            insert(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-01 01:00", "200.350");
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);
        navigator.setDate(2026, 8, 15);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context, true)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryStatistics(navigator.window());

            assertEquals(4, rows.size());
            assertEquals("2026-09-01 00:00", rows.get(0).timestamp);
            assertTrue(rows.get(0).contextOnly);
            assertEquals("2026-10-01 00:00", rows.get(3).timestamp);
            assertFalse(rows.get(3).contextOnly);
            for (HistoryStatisticsRepository.Observation row : rows) {
                assertEquals(HistorySemanticTimeline.Granularity.DAY, row.granularity);
            }

            HistoryStatisticsAnalytics.ConsumptionSummary summary =
                    HistoryStatisticsAnalytics.consumption(rows);
            assertEquals(2, summary.availableBuckets);
            assertEquals(0.950, summary.total, 0.000001);
            assertEquals("2026-09-01 00:00", summary.points.get(0).timestamp);
            assertEquals("2026-09-30 00:00", summary.points.get(1).timestamp);
        }
    }

    @Test public void allPeriodsDoesNotInjectSyntheticContextRows() {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-07-01 00:00", "190.000");
            insert(store, ArchiveFamilyPeriod.Family.MONTH, "2026-08-01 00:00", "196.000");
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.YEAR);
        navigator.setAllPeriods(true);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context, true)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.MONTH, navigator.window(), true);
            assertEquals(2, rows.size());
            assertFalse(rows.get(0).contextOnly);
            assertFalse(rows.get(1).contextOnly);
        }
    }

    private static void insert(ArchiveFamilyStore store, ArchiveFamilyPeriod.Family family,
                               String timestamp, String total) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder().totalVolume(total);
        values.temperature = "19.5 C";
        values.maxFlow = "0.50 m3/h";
        values.batteryPercent = "88 %";
        values.errorFlags = "0x00000000";
        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                family, timestamp, "2026-09-07T03:00:00Z",
                "fp-" + family + "-" + timestamp, "TEST", "TEST", values.build());
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("TEST-METER", period));
    }
}
