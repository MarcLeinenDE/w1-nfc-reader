package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryTimePresentationTest {
    @Test public void monthDeltaPredecessorUsesPreviousCompletedPeriodLabel() {
        HistoryStatisticsRepository.Observation previous = month("previous", "2025-09-01 00:00", 150.090);
        HistoryStatisticsRepository.Observation current = month("current", "2025-10-01 00:00", 155.708);

        assertEquals("September 2025",
                HistoryTimePresentation.formatPrimary(Locale.US, current));
        assertEquals("August 2025",
                HistoryTimePresentation.formatPredecessor(Locale.US, current, previous));
    }

    @Test public void resolvedFallFoldShowsOffsetsInsteadOfTwoIndistinguishableTimes() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        long start = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli();
        String token = HistoryResolvedTimeToken.interval(start, end, zone);

        String label = HistoryTimePresentation.formatArchivePeriod(
                Locale.US, HistorySemanticTimeline.Granularity.HOUR, token);

        assertTrue(label.contains("+02:00"));
        assertTrue(label.contains("+01:00"));
        assertTrue(label.contains("2:00"));
        assertEquals(start, HistoryTimePresentation.floatingSortMs(
                HistoryTimePresentation.periodStartTimestamp(
                        token, HistorySemanticTimeline.Granularity.HOUR)));
    }

    @Test public void ordinaryResolvedHourDoesNotAddNoisyOffset() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        long start = Instant.parse("2026-09-09T08:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-09-09T09:00:00Z").toEpochMilli();
        String token = HistoryResolvedTimeToken.interval(start, end, zone);

        String label = HistoryTimePresentation.formatArchivePeriod(
                Locale.US, HistorySemanticTimeline.Granularity.HOUR, token);

        assertFalse(label.contains("+02:00"));
        assertTrue(label.contains("10:00"));
        assertTrue(label.contains("11:00"));
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
