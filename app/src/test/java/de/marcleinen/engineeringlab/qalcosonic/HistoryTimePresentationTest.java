package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public final class HistoryTimePresentationTest {
    @Test public void monthDeltaPredecessorUsesPreviousCompletedPeriodLabel() {
        HistoryStatisticsRepository.Observation previous = month("previous", "2025-09-01 00:00", 150.090);
        HistoryStatisticsRepository.Observation current = month("current", "2025-10-01 00:00", 155.708);

        assertEquals("September 2025",
                HistoryTimePresentation.formatPrimary(Locale.US, current));
        assertEquals("August 2025",
                HistoryTimePresentation.formatPredecessor(Locale.US, current, previous));
    }

    private static HistoryStatisticsRepository.Observation month(
            String identity, String timestamp, double totalM3) {
        return new HistoryStatisticsRepository.Observation(
                identity, "TEST-METER", HistorySemanticTimeline.Granularity.MONTH, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), 0L, false, false, "",
                totalM3, null, null, null, null, null, "", null, "",
                null, null, null, "", null, "", null,
                "", "0x00000000", "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
