package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class HistoryResolvedTimeTokenTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test public void fallFoldKeepsTwoDifferentUtcBoundariesForSameWallClock() {
        long start = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli();
        String token = HistoryResolvedTimeToken.interval(start, end, BERLIN);

        HistoryResolvedTimeToken.Parsed parsed = HistoryResolvedTimeToken.parse(token);
        assertNotNull(parsed);
        assertTrue(parsed.interval);
        assertEquals(2, parsed.startLocal().getHour());
        assertEquals(2, parsed.endLocal().getHour());
        assertEquals("+02:00", parsed.startLocal().getOffset().toString());
        assertEquals("+01:00", parsed.endLocal().getOffset().toString());
        assertEquals(end, parsed.sortMs());
    }

    @Test public void foldHourIsStillOneValidNativeHour() {
        long start = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli();
        String previous = HistoryResolvedTimeToken.boundary(start, BERLIN);
        String current = HistoryResolvedTimeToken.interval(start, end, BERLIN);

        assertTrue(HistoryResolvedTimeToken.adjacent(
                previous, current, HistorySemanticTimeline.Granularity.HOUR));
    }

    @Test public void twoHourGapIsNotPromotedToOneHourlyBucket() {
        long start = Instant.parse("2026-09-09T08:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-09-09T10:00:00Z").toEpochMilli();
        String previous = HistoryResolvedTimeToken.boundary(start, BERLIN);
        String current = HistoryResolvedTimeToken.interval(start, end, BERLIN);

        assertFalse(HistoryResolvedTimeToken.adjacent(
                previous, current, HistorySemanticTimeline.Granularity.HOUR));
    }

    @Test public void springDstCalendarDayRemainsValidDespiteTwentyThreeUtcHours() {
        long start = Instant.parse("2026-03-28T23:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-03-29T22:00:00Z").toEpochMilli();
        String previous = HistoryResolvedTimeToken.boundary(start, BERLIN);
        String current = HistoryResolvedTimeToken.interval(start, end, BERLIN);

        assertTrue(HistoryResolvedTimeToken.adjacent(
                previous, current, HistorySemanticTimeline.Granularity.DAY));
    }
}
