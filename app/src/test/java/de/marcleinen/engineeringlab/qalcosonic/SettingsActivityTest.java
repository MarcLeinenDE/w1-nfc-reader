package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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
}
