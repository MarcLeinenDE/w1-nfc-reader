package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;

/**
 * Production observation used to verify the meter's default application before/after a family
 * archive session. Structural/default fingerprints remain available for the legacy Month path;
 * v2 family safety additionally retains meter identity and raw Type-F/ON_TIME evidence.
 */
final class DefaultReadObservation {
    final boolean validMbusLongFrame;
    final boolean recordParseComplete;
    final String structuralFingerprint;
    final String meterId;
    final MeterTimeEvidence timeEvidence;

    DefaultReadObservation(
            boolean validMbusLongFrame,
            boolean recordParseComplete,
            String structuralFingerprint) {
        this(validMbusLongFrame, recordParseComplete, structuralFingerprint, null, null);
    }

    DefaultReadObservation(
            boolean validMbusLongFrame,
            boolean recordParseComplete,
            String structuralFingerprint,
            String meterId,
            MeterTimeEvidence timeEvidence) {
        this.validMbusLongFrame = validMbusLongFrame;
        this.recordParseComplete = recordParseComplete;
        this.structuralFingerprint = structuralFingerprint;
        this.meterId = normalize(meterId);
        this.timeEvidence = timeEvidence;
    }

    static DefaultReadObservation fromReadout(QalcosonicReader.Readout readout) {
        if (readout == null || readout.meterResponse == null) {
            return new DefaultReadObservation(false, false, null, null, null);
        }
        MbusFrameSupport.FrameInspection frame =
                MbusFrameSupport.inspectSelectedResponse(readout.meterResponse);
        ArchiveRecordInspector.Inspection records =
                ArchiveRecordInspector.inspectSelectedResponse(readout.meterResponse);
        String meterId = null;
        try {
            MbusParser.MeterData parsed = MbusParser.parse(readout.meterResponse);
            meterId = parsed.meterId;
        } catch (IOException ignored) {
            // Structural/default health remains available, but v2 family safety will reject an
            // observation whose meter identity cannot be proven.
        }
        return new DefaultReadObservation(
                frame.validLongFrame,
                records.parseComplete,
                ArchiveStructureFingerprint.fromInspection(records),
                meterId,
                MeterTimeEvidence.inspect(readout.meterResponse));
    }

    boolean healthy() {
        return validMbusLongFrame && recordParseComplete && structuralFingerprint != null;
    }

    boolean fingerprintMatches(String expectedFingerprint) {
        return healthy()
                && expectedFingerprint != null
                && expectedFingerprint.equals(structuralFingerprint);
    }

    ArchiveFamilySyncState.StopReason familySafetyFailure(
            String expectedMeterId,
            String expectedFingerprint) {
        if (!fingerprintMatches(expectedFingerprint)) {
            return ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED;
        }
        String expected = normalize(expectedMeterId);
        // A null expected ID is allowed only for the temporary Month compatibility facade. The
        // immediately preceding dashboard contact already verified the user-selected meter on the
        // same connected NFC tag; this family preflight then adopts the post-reset meter ID and the
        // final Live verification must match it. Missing identity is never accepted.
        if (meterId == null || (expected != null && !expected.equals(meterId))) {
            return ArchiveFamilySyncState.StopReason.METER_ID_MISMATCH;
        }
        if (timeEvidence == null || !timeEvidence.frameValid || !timeEvidence.typeFPresent) {
            return ArchiveFamilySyncState.StopReason.TYPE_F_MISSING;
        }
        if (timeEvidence.invalidTime
                || timeEvidence.reservedMinuteBit6Set
                || timeEvidence.reservedHourBitsSet
                || timeEvidence.decodedRawWallClock == null) {
            return ArchiveFamilySyncState.StopReason.TYPE_F_INVALID;
        }
        if (timeEvidence.onTimeSeconds == null || timeEvidence.onTimeSeconds < 0L) {
            return ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT;
        }
        return ArchiveFamilySyncState.StopReason.NONE;
    }

    boolean verifiedForFamilySafety(String expectedMeterId, String expectedFingerprint) {
        return familySafetyFailure(expectedMeterId, expectedFingerprint)
                == ArchiveFamilySyncState.StopReason.NONE;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
