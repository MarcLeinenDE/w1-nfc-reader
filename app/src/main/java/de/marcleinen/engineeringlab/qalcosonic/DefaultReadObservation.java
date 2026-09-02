package de.marcleinen.engineeringlab.qalcosonic;

/** Minimal production observation used to verify restoration of the default application. */
final class DefaultReadObservation {
    final boolean validMbusLongFrame;
    final boolean recordParseComplete;
    final String structuralFingerprint;

    DefaultReadObservation(
            boolean validMbusLongFrame,
            boolean recordParseComplete,
            String structuralFingerprint) {
        this.validMbusLongFrame = validMbusLongFrame;
        this.recordParseComplete = recordParseComplete;
        this.structuralFingerprint = structuralFingerprint;
    }

    static DefaultReadObservation fromReadout(QalcosonicReader.Readout readout) {
        if (readout == null || readout.meterResponse == null) {
            return new DefaultReadObservation(false, false, null);
        }
        MbusFrameSupport.FrameInspection frame =
                MbusFrameSupport.inspectSelectedResponse(readout.meterResponse);
        ArchiveRecordInspector.Inspection records =
                ArchiveRecordInspector.inspectSelectedResponse(readout.meterResponse);
        return new DefaultReadObservation(
                frame.validLongFrame,
                records.parseComplete,
                ArchiveStructureFingerprint.fromInspection(records));
    }

    boolean healthy() {
        return validMbusLongFrame && recordParseComplete && structuralFingerprint != null;
    }
}
