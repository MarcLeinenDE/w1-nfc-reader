package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class HistorySyncFlowTest {
    @Test
    public void liveReadOnlyOffersHistoryAndNeverAutoArms() {
        HistorySyncFlow flow = new HistorySyncFlow();

        flow.onLiveReadSucceeded("12345678");

        assertEquals(HistorySyncFlow.State.OFFERED_AFTER_LIVE_READ, flow.state());
        assertEquals("12345678", flow.targetMeterId());
        assertTrue(flow.canRequestFullSync());
        assertEquals(
                HistorySyncFlow.MeterPresentationResult.IGNORED_NOT_ARMED,
                flow.onMeterPresented("12345678"));
        assertEquals(HistorySyncFlow.State.OFFERED_AFTER_LIVE_READ, flow.state());
    }

    @Test
    public void explicitRequestArmsAndWrongMeterCannotStartSync() {
        HistorySyncFlow flow = offeredFlow();
        flow.requestFullSync();

        assertEquals(HistorySyncFlow.State.ARMED_WAITING_FOR_METER, flow.state());
        assertFalse(flow.canRequestFullSync());
        assertEquals(
                HistorySyncFlow.MeterPresentationResult.WRONG_METER,
                flow.onMeterPresented("87654321"));
        assertEquals(HistorySyncFlow.State.ARMED_WAITING_FOR_METER, flow.state());

        assertEquals(
                HistorySyncFlow.MeterPresentationResult.STARTED,
                flow.onMeterPresented(" 12345678 "));
        assertEquals(HistorySyncFlow.State.RUNNING, flow.state());
    }

    @Test
    public void completedSyncCanBeExplicitlyFullyResyncedAgain() {
        HistorySyncFlow flow = runningFlow();
        flow.onSyncCompleted();

        assertEquals(HistorySyncFlow.State.COMPLETED, flow.state());
        assertTrue(flow.canRequestFullSync());

        flow.requestFullSync();
        assertEquals(HistorySyncFlow.State.ARMED_WAITING_FOR_METER, flow.state());
    }

    @Test
    public void partialSuccessKeepsTargetAndAllowsAnotherFullResync() {
        HistorySyncFlow flow = runningFlow();
        flow.onSyncPartialSuccess();

        assertEquals(HistorySyncFlow.State.PARTIAL_SUCCESS, flow.state());
        assertEquals("12345678", flow.targetMeterId());
        assertTrue(flow.canRequestFullSync());
    }

    @Test
    public void syncFailureDoesNotEraseSuccessfulLiveTarget() {
        HistorySyncFlow flow = runningFlow();
        flow.onSyncFailed();

        assertEquals(HistorySyncFlow.State.FAILED, flow.state());
        assertEquals("12345678", flow.targetMeterId());
        assertTrue(flow.canRequestFullSync());
    }

    @Test
    public void cancelOnlyDisarmsAndLeavesHistoryOfferAvailable() {
        HistorySyncFlow flow = offeredFlow();
        flow.requestFullSync();
        flow.cancelArmedSync();

        assertEquals(HistorySyncFlow.State.OFFERED_AFTER_LIVE_READ, flow.state());
        assertTrue(flow.canRequestFullSync());
    }

    @Test
    public void historyCannotBeRequestedBeforeSuccessfulLiveRead() {
        HistorySyncFlow flow = new HistorySyncFlow();

        assertFalse(flow.canRequestFullSync());
        assertThrows(IllegalStateException.class, flow::requestFullSync);
        assertThrows(IllegalArgumentException.class, () -> flow.onLiveReadSucceeded("  "));
    }

    @Test
    public void terminalCallbacksRequireRunningSync() {
        HistorySyncFlow flow = offeredFlow();

        assertThrows(IllegalStateException.class, flow::onSyncCompleted);
        assertThrows(IllegalStateException.class, flow::onSyncPartialSuccess);
        assertThrows(IllegalStateException.class, flow::onSyncFailed);
    }

    private static HistorySyncFlow offeredFlow() {
        HistorySyncFlow flow = new HistorySyncFlow();
        flow.onLiveReadSucceeded("12345678");
        return flow;
    }

    private static HistorySyncFlow runningFlow() {
        HistorySyncFlow flow = offeredFlow();
        flow.requestFullSync();
        assertEquals(
                HistorySyncFlow.MeterPresentationResult.STARTED,
                flow.onMeterPresented("12345678"));
        return flow;
    }
}
