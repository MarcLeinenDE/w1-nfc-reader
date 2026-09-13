package de.marcleinen.engineeringlab.qalcosonic;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Pure expected-bucket math for resolved LOCAL statistics windows. */
final class HistoryLocalBucketExpectation {
    private HistoryLocalBucketExpectation() { }

    /**
     * Returns the number of complete real hourly intervals inside the local window, or null when
     * either local boundary cannot be resolved safely in the supplied meter zone.
     */
    static Integer fullHours(LocalDateTime start, LocalDateTime end, ZoneId meterZone) {
        LocalTimeWindowResolver.Window resolved = LocalTimeWindowResolver.resolveWindow(
                start, end, meterZone, null, null);
        if (resolved == null || !resolved.resolved()) return null;

        ZonedDateTime first = Instant.ofEpochMilli(resolved.startUtcMs).atZone(meterZone);
        ZonedDateTime last = Instant.ofEpochMilli(resolved.endUtcMs).atZone(meterZone);

        ZonedDateTime firstBoundary = first.truncatedTo(ChronoUnit.HOURS);
        if (!firstBoundary.toInstant().equals(first.toInstant())) {
            firstBoundary = firstBoundary.plusHours(1);
        }
        ZonedDateTime lastBoundary = last.truncatedTo(ChronoUnit.HOURS);
        if (lastBoundary.toInstant().isBefore(firstBoundary.toInstant())) return 0;

        Duration span = Duration.between(firstBoundary.toInstant(), lastBoundary.toInstant());
        long hours = span.toHours();
        if (hours < 0L || hours > Integer.MAX_VALUE) return null;
        return (int) hours;
    }
}
