package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * v2.1 lossless backup facade.
 *
 * <p>The proven v2 core portability implementation remains the compatibility engine for all
 * pre-v2.1 product data. Schema 3 wraps that payload with the durable per-meter time model. This
 * keeps legacy schema-2 restore support while preventing new timezone/anchor evidence from living
 * outside the canonical *.qw1backup.</p>
 */
final class DataPortabilityV3 {
    static final int BACKUP_SCHEMA = 3;
    static final int LEGACY_BACKUP_SCHEMA = 2;

    private static final int MAX_BACKUP_BYTES = 50 * 1024 * 1024;
    private static final String FORMAT = "QALCOSONIC_W1_BACKUP";
    private static final String MANIFEST = "manifest.json";
    private static final String DATA = "data.json";
    private static final String TIME_MODEL = "meter_time_model";

    private DataPortabilityV3() { }

    static void writeBackup(Context context, OutputStream output) throws IOException, JSONException {
        byte[] legacyBytes = writeLegacyBackup(context);
        ParsedBackup legacy = parse(legacyBytes);
        if (legacy.schemaVersion != LEGACY_BACKUP_SCHEMA) {
            throw new IOException("LEGACY_BACKUP_SCHEMA_UNEXPECTED");
        }

        JSONObject data = cloneJson(legacy.data);
        data.put("schema_version", BACKUP_SCHEMA);
        JSONObject timeModel;
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            timeModel = timeStore.exportJson();
        } finally {
            timeStore.close();
        }
        data.put(TIME_MODEL, timeModel);

        byte[] dataBytes = data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject manifest = cloneJson(legacy.manifest);
        manifest.put("schema_version", BACKUP_SCHEMA);
        manifest.put("data_sha256", sha256(dataBytes));
        manifest.put("meter_time_profiles", timeModel.getJSONArray("profiles").length());
        manifest.put("meter_time_anchors", timeModel.getJSONArray("anchors").length());

        writeZip(output, manifest, dataBytes);
    }

    static DataPortability.BackupPreview inspectBackup(InputStream input)
            throws IOException, JSONException {
        byte[] bytes = readLimited(input, MAX_BACKUP_BYTES);
        ParsedBackup parsed = parse(bytes);
        if (parsed.schemaVersion == LEGACY_BACKUP_SCHEMA) {
            return DataPortability.inspectBackup(new ByteArrayInputStream(bytes));
        }
        if (parsed.schemaVersion != BACKUP_SCHEMA) throw new IOException("BACKUP_VERSION_UNSUPPORTED");

        byte[] legacyCore = downgradeToLegacyCore(parsed);
        DataPortability.BackupPreview core = DataPortability.inspectBackup(
                new ByteArrayInputStream(legacyCore));
        return new DataPortability.BackupPreview(
                core.createdUtc,
                core.activeMeterId,
                core.liveReadings,
                core.archivePeriods,
                core.replacementTransitions,
                core.hourPeriods,
                core.dayPeriods,
                core.monthPeriods,
                core.hourBaselineState,
                core.dayBaselineState,
                core.monthBaselineState,
                bytes);
    }

    static void restoreBackup(Context context, byte[] backupBytes) throws IOException, JSONException {
        ParsedBackup incoming = parse(backupBytes);
        if (incoming.schemaVersion == LEGACY_BACKUP_SCHEMA) {
            // Legacy data contains no trustworthy per-meter ZoneId/SU/anchor evidence. Restore the
            // proven core only and deliberately leave the local v2.1 time model untouched.
            DataPortability.restoreBackup(context, backupBytes);
            return;
        }
        if (incoming.schemaVersion != BACKUP_SCHEMA) throw new IOException("BACKUP_VERSION_UNSUPPORTED");

        byte[] coreBefore = writeLegacyBackup(context);
        JSONObject timeBefore;
        MeterTimeModelStore store = new MeterTimeModelStore(context);
        try {
            timeBefore = store.exportJson();
        } finally {
            store.close();
        }

        try {
            DataPortability.restoreBackup(context, downgradeToLegacyCore(incoming));
            MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
            try {
                timeStore.mergeJson(incoming.data.getJSONObject(TIME_MODEL));
            } finally {
                timeStore.close();
            }
        } catch (Exception error) {
            try {
                DataPortability.clearMeterData(context);
                MeterTimeModelStore rollbackStore = new MeterTimeModelStore(context);
                try {
                    rollbackStore.clear();
                } finally {
                    rollbackStore.close();
                }
                DataPortability.restoreBackup(context, coreBefore);
                MeterTimeModelStore restoreStore = new MeterTimeModelStore(context);
                try {
                    restoreStore.restoreJson(timeBefore);
                } finally {
                    restoreStore.close();
                }
            } catch (Exception rollback) {
                error.addSuppressed(rollback);
            }
            if (error instanceof IOException) throw (IOException) error;
            if (error instanceof JSONException) throw (JSONException) error;
            throw new IOException("BACKUP_IMPORT_FAILED", error);
        }
    }

    /** Full product-data clear for v2.1 callers. UI language/theme remain untouched. */
    static void clearMeterData(Context context) {
        DataPortability.clearMeterData(context);
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            timeStore.clear();
        } finally {
            timeStore.close();
        }
    }

    private static byte[] writeLegacyBackup(Context context) throws IOException, JSONException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPortability.writeBackup(context, out);
        return out.toByteArray();
    }

    private static byte[] downgradeToLegacyCore(ParsedBackup parsed)
            throws IOException, JSONException {
        if (parsed.schemaVersion != BACKUP_SCHEMA) {
            throw new IOException("SCHEMA3_REQUIRED_FOR_DOWNGRADE");
        }
        JSONObject data = cloneJson(parsed.data);
        data.remove(TIME_MODEL);
        data.put("schema_version", LEGACY_BACKUP_SCHEMA);
        byte[] dataBytes = data.toString().getBytes(StandardCharsets.UTF_8);

        JSONObject manifest = cloneJson(parsed.manifest);
        manifest.put("schema_version", LEGACY_BACKUP_SCHEMA);
        manifest.remove("meter_time_profiles");
        manifest.remove("meter_time_anchors");
        manifest.put("data_sha256", sha256(dataBytes));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeZip(out, manifest, dataBytes);
        return out.toByteArray();
    }

    private static ParsedBackup parse(byte[] bytes) throws IOException, JSONException {
        Map<String, byte[]> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8);
        ZipEntry entry;
        int total = 0;
        byte[] buffer = new byte[8192];
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (!(MANIFEST.equals(name) || DATA.equals(name))) continue;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int n;
            while ((n = zip.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BACKUP_BYTES) throw new IOException("BACKUP_TOO_LARGE");
                out.write(buffer, 0, n);
            }
            if (entries.put(name, out.toByteArray()) != null) {
                throw new IOException("DUPLICATE_BACKUP_ENTRY");
            }
        }
        if (!entries.containsKey(MANIFEST) || !entries.containsKey(DATA)) {
            throw new IOException("BACKUP_ENTRY_MISSING");
        }

        JSONObject manifest = new JSONObject(
                new String(entries.get(MANIFEST), StandardCharsets.UTF_8));
        if (!FORMAT.equals(manifest.optString("format"))) throw new IOException("BACKUP_FORMAT_UNSUPPORTED");
        int schema = manifest.optInt("schema_version", -1);
        if (schema != LEGACY_BACKUP_SCHEMA && schema != BACKUP_SCHEMA) {
            throw new IOException("BACKUP_VERSION_UNSUPPORTED");
        }
        if (!DATA.equals(manifest.optString("data_entry"))) throw new IOException("BACKUP_DATA_ENTRY_UNSUPPORTED");

        byte[] dataBytes = entries.get(DATA);
        if (!sha256(dataBytes).equalsIgnoreCase(manifest.optString("data_sha256"))) {
            throw new IOException("BACKUP_CHECKSUM_MISMATCH");
        }
        JSONObject data = new JSONObject(new String(dataBytes, StandardCharsets.UTF_8));
        if (data.optInt("schema_version", -1) != schema) {
            throw new IOException("BACKUP_SCHEMA_MISMATCH");
        }
        if (schema == BACKUP_SCHEMA) validateTimeModel(data.getJSONObject(TIME_MODEL));
        return new ParsedBackup(schema, manifest, data);
    }

    private static void validateTimeModel(JSONObject timeModel) throws JSONException {
        if (timeModel.optInt("schema_version", -1) != MeterTimeModelStore.JSON_SCHEMA) {
            throw new JSONException("unsupported meter time model schema");
        }
        JSONArray profiles = timeModel.getJSONArray("profiles");
        JSONArray anchors = timeModel.getJSONArray("anchors");
        // Validate every portable object now so malformed backups fail at preview/inspect time,
        // before the user confirms a restore.
        for (int i = 0; i < profiles.length(); i++) {
            MeterTimeModelStore.Profile.fromJson(profiles.getJSONObject(i));
        }
        for (int i = 0; i < anchors.length(); i++) {
            MeterTimeModelStore.AnchorRecord.fromJson(anchors.getJSONObject(i));
        }
    }

    private static JSONObject cloneJson(JSONObject source) throws JSONException {
        return new JSONObject(source.toString());
    }

    private static void writeZip(OutputStream output, JSONObject manifest, byte[] dataBytes)
            throws IOException, JSONException {
        ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(output), StandardCharsets.UTF_8);
        writeEntry(zip, MANIFEST, manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        writeEntry(zip, DATA, dataBytes);
        zip.finish();
        zip.flush();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException("BACKUP_TOO_LARGE");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(java.util.Locale.US, "%02x", b & 0xFF));
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class ParsedBackup {
        final int schemaVersion;
        final JSONObject manifest;
        final JSONObject data;

        ParsedBackup(int schemaVersion, JSONObject manifest, JSONObject data) {
            this.schemaVersion = schemaVersion;
            this.manifest = manifest;
            this.data = data;
        }
    }
}
