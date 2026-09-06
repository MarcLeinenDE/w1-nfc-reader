package de.marcleinen.engineeringlab.qalcosonic;

import java.util.List;

/** Family-neutral independent-period persistence contract. No NFC logic lives here. */
final class ArchivePersistenceCoordinator {
    enum WriteOutcome { INSERTED, CONFIRMED_IDENTICAL, CONFLICT_RECORDED }

    interface Store {
        WriteOutcome upsert(String meterId, ArchiveFamilyPeriod period);
    }

    static final class Result {
        final ArchiveFamilyPeriod.Family family;
        final int accepted;
        int inserted;
        int confirmed;
        int conflicts;
        int committed;
        String failedLoggerTimestamp;
        String failureDiagnostic;

        Result(ArchiveFamilyPeriod.Family family, int accepted) {
            this.family = family;
            this.accepted = accepted;
        }

        boolean complete() { return failureDiagnostic == null && committed == accepted; }
    }

    /**
     * One-at-a-time persistence session for an active archive traversal.
     *
     * <p>Each accepted period is committed before traversal is allowed to continue. A write failure
     * is retained in the snapshot and rethrown so the transport layer can stop protectively with
     * {@code PERSISTENCE_ERROR} while still performing its final default restore.</p>
     */
    static final class ImmediateSession {
        private final Store store;
        private final String meterId;
        private final ArchiveFamilyPeriod.Family family;

        private int accepted;
        private int inserted;
        private int confirmed;
        private int conflicts;
        private int committed;
        private String failedLoggerTimestamp;
        private String failureDiagnostic;

        ImmediateSession(Store store, String meterId, ArchiveFamilyPeriod.Family family) {
            if (store == null) throw new IllegalArgumentException("store == null");
            if (meterId == null || meterId.trim().isEmpty()) {
                throw new IllegalArgumentException("meterId required");
            }
            if (family == null) throw new IllegalArgumentException("family == null");
            this.store = store;
            this.meterId = meterId.trim();
            this.family = family;
        }

        void accept(ArchiveFamilyPeriod period) {
            if (failureDiagnostic != null) {
                throw new IllegalStateException("persistence session already failed: " + failureDiagnostic);
            }
            if (period == null) throw new IllegalArgumentException("period == null");
            if (period.family != family) throw new IllegalArgumentException("mixed archive families");

            accepted++;
            try {
                WriteOutcome outcome = store.upsert(meterId, period);
                if (outcome == WriteOutcome.INSERTED) inserted++;
                else if (outcome == WriteOutcome.CONFIRMED_IDENTICAL) confirmed++;
                else if (outcome == WriteOutcome.CONFLICT_RECORDED) conflicts++;
                else throw new IllegalStateException("unknown persistence outcome");
                committed++;
            } catch (RuntimeException error) {
                failedLoggerTimestamp = period.loggerTimestamp;
                failureDiagnostic = safe(error);
                throw error;
            }
        }

        Result result() {
            Result out = new Result(family, accepted);
            out.inserted = inserted;
            out.confirmed = confirmed;
            out.conflicts = conflicts;
            out.committed = committed;
            out.failedLoggerTimestamp = failedLoggerTimestamp;
            out.failureDiagnostic = failureDiagnostic;
            return out;
        }
    }

    private ArchivePersistenceCoordinator() {}

    static ImmediateSession beginImmediate(
            Store store,
            String meterId,
            ArchiveFamilyPeriod.Family family) {
        return new ImmediateSession(store, meterId, family);
    }

    static Result persist(Store store, String meterId, ArchiveFamilyPeriod.Family family,
                          List<ArchiveFamilyPeriod> periods) {
        if (store == null) throw new IllegalArgumentException("store == null");
        if (meterId == null || meterId.trim().isEmpty()) throw new IllegalArgumentException("meterId required");
        if (family == null) throw new IllegalArgumentException("family == null");
        if (periods == null) throw new IllegalArgumentException("periods == null");
        Result result = new Result(family, periods.size());
        for (ArchiveFamilyPeriod period : periods) {
            if (period == null) continue;
            if (period.family != family) throw new IllegalArgumentException("mixed archive families");
            try {
                WriteOutcome outcome = store.upsert(meterId, period);
                if (outcome == WriteOutcome.INSERTED) result.inserted++;
                else if (outcome == WriteOutcome.CONFIRMED_IDENTICAL) result.confirmed++;
                else if (outcome == WriteOutcome.CONFLICT_RECORDED) result.conflicts++;
                result.committed++;
            } catch (RuntimeException error) {
                result.failedLoggerTimestamp = period.loggerTimestamp;
                result.failureDiagnostic = safe(error);
                break;
            }
        }
        return result;
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
