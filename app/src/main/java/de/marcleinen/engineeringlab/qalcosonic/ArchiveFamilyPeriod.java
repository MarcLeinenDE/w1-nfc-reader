package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Objects;

/** Generic canonical period wrapper shared by Hour/Day/Month/Year archive families. */
final class ArchiveFamilyPeriod {
    enum Family { HOUR, DAY, MONTH, YEAR }

    static final String TIME_BASIS_METER_LOCAL = "METER_LOCAL_UNSPECIFIED_TZ";

    final Family family;
    final String loggerTimestamp;
    final String loggerTimeBasis;
    final String retrievedAtUtc;
    final String structuralFingerprint;
    final String source;
    final String validation;
    final ArchiveNormalizedValues values;

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String retrievedAtUtc,
                        String structuralFingerprint, String source, String validation,
                        ArchiveNormalizedValues values) {
        this(family, loggerTimestamp, TIME_BASIS_METER_LOCAL, retrievedAtUtc,
                structuralFingerprint, source, validation, values);
    }

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String loggerTimeBasis,
                        String retrievedAtUtc, String structuralFingerprint, String source,
                        String validation, ArchiveNormalizedValues values) {
        this.family = Objects.requireNonNull(family, "family");
        this.loggerTimestamp = requireText(loggerTimestamp, "loggerTimestamp");
        this.loggerTimeBasis = requireText(loggerTimeBasis, "loggerTimeBasis");
        this.retrievedAtUtc = requireText(retrievedAtUtc, "retrievedAtUtc");
        this.structuralFingerprint = requireText(structuralFingerprint, "structuralFingerprint");
        this.source = requireText(source, "source");
        this.validation = requireText(validation, "validation");
        this.values = values == null ? ArchiveNormalizedValues.builder().build() : values;
    }

    static ArchiveFamilyPeriod fromMonthly(MonthlyArchivePeriod monthly) {
        Objects.requireNonNull(monthly, "monthly");
        return new ArchiveFamilyPeriod(
                Family.MONTH,
                monthly.loggerTimestamp,
                monthly.retrievedAtUtc,
                monthly.structuralFingerprint,
                monthly.source,
                monthly.validation,
                ArchiveNormalizedValues.fromSnapshot(monthly.snapshot));
    }

    String stableKey(String meterId) {
        return requireText(meterId, "meterId") + "|" + family.name() + "|" + loggerTimestamp;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
