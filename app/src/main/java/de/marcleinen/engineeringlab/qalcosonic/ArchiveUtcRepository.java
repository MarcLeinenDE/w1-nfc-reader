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

    ArchiveWindowCoverage.Result coverageLocal(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset) {
        String meter = meterId == null ? null : meterId.trim();
        MeterTimeModelStore.Profile profile = meter == null || meter.isEmpty()
                ? null : timeStore.getProfile(meter);
        ZoneId meterZone = null;
        if (profile != null) {
            try {
                meterZone = ZoneId.of(profile.zoneId);
            } catch (DateTimeException ignored) {
                // Store validation normally prevents this. Preserve fail-closed window semantics
                // for any externally corrupted state instead of silently using the phone zone.
            }
        }
        return ArchiveWindowCoverage.evaluateLocal(
                startLocal,
                endLocal,
                meterZone,
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

    @Override public void close() {
        archiveStore.close();
        timeStore.close();
    }
}
