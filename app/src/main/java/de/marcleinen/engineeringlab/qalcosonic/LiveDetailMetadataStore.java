package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/** Latest successful live/default detail values that are not part of historical row identity. */
final class LiveDetailMetadataStore {
    private static final String PREFS = "latest_live_detail_metadata_v1";
    private final SharedPreferences prefs;

    static final class Summary {
        final String meterId;
        final long readAtMs;
        final String meterTime;
        final String serialNumber;
        final String manufacturer;
        final Integer meterVersion;
        final Double totalM3;
        final Double flowM3h;
        final Double waterTemperatureC;
        final Double ambientTemperatureC;
        final Integer batteryPercent;
        final Long onTimeSeconds;
        final Long operatingTimeSeconds;
        final String rawErrorFlags;

        Summary(String meterId, long readAtMs, String meterTime, String serialNumber,
                String manufacturer, Integer meterVersion, Double totalM3, Double flowM3h,
                Double waterTemperatureC, Double ambientTemperatureC, Integer batteryPercent,
                Long onTimeSeconds, Long operatingTimeSeconds, String rawErrorFlags) {
            this.meterId = meterId;
            this.readAtMs = readAtMs;
            this.meterTime = meterTime;
            this.serialNumber = serialNumber;
            this.manufacturer = manufacturer;
            this.meterVersion = meterVersion;
            this.totalM3 = totalM3;
            this.flowM3h = flowM3h;
            this.waterTemperatureC = waterTemperatureC;
            this.ambientTemperatureC = ambientTemperatureC;
            this.batteryPercent = batteryPercent;
            this.onTimeSeconds = onTimeSeconds;
            this.operatingTimeSeconds = operatingTimeSeconds;
            this.rawErrorFlags = rawErrorFlags;
        }

        boolean available() { return meterId != null && readAtMs > 0L; }
    }

    LiveDetailMetadataStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void record(MbusParser.MeterData meter, long readAtMs) {
        if (meter == null || meter.meterId == null || meter.meterId.trim().isEmpty() || readAtMs <= 0L) return;
        try {
            JSONObject object = new JSONObject();
            object.put("meter_id", meter.meterId);
            object.put("read_at_ms", readAtMs);
            put(object, "meter_time", meter.timepoint);
            put(object, "serial_number", meter.serialNumber);
            put(object, "manufacturer", meter.manufacturer);
            put(object, "meter_version", meter.meterVersion);
            put(object, "total_m3", meter.waterUsageM3);
            put(object, "flow_m3h", meter.flowM3h);
            put(object, "water_temperature_c", meter.waterTemperatureC);
            put(object, "ambient_temperature_c", meter.externalTemperatureC);
            put(object, "battery_percent", meter.batteryPercent);
            put(object, "on_time_seconds", meter.onTimeSeconds);
            put(object, "operating_time_seconds", meter.operatingTimeSeconds);
            put(object, "raw_error_flags", meter.errorFlagsRaw);
            prefs.edit().putString(key(meter.meterId), object.toString()).apply();
        } catch (JSONException ignored) { }
    }

    Summary get(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return empty();
        String raw = prefs.getString(key(meterId), null);
        if (raw == null) return empty();
        try {
            JSONObject in = new JSONObject(raw);
            return new Summary(
                    in.optString("meter_id", null), in.optLong("read_at_ms", 0L),
                    nullableString(in, "meter_time"), nullableString(in, "serial_number"),
                    nullableString(in, "manufacturer"), nullableInt(in, "meter_version"),
                    nullableDouble(in, "total_m3"), nullableDouble(in, "flow_m3h"),
                    nullableDouble(in, "water_temperature_c"), nullableDouble(in, "ambient_temperature_c"),
                    nullableInt(in, "battery_percent"), nullableLong(in, "on_time_seconds"),
                    nullableLong(in, "operating_time_seconds"), nullableString(in, "raw_error_flags"));
        } catch (JSONException ignored) {
            return empty();
        }
    }

    JSONObject exportJson() throws JSONException {
        JSONObject out = new JSONObject();
        for (String key : prefs.getAll().keySet()) {
            String value = prefs.getString(key, null);
            if (value != null) out.put(key, new JSONObject(value));
        }
        return out;
    }

    void restoreJson(JSONObject in) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        java.util.Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject value = in.optJSONObject(key);
            if (value != null) editor.putString(key, value.toString());
        }
        editor.apply();
    }

    void clear() { prefs.edit().clear().apply(); }

    private static void put(JSONObject object, String key, Object value) throws JSONException {
        if (value == null) object.put(key, JSONObject.NULL); else object.put(key, value);
    }

    private static String key(String meterId) { return meterId.trim().replaceAll("[^A-Za-z0-9_.-]", "_"); }
    private static String nullableString(JSONObject in, String key) { return in.isNull(key) ? null : in.optString(key, null); }
    private static Integer nullableInt(JSONObject in, String key) { return in.isNull(key) || !in.has(key) ? null : in.optInt(key); }
    private static Long nullableLong(JSONObject in, String key) { return in.isNull(key) || !in.has(key) ? null : in.optLong(key); }
    private static Double nullableDouble(JSONObject in, String key) { return in.isNull(key) || !in.has(key) ? null : in.optDouble(key); }
    private static Summary empty() { return new Summary(null, 0L, null, null, null, null, null, null, null, null, null, null, null, null); }
}
