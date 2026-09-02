package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Source-neutral semantic History model for future unified timelines.
 *
 * <p>Acquisition source is provenance, not the primary grouping. Points with the same timestamp
 * but different granularities remain independent observations and must not be collapsed.</p>
 */
final class HistorySemanticTimeline {
    enum Granularity {
        LIVE,
        HOUR,
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

    enum Provenance {
        LIVE_NFC,
        METER_ARCHIVE,
        LOCAL_DERIVED
    }

    enum Metric {
        CUMULATIVE_VOLUME,
        CONSUMPTION,
        FLOW,
        TEMPERATURE,
        BATTERY,
        STATUS_EVENT
    }

    static final class Point {
        final String identity;
        final long timestampMs;
        final Granularity granularity;
        final Provenance provenance;
        final Metric metric;
        final Double numericValue;
        final String displayUnit;

        Point(
                String identity,
                long timestampMs,
                Granularity granularity,
                Provenance provenance,
                Metric metric,
                Double numericValue,
                String displayUnit) {
            if (identity == null || identity.trim().isEmpty()) {
                throw new IllegalArgumentException("identity must not be blank");
            }
            this.identity = identity.trim();
            this.timestampMs = timestampMs;
            this.granularity = Objects.requireNonNull(granularity, "granularity");
            this.provenance = Objects.requireNonNull(provenance, "provenance");
            this.metric = Objects.requireNonNull(metric, "metric");
            this.numericValue = numericValue;
            this.displayUnit = displayUnit;
        }

        boolean isLocallyDerived() {
            return provenance == Provenance.LOCAL_DERIVED;
        }
    }

    private HistorySemanticTimeline() {
    }

    static List<Point> newestFirst(Collection<Point> points) {
        ArrayList<Point> result = new ArrayList<>(points);
        result.sort(Comparator
                .comparingLong((Point point) -> point.timestampMs)
                .reversed()
                .thenComparing(point -> point.metric)
                .thenComparing(point -> point.granularity)
                .thenComparing(point -> point.provenance)
                .thenComparing(point -> point.identity));
        return List.copyOf(result);
    }
}
