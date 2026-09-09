package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Read-only DB facade for the v2.1 canonical archive UTC projection and window coverage. */
final class ArchiveUtcRepository implements AutoCloseable {
    private final ArchiveFamilyStore archiveStore;
    private final MeterTimeModelStore timeStore;

    ArchiveUtcRepository(Context context) {
        Context app = context.getApplicationContext();
        archiveStore = new ArchiveFamilyStore(app);
        timeStore = new MeterTimeModelStore(app);
    }

    List<ArchiveUtcProjection.Period> project(
            String meterId,
            ArchiveFamilyPeriod.Family family) {
        List<ArchiveUtcProjection.Period> empty = new ArrayList<>();
        if (meterId == null || meterId.trim().isEmpty() || family == null) return empty;
        String meter = meterId.trim();
        return ArchiveUtcProjection.resolveFamily(
                archiveStore.getPeriods(meter, family),
                timeStore.anchors(meter));
    }

    ArchiveLocalWindowSelection.Result selectLocal(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset,
            ArchiveLocalWindowSelection.Semantics semantics) {
        String meter = meterId == null ? null : meterId.trim();
        return ArchiveLocalWindowSelection.select(
                startLocal,
                endLocal,
                meterZone(meter),
                explicitStartOffset,
                explicitEndOffset,
                project(meter, family),
                semantics);
    }

    ArchiveWindowCoverage.Result coverageLocal(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset) {
        String meter = meterId == null ? null : meterId.trim();
        return ArchiveWindowCoverage.evaluateLocal(
                startLocal,
                endLocal,
                meterZone(meter),
                explicitStartOffset,
                explicitEndOffset,
                project(meter, family));
    }

    ArchiveWindowCoverage.Result coverageUtc(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            long startUtcMs,
            long endUtcMs) {
        return ArchiveWindowCoverage.evaluateUtc(
                startUtcMs,
                endUtcMs,
                project(meterId, family));
    }

    /** Returns only the persisted per-meter IANA zone; never substitutes the phone zone. */
    ZoneId meterZone(String meterId) {
        MeterTimeModelStore.Profile profile = meterId == null || meterId.isEmpty()
                ? null : timeStore.getProfile(meterId);
        if (profile == null) return null;
        try {
            return ZoneId.of(profile.zoneId);
        } catch (DateTimeException ignored) {
            // Store validation normally prevents this. Preserve fail-closed window semantics
            // for any externally corrupted state instead of silently using the phone zone.
            return null;
        }
    }

    @Override public void close() {
        archiveStore.close();
        timeStore.close();
    }
}
