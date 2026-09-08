package de.marcleinen.engineeringlab.qalcosonic;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Pure wall-clock range math for custom History/Statistics windows. */
final class HistoryCustomRangeSemantics {
    private static final TimeZone FLOATING = TimeZone.getTimeZone("UTC");

    private HistoryCustomRangeSemantics() { }

    static boolean fullyContained(HistoryStatisticsRepository.Observation observation,
                                  HistoryPeriodNavigator.Window window) {
        if (observation == null || observation.live || window == null || !window.customRange
                || window.archiveStart == null || window.archiveEnd == null) return true;
        String start = HistoryTimePresentation.periodStartTimestamp(
                observation.timestamp, observation.granularity);
        return start != null
                && start.compareTo(window.archiveStart) >= 0
                && observation.timestamp.compareTo(window.archiveEnd) <= 0;
    }

    static boolean overlaps(HistoryStatisticsRepository.Observation observation,
                            HistoryPeriodNavigator.Window window) {
        if (observation == null || window == null || !window.customRange
                || window.archiveStart == null || window.archiveEnd == null) return true;
        if (observation.live) {
            return observation.deviceTimeMs >= window.deviceStartMs
                    && observation.deviceTimeMs < window.deviceEndMs;
        }
        String start = HistoryTimePresentation.periodStartTimestamp(
                observation.timestamp, observation.granularity);
        return start != null
                && start.compareTo(window.archiveEnd) < 0
                && observation.timestamp.compareTo(window.archiveStart) > 0;
    }

    static int expectedFullBuckets(HistoryPeriodNavigator.Window window,
                                   HistorySemanticTimeline.Granularity granularity) {
        if (window == null || !window.customRange || window.archiveStart == null
                || window.archiveEnd == null || !archiveGranularity(granularity)) return 0;
        Calendar cursor = parse(window.archiveStart);
        Calendar end = parse(window.archiveEnd);
        if (cursor == null || end == null || !cursor.before(end)) return 0;
        ceilBoundary(cursor, granularity);
        int count = 0;
        while (true) {
            Calendar next = (Calendar) cursor.clone();
            addPeriod(next, granularity, 1);
            if (next.after(end)) break;
            count++;
            cursor = next;
            if (count > 100000) break;
        }
        return count;
    }

    static boolean exactEdges(HistoryPeriodNavigator.Window window,
                              HistorySemanticTimeline.Granularity granularity) {
        if (window == null || !window.customRange || window.archiveStart == null
                || window.archiveEnd == null || !archiveGranularity(granularity)) return true;
        Calendar start = parse(window.archiveStart);
        Calendar end = parse(window.archiveEnd);
        return start != null && end != null
                && isBoundary(start, granularity) && isBoundary(end, granularity);
    }

    static String exactInteriorStart(HistoryPeriodNavigator.Window window,
                                     HistorySemanticTimeline.Granularity granularity) {
        if (window == null || window.archiveStart == null) return null;
        Calendar value = parse(window.archiveStart);
        if (value == null) return null;
        ceilBoundary(value, granularity);
        return canonical(value);
    }

    static String exactInteriorEnd(HistoryPeriodNavigator.Window window,
                                   HistorySemanticTimeline.Granularity granularity) {
        if (window == null || window.archiveEnd == null) return null;
        Calendar value = parse(window.archiveEnd);
        if (value == null) return null;
        floorBoundary(value, granularity);
        return canonical(value);
    }

    static HistorySemanticTimeline.Granularity automaticResolution(
            HistoryPeriodNavigator.Window window,
            HistoryStatisticsRepository.Availability availability) {
        if (window == null || !window.customRange) {
            return HistoryStatisticsRepository.targetGranularity(
                    window == null ? HistoryPeriodNavigator.Scale.MONTH : window.scale);
        }
        long duration = Math.max(0L, window.deviceEndMs - window.deviceStartMs);
        HistorySemanticTimeline.Granularity preferred;
        if (duration <= 7L * 24L * 60L * 60L * 1000L) {
            preferred = HistorySemanticTimeline.Granularity.HOUR;
        } else if (duration <= 120L * 24L * 60L * 60L * 1000L) {
            preferred = HistorySemanticTimeline.Granularity.DAY;
        } else {
            preferred = HistorySemanticTimeline.Granularity.MONTH;
        }
        if (availability == null || availability.count(preferred) > 0) return preferred;
        if (availability.hour > 0) return HistorySemanticTimeline.Granularity.HOUR;
        if (availability.day > 0) return HistorySemanticTimeline.Granularity.DAY;
        if (availability.month > 0) return HistorySemanticTimeline.Granularity.MONTH;
        return preferred;
    }

    private static boolean archiveGranularity(HistorySemanticTimeline.Granularity value) {
        return value == HistorySemanticTimeline.Granularity.HOUR
                || value == HistorySemanticTimeline.Granularity.DAY
                || value == HistorySemanticTimeline.Granularity.MONTH
                || value == HistorySemanticTimeline.Granularity.YEAR;
    }

    private static Calendar parse(String value) {
        if (value == null) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(FLOATING);
        try {
            Date parsed = format.parse(value);
            if (parsed == null) return null;
            Calendar calendar = Calendar.getInstance(FLOATING);
            calendar.setTime(parsed);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar;
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static String canonical(Calendar value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(FLOATING);
        return format.format(value.getTime());
    }

    private static boolean isBoundary(Calendar value,
                                      HistorySemanticTimeline.Granularity granularity) {
        if (value.get(Calendar.MINUTE) != 0 || value.get(Calendar.SECOND) != 0
                || value.get(Calendar.MILLISECOND) != 0) return false;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return true;
        if (value.get(Calendar.HOUR_OF_DAY) != 0) return false;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return true;
        if (value.get(Calendar.DAY_OF_MONTH) != 1) return false;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return true;
        return granularity != HistorySemanticTimeline.Granularity.YEAR
                || value.get(Calendar.MONTH) == Calendar.JANUARY;
    }

    private static void ceilBoundary(Calendar value,
                                     HistorySemanticTimeline.Granularity granularity) {
        if (isBoundary(value, granularity)) return;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) {
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            value.add(Calendar.HOUR_OF_DAY, 1);
            return;
        }
        if (granularity == HistorySemanticTimeline.Granularity.DAY) {
            value.set(Calendar.HOUR_OF_DAY, 0);
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            value.add(Calendar.DAY_OF_MONTH, 1);
            return;
        }
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) {
            value.set(Calendar.DAY_OF_MONTH, 1);
            value.set(Calendar.HOUR_OF_DAY, 0);
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            value.add(Calendar.MONTH, 1);
            return;
        }
        value.set(Calendar.MONTH, Calendar.JANUARY);
        value.set(Calendar.DAY_OF_MONTH, 1);
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        value.add(Calendar.YEAR, 1);
    }

    private static void floorBoundary(Calendar value,
                                      HistorySemanticTimeline.Granularity granularity) {
        if (isBoundary(value, granularity)) return;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) {
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            return;
        }
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return;
        value.set(Calendar.DAY_OF_MONTH, 1);
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return;
        value.set(Calendar.MONTH, Calendar.JANUARY);
    }

    private static void addPeriod(Calendar value,
                                  HistorySemanticTimeline.Granularity granularity, int amount) {
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) {
            value.add(Calendar.HOUR_OF_DAY, amount);
        } else if (granularity == HistorySemanticTimeline.Granularity.DAY) {
            value.add(Calendar.DAY_OF_MONTH, amount);
        } else if (granularity == HistorySemanticTimeline.Granularity.MONTH) {
            value.add(Calendar.MONTH, amount);
        } else {
            value.add(Calendar.YEAR, amount);
        }
    }
}
