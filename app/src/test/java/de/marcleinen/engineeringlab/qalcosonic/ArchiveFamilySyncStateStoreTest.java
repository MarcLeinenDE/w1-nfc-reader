package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveFamilySyncStateStoreTest {
    private Context context;
    private ArchiveFamilySyncStateStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(ArchiveFamilySyncStateStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new ArchiveFamilySyncStateStore(context);
    }

    @Test public void familiesHaveIndependentBaselineAndAttemptState() {
        ArchiveFamilySyncState hour = store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.PARTIAL,
                ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                true,
                "2026-08-01 10:00",
                "2026-09-06 09:00",
                100,
                100,
                0,
                0);

        assertEquals(ArchiveFamilySyncState.BaselineState.PARTIAL, hour.baselineState);
        assertEquals(ArchiveFamilySyncState.AttemptOutcome.PARTIAL, hour.lastAttemptOutcome);
        assertTrue(hour.recoveryRecommended);
        assertFalse(hour.baselineComplete());

        ArchiveFamilySyncState day = store.get("12345678", ArchiveFamilyPeriod.Family.DAY);
        assertEquals(ArchiveFamilySyncState.BaselineState.NEVER_SYNCED, day.baselineState);
        assertEquals(ArchiveFamilySyncState.AttemptOutcome.NONE, day.lastAttemptOutcome);
        assertFalse(day.hasAttempt());
    }

    @Test public void completeBaselineRequiresSemanticEndAndVerifiedFinalRestore() {
        assertThrows(IllegalArgumentException.class, () -> store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                false,
                "2024-04-01 00:00",
                "2026-09-01 00:00",
                30,
                30,
                0,
                0));

        assertThrows(IllegalArgumentException.class, () -> store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                true,
                "2024-04-01 00:00",
                "2026-09-01 00:00",
                30,
                30,
                0,
                0));

        ArchiveFamilySyncState complete = store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                2000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2024-04-01 00:00",
                "2026-09-01 00:00",
                30,
                30,
                0,
                0);

        assertTrue(complete.baselineComplete());
        assertEquals(2000L, complete.baselineCompletedAtMs);
        assertEquals(2000L, complete.lastSuccessMs);
        assertFalse(complete.recoveryRecommended);
    }

    @Test public void incrementalRequiresCompletedBaseline() {
        assertThrows(IllegalStateException.class, () -> store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED,
                true,
                null,
                "2026-09-06 09:00",
                1,
                1,
                0,
                0));
    }

    @Test public void laterIncrementalFailureDoesNotEraseCompletedBaseline() {
        ArchiveFamilySyncState initial = store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.DAY,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2024-02-16 00:00",
                "2026-09-06 00:00",
                932,
                932,
                0,
                0);

        ArchiveFamilySyncState later = store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.DAY,
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                2000L,
                ArchiveFamilySyncState.AttemptOutcome.PARTIAL,
                ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST,
                false,
                null,
                "2026-09-07 00:00",
                1,
                1,
                0,
                0);

        assertTrue(later.baselineComplete());
        assertEquals(initial.baselineCompletedAtMs, later.baselineCompletedAtMs);
        assertEquals(1000L, later.lastSuccessMs);
        assertEquals(2000L, later.lastAttemptMs);
        assertEquals(ArchiveFamilySyncState.AttemptOutcome.PARTIAL, later.lastAttemptOutcome);
        assertEquals(ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST, later.lastStopReason);
        assertEquals("2026-09-07 00:00", later.newestRawTimestamp);
        assertTrue(later.recoveryRecommended);
    }

    @Test public void successfulIncrementalStopsAtKnownRecordAndUpdatesAnchor() {
        store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2026-06-28 22:00",
                "2026-09-06 08:00",
                1600,
                1600,
                0,
                0);

        ArchiveFamilySyncState update = store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                2000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED,
                true,
                "2026-09-06 07:00",
                "2026-09-06 10:00",
                4,
                2,
                2,
                0);

        assertTrue(update.baselineComplete());
        assertEquals("2026-06-28 22:00", update.oldestRawTimestamp);
        assertEquals("2026-09-06 10:00", update.newestRawTimestamp);
        assertEquals("2026-09-06 10:00", update.incrementalAnchorRawTimestamp);
        assertEquals(2000L, update.lastSuccessMs);
        assertFalse(update.recoveryRecommended);
    }

    @Test public void exportRestoreRoundTripPreservesIndependentFamilyState() throws Exception {
        store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.RING_WRAP_DETECTED,
                true,
                "2024-04-01 00:00",
                "2026-09-01 00:00",
                30,
                20,
                10,
                1);
        store.recordAttempt(
                "12345678",
                ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                1100L,
                ArchiveFamilySyncState.AttemptOutcome.PARTIAL,
                ArchiveFamilySyncState.StopReason.WATCHDOG_REACHED,
                false,
                "2026-08-01 00:00",
                "2026-09-06 09:00",
                200,
                200,
                0,
                0);

        JSONObject exported = store.exportJson();
        assertEquals(ArchiveFamilySyncStateStore.SCHEMA_VERSION, exported.getInt("schema_version"));
        assertEquals(2, exported.getJSONArray("entries").length());

        store.clear();
        assertEquals(ArchiveFamilySyncState.BaselineState.NEVER_SYNCED,
                store.get("12345678", ArchiveFamilyPeriod.Family.MONTH).baselineState);

        store.restoreJson(exported);
        ArchiveFamilySyncState month = store.get("12345678", ArchiveFamilyPeriod.Family.MONTH);
        ArchiveFamilySyncState hour = store.get("12345678", ArchiveFamilyPeriod.Family.HOUR);
        assertTrue(month.baselineComplete());
        assertEquals(1, month.conflicts);
        assertEquals(ArchiveFamilySyncState.BaselineState.PARTIAL, hour.baselineState);
        assertEquals(ArchiveFamilySyncState.StopReason.WATCHDOG_REACHED, hour.lastStopReason);
    }

    @Test public void stopReasonContractContainsNoFixedRecordCapCompletion() {
        assertFalse(Arrays.stream(ArchiveFamilySyncState.StopReason.values())
                .anyMatch(value -> value.name().contains("HARD_CAP") || value.name().contains("RECORD_CAP")));
        assertTrue(ArchiveFamilySyncState.successfulStopForMode(
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL));
        assertTrue(ArchiveFamilySyncState.successfulStopForMode(
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED));
        assertFalse(ArchiveFamilySyncState.successfulStopForMode(
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED));
    }
}
