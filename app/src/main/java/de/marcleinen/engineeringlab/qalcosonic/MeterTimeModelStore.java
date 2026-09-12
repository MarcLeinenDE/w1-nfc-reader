package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Durable per-meter timezone and single active verified Live-anchor state for v2.1 real time. */
final class MeterTimeModelStore extends SQLiteOpenHelper {
    static final String DB_NAME = "meter_time.db";
    static final int DB_VERSION = 1;
    static final int JSON_SCHEMA = 1;

    static final String TABLE_PROFILES = "meter_time_profiles";
    static final String TABLE_ANCHORS = "meter_time_anchors";

    static final String ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE =
            "DEVICE_AT_FIRST_VERIFIED_LIVE";
    static final String ZONE_SOURCE_USER_SELECTED = "USER_SELECTED";
    static final String ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT = "VERIFIED_LIVE_DEFAULT";
    static final String ANCHOR_VALIDATION_COMPLETE = "COMPLETE";

    MeterTimeModelStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PROFILES + " ("
                + "meter_id TEXT PRIMARY KEY,"
                + "zone_id TEXT NOT NULL,"
                + "zone_source TEXT NOT NULL,"
                + "zone_assigned_at_ms INTEGER NOT NULL,"
                + "zone_updated_at_ms INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE " + TABLE_ANCHORS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "meter_id TEXT NOT NULL,"
                + "read_before_epoch_ms INTEGER NOT NULL,"
                + "read_after_epoch_ms INTEGER NOT NULL,"
                + "anchor_epoch_ms INTEGER NOT NULL,"
                + "uncertainty_ms INTEGER NOT NULL,"
                + "raw_meter_wall_clock TEXT NOT NULL,"
                + "raw_type_f_hex TEXT,"
                + "type_f_iv INTEGER NOT NULL,"
                + "type_f_su INTEGER NOT NULL,"
                + "on_time_seconds INTEGER NOT NULL,"
                + "provenance TEXT NOT NULL,"
                + "validation TEXT NOT NULL,"
                + "UNIQUE(meter_id,anchor_epoch_ms,on_time_seconds,raw_meter_wall_clock)"
                + ")");
        db.execSQL("CREATE INDEX idx_meter_time_anchor_latest ON " + TABLE_ANCHORS
                + " (meter_id,anchor_epoch_ms DESC,id DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Unsupported meter time DB migration "
                    + oldVersion + " -> " + newVersion);
        }
    }

    boolean assignZoneIfMissing(String meterId, String zoneId, String source, long atMs) {
        String meter = requireText(meterId, "meterId");
        String zone = normalizeZoneId(zoneId);
        String provenance = requireText(source, "source");
        requireEpoch(atMs);
        ContentValues values = new ContentValues();
        values.put("meter_id", meter);
        values.put("zone_id", zone);
        values.put("zone_source", provenance);
        values.put("zone_assigned_at_ms", atMs);
        values.put("zone_updated_at_ms", atMs);
        return getWritableDatabase().insertWithOnConflict(
                TABLE_PROFILES, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L;
    }

    void setZone(String meterId, String zoneId, String source, long atMs) {
        String meter = requireText(meterId, "meterId");
        String zone = normalizeZoneId(zoneId);
        String provenance = requireText(source, "source");
        requireEpoch(atMs);
        Profile existing = getProfile(meter);
        ContentValues values = new ContentValues();
        values.put("meter_id", meter);
        values.put("zone_id", zone);
        values.put("zone_source", provenance);
        values.put("zone_assigned_at_ms", existing == null ? atMs : existing.assignedAtMs);
        values.put("zone_updated_at_ms", atMs);
        getWritableDatabase().insertWithOnConflict(
                TABLE_PROFILES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    Profile getProfile(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query(
                TABLE_PROFILES,
                new String[]{"meter_id","zone_id","zone_source","zone_assigned_at_ms","zone_updated_at_ms"},
                "meter_id=?", new String[]{meterId.trim()}, null, null, null, "1")) {
            if (!c.moveToFirst()) return null;
            return new Profile(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4));
        }
    }

    /**
     * Records the newest verified Live/default anchor as the one active anchor for this meter.
     * Older rows from previous app versions are collapsed transactionally on the next write.
     * A backwards ON_TIME is an invariant violation and never replaces the active anchor.
     */
    long recordAnchor(
            VerifiedLiveTimeAnchor anchor,
            String provenance,
            String validation) {
        if (anchor == null || !anchor.usable()) {
            throw new IllegalArgumentException("usable verified Live anchor required");
        }
        String source = requireText(provenance, "provenance");
        String state = requireText(validation, "validation");
        AnchorRecord candidate = new AnchorRecord(
                -1L,
                anchor.meterId,
                anchor.readBeforeEpochMs,
                anchor.readAfterEpochMs,
                anchor.anchorEpochMs,
                anchor.uncertaintyMs,
                anchor.rawMeterWallClock,
                anchor.rawTypeFHex,
                anchor.invalidTime,
                anchor.summerTime,
                anchor.onTimeSeconds,
                source,
                state);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            AnchorRecord existing = latestAnchor(db, anchor.meterId);
            if (existing != null && candidate.onTimeSeconds < existing.onTimeSeconds) {
                throw new IllegalArgumentException("verified anchor ON_TIME moved backwards");
            }
            if (existing != null && !newerThan(candidate, existing)) {
                pruneOtherAnchors(db, anchor.meterId, existing.id);
                db.setTransactionSuccessful();
                return existing.id;
            }

            db.delete(TABLE_ANCHORS, "meter_id=?", new String[]{anchor.meterId});
            long id = db.insertOrThrow(TABLE_ANCHORS, null, candidate.valuesWithoutId());
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    AnchorRecord latestAnchor(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return null;
        return latestAnchor(getReadableDatabase(), meterId.trim());
    }

    /**
     * Returns only active anchors. Old databases may physically contain historical anchor rows, but
     * they are intentionally hidden from projection/export and are pruned on the next anchor write.
     */
    List<AnchorRecord> anchors(String meterId) {
        List<AnchorRecord> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        if (meterId != null && !meterId.trim().isEmpty()) {
            AnchorRecord active = latestAnchor(db, meterId.trim());
            if (active != null) out.add(active);
            return out;
        }

        String previousMeter = null;
        try (Cursor c = db.query(
                TABLE_ANCHORS,
                anchorProjection(),
                null,
                null,
                null,
                null,
                "meter_id,on_time_seconds DESC,anchor_epoch_ms DESC,id DESC")) {
            while (c.moveToNext()) {
                AnchorRecord candidate = readAnchor(c);
                if (candidate.meterId.equals(previousMeter)) continue;
                out.add(candidate);
                previousMeter = candidate.meterId;
            }
        }
        return out;
    }

    List<Profile> profiles() {
        List<Profile> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                TABLE_PROFILES,
                new String[]{"meter_id","zone_id","zone_source","zone_assigned_at_ms","zone_updated_at_ms"},
                null, null, null, null, "meter_id")) {
            while (c.moveToNext()) {
                out.add(new Profile(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4)));
            }
        }
        return out;
    }

    JSONObject exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema_version", JSON_SCHEMA);
        JSONArray profiles = new JSONArray();
        for (Profile profile : profiles()) profiles.put(profile.toJson());
        root.put("profiles", profiles);
        JSONArray anchors = new JSONArray();
        for (AnchorRecord anchor : anchors(null)) anchors.put(anchor.toJson());
        root.put("anchors", anchors);
        return root;
    }

    void restoreJson(JSONObject root) throws JSONException {
        validateJson(root);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ANCHORS, null, null);
            db.delete(TABLE_PROFILES, null, null);
            mergeJsonInTransaction(db, root);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Merge rule: existing per-meter zone wins; newest active anchor wins per physical meter. */
    void mergeJson(JSONObject root) throws JSONException {
        validateJson(root);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            mergeJsonInTransaction(db, root);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ANCHORS, null, null);
            db.delete(TABLE_PROFILES, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ANCHORS, "meter_id=?", new String[]{meterId.trim()});
            db.delete(TABLE_PROFILES, "meter_id=?", new String[]{meterId.trim()});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void mergeJsonInTransaction(SQLiteDatabase db, JSONObject root) throws JSONException {
        JSONArray importedProfiles = root.getJSONArray("profiles");
        for (int i = 0; i < importedProfiles.length(); i++) {
            Profile profile = Profile.fromJson(importedProfiles.getJSONObject(i));
            if (profileExists(db, profile.meterId)) continue;
            db.insertOrThrow(TABLE_PROFILES, null, profile.values());
        }
        JSONArray importedAnchors = root.getJSONArray("anchors");
        for (int i = 0; i < importedAnchors.length(); i++) {
            mergeActiveAnchorInTransaction(db,
                    AnchorRecord.fromJson(importedAnchors.getJSONObject(i)));
        }
    }

    private void mergeActiveAnchorInTransaction(SQLiteDatabase db, AnchorRecord candidate) {
        AnchorRecord existing = latestAnchor(db, candidate.meterId);
        if (existing != null && !newerThan(candidate, existing)) {
            pruneOtherAnchors(db, candidate.meterId, existing.id);
            return;
        }
        db.delete(TABLE_ANCHORS, "meter_id=?", new String[]{candidate.meterId});
        db.insertOrThrow(TABLE_ANCHORS, null, candidate.valuesWithoutId());
    }

    private static boolean newerThan(AnchorRecord candidate, AnchorRecord existing) {
        if (candidate.onTimeSeconds != existing.onTimeSeconds) {
            return candidate.onTimeSeconds > existing.onTimeSeconds;
        }
        if (candidate.anchorEpochMs != existing.anchorEpochMs) {
            return candidate.anchorEpochMs > existing.anchorEpochMs;
        }
        if (candidate.uncertaintyMs != existing.uncertaintyMs) {
            return candidate.uncertaintyMs < existing.uncertaintyMs;
        }
        return false;
    }

    private static void pruneOtherAnchors(SQLiteDatabase db, String meterId, long keepId) {
        db.delete(TABLE_ANCHORS,
                "meter_id=? AND id<>?",
                new String[]{meterId, Long.toString(keepId)});
    }

    private static AnchorRecord latestAnchor(SQLiteDatabase db, String meterId) {
        try (Cursor c = db.query(
                TABLE_ANCHORS,
                anchorProjection(),
                "meter_id=?",
                new String[]{meterId},
                null,
                null,
                "on_time_seconds DESC,anchor_epoch_ms DESC,id DESC",
                "1")) {
            return c.moveToFirst() ? readAnchor(c) : null;
        }
    }

    private static boolean profileExists(SQLiteDatabase db, String meterId) {
        try (Cursor c = db.query(TABLE_PROFILES, new String[]{"meter_id"},
                "meter_id=?", new String[]{meterId}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    private static void validateJson(JSONObject root) throws JSONException {
        if (root == null || root.optInt("schema_version", -1) != JSON_SCHEMA) {
            throw new JSONException("unsupported meter time model schema");
        }
        root.getJSONArray("profiles");
        root.getJSONArray("anchors");
    }

    static String normalizeZoneId(String zoneId) {
        String value = requireText(zoneId, "zoneId");
        try {
            ZoneId zone = ZoneId.of(value);
            if (zone instanceof ZoneOffset) throw new IllegalArgumentException("fixed numeric offset is not a meter zone");
            String id = zone.getId();
            if ("Z".equals(id) || id.startsWith("GMT+") || id.startsWith("GMT-")
                    || id.startsWith("UTC+") || id.startsWith("UTC-")) {
                throw new IllegalArgumentException("fixed offset-like zone is not allowed: " + id);
            }
            return id;
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("invalid IANA zone: " + value, error);
        }
    }

    private static void requireEpoch(long value) {
        if (value <= 0L) throw new IllegalArgumentException("positive epoch required");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }

    private static void put(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static String nullable(Cursor c, int index) {
        return c.isNull(index) ? null : c.getString(index);
    }

    private static String[] anchorProjection() {
        return new String[]{"id","meter_id","read_before_epoch_ms","read_after_epoch_ms","anchor_epoch_ms",
                "uncertainty_ms","raw_meter_wall_clock","raw_type_f_hex","type_f_iv","type_f_su",
                "on_time_seconds","provenance","validation"};
    }

    private static AnchorRecord readAnchor(Cursor c) {
        return new AnchorRecord(
                c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5),
                c.getString(6), nullable(c, 7), c.getInt(8) != 0, c.getInt(9) != 0,
                c.getLong(10), c.getString(11), c.getString(12));
    }

    static final class Profile {
        final String meterId;
        final String zoneId;
        final String source;
        final long assignedAtMs;
        final long updatedAtMs;

        Profile(String meterId, String zoneId, String source, long assignedAtMs, long updatedAtMs) {
            this.meterId = requireText(meterId, "meterId");
            this.zoneId = normalizeZoneId(zoneId);
            this.source = requireText(source, "source");
            requireEpoch(assignedAtMs);
            requireEpoch(updatedAtMs);
            if (updatedAtMs < assignedAtMs) throw new IllegalArgumentException("zone update before assignment");
            this.assignedAtMs = assignedAtMs;
            this.updatedAtMs = updatedAtMs;
        }

        ContentValues values() {
            ContentValues values = new ContentValues();
            values.put("meter_id", meterId);
            values.put("zone_id", zoneId);
            values.put("zone_source", source);
            values.put("zone_assigned_at_ms", assignedAtMs);
            values.put("zone_updated_at_ms", updatedAtMs);
            return values;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("meter_id", meterId)
                    .put("zone_id", zoneId)
                    .put("zone_source", source)
                    .put("zone_assigned_at_ms", assignedAtMs)
                    .put("zone_updated_at_ms", updatedAtMs);
        }

        static Profile fromJson(JSONObject in) throws JSONException {
            return new Profile(
                    in.getString("meter_id"),
                    in.getString("zone_id"),
                    in.getString("zone_source"),
                    in.getLong("zone_assigned_at_ms"),
                    in.getLong("zone_updated_at_ms"));
        }
    }

    static final class AnchorRecord {
        final long id;
        final String meterId;
        final long readBeforeEpochMs;
        final long readAfterEpochMs;
        final long anchorEpochMs;
        final long uncertaintyMs;
        final String rawMeterWallClock;
        final String rawTypeFHex;
        final boolean invalidTime;
        final boolean summerTime;
        final long onTimeSeconds;
        final String provenance;
        final String validation;

        AnchorRecord(
                long id,
                String meterId,
                long readBeforeEpochMs,
                long readAfterEpochMs,
                long anchorEpochMs,
                long uncertaintyMs,
                String rawMeterWallClock,
                String rawTypeFHex,
                boolean invalidTime,
                boolean summerTime,
                long onTimeSeconds,
                String provenance,
                String validation) {
            this.id = id;
            this.meterId = requireText(meterId, "meterId");
            this.readBeforeEpochMs = readBeforeEpochMs;
            this.readAfterEpochMs = readAfterEpochMs;
            this.anchorEpochMs = anchorEpochMs;
            this.uncertaintyMs = uncertaintyMs;
            this.rawMeterWallClock = requireText(rawMeterWallClock, "rawMeterWallClock");
            this.rawTypeFHex = rawTypeFHex;
            this.invalidTime = invalidTime;
            this.summerTime = summerTime;
            this.onTimeSeconds = onTimeSeconds;
            this.provenance = requireText(provenance, "provenance");
            this.validation = requireText(validation, "validation");
        }

        VerifiedLiveTimeAnchor toAnchor() {
            return new VerifiedLiveTimeAnchor(
                    meterId, readBeforeEpochMs, readAfterEpochMs, rawMeterWallClock,
                    rawTypeFHex, invalidTime, summerTime, onTimeSeconds);
        }

        ContentValues valuesWithoutId() {
            ContentValues values = new ContentValues();
            values.put("meter_id", meterId);
            values.put("read_before_epoch_ms", readBeforeEpochMs);
            values.put("read_after_epoch_ms", readAfterEpochMs);
            values.put("anchor_epoch_ms", anchorEpochMs);
            values.put("uncertainty_ms", uncertaintyMs);
            values.put("raw_meter_wall_clock", rawMeterWallClock);
            put(values, "raw_type_f_hex", rawTypeFHex);
            values.put("type_f_iv", invalidTime ? 1 : 0);
            values.put("type_f_su", summerTime ? 1 : 0);
            values.put("on_time_seconds", onTimeSeconds);
            values.put("provenance", provenance);
            values.put("validation", validation);
            return values;
        }

        JSONObject toJson() throws JSONException {
            JSONObject out = new JSONObject();
            out.put("meter_id", meterId);
            out.put("read_before_epoch_ms", readBeforeEpochMs);
            out.put("read_after_epoch_ms", readAfterEpochMs);
            out.put("anchor_epoch_ms", anchorEpochMs);
            out.put("uncertainty_ms", uncertaintyMs);
            out.put("raw_meter_wall_clock", rawMeterWallClock);
            out.put("raw_type_f_hex", rawTypeFHex == null ? JSONObject.NULL : rawTypeFHex);
            out.put("type_f_iv", invalidTime);
            out.put("type_f_su", summerTime);
            out.put("on_time_seconds", onTimeSeconds);
            out.put("provenance", provenance);
            out.put("validation", validation);
            return out;
        }

        static AnchorRecord fromJson(JSONObject in) throws JSONException {
            return new AnchorRecord(
                    -1L,
                    in.getString("meter_id"),
                    in.getLong("read_before_epoch_ms"),
                    in.getLong("read_after_epoch_ms"),
                    in.getLong("anchor_epoch_ms"),
                    in.getLong("uncertainty_ms"),
                    in.getString("raw_meter_wall_clock"),
                    in.isNull("raw_type_f_hex") ? null : in.getString("raw_type_f_hex"),
                    in.getBoolean("type_f_iv"),
                    in.getBoolean("type_f_su"),
                    in.getLong("on_time_seconds"),
                    in.getString("provenance"),
                    in.getString("validation"));
        }
    }
}
