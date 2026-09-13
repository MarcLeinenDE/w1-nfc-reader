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
     * Checks resolved read-model continuity for one already validated native archive bucket.
     *
     * <p>Native Hour/Day/Month/Year adjacency is validated upstream by
     * {@link ArchiveUtcProjection} from consecutive ON_TIME evidence. That is the correct place to
     * reject a missing native record. The two real-time boundaries may legitimately have been
     * resolved against different verified Live anchors; because meter time evidence is quantized,
     * such a handover can make the projected UTC duration differ slightly from the nominal native
     * duration. Analytics therefore checks the occurrence-safe shared UTC boundary here and must not
     * reclassify a native-valid bucket solely from projected duration.</p>
     */
    static boolean adjacent(
            String previous,
            String current,
            HistorySemanticTimeline.Granularity granularity) {
        Parsed first = parse(previous);
        Parsed second = parse(current);
        if (first == null || second == null || !second.interval || granularity == null) return false;
        if (!first.zoneId.equals(second.zoneId) || first.boundaryUtcMs() != second.startUtcMs) return false;
        switch (granularity) {
            case HOUR:
            case DAY:
            case MONTH:
            case YEAR:
                return true;
            default:
                return false;
        }
    }
}
