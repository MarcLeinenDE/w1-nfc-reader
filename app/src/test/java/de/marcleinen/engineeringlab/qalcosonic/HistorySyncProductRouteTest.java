package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Source-level product contract for the transitional Settings History surface. */
public final class HistorySyncProductRouteTest {
    @Test public void settingsOwnsExplicitHistorySyncEntry() throws Exception {
        String settings = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/SettingsActivity.java");
        assertTrue(settings.contains("new Intent(this, HistorySyncActivity.class)"));
        assertTrue(settings.contains("R.string.m3_sync_history"));
    }

    @Test public void historyActivityUsesImmediatePersistenceAndSafetyShellForAllExposedFamilies()
            throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("ArchivePersistenceCoordinator.beginImmediate"));
        assertTrue(activity.contains("ArchiveFamilyTransportAdapter.runMonth"));
        assertTrue(activity.contains("ArchiveFamilyProductionRunner.runDay"));
        assertTrue(activity.contains("ArchiveFamilyProductionRunner.runHour"));
        assertTrue(activity.contains("persistence::accept"));
        assertTrue(activity.contains("completeProductAttempt()"));
        assertTrue(activity.contains("FLAG_KEEP_SCREEN_ON"));
        assertTrue(activity.contains("SyncMode.INITIAL_FULL"));
        assertFalse(activity.contains("SyncMode.FULL_RESYNC"));
        assertFalse(activity.contains("SyncMode.INCREMENTAL"));
    }

    @Test public void dayAndHourAreExplicitButSyncAllRemainsDisabled() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("offerFamilySync(ArchiveFamilyPeriod.Family.DAY)"));
        assertTrue(activity.contains("offerFamilySync(ArchiveFamilyPeriod.Family.HOUR)"));
        assertTrue(activity.contains("allButton.setEnabled(false)"));
    }

    @Test public void exactValidatedMonthEntryPointIsStillUsed() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("ArchiveFamilyTransportAdapter.runMonth("));
    }

    @Test public void historyActivityIsNotExported() throws Exception {
        String manifest = read("app/src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android:name=\".HistorySyncActivity\" android:exported=\"false\""));
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
