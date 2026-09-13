package de.marcleinen.engineeringlab.qalcosonic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable external identity for one physical archive occurrence.
 *
 * <p>The ID is intentionally derived only from meter-native source evidence. It does not include
 * reconstructed UTC/local time, database row IDs, retrieval time, phone/install identity or record
 * content. A fresh phone that rereads the same physical archive occurrence therefore produces the
 * same ID, while a later time-anchor correction cannot turn that occurrence into a new record.</p>
 */
final class SourceRecordId {
    static final String PREFIX = "w1:v1:";
    private static final String DOMAIN = "w1-source-record-v1";

    private SourceRecordId() { }

    static String forArchive(ArchiveFamilyStore.StoredPeriod period) {
        Objects.requireNonNull(period, "period");
        return forArchive(period.family, period.meterId, period.loggerTimestamp, period.occurrenceKey);
    }

    static String forArchive(
            ArchiveFamilyPeriod.Family family,
            String meterId,
            String loggerTimestamp,
            String occurrenceKey) {
        Objects.requireNonNull(family, "family");
        String meter = requireText(meterId, "meterId");
        String logger = requireText(loggerTimestamp, "loggerTimestamp");
        String occurrence = occurrenceKey == null || occurrenceKey.trim().isEmpty()
                ? ArchiveOccurrenceKey.LEGACY
                : occurrenceKey.trim();

        // Length-prefix every field so the canonical byte sequence cannot become ambiguous if a
        // future meter/logger identifier happens to contain a separator character.
        String canonical = DOMAIN
                + field(meter)
                + field(family.name())
                + field(logger)
                + field(occurrence);
        return PREFIX + sha256Hex(canonical);
    }

    private static String field(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return "|" + bytes.length + ":" + value;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value.trim();
    }
}
