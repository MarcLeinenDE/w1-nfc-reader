package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Normalized, UI/history-oriented view of one decoded meter archive period.
 *
 * <p>This class deliberately contains no NFC/M-Bus I/O. It is the bridge between the low-level
 * archive decoder and future History/diagram/ESPHome integrations. Values are kept in the same
 * canonical text representation produced by the decoder until typed persistence is introduced.</p>
 */
final class ArchivePeriodSnapshot {
    enum PeriodType { HOUR, DAY, MONTH, YEAR, UNKNOWN }

    final PeriodType periodType;
    final String loggerDateTime;
    final String retrievedAtUtc;
    final String totalVolume;
    final String positiveVolume;
    final String reverseVolume;
    final String tariff1Volume;
    final String maxFlow;
    final String maxFlowAt;
    final String minFlow;
    final String minFlowAt;
    final String flow;
    final String maxTemperature;
    final String maxTemperatureAt;
    final String minTemperature;
    final String minTemperatureAt;
    final String temperature;
    final String externalTemperature;
    final String batteryPercent;
    final String errorFlags;
    final String onTime;
    final String operatingTime;
    final List<String> unmodeledSemantics;

    private ArchivePeriodSnapshot(Builder b) {
        periodType = b.periodType;
        loggerDateTime = b.loggerDateTime;
        retrievedAtUtc = b.retrievedAtUtc;
        totalVolume = b.totalVolume;
        positiveVolume = b.positiveVolume;
        reverseVolume = b.reverseVolume;
        tariff1Volume = b.tariff1Volume;
        maxFlow = b.maxFlow;
        maxFlowAt = b.maxFlowAt;
        minFlow = b.minFlow;
        minFlowAt = b.minFlowAt;
        flow = b.flow;
        maxTemperature = b.maxTemperature;
        maxTemperatureAt = b.maxTemperatureAt;
        minTemperature = b.minTemperature;
        minTemperatureAt = b.minTemperatureAt;
        temperature = b.temperature;
        externalTemperature = b.externalTemperature;
        batteryPercent = b.batteryPercent;
        errorFlags = b.errorFlags;
        onTime = b.onTime;
        operatingTime = b.operatingTime;
        unmodeledSemantics = Collections.unmodifiableList(new ArrayList<>(b.unmodeledSemantics));
    }

    static ArchivePeriodSnapshot fromInspection(
            PeriodType periodType,
            ArchiveRecordInspector.Inspection inspection) {
        return fromInspection(periodType, inspection, null);
    }

    static ArchivePeriodSnapshot fromInspection(
            PeriodType periodType,
            ArchiveRecordInspector.Inspection inspection,
            String retrievedAtUtc) {
        Builder b = new Builder(periodType == null ? PeriodType.UNKNOWN : periodType);
        b.retrievedAtUtc = retrievedAtUtc;
        if (inspection == null || !inspection.parseComplete) return new ArchivePeriodSnapshot(b);

        for (ArchiveRecordInspector.Record record : inspection.records) {
            if (record == null || record.identifierLike || record.value == null) continue;
            String semantic = record.semantic;
            String function = record.function;

            // Backward-compatible normalization for 0.7.9 inspections. The real-device capture
            // proved that BB 6D / D9 6D are Type-F extremum timestamps, while 0.7.9 had already
            // scaled those four raw bytes as numeric flow/temperature values. Once the low-level
            // inspector emits the corrected semantic this branch is no longer needed, but keeping
            // it makes imported 0.7.9 evidence normalize correctly too.
            String legacyExtremumTime = legacyExtremumDateTime(record);
            if (legacyExtremumTime != null) {
                int base = record.vif & 0x7F;
                if (base >= 0x38 && base <= 0x3F) {
                    if ("MAXIMUM".equals(function)) b.maxFlowAt = legacyExtremumTime;
                    else if ("MINIMUM".equals(function)) b.minFlowAt = legacyExtremumTime;
                } else if (base >= 0x58 && base <= 0x5F) {
                    if ("MAXIMUM".equals(function)) b.maxTemperatureAt = legacyExtremumTime;
                    else if ("MINIMUM".equals(function)) b.minTemperatureAt = legacyExtremumTime;
                }
                continue;
            }

            if ("TIME_POINT_DATE_TIME".equals(semantic) && b.loggerDateTime == null) {
                b.loggerDateTime = record.value;
            } else if ("VOLUME".equals(semantic) && "INSTANTANEOUS".equals(function)) {
                if (record.tariff == 0 && b.totalVolume == null) b.totalVolume = record.value;
                else if (record.tariff == 1 && b.tariff1Volume == null) b.tariff1Volume = record.value;
            } else if ("VOLUME_POSITIVE".equals(semantic)) {
                b.positiveVolume = record.value;
            } else if ("VOLUME_NEGATIVE".equals(semantic)) {
                b.reverseVolume = record.value;
            } else if ("VOLUME_FLOW".equals(semantic)) {
                if ("MAXIMUM".equals(function)) b.maxFlow = record.value;
                else if ("MINIMUM".equals(function)) b.minFlow = record.value;
                else if ("INSTANTANEOUS".equals(function)) b.flow = record.value;
            } else if ("VOLUME_FLOW_EXTREMUM_DATE_TIME".equals(semantic)) {
                if ("MAXIMUM".equals(function)) b.maxFlowAt = record.value;
                else if ("MINIMUM".equals(function)) b.minFlowAt = record.value;
            } else if ("FLOW_TEMPERATURE".equals(semantic)) {
                if ("MAXIMUM".equals(function)) b.maxTemperature = record.value;
                else if ("MINIMUM".equals(function)) b.minTemperature = record.value;
                else if ("INSTANTANEOUS".equals(function)) b.temperature = record.value;
            } else if ("TEMPERATURE_EXTREMUM_DATE_TIME".equals(semantic)) {
                if ("MAXIMUM".equals(function)) b.maxTemperatureAt = record.value;
                else if ("MINIMUM".equals(function)) b.minTemperatureAt = record.value;
            } else if ("EXTERNAL_TEMPERATURE".equals(semantic)) {
                b.externalTemperature = record.value;
            } else if ("BATTERY_PERCENT_QALCOSONIC".equals(semantic)) {
                b.batteryPercent = record.value;
            } else if ("ERROR_FLAGS".equals(semantic)) {
                b.errorFlags = record.value;
            } else if ("ON_TIME".equals(semantic)) {
                b.onTime = record.value;
            } else if ("OPERATING_TIME".equals(semantic)) {
                b.operatingTime = record.value;
            } else if (semantic != null && !b.unmodeledSemantics.contains(semantic)) {
                b.unmodeledSemantics.add(semantic);
            }
        }
        return new ArchivePeriodSnapshot(b);
    }

    /** Stable only within one known meter. The meter identity belongs to the History layer. */
    String periodKeyWithinMeter() {
        if (loggerDateTime == null) return null;
        return periodType.name() + "|" + loggerDateTime;
    }

    String toUserPreview() {
        StringBuilder out = new StringBuilder();
        out.append(periodTypeLabel()).append('\n');
        append(out, "Logger-Zeitpunkt", loggerDateTime);
        append(out, "Gesamtvolumen", totalVolume);
        append(out, "Vorwärtsvolumen", positiveVolume);
        append(out, "Rückwärtsvolumen", reverseVolume);
        append(out, "Tarif 1 Volumen", tariff1Volume);
        appendExtremum(out, "Max. Durchfluss", maxFlow, maxFlowAt);
        appendExtremum(out, "Min. Durchfluss", minFlow, minFlowAt);
        append(out, "Durchfluss", flow);
        appendExtremum(out, "Max. Temperatur", maxTemperature, maxTemperatureAt);
        appendExtremum(out, "Min. Temperatur", minTemperature, minTemperatureAt);
        append(out, "Temperatur", temperature);
        append(out, "Externe Temperatur", externalTemperature);
        append(out, "Fehlerstatus", errorFlags);
        append(out, "Batterie", batteryPercent);
        return out.toString().trim();
    }

    private static String legacyExtremumDateTime(ArchiveRecordInspector.Record record) {
        if (record.vife == null || record.vife.isEmpty() || (record.vife.get(0) & 0x7F) != 0x6D) return null;
        int base = record.vif & 0x7F;
        int scale;
        if (base >= 0x38 && base <= 0x3F) scale = (base & 7) - 6;
        else if (base >= 0x58 && base <= 0x5F) scale = (base & 3) - 3;
        else return null;
        if (record.value == null) return null;
        String numeric = record.value.split(" ", 2)[0];
        try {
            long raw = new BigDecimal(numeric).scaleByPowerOfTen(-scale).longValueExact();
            int b0 = (int) (raw & 0xFF);
            int b1 = (int) ((raw >> 8) & 0xFF);
            int b2 = (int) ((raw >> 16) & 0xFF);
            int b3 = (int) ((raw >> 24) & 0xFF);
            int minute = b0 & 0x3F, hour = b1 & 0x1F;
            int day = b2 & 0x1F, month = b3 & 0x0F;
            int year = ((b2 >> 5) | ((b3 >> 1) & 0xF8)) + 2000;
            if (minute > 59 || hour > 23 || day < 1 || day > 31 || month < 1 || month > 12) return null;
            return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String periodTypeLabel() {
        switch (periodType) {
            case HOUR: return "Stundenarchiv";
            case DAY: return "Tagesarchiv";
            case MONTH: return "Monatsarchiv";
            case YEAR: return "Jahresarchiv";
            default: return "Archivdatensatz";
        }
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value != null) out.append(label).append(": ").append(value).append('\n');
    }

    private static void appendExtremum(StringBuilder out, String label, String value, String at) {
        if (value == null && at == null) return;
        out.append(label).append(": ");
        out.append(value == null ? "—" : value);
        if (at != null) out.append(" · ").append(at);
        out.append('\n');
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s{%s}", periodType, periodKeyWithinMeter());
    }

    private static final class Builder {
        final PeriodType periodType;
        String loggerDateTime;
        String retrievedAtUtc;
        String totalVolume;
        String positiveVolume;
        String reverseVolume;
        String tariff1Volume;
        String maxFlow;
        String maxFlowAt;
        String minFlow;
        String minFlowAt;
        String flow;
        String maxTemperature;
        String maxTemperatureAt;
        String minTemperature;
        String minTemperatureAt;
        String temperature;
        String externalTemperature;
        String batteryPercent;
        String errorFlags;
        String onTime;
        String operatingTime;
        final List<String> unmodeledSemantics = new ArrayList<>();

        Builder(PeriodType periodType) { this.periodType = periodType; }
    }
}
