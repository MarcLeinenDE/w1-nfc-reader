package de.marcleinen.engineeringlab.qalcosonic;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;

/** Converts LOCAL navigator input in the meter-assigned IANA zone into one canonical UTC window. */
final class LocalTimeWindowResolver {
    enum Status {
        RESOLVED,
        ZONE_MISSING,
        AMBIGUOUS_LOCAL_TIME,
        NONEXISTENT_LOCAL_TIME,
        OFFSET_NOT_VALID_FOR_LOCAL_TIME,
        INVALID_RANGE
    }

    static final class Boundary {
        final Status status;
        final Long epochMs;
        final ZoneOffset offset;

        Boundary(Status status, Long epochMs, ZoneOffset offset) {
            this.status = status;
            this.epochMs = epochMs;
            this.offset = offset;
        }

        boolean resolved() {
            return status == Status.RESOLVED && epochMs != null;
        }
    }

    static final class Window {
        final Status status;
        final Long startUtcMs;
        final Long endUtcMs;
        final ZoneId zoneId;
        final ZoneOffset startOffset;
        final ZoneOffset endOffset;

        Window(
                Status status,
                Long startUtcMs,
                Long endUtcMs,
                ZoneId zoneId,
                ZoneOffset startOffset,
                ZoneOffset endOffset) {
            this.status = status;
            this.startUtcMs = startUtcMs;
            this.endUtcMs = endUtcMs;
            this.zoneId = zoneId;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        boolean resolved() {
            return status == Status.RESOLVED && startUtcMs != null && endUtcMs != null;
        }
    }

    private LocalTimeWindowResolver() { }

    static Boundary resolveBoundary(LocalDateTime local, ZoneId zone, ZoneOffset explicitOffset) {
        if (zone == null || local == null) return new Boundary(Status.ZONE_MISSING, null, null);
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> valid = rules.getValidOffsets(local);
        if (valid.isEmpty()) {
            return new Boundary(Status.NONEXISTENT_LOCAL_TIME, null, null);
        }
        ZoneOffset chosen;
        if (valid.size() == 1) {
            chosen = valid.get(0);
            if (explicitOffset != null && !chosen.equals(explicitOffset)) {
                return new Boundary(Status.OFFSET_NOT_VALID_FOR_LOCAL_TIME, null, null);
            }
        } else {
            if (explicitOffset == null) {
                return new Boundary(Status.AMBIGUOUS_LOCAL_TIME, null, null);
            }
            if (!valid.contains(explicitOffset)) {
                return new Boundary(Status.OFFSET_NOT_VALID_FOR_LOCAL_TIME, null, null);
            }
            chosen = explicitOffset;
        }
        return new Boundary(
                Status.RESOLVED,
                local.toInstant(chosen).toEpochMilli(),
                chosen);
    }

    static Window resolveWindow(
            LocalDateTime start,
            LocalDateTime end,
            ZoneId zone,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset) {
        Boundary from = resolveBoundary(start, zone, explicitStartOffset);
        if (!from.resolved()) return failedWindow(from.status, zone);
        Boundary to = resolveBoundary(end, zone, explicitEndOffset);
        if (!to.resolved()) return failedWindow(to.status, zone);
        if (from.epochMs >= to.epochMs) return failedWindow(Status.INVALID_RANGE, zone);
        return new Window(
                Status.RESOLVED,
                from.epochMs,
                to.epochMs,
                zone,
                from.offset,
                to.offset);
    }

    private static Window failedWindow(Status status, ZoneId zone) {
        return new Window(status, null, null, zone, null, null);
    }
}
