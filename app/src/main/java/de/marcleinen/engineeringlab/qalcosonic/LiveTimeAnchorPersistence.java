package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.time.ZoneId;

/**
 * Converts already-read default Live evidence into a durable v2.1 time anchor.
 *
 * <p>No NFC operation lives here. Candidate creation only inspects the response that the normal
 * Live route already received. The candidate remains in memory until the product accepts that Live
 * reading; callers must not persist a candidate for a meter that the user rejects.</p>
 */
final class LiveTimeAnchorPersistence {
    private LiveTimeAnchorPersistence() { }

    static VerifiedLiveTimeAnchor candidate(
            String expectedMeterId,
            long readBeforeEpochMs,
            long readAfterEpochMs,
            QalcosonicReader.Readout readout) {
        DefaultReadObservation observation = DefaultReadObservation.fromReadout(readout);
        if (!observation.verifiedForFamilySafety(expectedMeterId, null)) return null;
        VerifiedLiveTimeAnchor anchor = VerifiedLiveTimeAnchor.fromEvidence(
                expectedMeterId,
                readBeforeEpochMs,
                readAfterEpochMs,
                observation.timeEvidence);
        return anchor.usable() ? anchor : null;
    }

    static boolean persist(Context context, VerifiedLiveTimeAnchor anchor) {
        return persist(context, anchor, ZoneId.systemDefault().getId());
    }

    static boolean persist(Context context, VerifiedLiveTimeAnchor anchor, String deviceZoneId) {
        if (context == null || anchor == null || !anchor.usable()) return false;
        MeterTimeModelStore store = new MeterTimeModelStore(context.getApplicationContext());
        try {
            store.assignZoneIfMissing(
                    anchor.meterId,
                    deviceZoneId,
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    anchor.anchorEpochMs);
            store.recordAnchor(
                    anchor,
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
            return true;
        } catch (RuntimeException ignored) {
            // The time model is additive evidence. Never turn an otherwise successful normal Live
            // contact into a product failure solely because this auxiliary persistence failed.
            return false;
        } finally {
            store.close();
        }
    }
}
