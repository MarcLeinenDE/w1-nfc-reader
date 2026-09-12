package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic LOCAL statistics coverage classification over already resolved UTC intervals. */
final class UtcCoverage {
    enum Status {
        EXACT,
        PARTIAL_EDGES,
        OVERLAP_ONLY,
        GAP,
        UNRESOLVED_TIME
    }

    static final class Interval {
        final long startUtcMs;
        final long endUtcMs;

        Interval(long startUtcMs, long endUtcMs) {
            if (endUtcMs <= startUtcMs) throw new IllegalArgumentException("positive interval required");
            this.startUtcMs = startUtcMs;
            this.endUtcMs = endUtcMs;
        }
    }

    static final class Result {
        final Status status;
        final int overlappingIntervals;
        final int fullyContainedIntervals;

        Result(Status status, int overlappingIntervals, int fullyContainedIntervals) {
            this.status = status;
            this.overlappingIntervals = overlappingIntervals;
            this.fullyContainedIntervals = fullyContainedIntervals;
        }
    }

    private UtcCoverage() { }

    static Result evaluate(
            long requestStartUtcMs,
            long requestEndUtcMs,
            List<Interval> source,
            boolean allTimesResolved) {
        if (!allTimesResolved) return new Result(Status.UNRESOLVED_TIME, 0, 0);
        if (requestEndUtcMs <= requestStartUtcMs) throw new IllegalArgumentException("positive request required");

        List<Interval> overlaps = new ArrayList<>();
        if (source != null) {
            for (Interval interval : source) {
                if (interval == null) continue;
                if (interval.endUtcMs > requestStartUtcMs && interval.startUtcMs < requestEndUtcMs) {
                    overlaps.add(interval);
                }
            }
        }
        if (overlaps.isEmpty()) return new Result(Status.GAP, 0, 0);
        overlaps.sort(Comparator.comparingLong(interval -> interval.startUtcMs));

        int contained = 0;
        boolean partialEdge = false;
        long coveredUntil = requestStartUtcMs;
        for (Interval interval : overlaps) {
            if (interval.startUtcMs >= requestStartUtcMs && interval.endUtcMs <= requestEndUtcMs) {
                contained++;
            }
            if (interval.startUtcMs < requestStartUtcMs || interval.endUtcMs > requestEndUtcMs) {
                partialEdge = true;
            }
            long clippedStart = Math.max(interval.startUtcMs, requestStartUtcMs);
            long clippedEnd = Math.min(interval.endUtcMs, requestEndUtcMs);
            if (clippedStart > coveredUntil) {
                return new Result(Status.GAP, overlaps.size(), contained);
            }
            if (clippedEnd > coveredUntil) coveredUntil = clippedEnd;
        }
        if (coveredUntil < requestEndUtcMs) {
            return new Result(Status.GAP, overlaps.size(), contained);
        }
        if (!partialEdge) return new Result(Status.EXACT, overlaps.size(), contained);
        if (contained == 0) return new Result(Status.OVERLAP_ONLY, overlaps.size(), 0);
        return new Result(Status.PARTIAL_EDGES, overlaps.size(), contained);
    }
}
