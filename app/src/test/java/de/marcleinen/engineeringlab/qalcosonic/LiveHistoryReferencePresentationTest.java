package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class LiveHistoryReferencePresentationTest {
    @Test public void liveReferenceUsesExactHourBoundary() {
        String text = HistoryTimePresentation.formatPredecessor(Locale.GERMANY,
                live("2026-09-06 19:23"),
                archive(HistorySemanticTimeline.Granularity.HOUR, "2026-09-06 18:00"));

        assertTrue(text.contains("06.09.2026"));
        assertTrue(text.contains("18:00"));
    }

    @Test public void liveReferenceUsesDayBoundaryDate() {
        String text = HistoryTimePresentation.formatPredecessor(Locale.GERMANY,
                live("2026-08-02 10:00"),
                archive(HistorySemanticTimeline.Granularity.DAY, "2026-08-01 00:00"));

        assertTrue(text.contains("01.08.2026"));
    }

    @Test public void liveReferenceUsesHumanMonthPeriodLabel() {
        String text = HistoryTimePresentation.formatPredecessor(Locale.GERMANY,
                live("2026-07-15 10:00"),
                archive(HistorySemanticTimeline.Granularity.MONTH, "2026-07-01 00:00"));

        assertTrue(text.toLowerCase(Locale.GERMANY).contains("juni"));
        assertTrue(text.contains("2026"));
    }

    private static HistoryStatisticsRepository.Observation live(String timestamp) {
        return observation("live", HistorySemanticTimeline.Granularity.LIVE, timestamp, true);
    }

    private static HistoryStatisticsRepository.Observation archive(
            HistorySemanticTimeline.Granularity granularity, String timestamp) {
        return observation("archive-" + granularity, granularity, timestamp, false);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, HistorySemanticTimeline.Granularity granularity,
            String timestamp, boolean live) {
        return new HistoryStatisticsRepository.Observation(
                id, "A", granularity, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), 0L,
                live, false, "", 100.0,
                null, null, null, null, null, "", null, "",
                null, null, null, "", null, "", null,
                "", "0x00000000", "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
