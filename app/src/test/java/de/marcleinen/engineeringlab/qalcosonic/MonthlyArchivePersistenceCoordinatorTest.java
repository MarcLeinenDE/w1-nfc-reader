package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MonthlyArchivePersistenceCoordinatorTest {
    @Test
    public void emptyHistoryPlansFullSync() {
        FakeStore store = new FakeStore();
        MonthlyArchivePersistenceCoordinator.PlanContext context =
                MonthlyArchivePersistenceCoordinator.plan(store, "METER-1", 36);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FULL_SYNC, context.plan.mode);
        assertEquals(0, context.plan.requiredKnownOverlap);
        assertEquals(0, context.knownBefore.size());
        assertEquals(0, context.gapsBefore.size());
    }

    @Test
    public void knownHistoryStillPlansFullSyncByDefault() {
        FakeStore store = new FakeStore("2026-07-01 00:00", "2026-06-01 00:00");
        MonthlyArchivePersistenceCoordinator.PlanContext context =
                MonthlyArchivePersistenceCoordinator.plan(store, "METER-1", 36);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FULL_SYNC, context.plan.mode);
        assertEquals(0, context.plan.requiredKnownOverlap);
        assertEquals(2, context.knownBefore.size());
    }

    @Test
    public void fastSyncMustBeRequestedExplicitly() {
        FakeStore store = new FakeStore("2026-07-01 00:00", "2026-06-01 00:00");
        MonthlyArchivePersistenceCoordinator.PlanContext context =
                MonthlyArchivePersistenceCoordinator.planFastSync(store, "METER-1", 36, 2);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FAST_SYNC, context.plan.mode);
        assertEquals(2, context.plan.requiredKnownOverlap);
    }

    @Test
    public void fullSyncPersistsKnownPeriodsAsConfirmationsInsteadOfDuplicates() {
        FakeStore store = new FakeStore("2026-07-01 00:00", "2026-06-01 00:00");
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00"), period("2026-07-01 00:00"),
                        period("2026-06-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.TERMINAL_STANDARD_E5_NO_DATA);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persist(store, "METER-1", enumeration, 36);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FULL_SYNC, persisted.mode);
        assertFalse(persisted.fastContinuityProven);
        assertEquals(1, persisted.unseenBefore);
        assertEquals(1, persisted.inserted);
        assertEquals(2, persisted.confirmed);
        assertEquals(0, persisted.conflicts);
        assertEquals(3, persisted.committed);
        assertEquals(3, store.known.size());
        assertTrue(persisted.persistenceComplete());
        assertTrue(persisted.enumerationTerminalConfirmed);
        assertNull(persisted.postCommitReadDiagnostic);
    }

    @Test
    public void explicitFastSyncCanProveTailOverlap() {
        FakeStore store = new FakeStore("2026-07-01 00:00", "2026-06-01 00:00");
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00"), period("2026-07-01 00:00"),
                        period("2026-06-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.TERMINAL_STANDARD_E5_NO_DATA);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persistFastSync(
                        store, "METER-1", enumeration, 36, 2);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FAST_SYNC, persisted.mode);
        assertTrue(persisted.fastContinuityProven);
        assertEquals(1, persisted.inserted);
        assertEquals(2, persisted.confirmed);
        assertEquals(0, persisted.conflicts);
    }

    @Test
    public void laterStoreFailureCannotRollBackEarlierCommittedPeriods() {
        FakeStore store = new FakeStore();
        store.failOn = "2026-06-01 00:00";
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00"), period("2026-07-01 00:00"),
                        period("2026-06-01 00:00"), period("2026-05-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.IO_ERROR);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persist(store, "METER-1", enumeration, 36);

        assertEquals(2, persisted.committed);
        assertEquals(2, persisted.inserted);
        assertEquals("2026-06-01 00:00", persisted.failedLoggerTimestamp);
        assertFalse(persisted.persistenceComplete());
        assertTrue(persisted.enumerationPartialSuccess);
        assertTrue(store.known.contains("2026-08-01 00:00"));
        assertTrue(store.known.contains("2026-07-01 00:00"));
        assertFalse(store.known.contains("2026-06-01 00:00"));
        assertFalse(store.known.contains("2026-05-01 00:00"));
    }

    @Test
    public void postFailureReadOutageStillReturnsConservativeCommittedState() {
        FakeStore store = new FakeStore("2026-05-01 00:00");
        store.failOn = "2026-06-01 00:00";
        store.failReadsAfterWriteFailure = true;
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00"), period("2026-07-01 00:00"),
                        period("2026-06-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.IO_ERROR);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persist(store, "METER-1", enumeration, 36);

        assertEquals(2, persisted.committed);
        assertEquals("2026-06-01 00:00", persisted.failedLoggerTimestamp);
        assertNotNull(persisted.postCommitReadDiagnostic);
        assertTrue(persisted.postCommitReadDiagnostic.startsWith("POST_COMMIT_HISTORY_READ_FAILED:"));
        assertTrue(persisted.knownAfter.contains("2026-05-01 00:00"));
        assertTrue(persisted.knownAfter.contains("2026-08-01 00:00"));
        assertTrue(persisted.knownAfter.contains("2026-07-01 00:00"));
        assertFalse(persisted.knownAfter.contains("2026-06-01 00:00"));
        assertFalse(persisted.persistenceComplete());
    }

    @Test
    public void persistedConflictIsReportedButStillCountsAsCommitted() {
        FakeStore store = new FakeStore("2026-08-01 00:00");
        store.conflictOn = "2026-08-01 00:00";
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persist(store, "METER-1", enumeration, 36);

        assertEquals(1, persisted.conflicts);
        assertEquals(1, persisted.committed);
        assertTrue(persisted.hasConflicts());
        assertTrue(persisted.persistenceComplete());
    }

    @Test
    public void successfulInitialTerminalCommitsEveryAcceptedPeriod() {
        FakeStore store = new FakeStore();
        MonthlyArchiveEnumerator.Result enumeration = result(
                Arrays.asList(period("2026-08-01 00:00"), period("2026-07-01 00:00")),
                MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D);

        MonthlyArchivePersistenceCoordinator.PersistResult persisted =
                MonthlyArchivePersistenceCoordinator.persist(store, "METER-1", enumeration, 36);

        assertEquals(MonthlyArchiveSyncPlanner.Mode.FULL_SYNC, persisted.mode);
        assertEquals(2, persisted.inserted);
        assertEquals(0, persisted.confirmed);
        assertEquals(0, persisted.conflicts);
        assertEquals(2, persisted.knownAfter.size());
        assertTrue(persisted.gapsAfter.isEmpty());
        assertNull(persisted.failureDiagnostic);
        assertTrue(persisted.persistenceComplete());
    }

    private static MonthlyArchiveEnumerator.Result result(
            List<MonthlyArchivePeriod> periods, MonthlyArchiveEnumerator.StopReason stop) {
        return new MonthlyArchiveEnumerator.Result(periods, stop, null, periods.size() + 1, 36);
    }

    private static MonthlyArchivePeriod period(String timestamp) {
        return new MonthlyArchivePeriod(timestamp, "2026-08-31T12:23:55Z", "fp", null);
    }

    private static final class FakeStore implements MonthlyArchivePersistenceCoordinator.Store {
        final Set<String> known = new LinkedHashSet<>();
        String failOn;
        String conflictOn;
        boolean failReadsAfterWriteFailure;
        boolean writeFailed;

        FakeStore(String... initial) {
            known.addAll(Arrays.asList(initial));
        }

        @Override
        public List<String> getKnownMonthlyTimestamps(String meterIdentity) {
            if (failReadsAfterWriteFailure && writeFailed) {
                throw new IllegalStateException("DB_READ_FAIL");
            }
            return new ArrayList<>(known);
        }

        @Override
        public MonthlyArchivePersistenceCoordinator.WriteOutcome upsertMonthlyArchive(
                String meterIdentity, MonthlyArchivePeriod period) {
            if (period.loggerTimestamp.equals(failOn)) {
                writeFailed = true;
                throw new IllegalStateException("DB_FAIL");
            }
            if (period.loggerTimestamp.equals(conflictOn)) {
                return MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED;
            }
            return known.add(period.loggerTimestamp)
                    ? MonthlyArchivePersistenceCoordinator.WriteOutcome.INSERTED
                    : MonthlyArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL;
        }
    }
}
