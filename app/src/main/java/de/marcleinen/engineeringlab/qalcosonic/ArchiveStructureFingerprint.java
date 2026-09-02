package de.marcleinen.engineeringlab.qalcosonic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/** Produces the privacy-safe structural fingerprint used for default and archive verification. */
final class ArchiveStructureFingerprint {
    private ArchiveStructureFingerprint() {}

    static String fromInspection(ArchiveRecordInspector.Inspection inspection) {
        if (inspection == null) return null;
        StringBuilder material = new StringBuilder();
        material.append("CI=").append(inspection.ci)
                .append(";RECORD_BYTES=").append(inspection.variableRecordBytes)
                .append(";PARSE_COMPLETE=").append(inspection.parseComplete)
                .append(";RECORD_COUNT=").append(inspection.records.size())
                .append(";MFR_REMAINDER=").append(inspection.manufacturerSpecificRemainder)
                .append(";MFR_BYTES=").append(inspection.manufacturerSpecificBytes)
                .append(";MORE=").append(inspection.moreRecordsFollow).append('\n');
        for (ArchiveRecordInspector.Record record : inspection.records) {
            material.append("R").append(record.index)
                    .append(";DIF=").append(hex(record.dif))
                    .append(";DIFE=").append(hexList(record.dife))
                    .append(";STORAGE=").append(record.storageNumber)
                    .append(";FUNCTION=").append(record.function)
                    .append(";TARIFF=").append(record.tariff)
                    .append(";SUBUNIT=").append(record.subunit)
                    .append(";VIF=").append(hex(record.vif))
                    .append(";VIFE=").append(hexList(record.vife))
                    .append(";DATA_LENGTH=").append(record.dataLength)
                    .append('\n');
        }
        return sha256(material.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) out.append(String.format(Locale.US, "%02x", b & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(int value) {
        return value < 0 ? null : String.format(Locale.US, "0x%02X", value & 0xFF);
    }

    private static String hexList(List<Integer> values) {
        if (values == null || values.isEmpty()) return "NONE";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(hex(values.get(i)));
        }
        return out.toString();
    }
}
