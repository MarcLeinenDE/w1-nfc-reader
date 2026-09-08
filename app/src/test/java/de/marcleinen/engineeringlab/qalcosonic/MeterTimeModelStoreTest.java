package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class MeterTimeModelStoreTest {
    private Context context;
    private MeterTimeModelStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        store = new MeterTimeModelStore(context);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @Test public void firstVerifiedAssignmentIsStableAgainstLaterDeviceZoneChanges() {
        assertTrue(store.assignZoneIfMissing(
                "M1", "Europe/Berlin",
                MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                1_000L));
        assertFalse(store.assignZoneIfMissing(
                "M1", "America/New_York",
                MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                2_000L));

        MeterTimeModelStore.Profile profile = store.getProfile("M1");
        assertNotNull(profile);
        assertEquals("Europe/Berlin", profile.zoneId);
        assertEquals(1_000L, profile.assignedAtMs);
        assertEquals(1_000L, profile.updatedAtMs);
    }

    @Test public void explicitUserChangePreservesOriginalAssignmentTime() {
        store.assignZoneIfMissing(
                "M1", "Europe/Berlin",
                MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                1_000L);
        store.setZone(
                "M1", "America/New_York",
                MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED,
                5_000L);

        MeterTimeModelStore.Profile profile = store.getProfile("M1");
        assertEquals("America/New_York", profile.zoneId);
        assertEquals(MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED, profile.source);
        assertEquals(1_000L, profile.assignedAtMs);
        assertEquals(5_000L, profile.updatedAtMs);
    }

    @Test public void fixedNumericOffsetIsRejectedAsDurableMeterTimezone() {
        assertThrows(IllegalArgumentException.class,
                () -> store.assignZoneIfMissing("M1", "GMT+02:00",
                        MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE, 1_000L));
    }

    @Test public void verifiedAnchorRoundTripsLosslesslyThroughStoreJson() throws Exception {
        long center = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        store.assignZoneIfMissing(
                "M1", "Europe/Berlin",
                MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                center);
        VerifiedLiveTimeAnchor anchor = new VerifiedLiveTimeAnchor(
                "M1", center - 120L, center + 180L,
                "2026-09-08 21:00", "00 15 28 39", false, false, 81_000_000L);
        long id = store.recordAnchor(
                anchor,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        assertTrue(id > 0L);

        JSONObject exported = store.exportJson();
        store.clear();
        assertEquals(null, store.getProfile("M1"));
        assertEquals(null, store.latestAnchor("M1"));

        store.restoreJson(exported);
        MeterTimeModelStore.Profile profile = store.getProfile("M1");
        MeterTimeModelStore.AnchorRecord restored = store.latestAnchor("M1");
        assertEquals("Europe/Berlin", profile.zoneId);
        assertNotNull(restored);
        assertEquals(center + 30L, restored.anchorEpochMs);
        assertEquals(150L, restored.uncertaintyMs);
        assertEquals("2026-09-08 21:00", restored.rawMeterWallClock);
        assertEquals("00 15 28 39", restored.rawTypeFHex);
        assertEquals(81_000_000L, restored.onTimeSeconds);
        assertFalse(restored.invalidTime);
        assertFalse(restored.summerTime);
        assertTrue(restored.toAnchor().usable());
    }

    @Test public void mergeDoesNotSilentlyReplaceExistingMeterZone() throws Exception {
        store.assignZoneIfMissing(
                "M1", "Europe/Berlin",
                MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                1_000L);

        JSONObject imported = new JSONObject()
                .put("schema_version", MeterTimeModelStore.JSON_SCHEMA)
                .put("profiles", new JSONArray().put(new JSONObject()
                        .put("meter_id", "M1")
                        .put("zone_id", "America/New_York")
                        .put("zone_source", MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED)
                        .put("zone_assigned_at_ms", 1_000L)
                        .put("zone_updated_at_ms", 9_000L)))
                .put("anchors", new JSONArray());

        store.mergeJson(imported);

        assertEquals("Europe/Berlin", store.getProfile("M1").zoneId);
    }

    @Test public void duplicateAnchorEvidenceDoesNotCreateDuplicateRows() {
        long center = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = new VerifiedLiveTimeAnchor(
                "M1", center - 100L, center + 100L,
                "2026-09-08 21:00", "00 15 28 39", false, false, 81_000_000L);
        long first = store.recordAnchor(
                anchor,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        long second = store.recordAnchor(
                anchor,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);

        assertEquals(first, second);
        assertEquals(1, store.anchors("M1").size());
    }
}
