package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure analytics for the bounded v2 History/Statistics data subset. */
final class HistoryStatisticsAnalytics {
    private static final double DELTA_EPSILON_M3 = 0.0005;

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
        List<HistoryStatisticsRepository.Observation> ordered = new ArrayList<>();
        if (observations != null) {
            for (HistoryStatisticsRepository.Observation observation : observations) {
                if (observation == null || observation.totalM3 == null) continue;
                points.add(new WaterUsageAnalytics.Point(observation.identity, observation.meterId,
                        observation.timestamp, observation.sortMs, observation.granularity,
                        observation.totalM3));
                byIdentity.put(observation.identity, observation);
                ordered.add(observation);
            }
        }

        // Archive cards keep the established same-meter + same-granularity predecessor contract.
        Map<String, Delta> out = new HashMap<>();
        for (WaterUsageAnalytics.HistoryDelta delta : WaterUsageAnalytics.historyNewestFirst(points)) {
            HistoryStatisticsRepository.Observation previous = delta.previousPoint == null
                    ? null : byIdentity.get(delta.previousPoint.identity);
            out.put(delta.point.identity, new Delta(delta.consumptionSincePreviousM3, previous));
        }

        // Live is intentionally different: it is a point-in-time read, not a completed archive
        // period. Compare it with the newest strictly earlier known total for the same physical
        // meter, regardless of whether that reference is Live, Hour, Day or Month.
        ordered.sort(Comparator.comparingLong((HistoryStatisticsRepository.Observation value) -> value.sortMs)
                .thenComparing(value -> value.identity));
        Map<String, HistoryStatisticsRepository.Observation> latestByMeter = new HashMap<>();
        int index = 0;
        while (index < ordered.size()) {
            long timestamp = ordered.get(index).sortMs;
            int end = index + 1;
            while (end < ordered.size() && ordered.get(end).sortMs == timestamp) end++;

            for (int i = index; i < end; i++) {
                HistoryStatisticsRepository.Observation current = ordered.get(i);
                if (!current.live) continue;
                HistoryStatisticsRepository.Observation previous = latestByMeter.get(meterKey(current));
                Double value = validLiveDelta(previous, current);
                out.put(current.identity, new Delta(value, value == null ? null : previous));
            }

            Map<String, HistoryStatisticsRepository.Observation> bestAtTimestamp = new HashMap<>();
            for (int i = index; i < end; i++) {
                HistoryStatisticsRepository.Observation candidate = ordered.get(i);
                String meter = meterKey(candidate);
                HistoryStatisticsRepository.Observation existing = bestAtTimestamp.get(meter);
                if (existing == null || preferredLiveReference(candidate, existing)) {
                    bestAtTimestamp.put(meter, candidate);
                }
            }
            latestByMeter.putAll(bestAtTimestamp);
            index = end;
        }
        return out;
    }

    static ConsumptionSummary consumption(List<HistoryStatisticsRepository.Observation> observations) {
        Map<String, Delta> deltas = deltas(observations);
        Map<String, BucketAccumulator> buckets = new TreeMap<>();
        if (observations != null) {
            for (HistoryStatisticsRepository.Observation observation : observations) {
                if (observation == null || observation.contextOnly) continue;
                Delta delta = deltas.get(observation.identity);
                if (delta == null || delta.consumptionM3 == null || delta.previous == null) continue;

                // Statistics only consume archive series. Live deltas are a History presentation
                // feature and must never be promoted into natural Hour/Day/Month chart buckets.
                if (observation.live) continue;

                // Archive rows are end boundaries. The exact window-start boundary may be context
                // only, but it is still the valid start of the first displayed period.
                HistoryStatisticsRepository.Observation start = delta.previous;
                if (!HistoryTimePresentation.adjacent(start.timestamp, observation.timestamp,
                        observation.granularity)) continue;

                String meter = start.meterId == null ? "" : start.meterId;
                String key = start.timestamp + '\u0000' + meter;
                BucketAccumulator bucket = buckets.computeIfAbsent(key,
                        ignored -> new BucketAccumulator(start.timestamp, start.granularity, meter));
                bucket.value += delta.consumptionM3;
                bucket.count++;
            }
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
            String period = periodStart(observation);
            points.add(new MetricPoint(period, value, false, observation.meterId,
                    observation.granularity));
            latest = value;
            latestAt = period;
            if (min == null || value < min) { min = value; minAt = period; }
            if (max == null || value > max) { max = value; maxAt = period; }
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
            String period = periodStart(observation);
            points.add(new MetricPoint(period, value, false, observation.meterId,
                    observation.granularity));
            latest = value;
            latestAt = period;
            sum += value;
            count++;
            if (max == null || value > max) {
                max = value;
                maxAt = nonEmpty(observation.maxFlowAt) ? observation.maxFlowAt : period;
            }
        }
        return new FlowSummary(points, max, maxAt, count == 0 ? null : sum / count, latest, latestAt);
    }

    static BatterySummary battery(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> selected = selected(observations);
        List<MetricPoint> points = new ArrayList<>();
        Integer start = null, end = null;
        String startAt = null, endAt = null;
        String startMeter = null, endMeter = null;
        for (HistoryStatisticsRepository.Observation observation : selected) {
            Integer value = validBattery(observation.batteryPercent);
            if (value == null) continue;
            String period = periodStart(observation);
            points.add(new MetricPoint(period, value.doubleValue(), false,
                    observation.meterId, observation.granularity));
            if (start == null) {
                start = value;
                startAt = period;
                startMeter = observation.meterId;
            }
            end = value;
            endAt = period;
            endMeter = observation.meterId;
        }
        Integer change = start == null || end == null || startMeter == null || endMeter == null
                || !startMeter.equals(endMeter) ? null : end - start;
        return new BatterySummary(points, start, end, change, startAt, endAt);
    }

    static AlarmSummary alarms(List<HistoryStatisticsRepository.Observation> observations) {
        List<HistoryStatisticsRepository.Observation> sorted = selected(observations);
        List<AlarmEvent> events = new ArrayList<>();
        int alarmObservations = 0;
        String finalRaw = "0x00000000";
        boolean finalActive = false;

        // Historical ERROR_FLAGS are period evidence. Do not promote repeated rows to separate
        // incidents and do not invent an exact activation/clear timestamp. Each non-zero row says
        // only that the status was registered within the completed archive period.
        for (HistoryStatisticsRepository.Observation observation : sorted) {
            if (observation.live) continue;
            String raw = MeterStatusPresentation.historical(observation.archiveErrorFlags).raw;
            boolean active = !"0x00000000".equals(raw);
            finalRaw = raw;
            finalActive = active;
            if (!active) continue;
            alarmObservations++;
            events.add(new AlarmEvent(periodStart(observation), observation.meterId, raw,
                    null, AlarmEventType.OBSERVED));
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

    private static Double validLiveDelta(HistoryStatisticsRepository.Observation previous,
                                         HistoryStatisticsRepository.Observation current) {
        if (previous == null || current == null || previous.totalM3 == null || current.totalM3 == null) {
            return null;
        }
        if (current.sortMs <= previous.sortMs) return null;
        if (!meterKey(previous).equals(meterKey(current))) return null;
        double delta = current.totalM3 - previous.totalM3;
        if (delta < -DELTA_EPSILON_M3) return null;
        return Math.max(0.0, delta);
    }

    private static boolean preferredLiveReference(HistoryStatisticsRepository.Observation candidate,
                                                  HistoryStatisticsRepository.Observation existing) {
        int candidatePriority = referencePriority(candidate.granularity);
        int existingPriority = referencePriority(existing.granularity);
        if (candidatePriority != existingPriority) return candidatePriority > existingPriority;
        return candidate.identity.compareTo(existing.identity) > 0;
    }

    private static int referencePriority(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.LIVE) return 5;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return 4;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return 3;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return 2;
        if (granularity == HistorySemanticTimeline.Granularity.YEAR) return 1;
        return 0;
    }

    private static String meterKey(HistoryStatisticsRepository.Observation observation) {
        if (observation == null || observation.meterId == null || observation.meterId.trim().isEmpty()) {
            return "<unknown-meter>";
        }
        return observation.meterId.trim();
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

    private static String periodStart(HistoryStatisticsRepository.Observation observation) {
        String value = HistoryTimePresentation.periodStartTimestamp(
                observation.timestamp, observation.granularity);
        return value == null ? observation.timestamp : value;
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
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
        final String segment;
        double value;
        int count;

        BucketAccumulator(String timestamp, HistorySemanticTimeline.Granularity granularity,
                          String segment) {
            this.timestamp = timestamp;
            this.granularity = granularity;
            this.segment = segment == null ? "" : segment;
        }
    }
}
