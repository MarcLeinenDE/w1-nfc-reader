package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure family-neutral archive traversal state machine.
 *
 * <p>No NFC, persistence or wall-clock scheduling lives here. The caller issues exactly the
 * control returned by {@link Session#nextControl()} and feeds exactly one resulting candidate to
 * {@link Session#accept(Candidate)}. There is deliberately no record-count completion cap.</p>
 */
final class ArchiveTraversalStateMachine {
    static final String CONTROL_FCB1_7B = "7B";
    static final String CONTROL_FCB0_5B = "5B";
    private static final int MIN_BACKWARD_RECORDS_BEFORE_RING = 2;

    interface KnownRecordMatcher {
        /**
         * Return true only when the candidate is securely known locally, including whatever
         * content/ON_TIME/provenance checks the caller requires. Timestamp-only matching is not
         * sufficient for product incremental synchronization.
         */
        boolean securelyKnown(PeriodEvidence evidence);
    }

    static final class PeriodEvidence {
        final ArchiveFamilyPeriod period;
        final long onTimeSeconds;
        final boolean typeFPresent;
        final boolean typeFInvalid;

        PeriodEvidence(
                ArchiveFamilyPeriod period,
                long onTimeSeconds,
                boolean typeFPresent,
                boolean typeFInvalid) {
            this.period = period;
            this.onTimeSeconds = onTimeSeconds;
            this.typeFPresent = typeFPresent;
            this.typeFInvalid = typeFInvalid;
        }

        String rawTimestamp() {
            return period == null ? null : period.loggerTimestamp;
        }

        String structuralFingerprint() {
            return period == null ? null : period.structuralFingerprint;
        }
    }

    enum CandidateKind {
        PERIOD,
        PROTOCOL_TERMINAL,
        FAILURE
    }

    static final class Candidate {
        final CandidateKind kind;
        final PeriodEvidence evidence;
        final ArchiveFamilySyncState.StopReason failureReason;
        final String diagnostic;

        private Candidate(
                CandidateKind kind,
                PeriodEvidence evidence,
                ArchiveFamilySyncState.StopReason failureReason,
                String diagnostic) {
            this.kind = kind;
            this.evidence = evidence;
            this.failureReason = failureReason;
            this.diagnostic = diagnostic;
        }

        static Candidate period(PeriodEvidence evidence) {
            return new Candidate(CandidateKind.PERIOD, evidence,
                    ArchiveFamilySyncState.StopReason.NONE, null);
        }

        static Candidate terminal(String diagnostic) {
            return new Candidate(CandidateKind.PROTOCOL_TERMINAL, null,
                    ArchiveFamilySyncState.StopReason.NONE, diagnostic);
        }

        static Candidate failure(ArchiveFamilySyncState.StopReason reason, String diagnostic) {
            requireProtectiveReason(reason);
            return new Candidate(CandidateKind.FAILURE, null, reason, diagnostic);
        }

        private static void requireProtectiveReason(ArchiveFamilySyncState.StopReason reason) {
            if (reason == null || reason == ArchiveFamilySyncState.StopReason.NONE
                    || ArchiveFamilySyncState.successfulStopForMode(
                    ArchiveFamilySyncState.SyncMode.INCREMENTAL, reason)) {
                throw new IllegalArgumentException("protective/error stop reason required");
            }
        }
    }

    static final class Session {
        private final ArchiveFamilyPolicy policy;
        private final ArchiveFamilySyncState.SyncMode mode;
        private final KnownRecordMatcher knownMatcher;
        private final int requiredKnownOverlap;
        private final List<PeriodEvidence> accepted = new ArrayList<>();
        private final Set<String> acceptedStableRawTimestamps = new HashSet<>();

        private PeriodEvidence initialHead;
        private PeriodEvidence previous;
        private String anchorFingerprint;
        private PeriodEvidence ringHeadCandidate;
        private int selectedRequests;
        private int consecutiveKnownOverlap;
        private ArchiveFamilySyncState.StopReason stopReason = ArchiveFamilySyncState.StopReason.NONE;
        private String diagnostic;

        Session(
                ArchiveFamilyPolicy policy,
                ArchiveFamilySyncState.SyncMode mode,
                KnownRecordMatcher knownMatcher,
                int requiredKnownOverlap) {
            this.policy = Objects.requireNonNull(policy, "policy");
            this.mode = Objects.requireNonNull(mode, "mode");
            if (mode == ArchiveFamilySyncState.SyncMode.INCREMENTAL) {
                if (knownMatcher == null) throw new IllegalArgumentException("incremental matcher required");
                if (requiredKnownOverlap < 2) {
                    throw new IllegalArgumentException("incremental known overlap must be >= 2");
                }
                this.knownMatcher = knownMatcher;
                this.requiredKnownOverlap = requiredKnownOverlap;
            } else {
                if (requiredKnownOverlap != 0) {
                    throw new IllegalArgumentException("full traversal overlap must be 0");
                }
                this.knownMatcher = knownMatcher;
                this.requiredKnownOverlap = 0;
            }
        }

        String nextControl() {
            if (stopped()) return null;
            return (selectedRequests % 2 == 0) ? CONTROL_FCB1_7B : CONTROL_FCB0_5B;
        }

        void accept(Candidate candidate) {
            if (stopped()) throw new IllegalStateException("session already stopped: " + stopReason);
            selectedRequests++;
            if (candidate == null) {
                stop(ArchiveFamilySyncState.StopReason.UNKNOWN_ERROR, "candidate missing");
                return;
            }
            switch (candidate.kind) {
                case PROTOCOL_TERMINAL:
                    stop(ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL, candidate.diagnostic);
                    return;
                case FAILURE:
                    stop(candidate.failureReason, candidate.diagnostic);
                    return;
                case PERIOD:
                    acceptPeriod(candidate.evidence);
                    return;
                default:
                    stop(ArchiveFamilySyncState.StopReason.UNKNOWN_ERROR, "unhandled candidate kind");
            }
        }

        /** Protective stop before another selected request is issued. Does not increment request count. */
        void abort(ArchiveFamilySyncState.StopReason reason, String diagnostic) {
            if (stopped()) return;
            Candidate.requireProtectiveReason(reason);
            stop(reason, diagnostic);
        }

        boolean stopped() {
            return stopReason != ArchiveFamilySyncState.StopReason.NONE;
        }

        ArchiveFamilySyncState.StopReason stopReason() {
            return stopReason;
        }

        String diagnostic() {
            return diagnostic;
        }

        int selectedRequestsAttempted() {
            return selectedRequests;
        }

        int consecutiveKnownOverlap() {
            return consecutiveKnownOverlap;
        }

        /** O(1) cursor for transport-side delivery of newly accepted records. */
        int acceptedPeriodCount() {
            return accepted.size();
        }

        /** Returns the most recently accepted record without copying the accepted history. */
        PeriodEvidence lastAcceptedPeriod() {
            return accepted.isEmpty() ? null : accepted.get(accepted.size() - 1);
        }

        List<PeriodEvidence> acceptedPeriods() {
            return Collections.unmodifiableList(new ArrayList<>(accepted));
        }

        Result result() {
            return new Result(
                    policy.family,
                    mode,
                    acceptedPeriods(),
                    selectedRequests,
                    stopReason,
                    diagnostic,
                    consecutiveKnownOverlap);
        }

        private void acceptPeriod(PeriodEvidence evidence) {
            ArchiveFamilySyncState.StopReason integrityFailure = validateIntegrity(evidence);
            if (integrityFailure != ArchiveFamilySyncState.StopReason.NONE) {
                stop(integrityFailure, evidence == null ? "period evidence missing" : evidence.rawTimestamp());
                return;
            }

            if (ringHeadCandidate != null) {
                // A ring candidate is not successful until one additional selected request resumes
                // the expected backward progression from that exact returned session head.
                if (policy.isExpectedPreviousIdentity(
                        ringHeadCandidate.rawTimestamp(), ringHeadCandidate.onTimeSeconds,
                        evidence.rawTimestamp(), evidence.onTimeSeconds)) {
                    stop(ArchiveFamilySyncState.StopReason.RING_WRAP_DETECTED,
                            "ring continuation confirmed at request " + selectedRequests);
                } else {
                    stop(ArchiveFamilySyncState.StopReason.PROGRESSION_INCONSISTENT,
                            "ring continuation mismatch: " + ringHeadCandidate.rawTimestamp()
                                    + " -> " + evidence.rawTimestamp());
                }
                return;
            }

            if (initialHead == null) {
                initialHead = evidence;
                previous = evidence;
                anchorFingerprint = evidence.structuralFingerprint();
                addUniqueAccepted(evidence);
                updateKnownOverlap(evidence);
                return;
            }

            if (!anchorFingerprint.equals(evidence.structuralFingerprint())) {
                stop(ArchiveFamilySyncState.StopReason.STRUCTURE_ERROR,
                        "archive structure changed at " + evidence.rawTimestamp());
                return;
            }

            if (policy.isExpectedPreviousIdentity(
                    previous.rawTimestamp(), previous.onTimeSeconds,
                    evidence.rawTimestamp(), evidence.onTimeSeconds)) {
                previous = evidence;
                addUniqueAccepted(evidence);
                updateKnownOverlap(evidence);
                return;
            }

            if (isHeadAdvancedPlusOne(evidence)) {
                stop(ArchiveFamilySyncState.StopReason.HEAD_ADVANCED_DURING_TRAVERSAL,
                        "new archive head observed at " + evidence.rawTimestamp());
                return;
            }

            if (accepted.size() >= MIN_BACKWARD_RECORDS_BEFORE_RING && sameIdentity(initialHead, evidence)) {
                ringHeadCandidate = evidence;
                previous = evidence;
                consecutiveKnownOverlap = 0;
                return;
            }

            stop(ArchiveFamilySyncState.StopReason.PROGRESSION_INCONSISTENT,
                    "unexpected progression: " + previous.rawTimestamp() + " -> " + evidence.rawTimestamp());
        }

        private ArchiveFamilySyncState.StopReason validateIntegrity(PeriodEvidence evidence) {
            if (evidence == null || evidence.period == null) {
                return ArchiveFamilySyncState.StopReason.PARSER_ERROR;
            }
            if (evidence.period.family != policy.family) {
                return ArchiveFamilySyncState.StopReason.PARSER_ERROR;
            }
            if (evidence.rawTimestamp() == null || evidence.rawTimestamp().trim().isEmpty()) {
                return ArchiveFamilySyncState.StopReason.PARSER_ERROR;
            }
            if (evidence.structuralFingerprint() == null
                    || evidence.structuralFingerprint().trim().isEmpty()) {
                return ArchiveFamilySyncState.StopReason.STRUCTURE_ERROR;
            }
            if (!evidence.typeFPresent) return ArchiveFamilySyncState.StopReason.TYPE_F_MISSING;
            if (evidence.typeFInvalid) return ArchiveFamilySyncState.StopReason.TYPE_F_INVALID;
            if (evidence.onTimeSeconds < 0L) return ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT;
            return ArchiveFamilySyncState.StopReason.NONE;
        }

        private void addUniqueAccepted(PeriodEvidence evidence) {
            if (!acceptedStableRawTimestamps.add(evidence.rawTimestamp())) {
                stop(ArchiveFamilySyncState.StopReason.PROGRESSION_INCONSISTENT,
                        "duplicate period before confirmed ring: " + evidence.rawTimestamp());
                return;
            }
            accepted.add(evidence);
        }

        private void updateKnownOverlap(PeriodEvidence evidence) {
            if (stopped() || mode != ArchiveFamilySyncState.SyncMode.INCREMENTAL) return;
            if (knownMatcher.securelyKnown(evidence)) consecutiveKnownOverlap++;
            else consecutiveKnownOverlap = 0;
            if (consecutiveKnownOverlap >= requiredKnownOverlap) {
                stop(ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED,
                        "secure known overlap=" + consecutiveKnownOverlap);
            }
        }

        private boolean isHeadAdvancedPlusOne(PeriodEvidence candidate) {
            String expectedNext = policy.expectedNextRawTimestamp(initialHead.rawTimestamp());
            if (expectedNext == null || !expectedNext.equals(candidate.rawTimestamp())) return false;
            String reverseExpected = policy.expectedPreviousRawTimestamp(candidate.rawTimestamp());
            if (reverseExpected == null || !reverseExpected.equals(initialHead.rawTimestamp())) return false;

            // Use the same family-native raw-period relation to derive the expected ON_TIME delta.
            return policy.isExpectedPreviousIdentity(
                    candidate.rawTimestamp(), candidate.onTimeSeconds,
                    initialHead.rawTimestamp(), initialHead.onTimeSeconds);
        }

        private static boolean sameIdentity(PeriodEvidence first, PeriodEvidence second) {
            return first != null && second != null
                    && first.rawTimestamp().equals(second.rawTimestamp())
                    && first.onTimeSeconds == second.onTimeSeconds
                    && first.structuralFingerprint().equals(second.structuralFingerprint());
        }

        private void stop(ArchiveFamilySyncState.StopReason reason, String diagnostic) {
            stopReason = Objects.requireNonNull(reason, "reason");
            this.diagnostic = diagnostic;
        }
    }

    static final class Result {
        final ArchiveFamilyPeriod.Family family;
        final ArchiveFamilySyncState.SyncMode mode;
        final List<PeriodEvidence> periods;
        final int selectedRequestsAttempted;
        final ArchiveFamilySyncState.StopReason stopReason;
        final String diagnostic;
        final int consecutiveKnownOverlap;

        Result(
                ArchiveFamilyPeriod.Family family,
                ArchiveFamilySyncState.SyncMode mode,
                List<PeriodEvidence> periods,
                int selectedRequestsAttempted,
                ArchiveFamilySyncState.StopReason stopReason,
                String diagnostic,
                int consecutiveKnownOverlap) {
            this.family = family;
            this.mode = mode;
            this.periods = periods;
            this.selectedRequestsAttempted = selectedRequestsAttempted;
            this.stopReason = stopReason;
            this.diagnostic = diagnostic;
            this.consecutiveKnownOverlap = consecutiveKnownOverlap;
        }

        boolean semanticSuccess() {
            return ArchiveFamilySyncState.successfulStopForMode(mode, stopReason);
        }

        boolean partialSuccess() {
            return !periods.isEmpty() && !semanticSuccess();
        }
    }

    private ArchiveTraversalStateMachine() {}
}
