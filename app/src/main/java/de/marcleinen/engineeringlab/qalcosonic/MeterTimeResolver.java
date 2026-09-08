package de.marcleinen.engineeringlab.qalcosonic;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Resolves raw meter/archive time to canonical UTC using a verified Live ON_TIME anchor.
 *
 * <p>No civil timezone participates in the UTC derivation. The meter-assigned IANA zone belongs to
 * the LOCAL projection layer after a canonical UTC instant has been resolved.</p>
 *
 * <p>The raw meter/logger wall-clock relation is retained as independent clock-regime evidence. A
 * raw clock shift does not by itself invalidate an otherwise continuous ON_TIME derivation and is
 * therefore reported diagnostically rather than used to rewrite or reject canonical UTC.</p>
 */
final class MeterTimeResolver {
    static final String METHOD_ON_TIME_LIVE_ANCHOR = "ON_TIME_LIVE_ANCHOR_V1";
    static final long RAW_CLOCK_ALIGNMENT_TOLERANCE_MS = 120_000L;

    private static final DateTimeFormatter RAW = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    enum Status {
        RESOLVED,
        ANCHOR_INVALID,
        METER_MISMATCH,
        TYPE_F_INVALID,
        ON_TIME_MISSING,
        ON_TIME_AFTER_ANCHOR,
        RAW_TIME_INVALID,
        ARITHMETIC_ERROR
    }

    enum ClockRelation {
        ALIGNED,
        SHIFTED
    }

    static final class Resolution {
        final Status status;
        final Long epochMs;
        final long uncertaintyMs;
        final Long rawOriginSkewMs;
        final ClockRelation rawClockRelation;
        final String method;

        Resolution(
                Status status,
                Long epochMs,
                long uncertaintyMs,
                Long rawOriginSkewMs,
                ClockRelation rawClockRelation) {
            this.status = status;
            this.epochMs = epochMs;
            this.uncertaintyMs = uncertaintyMs;
            this.rawOriginSkewMs = rawOriginSkewMs;
            this.rawClockRelation = rawClockRelation;
            this.method = status == Status.RESOLVED ? METHOD_ON_TIME_LIVE_ANCHOR : null;
        }

        boolean resolved() {
            return status == Status.RESOLVED && epochMs != null;
        }
    }

    private MeterTimeResolver() { }

    static Resolution resolve(VerifiedLiveTimeAnchor anchor, ArchiveTimeEvidence archive) {
        if (anchor == null || !anchor.usable()) return fail(Status.ANCHOR_INVALID);
        if (archive == null || archive.meterId == null
                || !anchor.meterId.equals(archive.meterId)) {
            return fail(Status.METER_MISMATCH);
        }
        if (archive.invalidTime) return fail(Status.TYPE_F_INVALID);
        if (archive.onTimeSeconds == null || archive.onTimeSeconds < 0L) {
            return fail(Status.ON_TIME_MISSING);
        }
        if (archive.onTimeSeconds > anchor.onTimeSeconds) {
            return fail(Status.ON_TIME_AFTER_ANCHOR);
        }

        Long anchorRawMs = rawEpochLikeMs(anchor.rawMeterWallClock);
        Long archiveRawMs = rawEpochLikeMs(archive.rawLoggerWallClock);
        if (anchorRawMs == null || archiveRawMs == null) return fail(Status.RAW_TIME_INVALID);

        try {
            long anchorOrigin = Math.subtractExact(
                    anchorRawMs, Math.multiplyExact(anchor.onTimeSeconds, 1000L));
            long archiveOrigin = Math.subtractExact(
                    archiveRawMs, Math.multiplyExact(archive.onTimeSeconds, 1000L));
            long originSkew = Math.subtractExact(archiveOrigin, anchorOrigin);
            ClockRelation relation = originSkew >= -RAW_CLOCK_ALIGNMENT_TOLERANCE_MS
                    && originSkew <= RAW_CLOCK_ALIGNMENT_TOLERANCE_MS
                    ? ClockRelation.ALIGNED
                    : ClockRelation.SHIFTED;

            long elapsedSeconds = Math.subtractExact(
                    anchor.onTimeSeconds, archive.onTimeSeconds);
            long candidate = Math.subtractExact(
                    anchor.anchorEpochMs, Math.multiplyExact(elapsedSeconds, 1000L));
            return new Resolution(
                    Status.RESOLVED,
                    candidate,
                    anchor.uncertaintyMs,
                    originSkew,
                    relation);
        } catch (ArithmeticException error) {
            return fail(Status.ARITHMETIC_ERROR);
        }
    }

    static Long rawEpochLikeMs(String rawWallClock) {
        if (rawWallClock == null) return null;
        try {
            LocalDateTime local = LocalDateTime.parse(rawWallClock.trim(), RAW);
            return local.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeException error) {
            return null;
        }
    }

    private static Resolution fail(Status status) {
        return new Resolution(status, null, -1L, null, null);
    }
}
