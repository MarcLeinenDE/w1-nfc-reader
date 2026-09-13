package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class HistoryAllLiveBaselineTest {
    @Test public void mixedAllViewUsesChronologicallyNearestArchiveRegardlessOfGranularity() {
        HistoryStatisticsRepository.Observation month = archive(
                "month", HistorySemanticTimeline.Granularity.MONTH, 1_000L, 100.0, 0);
        HistoryStatisticsRepository.Observation hour = archive(
                "hour", HistorySemanticTimeline.Granularity.HOUR, 3_000L, 102.0, 0);
        HistoryStatisticsRepository.Observation day = archive(
                "day", HistorySemanticTimeline.Granularity.DAY, 3_500L, 102.3, 0);
        HistoryStatisticsRepository.Observation live = live("live", 4_000L, 102.8);

        Map<String, HistoryStatisticsAnalytics.Delta> deltas =
                HistoryStatisticsAnalytics.deltas(List.of(month, hour, day, live));

        HistoryStatisticsAnalytics.Delta delta = deltas.get("live");
        assertNotNull(delta);
        assertEquals("day", delta.previous.identity);
        assertEquals(0.5, delta.consumptionM3, 0.000001);
    }

    @Test public void mixedAllViewUsesImmediatePreviousLiveWhenItIsCloserThanArchive() {
        HistoryStatisticsRepository.Observation archive = archive(
                "archive", HistorySemanticTimeline.Granularity.HOUR, 2_000L, 10.0, 0);
        HistoryStatisticsRepository.Observation firstLive = live("live1", 3_000L, 10.2);
        HistoryStatisticsRepository.Observation secondLive = live("live2", 4_000L, 10.5);

        Map<String, HistoryStatisticsAnalytics.Delta> deltas =
                HistoryStatisticsAnalytics.deltas(List.of(archive, firstLive, secondLive));

        HistoryStatisticsAnalytics.Delta firstDelta = deltas.get("live1");
        assertNotNull(firstDelta);
        assertEquals("archive", firstDelta.previous.identity);
        assertEquals(0.2, firstDelta.consumptionM3, 0.000001);

        HistoryStatisticsAnalytics.Delta secondDelta = deltas.get("live2");
        assertNotNull(secondDelta);
        assertEquals("live1", secondDelta.previous.identity);
        assertEquals(0.3, secondDelta.consumptionM3, 0.000001);
    }

    @Test public void liveOnlyViewKeepsPreviousLiveBehavior() {
        HistoryStatisticsRepository.Observation first = live("live1", 1_000L, 10.0);
        HistoryStatisticsRepository.Observation second = live("live2", 2_000L, 10.2);

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(List.of(first, second)).get("live2");

        assertNotNull(delta);
        assertEquals("live1", delta.previous.identity);
        assertEquals(0.2, delta.consumptionM3, 0.000001);
    }

    @Test public void mixedAllViewNeverCrossesMeterIdentity() {
        HistoryStatisticsRepository.Observation sameMeter = archive(
                "same", HistorySemanticTimeline.Granularity.HOUR, 2_000L, 50.0, 0);
        HistoryStatisticsRepository.Observation otherMeter = archive(
                "other", "B", HistorySemanticTimeline.Granularity.HOUR, 3_000L, 900.0, 0);
        HistoryStatisticsRepository.Observation live = live("live", 4_000L, 50.4);

        HistoryStatisticsAnalytics.Delta delta = HistoryStatisticsAnalytics.deltas(
                List.of(sameMeter, otherMeter, live)).get("live");

        assertNotNull(delta);
        assertEquals("same", delta.previous.identity);
        assertEquals(0.4, delta.consumptionM3, 0.000001);
    }

    @Test public void conflictedArchiveIsSkippedForNearestTrustworthyPredecessor() {
        HistoryStatisticsRepository.Observation trustworthy = archive(
                "trusted", HistorySemanticTimeline.Granularity.DAY, 2_000L, 20.0, 0);
        HistoryStatisticsRepository.Observation conflicted = archive(
                "conflicted", HistorySemanticTimeline.Granularity.HOUR, 3_000L, 20.3, 1);
        HistoryStatisticsRepository.Observation live = live("live", 4_000L, 20.5);

        HistoryStatisticsAnalytics.Delta delta = HistoryStatisticsAnalytics.deltas(
                List.of(trustworthy, conflicted, live)).get("live");

        assertNotNull(delta);
        assertEquals("trusted", delta.previous.identity);
        assertEquals(0.5, delta.consumptionM3, 0.000001);
    }

    private static HistoryStatisticsRepository.Observation live(
            String id, long sortMs, double total) {
        return observation(id, "A", HistorySemanticTimeline.Granularity.LIVE,
                sortMs, true, total, 0);
    }

    private static HistoryStatisticsRepository.Observation archive(
            String id, HistorySemanticTimeline.Granularity granularity,
            long sortMs, double total, int conflictFlags) {
        return archive(id, "A", granularity, sortMs, total, conflictFlags);
    }

    private static HistoryStatisticsRepository.Observation archive(
            String id, String meter, HistorySemanticTimeline.Granularity granularity,
            long sortMs, double total, int conflictFlags) {
        return observation(id, meter, granularity, sortMs, false, total, conflictFlags);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, String meter, HistorySemanticTimeline.Granularity granularity,
            long sortMs, boolean live, double total, int conflictFlags) {
        return new HistoryStatisticsRepository.Observation(
                id, meter, granularity, "2026-09-12 12:00", sortMs, sortMs,
                live, false, "", total, null, null, null,
                null, null, "", null, "", null, null, null, "", null, "",
                88, "", "0x00000000", "", "", 1, 0, 0, conflictFlags,
                "TEST", "TEST");
    }
}
