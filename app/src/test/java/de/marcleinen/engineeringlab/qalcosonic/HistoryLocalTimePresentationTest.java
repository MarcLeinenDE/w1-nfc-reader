package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryLocalTimePresentationTest {
    @Test public void normalIntervalDoesNotAddUnnecessaryUtcOffsetNoise() {
        HistoryLocalArchiveReadModel.Row row = row(
                "2026-09-09T08:55:00Z",
                "2026-09-09T09:55:00Z",
                "Europe/Berlin");

        String text = HistoryLocalTimePresentation.formatPeriod(Locale.GERMANY, row);

        assertFalse(text.contains("UTC+"));
        assertTrue(text.contains("10:55"));
        assertTrue(text.contains("11:55"));
    }

    @Test public void fallbackIntervalShowsBothDifferentOffsets() {
        HistoryLocalArchiveReadModel.Row row = row(
                "2026-10-25T00:00:00Z",
                "2026-10-25T01:00:00Z",
                "Europe/Berlin");

        String text = HistoryLocalTimePresentation.formatPeriod(Locale.GERMANY, row);

        assertTrue(text.contains("UTC+02:00"));
        assertTrue(text.contains("UTC+01:00"));
    }

    private static HistoryLocalArchiveReadModel.Row row(
            String startUtc,
            String endUtc,
            String zoneId) {
        long start = Instant.parse(startUtc).toEpochMilli();
        long end = Instant.parse(endUtc).toEpochMilli();
        ZoneId zone = ZoneId.of(zoneId);
        return new HistoryLocalArchiveReadModel.Row(
                null,
                "test",
                null,
                "previous-test",
                start,
                end,
                zone,
                Instant.ofEpochMilli(start).atZone(zone),
                Instant.ofEpochMilli(end).atZone(zone),
                true,
                false);
    }
}
