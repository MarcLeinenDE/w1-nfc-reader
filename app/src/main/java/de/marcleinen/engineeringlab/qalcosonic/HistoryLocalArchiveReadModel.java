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
                long startUtcMs,
                long endUtcMs,
                ZoneId zoneId,
                ZonedDateTime startLocal,
                ZonedDateTime endLocal,
                boolean fullyContained,
                boolean partialOverlap) {
            this.source = source;
            this.identity = identity;
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
        long requestStart = selection.window.startUtcMs;
        long requestEnd = selection.window.endUtcMs;
        ZoneId zone = selection.window.zoneId;
        List<Row> rows = new ArrayList<>();
        for (ArchiveUtcProjection.Period period : selection.periods) {
            if (period == null || !period.resolvedInterval()) continue;
            boolean contained = period.startUtcMs >= requestStart && period.endUtcMs <= requestEnd;
            rows.add(new Row(
                    period.source,
                    period.identity,
                    period.startUtcMs,
                    period.endUtcMs,
                    zone,
                    Instant.ofEpochMilli(period.startUtcMs).atZone(zone),
                    Instant.ofEpochMilli(period.endUtcMs).atZone(zone),
                    contained,
                    !contained));
        }
        return Collections.unmodifiableList(rows);
    }
}
