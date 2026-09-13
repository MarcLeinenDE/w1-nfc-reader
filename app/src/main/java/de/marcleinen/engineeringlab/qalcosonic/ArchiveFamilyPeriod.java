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

    // Native time evidence is additive and never replaces the raw logger wall clock above.
    // Null means that evidence was not available in the source period; false IV/SU values are only
    // represented as 0 when Type-F itself was actually present.
    final Long onTimeSeconds;
    final String rawTypeFHex;
    final Integer typeFIv;
    final Integer typeFSu;

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String retrievedAtUtc,
                        String structuralFingerprint, String source, String validation,
                        ArchiveNormalizedValues values) {
        this(family, loggerTimestamp, TIME_BASIS_METER_LOCAL, retrievedAtUtc,
                structuralFingerprint, source, validation, values, null);
    }

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String retrievedAtUtc,
                        String structuralFingerprint, String source, String validation,
                        ArchiveNormalizedValues values, MeterTimeEvidence timeEvidence) {
        this(family, loggerTimestamp, TIME_BASIS_METER_LOCAL, retrievedAtUtc,
                structuralFingerprint, source, validation, values, timeEvidence);
    }

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String loggerTimeBasis,
                        String retrievedAtUtc, String structuralFingerprint, String source,
                        String validation, ArchiveNormalizedValues values) {
        this(family, loggerTimestamp, loggerTimeBasis, retrievedAtUtc,
                structuralFingerprint, source, validation, values, null);
    }

    ArchiveFamilyPeriod(Family family, String loggerTimestamp, String loggerTimeBasis,
                        String retrievedAtUtc, String structuralFingerprint, String source,
                        String validation, ArchiveNormalizedValues values,
                        MeterTimeEvidence timeEvidence) {
        this.family = Objects.requireNonNull(family, "family");
        this.loggerTimestamp = requireText(loggerTimestamp, "loggerTimestamp");
        this.loggerTimeBasis = requireText(loggerTimeBasis, "loggerTimeBasis");
        this.retrievedAtUtc = requireText(retrievedAtUtc, "retrievedAtUtc");
        this.structuralFingerprint = requireText(structuralFingerprint, "structuralFingerprint");
        this.source = requireText(source, "source");
        this.validation = requireText(validation, "validation");
        this.values = values == null ? ArchiveNormalizedValues.builder().build() : values;

        Long parsedOnTime = ArchiveOccurrenceKey.parseDurationSeconds(this.values.onTime);
        this.onTimeSeconds = timeEvidence != null && timeEvidence.onTimeSeconds != null
                ? timeEvidence.onTimeSeconds : parsedOnTime;
        if (timeEvidence != null && timeEvidence.typeFPresent) {
            this.rawTypeFHex = timeEvidence.rawTypeFHex;
            this.typeFIv = timeEvidence.invalidTime ? 1 : 0;
            this.typeFSu = timeEvidence.summerTime ? 1 : 0;
        } else {
            this.rawTypeFHex = null;
            this.typeFIv = null;
            this.typeFSu = null;
        }
    }

    static ArchiveFamilyPeriod fromMonthly(MonthlyArchivePeriod monthly) {
        return fromMonthly(monthly, null);
    }

    static ArchiveFamilyPeriod fromMonthly(MonthlyArchivePeriod monthly, MeterTimeEvidence timeEvidence) {
        Objects.requireNonNull(monthly, "monthly");
        return new ArchiveFamilyPeriod(
                Family.MONTH,
                monthly.loggerTimestamp,
                monthly.retrievedAtUtc,
                monthly.structuralFingerprint,
                monthly.source,
                monthly.validation,
                ArchiveNormalizedValues.fromSnapshot(monthly.snapshot),
                timeEvidence);
    }

    String occurrenceKey() {
        return ArchiveOccurrenceKey.fromEvidence(onTimeSeconds, rawTypeFHex);
    }

    String stableKey(String meterId) {
        return requireText(meterId, "meterId") + "|" + family.name() + "|" + loggerTimestamp
                + "|" + occurrenceKey();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
