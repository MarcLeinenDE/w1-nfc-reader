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
     * Treat each unresolved period as an unknown UTC region bracketed by the nearest resolved
     * boundary before/after it. An open prefix extends to negative infinity; an open suffix extends
     * to positive infinity. Only an overlap with the requested window is relevant.
     */
    private static boolean relevantUnresolved(
            long requestStartUtcMs,
            long requestEndUtcMs,
            List<ArchiveUtcProjection.Period> projected) {
        if (projected == null || projected.isEmpty()) return false;
        for (int i = 0; i < projected.size(); i++) {
            ArchiveUtcProjection.Period period = projected.get(i);
            if (period == null || period.resolvedInterval()) continue;

            Long lower = period.startBoundary != null && period.startBoundary.resolved()
                    ? period.startBoundary.utcMs
                    : previousResolvedBoundary(projected, i - 1);
            Long upper = period.endBoundary != null && period.endBoundary.resolved()
                    ? period.endBoundary.utcMs
                    : nextResolvedBoundary(projected, i + 1);

            if (unknownRegionOverlaps(requestStartUtcMs, requestEndUtcMs, lower, upper)) {
                return true;
            }
        }
        return false;
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
