package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryLocalArchiveReadModelTest {
    @Test public void dstFallbackKeepsTwoEqualWallClockBoundariesDistinctByOffsetAndUtc() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        long start = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();
        long end = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli();
        ArchiveUtcProjection.Period period = resolved(start, end);

        ArchiveLocalWindowSelection.Result selection = ArchiveLocalWindowSelection.select(
                LocalDateTime.of(2026, 10, 25, 2, 0),
                LocalDateTime.of(2026, 10, 25, 3, 0),
                berlin,
                java.time.ZoneOffset.ofHours(2),
                null,
                Collections.singletonList(period),
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

        HistoryLocalArchiveReadModel.Row row = HistoryLocalArchiveReadModel.rows(selection).get(0);

        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 0), row.startLocal.toLocalDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 0), row.endLocal.toLocalDateTime());
        assertEquals("+02:00", row.startLocal.getOffset().getId());
        assertEquals("+01:00", row.endLocal.getOffset().getId());
        assertEquals(start, row.startUtcMs);
        assertEquals(end, row.endUtcMs);
        assertTrue(row.fullyContained);
        assertFalse(row.partialOverlap);
    }

    @Test public void overlappingEdgeIsMarkedPartialWithoutChangingItsRealBoundaries() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        long requestStart = LocalDateTime.of(2026, 9, 9, 17, 0)
                .atZone(berlin).toInstant().toEpochMilli();
        ArchiveUtcProjection.Period period = resolved(
                requestStart - 5L * 60L * 1000L,
                requestStart + 55L * 60L * 1000L);

        ArchiveLocalWindowSelection.Result selection = ArchiveLocalWindowSelection.select(
                LocalDateTime.of(2026, 9, 9, 17, 0),
                LocalDateTime.of(2026, 9, 9, 18, 0),
                berlin,
                null,
                null,
                Collections.singletonList(period),
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

        HistoryLocalArchiveReadModel.Row row = HistoryLocalArchiveReadModel.rows(selection).get(0);
        assertTrue(row.partialOverlap);
        assertFalse(row.fullyContained);
        assertEquals(requestStart - 5L * 60L * 1000L, row.startUtcMs);
        assertEquals(requestStart + 55L * 60L * 1000L, row.endUtcMs);
    }

    private static ArchiveUtcProjection.Period resolved(long start, long end) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.RESOLVED,
                boundary(start),
                boundary(end));
    }

    private static ArchiveUtcProjection.Boundary boundary(long utcMs) {
        return new ArchiveUtcProjection.Boundary(
                null,
                ArchiveUtcProjection.BoundaryStatus.RESOLVED,
                utcMs,
                1L,
                1L,
                MeterTimeResolver.Status.RESOLVED,
                MeterTimeResolver.ClockRelation.ALIGNED,
                0L,
                MeterTimeResolver.METHOD_ON_TIME_LIVE_ANCHOR);
    }
}
