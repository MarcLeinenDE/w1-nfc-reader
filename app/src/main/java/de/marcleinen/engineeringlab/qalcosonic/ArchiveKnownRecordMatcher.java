package de.marcleinen.engineeringlab.qalcosonic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot matcher used only for incremental archive overlap detection.
 *
 * <p>The snapshot is intentionally taken before the incremental run starts. Records inserted by
 * the current attempt can therefore never become their own overlap evidence. A raw timestamp alone
 * is never sufficient: the native occurrence, canonical content, structure, provenance, time basis
 * and typed ON_TIME must all agree, and any stored conflict disqualifies the record as an overlap
 * anchor.</p>
 */
final class ArchiveKnownRecordMatcher implements ArchiveTraversalStateMachine.KnownRecordMatcher {
    private final String meterId;
    private final ArchiveFamilyPeriod.Family family;
    private final Map<String, ArchiveFamilyStore.StoredPeriod> knownByOccurrence;

    ArchiveKnownRecordMatcher(
            ArchiveFamilyStore store,
            String meterId,
            ArchiveFamilyPeriod.Family family) {
        Objects.requireNonNull(store, "store");
        if (meterId == null || meterId.trim().isEmpty()) {
            throw new IllegalArgumentException("meterId required");
        }
        this.meterId = meterId.trim();
        this.family = Objects.requireNonNull(family, "family");
        this.knownByOccurrence = new HashMap<>();
        List<ArchiveFamilyStore.StoredPeriod> known = store.getPeriods(this.meterId, family);
        for (ArchiveFamilyStore.StoredPeriod period : known) {
            if (period != null && period.loggerTimestamp != null && period.occurrenceKey != null) {
                knownByOccurrence.put(nativeKey(period.loggerTimestamp, period.occurrenceKey), period);
            }
        }
    }

    int snapshotSize() {
        return knownByOccurrence.size();
    }

    @Override public boolean securelyKnown(ArchiveTraversalStateMachine.PeriodEvidence evidence) {
        if (evidence == null || evidence.period == null) return false;
        ArchiveFamilyPeriod candidate = evidence.period;
        if (candidate.family != family) return false;
        if (evidence.onTimeSeconds < 0L) return false;

        String occurrence = ArchiveOccurrenceKey.fromEvidence(evidence.onTimeSeconds, null);
        ArchiveFamilyStore.StoredPeriod known = knownByOccurrence.get(
                nativeKey(candidate.loggerTimestamp, occurrence));
        if (known == null) return false;
        if (!meterId.equals(known.meterId) || known.family != family) return false;

        // Conflict/revision evidence means the canonical row is not strong enough to terminate an
        // incremental traversal, even if this particular observation happens to match one version.
        if (known.conflictFlags != 0 || known.revisionCount != 0) return false;
        if (!same(known.contentFingerprint, known.lastContentFingerprint)) return false;
        if (!same(known.structuralFingerprint, known.lastStructuralFingerprint)) return false;
        if (!same(known.source, known.lastSource) || !same(known.validation, known.lastValidation)) {
            return false;
        }

        if (!same(known.loggerTimeBasis, candidate.loggerTimeBasis)) return false;
        if (!same(known.structuralFingerprint, candidate.structuralFingerprint)) return false;
        if (!same(known.source, candidate.source) || !same(known.validation, candidate.validation)) {
            return false;
        }

        // The persisted SHA-256 is the strongest exact content equality check. Recompute it using
        // the same canonical field order and extras encoding as ArchiveFamilyStore. The explicit
        // field checks below remain as defense in depth and make future schema drift fail closed.
        String candidateContentFingerprint = contentFingerprint(candidate.values);
        if (!same(known.contentFingerprint, candidateContentFingerprint)) return false;
        if (!sameCanonicalContent(known, candidate.values)) return false;

        Long normalizedOnTimeSeconds = durationSeconds(candidate.values.onTime);
        return normalizedOnTimeSeconds != null
                && normalizedOnTimeSeconds.longValue() == evidence.onTimeSeconds
                && known.onTimeSeconds != null
                && known.onTimeSeconds.longValue() == evidence.onTimeSeconds;
    }

    private static String nativeKey(String loggerTimestamp, String occurrenceKey) {
        return String.valueOf(loggerTimestamp) + '\u0000' + String.valueOf(occurrenceKey);
    }

    private static boolean sameCanonicalContent(
            ArchiveFamilyStore.StoredPeriod known,
            ArchiveNormalizedValues candidate) {
        if (known == null || candidate == null) return false;
        // ArchiveFamilyStore exposes totalVolume without its display unit when reading a row. The
        // exact fingerprint above preserves the original unit; normalize only this secondary field
        // comparison to the same read representation.
        if (!same(known.totalVolume, ArchiveFamilyStore.measurementNumber(candidate.totalVolume))) return false;
        if (!same(known.positiveVolume, candidate.positiveVolume)) return false;
        if (!same(known.reverseVolume, candidate.reverseVolume)) return false;
        if (!same(known.tariff1Volume, candidate.tariff1Volume)) return false;
        if (!same(known.maxFlow, candidate.maxFlow)) return false;
        if (!same(known.maxFlowAt, candidate.maxFlowAt)) return false;
        if (!same(known.minFlow, candidate.minFlow)) return false;
        if (!same(known.minFlowAt, candidate.minFlowAt)) return false;
        if (!same(known.flow, candidate.flow)) return false;
        if (!same(known.maxTemperature, candidate.maxTemperature)) return false;
        if (!same(known.maxTemperatureAt, candidate.maxTemperatureAt)) return false;
        if (!same(known.minTemperature, candidate.minTemperature)) return false;
        if (!same(known.minTemperatureAt, candidate.minTemperatureAt)) return false;
        if (!same(known.temperature, candidate.temperature)) return false;
        if (!same(known.externalTemperature, candidate.externalTemperature)) return false;
        if (!same(known.batteryPercent, candidate.batteryPercent)) return false;
        if (!same(known.errorFlags, candidate.errorFlags)) return false;
        if (!same(known.onTime, candidate.onTime)) return false;
        if (!same(known.operatingTime, candidate.operatingTime)) return false;
        return known.extraValues.equals(candidate.extraValues);
    }

    private static String contentFingerprint(ArchiveNormalizedValues values) {
        if (values == null) return null;
        String extras = ArchiveFamilyStore.encodeExtraValues(values.extraValues);
        StringBuilder canonical = new StringBuilder();
        append(canonical, values.totalVolume);
        append(canonical, values.positiveVolume);
        append(canonical, values.reverseVolume);
        append(canonical, values.tariff1Volume);
        append(canonical, values.maxFlow);
        append(canonical, values.maxFlowAt);
        append(canonical, values.minFlow);
        append(canonical, values.minFlowAt);
        append(canonical, values.flow);
        append(canonical, values.maxTemperature);
        append(canonical, values.maxTemperatureAt);
        append(canonical, values.minTemperature);
        append(canonical, values.minTemperatureAt);
        append(canonical, values.temperature);
        append(canonical, values.externalTemperature);
        append(canonical, values.batteryPercent);
        append(canonical, values.errorFlags);
        append(canonical, values.onTime);
        append(canonical, values.operatingTime);
        append(canonical, extras);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void append(StringBuilder out, String value) {
        String normalized = value == null ? "<null>" : value;
        out.append(normalized.length()).append(':').append(normalized).append('|');
    }

    static Long durationSeconds(String value) {
        return ArchiveOccurrenceKey.parseDurationSeconds(value);
    }

    private static boolean same(Object first, Object second) {
        return Objects.equals(first, second);
    }
}
