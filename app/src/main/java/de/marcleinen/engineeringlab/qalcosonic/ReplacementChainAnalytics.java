package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates only trustworthy within-meter consumption segments across an explicitly confirmed
 * replacement chain. It never subtracts cumulative readings belonging to two physical meters.
 */
final class ReplacementChainAnalytics {
    static final class Result {
        final Double knownConsumptionM3;
        final boolean partial;
        final int includedSegments;

        Result(Double knownConsumptionM3, boolean partial, int includedSegments) {
            this.knownConsumptionM3 = knownConsumptionM3;
            this.partial = partial;
            this.includedSegments = includedSegments;
        }
    }

    private ReplacementChainAnalytics() { }

    static Result periodConsumption(Collection<WaterUsageAnalytics.Point> input,
                                    List<MeterLifecycleStore.Transition> transitions,
                                    long periodStartMs, long periodEndMs) {
        if (periodStartMs <= 0L || periodEndMs <= periodStartMs) return new Result(null, true, 0);
        List<WaterUsageAnalytics.Point> points = new ArrayList<>();
        if (input != null) {
            for (WaterUsageAnalytics.Point point : input) {
                if (point != null && point.meterId != null) points.add(point);
            }
        }
        points.sort(Comparator.comparingLong((WaterUsageAnalytics.Point p) -> p.sortMs)
                .thenComparing(p -> p.identity));
        if (points.isEmpty()) return new Result(null, true, 0);

        // A trustworthy period needs a real boundary point at its start. Archive month/year
        // boundaries are exact to the minute; allow one minute for representation differences.
        boolean startBoundaryKnown = false;
        for (WaterUsageAnalytics.Point point : points) {
            if (Math.abs(point.sortMs - periodStartMs) <= 60_000L) {
                startBoundaryKnown = true;
                break;
            }
        }

        List<WaterUsageAnalytics.HistoryDelta> deltas = WaterUsageAnalytics.historyNewestFirst(points);
        double sum = 0.0;
        int count = 0;
        for (WaterUsageAnalytics.HistoryDelta delta : deltas) {
            if (delta.consumptionSincePreviousM3 == null || delta.previousPoint == null) continue;
            long from = delta.previousPoint.sortMs;
            long to = delta.point.sortMs;
            if (from < periodStartMs || to > periodEndMs) continue;
            // WaterUsageAnalytics already rejects a raw cross-meter subtraction when meter IDs
            // are supplied. Keep the explicit check here as a second product-boundary guard.
            if (!delta.point.meterId.equals(delta.previousPoint.meterId)) continue;
            sum += delta.consumptionSincePreviousM3;
            count++;
        }

        boolean replacementInsidePeriod = false;
        if (transitions != null) {
            for (MeterLifecycleStore.Transition transition : transitions) {
                if (transition.confirmedAtMs > periodStartMs && transition.confirmedAtMs <= periodEndMs) {
                    replacementInsidePeriod = true;
                    break;
                }
            }
        }

        // A confirmed replacement creates an intentionally visible cut. Without an official
        // exchange reading/date the uncovered old->new gap is never invented, so the total is
        // marked partial even though all known same-meter segments are still added.
        boolean partial = !startBoundaryKnown || replacementInsidePeriod;
        return count == 0 ? new Result(null, partial, 0) : new Result(sum, partial, count);
    }
}
