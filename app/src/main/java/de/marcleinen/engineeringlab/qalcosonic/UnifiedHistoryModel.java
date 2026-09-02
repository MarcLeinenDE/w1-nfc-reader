package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Source-neutral rows for the intermediate combined Live + archive History screen. */
final class UnifiedHistoryModel {
    static final class Row {
        final String identity;
        final String timestamp;
        final HistorySemanticTimeline.Granularity granularity;
        final HistorySemanticTimeline.Provenance provenance;
        final String totalVolume;
        final String battery;
        final String status;

        Row(String identity, String timestamp, HistorySemanticTimeline.Granularity granularity,
            HistorySemanticTimeline.Provenance provenance, String totalVolume,
            String battery, String status) {
            this.identity = identity;
            this.timestamp = timestamp == null ? "" : timestamp;
            this.granularity = granularity;
            this.provenance = provenance;
            this.totalVolume = totalVolume;
            this.battery = battery;
            this.status = status;
        }
    }

    private UnifiedHistoryModel() {}

    static List<Row> newestFirst(List<Row> rows) {
        ArrayList<Row> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing((Row row) -> row.timestamp).reversed()
                .thenComparing(row -> row.granularity)
                .thenComparing(row -> row.identity));
        return List.copyOf(out);
    }
}
