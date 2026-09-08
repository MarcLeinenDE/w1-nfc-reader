package de.marcleinen.engineeringlab.qalcosonic;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Shared calendar/range model for v2 History and Statistics. */
final class HistoryPeriodNavigator {
    enum Scale { DAY, MONTH, YEAR }

    static final class Window {
        final Scale scale;
        final boolean allPeriods;
        final boolean customRange;
        final int year;
        final int month0;
        final int day;
        final String archiveStart;
        final String archiveEnd;
        final long deviceStartMs;
        final long deviceEndMs;
        final int expectedBuckets;

        Window(Scale scale, boolean allPeriods, boolean customRange,
               int year, int month0, int day,
               String archiveStart, String archiveEnd, long deviceStartMs, long deviceEndMs,
               int expectedBuckets) {
            this.scale = scale;
            this.allPeriods = allPeriods;
            this.customRange = customRange;
            this.year = year;
            this.month0 = month0;
            this.day = day;
            this.archiveStart = archiveStart;
            this.archiveEnd = archiveEnd;
            this.deviceStartMs = deviceStartMs;
            this.deviceEndMs = deviceEndMs;
            this.expectedBuckets = expectedBuckets;
        }
    }

    private Scale scale;
    private int year;
    private int month0;
    private int day;
    private boolean allPeriods;
    private boolean customRange;
    private long customStartMs;
    private long customEndMs;

    HistoryPeriodNavigator(Scale scale) {
        Calendar now = Calendar.getInstance();
        this.scale = scale == null ? Scale.MONTH : scale;
        this.year = now.get(Calendar.YEAR);
        this.month0 = now.get(Calendar.MONTH);
        this.day = now.get(Calendar.DAY_OF_MONTH);
        normalizeDay();
    }

    static Scale forHistory(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.LIVE
                || granularity == HistorySemanticTimeline.Granularity.HOUR) return Scale.DAY;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return Scale.MONTH;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH
                || granularity == HistorySemanticTimeline.Granularity.YEAR) return Scale.YEAR;
        return Scale.MONTH;
    }

    Scale scale() { return scale; }
    int year() { return year; }
    int month0() { return month0; }
    int day() { return day; }
    boolean allPeriods() { return allPeriods; }
    boolean customRange() { return customRange; }
    long customStartMs() { return customStartMs; }
    long customEndMs() { return customEndMs; }

    /** Changes the calendar navigation scale without destroying an active custom range. */
    void setScale(Scale value) {
        if (value == null || value == scale) return;
        scale = value;
        normalizeDay();
    }

    /** Explicit user selection of Day/Month/Year exits All/custom-range mode. */
    void selectScale(Scale value) {
        if (value != null) scale = value;
        allPeriods = false;
        customRange = false;
        normalizeDay();
    }

    void setAllPeriods(boolean value) {
        allPeriods = value;
        if (value) customRange = false;
    }

    void setDate(int year, int month0, int day) {
        this.year = year;
        this.month0 = month0;
        this.day = day;
        allPeriods = false;
        customRange = false;
        normalizeDay();
    }

    void setCustomRange(long startMs, long endMs) {
        if (startMs <= 0L || endMs <= startMs) {
            throw new IllegalArgumentException("custom range end must be after start");
        }
        customStartMs = startMs;
        customEndMs = endMs;
        allPeriods = false;
        customRange = true;
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startMs);
        year = start.get(Calendar.YEAR);
        month0 = start.get(Calendar.MONTH);
        day = start.get(Calendar.DAY_OF_MONTH);
        normalizeDay();
    }

    void move(int amount) {
        if (amount == 0 || customRange || allPeriods) return;
        Calendar calendar = localCalendar();
        if (scale == Scale.DAY) calendar.add(Calendar.DAY_OF_MONTH, amount);
        else if (scale == Scale.MONTH) calendar.add(Calendar.MONTH, amount);
        else calendar.add(Calendar.YEAR, amount);
        year = calendar.get(Calendar.YEAR);
        month0 = calendar.get(Calendar.MONTH);
        day = calendar.get(Calendar.DAY_OF_MONTH);
        normalizeDay();
    }

    Window window() {
        if (allPeriods) {
            return new Window(scale, true, false, year, month0, day,
                    null, null, 0L, Long.MAX_VALUE, 0);
        }

        if (customRange) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(customStartMs);
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(customEndMs);
            return new Window(scale, false, true, year, month0, day,
                    floatingCanonical(start), floatingCanonical(end),
                    customStartMs, customEndMs, 0);
        }

        Calendar start = localPeriodStart();
        Calendar end = (Calendar) start.clone();
        int expected;
        if (scale == Scale.DAY) {
            end.add(Calendar.DAY_OF_MONTH, 1);
            expected = 24;
        } else if (scale == Scale.MONTH) {
            expected = start.getActualMaximum(Calendar.DAY_OF_MONTH);
            end.add(Calendar.MONTH, 1);
        } else {
            expected = 12;
            end.add(Calendar.YEAR, 1);
        }

        return new Window(scale, false, false, year, month0, day,
                floatingCanonical(start), floatingCanonical(end),
                start.getTimeInMillis(), end.getTimeInMillis(), expected);
    }

    String label(Locale locale, String allPeriodsLabel) {
        if (allPeriods) return allPeriodsLabel == null ? "" : allPeriodsLabel;
        Locale use = locale == null ? Locale.getDefault() : locale;
        if (customRange) return customLabel(use);
        Calendar floating = floatingCalendar(year, month0, day);
        Date date = floating.getTime();
        if (scale == Scale.DAY) {
            DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, use);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(date);
        }
        if (scale == Scale.MONTH) {
            SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", use);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(date);
        }
        return String.format(use, "%04d", year);
    }

    private String customLabel(Locale locale) {
        Date start = new Date(customStartMs);
        Date end = new Date(customEndMs);
        DateFormat date = DateFormat.getDateInstance(DateFormat.SHORT, locale);
        DateFormat time = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        Calendar first = Calendar.getInstance();
        first.setTime(start);
        Calendar second = Calendar.getInstance();
        second.setTime(end);
        boolean sameDay = first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return date.format(start) + " " + time.format(start) + " – " + time.format(end);
        }
        return date.format(start) + " " + time.format(start)
                + " – " + date.format(end) + " " + time.format(end);
    }

    private void normalizeDay() {
        Calendar calendar = localCalendar();
        int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (day < 1) day = 1;
        if (day > max) day = max;
    }

    private Calendar localCalendar() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month0, Math.max(1, day), 12, 0, 0);
        return calendar;
    }

    private Calendar localPeriodStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        if (scale == Scale.DAY) calendar.set(year, month0, day, 0, 0, 0);
        else if (scale == Scale.MONTH) calendar.set(year, month0, 1, 0, 0, 0);
        else calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static Calendar floatingCalendar(int year, int month0, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month0, Math.max(1, day), 12, 0, 0);
        return calendar;
    }

    private static String floatingCanonical(Calendar localCalendar) {
        Calendar floating = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        floating.clear();
        floating.set(localCalendar.get(Calendar.YEAR), localCalendar.get(Calendar.MONTH),
                localCalendar.get(Calendar.DAY_OF_MONTH), localCalendar.get(Calendar.HOUR_OF_DAY),
                localCalendar.get(Calendar.MINUTE), 0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(floating.getTime());
    }
}
