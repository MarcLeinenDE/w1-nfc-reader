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
        assertEquals(ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                HistorySyncAllPlanner.modeFor(completeButLastAttemptFailed, false));
        assertEquals(ArchiveFamilySyncState.SyncMode.FULL_RESYNC,
                HistorySyncAllPlanner.modeFor(completeButLastAttemptFailed, true));
    }

    @Test public void fullResyncAvailabilityRequiresAllThreeCompleteBaselines() {
        ArchiveFamilySyncState hour = complete(ArchiveFamilyPeriod.Family.HOUR);
        ArchiveFamilySyncState day = complete(ArchiveFamilyPeriod.Family.DAY);
        ArchiveFamilySyncState month = complete(ArchiveFamilyPeriod.Family.MONTH);

        assertTrue(HistorySyncAllPlanner.fullResyncAvailable(hour, day, month));
        assertFalse(HistorySyncAllPlanner.fullResyncAvailable(
                ArchiveFamilySyncState.empty(ArchiveFamilyPeriod.Family.HOUR), day, month));
        assertFalse(HistorySyncAllPlanner.fullResyncAvailable(hour, null, month));
    }

    @Test public void chainingStopsWhenDefaultRestoreIsUnverified() {
        assertTrue(HistorySyncAllPlanner.mayContinue(true, true));
        assertTrue(HistorySyncAllPlanner.mayContinue(false, true));
        assertFalse(HistorySyncAllPlanner.mayContinue(false, false));
    }

    private static ArchiveFamilySyncState complete(ArchiveFamilyPeriod.Family family) {
        return new ArchiveFamilySyncState(
                family,
                ArchiveFamilySyncState.BaselineState.COMPLETE,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                20L,
                20L,
                10L,
                "2026-01-01 00:00",
                "2026-09-01 00:00",
                "2026-09-01 00:00",
                20,
                20,
                0,
                0,
                true,
                true);
    }
}
