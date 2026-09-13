package de.marcleinen.engineeringlab.qalcosonic;

/**
 * A verified Live/default observation tied to an Android epoch acquisition window.
 *
 * <p>The midpoint of the before/after window is the absolute anchor. Half of the observed window is
 * retained as acquisition uncertainty instead of pretending that the meter frame was observed at an
 * exact millisecond.</p>
 */
final class VerifiedLiveTimeAnchor {
    final String meterId;
    final long readBeforeEpochMs;
    final long readAfterEpochMs;
    final long anchorEpochMs;
    final long uncertaintyMs;
    final String rawMeterWallClock;
    final String rawTypeFHex;
    final boolean invalidTime;
    final boolean summerTime;
    final Long onTimeSeconds;

    VerifiedLiveTimeAnchor(
            String meterId,
            long readBeforeEpochMs,
            long readAfterEpochMs,
            String rawMeterWallClock,
            String rawTypeFHex,
            boolean invalidTime,
            boolean summerTime,
            Long onTimeSeconds) {
        this.meterId = normalizeMeter(meterId);
        this.readBeforeEpochMs = readBeforeEpochMs;
        this.readAfterEpochMs = readAfterEpochMs;
        this.anchorEpochMs = midpoint(readBeforeEpochMs, readAfterEpochMs);
        this.uncertaintyMs = uncertainty(readBeforeEpochMs, readAfterEpochMs);
        this.rawMeterWallClock = rawMeterWallClock;
        this.rawTypeFHex = rawTypeFHex;
        this.invalidTime = invalidTime;
        this.summerTime = summerTime;
        this.onTimeSeconds = onTimeSeconds;
    }

    static VerifiedLiveTimeAnchor fromEvidence(
            String meterId,
            long readBeforeEpochMs,
            long readAfterEpochMs,
            MeterTimeEvidence evidence) {
        if (evidence == null) {
            return new VerifiedLiveTimeAnchor(
                    meterId, readBeforeEpochMs, readAfterEpochMs,
                    null, null, true, false, null);
        }
        return new VerifiedLiveTimeAnchor(
                meterId,
                readBeforeEpochMs,
                readAfterEpochMs,
                evidence.decodedRawWallClock,
                evidence.rawTypeFHex,
                evidence.invalidTime,
                evidence.summerTime,
                evidence.onTimeSeconds);
    }

    boolean usable() {
        return meterId != null
                && readBeforeEpochMs > 0L
                && readAfterEpochMs >= readBeforeEpochMs
                && anchorEpochMs > 0L
                && rawMeterWallClock != null
                && !invalidTime
                && onTimeSeconds != null
                && onTimeSeconds >= 0L;
    }

    private static long midpoint(long before, long after) {
        if (before <= 0L || after < before) return -1L;
        return before + ((after - before) / 2L);
    }

    private static long uncertainty(long before, long after) {
        if (before <= 0L || after < before) return -1L;
        return Math.max(1L, (after - before + 1L) / 2L);
    }

    private static String normalizeMeter(String meterId) {
        if (meterId == null) return null;
        String value = meterId.trim();
        return value.isEmpty() ? null : value;
    }
}
