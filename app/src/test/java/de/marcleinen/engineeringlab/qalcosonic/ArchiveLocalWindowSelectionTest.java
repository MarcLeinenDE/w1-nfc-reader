package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ArchiveLocalWindowSelectionTest {
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long MINUTE = 60L * 1000L;

    @Test public void shiftedOneHourWindowShowsBothHistoryEdgesButCountsNoStatisticsBucket() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 9, 17, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 9, 18, 0);
        long requestStart = start.atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
        List<ArchiveUtcProjection.Period> projected = Arrays.asList(
                resolved(requestStart - 5L * MINUTE, requestStart + 55L * MINUTE),
                resolved(requestStart + 55L * MINUTE, requestStart + HOUR + 55L * MINUTE));

        ArchiveLocalWindowSelection.Result history = ArchiveLocalWindowSelection.select(
                start, end, ZoneId.of("Europe/Berlin"), null, null, projected,
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
        ArchiveLocalWindowSelection.Result statistics = ArchiveLocalWindowSelection.select(
                start, end, ZoneId.of("Europe/Berlin"), null, null, projected,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

        assertEquals(UtcCoverage.Status.OVERLAP_ONLY, history.coverage.status);
        assertEquals(2, history.periods.size());
        assertEquals(0, statistics.periods.size());
    }

    @Test public void partialEdgesKeepOnlyCompleteInnerIntervalsForStatistics() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 9, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 9, 13, 0);
        ZoneId zone = ZoneId.of("Europe/Berlin");
        long requestStart = start.atZone(zone).toInstant().toEpochMilli();
        List<ArchiveUtcProjection.Period> projected = Arrays.asList(
                resolved(requestStart - 5L * MINUTE, requestStart + 55L * MINUTE),
                resolved(requestStart + 55L * MINUTE, requestStart + HOUR + 55L * MINUTE),
                resolved(requestStart + HOUR + 55L * MINUTE, requestStart + 2L * HOUR + 55L * MINUTE),
                resolved(requestStart + 2L * HOUR + 55L * MINUTE, requestStart + 3L * HOUR + 55L * MINUTE));

        ArchiveLocalWindowSelection.Result history = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
        ArchiveLocalWindowSelection.Result statistics = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

        assertEquals(UtcCoverage.Status.PARTIAL_EDGES, history.coverage.status);
        assertEquals(4, history.periods.size());
        assertEquals(2, statistics.periods.size());
        assertEquals(requestStart + 55L * MINUTE,
                statistics.periods.get(0).startUtcMs.longValue());
        assertEquals(requestStart + HOUR + 55L * MINUTE,
                statistics.periods.get(1).startUtcMs.longValue());
    }

    @Test public void missingMeterZoneFailsClosedAndSelectsNothing() {
        ArchiveLocalWindowSelection.Result result = ArchiveLocalWindowSelection.select(
                LocalDateTime.of(2026, 9, 9, 10, 0),
                LocalDateTime.of(2026, 9, 9, 11, 0),
                null,
                null,
                null,
                Collections.singletonList(resolved(1L, 2L)),
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

        assertFalse(result.windowResolved());
        assertEquals(LocalTimeWindowResolver.Status.ZONE_MISSING, result.window.status);
        assertTrue(result.periods.isEmpty());
    }

    @Test public void unresolvedOldestPrefixDoesNotEnterLaterSafeSelection() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 9, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 9, 12, 0);
        ZoneId zone = ZoneId.of("Europe/Berlin");
        long requestStart = start.atZone(zone).toInstant().toEpochMilli();
        List<ArchiveUtcProjection.Period> projected = Arrays.asList(
                openPrefix(requestStart),
                resolved(requestStart, requestStart + HOUR),
                resolved(requestStart + HOUR, requestStart + 2L * HOUR));

        ArchiveLocalWindowSelection.Result result = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

        assertFalse(result.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.EXACT, result.coverage.status);
        assertEquals(2, result.periods.size());
    }

    private static ArchiveUtcProjection.Period resolved(long start, long end) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.RESOLVED,
                boundary(start),
                boundary(end));
    }

    private static ArchiveUtcProjection.Period openPrefix(long end) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                null,
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
