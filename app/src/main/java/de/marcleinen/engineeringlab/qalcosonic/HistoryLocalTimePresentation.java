package de.marcleinen.engineeringlab.qalcosonic;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Locale-aware presentation of explicit canonical UTC intervals in the meter-assigned zone. */
final class HistoryLocalTimePresentation {
    private HistoryLocalTimePresentation() { }

    static String formatPeriod(Locale locale, HistoryLocalArchiveReadModel.Row row) {
        if (row == null || row.zoneId == null) return "";
        Locale use = locale == null ? Locale.getDefault() : locale;
        TimeZone zone = TimeZone.getTimeZone(row.zoneId);
        Date start = new Date(row.startUtcMs);
        Date end = new Date(row.endUtcMs);
        DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM, use);
        DateFormat time = DateFormat.getTimeInstance(DateFormat.SHORT, use);
        date.setTimeZone(zone);
        time.setTimeZone(zone);

        boolean sameDate = row.startLocal.toLocalDate().equals(row.endLocal.toLocalDate());
        boolean showOffsets = !row.startLocal.getOffset().equals(row.endLocal.getOffset());
        String startTime = time.format(start) + (showOffsets ? " [" + offsetLabel(row.startLocal.getOffset()) + "]" : "");
        String endTime = time.format(end) + (showOffsets ? " [" + offsetLabel(row.endLocal.getOffset()) + "]" : "");
        if (sameDate) {
            return date.format(start) + " · " + startTime + "–" + endTime;
        }
        return date.format(start) + " · " + startTime
                + " – " + date.format(end) + " · " + endTime;
    }

    static String offsetLabel(java.time.ZoneOffset offset) {
        if (offset == null || "Z".equals(offset.getId())) return "UTC";
        return "UTC" + offset.getId();
    }
}
