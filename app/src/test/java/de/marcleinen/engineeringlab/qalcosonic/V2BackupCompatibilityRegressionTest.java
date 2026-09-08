package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Locks the explicit 2.0.0 portable-data breaking-change contract. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class V2BackupCompatibilityRegressionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(context);
    }

    @Test public void schema1BackupIsRejectedBeforeExistingStateIsTouched() throws Exception {
        new MeterLifecycleStore(context).adoptInitialMeter("LOCAL");
        byte[] schema1 = schema1Backup();

        try {
            DataPortability.restoreBackup(context, schema1);
            fail("schema-1 backup must not be accepted by v2");
        } catch (IOException expected) {
            assertEquals("BACKUP_VERSION_UNSUPPORTED", expected.getMessage());
        }

        assertEquals("LOCAL", new MeterLifecycleStore(context).activeMeterId());
    }

    private static byte[] schema1Backup() throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("format", "QALCOSONIC_W1_BACKUP");
        manifest.put("schema_version", 1);
        manifest.put("created_utc", "2026-09-08T00:00:00Z");
        manifest.put("data_entry", "data.json");
        manifest.put("data_sha256", "unused-because-schema-is-rejected-first");

        JSONObject data = new JSONObject();
        data.put("schema_version", 1);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            write(zip, "manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
            write(zip, "data.json", data.toString().getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
