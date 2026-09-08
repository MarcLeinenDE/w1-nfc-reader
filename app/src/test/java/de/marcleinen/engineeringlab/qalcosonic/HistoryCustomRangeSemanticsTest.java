package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryCustomRangeSemanticsTest {
    @Test public void customHourRangeCountsOnlyFullyContainedBuckets() {
        HistoryPeriodNavigator.Window window = customWindow(2026, Calendar.SEPTEMBER, 6,
                10, 15, 2026, Calendar.SEPTEMBER, 6, 13, 45);

        assertEquals(2, HistoryCustomRangeSemantics.expectedFullBuckets(
                window, HistorySemanticTimeline.Granularity.HOUR));
        assertFalse(HistoryCustomRangeSemantics.exactEdges(
                window, HistorySemanticTimeline.Granularity.HOUR));
        assertEquals("2026-09-06 11:00", HistoryCustomRangeSemantics.exactInteriorStart(
                window, HistorySemanticTimeline.Granularity.HOUR));
        assertEquals("2026-09-06 13:00", HistoryCustomRangeSemantics.exactInteriorEnd(
                window, HistorySemanticTimeline.Granularity.HOUR));
    }

    @Test public void overlappingEdgePeriodIsVisibleButNotStatisticallyContained() {
        HistoryPeriodNavigator.Window window = customWindow(2026, Calendar.SEPTEMBER, 6,
                10, 15, 2026, Calendar.SEPTEMBER, 6, 13, 45);
        HistoryStatisticsRepository.Observation leftEdge = hour("2026-09-06 11:00");
        HistoryStatisticsRepository.Observation contained = hour("2026-09-06 12:00");
        HistoryStatisticsRepository.Observation rightEdge = hour("2026-09-06 14:00");

        assertTrue(HistoryCustomRangeSemantics.overlaps(leftEdge, window));
        assertFalse(HistoryCustomRangeSemantics.fullyContained(leftEdge, window));
        assertTrue(HistoryCustomRangeSemantics.overlaps(contained, window));
        assertTrue(HistoryCustomRangeSemantics.fullyContained(contained, window));
        assertTrue(HistoryCustomRangeSemantics.overlaps(rightEdge, window));
        assertFalse(HistoryCustomRangeSemantics.fullyContained(rightEdge, window));
    }

    @Test public void automaticResolutionFallsBackToAvailableFamily() {
        HistoryPeriodNavigator.Window window = customWindow(2026, Calendar.SEPTEMBER, 1,
                0, 0, 2026, Calendar.SEPTEMBER, 3, 0, 0);
        HistoryStatisticsRepository.Availability availability =
                new HistoryStatisticsRepository.Availability(0, 0, 2, 1);

        assertEquals(HistorySemanticTimeline.Granularity.DAY,
                HistoryCustomRangeSemantics.automaticResolution(window, availability));
    }

    @Test public void exactAlignedRangeCountsEveryFullHour() {
        HistoryPeriodNavigator.Window window = customWindow(2026, Calendar.SEPTEMBER, 6,
                10, 0, 2026, Calendar.SEPTEMBER, 6, 13, 0);

        assertEquals(3, HistoryCustomRangeSemantics.expectedFullBuckets(
                window, HistorySemanticTimeline.Granularity.HOUR));
        assertTrue(HistoryCustomRangeSemantics.exactEdges(
                window, HistorySemanticTimeline.Granularity.HOUR));
    }

    private static HistoryPeriodNavigator.Window customWindow(
            int sy, int sm, int sd, int sh, int smin,
            int ey, int em, int ed, int eh, int emin) {
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(sy, sm, sd, sh, smin, 0);
        Calendar end = Calendar.getInstance();
        end.clear();
        end.set(ey, em, ed, eh, emin, 0);
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setCustomRange(start.getTimeInMillis(), end.getTimeInMillis());
        return navigator.window();
    }

    private static HistoryStatisticsRepository.Observation hour(String boundary) {
        return new HistoryStatisticsRepository.Observation(
                "HOUR|M1|" + boundary,
                "M1",
                HistorySemanticTimeline.Granularity.HOUR,
                boundary,
                HistoryTimePresentation.floatingSortMs(boundary),
                0L,
                false,
                false,
                "",
                100.0,
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
                90,
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
