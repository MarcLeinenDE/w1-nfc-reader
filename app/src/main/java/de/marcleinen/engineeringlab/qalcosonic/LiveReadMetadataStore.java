package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores the latest successful Live observation independently from History de-duplication.
 *
 * <p>The History database intentionally suppresses near-identical readings within a short window.
 * User-facing freshness and current alarm state must nevertheless reflect every successful real
 * Live read, so this metadata is kept separately and never changes History identity semantics.</p>
 */
final class LiveReadMetadataStore {
    private static final String PREFS = "latest_live_read_metadata";
    private final Context appContext;
    private final SharedPreferences prefs;

    LiveReadMetadataStore(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void record(MbusParser.MeterData meter, long readAtMs) {
        if (meter == null || meter.meterId == null || meter.meterId.trim().isEmpty()
                || meter.waterUsageM3 == null || readAtMs <= 0L) return;
        String prefix = keyPrefix(meter.meterId);
        prefs.edit()
                .putLong(prefix + "read_at_ms", readAtMs)
                .putLong(prefix + "total_bits", Double.doubleToLongBits(meter.waterUsageM3))
                .putInt(prefix + "battery", meter.batteryPercent == null ? -1 : meter.batteryPercent)
                .putString(prefix + "alarms", serializeAlarms(meter.getErrors()))
                .apply();
        SeasonalSupportPrompt.recordSuccessfulNormalLive(appContext, readAtMs);
    }

    Summary get(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return Summary.EMPTY;
        String prefix = keyPrefix(meterId);
        long atMs = prefs.getLong(prefix + "read_at_ms", 0L);
        if (atMs <= 0L) return Summary.EMPTY;
        long totalBits = prefs.getLong(prefix + "total_bits", Double.doubleToLongBits(Double.NaN));
        int battery = prefs.getInt(prefix + "battery", -1);
        return new Summary(atMs, Double.longBitsToDouble(totalBits),
                battery < 0 ? null : battery, prefs.getString(prefix + "alarms", ""));
    }

    JSONObject exportJson() throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Long || value instanceof Integer || value instanceof String
                    || value instanceof Boolean || value instanceof Float) {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    /** Restore using the preference schema, not JSONObject's runtime numeric type. */
    void restoreJson(JSONObject in) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        java.util.Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = in.opt(key);
            if (key.endsWith(".read_at_ms") || key.endsWith(".total_bits")) {
                if (value instanceof Number) editor.putLong(key, ((Number) value).longValue());
            } else if (key.endsWith(".battery")) {
                if (value instanceof Number) editor.putInt(key, ((Number) value).intValue());
            } else if (key.endsWith(".alarms")) {
                if (value instanceof String) editor.putString(key, (String) value);
            }
        }
        editor.commit();
    }

    void clear() { prefs.edit().clear().commit(); }

    private static String serializeAlarms(List<MbusParser.MeterAlarm> alarms) {
        if (alarms == null || alarms.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (MbusParser.MeterAlarm alarm : alarms) names.add(alarm.name());
        return android.text.TextUtils.join(",", names);
    }

    private static String keyPrefix(String meterId) {
        return meterId.trim().replaceAll("[^A-Za-z0-9_.-]", "_") + ".";
    }

    static final class Summary {
        static final Summary EMPTY = new Summary(0L, Double.NaN, null, "");
        final long readAtMs;
        final double totalM3;
        final Integer batteryPercent;
        final String alarmCodes;

        Summary(long readAtMs, double totalM3, Integer batteryPercent, String alarmCodes) {
            this.readAtMs = readAtMs;
            this.totalM3 = totalM3;
            this.batteryPercent = batteryPercent;
            this.alarmCodes = alarmCodes == null ? "" : alarmCodes;
        }

        boolean available() { return readAtMs > 0L && !Double.isNaN(totalM3); }
        boolean hasAlarms() { return !alarmCodes.isEmpty(); }
    }
}
