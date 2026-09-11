package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only projection from native archive evidence to canonical UTC boundaries/intervals.
 *
 * <p>The projection deliberately does not mutate archive rows and does not change History query
 * semantics yet. A stored raw logger timestamp is a completed-period END boundary. A complete UTC
 * interval is formed only from two consecutive resolved native boundaries of the same physical
 * meter and archive family. Native adjacency is validated from ON_TIME before the two independently
 * resolved UTC boundaries are joined. This keeps missing native buckets from being silently
 * compressed while allowing two valid boundaries to come from different verified Live anchors.</p>
 */
final class ArchiveUtcProjection {
    private static final long HOUR_SECONDS = 60L * 60L;
    private static final long DAY_SECONDS = 24L * HOUR_SECONDS;

    enum BoundaryStatus {
        RESOLVED,
        NATIVE_EVIDENCE_MISSING,
        TYPE_F_INVALID,
        NO_SUITABLE_ANCHOR,
        RESOLUTION_FAILED
    }

    enum PeriodStatus {
        RESOLVED,
        START_BOUNDARY_UNAVAILABLE,
        END_BOUNDARY_UNRESOLVED,
        NATIVE_GAP,
        NON_MONOTONIC
    }

    static final class Boundary {
        final ArchiveFamilyStore.StoredPeriod source;
        final String identity;
        final BoundaryStatus status;
        final Long utcMs;
        final long uncertaintyMs;
        final Long anchorId;
        final MeterTimeResolver.Status resolverStatus;
        final MeterTimeResolver.ClockRelation rawClockRelation;
        final Long rawOriginSkewMs;
        final String method;

        Boundary(
                ArchiveFamilyStore.StoredPeriod source,
                BoundaryStatus status,
                Long utcMs,
                long uncertaintyMs,
                Long anchorId,
                MeterTimeResolver.Status resolverStatus,
                MeterTimeResolver.ClockRelation rawClockRelation,
                Long rawOriginSkewMs,
                String method) {
            this.source = source;
            this.identity = identity(source);
            this.status = status;
            this.utcMs = utcMs;
            this.uncertaintyMs = uncertaintyMs;
            this.anchorId = anchorId;
            this.resolverStatus = resolverStatus;
            this.rawClockRelation = rawClockRelation;
            this.rawOriginSkewMs = rawOriginSkewMs;
            this.method = method;
        }

        boolean resolved() {
            return status == BoundaryStatus.RESOLVED && utcMs != null;
        }
    }

    static final class Period {
        final ArchiveFamilyStore.StoredPeriod source;
        final String identity;
        final PeriodStatus status;
        final Boundary startBoundary;
        final Boundary endBoundary;
        final Long startUtcMs;
        final Long endUtcMs;
        final long uncertaintyMs;

        Period(
                ArchiveFamilyStore.StoredPeriod source,
                PeriodStatus status,
                Boundary startBoundary,
                Boundary endBoundary) {
            this.source = source;
            this.identity = identity(source);
            this.status = status;
            this.startBoundary = startBoundary;
            this.endBoundary = endBoundary;
            this.startUtcMs = startBoundary == null ? null : startBoundary.utcMs;
            this.endUtcMs = endBoundary == null ? null : endBoundary.utcMs;
            this.uncertaintyMs = Math.max(
                    startBoundary == null ? -1L : startBoundary.uncertaintyMs,
                    endBoundary == null ? -1L : endBoundary.uncertaintyMs);
        }

        boolean resolvedInterval() {
            return status == PeriodStatus.RESOLVED
                    && startUtcMs != null
                    && endUtcMs != null
                    && endUtcMs > startUtcMs;
        }

        UtcCoverage.Interval coverageInterval() {
            return resolvedInterval() ? new UtcCoverage.Interval(startUtcMs, endUtcMs) : null;
        }
    }

    private ArchiveUtcProjection() { }

    static List<Period> resolveFamily(
            List<ArchiveFamilyStore.StoredPeriod> stored,
            List<MeterTimeModelStore.AnchorRecord> anchors) {
        List<ArchiveFamilyStore.StoredPeriod> ordered = new ArrayList<>();
        if (stored != null) {
            for (ArchiveFamilyStore.StoredPeriod period : stored) {
                if (period != null) ordered.add(period);
            }
        }
        ordered.sort(Comparator
                .comparing((ArchiveFamilyStore.StoredPeriod p) -> p.meterId)
                .thenComparing(p -> p.family.name())
                .thenComparingLong(p -> p.onTimeSeconds == null ? Long.MIN_VALUE : p.onTimeSeconds)
                .thenComparingLong(p -> p.id));

        List<Period> out = new ArrayList<>();
        Boundary previous = null;
        String previousMeter = null;
        ArchiveFamilyPeriod.Family previousFamily = null;
        for (ArchiveFamilyStore.StoredPeriod source : ordered) {
            Boundary end = resolveBoundary(source, anchors);
            boolean sameSeries = previous != null
                    && source.meterId.equals(previousMeter)
                    && source.family == previousFamily;
            Boundary start = sameSeries ? previous : null;
            PeriodStatus status;
            if (!end.resolved()) {
                status = PeriodStatus.END_BOUNDARY_UNRESOLVED;
            } else if (start == null || !start.resolved()) {
                status = PeriodStatus.START_BOUNDARY_UNAVAILABLE;
            } else if (!nativeAdjacent(start.source, source)) {
                status = PeriodStatus.NATIVE_GAP;
            } else if (end.utcMs <= start.utcMs) {
                status = PeriodStatus.NON_MONOTONIC;
            } else {
                status = PeriodStatus.RESOLVED;
            }
            out.add(new Period(source, status, start, end));
            previous = end;
            previousMeter = source.meterId;
            previousFamily = source.family;
        }
        return out;
    }

    static Boundary resolveBoundary(
            ArchiveFamilyStore.StoredPeriod source,
            List<MeterTimeModelStore.AnchorRecord> anchors) {
        if (!hasNativeEvidence(source)) {
            return unresolved(source, BoundaryStatus.NATIVE_EVIDENCE_MISSING, null);
        }
        if (source.typeFIv != 0) {
            return unresolved(source, BoundaryStatus.TYPE_F_INVALID, MeterTimeResolver.Status.TYPE_F_INVALID);
        }
        MeterTimeModelStore.AnchorRecord selected = selectAnchor(source, anchors);
        if (selected == null) {
            return unresolved(source, BoundaryStatus.NO_SUITABLE_ANCHOR, null);
        }
        ArchiveTimeEvidence evidence = new ArchiveTimeEvidence(
                source.meterId,
                source.family,
                source.loggerTimestamp,
                source.rawTypeFHex,
                false,
                source.typeFSu != 0,
                source.onTimeSeconds);
        MeterTimeResolver.Resolution resolution = MeterTimeResolver.resolve(selected.toAnchor(), evidence);
        if (!resolution.resolved()) {
            return new Boundary(
                    source,
                    BoundaryStatus.RESOLUTION_FAILED,
                    null,
                    -1L,
                    selected.id,
                    resolution.status,
                    resolution.rawClockRelation,
                    resolution.rawOriginSkewMs,
                    resolution.method);
        }
        return new Boundary(
                source,
                BoundaryStatus.RESOLVED,
                resolution.epochMs,
                resolution.uncertaintyMs,
                selected.id,
                resolution.status,
                resolution.rawClockRelation,
                resolution.rawOriginSkewMs,
                resolution.method);
    }

    private static MeterTimeModelStore.AnchorRecord selectAnchor(
            ArchiveFamilyStore.StoredPeriod source,
            List<MeterTimeModelStore.AnchorRecord> anchors) {
        if (source == null || source.onTimeSeconds == null || anchors == null) return null;
        MeterTimeModelStore.AnchorRecord best = null;
        long bestDelta = Long.MAX_VALUE;
        for (MeterTimeModelStore.AnchorRecord candidate : anchors) {
            if (candidate == null || !source.meterId.equals(candidate.meterId)) continue;
            if (!MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT.equals(candidate.provenance)
                    || !MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE.equals(candidate.validation)) {
                continue;
            }
            if (candidate.rawTypeFHex == null || candidate.rawTypeFHex.trim().isEmpty()
                    || !candidate.toAnchor().usable()
                    || candidate.onTimeSeconds < source.onTimeSeconds) {
                continue;
            }
            long delta = candidate.onTimeSeconds - source.onTimeSeconds;
            if (best == null || delta < bestDelta
                    || (delta == bestDelta && candidate.uncertaintyMs < best.uncertaintyMs)
                    || (delta == bestDelta && candidate.uncertaintyMs == best.uncertaintyMs
                    && candidate.anchorEpochMs < best.anchorEpochMs)) {
                best = candidate;
                bestDelta = delta;
            }
        }
        return best;
    }

    /**
     * Validates native archive adjacency from monotonic ON_TIME evidence, not from the projected UTC
     * duration. Independently verified Live anchors are minute-level meter evidence tied to a real
     * acquisition instant, so a handover between two anchors can legitimately make the projected
     * real-time duration differ slightly from the exact native elapsed duration. Missing native
     * records must still remain a known coverage gap rather than being compressed into one bucket.
     */
    private static boolean nativeAdjacent(
            ArchiveFamilyStore.StoredPeriod previous,
            ArchiveFamilyStore.StoredPeriod current) {
        if (previous == null || current == null
                || previous.onTimeSeconds == null || current.onTimeSeconds == null
                || previous.family == null || current.family == null
                || previous.family != current.family
                || previous.meterId == null || !previous.meterId.equals(current.meterId)) {
            return false;
        }
        long delta;
        try {
            delta = Math.subtractExact(current.onTimeSeconds, previous.onTimeSeconds);
        } catch (ArithmeticException error) {
            return false;
        }
        switch (current.family) {
            case HOUR:
                return delta == HOUR_SECONDS;
            case DAY:
                return delta == DAY_SECONDS;
            case MONTH:
                return delta == 28L * DAY_SECONDS
                        || delta == 29L * DAY_SECONDS
                        || delta == 30L * DAY_SECONDS
                        || delta == 31L * DAY_SECONDS;
            case YEAR:
                return delta == 365L * DAY_SECONDS || delta == 366L * DAY_SECONDS;
            default:
                return false;
        }
    }

    private static boolean hasNativeEvidence(ArchiveFamilyStore.StoredPeriod source) {
        return source != null
                && source.meterId != null
                && source.family != null
                && source.loggerTimestamp != null
                && source.onTimeSeconds != null
                && source.rawTypeFHex != null
                && !source.rawTypeFHex.trim().isEmpty()
                && source.typeFIv != null
                && source.typeFSu != null;
    }

    private static Boundary unresolved(
            ArchiveFamilyStore.StoredPeriod source,
            BoundaryStatus status,
            MeterTimeResolver.Status resolverStatus) {
        return new Boundary(source, status, null, -1L, null, resolverStatus, null, null, null);
    }

    static String identity(ArchiveFamilyStore.StoredPeriod source) {
        return source == null ? "" : ArchiveRecordIdentity.of(source);
    }
}
