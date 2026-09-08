package de.marcleinen.engineeringlab.qalcosonic;

/** Pure product policy for the combined Hour -> Day -> Month synchronization actions. */
final class HistorySyncAllPlanner {
    private HistorySyncAllPlanner() { }

    /** Normal Sync All stays conservative: complete families update incrementally, others initialize. */
    static ArchiveFamilySyncState.SyncMode modeFor(ArchiveFamilySyncState state) {
        if (state == null) throw new IllegalArgumentException("family state required");
        return state.baselineComplete()
                ? ArchiveFamilySyncState.SyncMode.INCREMENTAL
                : ArchiveFamilySyncState.SyncMode.INITIAL_FULL;
    }

    /** Full Re-Sync is a separate advanced action and must never weaken normal Sync All. */
    static ArchiveFamilySyncState.SyncMode modeFor(
            ArchiveFamilySyncState state,
            boolean fullResync) {
        if (state == null) throw new IllegalArgumentException("family state required");
        return fullResync ? ArchiveFamilySyncState.SyncMode.FULL_RESYNC : modeFor(state);
    }

    /** Full Re-Sync is offered only after all three product history baselines are authoritative. */
    static boolean fullResyncAvailable(
            ArchiveFamilySyncState hour,
            ArchiveFamilySyncState day,
            ArchiveFamilySyncState month) {
        return hour != null && day != null && month != null
                && hour.baselineComplete()
                && day.baselineComplete()
                && month.baselineComplete();
    }

    /**
     * A later family may start only after the previous family either completed normally or at
     * least restored and verified the default application. If restore is unverified, the meter
     * state is unknown and the combined action stops rather than chaining another archive select.
     */
    static boolean mayContinue(boolean attemptComplete, boolean finalRestoreVerified) {
        return attemptComplete || finalRestoreVerified;
    }
}
