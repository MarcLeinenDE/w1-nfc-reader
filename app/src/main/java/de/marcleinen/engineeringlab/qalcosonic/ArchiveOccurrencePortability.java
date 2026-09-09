package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Occurrence-safe archive merge used by schema-3 portability after meter_archive.db v2. */
final class ArchiveOccurrencePortability {
    private static final String[] PERIOD_COLUMNS = new String[]{
            "meter_id","archive_family","logger_timestamp","occurrence_key","logger_time_basis",
            "on_time_seconds","raw_type_f_hex","type_f_iv","type_f_su","retrieved_at_utc","retrieved_at_ms",
            "first_retrieved_at_utc","first_retrieved_at_ms","source","validation","structural_fingerprint",
            "content_fingerprint","observation_count","identical_content_confirmations","structural_confirmations",
            "provenance_confirmations","revision_count","conflict_flags","last_content_fingerprint",
            "last_structural_fingerprint","last_source","last_validation","total_volume","positive_volume",
            "reverse_volume","tariff1_volume","max_flow","max_flow_at","min_flow","min_flow_at","flow",
            "max_temperature","max_temperature_at","min_temperature","min_temperature_at","temperature",
            "external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values"};

    private static final String[] CONFLICT_COLUMNS = new String[]{
            "occurrence_key","on_time_seconds","raw_type_f_hex","type_f_iv","type_f_su",
            "observed_at_utc","observed_at_ms","source","validation","structural_fingerprint",
            "content_fingerprint","content_changed","structure_changed","provenance_changed","total_volume",
            "positive_volume","reverse_volume","tariff1_volume","max_flow","max_flow_at","min_flow","min_flow_at",
            "flow","max_temperature","max_temperature_at","min_temperature","min_temperature_at","temperature",
            "external_temperature","battery_percent","error_flags","on_time","operating_time","extra_values"};

    private ArchiveOccurrencePortability() { }

    static void merge(Context context, JSONArray periods, JSONArray conflicts) throws JSONException {
        if (periods == null || conflicts == null) throw new JSONException("archive envelope required");
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < periods.length(); i++) mergePeriod(db, periods.getJSONObject(i));
                for (int i = 0; i < conflicts.length(); i++) mergeConflict(db, conflicts.getJSONObject(i));
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    private static void mergePeriod(SQLiteDatabase db, JSONObject row) throws JSONException {
        String meter = requiredString(row, "meter_id");
        String family = requiredString(row, "archive_family");
        String logger = requiredString(row, "logger_timestamp");
        String occurrence = requiredString(row, "occurrence_key");
        try (Cursor c = db.query(ArchiveFamilyStore.TABLE_PERIODS, null,
                "meter_id=? AND archive_family=? AND logger_timestamp=? AND occurrence_key=?",
                new String[]{meter, family, logger, occurrence}, null, null, null, "1")) {
            if (!c.moveToFirst()) {
                db.insertOrThrow(ArchiveFamilyStore.TABLE_PERIODS, null, values(row, PERIOD_COLUMNS));
                return;
            }

            long id = c.getLong(c.getColumnIndexOrThrow("id"));
            String existingFingerprint = c.getString(c.getColumnIndexOrThrow("content_fingerprint"));
            String importedFingerprint = requiredString(row, "content_fingerprint");
            if (existingFingerprint.equals(importedFingerprint)) {
                ContentValues update = new ContentValues();
                max(update, c, row, "observation_count", 1);
                max(update, c, row, "identical_content_confirmations", 0);
                max(update, c, row, "structural_confirmations", 0);
                max(update, c, row, "provenance_confirmations", 0);
                max(update, c, row, "revision_count", 0);
                update.put("conflict_flags",
                        c.getInt(c.getColumnIndexOrThrow("conflict_flags")) | row.optInt("conflict_flags", 0));

                long existingRetrieved = c.getLong(c.getColumnIndexOrThrow("retrieved_at_ms"));
                long importedRetrieved = row.getLong("retrieved_at_ms");
                if (importedRetrieved > existingRetrieved) {
                    update.put("retrieved_at_ms", importedRetrieved);
                    update.put("retrieved_at_utc", requiredString(row, "retrieved_at_utc"));
                    update.put("last_content_fingerprint", importedFingerprint);
                    update.put("last_structural_fingerprint", requiredString(row, "last_structural_fingerprint"));
                    update.put("last_source", requiredString(row, "last_source"));
                    update.put("last_validation", requiredString(row, "last_validation"));
                }
                long existingFirst = c.getLong(c.getColumnIndexOrThrow("first_retrieved_at_ms"));
                long importedFirst = row.getLong("first_retrieved_at_ms");
                if (importedFirst < existingFirst) {
                    update.put("first_retrieved_at_ms", importedFirst);
                    update.put("first_retrieved_at_utc", requiredString(row, "first_retrieved_at_utc"));
                }
                fillIfMissing(update, c, row, "on_time_seconds");
                fillIfMissing(update, c, row, "raw_type_f_hex");
                fillIfMissing(update, c, row, "type_f_iv");
                fillIfMissing(update, c, row, "type_f_su");
                if (update.size() > 0) {
                    db.update(ArchiveFamilyStore.TABLE_PERIODS, update, "id=?", new String[]{Long.toString(id)});
                }
            } else {
                ContentValues update = new ContentValues();
                update.put("conflict_flags",
                        c.getInt(c.getColumnIndexOrThrow("conflict_flags")) | ArchiveFamilyStore.CONFLICT_CONTENT);
                update.put("revision_count", Math.max(
                        c.getInt(c.getColumnIndexOrThrow("revision_count")) + 1,
                        row.optInt("revision_count", 0)));
                db.update(ArchiveFamilyStore.TABLE_PERIODS, update, "id=?", new String[]{Long.toString(id)});
                insertConflictFromPeriod(db, id, row);
            }
        }
    }

    private static void mergeConflict(SQLiteDatabase db, JSONObject row) throws JSONException {
        long periodId = findPeriodId(db,
                requiredString(row, "meter_id"),
                requiredString(row, "archive_family"),
                requiredString(row, "logger_timestamp"),
                requiredString(row, "occurrence_key"));
        if (periodId <= 0L) throw new JSONException("conflict without archive occurrence");
        long observed = row.getLong("observed_at_ms");
        String fingerprint = requiredString(row, "content_fingerprint");
        try (Cursor c = db.query(ArchiveFamilyStore.TABLE_CONFLICTS, new String[]{"id"},
                "archive_period_id=? AND observed_at_ms=? AND content_fingerprint=?",
                new String[]{Long.toString(periodId), Long.toString(observed), fingerprint},
                null, null, null, "1")) {
            if (c.moveToFirst()) return;
        }
        ContentValues v = values(row, CONFLICT_COLUMNS);
        v.put("archive_period_id", periodId);
        db.insertOrThrow(ArchiveFamilyStore.TABLE_CONFLICTS, null, v);
    }

    private static void insertConflictFromPeriod(SQLiteDatabase db, long periodId, JSONObject row)
            throws JSONException {
        ContentValues v = new ContentValues();
        v.put("archive_period_id", periodId);
        copy(row, v, "occurrence_key");
        copyNullable(row, v, "on_time_seconds");
        copyNullable(row, v, "raw_type_f_hex");
        copyNullable(row, v, "type_f_iv");
        copyNullable(row, v, "type_f_su");
        v.put("observed_at_utc", requiredString(row, "retrieved_at_utc"));
        v.put("observed_at_ms", row.getLong("retrieved_at_ms"));
        v.put("source", requiredString(row, "source"));
        v.put("validation", requiredString(row, "validation"));
        v.put("structural_fingerprint", requiredString(row, "structural_fingerprint"));
        v.put("content_fingerprint", requiredString(row, "content_fingerprint"));
        v.put("content_changed", 1);
        v.put("structure_changed", 0);
        v.put("provenance_changed", 0);
        for (String column : new String[]{"total_volume","positive_volume","reverse_volume","tariff1_volume",
                "max_flow","max_flow_at","min_flow","min_flow_at","flow","max_temperature","max_temperature_at",
                "min_temperature","min_temperature_at","temperature","external_temperature","battery_percent",
                "error_flags","on_time","operating_time","extra_values"}) {
            copyNullable(row, v, column);
        }
        db.insertOrThrow(ArchiveFamilyStore.TABLE_CONFLICTS, null, v);
    }

    private static long findPeriodId(SQLiteDatabase db, String meter, String family, String logger, String occurrence) {
        try (Cursor c = db.query(ArchiveFamilyStore.TABLE_PERIODS, new String[]{"id"},
                "meter_id=? AND archive_family=? AND logger_timestamp=? AND occurrence_key=?",
                new String[]{meter, family, logger, occurrence}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getLong(0) : -1L;
        }
    }

    private static ContentValues values(JSONObject row, String[] columns) throws JSONException {
        ContentValues out = new ContentValues();
        for (String column : columns) copyNullable(row, out, column);
        return out;
    }

    private static void max(ContentValues update, Cursor c, JSONObject row, String column, int fallback) {
        update.put(column, Math.max(c.getInt(c.getColumnIndexOrThrow(column)), row.optInt(column, fallback)));
    }

    private static void fillIfMissing(ContentValues update, Cursor c, JSONObject row, String column)
            throws JSONException {
        int index = c.getColumnIndexOrThrow(column);
        if (!c.isNull(index) || !row.has(column) || row.isNull(column)) return;
        putJson(update, column, row.get(column));
    }

    private static void copy(JSONObject row, ContentValues out, String column) throws JSONException {
        if (!row.has(column) || row.isNull(column)) throw new JSONException("missing " + column);
        putJson(out, column, row.get(column));
    }

    private static void copyNullable(JSONObject row, ContentValues out, String column) throws JSONException {
        if (!row.has(column) || row.isNull(column)) {
            out.putNull(column);
            return;
        }
        putJson(out, column, row.get(column));
    }

    private static void putJson(ContentValues out, String column, Object value) {
        if (value instanceof Integer) out.put(column, (Integer) value);
        else if (value instanceof Long) out.put(column, (Long) value);
        else if (value instanceof Number) out.put(column, ((Number) value).doubleValue());
        else if (value instanceof Boolean) out.put(column, ((Boolean) value) ? 1 : 0);
        else out.put(column, value.toString());
    }

    private static String requiredString(JSONObject row, String key) throws JSONException {
        String value = row.getString(key);
        if (value == null || value.trim().isEmpty()) throw new JSONException("missing " + key);
        return value.trim();
    }
}
