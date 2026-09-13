package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression coverage for visible-but-non-prorated statistics edge intervals. */
public final class StatisticsPartialEdgePresentationTest {
    @Test public void partialEdgeIntervalsRemainVisibleButDoNotEnterConsumptionKpis() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        long start = 1_789_000_000_000L;
        double total = 100.0;
        List<HistoryStatisticsRepository.Observation> observations = new ArrayList<>();
        observations.add(observation(
                "boundary", HistoryResolvedTimeToken.boundary(start, zone), start, true, total));

        for (int i = 0; i < 24; i++) {
            long periodStart = start + i * 3_600_000L;
            long periodEnd = periodStart + 3_600_000L;
            boolean partial = i == 0 || i == 23;
            total += partial ? 10.0 : 1.0;
            observations.add(observation(
                    "h" + i,
                    HistoryResolvedTimeToken.interval(periodStart, periodEnd, zone),
                    periodEnd,
                    partial,
                    total));
        }

        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(observations);

        // All 24 physical intervals stay visible in the chart. Only the 22 fully-contained
        // intervals contribute to selected-window KPIs; the two 10 m3 edge values remain context.
        assertEquals(24, summary.points.size());
        assertEquals(22, summary.availableBuckets);
        assertEquals(22.0, summary.total, 0.000001);
        assertEquals(1.0, summary.average, 0.000001);
        assertEquals(1.0, summary.maximum, 0.000001);
        assertEquals(1.0, summary.minimum, 0.000001);
        assertTrue(summary.points.get(0).partial);
        assertFalse(summary.points.get(1).partial);
        assertTrue(summary.points.get(23).partial);
        assertEquals(10.0, summary.points.get(0).value, 0.000001);
        assertEquals(10.0, summary.points.get(23).value, 0.000001);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, String timestamp, long sortMs, boolean contextOnly, double total) {
        return new HistoryStatisticsRepository.Observation(
                id,
                "A",
                HistorySemanticTimeline.Granularity.HOUR,
                timestamp,
                sortMs,
                0L,
                false,
                contextOnly,
                "",
                total,
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                "",
                null,
                null,
                null,
                "",
                null,
                "",
                null,
                "",
                "0x00000000",
                "",
                "",
                1,
                0,
                0,
                0,
                "TEST",
                "TEST");
    }
}
