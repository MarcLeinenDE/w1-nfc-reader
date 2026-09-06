package de.marcleinen.engineeringlab.qalcosonic;

/**
 * TEMPORARY development-only formatter for physical History-sync validation.
 *
 * <p>Deliberately excludes NFC UIDs, raw NFC traffic and raw M-Bus frames. Remove this helper once
 * the v2 family integration has passed the required real-device gates and permanent diagnostics
 * have been designed.</p>
 */
final class HistorySyncTemporaryDebug {
    private HistorySyncTemporaryDebug() {}

    static String running(String expectedMeterId, ArchiveFamilyPeriod.Family family) {
        return "TEMP DEBUG — wird vor Release entfernt\n"
                + "family=" + safeFamily(family) + "\n"
                + "expectedMeter=" + safe(expectedMeterId) + "\n"
                + "phase=RUNNING";
    }

    static String format(
            String expectedMeterId,
            ArchiveFamilyTransportAdapter.Result transport,
            ArchivePersistenceCoordinator.Result persisted) {
        if (transport == null) {
            return "TEMP DEBUG — wird vor Release entfernt\ntransport=MISSING";
        }

        StringBuilder out = new StringBuilder();
        out.append("TEMP DEBUG — wird vor Release entfernt\n");
        line(out, "family", transport.family);
        line(out, "expectedMeter", expectedMeterId);
        line(out, "effectiveStop", transport.effectiveStopReason());
        line(out, "traversalStop", transport.traversal == null ? null : transport.traversal.stopReason);
        line(out, "traversalDiag", transport.traversal == null ? null : transport.traversal.diagnostic);
        line(out, "selectedRequests", transport.selectedRequestsAttempted());
        line(out, "acceptedRecords", transport.traversal == null ? -1 : transport.traversal.periods.size());
        line(out, "persistedAccepted", transport.persistedAccepted);
        line(out, "persistenceDiag", transport.persistenceDiagnostic);
        line(out, "selectAttempted", transport.applicationSelectAttempted);
        line(out, "archivePrepare", transport.archivePrepareAttempted + "/" + transport.archivePrepareSucceeded);
        line(out, "archivePrepareDiag", transport.archivePrepareDiagnostic);

        ArchiveFamilyTransportAdapter.SafetyVerification pre = transport.preflight;
        line(out, "preflightVerified", pre != null && pre.verified);
        line(out, "preflightFailure", pre == null ? null : pre.failureReason);
        line(out, "preflightDiag", pre == null ? null : pre.diagnostic);
        line(out, "preflightMeter", meter(pre));
        line(out, "preflightFingerprint", fingerprint(pre));
        line(out, "preflightRawTime", pre == null ? null : pre.rawMeterTime());
        line(out, "preflightOnTime", pre == null ? null : pre.onTimeSeconds());
        line(out, "preflightResetCommands", pre == null ? -1 : pre.applicationResetCommands);
        line(out, "preflightLiveReads", pre == null ? -1 : pre.liveReadAttempts);

        if (transport.boundary != null) {
            line(out, "boundaryValid", transport.boundary.valid);
            line(out, "nextBoundary", transport.boundary.nextRawBoundary);
            line(out, "boundaryRemainingMs", transport.boundary.conservativeRemainingMs);
        } else {
            line(out, "boundary", "MISSING");
        }

        ArchiveFamilyTransportAdapter.SafetyVerification fin = transport.finalVerification;
        line(out, "finalVerified", fin != null && fin.verified);
        line(out, "finalFailure", fin == null ? null : fin.failureReason);
        line(out, "finalDiag", fin == null ? null : fin.diagnostic);
        line(out, "finalMeter", meter(fin));
        line(out, "finalFingerprint", fingerprint(fin));
        line(out, "finalResetCommands", fin == null ? -1 : fin.applicationResetCommands);
        line(out, "finalLiveReads", fin == null ? -1 : fin.liveReadAttempts);

        if (persisted != null) {
            line(out, "dbAccepted", persisted.accepted);
            line(out, "dbCommitted", persisted.committed);
            line(out, "dbInserted", persisted.inserted);
            line(out, "dbConfirmed", persisted.confirmed);
            line(out, "dbConflicts", persisted.conflicts);
            line(out, "dbFailedTimestamp", persisted.failedLoggerTimestamp);
            line(out, "dbFailure", persisted.failureDiagnostic);
        }
        line(out, "transportDiag", transport.diagnostic);
        return out.toString().trim();
    }

    static String exception(
            String expectedMeterId,
            ArchiveFamilyPeriod.Family family,
            Throwable error) {
        return "TEMP DEBUG — wird vor Release entfernt\n"
                + "family=" + safeFamily(family) + "\n"
                + "expectedMeter=" + safe(expectedMeterId) + "\n"
                + "phase=EXCEPTION\n"
                + "exception=" + (error == null ? "null" : error.getClass().getSimpleName()) + "\n"
                + "message=" + (error == null ? "null" : safe(error.getMessage()));
    }

    private static String meter(ArchiveFamilyTransportAdapter.SafetyVerification verification) {
        return verification == null || verification.observation == null
                ? null : verification.observation.meterId;
    }

    private static String fingerprint(ArchiveFamilyTransportAdapter.SafetyVerification verification) {
        return verification == null ? null : verification.structuralFingerprint();
    }

    private static void line(StringBuilder out, String name, Object value) {
        out.append(name).append('=').append(value == null ? "null" : value).append('\n');
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String safeFamily(ArchiveFamilyPeriod.Family family) {
        return family == null ? "null" : family.name();
    }
}
