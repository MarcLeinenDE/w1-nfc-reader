package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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

    @Test public void springDstSeparatesCivilWindowLengthFromNativeArchiveDayLength() {
        LocalTimeWindowResolver.Window civilDay = LocalTimeWindowResolver.resolveWindow(
                LocalDateTime.of(2026, 3, 29, 0, 0),
                LocalDateTime.of(2026, 3, 30, 0, 0),
                BERLIN,
                null,
                null);
        assertTrue(civilDay.resolved());
        assertEquals(Duration.ofHours(23).toMillis(),
                civilDay.endUtcMs - civilDay.startUtcMs);

        long nativeStart = Instant.parse("2026-03-28T22:59:00Z").toEpochMilli();
        String previous = HistoryResolvedTimeToken.boundary(nativeStart, BERLIN);
        String nativeDay = HistoryResolvedTimeToken.interval(
                nativeStart,
                Instant.ofEpochMilli(nativeStart).plus(Duration.ofHours(24)).toEpochMilli(),
                BERLIN);
        String civilLengthOnly = HistoryResolvedTimeToken.interval(
                nativeStart,
                Instant.ofEpochMilli(nativeStart).plus(Duration.ofHours(23)).toEpochMilli(),
                BERLIN);

        assertTrue(HistoryResolvedTimeToken.adjacent(
                previous, nativeDay, HistorySemanticTimeline.Granularity.DAY));
        assertFalse(HistoryResolvedTimeToken.adjacent(
                previous, civilLengthOnly, HistorySemanticTimeline.Granularity.DAY));
    }
}
