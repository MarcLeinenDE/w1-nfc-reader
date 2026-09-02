package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Objects;

/** Immutable normalized identity/provenance wrapper for one validated Monthly archive period. */
final class MonthlyArchivePeriod {
    static final String SOURCE_NFC_ARCHIVE = "NFC_ARCHIVE";
    static final String VALIDATION_COMPLETE = "COMPLETE";

    final String loggerTimestamp;
    final String retrievedAtUtc;
    final String structuralFingerprint;
    final String source;
    final String validation;
    final ArchivePeriodSnapshot snapshot;

    MonthlyArchivePeriod(
            String loggerTimestamp,
            String retrievedAtUtc,
            String structuralFingerprint,
            ArchivePeriodSnapshot snapshot) {
        this(loggerTimestamp, retrievedAtUtc, structuralFingerprint,
                SOURCE_NFC_ARCHIVE, VALIDATION_COMPLETE, snapshot);
    }

    MonthlyArchivePeriod(
            String loggerTimestamp,
            String retrievedAtUtc,
            String structuralFingerprint,
            String source,
            String validation,
            ArchivePeriodSnapshot snapshot) {
        this.loggerTimestamp = requireText(loggerTimestamp, "loggerTimestamp");
        this.retrievedAtUtc = retrievedAtUtc;
        this.structuralFingerprint = requireText(structuralFingerprint, "structuralFingerprint");
        this.source = requireText(source, "source");
        this.validation = requireText(validation, "validation");
        if (snapshot != null && snapshot.periodType != ArchivePeriodSnapshot.PeriodType.MONTH) {
            throw new IllegalArgumentException("snapshot must be MONTH");
        }
        if (snapshot != null && snapshot.loggerDateTime != null
                && !loggerTimestamp.equals(snapshot.loggerDateTime)) {
            throw new IllegalArgumentException("snapshot/logger timestamp mismatch");
        }
        this.snapshot = snapshot;
    }

    String periodKeyWithinMeter() {
        return "MONTH|" + loggerTimestamp;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MonthlyArchivePeriod)) return false;
        MonthlyArchivePeriod that = (MonthlyArchivePeriod) other;
        return loggerTimestamp.equals(that.loggerTimestamp)
                && structuralFingerprint.equals(that.structuralFingerprint)
                && source.equals(that.source)
                && validation.equals(that.validation)
                && Objects.equals(retrievedAtUtc, that.retrievedAtUtc);
    }

    @Override public int hashCode() {
        return Objects.hash(loggerTimestamp, retrievedAtUtc, structuralFingerprint, source, validation);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
