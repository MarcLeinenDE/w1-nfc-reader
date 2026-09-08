package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Version-2 family-specific History synchronization metadata.
 *
 * <p>This store intentionally does not import the v1 meter-level HistorySyncMetadataStore. The
 * v1 COMPLETE state could have been produced by a fixed Month hard-cap and is therefore not valid
 * evidence that a family baseline reached a semantic archive end.</p>
 */
final class ArchiveFamilySyncStateStore {
    static final String PREFS = "history_sync_v2";
    static final int SCHEMA_VERSION = 2;

    private final SharedPreferences prefs;

    ArchiveFamilySyncStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    ArchiveFamilySyncState get(String meterId, ArchiveFamilyPeriod.Family family) {
        String meter = requireMeter(meterId);
        if (family == null) throw new IllegalArgumentException("family required");
        String encoded = prefs.getString(key(meter, family), null);
        if (encoded == null) return ArchiveFamilySyncState.empty(family);
        try {
            JSONObject object = new JSONObject(encoded);
            if (!meter.equals(requiredString(object, "meter_id"))) {
                return ArchiveFamilySyncState.empty(family);
            }
            if (!family.name().equals(requiredString(object, "family"))) {
                return ArchiveFamilySyncState.empty(family);
            }
            return decode(object);
        } catch (JSONException | IllegalArgumentException error) {
            return ArchiveFamilySyncState.empty(family);
        }
    }

    ArchiveFamilySyncState recordAttempt(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            ArchiveFamilySyncState.SyncMode mode,
            long atMs,
            ArchiveFamilySyncState.AttemptOutcome outcome,
            ArchiveFamilySyncState.StopReason stopReason,
            boolean finalRestoreVerified,
            String oldestRawTimestamp,
            String newestRawTimestamp,
            int accepted,
            int inserted,
            int confirmed,
            int conflicts) {
        String meter = requireMeter(meterId);
        if (family == null) throw new IllegalArgumentException("family required");
        if (mode == null) throw new IllegalArgumentException("mode required");
        if (outcome == null || outcome == ArchiveFamilySyncState.AttemptOutcome.NONE) {
            throw new IllegalArgumentException("terminal attempt outcome required");
        }
        if (stopReason == null || stopReason == ArchiveFamilySyncState.StopReason.NONE) {
            throw new IllegalArgumentException("stopReason required");
        }
        if (atMs <= 0L) throw new IllegalArgumentException("atMs must be > 0");
        if (accepted < 0 || inserted < 0 || confirmed < 0 || conflicts < 0) {
            throw new IllegalArgumentException("attempt counters must be >= 0");
        }

        ArchiveFamilySyncState current = get(meter, family);
        if (mode == ArchiveFamilySyncState.SyncMode.INCREMENTAL && !current.baselineComplete()) {
            throw new IllegalStateException("incremental sync requires a complete family baseline");
        }

        boolean semanticSuccess = ArchiveFamilySyncState.successfulStopForMode(mode, stopReason);
        if (outcome == ArchiveFamilySyncState.AttemptOutcome.COMPLETE
                && (!semanticSuccess || !finalRestoreVerified)) {
            throw new IllegalArgumentException(
                    "complete outcome requires a successful semantic stop and verified final restore");
        }
        if (outcome != ArchiveFamilySyncState.AttemptOutcome.COMPLETE && semanticSuccess) {
            // A terminal/known-record stop without a verified final Live is still partial because
            // the family safety shell has not completed.
            if (finalRestoreVerified) {
                throw new IllegalArgumentException(
                        "successful semantic stop plus verified restore must be COMPLETE");
            }
        }

        ArchiveFamilySyncState.BaselineState baseline = current.baselineState;
        long baselineCompletedAtMs = current.baselineCompletedAtMs;
        if (baseline != ArchiveFamilySyncState.BaselineState.COMPLETE
                && mode != ArchiveFamilySyncState.SyncMode.INCREMENTAL) {
            if (outcome == ArchiveFamilySyncState.AttemptOutcome.COMPLETE) {
                baseline = ArchiveFamilySyncState.BaselineState.COMPLETE;
                baselineCompletedAtMs = atMs;
            } else if (accepted > 0) {
                baseline = ArchiveFamilySyncState.BaselineState.PARTIAL;
            }
        }

        String oldest = earlier(current.oldestRawTimestamp, oldestRawTimestamp);
        String newest = later(current.newestRawTimestamp, newestRawTimestamp);
        long lastSuccessMs = outcome == ArchiveFamilySyncState.AttemptOutcome.COMPLETE
                ? atMs
                : current.lastSuccessMs;
        String incrementalAnchor = outcome == ArchiveFamilySyncState.AttemptOutcome.COMPLETE
                ? newest
                : current.incrementalAnchorRawTimestamp;
        boolean recoveryRecommended = outcome != ArchiveFamilySyncState.AttemptOutcome.COMPLETE;

        ArchiveFamilySyncState next = new ArchiveFamilySyncState(
                family,
                baseline,
                outcome,
                mode,
                stopReason,
                atMs,
                lastSuccessMs,
                baselineCompletedAtMs,
                oldest,
                newest,
                incrementalAnchor,
                accepted,
                inserted,
                confirmed,
                conflicts,
                finalRestoreVerified,
                recoveryRecommended);
        persist(meter, next);
        return next;
    }

    JSONObject exportJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("schema_version", SCHEMA_VERSION);
        JSONArray entries = new JSONArray();
        List<JSONObject> decoded = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            try {
                decoded.add(new JSONObject((String) entry.getValue()));
            } catch (JSONException ignored) {
                // Corrupt local metadata is omitted rather than exported as trusted state.
            }
        }
        decoded.sort(Comparator
                .comparing((JSONObject object) -> object.optString("meter_id", ""))
                .thenComparing(object -> object.optString("family", "")));
        for (JSONObject object : decoded) entries.put(object);
        out.put("entries", entries);
        return out;
    }

    void restoreJson(JSONObject in) throws JSONException {
        if (in == null || in.optInt("schema_version", -1) != SCHEMA_VERSION) {
            throw new JSONException("unsupported history sync schema");
        }
        JSONArray entries = in.getJSONArray("entries");
        List<PendingEntry> validated = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject object = entries.getJSONObject(i);
            String meter = requireMeter(requiredString(object, "meter_id"));
            ArchiveFamilyPeriod.Family family = ArchiveFamilyPeriod.Family.valueOf(
                    requiredString(object, "family"));
            ArchiveFamilySyncState state = decode(object);
            validated.add(new PendingEntry(key(meter, family), encode(meter, state).toString()));
        }

        SharedPreferences.Editor editor = prefs.edit().clear();
        for (PendingEntry entry : validated) editor.putString(entry.key, entry.value);
        if (!editor.commit()) throw new JSONException("history sync metadata commit failed");
    }

    void clear() {
        prefs.edit().clear().commit();
    }

    private void persist(String meterId, ArchiveFamilySyncState state) {
        try {
            prefs.edit().putString(key(meterId, state.family), encode(meterId, state).toString()).commit();
        } catch (JSONException error) {
            throw new IllegalStateException("history sync state encode failed", error);
        }
    }

    private static JSONObject encode(String meterId, ArchiveFamilySyncState state) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("meter_id", meterId);
        out.put("family", state.family.name());
        out.put("baseline_state", state.baselineState.name());
        out.put("last_attempt_outcome", state.lastAttemptOutcome.name());
        out.put("last_sync_mode", state.lastSyncMode.name());
        out.put("last_stop_reason", state.lastStopReason.name());
        out.put("last_attempt_ms", state.lastAttemptMs);
        out.put("last_success_ms", state.lastSuccessMs);
        out.put("baseline_completed_at_ms", state.baselineCompletedAtMs);
        putNullable(out, "oldest_raw_timestamp", state.oldestRawTimestamp);
        putNullable(out, "newest_raw_timestamp", state.newestRawTimestamp);
        putNullable(out, "incremental_anchor_raw_timestamp", state.incrementalAnchorRawTimestamp);
        out.put("accepted", state.accepted);
        out.put("inserted", state.inserted);
        out.put("confirmed", state.confirmed);
        out.put("conflicts", state.conflicts);
        out.put("final_restore_verified", state.finalRestoreVerified);
        out.put("recovery_recommended", state.recoveryRecommended);
        return out;
    }

    private static ArchiveFamilySyncState decode(JSONObject in) throws JSONException {
        ArchiveFamilyPeriod.Family family = ArchiveFamilyPeriod.Family.valueOf(requiredString(in, "family"));
        return new ArchiveFamilySyncState(
                family,
                ArchiveFamilySyncState.BaselineState.valueOf(requiredString(in, "baseline_state")),
                ArchiveFamilySyncState.AttemptOutcome.valueOf(requiredString(in, "last_attempt_outcome")),
                ArchiveFamilySyncState.SyncMode.valueOf(requiredString(in, "last_sync_mode")),
                ArchiveFamilySyncState.StopReason.valueOf(requiredString(in, "last_stop_reason")),
                in.getLong("last_attempt_ms"),
                in.getLong("last_success_ms"),
                in.getLong("baseline_completed_at_ms"),
                nullableString(in, "oldest_raw_timestamp"),
                nullableString(in, "newest_raw_timestamp"),
                nullableString(in, "incremental_anchor_raw_timestamp"),
                in.getInt("accepted"),
                in.getInt("inserted"),
                in.getInt("confirmed"),
                in.getInt("conflicts"),
                in.getBoolean("final_restore_verified"),
                in.getBoolean("recovery_recommended"));
    }

    private static String key(String meterId, ArchiveFamilyPeriod.Family family) {
        return escapeKey(meterId) + "|" + family.name();
    }

    private static String escapeKey(String value) {
        return value.replace("%", "%25").replace("|", "%7C");
    }

    private static String requireMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) {
            throw new IllegalArgumentException("meterId required");
        }
        return meterId.trim();
    }

    private static String requiredString(JSONObject in, String key) throws JSONException {
        String value = in.getString(key);
        if (value == null || value.trim().isEmpty()) throw new JSONException(key + " required");
        return value.trim();
    }

    private static String nullableString(JSONObject in, String key) {
        if (in.isNull(key)) return null;
        String value = in.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static void putNullable(JSONObject out, String key, String value) throws JSONException {
        if (value == null) out.put(key, JSONObject.NULL); else out.put(key, value);
    }

    private static String earlier(String first, String second) {
        first = normalizeOptional(first);
        second = normalizeOptional(second);
        if (first == null) return second;
        if (second == null) return first;
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static String later(String first, String second) {
        first = normalizeOptional(first);
        second = normalizeOptional(second);
        if (first == null) return second;
        if (second == null) return first;
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class PendingEntry {
        final String key;
        final String value;

        PendingEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
