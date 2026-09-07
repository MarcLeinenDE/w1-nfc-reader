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
        return formatArchivePeriod(use, observation.granularity, observation.timestamp);
    }

    /**
     * Formats an archive logger boundary as the completed interval that ends at that boundary.
     * Stored timestamps remain unchanged; only product presentation is shifted to the period they
     * semantically close (for example 2025-10-01 00:00 MONTH -> September 2025).
     */
    static String formatArchivePeriod(Locale locale,
                                      HistorySemanticTimeline.Granularity granularity,
                                      String boundaryTimestamp) {
        Locale use = locale == null ? Locale.getDefault() : locale;
        String start = periodStartTimestamp(boundaryTimestamp, granularity);
        if (start == null) return boundaryTimestamp == null ? "" : boundaryTimestamp;
        return formatPeriodStart(use, granularity, start);
    }

    /** Formats a period whose supplied timestamp is already its start boundary. */
    static String formatPeriodStart(Locale locale,
                                    HistorySemanticTimeline.Granularity granularity,
                                    String startTimestamp) {
        Locale use = locale == null ? Locale.getDefault() : locale;
        Date start = parseFloating(startTimestamp);
        if (start == null) return startTimestamp == null ? "" : startTimestamp;
        HistorySemanticTimeline.Granularity useGranularity = granularity == null
                ? HistorySemanticTimeline.Granularity.HOUR : granularity;
        switch (useGranularity) {
            case HOUR: {
                Date end = add(start, Calendar.HOUR_OF_DAY, 1);
                if (sameFloatingDate(start, end)) {
                    return formatFloatingDate(use, start) + " · "
                            + formatFloatingTime(use, start) + "–" + formatFloatingTime(use, end);
                }
                return formatFloatingDate(use, start) + " · " + formatFloatingTime(use, start)
                        + " – " + formatFloatingDate(use, end) + " · " + formatFloatingTime(use, end);
            }
            case DAY:
                return formatFloatingDate(use, start);
            case MONTH:
                return formatFloatingMonth(use, start);
            case YEAR:
                return formatFloatingYear(use, start);
            case LIVE:
            default:
                return formatFloatingDate(use, start) + " · " + formatFloatingTime(use, start);
        }
    }

    /** Returns the canonical floating start boundary for the period ending at {@code boundary}. */
    static String periodStartTimestamp(String boundaryTimestamp,
                                       HistorySemanticTimeline.Granularity granularity) {
        Date boundary = parseFloating(boundaryTimestamp);
        if (boundary == null || granularity == null || granularity == HistorySemanticTimeline.Granularity.LIVE) {
            return boundaryTimestamp;
        }
        Calendar c = Calendar.getInstance(FLOATING_ZONE, Locale.US);
        c.setTime(boundary);
        switch (granularity) {
            case HOUR: c.add(Calendar.HOUR_OF_DAY, -1); break;
            case DAY: c.add(Calendar.DAY_OF_MONTH, -1); break;
            case MONTH: c.add(Calendar.MONTH, -1); break;
            case YEAR: c.add(Calendar.YEAR, -1); break;
            default: return boundaryTimestamp;
        }
        return canonicalFloating(c.getTime());
    }

    /** Raw floating archive time, for explicit extrema/event timestamps that are not period keys. */
    static String formatExactFloatingDateTime(Locale locale, String timestamp) {
        Locale use = locale == null ? Locale.getDefault() : locale;
        Date floating = parseFloating(timestamp);
        if (floating == null) return timestamp == null ? "" : timestamp;
        return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
    }

    static String formatFloatingPrimary(Locale locale,
                                        HistorySemanticTimeline.Granularity granularity,
                                        String timestamp) {
        Locale use = locale == null ? Locale.getDefault() : locale;
        Date floating = parseFloating(timestamp);
        if (floating == null) return timestamp == null ? "" : timestamp;
        HistorySemanticTimeline.Granularity useGranularity = granularity == null
                ? HistorySemanticTimeline.Granularity.HOUR : granularity;
        switch (useGranularity) {
            case HOUR:
                return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
            case DAY:
                return formatFloatingDate(use, floating);
            case MONTH:
                return formatFloatingMonth(use, floating);
            case YEAR:
                return formatFloatingYear(use, floating);
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
        if (previous.live && previous.deviceTimeMs > 0L) {
            return formatDeviceDateTime(use, previous.deviceTimeMs);
        }
        if (!previous.live) {
            HistorySemanticTimeline.Granularity granularity = previous.granularity == null
                    ? current.granularity : previous.granularity;
            return formatArchivePeriod(use, granularity, previous.timestamp);
        }
        Date floating = parseFloating(previous.timestamp);
        if (floating == null) return previous.timestamp == null ? "" : previous.timestamp;
        return formatFloatingDate(use, floating) + " · " + formatFloatingTime(use, floating);
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

    private static String formatFloatingYear(Locale locale, Date date) {
        SimpleDateFormat year = new SimpleDateFormat("yyyy", locale);
        year.setTimeZone(FLOATING_ZONE);
        return year.format(date);
    }

    private static String canonicalFloating(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(FLOATING_ZONE);
        return format.format(date);
    }

    private static Date add(Date date, int field, int amount) {
        Calendar c = Calendar.getInstance(FLOATING_ZONE);
        c.setTime(date);
        c.add(field, amount);
        return c.getTime();
    }

    private static Date parseFloating(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(FLOATING_ZONE);
        try { return format.parse(timestamp.trim()); }
        catch (ParseException ignored) { return null; }
    }

    private static boolean sameFloatingDate(Date first, Date second) {
        Calendar a = Calendar.getInstance(FLOATING_ZONE);
        Calendar b = Calendar.getInstance(FLOATING_ZONE);
        a.setTime(first);
        b.setTime(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
