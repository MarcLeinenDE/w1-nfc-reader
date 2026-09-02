package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ReplacementChainAnalyticsTest {
    @Test public void sumsOnlyWithinMeterSegmentsAcrossConfirmedReplacement() {
        long start = 1_000_000L;
        long replacement = start + 10_000L;
        long end = start + 20_000L;
        List<WaterUsageAnalytics.Point> points = List.of(
                point("a0", "A", start, 100.0),
                point("a1", "A", start + 8_000L, 103.0),
                point("b0", "B", replacement, 0.2),
                point("b1", "B", end, 1.7));
        MeterLifecycleStore.Transition transition = new MeterLifecycleStore.Transition(
                "A", "B", replacement, replacement, 0.2);

        ReplacementChainAnalytics.Result result = ReplacementChainAnalytics.periodConsumption(
                points, List.of(transition), start, end);

        assertEquals(4.5, result.knownConsumptionM3, 0.000001);
        assertEquals(2, result.includedSegments);
        assertTrue(result.partial);
    }

    @Test public void neverSubtractsRawCumulativeReadingsAcrossMeters() {
        long start = 1_000_000L;
        long end = start + 20_000L;
        List<WaterUsageAnalytics.Point> points = List.of(
                point("a0", "A", start, 200.0),
                point("b0", "B", end, 0.5));

        ReplacementChainAnalytics.Result result = ReplacementChainAnalytics.periodConsumption(
                points, List.of(new MeterLifecycleStore.Transition("A", "B", start + 10_000L,
                        end, 0.5)), start, end);

        assertEquals(null, result.knownConsumptionM3);
        assertTrue(result.partial);
        assertEquals(0, result.includedSegments);
    }

    private static WaterUsageAnalytics.Point point(String id, String meterId, long ms, double total) {
        return new WaterUsageAnalytics.Point(id, meterId, "2026-01-01 00:00", ms,
                HistorySemanticTimeline.Granularity.LIVE, total);
    }
}
