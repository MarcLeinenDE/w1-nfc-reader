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

    @Test public void newerVerifiedAnchorReplacesOlderActiveAnchor() {
        long firstCenter = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        long secondCenter = Instant.parse("2026-09-09T20:00:00Z").toEpochMilli();
        VerifiedLiveTimeAnchor first = new VerifiedLiveTimeAnchor(
                "M1", firstCenter - 100L, firstCenter + 100L,
                "2026-09-08 21:00", "00 15 28 39", false, false, 81_000_000L);
        VerifiedLiveTimeAnchor second = new VerifiedLiveTimeAnchor(
                "M1", secondCenter - 100L, secondCenter + 100L,
                "2026-09-09 21:00", "00 15 29 39", false, false, 81_086_400L);

        store.recordAnchor(first,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        store.recordAnchor(second,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);

        assertEquals(1, store.anchors("M1").size());
        assertEquals(81_086_400L, store.latestAnchor("M1").onTimeSeconds);
        assertEquals(secondCenter, store.latestAnchor("M1").anchorEpochMs);
    }

    @Test public void backwardsOnTimeCannotReplaceActiveAnchor() {
        long firstCenter = Instant.parse("2026-09-09T20:00:00Z").toEpochMilli();
        long laterCenter = Instant.parse("2026-09-10T20:00:00Z").toEpochMilli();
        VerifiedLiveTimeAnchor active = new VerifiedLiveTimeAnchor(
                "M1", firstCenter - 100L, firstCenter + 100L,
                "2026-09-09 21:00", "00 15 29 39", false, false, 81_086_400L);
        VerifiedLiveTimeAnchor backwards = new VerifiedLiveTimeAnchor(
                "M1", laterCenter - 100L, laterCenter + 100L,
                "2026-09-10 21:00", "00 15 2A 39", false, false, 81_000_000L);
        store.recordAnchor(active,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);

        assertThrows(IllegalArgumentException.class,
                () -> store.recordAnchor(backwards,
                        MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                        MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE));
        assertEquals(81_086_400L, store.latestAnchor("M1").onTimeSeconds);
        assertEquals(1, store.anchors("M1").size());
    }

    @Test public void restoreOfLegacyMultiAnchorJsonKeepsOnlyNewestPerMeter() throws Exception {
        long firstCenter = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        long secondCenter = Instant.parse("2026-09-09T20:00:00Z").toEpochMilli();
        JSONObject older = anchorJson("M1", firstCenter, 81_000_000L, "2026-09-08 21:00");
        JSONObject newer = anchorJson("M1", secondCenter, 81_086_400L, "2026-09-09 21:00");
        JSONObject legacy = new JSONObject()
                .put("schema_version", MeterTimeModelStore.JSON_SCHEMA)
                .put("profiles", new JSONArray())
                .put("anchors", new JSONArray().put(newer).put(older));

        store.restoreJson(legacy);

        assertEquals(1, store.anchors("M1").size());
        assertEquals(81_086_400L, store.latestAnchor("M1").onTimeSeconds);
        assertEquals(secondCenter, store.latestAnchor("M1").anchorEpochMs);
    }

    private static JSONObject anchorJson(
            String meterId, long center, long onTime, String rawClock) throws Exception {
        return new JSONObject()
                .put("meter_id", meterId)
                .put("read_before_epoch_ms", center - 100L)
                .put("read_after_epoch_ms", center + 100L)
                .put("anchor_epoch_ms", center)
                .put("uncertainty_ms", 100L)
                .put("raw_meter_wall_clock", rawClock)
                .put("raw_type_f_hex", "00 15 28 39")
                .put("type_f_iv", false)
                .put("type_f_su", false)
                .put("on_time_seconds", onTime)
                .put("provenance", MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT)
                .put("validation", MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
    }
}
