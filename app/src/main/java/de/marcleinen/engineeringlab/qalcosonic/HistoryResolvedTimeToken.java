package de.marcleinen.engineeringlab.qalcosonic;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Ephemeral read-model token for resolved archive time.
 *
 * <p>The token never enters persistence or protocol data. It carries canonical UTC interval
 * boundaries plus the meter's IANA zone through the existing String-based History/Statistics
 * presentation and analytics interfaces without collapsing DST folds back to an ambiguous wall
 * clock string.</p>
 */
final class HistoryResolvedTimeToken {
    private static final String PREFIX = "@W1RT|";
    private static final String TYPE_INTERVAL = "I";
    private static final String TYPE_BOUNDARY = "B";
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long DAY_MS = 24L * HOUR_MS;

    static final class Parsed {
        final boolean interval;
        final long startUtcMs;
        final long endUtcMs;
        final ZoneId zoneId;

        Parsed(boolean interval, long startUtcMs, long endUtcMs, ZoneId zoneId) {
            this.interval = interval;
            this.startUtcMs = startUtcMs;
            this.endUtcMs = endUtcMs;
            this.zoneId = zoneId;
        }

        ZonedDateTime startLocal() {
            return Instant.ofEpochMilli(startUtcMs).atZone(zoneId);
        }

        ZonedDateTime endLocal() {
            return Instant.ofEpochMilli(endUtcMs).atZone(zoneId);
        }

        long sortMs() {
            return interval ? endUtcMs : startUtcMs;
        }

        long boundaryUtcMs() {
            return interval ? endUtcMs : startUtcMs;
        }
    }

    private HistoryResolvedTimeToken() { }

    static String interval(long startUtcMs, long endUtcMs, ZoneId zoneId) {
        if (startUtcMs <= 0L || endUtcMs <= startUtcMs || zoneId == null) {
            throw new IllegalArgumentException("positive resolved interval and zone required");
        }
        return PREFIX + TYPE_INTERVAL + "|" + startUtcMs + "|" + endUtcMs + "|" + zoneId.getId();
    }

    static String boundary(long utcMs, ZoneId zoneId) {
        if (utcMs <= 0L || zoneId == null) {
            throw new IllegalArgumentException("positive resolved boundary and zone required");
        }
        return PREFIX + TYPE_BOUNDARY + "|" + utcMs + "|" + utcMs + "|" + zoneId.getId();
    }

    static Parsed parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String[] parts = value.split("\\|", 5);
        if (parts.length != 5 || !"@W1RT".equals(parts[0])) return null;
        boolean interval;
        if (TYPE_INTERVAL.equals(parts[1])) interval = true;
        else if (TYPE_BOUNDARY.equals(parts[1])) interval = false;
        else return null;
        try {
            long start = Long.parseLong(parts[2]);
            long end = Long.parseLong(parts[3]);
            ZoneId zone = ZoneId.of(parts[4]);
            if (start <= 0L || end < start || (interval && end <= start) || (!interval && end != start)) {
                return null;
            }
            return new Parsed(interval, start, end, zone);
        } catch (NumberFormatException | DateTimeException error) {
            return null;
        }
    }

    /**
     * Checks whether a resolved interval can represent exactly one native physical archive bucket.
     *
     * <p>The canonical UTC projection is derived from ON_TIME elapsed seconds before an IANA zone
     * participates at all. Therefore DST must never widen or shrink a native bucket in UTC. LOCAL is
     * presentation only: it may make one valid interval cross civil dates, months, offsets or folds,
     * but the elapsed physical duration stays unchanged. Exact native durations also prevent a
     * missing archive bucket from being silently compressed into one statistics bucket.</p>
     */
    static boolean adjacent(
            String previous,
            String current,
            HistorySemanticTimeline.Granularity granularity) {
        Parsed first = parse(previous);
        Parsed second = parse(current);
        if (first == null || second == null || !second.interval || granularity == null) return false;
        if (!first.zoneId.equals(second.zoneId) || first.boundaryUtcMs() != second.startUtcMs) return false;

        long durationMs = second.endUtcMs - second.startUtcMs;
        switch (granularity) {
            case HOUR:
                return durationMs == HOUR_MS;
            case DAY:
                return durationMs == DAY_MS;
            case MONTH:
                return durationMs == 28L * DAY_MS
                        || durationMs == 29L * DAY_MS
                        || durationMs == 30L * DAY_MS
                        || durationMs == 31L * DAY_MS;
            case YEAR:
                return durationMs == 365L * DAY_MS || durationMs == 366L * DAY_MS;
            default:
                return false;
        }
    }
}
