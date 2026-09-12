package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Objects;

/** Canonical semantic identity for one physical archive occurrence. */
final class ArchiveRecordIdentity {
    private ArchiveRecordIdentity() { }

    static String of(ArchiveFamilyStore.StoredPeriod period) {
        Objects.requireNonNull(period, "period");
        return of(period.family, period.meterId, period.loggerTimestamp, period.occurrenceKey);
    }

    static String of(
            ArchiveFamilyPeriod.Family family,
            String meterId,
            String loggerTimestamp,
            String occurrenceKey) {
        Objects.requireNonNull(family, "family");
        String meter = requireText(meterId, "meterId");
        String timestamp = requireText(loggerTimestamp, "loggerTimestamp");
        String occurrence = occurrenceKey == null || occurrenceKey.trim().isEmpty()
                ? ArchiveOccurrenceKey.LEGACY
                : occurrenceKey.trim();
        return meter + "|" + family.name() + "|" + timestamp + "|" + occurrence;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value.trim();
    }
}
