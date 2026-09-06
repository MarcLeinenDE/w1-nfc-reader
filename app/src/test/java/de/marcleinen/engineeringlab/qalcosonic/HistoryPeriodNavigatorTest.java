package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryPeriodNavigatorTest {
    @Test public void dayWindowUsesFloatingMeterBoundariesWithoutTimezoneRewrite() {
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setDate(2026, 8, 6);

        HistoryPeriodNavigator.Window window = navigator.window();

        assertEquals("2026-09-06 00:00", window.archiveStart);
        assertEquals("2026-09-07 00:00", window.archiveEnd);
        assertEquals(24, window.expectedBuckets);
        assertFalse(window.allPeriods);
    }

    @Test public void monthAndYearWindowsExposeNaturalExpectedBucketCounts() {
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);
        navigator.setDate(2026, 8, 6);
        assertEquals(30, navigator.window().expectedBuckets);
        assertEquals("2026-09-01 00:00", navigator.window().archiveStart);
        assertEquals("2026-10-01 00:00", navigator.window().archiveEnd);

        navigator.setScale(HistoryPeriodNavigator.Scale.YEAR);
        assertEquals(12, navigator.window().expectedBuckets);
        assertEquals("2026-01-01 00:00", navigator.window().archiveStart);
        assertEquals("2027-01-01 00:00", navigator.window().archiveEnd);
    }

    @Test public void historyGranularitySelectsContextualCalendarScale() {
        assertEquals(HistoryPeriodNavigator.Scale.DAY,
                HistoryPeriodNavigator.forHistory(HistorySemanticTimeline.Granularity.HOUR));
        assertEquals(HistoryPeriodNavigator.Scale.MONTH,
                HistoryPeriodNavigator.forHistory(HistorySemanticTimeline.Granularity.DAY));
        assertEquals(HistoryPeriodNavigator.Scale.YEAR,
                HistoryPeriodNavigator.forHistory(HistorySemanticTimeline.Granularity.MONTH));
        assertEquals(HistoryPeriodNavigator.Scale.MONTH, HistoryPeriodNavigator.forHistory(null));
    }

    @Test public void GermanFloatingTimestampIsLocalizedButKeepsMeterWallClock() {
        String hour = HistoryTimePresentation.formatFloatingPrimary(Locale.GERMANY,
                HistorySemanticTimeline.Granularity.HOUR, "2026-09-06 18:00");
        String month = HistoryTimePresentation.formatFloatingPrimary(Locale.GERMANY,
                HistorySemanticTimeline.Granularity.MONTH, "2026-09-01 00:00");

        assertTrue(hour.contains("06.09.2026"));
        assertTrue(hour.contains("18:00"));
        assertTrue(month.toLowerCase(Locale.GERMANY).contains("september"));
        assertTrue(month.contains("2026"));
    }

    @Test public void hourlyPredecessorOnSameDayShowsActualHour() {
        HistoryStatisticsRepository.Observation current = observation("now", "2026-09-06 18:00");
        HistoryStatisticsRepository.Observation previous = observation("prev", "2026-09-06 17:00");

        assertEquals("17:00", HistoryTimePresentation.formatPredecessor(
                Locale.GERMANY, current, previous));
    }

    private static HistoryStatisticsRepository.Observation observation(String id, String timestamp) {
        return new HistoryStatisticsRepository.Observation(
                id, "M1", HistorySemanticTimeline.Granularity.HOUR, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), 0L, false, false, "",
                100.0, null, null, null, null, null, "", null, "",
                null, null, null, "", null, "", 88,
                "", "0x00000000", "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
