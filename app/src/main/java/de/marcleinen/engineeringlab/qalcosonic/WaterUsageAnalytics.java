package de.marcleinen.engineeringlab.qalcosonic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pure calculations for History deltas and water-consumption statistics. */
final class WaterUsageAnalytics {
    private static final double EPSILON_M3 = 0.0005;

    static final class Point {
        final String identity;
        final String meterId;
        final String timestamp;
        final long sortMs;
        final HistorySemanticTimeline.Granularity granularity;
        final double totalM3;

        Point(String identity, String timestamp, long sortMs,
              HistorySemanticTimeline.Granularity granularity, double totalM3) {
            this(identity, null, timestamp, sortMs, granularity, totalM3);
        }

        Point(String identity, String meterId, String timestamp, long sortMs,
              HistorySemanticTimeline.Granularity granularity, double totalM3) {
            if (identity == null || identity.trim().isEmpty()) {
                throw new IllegalArgumentException("identity required");
            }
            this.identity = identity;
            this.meterId = meterId == null || meterId.trim().isEmpty() ? null : meterId.trim();
            this.timestamp = timestamp == null ? "" : timestamp;
            this.sortMs = sortMs;
            this.granularity = granularity;
            this.totalM3 = totalM3;
        }
    }

    static final class HistoryDelta {
        final Point point;
        final Double consumptionSincePreviousM3;
        final Point previousPoint;

        HistoryDelta(Point point, Double consumptionSincePreviousM3, Point previousPoint) {
            this.point = point;
            this.consumptionSincePreviousM3 = consumptionSincePreviousM3;
            this.previousPoint = previousPoint;
        }
    }

    static final class MonthBucket {
        final String monthKey;
        final double consumptionM3;
        final boolean partial;

        MonthBucket(String monthKey, double consumptionM3, boolean partial) {
            this.monthKey = monthKey;
            this.consumptionM3 = consumptionM3;
            this.partial = partial;
        }
    }

    static final class Statistics {
        final List<MonthBucket> monthBuckets;
        final Double currentMonthM3;
        final Double currentYearM3;
        final Double averageCompleteMonthM3;
        final Double lastCompleteMonthM3;
        final Double previousCompleteMonthM3;
        final Double lastCompleteMonthChangePercent;

        Statistics(List<MonthBucket> monthBuckets, Double currentMonthM3, Double currentYearM3,
                   Double averageCompleteMonthM3, Double lastCompleteMonthM3,
                   Double previousCompleteMonthM3, Double lastCompleteMonthChangePercent) {
            this.monthBuckets = Collections.unmodifiableList(new ArrayList<>(monthBuckets));
            this.currentMonthM3 = currentMonthM3;
            this.currentYearM3 = currentYearM3;
            this.averageCompleteMonthM3 = averageCompleteMonthM3;
            this.lastCompleteMonthM3 = lastCompleteMonthM3;
            this.previousCompleteMonthM3 = previousCompleteMonthM3;
            this.lastCompleteMonthChangePercent = lastCompleteMonthChangePercent;
        }

        List<MonthBucket> currentYear(String year) {
            List<MonthBucket> result = new ArrayList<>();
            if (year == null) return result;
            for (MonthBucket bucket : monthBuckets) {
                if (bucket.monthKey.startsWith(year + "-")) result.add(bucket);
            }
            return result;
        }

        List<MonthBucket> latestMonths(int limit) {
            if (limit <= 0 || monthBuckets.isEmpty()) return new ArrayList<>();
            int from = Math.max(0, monthBuckets.size() - limit);
            return new ArrayList<>(monthBuckets.subList(from, monthBuckets.size()));
        }
    }

    private WaterUsageAnalytics() { }

    static List<HistoryDelta> historyNewestFirst(Collection<Point> input) {
        List<Point> points = new ArrayList<>(input == null ? Collections.emptyList() : input);
        points.sort(Comparator.comparingLong((Point p) -> p.sortMs)
                .thenComparing(p -> p.granularity == null ? "" : p.granularity.name())
                .thenComparing(p -> p.identity));

        List<HistoryDelta> ascending = new ArrayList<>();
        Point previousStrictTimestamp = null;
        int index = 0;
        while (index < points.size()) {
            long timestamp = points.get(index).sortMs;
            int end = index + 1;
            while (end < points.size() && points.get(end).sortMs == timestamp) end++;

            for (int i = index; i < end; i++) {
                Point point = points.get(i);
                Double delta = validDelta(previousStrictTimestamp, point);
                ascending.add(new HistoryDelta(point, delta,
                        delta == null ? null : previousStrictTimestamp));
            }

            Point reference = points.get(index);
            for (int i = index + 1; i < end; i++) {
                Point candidate = points.get(i);
                if (priority(candidate.granularity) < priority(reference.granularity)) reference = candidate;
            }
            previousStrictTimestamp = reference;
            index = end;
        }

        Collections.reverse(ascending);
        return ascending;
    }

    static Statistics statistics(Collection<Point> monthlyInput, Point latestLive, long nowMs) {
        List<Point> monthly = new ArrayList<>();
        if (monthlyInput != null) {
            for (Point point : monthlyInput) {
                if (point != null
                        && point.granularity == HistorySemanticTimeline.Granularity.MONTH
                        && monthKey(point.timestamp) != null) {
                    monthly.add(point);
                }
            }
        }
        monthly.sort(Comparator.comparing(p -> monthKey(p.timestamp)));

        List<MonthBucket> buckets = new ArrayList<>();
        for (int i = 0; i + 1 < monthly.size(); i++) {
            Point start = monthly.get(i);
            Point end = monthly.get(i + 1);
            String startKey = monthKey(start.timestamp);
            String endKey = monthKey(end.timestamp);
            Double delta = isNextMonth(startKey, endKey) ? validDelta(start, end) : null;
            if (delta != null) buckets.add(new MonthBucket(startKey, delta, false));
        }

        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date(nowMs));
        String currentYear = currentMonth.substring(0, 4);
        Double currentMonthValue = null;
        if (latestLive != null && !monthly.isEmpty()) {
            Point latestMonthStart = monthly.get(monthly.size() - 1);
            if (currentMonth.equals(monthKey(latestMonthStart.timestamp))) {
                currentMonthValue = validDelta(latestMonthStart, latestLive);
                if (currentMonthValue != null) {
                    buckets.add(new MonthBucket(currentMonth, currentMonthValue, true));
                }
            }
        }

        Double currentYearValue = null;
        if (latestLive != null) {
            Point januaryStart = null;
            for (Point point : monthly) {
                if ((currentYear + "-01").equals(monthKey(point.timestamp))) {
                    januaryStart = point;
                    break;
                }
            }
            currentYearValue = validDelta(januaryStart, latestLive);
        }

        double sum = 0.0;
        int count = 0;
        List<MonthBucket> complete = new ArrayList<>();
        for (MonthBucket bucket : buckets) {
            if (!bucket.partial) {
                complete.add(bucket);
                if (bucket.monthKey.startsWith(currentYear + "-")) {
                    sum += bucket.consumptionM3;
                    count++;
                }
            }
        }
        Double average = count == 0 ? null : sum / count;
        Double last = complete.isEmpty() ? null : complete.get(complete.size() - 1).consumptionM3;
        Double previous = complete.size() < 2 ? null : complete.get(complete.size() - 2).consumptionM3;
        Double change = null;
        if (last != null && previous != null && Math.abs(previous) > EPSILON_M3) {
            change = (last - previous) / previous * 100.0;
        }

        return new Statistics(buckets, currentMonthValue, currentYearValue, average, last, previous, change);
    }

    static String monthKey(String timestamp) {
        if (timestamp == null || timestamp.length() < 7) return null;
        String value = timestamp.substring(0, 7);
        if (value.charAt(4) != '-') return null;
        for (int i = 0; i < value.length(); i++) {
            if (i == 4) continue;
            if (!Character.isDigit(value.charAt(i))) return null;
        }
        int month;
        try { month = Integer.parseInt(value.substring(5, 7)); }
        catch (NumberFormatException error) { return null; }
        return month >= 1 && month <= 12 ? value : null;
    }

    private static boolean isNextMonth(String first, String second) {
        if (first == null || second == null) return false;
        int firstYear = Integer.parseInt(first.substring(0, 4));
        int firstMonth = Integer.parseInt(first.substring(5, 7));
        int secondYear = Integer.parseInt(second.substring(0, 4));
        int secondMonth = Integer.parseInt(second.substring(5, 7));
        if (firstMonth == 12) return secondYear == firstYear + 1 && secondMonth == 1;
        return secondYear == firstYear && secondMonth == firstMonth + 1;
    }

    private static Double validDelta(Point previous, Point current) {
        if (previous == null || current == null || current.sortMs <= previous.sortMs) return null;
        if (previous.meterId != null && current.meterId != null
                && !previous.meterId.equals(current.meterId)) return null;
        double delta = current.totalM3 - previous.totalM3;
        if (delta < -EPSILON_M3) return null;
        return Math.max(0.0, delta);
    }

    private static int priority(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == null) return 99;
        switch (granularity) {
            case LIVE: return 0;
            case HOUR: return 1;
            case DAY: return 2;
            case WEEK: return 3;
            case MONTH: return 4;
            case YEAR: return 5;
            default: return 99;
        }
    }
}
