package de.marcleinen.engineeringlab.qalcosonic;

import android.app.LocaleManager;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityTest {
    private static final Pattern CSV_FIELD = Pattern.compile("\\\"([^\\\"]*)\\\"");

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(context);
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void backupRoundTripRestoresReadingsArchiveReplacementChainAndFamilyState() throws Exception {
        MbusParser.MeterData first = meter("A", 203.500, 88, "2026-08-31 20:08");
        MbusParser.MeterData second = meter("B", 0.250, 100, "2026-09-01 09:00");
        try (MeterHistoryStore live = new MeterHistoryStore(context)) {
            live.insertSuccessful(first, 1_000_000L);
            live.insertSuccessful(second, 2_000_000L);
        }
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("A", archivePeriod(ArchiveFamilyPeriod.Family.MONTH,
                            "2026-08-01 00:00", "2026-08-31T19:00:00Z", "196.668")));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("B", archivePeriod(ArchiveFamilyPeriod.Family.HOUR,
                            "2026-09-01 08:00", "2026-09-01T09:01:00Z", "0.200")));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("B", archivePeriod(ArchiveFamilyPeriod.Family.DAY,
                            "2026-09-01 00:00", "2026-09-01T09:02:00Z", "0.250")));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("B", archivePeriod(ArchiveFamilyPeriod.Family.MONTH,
                            "2026-09-01 00:00", "2026-09-01T09:03:00Z", "0.250")));
        }
        MeterLifecycleStore lifecycle = new MeterLifecycleStore(context);
        lifecycle.adoptInitialMeter("A");
        lifecycle.confirmReplacement("A", "B", 2_000_000L, 2_000_000L, 0.250);
        new LiveReadMetadataStore(context).record(second, 2_000_000L);

        ArchiveFamilySyncStateStore familySync = new ArchiveFamilySyncStateStore(context);
        familySync.recordAttempt(
                "B", ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                3_000_000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2026-08-25 00:00", "2026-09-01 08:00",
                8, 8, 0, 0);
        familySync.recordAttempt(
                "B", ArchiveFamilyPeriod.Family.DAY,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                4_000_000L,
                ArchiveFamilySyncState.AttemptOutcome.PARTIAL,
                ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST,
                false,
                "2026-08-31 00:00", "2026-09-01 00:00",
                2, 2, 0, 0);
        familySync.recordAttempt(
                "B", ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                5_000_000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2026-08-01 00:00", "2026-09-01 00:00",
                2, 2, 0, 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0);
        assertEquals(2, DataPortability.BACKUP_SCHEMA);

        DataPortability.BackupPreview preview = DataPortability.inspectBackup(
                new ByteArrayInputStream(bytes));
        assertEquals("B", preview.activeMeterId);
        assertEquals(2, preview.liveReadings);
        assertEquals(4, preview.archivePeriods);
        assertEquals(1, preview.replacementTransitions);
        assertEquals(1, preview.hourPeriods);
        assertEquals(1, preview.dayPeriods);
        assertEquals(2, preview.monthPeriods);
        assertEquals(ArchiveFamilySyncState.BaselineState.COMPLETE, preview.hourBaselineState);
        assertEquals(ArchiveFamilySyncState.BaselineState.PARTIAL, preview.dayBaselineState);
        assertEquals(ArchiveFamilySyncState.BaselineState.COMPLETE, preview.monthBaselineState);

        DataPortability.clearMeterData(context);
        assertEquals(null, new MeterLifecycleStore(context).activeMeterId());
        assertEquals(ArchiveFamilySyncState.BaselineState.NEVER_SYNCED,
                new ArchiveFamilySyncStateStore(context)
                        .get("B", ArchiveFamilyPeriod.Family.HOUR).baselineState);

        DataPortability.restoreBackup(context, bytes);

        MeterLifecycleStore restoredLifecycle = new MeterLifecycleStore(context);
        assertEquals("B", restoredLifecycle.activeMeterId());
        assertEquals(List.of("A", "B"), restoredLifecycle.chainMeterIds());
        try (MeterHistoryStore live = new MeterHistoryStore(context);
             ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(1, live.getReadings("A", 0L).size());
            assertEquals(1, live.getReadings("B", 0L).size());
            assertEquals(1, archive.getPeriods("B", ArchiveFamilyPeriod.Family.HOUR).size());
            assertEquals(1, archive.getPeriods("B", ArchiveFamilyPeriod.Family.DAY).size());
            assertEquals(1, archive.getPeriods("B", ArchiveFamilyPeriod.Family.MONTH).size());
            assertEquals("196.668", archive.getPeriods("A", ArchiveFamilyPeriod.Family.MONTH)
                    .get(0).totalVolume);
        }
        assertTrue(new LiveReadMetadataStore(context).get("B").available());

        ArchiveFamilySyncState restoredHour = new ArchiveFamilySyncStateStore(context)
                .get("B", ArchiveFamilyPeriod.Family.HOUR);
        ArchiveFamilySyncState restoredDay = new ArchiveFamilySyncStateStore(context)
                .get("B", ArchiveFamilyPeriod.Family.DAY);
        ArchiveFamilySyncState restoredMonth = new ArchiveFamilySyncStateStore(context)
                .get("B", ArchiveFamilyPeriod.Family.MONTH);
        assertTrue(restoredHour.baselineComplete());
        assertEquals(ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                restoredHour.lastStopReason);
        assertEquals(ArchiveFamilySyncState.BaselineState.PARTIAL, restoredDay.baselineState);
        assertEquals(ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST,
                restoredDay.lastStopReason);
        assertEquals(2, restoredDay.accepted);
        assertTrue(restoredMonth.baselineComplete());
        assertEquals(5_000_000L, restoredMonth.baselineCompletedAtMs);
    }

    @Test
    @Config(sdk = 28)
    public void csvExportDoesNotTreatM3UnitSuffixAsDigit() throws Exception {
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", archivePeriod(ArchiveFamilyPeriod.Family.MONTH,
                            "2024-09-01 00:00", "2026-09-05T19:00:00Z", "0 m3")));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", archivePeriod(ArchiveFamilyPeriod.Family.MONTH,
                            "2024-10-01 00:00", "2026-09-05T19:01:00Z", "1.931 m3")));
        }
        new MeterLifecycleStore(context).adoptInitialMeter("M1");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeCsv(context, out);
        String csv = out.toString(StandardCharsets.UTF_8);

        List<String> september = csvRow(csv, "2024-09-01 00:00");
        List<String> october = csvRow(csv, "2024-10-01 00:00");
        assertEquals(0.0, localizedDouble(september.get(6)), 0.000001);
        assertEquals(1.931, localizedDouble(october.get(6)), 0.000001);
        assertEquals(1.931, localizedDouble(october.get(7)), 0.000001);
    }

    @Test
    @Config(sdk = 33, manifest = Config.NONE)
    public void backupRestoreReconcilesAndroid13ApplicationLocale() throws Exception {
        MbusParser.MeterData meter = meter("A", 10.0, 90, "2026-08-31 20:00");
        try (MeterHistoryStore live = new MeterHistoryStore(context)) {
            live.insertSuccessful(meter, 1_000_000L);
        }
        new MeterLifecycleStore(context).adoptInitialMeter("A");
        UiPreferences.restorePortable(context, UiPreferences.THEME_DARK, "de");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        byte[] backup = out.toByteArray();

        // Simulate a different per-app language already being active on the target phone.
        UiPreferences.restorePortable(context, UiPreferences.THEME_LIGHT, "en");
        DataPortability.clearMeterData(context);
        DataPortability.restoreBackup(context, backup);

        assertEquals(UiPreferences.THEME_DARK, UiPreferences.getTheme(context));
        assertEquals("de", UiPreferences.getSelectedLanguageTag(context));
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        assertNotNull(manager);
        assertEquals("de", manager.getApplicationLocales().get(0).toLanguageTag());
    }

    @Test public void checksumFailureIsRejectedBeforeExistingDataIsTouched() throws Exception {
        MbusParser.MeterData meter = meter("A", 10.0, 90, "2026-08-31 20:00");
        try (MeterHistoryStore live = new MeterHistoryStore(context)) {
            live.insertSuccessful(meter, 1_000_000L);
        }
        new MeterLifecycleStore(context).adoptInitialMeter("A");
        new ArchiveFamilySyncStateStore(context).recordAttempt(
                "A", ArchiveFamilyPeriod.Family.MONTH,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                2_000_000L,
                ArchiveFamilySyncState.AttemptOutcome.COMPLETE,
                ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                true,
                "2026-01-01 00:00", "2026-08-01 00:00",
                8, 8, 0, 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        byte[] corrupt = corruptDataEntryWithoutUpdatingManifest(out.toByteArray());

        boolean failed = false;
        try {
            DataPortability.restoreBackup(context, corrupt);
        } catch (Exception expected) {
            failed = true;
        }
        assertTrue(failed);
        assertEquals("A", new MeterLifecycleStore(context).activeMeterId());
        try (MeterHistoryStore live = new MeterHistoryStore(context)) {
            assertEquals(1, live.getReadings("A", 0L).size());
        }
        assertTrue(new ArchiveFamilySyncStateStore(context)
                .get("A", ArchiveFamilyPeriod.Family.MONTH).baselineComplete());
    }

    private static ArchiveFamilyPeriod archivePeriod(
            ArchiveFamilyPeriod.Family family,
            String timestamp,
            String retrievedAtUtc,
            String total) {
        return new ArchiveFamilyPeriod(family, timestamp, retrievedAtUtc,
                "test-structure", "TEST", "VALIDATED",
                ArchiveNormalizedValues.builder()
                        .totalVolume(total)
                        .batteryPercent("100 %")
                        .errorFlags("0x00000000")
                        .build());
    }

    private static List<String> csvRow(String csv, String primaryTime) {
        for (String line : csv.split("\\r?\\n")) {
            List<String> fields = new ArrayList<>();
            Matcher matcher = CSV_FIELD.matcher(line);
            while (matcher.find()) fields.add(matcher.group(1).replace("\"\"", "\""));
            if (fields.size() > 7 && primaryTime.equals(fields.get(3))) return fields;
        }
        throw new AssertionError("missing CSV row " + primaryTime);
    }

    private static double localizedDouble(String value) {
        return Double.parseDouble(value.replace(',', '.'));
    }

    private static byte[] corruptDataEntryWithoutUpdatingManifest(byte[] backup) throws Exception {
        ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(backup), StandardCharsets.UTF_8);
             ZipOutputStream output = new ZipOutputStream(rewritten, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                int n;
                while ((n = input.read(buffer)) != -1) entryBytes.write(buffer, 0, n);
                byte[] payload = entryBytes.toByteArray();
                if ("data.json".equals(entry.getName())) {
                    byte[] suffix = " ".getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream changed = new ByteArrayOutputStream();
                    changed.write(payload);
                    changed.write(suffix);
                    payload = changed.toByteArray();
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(payload);
                output.closeEntry();
            }
        }
        return rewritten.toByteArray();
    }

    private static MbusParser.MeterData meter(String id, double total, int battery, String meterTime) {
        MbusParser.MeterData data = new MbusParser.MeterData();
        data.meterId = id;
        data.waterUsageM3 = total;
        data.batteryPercent = battery;
        data.timepoint = meterTime;
        return data;
    }
}
