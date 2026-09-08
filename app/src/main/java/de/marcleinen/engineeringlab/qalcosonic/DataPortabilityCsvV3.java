package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * CSV schema 3 export with explicit observation/period semantics.
 *
 * <p>Archive logger timestamps are stored period-end boundaries. CSV v3 therefore exports both
 * {@code period_start} and {@code period_end}; Live and local lifecycle events do not invent an
 * archive period and leave both fields empty. Retrieval timestamps are kept separate from meter
 * logger/meter-clock timestamps.</p>
 */
final class DataPortabilityCsvV3 {
    static final int CSV_SCHEMA = 3;
    private static final double DELTA_EPSILON_M3 = 0.0005;
    private static final String RULE_ARCHIVE = "PREVIOUS_SAME_METER_SAME_GRANULARITY";
    private static final String RULE_LIVE = "PREVIOUS_SAME_METER_SAME_GRANULARITY";

    private DataPortabilityCsvV3() { }

    static void writeCsv(Context context, OutputStream output) throws IOException {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        char delimiter = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? ';' : ',';
        NumberFormat numbers = NumberFormat.getNumberInstance(locale);
        numbers.setGroupingUsed(false);
        numbers.setMinimumFractionDigits(0);
        numbers.setMaximumFractionDigits(3);
        DateFormat deviceDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);

        List<ExportRow> rows = exportRows(context);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        appendCsv(csv, delimiter,
                "schema_version", "meter_id", "record_type",
                "period_start", "period_end", "time_basis",
                "retrieved_at", "retrieved_at_utc", "meter_time",
                "total_m3", "consumption_m3",
                "consumption_reference_rule", "consumption_reference_type",
                "consumption_reference_time", "consumption_reference_total_m3",
                "positive_m3", "reverse_m3", "tariff1_m3",
                "flow_m3h", "max_flow_m3h", "max_flow_at", "min_flow_m3h", "min_flow_at",
                "water_temperature_c", "external_temperature_c",
                "max_temperature_c", "max_temperature_at", "min_temperature_c", "min_temperature_at",
                "battery_percent", "status", "raw_status", "on_time", "operating_time",
                "baseline_state", "observation_count", "identical_content_confirmations",
                "revision_count", "conflict_flags", "source", "validation", "provenance");
        for (ExportRow row : rows) {
            appendCsv(csv, delimiter,
                    Integer.toString(CSV_SCHEMA),
                    row.meterId,
                    row.type,
                    row.periodStart,
                    row.periodEnd,
                    row.timeBasis,
                    row.retrievedAtMs <= 0L ? "" : deviceDate.format(new Date(row.retrievedAtMs)),
                    row.retrievedAtMs <= 0L ? "" : utcMillis(row.retrievedAtMs),
                    row.meterTime,
                    number(numbers, row.totalM3),
                    number(numbers, row.consumptionM3),
                    row.consumptionReferenceRule,
                    row.consumptionReferenceType,
                    row.consumptionReferenceTime,
                    number(numbers, row.consumptionReferenceTotalM3),
                    number(numbers, row.positiveM3),
                    number(numbers, row.reverseM3),
                    number(numbers, row.tariff1M3),
                    number(numbers, row.flowM3h),
                    number(numbers, row.maxFlowM3h),
                    row.maxFlowAt,
                    number(numbers, row.minFlowM3h),
                    row.minFlowAt,
                    number(numbers, row.waterTemperatureC),
                    number(numbers, row.externalTemperatureC),
                    number(numbers, row.maxTemperatureC),
                    row.maxTemperatureAt,
                    number(numbers, row.minTemperatureC),
                    row.minTemperatureAt,
                    integer(row.batteryPercent),
                    row.status,
                    row.rawStatus,
                    row.onTime,
                    row.operatingTime,
                    row.baselineState,
                    integer(row.observationCount),
                    integer(row.identicalContentConfirmations),
                    integer(row.revisionCount),
                    integer(row.conflictFlags),
                    row.source,
                    row.validation,
                    row.provenance);
        }
        output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static List<ExportRow> exportRows(Context context) {
        List<ExportRow> out = new ArrayList<>();
        List<WaterUsageAnalytics.Point> points = new ArrayList<>();
        Map<String, ExportRow> byIdentity = new HashMap<>();
        ArchiveFamilySyncStateStore familySyncStore = new ArchiveFamilySyncStateStore(context);

        try (MeterHistoryStore liveStore = new MeterHistoryStore(context);
             ArchiveFamilyStore archiveStore = new ArchiveFamilyStore(context)) {
            Set<String> meters = new LinkedHashSet<>(liveStore.getMeterIds());
            meters.addAll(new MeterLifecycleStore(context).chainMeterIds());
            SQLiteDatabase archiveDb = archiveStore.getReadableDatabase();
            try (Cursor cursor = archiveDb.rawQuery(
                    "SELECT DISTINCT meter_id FROM " + ArchiveFamilyStore.TABLE_PERIODS, null)) {
                while (cursor.moveToNext()) meters.add(cursor.getString(0));
            }

            for (String meter : meters) {
                if (meter == null || meter.trim().isEmpty()) continue;
                for (MeterHistoryStore.Reading reading : liveStore.getReadings(meter, 0L)) {
                    String timestamp = HistoryTimePresentation.localMinute(reading.readAtMs);
                    long sort = HistoryTimePresentation.floatingSortMs(timestamp);
                    String identity = "LIVE|" + reading.id;
                    WaterUsageAnalytics.Point point = new WaterUsageAnalytics.Point(
                            identity, meter, timestamp, sort,
                            HistorySemanticTimeline.Granularity.LIVE, reading.totalM3);
                    points.add(point);

                    ExportRow row = new ExportRow();
                    row.identity = identity;
                    row.meterId = meter;
                    row.type = "LIVE";
                    row.timeBasis = "DEVICE_LOCAL";
                    row.retrievedAtMs = reading.readAtMs;
                    row.sortMs = sort;
                    row.meterTime = emptyIfNull(reading.meterTime);
                    row.totalM3 = reading.totalM3;
                    row.positiveM3 = reading.positiveM3;
                    row.reverseM3 = reading.negativeM3;
                    row.flowM3h = reading.flowM3h;
                    row.waterTemperatureC = reading.waterTemperatureC;
                    row.externalTemperatureC = reading.ambientTemperatureC;
                    row.batteryPercent = reading.batteryPercent;
                    row.status = MeterStatusPresentation.localizedAlarmCodes(context, reading.alarmCodes);
                    row.source = "LIVE";
                    row.provenance = context.getString(R.string.m3_provenance_live);
                    byIdentity.put(identity, row);
                    out.add(row);
                }

                for (ArchiveFamilyStore.StoredPeriod period : archiveStore.getPeriods(meter, null)) {
                    Double total = parseMeasurement(period.totalVolume);
                    long sort = HistoryTimePresentation.floatingSortMs(period.loggerTimestamp);
                    if (total == null || sort <= 0L) continue;
                    HistorySemanticTimeline.Granularity granularity = granularity(period.family);
                    String identity = period.family.name() + "|" + meter + "|" + period.loggerTimestamp;
                    WaterUsageAnalytics.Point point = new WaterUsageAnalytics.Point(
                            identity, meter, period.loggerTimestamp, sort, granularity, total);
                    points.add(point);

                    MeterStatusPresentation.Historical status =
                            MeterStatusPresentation.historical(period.errorFlags);
                    ArchiveFamilySyncState familyState = familySyncStore.get(meter, period.family);
                    ExportRow row = new ExportRow();
                    row.identity = identity;
                    row.meterId = meter;
                    row.type = period.family.name();
                    row.periodStart = emptyIfNull(
                            HistoryTimePresentation.periodStartTimestamp(period.loggerTimestamp, granularity));
                    row.periodEnd = emptyIfNull(period.loggerTimestamp);
                    row.timeBasis = emptyIfNull(period.loggerTimeBasis);
                    row.retrievedAtMs = period.retrievedAtMs;
                    row.sortMs = sort;
                    row.totalM3 = total;
                    row.positiveM3 = parseMeasurement(period.positiveVolume);
                    row.reverseM3 = parseMeasurement(period.reverseVolume);
                    row.tariff1M3 = parseMeasurement(period.tariff1Volume);
                    row.flowM3h = parseMeasurement(period.flow);
                    row.maxFlowM3h = parseMeasurement(period.maxFlow);
                    row.maxFlowAt = emptyIfNull(period.maxFlowAt);
                    row.minFlowM3h = parseMeasurement(period.minFlow);
                    row.minFlowAt = emptyIfNull(period.minFlowAt);
                    row.waterTemperatureC = parseMeasurement(period.temperature);
                    row.externalTemperatureC = parseMeasurement(period.externalTemperature);
                    row.maxTemperatureC = parseMeasurement(period.maxTemperature);
                    row.maxTemperatureAt = emptyIfNull(period.maxTemperatureAt);
                    row.minTemperatureC = parseMeasurement(period.minTemperature);
                    row.minTemperatureAt = emptyIfNull(period.minTemperatureAt);
                    row.batteryPercent = parseInteger(period.batteryPercent);
                    row.status = status.hasAnyStatus() ? status.summary(context) : "";
                    row.rawStatus = status.hasAnyStatus() ? status.raw : "";
                    row.onTime = emptyIfNull(period.onTime);
                    row.operatingTime = emptyIfNull(period.operatingTime);
                    row.baselineState = familyState.baselineState.name();
                    row.observationCount = period.observationCount;
                    row.identicalContentConfirmations = period.identicalContentConfirmations;
                    row.revisionCount = period.revisionCount;
                    row.conflictFlags = period.conflictFlags;
                    row.source = emptyIfNull(period.source);
                    row.validation = emptyIfNull(period.validation);
                    row.provenance = context.getString(R.string.m3_provenance_archive);
                    byIdentity.put(identity, row);
                    out.add(row);
                }
            }
        }

        // Archive consumption remains same meter + same granularity. This also initializes Live
        // rows with their same-Live predecessor; Live references below intentionally keep that
        // same-granularity contract even when newer archive observations exist.
        for (WaterUsageAnalytics.HistoryDelta delta : WaterUsageAnalytics.historyNewestFirst(points)) {
            ExportRow row = byIdentity.get(delta.point.identity);
            if (row == null || delta.point.granularity == HistorySemanticTimeline.Granularity.LIVE) continue;
            applyReference(row, delta.consumptionSincePreviousM3, delta.previousPoint, RULE_ARCHIVE);
        }
        applyLiveReferences(points, byIdentity);

        for (MeterLifecycleStore.Transition transition : new MeterLifecycleStore(context).transitions()) {
            ExportRow row = new ExportRow();
            row.meterId = transition.successorMeterId;
            row.type = "METER_REPLACEMENT";
            row.timeBasis = "DEVICE_LOCAL";
            row.retrievedAtMs = transition.confirmedAtMs;
            row.sortMs = HistoryTimePresentation.floatingSortMs(
                    HistoryTimePresentation.localMinute(transition.confirmedAtMs));
            row.status = context.getString(R.string.m3_meter_replacement_event);
            row.source = "LOCAL_EVENT";
            row.provenance = transition.predecessorMeterId + " -> " + transition.successorMeterId;
            out.add(row);
        }

        out.sort(Comparator.comparingLong((ExportRow row) -> row.sortMs)
                .thenComparing(row -> emptyIfNull(row.meterId))
                .thenComparing(row -> emptyIfNull(row.type))
                .thenComparing(row -> emptyIfNull(row.identity)));
        return out;
    }

    private static void applyLiveReferences(
            List<WaterUsageAnalytics.Point> input,
            Map<String, ExportRow> byIdentity) {
        List<WaterUsageAnalytics.Point> points = new ArrayList<>(input);
        points.sort(Comparator.comparingLong((WaterUsageAnalytics.Point point) -> point.sortMs)
                .thenComparing(point -> point.identity));
        Map<String, WaterUsageAnalytics.Point> latestByMeter = new HashMap<>();
        int index = 0;
        while (index < points.size()) {
            long timestamp = points.get(index).sortMs;
            int end = index + 1;
            while (end < points.size() && points.get(end).sortMs == timestamp) end++;

            for (int i = index; i < end; i++) {
                WaterUsageAnalytics.Point current = points.get(i);
                if (current.granularity != HistorySemanticTimeline.Granularity.LIVE) continue;
                WaterUsageAnalytics.Point previous = latestByMeter.get(meterKey(current));
                Double delta = validDelta(previous, current);
                ExportRow row = byIdentity.get(current.identity);
                if (row != null) applyReference(row, delta, delta == null ? null : previous, RULE_LIVE);
            }

            Map<String, WaterUsageAnalytics.Point> bestAtTimestamp = new HashMap<>();
            for (int i = index; i < end; i++) {
                WaterUsageAnalytics.Point candidate = points.get(i);
                if (!supportedLiveReference(candidate.granularity)) continue;
                String meter = meterKey(candidate);
                WaterUsageAnalytics.Point existing = bestAtTimestamp.get(meter);
                if (existing == null || preferredLiveReference(candidate, existing)) {
                    bestAtTimestamp.put(meter, candidate);
                }
            }
            latestByMeter.putAll(bestAtTimestamp);
            index = end;
        }
    }

    private static void applyReference(
            ExportRow row,
            Double delta,
            WaterUsageAnalytics.Point previous,
            String rule) {
        row.consumptionM3 = delta;
        row.consumptionReferenceRule = "";
        row.consumptionReferenceType = "";
        row.consumptionReferenceTime = "";
        row.consumptionReferenceTotalM3 = null;
        if (delta == null || previous == null) return;
        row.consumptionReferenceRule = rule;
        row.consumptionReferenceType = previous.granularity == null
                ? "" : previous.granularity.name();
        row.consumptionReferenceTime = emptyIfNull(previous.timestamp);
        row.consumptionReferenceTotalM3 = previous.totalM3;
    }

    private static Double validDelta(
            WaterUsageAnalytics.Point previous,
            WaterUsageAnalytics.Point current) {
        if (previous == null || current == null || current.sortMs <= previous.sortMs) return null;
        if (!meterKey(previous).equals(meterKey(current))) return null;
        double delta = current.totalM3 - previous.totalM3;
        if (delta < -DELTA_EPSILON_M3) return null;
        return Math.max(0.0, delta);
    }

    private static boolean preferredLiveReference(
            WaterUsageAnalytics.Point candidate,
            WaterUsageAnalytics.Point existing) {
        int candidatePriority = referencePriority(candidate.granularity);
        int existingPriority = referencePriority(existing.granularity);
        if (candidatePriority != existingPriority) return candidatePriority > existingPriority;
        return candidate.identity.compareTo(existing.identity) > 0;
    }

    private static boolean supportedLiveReference(HistorySemanticTimeline.Granularity granularity) {
        return granularity == HistorySemanticTimeline.Granularity.LIVE;
    }

    private static int referencePriority(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.LIVE) return 4;
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return 3;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return 2;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return 1;
        return 0;
    }

    private static String meterKey(WaterUsageAnalytics.Point point) {
        if (point == null || point.meterId == null || point.meterId.trim().isEmpty()) {
            return "<unknown-meter>";
        }
        return point.meterId.trim();
    }

    private static void appendCsv(StringBuilder out, char delimiter, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(delimiter);
            String value = values[i] == null ? "" : values[i];
            out.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }

    private static Double parseMeasurement(String value) {
        String number = ArchiveFamilyStore.measurementNumber(value);
        if (number == null || number.isEmpty()) return null;
        try { return Double.parseDouble(number); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Integer parseInteger(String value) {
        Double parsed = parseMeasurement(value);
        return parsed == null ? null : (int) Math.round(parsed);
    }

    private static String number(NumberFormat numbers, Double value) {
        return value == null ? "" : numbers.format(value);
    }

    private static String integer(Integer value) {
        return value == null ? "" : Integer.toString(value);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String utcMillis(long ms) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(ms));
    }

    private static HistorySemanticTimeline.Granularity granularity(
            ArchiveFamilyPeriod.Family family) {
        switch (family) {
            case HOUR: return HistorySemanticTimeline.Granularity.HOUR;
            case DAY: return HistorySemanticTimeline.Granularity.DAY;
            case YEAR: return HistorySemanticTimeline.Granularity.YEAR;
            case MONTH:
            default: return HistorySemanticTimeline.Granularity.MONTH;
        }
    }

    private static final class ExportRow {
        String identity = "";
        String meterId = "";
        String type = "";
        String periodStart = "";
        String periodEnd = "";
        String timeBasis = "";
        long retrievedAtMs;
        long sortMs;
        String meterTime = "";
        Double totalM3;
        Double consumptionM3;
        String consumptionReferenceRule = "";
        String consumptionReferenceType = "";
        String consumptionReferenceTime = "";
        Double consumptionReferenceTotalM3;
        Double positiveM3;
        Double reverseM3;
        Double tariff1M3;
        Double flowM3h;
        Double maxFlowM3h;
        String maxFlowAt = "";
        Double minFlowM3h;
        String minFlowAt = "";
        Double waterTemperatureC;
        Double externalTemperatureC;
        Double maxTemperatureC;
        String maxTemperatureAt = "";
        Double minTemperatureC;
        String minTemperatureAt = "";
        Integer batteryPercent;
        String status = "";
        String rawStatus = "";
        String onTime = "";
        String operatingTime = "";
        String baselineState = "";
        Integer observationCount;
        Integer identicalContentConfirmations;
        Integer revisionCount;
        Integer conflictFlags;
        String source = "";
        String validation = "";
        String provenance = "";
    }
}
