package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Product lifecycle state: exactly one active meter, with explicitly confirmed predecessor links.
 * This is presentation/domain metadata only; original readings always retain their physical meter ID.
 */
final class MeterLifecycleStore {
    private static final String PREFS = "meter_lifecycle_v1";
    private static final String KEY_ACTIVE = "active_meter";
    private static final String KEY_TRANSITIONS = "transitions_json";

    static final class Transition {
        final String predecessorMeterId;
        final String successorMeterId;
        final long confirmedAtMs;
        final long firstSuccessorReadAtMs;
        final double firstSuccessorTotalM3;

        Transition(String predecessorMeterId, String successorMeterId, long confirmedAtMs,
                   long firstSuccessorReadAtMs, double firstSuccessorTotalM3) {
            this.predecessorMeterId = predecessorMeterId;
            this.successorMeterId = successorMeterId;
            this.confirmedAtMs = confirmedAtMs;
            this.firstSuccessorReadAtMs = firstSuccessorReadAtMs;
            this.firstSuccessorTotalM3 = firstSuccessorTotalM3;
        }

        JSONObject toJson() throws JSONException {
            JSONObject out = new JSONObject();
            out.put("predecessor_meter_id", predecessorMeterId);
            out.put("successor_meter_id", successorMeterId);
            out.put("confirmed_at_ms", confirmedAtMs);
            out.put("first_successor_read_at_ms", firstSuccessorReadAtMs);
            out.put("first_successor_total_m3", firstSuccessorTotalM3);
            return out;
        }

        static Transition fromJson(JSONObject in) throws JSONException {
            return new Transition(
                    in.getString("predecessor_meter_id"),
                    in.getString("successor_meter_id"),
                    in.getLong("confirmed_at_ms"),
                    in.getLong("first_successor_read_at_ms"),
                    in.getDouble("first_successor_total_m3"));
        }
    }

    private final SharedPreferences prefs;

    MeterLifecycleStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String activeMeterId() {
        String value = prefs.getString(KEY_ACTIVE, null);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    void adoptInitialMeter(String meterId) {
        requireMeter(meterId);
        if (activeMeterId() == null) prefs.edit().putString(KEY_ACTIVE, meterId.trim()).apply();
    }

    void startFresh(String meterId) {
        requireMeter(meterId);
        prefs.edit().clear().putString(KEY_ACTIVE, meterId.trim()).apply();
    }

    void confirmReplacement(String predecessorMeterId, String successorMeterId,
                            long confirmedAtMs, long firstSuccessorReadAtMs,
                            double firstSuccessorTotalM3) {
        requireMeter(predecessorMeterId);
        requireMeter(successorMeterId);
        if (predecessorMeterId.trim().equals(successorMeterId.trim())) {
            throw new IllegalArgumentException("replacement meter must differ");
        }
        String active = activeMeterId();
        if (active == null || !active.equals(predecessorMeterId.trim())) {
            throw new IllegalStateException("predecessor is not active meter");
        }
        List<Transition> transitions = transitions();
        for (Transition transition : transitions) {
            if (transition.successorMeterId.equals(successorMeterId.trim())) {
                throw new IllegalStateException("successor already exists in replacement chain");
            }
        }
        transitions.add(new Transition(predecessorMeterId.trim(), successorMeterId.trim(),
                confirmedAtMs, firstSuccessorReadAtMs, firstSuccessorTotalM3));
        prefs.edit()
                .putString(KEY_TRANSITIONS, encodeTransitions(transitions))
                .putString(KEY_ACTIVE, successorMeterId.trim())
                .apply();
    }

    List<Transition> transitions() {
        String json = prefs.getString(KEY_TRANSITIONS, "[]");
        List<Transition> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < array.length(); i++) out.add(Transition.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) {
            // Corrupt lifecycle metadata is not allowed to damage physical meter history.
        }
        return out;
    }

    List<String> chainMeterIds() {
        List<Transition> transitions = transitions();
        List<String> out = new ArrayList<>();
        if (!transitions.isEmpty()) {
            out.add(transitions.get(0).predecessorMeterId);
            for (Transition transition : transitions) out.add(transition.successorMeterId);
        } else if (activeMeterId() != null) {
            out.add(activeMeterId());
        }
        return Collections.unmodifiableList(out);
    }

    JSONObject exportJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("active_meter_id", activeMeterId() == null ? JSONObject.NULL : activeMeterId());
        JSONArray array = new JSONArray();
        for (Transition transition : transitions()) array.put(transition.toJson());
        out.put("transitions", array);
        return out;
    }

    void restoreJson(JSONObject in) throws JSONException {
        String active = in.isNull("active_meter_id") ? null : in.getString("active_meter_id");
        JSONArray source = in.optJSONArray("transitions");
        List<Transition> restored = new ArrayList<>();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) restored.add(Transition.fromJson(source.getJSONObject(i)));
        }
        SharedPreferences.Editor editor = prefs.edit().clear();
        if (active != null && !active.trim().isEmpty()) editor.putString(KEY_ACTIVE, active.trim());
        editor.putString(KEY_TRANSITIONS, encodeTransitions(restored)).apply();
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    private static String encodeTransitions(List<Transition> transitions) {
        JSONArray array = new JSONArray();
        for (Transition transition : transitions) {
            try { array.put(transition.toJson()); } catch (JSONException ignored) { }
        }
        return array.toString();
    }

    private static void requireMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) throw new IllegalArgumentException("meter id required");
    }
}
