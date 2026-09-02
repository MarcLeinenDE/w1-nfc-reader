package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Locale;

/** Production-only structural interpretation of a selected Monthly archive response. */
final class MonthlyArchiveResponse {
    static final int MAX_PRIVACY_SAFE_SHORT_RESPONSE_BYTES = 8;

    private MonthlyArchiveResponse() {}

    static Observation inspect(byte[] payload) {
        if (payload == null) return Observation.invalid("NO_HOST_RESPONSE");

        MbusFrameSupport.FrameInspection frame = MbusFrameSupport.inspectSelectedResponse(payload);
        if (!frame.validLongFrame) {
            return Observation.invalid("MBUS_LONG_FRAME_NOT_FOUND");
        }

        ArchiveRecordInspector.Inspection records =
                ArchiveRecordInspector.inspectSelectedResponse(payload);
        return new Observation(
                true,
                records.parseComplete,
                records.errorCode,
                loggerTimestamp(records),
                ArchiveStructureFingerprint.fromInspection(records));
    }

    static boolean healthyMonthlyFrame(Observation observation) {
        return observation != null
                && observation.validLongFrame
                && observation.parseComplete
                && observation.loggerTimestamp != null
                && observation.structuralFingerprint != null;
    }

    static String safeShortHex(byte[] payload) {
        if (payload == null
                || payload.length < 1
                || payload.length > MAX_PRIVACY_SAFE_SHORT_RESPONSE_BYTES) {
            return null;
        }
        StringBuilder out = new StringBuilder(payload.length * 3);
        for (int i = 0; i < payload.length; i++) {
            if (i > 0) out.append(' ');
            out.append(String.format(Locale.US, "%02X", payload[i] & 0xFF));
        }
        return out.toString();
    }

    private static String loggerTimestamp(ArchiveRecordInspector.Inspection inspection) {
        if (inspection == null || !inspection.parseComplete) return null;
        for (ArchiveRecordInspector.Record record : inspection.records) {
            if ("TIME_POINT_DATE_TIME".equals(record.semantic)) return record.value;
        }
        return null;
    }

    static final class Observation {
        final boolean validLongFrame;
        final boolean parseComplete;
        final String parseError;
        final String loggerTimestamp;
        final String structuralFingerprint;

        Observation(
                boolean validLongFrame,
                boolean parseComplete,
                String parseError,
                String loggerTimestamp,
                String structuralFingerprint) {
            this.validLongFrame = validLongFrame;
            this.parseComplete = parseComplete;
            this.parseError = parseError;
            this.loggerTimestamp = loggerTimestamp;
            this.structuralFingerprint = structuralFingerprint;
        }

        static Observation invalid(String error) {
            return new Observation(false, false, error, null, null);
        }
    }
}
