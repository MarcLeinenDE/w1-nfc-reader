package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure analytics for the bounded v2 History/Statistics data subset. */
final class HistoryStatisticsAnalytics {
    static final class Delta {
        final Double consumptionM3;
        final HistoryStatisticsRepository.Observation previous;

        Delta(Double consumptionM3, HistoryStatisticsRepository.Observation previous) {
            this.consumptionM3 = consumptionM3;
            this.previous = previous;
        }
    }

    static final class MetricPoint {
        final String timestamp;
        final double value;
        final boolean partial;
        final String segment;
        final HistorySemanticTimeline.Granularity granularity;

        MetricPoint(String timestamp, double value, boolean partial, String segment,
                    HistorySemanticTimeline.Granularity granularity) {
            this.timestamp = timestamp;
            this.value = value;
            this.partial = partial;
            this.segment = segment == null ? "" : segment;
            this.granularity = granularity;
        }
    }

    static final class ConsumptionSummary {
        final List<MetricPoint> points;
        final Double total;
        final Double average;
        final Double maximum;
        final String maximumAt;
        final Double minimum;
        final String minimumAt;
        final int availableBuckets;

        ConsumptionSummary(List<MetricPoint> points, Double total, Double average,
                           Double maximum, String maximumAt, Double minimum, String minimumAt,
                           int availableBuckets) {
            this.points = Collections.unmodifiableList(points);
            this.total = total;
            this.average = average;
            this.maximum = maximum;
            this.maximumAt = maximumAt;
            this.minimum = minimum;
            this.minimumAt = minimumAt;
            this.availableBuckets = availableBuckets;
        }
    }

    static final class TemperatureSummary {
        final List<MetricPoint> points;
        final Double latest;
        final String latestAt;
        final Double minimum;
        final String minimumAt;
        final Double maximum;
        final String maximumAt;
        final Double span;

        TemperatureSummary(List<MetricPoint> points, Double latest, String latestAt,
                           Double minimum, String minimumAt, Double maximum, String maximumAt,
                           Double span) {
            this.points = Collections.unmodifiableList(points);
            this.latest = latest;
            this.latestAt = latestAt;
            this.minimum = minimum;
            this.minimumAt = minimumAt;
            this.maximum = maximum;
            this.maximumAt = maximumAt;
            this.span = span;
        }
    }

    static final class FlowSummary {
        final List<MetricPoint> points;
        final Double maximum;
        final String maximumAt;
        final Double averagePeak;
        final Double latest;
        final String latestAt;

        FlowSummary(List<MetricPoint> points, Double maximum, String maximumAt,
                    Double averagePeak, Double latest, String latestAt) {
            this.points = Collections.unmodifiableList(points);
            this.maximum = maximum;
            this.maximumAt = maximumAt;
            this.averagePeak = averagePeak;
            this.latest = latest;
            this.latestAt = latestAt;
        }
    }

    static final class BatterySummary {
        final List<MetricPoint> points;
        final Integer start;
        final Integer end;
        final Integer change;
        final String startAt;
        final String endAt;

        BatterySummary(List<MetricPoint> points, Integer start, Integer end, Integer change,
                       String startAt, String endAt) {
            this.points = Collections.unmodifiableList(points);
            this.start = start;
            this.end = end;
            this.change = change;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    enum AlarmEventType { ACTIVATED, CLEARED, CHANGED, OBSERVED }

    static final class AlarmEvent {
        final String timestamp;
        final String meterId;
        final String raw;
        final String previousRaw;
        final AlarmEventType type;

        AlarmEvent(String timestamp, String meterId, String raw, String previousRaw,
                   AlarmEventType type) {
            this.timestamp = timestamp;
            this.meterId = meterId;
            this.raw = raw;
            this.previousRaw = previousRaw;
            this.type = type;
        }
    }

    static final class AlarmSummary {
        final List<AlarmEvent> events;
        final boolean activeAtEnd;
        final String endRaw;
        final int alarmObservations;

        AlarmSummary(List<AlarmEvent> events, boolean activeAtEnd, String endRaw,
                     int alarmObservations) {
            this.events = Collections.unmodifiableList(events);
            this.activeAtEnd = activeAtEnd;
            this.endRaw = endRaw;
            this.alarmObservations = alarmObservations;
        }
    }

    private HistoryStatisticsAnalytics() { }

    static Map<String, Delta> deltas(List<HistoryStatisticsRepository.Observation> observations) {
        List<WaterUsageAnalytics.Point> points = new ArrayList<>();
        Map<String, HistoryStatisticsRepository.Observation> byIdentity = new HashMap<>();
        if (observations != null) {
            for (HistoryStatisticsRepository.Observation observation : observations) {
                if (observation == null || observation.totalM3 == null) continue;
                points.add(new WaterUsageAnalytics.Point(observation.identity, observation.meterId,
                        observation.timestamp, observation.sortMs, observation.granularity,
                        observation.totalM3));
                byIdentity.put(observation.identity, observation);
            }
        }
        Map<String, Delta> out = new HashMap<>();
        for (WaterUsageAnalytics.HistoryDelta delta : WaterUsageAnalytics.historyNewestFirst(points)) {
            HistoryStatisticsRepository.Observation previous = delta.previousPoint == null
                    ? null : byIdentity.get(delta.previousPoint.identity);
            out.put(delta.point.identity, new Delta(delta.consumptionSincePreviousM3, previous));
        }
        return out;
    }

    static ConsumptionSummary consumption(List<HistoryStatisticsRepository.Observation> observations) {
        Map<String, Delta> deltas = deltas(observations);
        Map<String, BucketAccumulator> buckets = new TreeMap<>();
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation.contextOnly) continue;
            Delta delta = deltas.get(observation.identity);
            if (delta == null || delta.consumptionM3 == null) continue;
            BucketAccumulator bucket = buckets.computeIfAbsent(observation.timestamp,
                    ignored -> new BucketAccumulator(observation.timestamp, observation.granularity));
            bucket.value += delta.consumptionM3;
            bucket.count++;
            if (bucket.segment.length() == 0) bucket.segment = observation.meterId;
            else if (!bucket.segment.equals(observation.meterId)) bucket.segment = "MULTI";
        }

        List<MetricPoint> points = new ArrayList<>();
        double total = 0.0;
        Double max = null, min = null;
        String maxAt = null, minAt = null;
        for (BucketAccumulator bucket : buckets.values()) {
            points.add(new MetricPoint(bucket.timestamp, bucket.value, false, bucket.segment,
                    bucket.granularity));
            total += bucket.value;
            if (max == null || bucket.value > max) { max = bucket.value; maxAt = bucket.timestamp; }
            if (min == null || bucket.value < min) { min = bucket.value; minAt = bucket.timestamp; }
        }
        Double totalValue = points.isEmpty() ? null : total;
        Double average = points.isEmpty() ? null : total / points.size();
        return new ConsumptionSummary(points, totalValue, average, max, maxAt, min, minAt, points.size());
    }

    static TemperatureSummary temperature(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> selected = selected(observations);
        List<MetricPoint> points = new ArrayList<>();
        Double min = null, max = null, latest = null;
        String minAt = null, maxAt = null, latestAt = null;
        for (HistoryStatisticsRepository.Observation observation : selected) {
            Double value = validTemperature(observation.waterTemperatureC);
            if (value == null) continue;
            points.add(new MetricPoint(observation.timestamp, value, false, observation.meterId,
                    observation.granularity));
            latest = value;
            latestAt = observation.timestamp;
            if (min == null || value < min) { min = value; minAt = observation.timestamp; }
            if (max == null || value > max) { max = value; maxAt = observation.timestamp; }
        }
        return new TemperatureSummary(points, latest, latestAt, min, minAt, max, maxAt,
                min == null || max == null ? null : max - min);
    }

    static FlowSummary flow(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> selected = selected(observations);
        List<MetricPoint> points = new ArrayList<>();
        Double max = null, latest = null;
        String maxAt = null, latestAt = null;
        double sum = 0.0;
        int count = 0;
        for (HistoryStatisticsRepository.Observation observation : selected) {
            Double value = finiteNonNegative(observation.maxFlowM3h);
            if (value == null) continue;
            points.add(new MetricPoint(observation.timestamp, value, false, observation.meterId,
                    observation.granularity));
            latest = value;
            latestAt = observation.timestamp;
            sum += value;
            count++;
            if (max == null || value > max) { max = value; maxAt = observation.timestamp; }
        }
        return new FlowSummary(points, max, maxAt, count == 0 ? null : sum / count, latest, latestAt);
    }

    static BatterySummary battery(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> selected = selected(observations);
        List<MetricPoint> points = new ArrayList<>();
        Integer start = null, end = null;
        String startAt = null, endAt = null;
        for (HistoryStatisticsRepository.Observation observation : selected) {
            Integer value = validBattery(observation.batteryPercent);
            if (value == null) continue;
            points.add(new MetricPoint(observation.timestamp, value.doubleValue(), false,
                    observation.meterId, observation.granularity));
            if (start == null) { start = value; startAt = observation.timestamp; }
            end = value;
            endAt = observation.timestamp;
        }
        return new BatterySummary(points, start, end,
                start == null || end == null ? null : end - start, startAt, endAt);
    }

    static AlarmSummary alarms(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> sorted = new ArrayList<>(observations);
        sorted.sort(Comparator.comparingLong((HistoryStatisticsRepository.Observation value) -> value.sortMs)
                .thenComparing(value -> value.identity));
        Map<String, String> previousByMeter = new LinkedHashMap<>();
        List<AlarmEvent> events = new ArrayList<>();
        int alarmObservations = 0;
        String finalRaw = "0x00000000";
        boolean finalActive = false;

        for (HistoryStatisticsRepository.Observation observation : sorted) {
            if (observation.live) continue;
            String raw = MeterStatusPresentation.historical(observation.archiveErrorFlags).raw;
            boolean active = !"0x00000000".equals(raw);
            String previous = previousByMeter.get(observation.meterId);
            if (!observation.contextOnly) {
                if (active) alarmObservations++;
                AlarmEventType type = null;
                if (previous == null) {
                    if (active) type = AlarmEventType.OBSERVED;
                } else if (!previous.equals(raw)) {
                    boolean previousActive = !"0x00000000".equals(previous);
                    if (!previousActive && active) type = AlarmEventType.ACTIVATED;
                    else if (previousActive && !active) type = AlarmEventType.CLEARED;
                    else type = AlarmEventType.CHANGED;
                }
                if (type != null) events.add(new AlarmEvent(observation.timestamp,
                        observation.meterId, raw, previous, type));
                finalRaw = raw;
                finalActive = active;
            }
            previousByMeter.put(observation.meterId, raw);
        }
        return new AlarmSummary(events, finalActive, finalRaw, alarmObservations);
    }

    static Double validTemperature(Double value) {
        if (!finite(value)) return null;
        // -100 °C is present in legacy W1 archive rows as an unavailable/sentinel value.
        // Exclude only that established sentinel instead of inventing a broad physical range.
        if (Math.abs(value + 100.0) < 0.01) return null;
        return value;
    }

    private static List<HistoryStatisticsRepository.Observation> selected(
            List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> out = new ArrayList<>();
        if (observations != null) {
            for (HistoryStatisticsRepository.Observation observation : observations) {
                if (observation != null && !observation.contextOnly) out.add(observation);
            }
        }
        out.sort(Comparator.comparingLong((HistoryStatisticsRepository.Observation value) -> value.sortMs)
                .thenComparing(value -> value.identity));
        return out;
    }

    private static Integer validBattery(Integer value) {
        return value != null && value >= 0 && value <= 100 ? value : null;
    }

    private static Double finiteNonNegative(Double value) {
        return finite(value) && value >= 0.0 ? value : null;
    }

    private static boolean finite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private static final class BucketAccumulator {
        final String timestamp;
        final HistorySemanticTimeline.Granularity granularity;
        double value;
        int count;
        String segment = "";

        BucketAccumulator(String timestamp, HistorySemanticTimeline.Granularity granularity) {
            this.timestamp = timestamp;
            this.granularity = granularity;
        }
    }
}
