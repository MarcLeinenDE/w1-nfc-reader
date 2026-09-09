package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Read-only DB facade for the v2.1 canonical archive UTC projection. */
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

    UtcCoverage.Result coverage(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            long requestStartUtcMs,
            long requestEndUtcMs) {
        List<ArchiveUtcProjection.Period> projected = project(meterId, family);
        List<UtcCoverage.Interval> intervals = new ArrayList<>();
        boolean allTimesResolved = true;
        for (ArchiveUtcProjection.Period period : projected) {
            if (period == null) continue;
            UtcCoverage.Interval interval = period.coverageInterval();
            if (interval != null) {
                intervals.add(interval);
            } else {
                allTimesResolved = false;
            }
        }
        return UtcCoverage.evaluate(
                requestStartUtcMs,
                requestEndUtcMs,
                intervals,
                allTimesResolved);
    }

    @Override public void close() {
        archiveStore.close();
        timeStore.close();
    }
}
