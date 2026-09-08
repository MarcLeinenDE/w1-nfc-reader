package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistorySyncAllPlannerTest {
    @Test public void incompleteFamilyUsesInitialFullMode() {
        assertEquals(ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                HistorySyncAllPlanner.modeFor(ArchiveFamilySyncState.empty(
                        ArchiveFamilyPeriod.Family.HOUR)));
    }

    @Test public void completeBaselineUsesIncrementalEvenAfterLaterFailedUpdate() {
        ArchiveFamilySyncState completeButLastAttemptFailed = new ArchiveFamilySyncState(
                ArchiveFamilyPeriod.Family.DAY,
                ArchiveFamilySyncState.BaselineState.COMPLETE,
                ArchiveFamilySyncState.AttemptOutcome.FAILED,
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                ArchiveFamilySyncState.StopReason.IO_ERROR,
                20L,
                10L,
                5L,
                "2026-08-01 00:00",
                "2026-09-01 00:00",
                "2026-09-01 00:00",
                0,
                0,
                0,
                0,
                false,
                true);

        assertEquals(ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                HistorySyncAllPlanner.modeFor(completeButLastAttemptFailed));
    }

    @Test public void chainingStopsWhenDefaultRestoreIsUnverified() {
        assertTrue(HistorySyncAllPlanner.mayContinue(true, true));
        assertTrue(HistorySyncAllPlanner.mayContinue(false, true));
        assertFalse(HistorySyncAllPlanner.mayContinue(false, false));
    }
}
