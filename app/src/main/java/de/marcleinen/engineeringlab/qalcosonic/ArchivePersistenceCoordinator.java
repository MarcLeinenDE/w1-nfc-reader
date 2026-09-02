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

    private ArchivePersistenceCoordinator() {}

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
