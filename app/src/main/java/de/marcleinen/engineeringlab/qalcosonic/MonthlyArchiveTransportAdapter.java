package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin production-oriented bridge between the physically validated Monthly wire protocol and the
 * pure {@link MonthlyArchiveEnumerator}. No persistence or UI behavior lives here.
 */
final class MonthlyArchiveTransportAdapter {
    static final int PREPARE_TIMEOUT_MS = 1800;
    static final int SELECT_TIMEOUT_MS = 1800;
    static final int SELECTED_TIMEOUT_MS = 2400;
    static final int RESET_TIMEOUT_MS = 1200;
    static final int RESTORE_COOLDOWN_MS = 1000;
    static final String PROTECTED_DEFAULT_FINGERPRINT =
            "3333e88c66d1d240f37b2ec00d1bb0b5c16f74f1bafda6912569a157e1857314";

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
        return run(hardCap, wire, verifier, retrievedAtUtc, MonthlyArchiveTransportAdapter::mapResponse);
    }

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

    private static String safe(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
