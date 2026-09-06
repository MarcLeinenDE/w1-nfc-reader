package de.marcleinen.engineeringlab.qalcosonic;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Locale-aware presentation for device timestamps and floating meter logger timestamps. */
final class HistoryTimePresentation {
    private static final TimeZone FLOATING_ZONE = TimeZone.getTimeZone("UTC");

    private HistoryTimePresentation() { }

    static String formatPrimary(Locale locale, HistoryStatisticsRepository.Observation observation) {
        if (observation == null) return "";
        Locale use = locale == null ? Locale.getDefault() : locale;
        if (observation.live && observation.deviceTimeMs > 0L) {
            return formatDeviceDateTime(use, observation.deviceTimeMs);
        }
        Date floating = parseFloating(observation.timestamp);
        if (floating == null) return observation.timestamp == null ? "" : observation.timestamp;
        switch (observation.granularity) {
            case HOUR:
                return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
            case DAY:
                return formatFloatingDate(use, floating);
            case MONTH:
                return formatFloatingMonth(use, floating);
            case YEAR:
                return new SimpleDateFormat("yyyy", use) {{ setTimeZone(FLOATING_ZONE); }}.format(floating);
            case LIVE:
            default:
                return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
        }
    }

    static String formatPredecessor(Locale locale,
                                    HistoryStatisticsRepository.Observation current,
                                    HistoryStatisticsRepository.Observation previous) {
        if (current == null || previous == null) return "";
        Locale use = locale == null ? Locale.getDefault() : locale;
        if (previous.live && previous.deviceTimeMs > 0L) return formatDeviceDateTime(use, previous.deviceTimeMs);
        Date floating = parseFloating(previous.timestamp);
        if (floating == null) return previous.timestamp == null ? "" : previous.timestamp;
        switch (current.granularity) {
            case HOUR:
                if (sameFloatingDate(current.timestamp, previous.timestamp)) return formatFloatingTime(use, floating);
                return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
            case DAY:
                return formatFloatingDate(use, floating);
            case MONTH:
                return formatFloatingMonth(use, floating);
            case YEAR:
                SimpleDateFormat year = new SimpleDateFormat("yyyy", use);
                year.setTimeZone(FLOATING_ZONE);
                return year.format(floating);
            case LIVE:
            default:
                return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
        }
    }

    static String formatAxis(Locale locale, HistorySemanticTimeline.Granularity granularity,
                             String timestamp) {
        Locale use = locale == null ? Locale.getDefault() : locale;
        Date floating = parseFloating(timestamp);
        if (floating == null) return timestamp == null ? "" : timestamp;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) {
            return formatFloatingTime(use, floating);
        }
        if (granularity == HistorySemanticTimeline.Granularity.DAY) {
            SimpleDateFormat day = new SimpleDateFormat("d", use);
            day.setTimeZone(FLOATING_ZONE);
            return day.format(floating);
        }
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) {
            SimpleDateFormat month = new SimpleDateFormat("MMM", use);
            month.setTimeZone(FLOATING_ZONE);
            return month.format(floating);
        }
        return formatFloatingDate(use, floating);
    }

    static long floatingSortMs(String timestamp) {
        Date value = parseFloating(timestamp);
        return value == null ? 0L : value.getTime();
    }

    static String localMinute(long deviceTimeMs) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(deviceTimeMs));
    }

    static boolean adjacent(String first, String second, HistorySemanticTimeline.Granularity granularity) {
        Date a = parseFloating(first);
        Date b = parseFloating(second);
        if (a == null || b == null || granularity == null) return false;
        Calendar c = Calendar.getInstance(FLOATING_ZONE);
        c.setTime(a);
        switch (granularity) {
            case HOUR: c.add(Calendar.HOUR_OF_DAY, 1); break;
            case DAY: c.add(Calendar.DAY_OF_MONTH, 1); break;
            case MONTH: c.add(Calendar.MONTH, 1); break;
            case YEAR: c.add(Calendar.YEAR, 1); break;
            default: return false;
        }
        return c.getTimeInMillis() == b.getTime();
    }

    private static String formatDeviceDateTime(Locale locale, long ms) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .format(new Date(ms));
    }

    private static String formatFloatingDate(Locale locale, Date date) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
        format.setTimeZone(FLOATING_ZONE);
        return format.format(date);
    }

    private static String formatFloatingTime(Locale locale, Date date) {
        DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        format.setTimeZone(FLOATING_ZONE);
        return format.format(date);
    }

    private static String formatFloatingMonth(Locale locale, Date date) {
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", locale);
        format.setTimeZone(FLOATING_ZONE);
        return format.format(date);
    }

    private static Date parseFloating(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(FLOATING_ZONE);
        try { return format.parse(timestamp.trim()); }
        catch (ParseException ignored) { return null; }
    }

    private static boolean sameFloatingDate(String first, String second) {
        return first != null && second != null && first.length() >= 10 && second.length() >= 10
                && first.substring(0, 10).equals(second.substring(0, 10));
    }
}
