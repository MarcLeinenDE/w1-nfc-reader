package de.marcleinen.engineeringlab.qalcosonic;

/**
 * Production mapping for the physically researched Day/Hour families.
 *
 * <p>The Month path intentionally remains on {@link ArchiveFamilyTransportAdapter#runMonth} so the
 * exact real-device validated Month candidate is not rewritten while Day/Hour are brought through
 * their own physical gates.</p>
 */
final class ArchiveFamilyProductionRunner {
    private static final String SOURCE_NFC_ARCHIVE = "NFC_ARCHIVE";
    private static final String VALIDATION_COMPLETE = "COMPLETE";

    private ArchiveFamilyProductionRunner() {}

    static ArchiveFamilyTransportAdapter.Result runDay(
            ArchiveFamilyTransportAdapter.Wire wire,
            ArchiveFamilyTransportAdapter.DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            ArchiveFamilyTransportAdapter.AcceptedPeriodSink acceptedPeriodSink) {
        return runFamily(
                ArchiveFamilyPeriod.Family.DAY,
                wire,
                verifier,
                expectedMeterId,
                retrievedAtUtc,
                mode,
                acceptedPeriodSink);
    }

    static ArchiveFamilyTransportAdapter.Result runHour(
            ArchiveFamilyTransportAdapter.Wire wire,
            ArchiveFamilyTransportAdapter.DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            ArchiveFamilyTransportAdapter.AcceptedPeriodSink acceptedPeriodSink) {
        return runFamily(
                ArchiveFamilyPeriod.Family.HOUR,
                wire,
                verifier,
                expectedMeterId,
                retrievedAtUtc,
                mode,
                acceptedPeriodSink);
    }

    private static ArchiveFamilyTransportAdapter.Result runFamily(
            ArchiveFamilyPeriod.Family family,
            ArchiveFamilyTransportAdapter.Wire wire,
            ArchiveFamilyTransportAdapter.DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveFamilySyncState.SyncMode mode,
            ArchiveFamilyTransportAdapter.AcceptedPeriodSink acceptedPeriodSink) {
        if (family != ArchiveFamilyPeriod.Family.DAY && family != ArchiveFamilyPeriod.Family.HOUR) {
            throw new IllegalArgumentException("Day/Hour production runner only: " + family);
        }
        return ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(family),
                wire,
                verifier,
                expectedMeterId,
                retrievedAtUtc,
                mode,
                null,
                0,
                ArchiveFamilyProductionRunner::mapResponse,
                acceptedPeriodSink);
    }

    static ArchiveTraversalStateMachine.Candidate mapResponse(
            ArchiveFamilyPolicy policy,
            byte[] response,
            String retrievedAtUtc) {
        if (policy == null) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                    "archive family policy missing");
        }
        if (policy.family != ArchiveFamilyPeriod.Family.DAY
                && policy.family != ArchiveFamilyPeriod.Family.HOUR) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                    "Day/Hour response mapper not enabled for " + policy.family);
        }
        if (response == null) {
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE,
                    "NO_HOST_RESPONSE");
        }
        if (ArchiveFamilyResponse.isExactProtocolTerminal(response)) {
            return ArchiveTraversalStateMachine.Candidate.terminal(
                    ArchiveFamilyResponse.safeShortHex(response));
        }

        ArchiveFamilyResponse.Observation observation = ArchiveFamilyResponse.inspect(response);
        if (!ArchiveFamilyResponse.healthyFrame(observation)) {
            String reason = ArchiveFamilyResponse.safeShortHex(response);
            if (reason == null) {
                reason = observation == null || observation.parseError == null
                        ? policy.family.name() + "_ARCHIVE_RESPONSE_INVALID"
                        : observation.parseError;
            }
            return ArchiveTraversalStateMachine.Candidate.failure(
                    ArchiveFamilySyncState.StopReason.PARSER_ERROR,
                    reason);
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
        ArchivePeriodSnapshot.PeriodType periodType = policy.family == ArchiveFamilyPeriod.Family.DAY
                ? ArchivePeriodSnapshot.PeriodType.DAY
                : ArchivePeriodSnapshot.PeriodType.HOUR;
        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                periodType,
                inspection,
                retrievedAtUtc);
        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                policy.family,
                observation.loggerTimestamp,
                retrievedAtUtc,
                observation.structuralFingerprint,
                SOURCE_NFC_ARCHIVE,
                VALIDATION_COMPLETE,
                ArchiveNormalizedValues.fromSnapshot(snapshot),
                time);

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
}
