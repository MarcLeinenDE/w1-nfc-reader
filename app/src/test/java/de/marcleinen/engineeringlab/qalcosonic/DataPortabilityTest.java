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

    @Test public void backupRoundTripRestoresReadingsArchiveAndReplacementChain() throws Exception {
        MbusParser.MeterData first = meter("A", 203.500, 88, "2026-08-31 20:08");
        MbusParser.MeterData second = meter("B", 0.250, 100, "2026-09-01 09:00");
        try (MeterHistoryStore live = new MeterHistoryStore(context)) {
            live.insertSuccessful(first, 1_000_000L);
            live.insertSuccessful(second, 2_000_000L);
        }
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                    ArchiveFamilyPeriod.Family.MONTH,
                    "2026-08-01 00:00",
                    "2026-08-31T19:00:00Z",
                    "test-structure",
                    "TEST",
                    "VALIDATED",
                    ArchiveNormalizedValues.builder()
                            .totalVolume("196.668")
                            .batteryPercent("89")
                            .errorFlags("0x00000000")
                            .build());
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("A", period));
        }
        MeterLifecycleStore lifecycle = new MeterLifecycleStore(context);
        lifecycle.adoptInitialMeter("A");
        lifecycle.confirmReplacement("A", "B", 2_000_000L, 2_000_000L, 0.250);
        new LiveReadMetadataStore(context).record(second, 2_000_000L);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0);

        DataPortability.BackupPreview preview = DataPortability.inspectBackup(
                new ByteArrayInputStream(bytes));
        assertEquals("B", preview.activeMeterId);
        assertEquals(2, preview.liveReadings);
        assertEquals(1, preview.archivePeriods);
        assertEquals(1, preview.replacementTransitions);

        DataPortability.clearMeterData(context);
        assertEquals(null, new MeterLifecycleStore(context).activeMeterId());

        DataPortability.restoreBackup(context, bytes);

        MeterLifecycleStore restoredLifecycle = new MeterLifecycleStore(context);
        assertEquals("B", restoredLifecycle.activeMeterId());
        assertEquals(List.of("A", "B"), restoredLifecycle.chainMeterIds());
        try (MeterHistoryStore live = new MeterHistoryStore(context);
             ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(1, live.getReadings("A", 0L).size());
            assertEquals(1, live.getReadings("B", 0L).size());
            assertEquals(1, archive.getPeriods("A", ArchiveFamilyPeriod.Family.MONTH).size());
            assertEquals("196.668", archive.getPeriods("A", ArchiveFamilyPeriod.Family.MONTH)
                    .get(0).totalVolume);
        }
        assertTrue(new LiveReadMetadataStore(context).get("B").available());
    }

    @Test
    @Config(sdk = 28)
    public void csvExportDoesNotTreatM3UnitSuffixAsDigit() throws Exception {
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", archivePeriod("2024-09-01 00:00",
                            "2026-09-05T19:00:00Z", "0 m3")));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", archivePeriod("2024-10-01 00:00",
                            "2026-09-05T19:01:00Z", "1.931 m3")));
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
    }

    private static ArchiveFamilyPeriod archivePeriod(String timestamp, String retrievedAtUtc, String total) {
        return new ArchiveFamilyPeriod(ArchiveFamilyPeriod.Family.MONTH, timestamp, retrievedAtUtc,
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
