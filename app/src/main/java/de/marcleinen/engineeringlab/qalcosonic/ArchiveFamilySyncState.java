package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Objects;

/**
 * Product-layer synchronization state for one archive family on one meter.
 *
 * <p>Baseline completeness is intentionally separate from the latest attempt outcome. A later
 * interrupted incremental update must not erase a previously completed baseline, while the UI
 * still needs to report that the latest attempt was partial or failed.</p>
 */
final class ArchiveFamilySyncState {
    enum BaselineState {
        NEVER_SYNCED,
        PARTIAL,
        COMPLETE
    }

    enum AttemptOutcome {
        NONE,
        COMPLETE,
        PARTIAL,
        FAILED
    }

    enum SyncMode {
        INITIAL_FULL,
        INCREMENTAL,
        FULL_RESYNC
    }

    enum StopReason {
        NONE,

        // Successful semantic stops.
        KNOWN_RECORD_REACHED,
        PROTOCOL_TERMINAL,
        RING_WRAP_DETECTED,

        // Protective/incomplete stops.
        BOUNDARY_GUARD_REACHED,
        HEAD_ADVANCED_DURING_TRAVERSAL,
        TRANSPORT_SESSION_LOST,
        NO_HOST_RESPONSE,
        IO_ERROR,
        PARSER_ERROR,
        STRUCTURE_ERROR,
        TYPE_F_MISSING,
        TYPE_F_INVALID,
        ON_TIME_INCONSISTENT,
        PROGRESSION_INCONSISTENT,
        DEFAULT_STATE_UNVERIFIED,
        WATCHDOG_REACHED,
        PERSISTENCE_ERROR,
        UNKNOWN_ERROR
    }

    final ArchiveFamilyPeriod.Family family;
    final BaselineState baselineState;
    final AttemptOutcome lastAttemptOutcome;
    final SyncMode lastSyncMode;
    final StopReason lastStopReason;
    final long lastAttemptMs;
    final long lastSuccessMs;
    final long baselineCompletedAtMs;
    final String oldestRawTimestamp;
    final String newestRawTimestamp;
    final String incrementalAnchorRawTimestamp;
    final int accepted;
    final int inserted;
    final int confirmed;
    final int conflicts;
    final boolean finalRestoreVerified;
    final boolean recoveryRecommended;

    ArchiveFamilySyncState(
            ArchiveFamilyPeriod.Family family,
            BaselineState baselineState,
            AttemptOutcome lastAttemptOutcome,
            SyncMode lastSyncMode,
            StopReason lastStopReason,
            long lastAttemptMs,
            long lastSuccessMs,
            long baselineCompletedAtMs,
            String oldestRawTimestamp,
            String newestRawTimestamp,
            String incrementalAnchorRawTimestamp,
            int accepted,
            int inserted,
            int confirmed,
            int conflicts,
            boolean finalRestoreVerified,
            boolean recoveryRecommended) {
        this.family = Objects.requireNonNull(family, "family");
        this.baselineState = Objects.requireNonNull(baselineState, "baselineState");
        this.lastAttemptOutcome = Objects.requireNonNull(lastAttemptOutcome, "lastAttemptOutcome");
        this.lastSyncMode = Objects.requireNonNull(lastSyncMode, "lastSyncMode");
        this.lastStopReason = Objects.requireNonNull(lastStopReason, "lastStopReason");
        this.lastAttemptMs = lastAttemptMs;
        this.lastSuccessMs = lastSuccessMs;
        this.baselineCompletedAtMs = baselineCompletedAtMs;
        this.oldestRawTimestamp = normalizeOptional(oldestRawTimestamp);
        this.newestRawTimestamp = normalizeOptional(newestRawTimestamp);
        this.incrementalAnchorRawTimestamp = normalizeOptional(incrementalAnchorRawTimestamp);
        this.accepted = nonNegative(accepted, "accepted");
        this.inserted = nonNegative(inserted, "inserted");
        this.confirmed = nonNegative(confirmed, "confirmed");
        this.conflicts = nonNegative(conflicts, "conflicts");
        this.finalRestoreVerified = finalRestoreVerified;
        this.recoveryRecommended = recoveryRecommended;
    }

    static ArchiveFamilySyncState empty(ArchiveFamilyPeriod.Family family) {
        return new ArchiveFamilySyncState(
                family,
                BaselineState.NEVER_SYNCED,
                AttemptOutcome.NONE,
                SyncMode.INITIAL_FULL,
                StopReason.NONE,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                false,
                false);
    }

    boolean baselineComplete() {
        return baselineState == BaselineState.COMPLETE;
    }

    boolean hasAttempt() {
        return lastAttemptMs > 0L && lastAttemptOutcome != AttemptOutcome.NONE;
    }

    static boolean successfulStopForMode(SyncMode mode, StopReason reason) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(reason, "reason");
        switch (mode) {
            case INITIAL_FULL:
            case FULL_RESYNC:
                return reason == StopReason.PROTOCOL_TERMINAL
                        || reason == StopReason.RING_WRAP_DETECTED;
            case INCREMENTAL:
                return reason == StopReason.KNOWN_RECORD_REACHED
                        || reason == StopReason.PROTOCOL_TERMINAL
                        || reason == StopReason.RING_WRAP_DETECTED;
            default:
                return false;
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }
}
