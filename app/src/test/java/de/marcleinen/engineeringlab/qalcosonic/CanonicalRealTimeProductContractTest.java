package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the v2.1 decision that raw meter wall-clock is evidence, not a normal primary UI mode. */
public final class CanonicalRealTimeProductContractTest {
    private static final String BASE =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/MaterialBaseActivity.java";
    private static final String SETTINGS =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/SettingsActivity.java";

    @Test public void normalProductActivitiesNormalizeLegacyMeterPreferenceToLocal() throws Exception {
        String source = read(BASE);
        assertTrue(source.contains("UiPreferences.getTimeBasis(this) != AppTimeBasis.LOCAL"));
        assertTrue(source.contains("UiPreferences.setTimeBasis(this, AppTimeBasis.LOCAL)"));
    }

    @Test public void settingsNoLongerExposeRawMeterClockAsPeerTimeBasis() throws Exception {
        String source = read(SETTINGS);
        assertFalse(source.contains("chooseTimeBasis()"));
        assertFalse(source.contains("timeBasisSummary()"));
        assertFalse(source.contains("getString(R.string.v21_time_basis), timeBasisSummary()"));
    }

    @Test public void restoredDevelopmentBackupIsNormalizedBeforeSettingsRecreate() throws Exception {
        String source = read(SETTINGS);
        assertTrue(source.contains("DataPortabilityV3.restoreBackup(this, preview.bytes)"));
        assertTrue(source.contains("UiPreferences.setTimeBasis(this, AppTimeBasis.LOCAL)"));
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
        throw new AssertionError("file not found: " + path + " from " + Paths.get("").toAbsolutePath());
    }
}
