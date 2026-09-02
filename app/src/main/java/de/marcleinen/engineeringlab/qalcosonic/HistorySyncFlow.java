package de.marcleinen.engineeringlab.qalcosonic;

/**
 * Pure product-layer state contract for an explicit History synchronization.
 *
 * <p>0.7.26 deliberately does not perform NFC I/O. A successful normal live read only offers the
 * History action. The user must explicitly request a full sync, after which the matching meter
 * must be presented before a future transport integration may start.</p>
 */
final class HistorySyncFlow {
    enum State {
        IDLE,
        OFFERED_AFTER_LIVE_READ,
        ARMED_WAITING_FOR_METER,
        RUNNING,
        COMPLETED,
        PARTIAL_SUCCESS,
        FAILED
    }

    enum MeterPresentationResult {
        STARTED,
        WRONG_METER,
        IGNORED_NOT_ARMED
    }

    private State state = State.IDLE;
    private String targetMeterId;

    State state() {
        return state;
    }

    String targetMeterId() {
        return targetMeterId;
    }

    void onLiveReadSucceeded(String meterId) {
        if (state == State.RUNNING) {
            throw new IllegalStateException("History synchronization is already running");
        }
        targetMeterId = requireMeterId(meterId);
        state = State.OFFERED_AFTER_LIVE_READ;
    }

    boolean canRequestFullSync() {
        return targetMeterId != null
                && state != State.ARMED_WAITING_FOR_METER
                && state != State.RUNNING;
    }

    void requestFullSync() {
        if (!canRequestFullSync()) {
            throw new IllegalStateException("A successful live read is required before History sync");
        }
        state = State.ARMED_WAITING_FOR_METER;
    }

    MeterPresentationResult onMeterPresented(String meterId) {
        if (state != State.ARMED_WAITING_FOR_METER) {
            return MeterPresentationResult.IGNORED_NOT_ARMED;
        }
        if (!targetMeterId.equals(normalizeMeterId(meterId))) {
            return MeterPresentationResult.WRONG_METER;
        }
        state = State.RUNNING;
        return MeterPresentationResult.STARTED;
    }

    void onSyncCompleted() {
        requireRunning();
        state = State.COMPLETED;
    }

    void onSyncPartialSuccess() {
        requireRunning();
        state = State.PARTIAL_SUCCESS;
    }

    void onSyncFailed() {
        requireRunning();
        state = State.FAILED;
    }

    void cancelArmedSync() {
        if (state == State.ARMED_WAITING_FOR_METER) {
            state = State.OFFERED_AFTER_LIVE_READ;
        }
    }

    private void requireRunning() {
        if (state != State.RUNNING) {
            throw new IllegalStateException("History synchronization is not running");
        }
    }

    private static String requireMeterId(String meterId) {
        String normalized = normalizeMeterId(meterId);
        if (normalized == null) {
            throw new IllegalArgumentException("meterId must not be blank");
        }
        return normalized;
    }

    private static String normalizeMeterId(String meterId) {
        if (meterId == null) return null;
        String normalized = meterId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
