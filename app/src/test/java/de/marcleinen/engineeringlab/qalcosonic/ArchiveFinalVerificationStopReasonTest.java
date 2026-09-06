package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

public final class ArchiveFinalVerificationStopReasonTest {
    @Test public void finalLiveFailureOverridesEarlierProtocolTerminal() {
        ArchiveTraversalStateMachine.Result traversal = new ArchiveTraversalStateMachine.Result(
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                Collections.emptyList(),
                1,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                "E5 7D",
                0);
        ArchiveFamilyTransportAdapter.SafetyVerification preflight =
                new ArchiveFamilyTransportAdapter.SafetyVerification();
        preflight.verified = true;
        ArchiveFamilyTransportAdapter.SafetyVerification finalVerification =
                new ArchiveFamilyTransportAdapter.SafetyVerification();
        finalVerification.verified = false;
        finalVerification.failureReason = ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT;

        ArchiveFamilyTransportAdapter.Result result = new ArchiveFamilyTransportAdapter.Result(
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                traversal,
                Collections.emptyList(),
                preflight,
                finalVerification,
                null,
                true,
                0L,
                0L,
                null,
                false,
                0,
                null);

        assertEquals(ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT,
                result.effectiveStopReason());
    }

    @Test public void protectiveTraversalFailureRemainsPrimary() {
        ArchiveTraversalStateMachine.Result traversal = new ArchiveTraversalStateMachine.Result(
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                Collections.emptyList(),
                1,
                ArchiveFamilySyncState.StopReason.IO_ERROR,
                "TagLost",
                0);
        ArchiveFamilyTransportAdapter.SafetyVerification finalVerification =
                new ArchiveFamilyTransportAdapter.SafetyVerification();
        finalVerification.verified = false;
        finalVerification.failureReason = ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED;

        ArchiveFamilyTransportAdapter.Result result = new ArchiveFamilyTransportAdapter.Result(
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                traversal,
                Collections.emptyList(),
                new ArchiveFamilyTransportAdapter.SafetyVerification(),
                finalVerification,
                null,
                true,
                0L,
                0L,
                null,
                false,
                0,
                null);

        assertEquals(ArchiveFamilySyncState.StopReason.IO_ERROR,
                result.effectiveStopReason());
    }
}
