package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ArchivePeriodSemanticsTest {
    @Test public void monthBoundaryRepresentsPreviousCompletedMonth() {
        assertEquals("2025-09-01 00:00", HistoryTimePresentation.periodStartTimestamp(
                "2025-10-01 00:00", HistorySemanticTimeline.Granularity.MONTH));
        assertEquals("September 2025", HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.MONTH,
                "2025-10-01 00:00"));
    }

    @Test public void dayBoundaryRepresentsPreviousCompletedDay() {
        assertEquals("2025-09-05 00:00", HistoryTimePresentation.periodStartTimestamp(
                "2025-09-06 00:00", HistorySemanticTimeline.Granularity.DAY));
        String displayed = HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.DAY,
                "2025-09-06 00:00");
        assertTrue(displayed.contains("05.09.2025"));
    }

    @Test public void hourBoundaryRepresentsPreviousCompletedHour() {
        assertEquals("2026-09-06 17:00", HistoryTimePresentation.periodStartTimestamp(
                "2026-09-06 18:00", HistorySemanticTimeline.Granularity.HOUR));
        String displayed = HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.HOUR,
                "2026-09-06 18:00");
        assertTrue(displayed.contains("17:00"));
        assertTrue(displayed.contains("18:00"));
    }

    @Test public void midnightHourBoundaryKeepsBothCalendarDatesVisible() {
        String displayed = HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.HOUR,
                "2026-09-07 00:00");
        assertTrue(displayed.contains("06.09.2026"));
        assertTrue(displayed.contains("23:00"));
        assertTrue(displayed.contains("07.09.2026"));
        assertTrue(displayed.contains("00:00"));
    }

    @Test public void liveTimestampIsNotShiftedToPreviousArchivePeriod() {
        assertEquals("2026-09-06 18:00", HistoryTimePresentation.periodStartTimestamp(
                "2026-09-06 18:00", HistorySemanticTimeline.Granularity.LIVE));
    }
}
