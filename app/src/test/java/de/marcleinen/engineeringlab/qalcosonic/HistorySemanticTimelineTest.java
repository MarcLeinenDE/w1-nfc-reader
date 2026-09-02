package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class HistorySemanticTimelineTest {
    @Test
    public void sameTimestampDifferentGranularitiesRemainDistinct() {
        long timestamp = 1_000L;
        HistorySemanticTimeline.Point live = point(
                "live:1",
                timestamp,
                HistorySemanticTimeline.Granularity.LIVE,
                HistorySemanticTimeline.Provenance.LIVE_NFC);
        HistorySemanticTimeline.Point month = point(
                "month:2026-08",
                timestamp,
                HistorySemanticTimeline.Granularity.MONTH,
                HistorySemanticTimeline.Provenance.METER_ARCHIVE);

        List<HistorySemanticTimeline.Point> result =
                HistorySemanticTimeline.newestFirst(List.of(live, month));

        assertEquals(2, result.size());
        assertTrue(result.contains(live));
        assertTrue(result.contains(month));
    }

    @Test
    public void timelineCombinesSourcesBySemanticTimeNotSourceSection() {
        HistorySemanticTimeline.Point olderLive = point(
                "live:old",
                1_000L,
                HistorySemanticTimeline.Granularity.LIVE,
                HistorySemanticTimeline.Provenance.LIVE_NFC);
        HistorySemanticTimeline.Point newestMonth = point(
                "month:new",
                3_000L,
                HistorySemanticTimeline.Granularity.MONTH,
                HistorySemanticTimeline.Provenance.METER_ARCHIVE);
        HistorySemanticTimeline.Point middleDay = point(
                "day:middle",
                2_000L,
                HistorySemanticTimeline.Granularity.DAY,
                HistorySemanticTimeline.Provenance.METER_ARCHIVE);

        List<HistorySemanticTimeline.Point> result = HistorySemanticTimeline.newestFirst(
                List.of(olderLive, newestMonth, middleDay));

        assertEquals("month:new", result.get(0).identity);
        assertEquals("day:middle", result.get(1).identity);
        assertEquals("live:old", result.get(2).identity);
    }

    @Test
    public void derivedWeekIsExplicitlyMarkedLocal() {
        HistorySemanticTimeline.Point derived = new HistorySemanticTimeline.Point(
                "derived:week:2026-W35",
                4_000L,
                HistorySemanticTimeline.Granularity.WEEK,
                HistorySemanticTimeline.Provenance.LOCAL_DERIVED,
                HistorySemanticTimeline.Metric.CONSUMPTION,
                0.123,
                "m³");
        HistorySemanticTimeline.Point nativeMonth = point(
                "month:2026-08",
                4_000L,
                HistorySemanticTimeline.Granularity.MONTH,
                HistorySemanticTimeline.Provenance.METER_ARCHIVE);

        assertTrue(derived.isLocallyDerived());
        assertFalse(nativeMonth.isLocallyDerived());
    }

    @Test
    public void invalidIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HistorySemanticTimeline.Point(
                "  ",
                0L,
                HistorySemanticTimeline.Granularity.LIVE,
                HistorySemanticTimeline.Provenance.LIVE_NFC,
                HistorySemanticTimeline.Metric.CUMULATIVE_VOLUME,
                1.0,
                "m³"));
    }

    private static HistorySemanticTimeline.Point point(
            String identity,
            long timestamp,
            HistorySemanticTimeline.Granularity granularity,
            HistorySemanticTimeline.Provenance provenance) {
        return new HistorySemanticTimeline.Point(
                identity,
                timestamp,
                granularity,
                provenance,
                HistorySemanticTimeline.Metric.CUMULATIVE_VOLUME,
                1.0,
                "m³");
    }
}
