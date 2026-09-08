package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityV3Test {
    private Context context;
    private TimeZone originalZone;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        originalZone = TimeZone.getDefault();
        DataPortabilityV3.clearMeterData(context);
    }

    @After public void tearDown() {
        DataPortabilityV3.clearMeterData(context);
        TimeZone.setDefault(originalZone);
    }

    @Test public void schema3RoundTripPreservesMeterZoneAndVerifiedAnchor() throws Exception {
        long center = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        MeterTimeModelStore time = new MeterTimeModelStore(context);
        try {
            time.assignZoneIfMissing(
                    "M1", "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    center);
            time.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            "M1", center - 100L, center + 100L,
                            "2026-09-08 21:00", "00 15 28 39", false, false, 81_000_000L),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        } finally {
            time.close();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityV3.writeBackup(context, out);
        byte[] backup = out.toByteArray();
        assertTrue(backup.length > 0);
        assertEquals(3, DataPortabilityV3.BACKUP_SCHEMA);

        DataPortability.BackupPreview preview = DataPortabilityV3.inspectBackup(
                new ByteArrayInputStream(backup));
        assertEquals("M1", preview.activeMeterId);

        DataPortabilityV3.clearMeterData(context);
        DataPortabilityV3.restoreBackup(context, preview.bytes);

        MeterTimeModelStore restored = new MeterTimeModelStore(context);
        try {
            MeterTimeModelStore.Profile profile = restored.getProfile("M1");
            MeterTimeModelStore.AnchorRecord anchor = restored.latestAnchor("M1");
            assertNotNull(profile);
            assertEquals("Europe/Berlin", profile.zoneId);
            assertNotNull(anchor);
            assertEquals(center, anchor.anchorEpochMs);
            assertEquals(81_000_000L, anchor.onTimeSeconds);
            assertEquals("00 15 28 39", anchor.rawTypeFHex);
        } finally {
            restored.close();
        }
    }

    @Test public void legacySchema2RestoreDoesNotInventTimeModelEvidence() throws Exception {
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, legacy);
        assertEquals(2, DataPortability.BACKUP_SCHEMA);

        DataPortabilityV3.clearMeterData(context);
        DataPortabilityV3.restoreBackup(context, legacy.toByteArray());

        MeterTimeModelStore time = new MeterTimeModelStore(context);
        try {
            assertNull(time.getProfile("M1"));
            assertNull(time.latestAnchor("M1"));
        } finally {
            time.close();
        }
    }

    @Test public void restoreOnPhoneInAnotherZoneKeepsBackedUpMeterZone() throws Exception {
        long now = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli();
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        MeterTimeModelStore source = new MeterTimeModelStore(context);
        try {
            source.assignZoneIfMissing(
                    "M1", "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    now);
        } finally {
            source.close();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityV3.writeBackup(context, out);

        DataPortabilityV3.clearMeterData(context);
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        DataPortabilityV3.restoreBackup(context, out.toByteArray());

        MeterTimeModelStore restored = new MeterTimeModelStore(context);
        try {
            assertEquals("Europe/Berlin", restored.getProfile("M1").zoneId);
        } finally {
            restored.close();
        }
    }

    @Test public void legacySchema2CanStillBeInspectedThroughV3Facade() throws Exception {
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, legacy);

        DataPortability.BackupPreview preview = DataPortabilityV3.inspectBackup(
                new ByteArrayInputStream(legacy.toByteArray()));
        assertEquals("M1", preview.activeMeterId);
    }
}
