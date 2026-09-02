package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline-first persistence orchestration for already validated Monthly archive periods.
 *
 * <p>No NFC/M-Bus I/O lives here. Each accepted period is written through an independent atomic
 * store operation so a later database failure cannot roll back previously committed periods.
 * The canonical default is explicit full synchronization. Known-overlap stopping is exposed only
 * through the separately named fast-sync path.</p>
 */
final class MonthlyArchivePersistenceCoordinator {
    static final int DEFAULT_FAST_SYNC_KNOWN_OVERLAP = 2;

    enum WriteOutcome {
        INSERTED,
        CONFIRMED_IDENTICAL,
        CONFLICT_RECORDED
    }

    interface Store {
        List<String> getKnownMonthlyTimestamps(String meterIdentity);

        WriteOutcome upsertMonthlyArchive(String meterIdentity, MonthlyArchivePeriod period);
    }

    static final class PlanContext {
        final MonthlyArchiveSyncPlanner.Plan plan;
        final List<String> knownBefore;
        final List<String> gapsBefore;

        PlanContext(MonthlyArchiveSyncPlanner.Plan plan, List<String> knownBefore,
                    List<String> gapsBefore) {
            this.plan = plan;
            this.knownBefore = Collections.unmodifiableList(new ArrayList<>(knownBefore));
            this.gapsBefore = Collections.unmodifiableList(new ArrayList<>(gapsBefore));
        }
    }

    static final class PersistResult {
        final MonthlyArchiveSyncPlanner.Mode mode;
        final MonthlyArchiveEnumerator.StopReason enumerationStopReason;
        final boolean enumerationTerminalConfirmed;
        final boolean enumerationPartialSuccess;
        final int acceptedPeriods;
        final int knownBefore;
        final int unseenBefore;
        final boolean fastContinuityProven;
        int inserted;
        int confirmed;
        int conflicts;
        int committed;
        String failedLoggerTimestamp;
        String failureDiagnostic;
        String postCommitReadDiagnostic;
        List<String> knownAfter = Collections.emptyList();
        List<String> gapsAfter = Collections.emptyList();

        PersistResult(MonthlyArchiveSyncPlanner.Mode mode,
                      MonthlyArchiveEnumerator.Result enumeration,
                      int knownBefore,
                      int unseenBefore,
                      boolean fastContinuityProven) {
            this.mode = mode;
            this.enumerationStopReason = enumeration.stopReason;
            this.enumerationTerminalConfirmed = enumeration.terminalConfirmed();
            this.enumerationPartialSuccess = enumeration.partialSuccess();
            this.acceptedPeriods = enumeration.periods.size();
            this.knownBefore = knownBefore;
            this.unseenBefore = unseenBefore;
            this.fastContinuityProven = fastContinuityProven;
        }

        boolean persistenceComplete() {
            return failureDiagnostic == null && committed == acceptedPeriods;
        }

        boolean hasConflicts() {
            return conflicts > 0;
        }
    }

    private MonthlyArchivePersistenceCoordinator() {}

    /** Canonical plan for an explicit History action: always permit a complete bounded walk. */
    static PlanContext plan(Store store, String meterIdentity, int hardCap) {
        requireStoreAndMeter(store, meterIdentity);
        List<String> known = safeKnown(store.getKnownMonthlyTimestamps(meterIdentity));
        MonthlyArchiveSyncPlanner.Plan plan = MonthlyArchiveSyncPlanner.fullSync(hardCap);
        List<String> gaps = MonthlyArchiveSyncPlanner.findGaps(known);
        return new PlanContext(plan, known, gaps);
    }

    /** Optional fast-sync optimization; never selected implicitly by local completeness. */
    static PlanContext planFastSync(Store store, String meterIdentity, int hardCap) {
        return planFastSync(store, meterIdentity, hardCap, DEFAULT_FAST_SYNC_KNOWN_OVERLAP);
    }

    static PlanContext planFastSync(
            Store store, String meterIdentity, int hardCap, int requiredKnownOverlap) {
        requireStoreAndMeter(store, meterIdentity);
        List<String> known = safeKnown(store.getKnownMonthlyTimestamps(meterIdentity));
        MonthlyArchiveSyncPlanner.Plan plan = MonthlyArchiveSyncPlanner.fastSync(
                hardCap, requiredKnownOverlap);
        List<String> gaps = MonthlyArchiveSyncPlanner.findGaps(known);
        return new PlanContext(plan, known, gaps);
    }

    static PersistResult persist(Store store,
                                 String meterIdentity,
                                 MonthlyArchiveEnumerator.Result enumeration,
                                 int hardCap) {
        return persistWithContext(store, meterIdentity, enumeration, plan(store, meterIdentity, hardCap));
    }

    static PersistResult persistFastSync(Store store,
                                         String meterIdentity,
                                         MonthlyArchiveEnumerator.Result enumeration,
                                         int hardCap) {
        return persistFastSync(store, meterIdentity, enumeration, hardCap,
                DEFAULT_FAST_SYNC_KNOWN_OVERLAP);
    }

    static PersistResult persistFastSync(Store store,
                                         String meterIdentity,
                                         MonthlyArchiveEnumerator.Result enumeration,
                                         int hardCap,
                                         int requiredKnownOverlap) {
        return persistWithContext(store, meterIdentity, enumeration,
                planFastSync(store, meterIdentity, hardCap, requiredKnownOverlap));
    }

    private static PersistResult persistWithContext(
            Store store,
            String meterIdentity,
            MonthlyArchiveEnumerator.Result enumeration,
            PlanContext context) {
        requireStoreAndMeter(store, meterIdentity);
        if (enumeration == null) throw new IllegalArgumentException("enumeration == null");

        List<String> unseen = MonthlyArchiveSyncPlanner.unseenPeriods(
                enumeration.periods, context.knownBefore);
        boolean continuity = context.plan.mode == MonthlyArchiveSyncPlanner.Mode.FAST_SYNC
                && MonthlyArchiveSyncPlanner.fastContinuityProven(
                enumeration.periods, context.knownBefore, context.plan.requiredKnownOverlap);

        PersistResult result = new PersistResult(
                context.plan.mode,
                enumeration,
                context.knownBefore.size(),
                unseen.size(),
                continuity);
        List<String> committedTimestamps = new ArrayList<>();

        for (MonthlyArchivePeriod period : enumeration.periods) {
            if (period == null) continue;
            try {
                WriteOutcome outcome = store.upsertMonthlyArchive(meterIdentity, period);
                if (outcome == WriteOutcome.INSERTED) result.inserted++;
                else if (outcome == WriteOutcome.CONFIRMED_IDENTICAL) result.confirmed++;
                else if (outcome == WriteOutcome.CONFLICT_RECORDED) result.conflicts++;
                else throw new IllegalStateException("Unknown persistence outcome");
                result.committed++;
                committedTimestamps.add(period.loggerTimestamp);
            } catch (RuntimeException error) {
                result.failedLoggerTimestamp = period.loggerTimestamp;
                result.failureDiagnostic = safe(error);
                break;
            }
        }

        try {
            result.knownAfter = Collections.unmodifiableList(new ArrayList<>(
                    safeKnown(store.getKnownMonthlyTimestamps(meterIdentity))));
        } catch (RuntimeException error) {
            // A broken DB must not erase knowledge of commits already acknowledged one-by-one.
            // Build a conservative fallback from the pre-sync state plus confirmed commits.
            Set<String> fallback = new LinkedHashSet<>(context.knownBefore);
            fallback.addAll(committedTimestamps);
            result.knownAfter = Collections.unmodifiableList(new ArrayList<>(fallback));
            result.postCommitReadDiagnostic = "POST_COMMIT_HISTORY_READ_FAILED:" + safe(error);
        }
        result.gapsAfter = MonthlyArchiveSyncPlanner.findGaps(result.knownAfter);
        return result;
    }

    private static void requireStoreAndMeter(Store store, String meterIdentity) {
        if (store == null) throw new IllegalArgumentException("store == null");
        if (meterIdentity == null || meterIdentity.trim().isEmpty()) {
            throw new IllegalArgumentException("meterIdentity required");
        }
    }

    private static List<String> safeKnown(List<String> known) {
        return known == null ? Collections.emptyList() : new ArrayList<>(known);
    }

    private static String safe(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
