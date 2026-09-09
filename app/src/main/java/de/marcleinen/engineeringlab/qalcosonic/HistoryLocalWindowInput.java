package de.marcleinen.engineeringlab.qalcosonic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Bridges the legacy floating navigator fields into meter-zone LOCAL query input. */
final class HistoryLocalWindowInput {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    final LocalDateTime start;
    final LocalDateTime end;

    HistoryLocalWindowInput(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("positive local window required");
        }
        this.start = start;
        this.end = end;
    }

    static HistoryLocalWindowInput from(HistoryPeriodNavigator.Window window) {
        if (window == null || window.allPeriods
                || window.archiveStart == null || window.archiveEnd == null) {
            return null;
        }
        try {
            return new HistoryLocalWindowInput(
                    LocalDateTime.parse(window.archiveStart, FORMAT),
                    LocalDateTime.parse(window.archiveEnd, FORMAT));
        } catch (DateTimeParseException error) {
            return null;
        }
    }
}
