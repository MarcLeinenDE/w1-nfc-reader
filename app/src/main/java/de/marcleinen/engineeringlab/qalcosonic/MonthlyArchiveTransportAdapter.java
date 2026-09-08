package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the released Month call site plus the legacy isolated test adapter.
 *
 * <p>When the real production wire implements the v2 family interfaces, the four-argument
 * {@link #run(int, Wire, DefaultVerifier, String)} delegates to the cap-free family safety shell.
 * The hard-cap argument is ignored on that production path and is retained only until the old
 * dashboard call site is retired by the Settings-based v2 History UX. The five-argument overload
 * remains an isolated legacy regression seam for synthetic tests and is not used by the real
 * {@link MonthlyArchiveNfcWire} production path.</p>
 */
final class MonthlyArchiveTransportAdapter {
    static final int PREPARE_TIMEOUT_MS = 1800;
    static final int SELECT_TIMEOUT_MS = 1800;
    static final int SELECTED_TIMEOUT_MS = 2400;
    static final int RESET_TIMEOUT_MS = 1200;
    static final int RESTORE_COOLDOWN_MS = 1000;
    static final String PROTECTED_DEFAULT_FINGERPRINT =
            "3333e88c66d1d240f37b2ec00d1bb0b5c16f74f1bafda6912569a157e1857314";
    private static final String V2_BRIDGE_METER_ID = "__W1_V2_MONTH_SAME_TAG__";

    interface Wire {
        void prepare() throws IOException;
        byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) throws IOException;
        void coolDown(long millis);
        boolean transportHealthy();
    }

    interface DefaultVerifier {
        DefaultReadObservation readDefault() throws IOException;
    }

    interface ResponseMapper {
        MonthlyArchiveEnumerator.Candidate map(byte[] response, String retrievedAtUtc);
    }

    static final class RestoreResult {
        int applicationResetCommands;
        int defaultVerifyAttempts;
        boolean defaultVerified;
        boolean transportHealthy;
        String status = "NOT_ATTEMPTED";
        String diagnostic;
    }

    static final class Result {
        final MonthlyArchiveEnumerator.Result enumeration;
        final List<String> selectedControls;
        final int hardCap;
        String sessionStopReason;
        String diagnostic;
        boolean monthlySelectAttempted;
        RestoreResult restore = new RestoreResult();

        Result(MonthlyArchiveEnumerator.Result enumeration, List<String> selectedControls, int hardCap) {
            this.enumeration = enumeration;
            this.selectedControls = Collections.unmodifiableList(new ArrayList<>(selectedControls));
            this.hardCap = hardCap;
        }

        int selectedRequestsAttempted() { return selectedControls.size(); }
    }

    static Result run(int hardCap, Wire wire, DefaultVerifier verifier, String retrievedAtUtc) {
        if (wire instanceof ArchiveFamilyTransportAdapter.Wire
                && verifier instanceof ArchiveFamilyTransportAdapter.DefaultVerifier) {
            return runV2CompatibilityFacade(
                    (ArchiveFamilyTransportAdapter.Wire) wire,
                    verifier,
                    retrievedAtUtc);
        }
        return run(hardCap, wire, verifier, retrievedAtUtc, MonthlyArchiveTransportAdapter::mapResponse);
    }

    /**
     * Legacy synthetic regression seam. The real MonthlyArchiveNfcWire implements the v2 family
     * interfaces and therefore never reaches this method through the production four-argument
     * call above.
     */
    static Result run(int hardCap, Wire wire, DefaultVerifier verifier, String retrievedAtUtc,
                      ResponseMapper mapper) {
        if (wire == null) throw new IllegalArgumentException("wire == null");
        if (verifier == null) throw new IllegalArgumentException("verifier == null");
        if (mapper == null) throw new IllegalArgumentException("mapper == null");

        MonthlyArchiveEnumerator.Session session = new MonthlyArchiveEnumerator.Session(hardCap);
        List<String> controls = new ArrayList<>();
        String stop = null;
        String diagnostic = null;
        boolean restoreNeeded = false;

        try {
            wire.prepare();
            byte[] reset = wire.exchange("MONTHLY_SYNC_MBUS_RESET", MbusFrameSupport.reset(),
                    PREPARE_TIMEOUT_MS);
            if (reset == null) {
                stop = "PREPARE_RESET_NO_HOST_RESPONSE";
            } else {
                restoreNeeded = true;
                byte[] select = wire.exchange("MONTHLY_SYNC_SELECT_50_40",
                        MbusFrameSupport.monthlyApplicationSelect(), SELECT_TIMEOUT_MS);
                if (select == null) {
                    stop = "MONTH_SELECT_NO_HOST_RESPONSE";
                } else {
                    while (true) {
                        String control = session.nextControl();
                        if (control == null) break;
                        byte[] request = requestForControl(control);
                        controls.add(control);
                        try {
                            byte[] response = wire.exchange(
                                    "MONTHLY_SYNC_SELECTED_" + controls.size() + "_" + control,
                                    request,
                                    SELECTED_TIMEOUT_MS);
                            MonthlyArchiveEnumerator.Candidate candidate;
                            try {
                                candidate = mapper.map(response, retrievedAtUtc);
                            } catch (RuntimeException error) {
                                candidate = MonthlyArchiveEnumerator.Candidate.other(
                                        "DECODE_EXCEPTION:" + safe(error));
                            }
                            session.accept(candidate);
                        } catch (IOException error) {
                            session.accept(MonthlyArchiveEnumerator.Candidate.ioError(safe(error)));
                        }
                        if (session.stopped()) break;
                    }
                    stop = session.stopReason().name();
                    diagnostic = session.diagnostic();
                }
            }
        } catch (IOException error) {
            stop = "TRANSPORT_PREPARE_IO_ERROR";
            diagnostic = safe(error);
        }

        Result out = new Result(session.result(), controls, hardCap);
        out.sessionStopReason = stop == null ? session.stopReason().name() : stop;
        out.diagnostic = diagnostic;
        out.monthlySelectAttempted = restoreNeeded;
        if (restoreNeeded) out.restore = restore(wire, verifier);
        return out;
    }

    private static Result runV2CompatibilityFacade(
            ArchiveFamilyTransportAdapter.Wire wire,
            DefaultVerifier verifier,
            String retrievedAtUtc) {
        if (wire == null) throw new IllegalArgumentException("wire == null");
        if (verifier == null) throw new IllegalArgumentException("verifier == null");
        if (retrievedAtUtc == null || retrievedAtUtc.trim().isEmpty()) {
            throw new IllegalArgumentException("retrievedAtUtc required");
        }

        ArchiveFamilyPolicy policy = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH);
        Map<String, MonthlyArchivePeriod> mappedPeriods = new LinkedHashMap<>();
        ArchiveFamilyTransportAdapter.ResponseMapper mapper = (familyPolicy, response, retrieved) -> {
            ArchiveTraversalStateMachine.Candidate generic =
                    ArchiveFamilyTransportAdapter.mapProductionResponse(familyPolicy, response, retrieved);
            if (generic.kind == ArchiveTraversalStateMachine.CandidateKind.PERIOD
                    && generic.evidence != null && generic.evidence.period != null) {
                MonthlyArchiveEnumerator.Candidate legacy = mapResponse(response, retrieved);
                if (legacy.kind != MonthlyArchiveEnumerator.CandidateKind.PERIOD
                        || legacy.period == null) {
                    return ArchiveTraversalStateMachine.Candidate.failure(
                            ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                            "legacy Month snapshot mapping unavailable");
                }
                mappedPeriods.put(generic.evidence.period.loggerTimestamp, legacy.period);
            }
            return generic;
        };

        SameTagIdentityBridge bridgeVerifier = new SameTagIdentityBridge(verifier);
        ArchiveFamilyTransportAdapter.Result v2 = ArchiveFamilyTransportAdapter.run(
                policy,
                wire,
                bridgeVerifier,
                V2_BRIDGE_METER_ID,
                retrievedAtUtc,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper);

        List<MonthlyArchivePeriod> accepted = new ArrayList<>();
        boolean mappingComplete = true;
        for (ArchiveTraversalStateMachine.PeriodEvidence evidence : v2.traversal.periods) {
            MonthlyArchivePeriod period = mappedPeriods.get(evidence.rawTimestamp());
            if (period == null) {
                mappingComplete = false;
                break;
            }
            accepted.add(period);
        }

        MonthlyArchiveEnumerator.StopReason legacyStop = mappingComplete
                ? legacyStop(v2.traversal.stopReason, v2.traversal.diagnostic)
                : MonthlyArchiveEnumerator.StopReason.OTHER_RESPONSE;
        String legacyDiagnostic = mappingComplete
                ? v2.traversal.diagnostic
                : "v2 accepted Month period could not be mapped to legacy snapshot";
        MonthlyArchiveEnumerator.Result enumeration = new MonthlyArchiveEnumerator.Result(
                accepted,
                legacyStop,
                legacyDiagnostic,
                v2.selectedRequestsAttempted(),
                0);

        Result out = new Result(enumeration, v2.selectedControls, 0);
        out.sessionStopReason = v2.effectiveStopReason().name();
        out.diagnostic = firstNonBlank(
                legacyDiagnostic,
                v2.finalVerification == null ? null : v2.finalVerification.diagnostic,
                v2.diagnostic);
        out.monthlySelectAttempted = v2.applicationSelectAttempted;
        out.restore = legacyRestore(v2, wire);
        return out;
    }

    private static MonthlyArchiveEnumerator.StopReason legacyStop(
            ArchiveFamilySyncState.StopReason reason,
            String diagnostic) {
        if (reason == null) return MonthlyArchiveEnumerator.StopReason.OTHER_RESPONSE;
        switch (reason) {
            case PROTOCOL_TERMINAL:
                return "E5 7D".equals(diagnostic)
                        ? MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D
                        : MonthlyArchiveEnumerator.StopReason.TERMINAL_STANDARD_E5_NO_DATA;
            case RING_WRAP_DETECTED:
                // Compatibility alias only: the old dashboard knows only terminalConfirmed().
                // The v2 traversal result remains the authoritative semantic reason.
                return MonthlyArchiveEnumerator.StopReason.TERMINAL_STANDARD_E5_NO_DATA;
            case NO_HOST_RESPONSE:
                return MonthlyArchiveEnumerator.StopReason.NO_HOST_RESPONSE;
            case IO_ERROR:
            case TRANSPORT_SESSION_LOST:
                return MonthlyArchiveEnumerator.StopReason.IO_ERROR;
            case STRUCTURE_ERROR:
                return MonthlyArchiveEnumerator.StopReason.STRUCTURE_CHANGED;
            case PROGRESSION_INCONSISTENT:
                return MonthlyArchiveEnumerator.StopReason.NON_MONTHLY_PROGRESSION;
            default:
                return MonthlyArchiveEnumerator.StopReason.OTHER_RESPONSE;
        }
    }

    private static RestoreResult legacyRestore(
            ArchiveFamilyTransportAdapter.Result v2,
            ArchiveFamilyTransportAdapter.Wire wire) {
        RestoreResult restore = new RestoreResult();
        if (v2.finalVerification == null) {
            restore.status = "V2_FINAL_VERIFICATION_NOT_ATTEMPTED";
            restore.transportHealthy = wire.transportHealthy();
            return restore;
        }
        restore.applicationResetCommands = v2.finalVerification.applicationResetCommands;
        restore.defaultVerifyAttempts = v2.finalVerification.liveReadAttempts;
        restore.defaultVerified = v2.finalRestoreVerified();
        restore.transportHealthy = wire.transportHealthy();
        restore.status = restore.defaultVerified
                ? "DEFAULT_VERIFIED"
                : "V2_" + v2.finalVerification.failureReason.name();
        restore.diagnostic = v2.finalVerification.diagnostic;
        return restore;
    }

    static byte[] requestForControl(String control) {
        if (MonthlyArchiveEnumerator.CONTROL_FCB1_7B.equals(control)) {
            return MbusFrameSupport.reqUd2Fcb1();
        }
        if (MonthlyArchiveEnumerator.CONTROL_FCB0_5B.equals(control)) {
            return MbusFrameSupport.reqUd2Fcb0();
        }
        throw new IllegalArgumentException("unsupported Monthly control " + control);
    }

    static MonthlyArchiveEnumerator.Candidate mapResponse(byte[] response, String retrievedAtUtc) {
        if (response == null) return MonthlyArchiveEnumerator.Candidate.noHost();
        if (response.length <= MonthlyArchiveResponse.MAX_PRIVACY_SAFE_SHORT_RESPONSE_BYTES) {
            MonthlyArchiveEnumerator.Candidate shortCandidate =
                    MonthlyArchiveEnumerator.classifyShortResponse(response);
            if (shortCandidate.kind != MonthlyArchiveEnumerator.CandidateKind.OTHER_RESPONSE) {
                return shortCandidate;
            }
        }

        MonthlyArchiveResponse.Observation observation = MonthlyArchiveResponse.inspect(response);
        if (!MonthlyArchiveResponse.healthyMonthlyFrame(observation)) {
            String shortHex = MonthlyArchiveResponse.safeShortHex(response);
            String reason = shortHex != null
                    ? shortHex
                    : (observation.parseError != null
                    ? observation.parseError
                    : "NON_MONTHLY_RESPONSE_LENGTH=" + response.length);
            return MonthlyArchiveEnumerator.Candidate.other(reason);
        }

        ArchiveRecordInspector.Inspection inspection =
                ArchiveRecordInspector.inspectSelectedResponse(response);
        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                ArchivePeriodSnapshot.PeriodType.MONTH, inspection, retrievedAtUtc);
        MonthlyArchivePeriod period = new MonthlyArchivePeriod(
                observation.loggerTimestamp,
                retrievedAtUtc,
                observation.structuralFingerprint,
                snapshot);
        return MonthlyArchiveEnumerator.Candidate.period(period);
    }

    private static RestoreResult restore(Wire wire, DefaultVerifier verifier) {
        RestoreResult result = new RestoreResult();
        wire.coolDown(RESTORE_COOLDOWN_MS);
        issueReset(wire, result, "MONTHLY_SYNC_APPLICATION_RESET_DEFAULT");
        wire.coolDown(RESTORE_COOLDOWN_MS);
        Verification first = verify(verifier, wire, result);
        if (first.verified) {
            result.defaultVerified = true;
            result.transportHealthy = true;
            result.status = "DEFAULT_VERIFIED";
            return result;
        }

        boolean transportHealthy = first.transportHealthy && wire.transportHealthy();
        if (MonthlyArchiveRestorePolicy.shouldRetryApplicationReset(
                first.defaultHealthy, first.fingerprintMatch, transportHealthy, 0)) {
            issueReset(wire, result, "MONTHLY_SYNC_APPLICATION_RESET_DEFAULT_RETRY");
            wire.coolDown(RESTORE_COOLDOWN_MS);
            Verification second = verify(verifier, wire, result);
            result.defaultVerified = second.verified;
            result.transportHealthy = second.transportHealthy && wire.transportHealthy();
            result.status = MonthlyArchiveRestorePolicy.status(
                    second.defaultHealthy, second.fingerprintMatch, result.transportHealthy, 1);
            if (second.diagnostic != null) result.diagnostic = second.diagnostic;
            return result;
        }

        result.defaultVerified = false;
        result.transportHealthy = transportHealthy;
        result.status = MonthlyArchiveRestorePolicy.status(
                first.defaultHealthy, first.fingerprintMatch, transportHealthy, 0);
        result.diagnostic = first.diagnostic;
        return result;
    }

    private static void issueReset(Wire wire, RestoreResult result, String label) {
        result.applicationResetCommands++;
        try {
            wire.exchange(label, MbusFrameSupport.applicationResetDefault(), RESET_TIMEOUT_MS);
        } catch (IOException error) {
            result.diagnostic = safe(error);
        }
    }

    private static Verification verify(DefaultVerifier verifier, Wire wire, RestoreResult result) {
        result.defaultVerifyAttempts++;
        try {
            DefaultReadObservation observation = verifier.readDefault();
            boolean healthy = observation != null && observation.healthy();
            boolean match = healthy && PROTECTED_DEFAULT_FINGERPRINT.equals(
                    observation.structuralFingerprint);
            return new Verification(healthy, match, true, null);
        } catch (IOException error) {
            return new Verification(false, false, false, safe(error));
        }
    }

    private static final class SameTagIdentityBridge
            implements ArchiveFamilyTransportAdapter.DefaultVerifier {
        private final DefaultVerifier delegate;
        private String adoptedMeterId;

        SameTagIdentityBridge(DefaultVerifier delegate) {
            this.delegate = delegate;
        }

        @Override public DefaultReadObservation readDefault() throws IOException {
            DefaultReadObservation observation = delegate.readDefault();
            if (observation == null || observation.meterId == null) return observation;
            if (adoptedMeterId == null) adoptedMeterId = observation.meterId;
            String bridgedMeterId = adoptedMeterId.equals(observation.meterId)
                    ? V2_BRIDGE_METER_ID
                    : observation.meterId;
            return new DefaultReadObservation(
                    observation.validMbusLongFrame,
                    observation.recordParseComplete,
                    observation.structuralFingerprint,
                    bridgedMeterId,
                    observation.timeEvidence);
        }
    }

    private static final class Verification {
        final boolean defaultHealthy;
        final boolean fingerprintMatch;
        final boolean transportHealthy;
        final boolean verified;
        final String diagnostic;

        Verification(boolean defaultHealthy, boolean fingerprintMatch, boolean transportHealthy,
                     String diagnostic) {
            this.defaultHealthy = defaultHealthy;
            this.fingerprintMatch = fingerprintMatch;
            this.transportHealthy = transportHealthy;
            this.verified = MonthlyArchiveRestorePolicy.restored(defaultHealthy, fingerprintMatch);
            this.diagnostic = diagnostic;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private static String safe(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
