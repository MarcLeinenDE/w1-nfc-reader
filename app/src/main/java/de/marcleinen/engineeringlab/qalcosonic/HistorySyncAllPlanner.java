package de.marcleinen.engineeringlab.qalcosonic;

/** Pure product policy for the combined Hour -> Day -> Month synchronization action. */
final class HistorySyncAllPlanner {
    private HistorySyncAllPlanner() { }

    static ArchiveFamilySyncState.SyncMode modeFor(ArchiveFamilySyncState state) {
        if (state == null) throw new IllegalArgumentException("family state required");
        return state.baselineComplete()
                ? ArchiveFamilySyncState.SyncMode.INCREMENTAL
                : ArchiveFamilySyncState.SyncMode.INITIAL_FULL;
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
