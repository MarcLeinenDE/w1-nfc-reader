package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @Test public void schema3ArchiveEnvelopePromotesV2OnTimeWithoutInventingTypeF() throws Exception {
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            ArchiveNormalizedValues.Builder valuesBuilder = ArchiveNormalizedValues.builder()
                    .totalVolume("10.000 m3");
            valuesBuilder.onTime = "80734200 s";
            ArchiveNormalizedValues values = valuesBuilder.build();
            ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                    ArchiveFamilyPeriod.Family.HOUR,
                    "2026-09-08 04:00",
                    "2026-09-08T04:01:00Z",
                    "FP-HOUR",
                    "NFC_ARCHIVE",
                    "COMPLETE",
                    values);
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", period));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityV3.writeBackup(context, out);
        byte[] backup = out.toByteArray();
        JSONObject data = dataJson(backup);
        JSONArray periods = data.getJSONArray("archive_periods_v2");

        assertEquals(1, periods.length());
        JSONObject row = periods.getJSONObject(0);
        assertEquals("OT:80734200", row.getString("occurrence_key"));
        assertEquals(80_734_200L, row.getLong("on_time_seconds"));
        assertTrue(row.isNull("raw_type_f_hex"));
        assertTrue(row.isNull("type_f_iv"));
        assertTrue(row.isNull("type_f_su"));

        DataPortability.BackupPreview preview = DataPortabilityV3.inspectBackup(
                new ByteArrayInputStream(backup));
        assertEquals(1, preview.archivePeriods);
        assertEquals(1, preview.hourPeriods);
    }

    @Test public void schema3RoundTripPreservesRepeatedRawTimestampAndConflictParentOccurrence() throws Exception {
        new MeterLifecycleStore(context).adoptInitialMeter("M1");
        String repeated = "2026-10-25 02:00";
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            ArchiveFamilyPeriod first = archivePeriod(repeated, "10.000 m3", "100000 s", "shape-a");
            ArchiveFamilyPeriod second = archivePeriod(repeated, "20.000 m3", "103600 s", "shape-b");
            ArchiveFamilyPeriod changedFirst = archivePeriod(repeated, "10.100 m3", "100000 s", "shape-a");
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, archive.upsert("M1", first));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, archive.upsert("M1", second));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED,
                    archive.upsert("M1", changedFirst));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityV3.writeBackup(context, out);
        byte[] backup = out.toByteArray();
        JSONObject data = dataJson(backup);
        JSONArray portablePeriods = data.getJSONArray("archive_periods_v2");
        assertEquals(2, portablePeriods.length());
        Set<String> portableOccurrences = new HashSet<>();
        for (int i = 0; i < portablePeriods.length(); i++) {
            portableOccurrences.add(portablePeriods.getJSONObject(i).getString("occurrence_key"));
        }
        assertTrue(portableOccurrences.contains("OT:100000"));
        assertTrue(portableOccurrences.contains("OT:103600"));
        assertEquals(1, data.getJSONArray("archive_conflicts_v2").length());
        assertEquals("OT:100000",
                data.getJSONArray("archive_conflicts_v2").getJSONObject(0).getString("occurrence_key"));

        DataPortabilityV3.clearMeterData(context);
        DataPortabilityV3.restoreBackup(context, backup);

        try (ArchiveFamilyStore restored = new ArchiveFamilyStore(context)) {
            assertEquals(2, restored.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR).size());
            assertEquals(1, restored.conflictCount("M1", ArchiveFamilyPeriod.Family.HOUR, repeated));
            try (Cursor c = restored.getReadableDatabase().rawQuery(
                    "SELECT p.occurrence_key FROM " + ArchiveFamilyStore.TABLE_CONFLICTS + " c JOIN "
                            + ArchiveFamilyStore.TABLE_PERIODS + " p ON p.id=c.archive_period_id",
                    null)) {
                assertTrue(c.moveToFirst());
                assertEquals("OT:100000", c.getString(0));
            }
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

    private static ArchiveFamilyPeriod archivePeriod(String timestamp, String total, String onTime, String shape) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder().totalVolume(total);
        values.onTime = onTime;
        return new ArchiveFamilyPeriod(
                ArchiveFamilyPeriod.Family.HOUR,
                timestamp,
                "2026-10-25T03:30:00Z",
                shape,
                "NFC_ARCHIVE",
                "COMPLETE",
                values.build());
    }

    private static JSONObject dataJson(byte[] backup) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(backup), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                if (!"data.json".equals(entry.getName())) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zip.read(buffer)) != -1) out.write(buffer, 0, n);
                return new JSONObject(out.toString(StandardCharsets.UTF_8));
            }
        }
        throw new AssertionError("data.json missing");
    }
}
