package de.marcleinen.engineeringlab.qalcosonic;

import java.math.BigDecimal;
import java.util.Locale;

/** Native archive occurrence discriminator independent from derived UTC. */
final class ArchiveOccurrenceKey {
    static final String LEGACY = "LEGACY";
    static final String ON_TIME_PREFIX = "OT:";
    static final String TYPE_F_PREFIX = "TF:";

    private ArchiveOccurrenceKey() { }

    static String fromEvidence(Long onTimeSeconds, String rawTypeFHex) {
        if (onTimeSeconds != null && onTimeSeconds >= 0L) {
            return ON_TIME_PREFIX + onTimeSeconds;
        }
        String normalizedTypeF = normalizeTypeFHex(rawTypeFHex);
        if (normalizedTypeF != null) return TYPE_F_PREFIX + normalizedTypeF;
        return LEGACY;
    }

    static String fromLegacyOnTime(String onTimeDisplay) {
        return fromEvidence(parseDurationSeconds(onTimeDisplay), null);
    }

    /** Parses the v2.0 human-readable duration only when it maps exactly to whole seconds. */
    static Long parseDurationSeconds(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2) return null;
        long multiplier;
        switch (parts[1]) {
            case "s": multiplier = 1L; break;
            case "min": multiplier = 60L; break;
            case "h": multiplier = 3_600L; break;
            case "d": multiplier = 86_400L; break;
            default: return null;
        }
        try {
            BigDecimal raw = new BigDecimal(parts[0].replace(',', '.'));
            long seconds = raw.multiply(BigDecimal.valueOf(multiplier)).longValueExact();
            return seconds < 0L ? null : seconds;
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** Canonical key form only; lossless Type-F evidence itself is stored separately. */
    static String normalizeTypeFHex(String rawTypeFHex) {
        if (rawTypeFHex == null) return null;
        String compact = rawTypeFHex.trim().replaceAll("\\s+", "");
        if (compact.length() != 8) return null;
        for (int i = 0; i < compact.length(); i++) {
            if (Character.digit(compact.charAt(i), 16) < 0) return null;
        }
        return compact.toUpperCase(Locale.ROOT);
    }
}
