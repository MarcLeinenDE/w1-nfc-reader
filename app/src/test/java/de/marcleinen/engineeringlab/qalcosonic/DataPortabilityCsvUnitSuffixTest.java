package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityCsvUnitSuffixTest {
    private static final Pattern CSV_FIELD = Pattern.compile("\\\"([^\\\"]*)\\\"");

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(context);
    }

    @Test public void csvUsesLeadingMeasurementNumberAndNeverUnitDigits() throws Exception {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder();
        values.totalVolume = "10.500 m3";
        values.positiveVolume = "1.73 m3";
        values.reverseVolume = "0 m3";
        values.tariff1Volume = "1,25 m3";
        values.flow = "0.04 m3/h";
        values.maxFlow = "-0.25 m3/h";
        values.minFlow = "0 m3/h";
        values.temperature = "-1.5 C";
        values.externalTemperature = "12.0 C";
        values.batteryPercent = "87 %";
        values.errorFlags = "0x00000000";

        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                ArchiveFamilyPeriod.Family.HOUR,
                "2026-09-06 18:00",
                "2026-09-06T18:01:00Z",
                "test-structure",
                "TEST",
                "VALIDATED",
                values.build());

        try (ArchiveFamilyStore archive = new ArchiveFamilyStore(context)) {
            assertEquals(
                    ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                    archive.upsert("M1", period));
        }
        new MeterLifecycleStore(context).adoptInitialMeter("M1");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeCsv(context, out);
        String csv = out.toString(StandardCharsets.UTF_8);
        List<String> header = header(csv);
        List<String> row = row(csv, "2026-09-06 18:00");

        assertEquals(10.500, number(header, row, "total_m3"), 0.000001);
        assertEquals(1.730, number(header, row, "positive_m3"), 0.000001);
        assertEquals(0.0, number(header, row, "reverse_m3"), 0.000001);
        assertEquals(1.250, number(header, row, "tariff1_m3"), 0.000001);
        assertEquals(0.040, number(header, row, "flow_m3h"), 0.000001);
        assertEquals(-0.250, number(header, row, "max_flow_m3h"), 0.000001);
        assertEquals(0.0, number(header, row, "min_flow_m3h"), 0.000001);
        assertEquals(-1.5, number(header, row, "water_temperature_c"), 0.000001);
        assertEquals(12.0, number(header, row, "external_temperature_c"), 0.000001);
    }

    private static List<String> header(String csv) {
        for (String line : csv.split("\\r?\\n")) {
            List<String> fields = fields(line);
            if (fields.isEmpty()) continue;
            if (fields.get(0).startsWith("\uFEFF")) {
                fields.set(0, fields.get(0).substring(1));
            }
            if (fields.contains("schema_version") && fields.contains("record_type")) return fields;
        }
        throw new AssertionError("missing CSV header");
    }

    private static List<String> row(String csv, String primaryTime) {
        for (String line : csv.split("\\r?\\n")) {
            List<String> fields = fields(line);
            if (fields.size() > 3 && primaryTime.equals(fields.get(3))) return fields;
        }
        throw new AssertionError("missing CSV row " + primaryTime);
    }

    private static List<String> fields(String line) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = CSV_FIELD.matcher(line);
        while (matcher.find()) fields.add(matcher.group(1).replace("\"\"", "\""));
        return fields;
    }

    private static double number(List<String> header, List<String> row, String column) {
        int index = header.indexOf(column);
        if (index < 0 || index >= row.size()) throw new AssertionError("missing CSV column " + column);
        return Double.parseDouble(row.get(index).replace(',', '.'));
    }
}
