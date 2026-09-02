package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Archive-family-neutral normalized values. Known cross-family values keep stable fields while
 * {@code extraValues} preserves future family-specific semantics without a schema redesign.
 */
final class ArchiveNormalizedValues {
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
    final Map<String, String> extraValues;

    private ArchiveNormalizedValues(Builder b) {
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
        extraValues = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraValues));
    }

    static ArchiveNormalizedValues fromSnapshot(ArchivePeriodSnapshot snapshot) {
        Builder b = builder();
        if (snapshot == null) return b.build();
        b.totalVolume = snapshot.totalVolume;
        b.positiveVolume = snapshot.positiveVolume;
        b.reverseVolume = snapshot.reverseVolume;
        b.tariff1Volume = snapshot.tariff1Volume;
        b.maxFlow = snapshot.maxFlow;
        b.maxFlowAt = snapshot.maxFlowAt;
        b.minFlow = snapshot.minFlow;
        b.minFlowAt = snapshot.minFlowAt;
        b.flow = snapshot.flow;
        b.maxTemperature = snapshot.maxTemperature;
        b.maxTemperatureAt = snapshot.maxTemperatureAt;
        b.minTemperature = snapshot.minTemperature;
        b.minTemperatureAt = snapshot.minTemperatureAt;
        b.temperature = snapshot.temperature;
        b.externalTemperature = snapshot.externalTemperature;
        b.batteryPercent = snapshot.batteryPercent;
        b.errorFlags = snapshot.errorFlags;
        b.onTime = snapshot.onTime;
        b.operatingTime = snapshot.operatingTime;
        for (String semantic : snapshot.unmodeledSemantics) {
            if (semantic != null && !semantic.isEmpty()) {
                // Existing decoder versions only expose the semantic name for unmodeled fields.
                // Future family decoders can put the normalized value into this same map.
                b.extraValues.put("semantic:" + semantic, "");
            }
        }
        return b.build();
    }

    static Builder builder() { return new Builder(); }

    static final class Builder {
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
        final Map<String, String> extraValues = new LinkedHashMap<>();

        Builder totalVolume(String value) { totalVolume = value; return this; }
        Builder batteryPercent(String value) { batteryPercent = value; return this; }
        Builder errorFlags(String value) { errorFlags = value; return this; }
        Builder extra(String key, String value) {
            if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("extra key required");
            extraValues.put(key.trim(), value == null ? "" : value);
            return this;
        }
        ArchiveNormalizedValues build() { return new ArchiveNormalizedValues(this); }
    }
}
