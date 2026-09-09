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
                resolved(requestStart - 5L * MINUTE, requestStart + 55L * MINUTE, "A"),
                resolved(requestStart + 55L * MINUTE, requestStart + HOUR + 55L * MINUTE, "B"));

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
                resolved(requestStart - 5L * MINUTE, requestStart + 55L * MINUTE, "A"),
                resolved(requestStart + 55L * MINUTE, requestStart + HOUR + 55L * MINUTE, "B"),
                resolved(requestStart + HOUR + 55L * MINUTE, requestStart + 2L * HOUR + 55L * MINUTE, "C"),
                resolved(requestStart + 2L * HOUR + 55L * MINUTE, requestStart + 3L * HOUR + 55L * MINUTE, "D"));

        ArchiveLocalWindowSelection.Result history = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
        ArchiveLocalWindowSelection.Result statistics = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

        assertEquals(UtcCoverage.Status.PARTIAL_EDGES, history.coverage.status);
        assertEquals(4, history.periods.size());
        assertEquals(2, statistics.periods.size());
        assertEquals("B", statistics.periods.get(0).identity);
        assertEquals("C", statistics.periods.get(1).identity);
    }

    @Test public void missingMeterZoneFailsClosedAndSelectsNothing() {
        ArchiveLocalWindowSelection.Result result = ArchiveLocalWindowSelection.select(
                LocalDateTime.of(2026, 9, 9, 10, 0),
                LocalDateTime.of(2026, 9, 9, 11, 0),
                null,
                null,
                null,
                Collections.singletonList(resolved(1L, 2L, "A")),
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
                openPrefix(requestStart, "OLD"),
                resolved(requestStart, requestStart + HOUR, "A"),
                resolved(requestStart + HOUR, requestStart + 2L * HOUR, "B"));

        ArchiveLocalWindowSelection.Result result = ArchiveLocalWindowSelection.select(
                start, end, zone, null, null, projected,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);

        assertFalse(result.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.EXACT, result.coverage.status);
        assertEquals(2, result.periods.size());
    }

    private static ArchiveUtcProjection.Period resolved(long start, long end, String identity) {
        ArchiveUtcProjection.Boundary from = boundary(start, identity + "-S");
        ArchiveUtcProjection.Boundary to = boundary(end, identity + "-E");
        return new TestPeriod(identity, ArchiveUtcProjection.PeriodStatus.RESOLVED, from, to);
    }

    private static ArchiveUtcProjection.Period openPrefix(long end, String identity) {
        return new TestPeriod(
                identity,
                ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                null,
                boundary(end, identity + "-E"));
    }

    private static ArchiveUtcProjection.Boundary boundary(long utcMs, String ignoredIdentity) {
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

    /** Test-only identity wrapper because production Period derives identity from its source row. */
    private static final class TestPeriod extends ArchiveUtcProjection.Period {
        private final String testIdentity;

        TestPeriod(String identity, ArchiveUtcProjection.PeriodStatus status,
                   ArchiveUtcProjection.Boundary start, ArchiveUtcProjection.Boundary end) {
            super(null, status, start, end);
            this.testIdentity = identity;
        }
    }
}
