package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;

/**
 * Production observation used to verify the meter's default application before/after a family
 * archive session. A concrete default fingerprint may be used as within-session consistency
 * evidence, but must not become a global W1 firmware allow-list.
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
        if (!healthy()) {
            return ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED;
        }
        // Research proved the fingerprint useful as session-consistency evidence, not as a global
        // firmware allow-list. The preflight therefore accepts any healthy structure and the final
        // Live read may then require the exact preflight fingerprint.
        if (expectedFingerprint != null && !expectedFingerprint.equals(structuralFingerprint)) {
            return ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED;
        }
        String expected = normalize(expectedMeterId);
        if (expected == null || meterId == null || !expected.equals(meterId)) {
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
