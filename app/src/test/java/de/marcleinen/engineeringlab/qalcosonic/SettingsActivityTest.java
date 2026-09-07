package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class SettingsActivityTest {
    @Test public void backupExportContractPreservesQw1backupExtension() {
        assertEquals("application/octet-stream", SettingsActivity.BACKUP_CREATE_MIME);
        String fileName = SettingsActivity.backupFileName();
        assertTrue(fileName.endsWith(".qw1backup"));
        assertFalse(fileName.endsWith(".zip"));
    }

    @Test public void completeBackupStateIsPresentedAsBaselineNotCurrentSync() throws Exception {
        String settings = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/SettingsActivity.java");
        assertTrue(settings.contains("return getString(R.string.m3_history_baseline_complete);"));
        assertFalse(settings.contains("return getString(R.string.m3_state_sync_ok_title);"));
    }

    private static String read(String path) throws Exception {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return new String(Files.readAllBytes(fromRoot), StandardCharsets.UTF_8);
        }
        Path fromModule = Paths.get(path.replaceFirst("^app/", ""));
        if (Files.exists(fromModule)) {
            return new String(Files.readAllBytes(fromModule), StandardCharsets.UTF_8);
        }
        throw new AssertionError("file not found: " + path);
    }
}
