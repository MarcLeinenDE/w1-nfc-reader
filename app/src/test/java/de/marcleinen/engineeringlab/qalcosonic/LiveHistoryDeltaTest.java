package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class LiveHistoryDeltaTest {
    @Test public void mixedAllViewLiveUsesNearestEarlierArchiveAcrossGranularities() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("day", "A", HistorySemanticTimeline.Granularity.DAY,
                        "2026-09-07 00:00", 205.200, false),
                observation("live1", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:23", 205.050, true),
                observation("hour", "A", HistorySemanticTimeline.Granularity.HOUR,
                        "2026-09-07 20:00", 205.391, false),
                observation("live2", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-07 21:15", 205.391, true));

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(values).get("live2");

        assertEquals(0.0, delta.consumptionM3, 0.000001);
        assertEquals("hour", delta.previous.identity);
        assertEquals(HistorySemanticTimeline.Granularity.HOUR, delta.previous.granularity);
    }

    @Test public void firstLiveUsesEarlierArchiveWhenMixedHistoryProvidesOne() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("month", "A", HistorySemanticTimeline.Granularity.MONTH,
                        "2026-07-01 00:00", 190.000, false),
                observation("day", "A", HistorySemanticTimeline.Granularity.DAY,
                        "2026-08-01 00:00", 195.000, false),
                observation("hour", "A", HistorySemanticTimeline.Granularity.HOUR,
                        "2026-08-02 09:00", 195.400, false),
                observation("live", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-08-02 10:00", 195.500, true));

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(values).get("live");

        assertEquals(0.1, delta.consumptionM3, 0.000001);
        assertEquals("hour", delta.previous.identity);
    }

    @Test public void liveSeriesDoesNotCrossReplacementMeter() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("aLive1", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 18:00", 205.000, true),
                observation("bLive", "B", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:00", 4.000, true),
                observation("aLive2", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:23", 205.126, true));

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(values).get("aLive2");

        assertEquals("aLive1", delta.previous.identity);
        assertEquals(0.126, delta.consumptionM3, 0.000001);
    }

    @Test public void sameTimestampIsNotAcceptedAsPreviousLiveReference() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("live1", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:23", 205.120, true),
                observation("live2", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:23", 205.126, true));

        Map<String, HistoryStatisticsAnalytics.Delta> deltas =
                HistoryStatisticsAnalytics.deltas(values);

        assertNull(deltas.get("live1").consumptionM3);
        assertNull(deltas.get("live1").previous);
        assertNull(deltas.get("live2").consumptionM3);
        assertNull(deltas.get("live2").previous);
    }

    @Test public void negativeLiveDeltaIsRejected() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("live1", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 18:00", 205.000, true),
                observation("live2", "A", HistorySemanticTimeline.Granularity.LIVE,
                        "2026-09-06 19:23", 204.900, true));

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(values).get("live2");

        assertNull(delta.consumptionM3);
        assertNull(delta.previous);
    }

    @Test public void archiveDeltaStillUsesSameGranularityOnly() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("month1", "A", HistorySemanticTimeline.Granularity.MONTH,
                        "2026-08-01 00:00", 190.000, false),
                observation("hour", "A", HistorySemanticTimeline.Granularity.HOUR,
                        "2026-08-31 23:00", 196.500, false),
                observation("month2", "A", HistorySemanticTimeline.Granularity.MONTH,
                        "2026-09-01 00:00", 196.668, false));

        HistoryStatisticsAnalytics.Delta delta =
                HistoryStatisticsAnalytics.deltas(values).get("month2");

        assertEquals("month1", delta.previous.identity);
        assertEquals(6.668, delta.consumptionM3, 0.000001);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, String meter, HistorySemanticTimeline.Granularity granularity,
            String timestamp, double totalM3, boolean live) {
        return new HistoryStatisticsRepository.Observation(
                id, meter, granularity, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), live ? 1L : 0L,
                live, false, "", totalM3,
                null, null, null, null, null, "", null, "",
                null, null, null, "", null, "", null,
                "", "0x00000000", "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
