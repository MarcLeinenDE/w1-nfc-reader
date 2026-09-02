package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/** Lightweight local metadata for user-facing History freshness and last sync outcome. */
final class HistorySyncMetadataStore {
    private static final String PREFS = "history_sync_metadata";
    private final SharedPreferences prefs;

    HistorySyncMetadataStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void record(String meterId, long atMs, boolean complete, boolean partial,
                int accepted, int inserted, int confirmed, int conflicts) {
        if (meterId == null || meterId.trim().isEmpty() || atMs <= 0L) return;
        String prefix = keyPrefix(meterId);
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(prefix + "last_attempt_ms", atMs)
                .putString(prefix + "last_outcome", complete ? "COMPLETE" : partial ? "PARTIAL" : "FAILED")
                .putInt(prefix + "accepted", accepted)
                .putInt(prefix + "inserted", inserted)
                .putInt(prefix + "confirmed", confirmed)
                .putInt(prefix + "conflicts", conflicts);
        if (complete) editor.putLong(prefix + "last_success_ms", atMs);
        editor.apply();
    }

    Summary get(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return Summary.EMPTY;
        String prefix = keyPrefix(meterId);
        return new Summary(
                prefs.getLong(prefix + "last_attempt_ms", 0L),
                prefs.getLong(prefix + "last_success_ms", 0L),
                prefs.getString(prefix + "last_outcome", ""),
                prefs.getInt(prefix + "accepted", 0),
                prefs.getInt(prefix + "inserted", 0),
                prefs.getInt(prefix + "confirmed", 0),
                prefs.getInt(prefix + "conflicts", 0));
    }

    JSONObject exportJson() throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Long || value instanceof Integer || value instanceof String
                    || value instanceof Boolean || value instanceof Float) out.put(entry.getKey(), value);
        }
        return out;
    }

    /** Restore according to the persisted preference schema, independent of JSON number width. */
    void restoreJson(JSONObject in) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        java.util.Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = in.opt(key);
            if (key.endsWith(".last_attempt_ms") || key.endsWith(".last_success_ms")) {
                if (value instanceof Number) editor.putLong(key, ((Number) value).longValue());
            } else if (key.endsWith(".accepted") || key.endsWith(".inserted")
                    || key.endsWith(".confirmed") || key.endsWith(".conflicts")) {
                if (value instanceof Number) editor.putInt(key, ((Number) value).intValue());
            } else if (key.endsWith(".last_outcome")) {
                if (value instanceof String) editor.putString(key, (String) value);
            }
        }
        editor.commit();
    }

    void clear() { prefs.edit().clear().commit(); }

    private static String keyPrefix(String meterId) {
        return meterId.trim().replaceAll("[^A-Za-z0-9_.-]", "_") + ".";
    }

    static final class Summary {
        static final Summary EMPTY = new Summary(0L, 0L, "", 0, 0, 0, 0);
        final long lastAttemptMs;
        final long lastSuccessMs;
        final String outcome;
        final int accepted;
        final int inserted;
        final int confirmed;
        final int conflicts;

        Summary(long lastAttemptMs, long lastSuccessMs, String outcome,
                int accepted, int inserted, int confirmed, int conflicts) {
            this.lastAttemptMs = lastAttemptMs;
            this.lastSuccessMs = lastSuccessMs;
            this.outcome = outcome == null ? "" : outcome;
            this.accepted = accepted;
            this.inserted = inserted;
            this.confirmed = confirmed;
            this.conflicts = conflicts;
        }

        boolean lastAttemptComplete() { return "COMPLETE".equals(outcome); }
        boolean lastAttemptPartial() { return "PARTIAL".equals(outcome); }
        boolean lastAttemptFailed() { return "FAILED".equals(outcome); }
    }
}
