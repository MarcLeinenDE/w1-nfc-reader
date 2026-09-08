package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Family-neutral v2 safety-shell transport orchestrator.
 *
 * <p>The current production entry point is Month only. Hour/Day can reuse the same shell after
 * their response mapping and product exposure are enabled. Completion never depends on a record
 * count: only protocol/ring/incremental semantic stops plus a verified final default Live read can
 * complete a family attempt.</p>
 */
final class ArchiveFamilyTransportAdapter {
    static final int PREPARE_TIMEOUT_MS = 1800;
    static final int SELECT_TIMEOUT_MS = 1800;
    static final int SELECTED_TIMEOUT_MS = 2400;
    static final int RESET_TIMEOUT_MS = 1200;
    static final int STABILIZATION_MS = 1000;
    static final int RESTORE_PRE_RESET_COOLDOWN_MS = 1000;

    // The protected Live reader waits up to 1.8 s for each of its M-Bus reset/read commands.
    // Keep enough time before a raw archive boundary for one selected request, the Research-proven
    // pre-restore cooldown, default restore, the two protected Live commands and transport overhead.
    static final long PROTECTED_LIVE_MBUS_BUDGET_MS = 2L * 1800L;
    static final long TRANSPORT_OVERHEAD_BUDGET_MS = 2000L;
    static final long BOUNDARY_GUARD_BUDGET_MS =
            SELECTED_TIMEOUT_MS
                    + RESTORE_PRE_RESET_COOLDOWN_MS
                    + RESET_TIMEOUT_MS
                    + STABILIZATION_MS
                    + PROTECTED_LIVE_MBUS_BUDGET_MS
                    + TRANSPORT_OVERHEAD_BUDGET_MS;

    // Pure failsafe only. Hitting this is always PARTIAL/INCOMPLETE, never archive completion.
    static final long TECHNICAL_WATCHDOG_MS = 30L * 60L * 1000L;

    interface Wire {
        void prepare() throws IOException;
        byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) throws IOException;
        void coolDown(long millis);
        boolean transportHealthy();
        long elapsedRealtimeMs();
    }

    interface DefaultVerifier {
        DefaultReadObservation readDefault() throws IOException;
    }

    interface ResponseMapper {
        ArchiveTraversalStateMachine.Candidate map(
                ArchiveFamilyPolicy policy,
                byte[] response,
                String retrievedAtUtc);
    }

    /** Called synchronously after, and only after, the traversal accepts a new stable record. */
    interface AcceptedPeriodSink {
        void persist(ArchiveFamilyPeriod period);
    }

    static final class SafetyVerification {
        int applicationResetCommands;
        int liveReadAttempts;
        boolean verified;
        ArchiveFamilySyncState.StopReason failureReason = ArchiveFamilySyncState.StopReason.NONE;
        String diagnostic;
        DefaultReadObservation observation;

        Long onTimeSeconds() {
            return observation == null || observation.timeEvidence == null
                    ? null : observation.timeEvidence.onTimeSeconds;
        }

        String rawMeterTime() {
            return observation == null || observation.timeEvidence == null
                    ? null : observation.timeEvidence.decodedRawWallClock;
        }

        String structuralFingerprint() {
            return observation == null ? null : observation.structuralFingerprint;
        }
    }

    static final class Result {
        final ArchiveFamilyPeriod.Family family;
        final ArchiveFamilySyncState.SyncMode mode;
        final ArchiveTraversalStateMachine.Result traversal;
        final List<String> selectedControls;
        final SafetyVerification preflight;
        final SafetyVerification finalVerification;
        final ArchiveFamilyPolicy.Boundary boundary;
        final boolean applicationSelectAttempted;
        final long boundaryDeadlineElapsedMs;
        final long watchdogDeadlineElapsedMs;
        final String diagnostic;
        final boolean persistenceAttached;
        final int persistedAccepted;
        final String persistenceDiagnostic;
        final boolean archivePrepareAttempted;
        final boolean archivePrepareSucceeded;
        final String archivePrepareDiagnostic;

        Result(
                ArchiveFamilyPeriod.Family family,
                ArchiveFamilySyncState.SyncMode mode,
                ArchiveTraversalStateMachine.Result traversal,
                List<String> selectedControls,
                SafetyVerification preflight,
                SafetyVerification finalVerification,
                ArchiveFamilyPolicy.Boundary boundary,
                boolean applicationSelectAttempted,
                long boundaryDeadlineElapsedMs,
                long watchdogDeadlineElapsedMs,
                String diagnostic,
                boolean persistenceAttached,
                int persistedAccepted,
                String persistenceDiagnostic,
                boolean archivePrepareAttempted,
                boolean archivePrepareSucceeded,
                String archivePrepareDiagnostic) {
            this.family = family;
            this.mode = mode;
            this.traversal = traversal;
            this.selectedControls = Collections.unmodifiableList(new ArrayList<>(selectedControls));
            this.preflight = preflight;
            this.finalVerification = finalVerification;
            this.boundary = boundary;
            this.applicationSelectAttempted = applicationSelectAttempted;
            this.boundaryDeadlineElapsedMs = boundaryDeadlineElapsedMs;
            this.watchdogDeadlineElapsedMs = watchdogDeadlineElapsedMs;
            this.diagnostic = diagnostic;
            this.persistenceAttached = persistenceAttached;
            this.persistedAccepted = persistedAccepted;
            this.persistenceDiagnostic = persistenceDiagnostic;
            this.archivePrepareAttempted = archivePrepareAttempted;
            this.archivePrepareSucceeded = archivePrepareSucceeded;
            this.archivePrepareDiagnostic = archivePrepareDiagnostic;
        }

        int selectedRequestsAttempted() {
            return traversal.selectedRequestsAttempted;
        }

        boolean finalRestoreVerified() {
            return finalVerification != null && finalVerification.verified;
        }

        boolean completeSafetyShell() {
            return traversal.semanticSuccess() && finalRestoreVerified();
        }

        boolean persistenceComplete() {
            return persistenceAttached
                    && persistenceDiagnostic == null
                    && persistedAccepted == traversal.periods.size();
        }

        /** Full product completion requires both the protocol shell and durable accepted records. */
        boolean completeProductAttempt() {
            return completeSafetyShell() && persistenceComplete();
        }

        ArchiveFamilySyncState.StopReason effectiveStopReason() {
            if (persistenceDiagnostic != null
                    || (persistenceAttached && persistedAccepted != traversal.periods.size())) {
                return ArchiveFamilySyncState.StopReason.PERSISTENCE_ERROR;
            }
            // A semantic traversal end is only successful together with the final default Live.
            // If that final verification fails, expose the actual safety failure instead of the
            // earlier protocol terminal / ring / known-record marker.
            if (traversal.semanticSuccess()
                    && finalVerification != null
                    && finalVerification.failureReason != ArchiveFamilySyncState.StopReason.NONE) {
                return finalVerification.failureReason;
            }
            if (traversal.stopReason != ArchiveFamilySyncState.StopReason.NONE) {
                return traversal.stopReason;
            }
            if (preflight != null && preflight.failureReason != ArchiveFamilySyncState.StopReason.NONE) {
                return preflight.failureReason;
            }
            if (finalVerification != null
                    && finalVerification.failureReason != ArchiveFamilySyncState.StopReason.NONE) {
                return finalVerification.failureReason;
            }
            return ArchiveFamilySyncState.StopReason.UNKNOWN_ERROR;
        }
    }

    static Result runMonth(
            Wire wire,
            DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode) {
        return runMonth(wire, verifier, expectedMeterId, retrievedAtUtc, mode, null);
    }

    static Result runMonth(
            Wire wire,
            DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            AcceptedPeriodSink acceptedPeriodSink) {
        return run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                expectedMeterId,
                retrievedAtUtc,
                mode,
                null,
                0,
                ArchiveFamilyTransportAdapter::mapProductionResponse,
                acceptedPeriodSink);
    }

    static Result run(
            ArchiveFamilyPolicy policy,
            Wire wire,
            DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            ArchiveTraversalStateMachine.KnownRecordMatcher knownMatcher,
            int requiredKnownOverlap,
            ResponseMapper mapper) {
        return run(policy, wire, verifier, expectedMeterId, retrievedAtUtc, mode,
                knownMatcher, requiredKnownOverlap, mapper, null);
    }

    static Result run(
            ArchiveFamilyPolicy policy,
            Wire wire,
            DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            ArchiveTraversalStateMachine.KnownRecordMatcher knownMatcher,
            int requiredKnownOverlap,
            ResponseMapper mapper,
            AcceptedPeriodSink acceptedPeriodSink) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(mapper, "mapper");
        if (expectedMeterId == null || expectedMeterId.trim().isEmpty()) {
            throw new IllegalArgumentException("expectedMeterId required");
        }
        if (retrievedAtUtc == null || retrievedAtUtc.trim().isEmpty()) {
            throw new IllegalArgumentException("retrievedAtUtc required");
        }

        ArchiveTraversalStateMachine.Session session = new ArchiveTraversalStateMachine.Session(
                policy, mode, knownMatcher, requiredKnownOverlap);
        List<String> controls = new ArrayList<>();
        SafetyVerification preflight = new SafetyVerification();
        SafetyVerification finalVerification = new SafetyVerification();
        ArchiveFamilyPolicy.Boundary boundary = null;
        boolean selectAttempted = false;
        boolean restoreNeeded = false;
        long boundaryDeadline = -1L;
        long watchdogDeadline = -1L;
        String diagnostic = null;
        int persistedAccepted = 0;
        String persistenceDiagnostic = null;
        boolean archivePrepareAttempted = false;
        boolean archivePrepareSucceeded = false;
        String archivePrepareDiagnostic = null;

        try {
            wire.prepare();
            restoreNeeded = true;

            boolean resetAccepted = issueApplicationReset(
                    wire, preflight, "ARCHIVE_SYNC_START_RESET_DEFAULT");
            if (!resetAccepted) {
                session.abort(preflight.failureReason, preflight.diagnostic);
            } else {
                wire.coolDown(STABILIZATION_MS);
                verifyDefault(verifier, preflight, expectedMeterId, null);
                if (!preflight.verified) {
                    session.abort(preflight.failureReason, preflight.diagnostic);
                } else {
                    boundary = policy.nextBoundary(preflight.rawMeterTime());
                    if (boundary == null || !boundary.valid) {
                        session.abort(ArchiveFamilySyncState.StopReason.TYPE_F_INVALID,
                                "family boundary could not be derived from verified raw meter time");
                    } else {
                        long anchorElapsed = wire.elapsedRealtimeMs();
                        boundaryDeadline = safeAdd(anchorElapsed, boundary.conservativeRemainingMs);
                        watchdogDeadline = safeAdd(anchorElapsed, TECHNICAL_WATCHDOG_MS);
                        if (!canIssueSelectedRequest(wire.elapsedRealtimeMs(), boundaryDeadline)) {
                            session.abort(ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                                    "insufficient safe time before " + boundary.nextRawBoundary);
                        }
                    }
                }
            }

            // Frozen Research performed a fresh ST25 mailbox/addressing prepare after the protected
            // Live preflight and before the archive M-Bus reset/select. QalcosonicReader uses the
            // same physical mailbox independently, so re-establish this transport state explicitly
            // instead of assuming the pre-Live prepare remains authoritative.
            if (!session.stopped()) {
                archivePrepareAttempted = true;
                try {
                    wire.prepare();
                    archivePrepareSucceeded = true;
                } catch (IOException error) {
                    archivePrepareDiagnostic = safe(error);
                    session.abort(ArchiveFamilySyncState.StopReason.IO_ERROR,
                            "post-Live archive wire prepare failed: " + archivePrepareDiagnostic);
                }
            }

            if (!session.stopped()) {
                byte[] mbusReset = wire.exchange(
                        "ARCHIVE_SYNC_MBUS_RESET", MbusFrameSupport.reset(), PREPARE_TIMEOUT_MS);
                if (mbusReset == null) {
                    session.abort(ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE,
                            "M-Bus reset returned no host response");
                }
            }

            if (!session.stopped()) {
                selectAttempted = true;
                byte[] select = wire.exchange(
                        "ARCHIVE_SYNC_SELECT_50_" + hex2(policy.selectorSubcode),
                        policy.applicationSelectFrame(), SELECT_TIMEOUT_MS);
                if (select == null) {
                    session.abort(ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE,
                            "archive application select returned no host response");
                }
            }

            while (!session.stopped()) {
                long now = wire.elapsedRealtimeMs();
                if (watchdogDeadline > 0L && now >= watchdogDeadline) {
                    session.abort(ArchiveFamilySyncState.StopReason.WATCHDOG_REACHED,
                            "technical wall-clock watchdog reached");
                    break;
                }
                if (!canIssueSelectedRequest(now, boundaryDeadline)) {
                    session.abort(ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                            "family boundary safety reserve reached");
                    break;
                }

                String control = session.nextControl();
                if (control == null) break;
                controls.add(control);
                byte[] request = requestForControl(control);
                try {
                    byte[] response = wire.exchange(
                            "ARCHIVE_SYNC_SELECTED_" + controls.size() + "_" + control,
                            request,
                            SELECTED_TIMEOUT_MS);
                    ArchiveTraversalStateMachine.Candidate candidate;
                    try {
                        candidate = mapper.map(policy, response, retrievedAtUtc);
                    } catch (RuntimeException error) {
                        candidate = ArchiveTraversalStateMachine.Candidate.failure(
                                ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                                "DECODE_EXCEPTION:" + safe(error));
                    }

                    int acceptedBefore = session.acceptedPeriodCount();
                    session.accept(candidate);
                    if (acceptedPeriodSink != null
                            && session.acceptedPeriodCount() > acceptedBefore) {
                        ArchiveTraversalStateMachine.PeriodEvidence accepted =
                                session.lastAcceptedPeriod();
                        try {
                            if (accepted == null || accepted.period == null) {
                                throw new IllegalStateException("accepted period missing");
                            }
                            acceptedPeriodSink.persist(accepted.period);
                            persistedAccepted++;
                        } catch (RuntimeException error) {
                            persistenceDiagnostic = safe(error);
                            if (!session.stopped()) {
                                session.abort(ArchiveFamilySyncState.StopReason.PERSISTENCE_ERROR,
                                        persistenceDiagnostic);
                            }
                            break;
                        }
                    }
                } catch (IOException error) {
                    session.accept(ArchiveTraversalStateMachine.Candidate.failure(
                            ArchiveFamilySyncState.StopReason.IO_ERROR, safe(error)));
                }
            }
        } catch (IOException error) {
            diagnostic = safe(error);
            if (!session.stopped()) {
                session.abort(ArchiveFamilySyncState.StopReason.IO_ERROR, diagnostic);
            }
        } finally {
            if (restoreNeeded) {
                finalVerification = restoreAndVerify(
                        wire, verifier, expectedMeterId, preflight.verified ? preflight : null);
            }
        }

        return new Result(
                policy.family,
                mode,
                session.result(),
                controls,
                preflight,
                finalVerification,
                boundary,
                selectAttempted,
                boundaryDeadline,
                watchdogDeadline,
                diagnostic,
                acceptedPeriodSink != null,
                persistedAccepted,
                persistenceDiagnostic,
                archivePrepareAttempted,
                archivePrepareSucceeded,
                archivePrepareDiagnostic);
    }

    static ArchiveTraversalStateMachine.Candidate mapProductionResponse(
            ArchiveFamilyPolicy policy,
            byte[] response,
            String retrievedAtUtc) {
        if (response == null) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE, "NO_HOST_RESPONSE");
        }
        if (isExactProtocolTerminal(response)) {
            return ArchiveTraversalStateMachine.Candidate.terminal(safeShortHex(response));
        }
        if (policy.family != ArchiveFamilyPeriod.Family.MONTH) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                    "production response mapper not enabled for " + policy.family);
        }

        MonthlyArchiveResponse.Observation observation = MonthlyArchiveResponse.inspect(response);
        if (!MonthlyArchiveResponse.healthyMonthlyFrame(observation)) {
            String reason = safeShortHex(response);
            if (reason == null) {
                reason = observation == null || observation.parseError == null
                        ? "MONTH_ARCHIVE_RESPONSE_INVALID"
                        : observation.parseError;
            }
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.PARSER_ERROR, reason);
        }

        MeterTimeEvidence time = MeterTimeEvidence.inspect(response);
        if (time.typeFPresent && time.decodedRawWallClock != null
                && !observation.loggerTimestamp.equals(time.decodedRawWallClock)) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.TYPE_F_INVALID,
                    "logger Type-F mismatch: " + observation.loggerTimestamp
                            + " != " + time.decodedRawWallClock);
        }

        ArchiveRecordInspector.Inspection inspection =
                ArchiveRecordInspector.inspectSelectedResponse(response);
        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                ArchivePeriodSnapshot.PeriodType.MONTH, inspection, retrievedAtUtc);
        MonthlyArchivePeriod monthly = new MonthlyArchivePeriod(
                observation.loggerTimestamp,
                retrievedAtUtc,
                observation.structuralFingerprint,
                snapshot);
        ArchiveFamilyPeriod period = ArchiveFamilyPeriod.fromMonthly(monthly);
        long onTime = time.onTimeSeconds == null ? -1L : time.onTimeSeconds;
        boolean typeFInvalid = time.invalidTime
                || time.reservedMinuteBit6Set
                || time.reservedHourBitsSet;
        return ArchiveTraversalStateMachine.Candidate.period(
                new ArchiveTraversalStateMachine.PeriodEvidence(
                        period,
                        onTime,
                        time.typeFPresent,
                        typeFInvalid));
    }

    static byte[] requestForControl(String control) {
        if (ArchiveTraversalStateMachine.CONTROL_FCB1_7B.equals(control)) {
            return MbusFrameSupport.reqUd2Fcb1();
        }
        if (ArchiveTraversalStateMachine.CONTROL_FCB0_5B.equals(control)) {
            return MbusFrameSupport.reqUd2Fcb0();
        }
        throw new IllegalArgumentException("unsupported archive control " + control);
    }

    private static SafetyVerification restoreAndVerify(
            Wire wire,
            DefaultVerifier verifier,
            String expectedMeterId,
            SafetyVerification initial) {
        SafetyVerification result = new SafetyVerification();

        // Frozen physical Research deliberately gave the selected archive application one full
        // second to settle before issuing Application Reset Default. Keep that exact ordering,
        // then retain the product's post-reset stabilization before the protected Live verify.
        wire.coolDown(RESTORE_PRE_RESET_COOLDOWN_MS);
        boolean resetAccepted = issueApplicationReset(
                wire, result, "ARCHIVE_SYNC_FINAL_RESET_DEFAULT");
        if (!resetAccepted) return result;

        wire.coolDown(STABILIZATION_MS);
        verifyDefault(verifier, result, expectedMeterId, initial);

        // Preserve the existing single structural reset retry only for a healthy transport whose
        // following default response is structurally unverified. Never retry a meter mismatch,
        // missing reset host response, or time-integrity failure as a mere structural miss.
        if (!result.verified
                && result.failureReason == ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED
                && wire.transportHealthy()) {
            wire.coolDown(RESTORE_PRE_RESET_COOLDOWN_MS);
            resetAccepted = issueApplicationReset(
                    wire, result, "ARCHIVE_SYNC_FINAL_RESET_DEFAULT_RETRY");
            if (!resetAccepted) return result;
            wire.coolDown(STABILIZATION_MS);
            verifyDefault(verifier, result, expectedMeterId, initial);
        }
        return result;
    }

    private static void verifyDefault(
            DefaultVerifier verifier,
            SafetyVerification result,
            String expectedMeterId,
            SafetyVerification initial) {
        result.liveReadAttempts++;
        try {
            DefaultReadObservation observation = verifier.readDefault();
            result.observation = observation;
            if (observation == null) {
                result.verified = false;
                result.failureReason = ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED;
                result.diagnostic = "default Live observation missing";
                return;
            }
            // Frozen Research treats the first healthy default structure as session evidence,
            // not as a global firmware allow-list. Final verification must match that exact
            // preflight structure when an initial anchor exists.
            String expectedFingerprint = initial == null ? null : initial.structuralFingerprint();
            ArchiveFamilySyncState.StopReason failure = observation.familySafetyFailure(
                    expectedMeterId, expectedFingerprint);
            if (failure != ArchiveFamilySyncState.StopReason.NONE) {
                result.verified = false;
                result.failureReason = failure;
                result.diagnostic = "default Live verification failed: " + failure.name();
                return;
            }
            if (initial != null && initial.observation != null) {
                Long initialOnTime = initial.onTimeSeconds();
                Long finalOnTime = result.onTimeSeconds();
                if (initialOnTime == null || finalOnTime == null || finalOnTime < initialOnTime) {
                    result.verified = false;
                    result.failureReason = ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT;
                    result.diagnostic = "final Live ON_TIME regressed";
                    return;
                }
                String initialRaw = initial.rawMeterTime();
                String finalRaw = result.rawMeterTime();
                if (initialRaw == null || finalRaw == null || finalRaw.compareTo(initialRaw) < 0) {
                    result.verified = false;
                    result.failureReason = ArchiveFamilySyncState.StopReason.PROGRESSION_INCONSISTENT;
                    result.diagnostic = "final raw meter time moved backwards";
                    return;
                }
            }
            result.verified = true;
            result.failureReason = ArchiveFamilySyncState.StopReason.NONE;
            result.diagnostic = null;
        } catch (IOException error) {
            result.verified = false;
            result.failureReason = ArchiveFamilySyncState.StopReason.IO_ERROR;
            result.diagnostic = safe(error);
        }
    }

    private static boolean issueApplicationReset(
            Wire wire,
            SafetyVerification result,
            String label) {
        result.applicationResetCommands++;
        try {
            byte[] response = wire.exchange(
                    label, MbusFrameSupport.applicationResetDefault(), RESET_TIMEOUT_MS);
            if (response == null) {
                result.verified = false;
                result.failureReason = ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE;
                result.diagnostic = "application reset returned no host response";
                return false;
            }
            return true;
        } catch (IOException error) {
            result.verified = false;
            result.failureReason = ArchiveFamilySyncState.StopReason.IO_ERROR;
            result.diagnostic = safe(error);
            return false;
        }
    }

    private static boolean canIssueSelectedRequest(long now, long boundaryDeadline) {
        if (boundaryDeadline <= 0L) return false;
        return safeAdd(now, BOUNDARY_GUARD_BUDGET_MS) < boundaryDeadline;
    }

    private static boolean isExactProtocolTerminal(byte[] response) {
        return (response.length == 1 && (response[0] & 0xFF) == 0xE5)
                || (response.length == 2
                && (response[0] & 0xFF) == 0xE5
                && (response[1] & 0xFF) == 0x7D);
    }

    private static String safeShortHex(byte[] response) {
        return MonthlyArchiveResponse.safeShortHex(response);
    }

    private static String hex2(int value) {
        return String.format(java.util.Locale.US, "%02X", value & 0xFF);
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static String safe(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private ArchiveFamilyTransportAdapter() {}
}
