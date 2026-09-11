package de.marcleinen.engineeringlab.qalcosonic;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Window-scoped coverage over canonical archive UTC periods.
 *
 * <p>Unresolved archive time only dominates when its unknown UTC region can affect the requested
 * window. This prevents the naturally open start edge of the oldest stored bucket from poisoning
 * later, fully resolved requests.</p>
 */
final class ArchiveWindowCoverage {
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long DAY_MS = 24L * HOUR_MS;

    static final class Result {
        final LocalTimeWindowResolver.Window window;
        final UtcCoverage.Result coverage;
        final int projectedPeriods;
        final int resolvedIntervals;
        final boolean relevantUnresolvedTime;

        Result(
                LocalTimeWindowResolver.Window window,
                UtcCoverage.Result coverage,
                int projectedPeriods,
                int resolvedIntervals,
                boolean relevantUnresolvedTime) {
            this.window = window;
            this.coverage = coverage;
            this.projectedPeriods = projectedPeriods;
            this.resolvedIntervals = resolvedIntervals;
            this.relevantUnresolvedTime = relevantUnresolvedTime;
        }

        boolean windowResolved() {
            return window != null && window.resolved();
        }
    }

    private ArchiveWindowCoverage() { }

    static Result evaluateLocal(
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            ZoneId meterZone,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset,
            List<ArchiveUtcProjection.Period> projected) {
        LocalTimeWindowResolver.Window window = LocalTimeWindowResolver.resolveWindow(
                startLocal,
                endLocal,
                meterZone,
                explicitStartOffset,
                explicitEndOffset);
        if (!window.resolved()) {
            return new Result(window, null, size(projected), countResolved(projected), false);
        }
        return evaluateResolvedWindow(window, projected);
    }

    static Result evaluateUtc(
            long requestStartUtcMs,
            long requestEndUtcMs,
            List<ArchiveUtcProjection.Period> projected) {
        if (requestEndUtcMs <= requestStartUtcMs) {
            throw new IllegalArgumentException("positive request required");
        }
        LocalTimeWindowResolver.Window synthetic = new LocalTimeWindowResolver.Window(
                LocalTimeWindowResolver.Status.RESOLVED,
                requestStartUtcMs,
                requestEndUtcMs,
                ZoneId.of("UTC"),
                ZoneOffset.UTC,
                ZoneOffset.UTC);
        return evaluateResolvedWindow(synthetic, projected);
    }

    private static Result evaluateResolvedWindow(
            LocalTimeWindowResolver.Window window,
            List<ArchiveUtcProjection.Period> projected) {
        List<UtcCoverage.Interval> resolved = new ArrayList<>();
        if (projected != null) {
            for (ArchiveUtcProjection.Period period : projected) {
                if (period == null) continue;
                UtcCoverage.Interval interval = period.coverageInterval();
                if (interval != null) resolved.add(interval);
            }
        }
        boolean unresolved = relevantUnresolved(
                window.startUtcMs,
                window.endUtcMs,
                projected);
        UtcCoverage.Result coverage = UtcCoverage.evaluate(
                window.startUtcMs,
                window.endUtcMs,
                resolved,
                !unresolved);
        return new Result(window, coverage, size(projected), resolved.size(), unresolved);
    }

    /**
     * Treat unresolved periods as unknown UTC regions bracketed by trustworthy resolved boundaries.
     *
     * <p>A {@link ArchiveUtcProjection.PeriodStatus#NATIVE_GAP} is different: both real-time
     * boundaries are known, but native ON_TIME proves that one or more archive records are missing
     * between them. That is an ordinary known coverage gap, not an unresolved-time condition, so it
     * must not trigger the red LOCAL time-resolution warning.</p>
     *
     * <p>The first stored native archive record is special: its end boundary can be resolved while
     * its start is unavailable solely because the predecessor record is no longer stored. That
     * natural open prefix is not unbounded in physical time. A single native W1 record can span at
     * most one Hour, one Day, 31 Days for Month, or 366 Days for Year. Using only that conservative
     * maximum for warning relevance prevents an oldest Hour record from making every earlier date
     * in History look time-ambiguous. It does <strong>not</strong> resolve or render the missing
     * boundary and therefore does not weaken the fail-closed canonical UTC model.</p>
     *
     * <p>Interior unresolved runs and open suffixes remain conservatively bracketed by the nearest
     * resolved evidence. If their location cannot be bounded safely, they remain open-ended and the
     * warning is retained.</p>
     */
    private static boolean relevantUnresolved(
            long requestStartUtcMs,
            long requestEndUtcMs,
            List<ArchiveUtcProjection.Period> projected) {
        if (projected == null || projected.isEmpty()) return false;
        for (int i = 0; i < projected.size(); i++) {
            ArchiveUtcProjection.Period period = projected.get(i);
            if (period == null || period.resolvedInterval()) continue;
            if (period.status == ArchiveUtcProjection.PeriodStatus.NATIVE_GAP) continue;

            Long lower = period.startBoundary != null && period.startBoundary.resolved()
                    ? period.startBoundary.utcMs
                    : previousResolvedBoundary(projected, i - 1);
            Long upper = period.endBoundary != null && period.endBoundary.resolved()
                    ? period.endBoundary.utcMs
                    : nextResolvedBoundary(projected, i + 1);

            if (lower == null && upper != null) {
                lower = boundedNaturalOpenPrefixStart(period, upper, projected, i);
            }

            if (unknownRegionOverlaps(requestStartUtcMs, requestEndUtcMs, lower, upper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a conservative lower bound only for the natural first-record open prefix.
     * Any earlier unresolved record means this is an unresolved run, not a harmless retention edge.
     */
    private static Long boundedNaturalOpenPrefixStart(
            ArchiveUtcProjection.Period period,
            long resolvedUpperUtcMs,
            List<ArchiveUtcProjection.Period> projected,
            int index) {
        if (period == null
                || period.status != ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE
                || period.source == null
                || period.source.family == null
                || hasEarlierPeriod(projected, index)) {
            return null;
        }
        long maximumDurationMs = maximumNativeDurationMs(period.source.family);
        if (maximumDurationMs <= 0L) return null;
        try {
            return Math.subtractExact(resolvedUpperUtcMs, maximumDurationMs);
        } catch (ArithmeticException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean hasEarlierPeriod(List<ArchiveUtcProjection.Period> projected, int index) {
        if (projected == null) return false;
        for (int i = index - 1; i >= 0; i--) {
            if (projected.get(i) != null) return true;
        }
        return false;
    }

    private static long maximumNativeDurationMs(ArchiveFamilyPeriod.Family family) {
        if (family == null) return -1L;
        switch (family) {
            case HOUR:
                return HOUR_MS;
            case DAY:
                return DAY_MS;
            case MONTH:
                return 31L * DAY_MS;
            case YEAR:
                return 366L * DAY_MS;
            default:
                return -1L;
        }
    }

    private static Long previousResolvedBoundary(
            List<ArchiveUtcProjection.Period> projected,
            int fromIndex) {
        for (int i = fromIndex; i >= 0; i--) {
            ArchiveUtcProjection.Period candidate = projected.get(i);
            if (candidate != null && candidate.endBoundary != null
                    && candidate.endBoundary.resolved()) {
                return candidate.endBoundary.utcMs;
            }
        }
        return null;
    }

    private static Long nextResolvedBoundary(
            List<ArchiveUtcProjection.Period> projected,
            int fromIndex) {
        for (int i = fromIndex; i < projected.size(); i++) {
            ArchiveUtcProjection.Period candidate = projected.get(i);
            if (candidate != null && candidate.endBoundary != null
                    && candidate.endBoundary.resolved()) {
                return candidate.endBoundary.utcMs;
            }
        }
        return null;
    }

    private static boolean unknownRegionOverlaps(
            long requestStartUtcMs,
            long requestEndUtcMs,
            Long lowerUtcMs,
            Long upperUtcMs) {
        if (lowerUtcMs == null && upperUtcMs == null) return true;
        if (lowerUtcMs == null) return requestStartUtcMs < upperUtcMs;
        if (upperUtcMs == null) return requestEndUtcMs > lowerUtcMs;
        long low = Math.min(lowerUtcMs, upperUtcMs);
        long high = Math.max(lowerUtcMs, upperUtcMs);
        if (low == high) return requestStartUtcMs <= low && requestEndUtcMs > low;
        return high > requestStartUtcMs && low < requestEndUtcMs;
    }

    private static int size(List<ArchiveUtcProjection.Period> projected) {
        return projected == null ? 0 : projected.size();
    }

    private static int countResolved(List<ArchiveUtcProjection.Period> projected) {
        int count = 0;
        if (projected != null) {
            for (ArchiveUtcProjection.Period period : projected) {
                if (period != null && period.resolvedInterval()) count++;
            }
        }
        return count;
    }
}
