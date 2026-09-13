package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class UtcCoverageTest {
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long MINUTE = 60L * 1000L;

    @Test public void shiftedOneHourRequestWithTwoEdgeBucketsIsOverlapOnly() {
        long start = 17L * HOUR;
        long end = 18L * HOUR;
        UtcCoverage.Result result = UtcCoverage.evaluate(start, end, Arrays.asList(
                new UtcCoverage.Interval(16L * HOUR + 55L * MINUTE, 17L * HOUR + 55L * MINUTE),
                new UtcCoverage.Interval(17L * HOUR + 55L * MINUTE, 18L * HOUR + 55L * MINUTE)
        ), true);

        assertEquals(UtcCoverage.Status.OVERLAP_ONLY, result.status);
        assertEquals(2, result.overlappingIntervals);
        assertEquals(0, result.fullyContainedIntervals);
    }

    @Test public void completeInnerBucketsWithPartialEdgesAreClassifiedSeparately() {
        long start = 10L * HOUR;
        long end = 13L * HOUR;
        UtcCoverage.Result result = UtcCoverage.evaluate(start, end, Arrays.asList(
                new UtcCoverage.Interval(9L * HOUR + 55L * MINUTE, 10L * HOUR + 55L * MINUTE),
                new UtcCoverage.Interval(10L * HOUR + 55L * MINUTE, 11L * HOUR + 55L * MINUTE),
                new UtcCoverage.Interval(11L * HOUR + 55L * MINUTE, 12L * HOUR + 55L * MINUTE),
                new UtcCoverage.Interval(12L * HOUR + 55L * MINUTE, 13L * HOUR + 55L * MINUTE)
        ), true);

        assertEquals(UtcCoverage.Status.PARTIAL_EDGES, result.status);
        assertEquals(2, result.fullyContainedIntervals);
    }

    @Test public void alignedCompleteBucketsAreExact() {
        UtcCoverage.Result result = UtcCoverage.evaluate(10L * HOUR, 12L * HOUR, Arrays.asList(
                new UtcCoverage.Interval(10L * HOUR, 11L * HOUR),
                new UtcCoverage.Interval(11L * HOUR, 12L * HOUR)
        ), true);

        assertEquals(UtcCoverage.Status.EXACT, result.status);
        assertEquals(2, result.fullyContainedIntervals);
    }

    @Test public void missingBucketInsideRangeIsGap() {
        UtcCoverage.Result result = UtcCoverage.evaluate(10L * HOUR, 13L * HOUR, Arrays.asList(
                new UtcCoverage.Interval(10L * HOUR, 11L * HOUR),
                new UtcCoverage.Interval(12L * HOUR, 13L * HOUR)
        ), true);

        assertEquals(UtcCoverage.Status.GAP, result.status);
    }

    @Test public void unresolvedTimestampDominatesCoverageMath() {
        UtcCoverage.Result result = UtcCoverage.evaluate(10L * HOUR, 11L * HOUR, Arrays.asList(
                new UtcCoverage.Interval(10L * HOUR, 11L * HOUR)
        ), false);

        assertEquals(UtcCoverage.Status.UNRESOLVED_TIME, result.status);
    }
}
