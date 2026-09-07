package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityCsvV3Test {
    private static final Pattern CSV_FIELD = Pattern.compile("\\\"([^\\\"]*)\\\"");

    private Context context;
    private TimeZone originalTimeZone;

    @Before public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        context = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(context);
    }

    @After public void tearDown() {
        DataPortability.clearMeterData(context);
        TimeZone.setDefault(originalTimeZone);
    }

    @Test public void schema3SeparatesArchivePeriodFromRetrievalAndMeterClock() throws Exception {
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH,
                            "2026-09-01 00:00", "2026-09-06T18:00:00Z", 100.0)));
            assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH,
                            "2026-10-01 00:00", "2026-10-06T18:00:00Z", 105.0)));
        }
        new MeterLifecycleStore(context).adoptInitialMeter("M1");

        MbusParser.MeterData live = meter("M1", 105.4, "2026-10-06 18:05");
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            store.insertSuccessful(live, utcMs("2026-10-06 18:05"));
        }

        String csv = export();
        List<String> header = header(csv);
        List<String> month = row(csv, header, "record_type", "MONTH", "period_end", "2026-10-01 00:00");
        List<String> liveRow = row(csv, header, "record_type", "LIVE", "meter_time", "2026-10-06 18:05");

        assertEquals("3", value(header, month, "schema_version"));
        assertEquals("2026-09-01 00:00", value(header, month, "period_start"));
        assertEquals("2026-10-01 00:00", value(header, month, "period_end"));
        assertEquals(ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL, value(header, month, "time_basis"));
        assertFalse(value(header, month, "retrieved_at_utc").isEmpty());
        assertEquals("", value(header, month, "meter_time"));
        assertEquals(5.0, number(header, month, "consumption_m3"), 0.000001);
        assertEquals("PREVIOUS_SAME_METER_SAME_GRANULARITY",
                value(header, month, "consumption_reference_rule"));
        assertEquals("MONTH", value(header, month, "consumption_reference_type"));
        assertEquals("2026-09-01 00:00", value(header, month, "consumption_reference_time"));
        assertEquals(100.0, number(header, month, "consumption_reference_total_m3"), 0.000001);

        assertEquals("", value(header, liveRow, "period_start"));
        assertEquals("", value(header, liveRow, "period_end"));
        assertEquals("DEVICE_LOCAL", value(header, liveRow, "time_basis"));
        assertEquals("2026-10-06 18:05", value(header, liveRow, "meter_time"));
        assertFalse(value(header, liveRow, "retrieved_at_utc").isEmpty());
    }

    @Test public void liveUsesNewestEarlierKnownSameMeterAcrossArchiveGranularities() throws Exception {
        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            archive.upsert("M1", period(ArchiveFamilyPeriod.Family.HOUR,
                    "2026-09-06 17:00", "2026-09-06T18:01:00Z", 10.2));
            archive.upsert("M1", period(ArchiveFamilyPeriod.Family.HOUR,
                    "2026-09-06 18:00", "2026-09-06T18:02:00Z", 10.5));
            archive.upsert("OTHER", period(ArchiveFamilyPeriod.Family.HOUR,
                    "2026-09-06 18:04", "2026-09-06T18:04:30Z", 99.0));
        }
        new MeterLifecycleStore(context).adoptInitialMeter("M1");

        MbusParser.MeterData live = meter("M1", 10.8, "2026-09-06 18:05");
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            store.insertSuccessful(live, utcMs("2026-09-06 18:05"));
        }

        String csv = export();
        List<String> header = header(csv);
        List<String> liveRow = row(csv, header, "record_type", "LIVE", "meter_time", "2026-09-06 18:05");
        List<String> hour = row(csv, header, "record_type", "HOUR", "period_end", "2026-09-06 18:00");

        assertEquals(0.3, number(header, liveRow, "consumption_m3"), 0.000001);
        assertEquals("LATEST_STRICTLY_EARLIER_SAME_METER",
                value(header, liveRow, "consumption_reference_rule"));
        assertEquals("HOUR", value(header, liveRow, "consumption_reference_type"));
        assertEquals("2026-09-06 18:00", value(header, liveRow, "consumption_reference_time"));
        assertEquals(10.5, number(header, liveRow, "consumption_reference_total_m3"), 0.000001);

        assertEquals("2026-09-06 17:00", value(header, hour, "period_start"));
        assertEquals(0.3, number(header, hour, "consumption_m3"), 0.000001);
        assertEquals("HOUR", value(header, hour, "consumption_reference_type"));
        assertEquals("2026-09-06 17:00", value(header, hour, "consumption_reference_time"));
    }

    @Test public void productCsvRoutesUseSchema3ExporterOnly() throws Exception {
        String settings = TestSource.read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/SettingsActivity.java");
        String dashboard = TestSource.read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(settings.contains("DataPortabilityCsvV3.writeCsv(this, out)"));
        assertTrue(dashboard.contains("DataPortabilityCsvV3.writeCsv(this, out)"));
        assertFalse(settings.contains("DataPortability.writeCsv(this, out)"));
        assertFalse(dashboard.contains("DataPortability.writeCsv(this, out)"));
    }

    private String export() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortabilityCsvV3.writeCsv(context, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static ArchiveFamilyPeriod period(ArchiveFamilyPeriod.Family family, String boundary,
                                              String retrievedUtc, double totalM3) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder();
        values.totalVolume = String.format(Locale.US, "%.3f m3", totalM3);
        return new ArchiveFamilyPeriod(family, boundary, retrievedUtc,
                "test-structure-" + family.name() + "-" + boundary,
                "TEST", "VALIDATED", values.build());
    }

    private static MbusParser.MeterData meter(String meterId, double totalM3, String meterTime) {
        MbusParser.MeterData meter = new MbusParser.MeterData();
        meter.meterId = meterId;
        meter.waterUsageM3 = totalM3;
        meter.timepoint = meterTime;
        meter.batteryPercent = 90;
        return meter;
    }

    private static long utcMs(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date parsed = format.parse(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed.getTime();
    }

    private static List<String> header(String csv) {
        for (String line : csv.split("\\r?\\n")) {
            List<String> fields = fields(line);
            if (fields.isEmpty()) continue;
            if (fields.get(0).startsWith("\uFEFF")) fields.set(0, fields.get(0).substring(1));
            if (fields.contains("schema_version") && fields.contains("record_type")) return fields;
        }
        throw new AssertionError("missing CSV header");
    }

    private static List<String> row(String csv, List<String> header,
                                    String key1, String value1, String key2, String value2) {
        int index1 = header.indexOf(key1);
        int index2 = header.indexOf(key2);
        if (index1 < 0 || index2 < 0) throw new AssertionError("missing selector column");
        for (String line : csv.split("\\r?\\n")) {
            List<String> fields = fields(line);
            if (fields.size() != header.size()) continue;
            if (value1.equals(fields.get(index1)) && value2.equals(fields.get(index2))) return fields;
        }
        throw new AssertionError("missing CSV row " + key1 + "=" + value1 + ", " + key2 + "=" + value2);
    }

    private static List<String> fields(String line) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = CSV_FIELD.matcher(line);
        while (matcher.find()) fields.add(matcher.group(1).replace("\"\"", "\""));
        return fields;
    }

    private static String value(List<String> header, List<String> row, String column) {
        int index = header.indexOf(column);
        if (index < 0 || index >= row.size()) throw new AssertionError("missing CSV column " + column);
        return row.get(index);
    }

    private static double number(List<String> header, List<String> row, String column) {
        return Double.parseDouble(value(header, row, column).replace(',', '.'));
    }

    /** Small source reader shared only inside this regression class to avoid Android reflection. */
    private static final class TestSource {
        static String read(String path) throws Exception {
            java.nio.file.Path fromRoot = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.exists(fromRoot)) {
                return new String(java.nio.file.Files.readAllBytes(fromRoot), StandardCharsets.UTF_8);
            }
            java.nio.file.Path fromModule = java.nio.file.Paths.get(path.replaceFirst("^app/", ""));
            if (java.nio.file.Files.exists(fromModule)) {
                return new String(java.nio.file.Files.readAllBytes(fromModule), StandardCharsets.UTF_8);
            }
            throw new AssertionError("file not found: " + path);
        }
    }
}
