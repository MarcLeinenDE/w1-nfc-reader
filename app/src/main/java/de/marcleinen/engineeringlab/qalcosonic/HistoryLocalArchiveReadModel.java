package de.marcleinen.engineeringlab.qalcosonic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit real/local interval metadata for selected archive records. */
final class HistoryLocalArchiveReadModel {
    static final class Row {
        final ArchiveFamilyStore.StoredPeriod source;
        final String identity;
        final ArchiveFamilyStore.StoredPeriod startSource;
        final String startIdentity;
        final long startUtcMs;
        final long endUtcMs;
        final ZoneId zoneId;
        final ZonedDateTime startLocal;
        final ZonedDateTime endLocal;
        final boolean fullyContained;
        final boolean partialOverlap;

        Row(
                ArchiveFamilyStore.StoredPeriod source,
                String identity,
                ArchiveFamilyStore.StoredPeriod startSource,
                String startIdentity,
                long startUtcMs,
                long endUtcMs,
                ZoneId zoneId,
                ZonedDateTime startLocal,
                ZonedDateTime endLocal,
                boolean fullyContained,
                boolean partialOverlap) {
            this.source = source;
            this.identity = identity;
            this.startSource = startSource;
            this.startIdentity = startIdentity == null ? "" : startIdentity;
            this.startUtcMs = startUtcMs;
            this.endUtcMs = endUtcMs;
            this.zoneId = zoneId;
            this.startLocal = startLocal;
            this.endLocal = endLocal;
            this.fullyContained = fullyContained;
            this.partialOverlap = partialOverlap;
        }
    }

    private HistoryLocalArchiveReadModel() { }

    static List<Row> rows(ArchiveLocalWindowSelection.Result selection) {
        if (selection == null || !selection.windowResolved()
                || selection.window.zoneId == null || selection.periods == null) {
            return Collections.emptyList();
        }
        return rows(
                selection.periods,
                selection.window.zoneId,
                selection.window.startUtcMs,
                selection.window.endUtcMs);
    }

    /** All-periods projection: only already-resolved native intervals are exposed. */
    static List<Row> rows(List<ArchiveUtcProjection.Period> periods, ZoneId zoneId) {
        if (zoneId == null) return Collections.emptyList();
        return rows(periods, zoneId, null, null);
    }

    private static List<Row> rows(
            List<ArchiveUtcProjection.Period> periods,
            ZoneId zone,
            Long requestStart,
            Long requestEnd) {
        if (periods == null || zone == null) return Collections.emptyList();
        List<Row> rows = new ArrayList<>();
        for (ArchiveUtcProjection.Period period : periods) {
            if (period == null || !period.resolvedInterval()) continue;
            boolean bounded = requestStart != null && requestEnd != null;
            boolean contained = !bounded
                    || (period.startUtcMs >= requestStart && period.endUtcMs <= requestEnd);
            ArchiveFamilyStore.StoredPeriod startSource = period.startBoundary == null
                    ? null : period.startBoundary.source;
            String startIdentity = period.startBoundary == null
                    ? null : period.startBoundary.identity;
            rows.add(new Row(
                    period.source,
                    period.identity,
                    startSource,
                    startIdentity,
                    period.startUtcMs,
                    period.endUtcMs,
                    zone,
                    Instant.ofEpochMilli(period.startUtcMs).atZone(zone),
                    Instant.ofEpochMilli(period.endUtcMs).atZone(zone),
                    contained,
                    bounded && !contained));
        }
        rows.sort((first, second) -> {
            int byEnd = Long.compare(first.endUtcMs, second.endUtcMs);
            if (byEnd != 0) return byEnd;
            return first.identity.compareTo(second.identity);
        });
        return Collections.unmodifiableList(rows);
    }
}
