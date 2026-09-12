package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ArchiveWindowCoverageTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test public void naturalOldestOpenEdgeDoesNotPoisonLaterExactWindow() {
        List<ArchiveUtcProjection.Period> periods = Arrays.asList(
                openPrefix(10L * HOUR),
                resolved(10L * HOUR, 11L * HOUR),
                resolved(11L * HOUR, 12L * HOUR));

        ArchiveWindowCoverage.Result result = ArchiveWindowCoverage.evaluateUtc(
                10L * HOUR, 12L * HOUR, periods);

        assertFalse(result.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.EXACT, result.coverage.status);
        assertEquals(2, result.coverage.fullyContainedIntervals);
    }

    @Test public void requestTouchingUnknownOldestBucketIsUnresolvedTime() {
        List<ArchiveUtcProjection.Period> periods = Arrays.asList(
                openPrefix(10L * HOUR),
                resolved(10L * HOUR, 11L * HOUR));

        ArchiveWindowCoverage.Result result = ArchiveWindowCoverage.evaluateUtc(
                9L * HOUR, 11L * HOUR, periods);

        assertTrue(result.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.UNRESOLVED_TIME, result.coverage.status);
    }

    @Test public void unresolvedInteriorRunOnlyPoisonsWindowsThatCrossIt() {
        List<ArchiveUtcProjection.Period> periods = Arrays.asList(
                resolved(10L * HOUR, 11L * HOUR),
                unresolvedEnd(11L * HOUR),
                unresolvedStartWithResolvedEnd(12L * HOUR),
                resolved(12L * HOUR, 13L * HOUR));

        ArchiveWindowCoverage.Result crossing = ArchiveWindowCoverage.evaluateUtc(
                10L * HOUR, 13L * HOUR, periods);
        ArchiveWindowCoverage.Result later = ArchiveWindowCoverage.evaluateUtc(
                12L * HOUR, 13L * HOUR, periods);

        assertTrue(crossing.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.UNRESOLVED_TIME, crossing.coverage.status);
        assertFalse(later.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.EXACT, later.coverage.status);
    }

    @Test public void missingDataWithoutKnownUnresolvedPeriodIsGapNotUnresolvedTime() {
        ArchiveWindowCoverage.Result result = ArchiveWindowCoverage.evaluateUtc(
                10L * HOUR, 11L * HOUR, java.util.Collections.emptyList());

        assertFalse(result.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.GAP, result.coverage.status);
    }

    @Test public void ambiguousLocalDstBoundaryRequiresExplicitOffset() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 2, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 25, 3, 0);

        ArchiveWindowCoverage.Result ambiguous = ArchiveWindowCoverage.evaluateLocal(
                start, end, berlin, null, null, java.util.Collections.emptyList());
        ArchiveWindowCoverage.Result explicit = ArchiveWindowCoverage.evaluateLocal(
                start, end, berlin, ZoneOffset.ofHours(2), null, java.util.Collections.emptyList());

        assertFalse(ambiguous.windowResolved());
        assertEquals(LocalTimeWindowResolver.Status.AMBIGUOUS_LOCAL_TIME, ambiguous.window.status);
        assertNull(ambiguous.coverage);
        assertTrue(explicit.windowResolved());
        assertEquals(2L * HOUR,
                explicit.window.endUtcMs.longValue() - explicit.window.startUtcMs.longValue());
        assertEquals(UtcCoverage.Status.GAP, explicit.coverage.status);
    }

    @Test public void nonexistentLocalSpringBoundaryIsRejectedBeforeCoverageMath() {
        ArchiveWindowCoverage.Result result = ArchiveWindowCoverage.evaluateLocal(
                LocalDateTime.of(2026, 3, 29, 2, 30),
                LocalDateTime.of(2026, 3, 29, 4, 0),
                ZoneId.of("Europe/Berlin"),
                null,
                null,
                java.util.Collections.emptyList());

        assertFalse(result.windowResolved());
        assertEquals(LocalTimeWindowResolver.Status.NONEXISTENT_LOCAL_TIME, result.window.status);
        assertNull(result.coverage);
    }

    private static ArchiveUtcProjection.Period resolved(long start, long end) {
        ArchiveUtcProjection.Boundary from = boundary(start);
        ArchiveUtcProjection.Boundary to = boundary(end);
        return new ArchiveUtcProjection.Period(
                null, ArchiveUtcProjection.PeriodStatus.RESOLVED, from, to);
    }

    private static ArchiveUtcProjection.Period openPrefix(long end) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                null,
                boundary(end));
    }

    private static ArchiveUtcProjection.Period unresolvedEnd(long start) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.END_BOUNDARY_UNRESOLVED,
                boundary(start),
                unresolvedBoundary());
    }

    private static ArchiveUtcProjection.Period unresolvedStartWithResolvedEnd(long end) {
        return new ArchiveUtcProjection.Period(
                null,
                ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                unresolvedBoundary(),
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

    private static ArchiveUtcProjection.Boundary unresolvedBoundary() {
        return new ArchiveUtcProjection.Boundary(
                null,
                ArchiveUtcProjection.BoundaryStatus.NO_SUITABLE_ANCHOR,
                null,
                -1L,
                null,
                null,
                null,
                null,
                null);
    }
}
