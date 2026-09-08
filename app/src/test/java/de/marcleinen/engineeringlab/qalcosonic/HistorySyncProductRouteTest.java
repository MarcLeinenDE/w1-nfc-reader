package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Source-level product contract for the Settings History synchronization surface. */
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
        assertTrue(activity.contains("ArchiveIncrementalProductionRunner.run("));
        assertTrue(activity.contains("new ArchiveKnownRecordMatcher("));
        assertTrue(activity.contains("persistence.accept(period);"));
        assertTrue(activity.contains("ArchiveFamilyTransportAdapter.AcceptedPeriodSink progressSink"));
        assertTrue(activity.contains("progressSink"));
        assertTrue(activity.contains("completeProductAttempt()"));
        assertTrue(activity.contains("FLAG_KEEP_SCREEN_ON"));
        assertTrue(activity.contains("SyncMode.INITIAL_FULL"));
        assertTrue(activity.contains("SyncMode.INCREMENTAL"));
        assertTrue(activity.contains("SyncMode.FULL_RESYNC"));
    }

    @Test public void incrementalSnapshotIsTakenBeforeCurrentAttemptPersistence() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        int snapshot = activity.indexOf("new ArchiveKnownRecordMatcher(");
        int persistence = activity.indexOf("ArchivePersistenceCoordinator.beginImmediate(", snapshot);
        assertTrue(snapshot >= 0);
        assertTrue(persistence > snapshot);
    }

    @Test public void syncAllUsesValidatedFamilyOrderAndIndependentPerFamilyModes() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        int hour = activity.indexOf("ArchiveFamilyPeriod.Family.HOUR,\n            ArchiveFamilyPeriod.Family.DAY");
        int day = activity.indexOf("ArchiveFamilyPeriod.Family.DAY,\n            ArchiveFamilyPeriod.Family.MONTH");
        assertTrue(hour >= 0);
        assertTrue(day > hour);
        assertTrue(activity.contains("BETWEEN_FAMILY_RECONNECT_MS = 1500L"));
        assertTrue(activity.contains("SystemClock.sleep(BETWEEN_FAMILY_RECONNECT_MS)"));
        assertTrue(activity.contains("NfcV nfcv = NfcV.get(tag)"));
        assertTrue(activity.contains("HistorySyncAllPlanner.modeFor(existing, fullResync)"));
        assertTrue(activity.contains("HistorySyncAllPlanner.mayContinue("));
        assertFalse(activity.contains("SKIPPED_BASELINE_COMPLETE"));
        assertFalse(activity.contains("mode=ALL_BASELINES"));
    }

    @Test public void completedIndividualFamilyIsRoutedToIncrementalMode() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("current.baselineComplete()\n                ? ArchiveFamilySyncState.SyncMode.INCREMENTAL"));
        assertTrue(activity.contains("R.string.m3_sync_update_history"));
        assertTrue(activity.contains("armFamily(meter, family, mode)"));
    }

    @Test public void fullResyncIsSeparateAdvancedActionAndNeverReplacesNormalSyncAll() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        String planner = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncAllPlanner.java");

        assertTrue(activity.contains("fullResyncButton"));
        assertTrue(activity.contains("private void offerFullResync()"));
        assertTrue(activity.contains("armAll(meter, true)"));
        assertTrue(activity.contains("armAll(meter, false)"));
        assertTrue(activity.contains("HistorySyncAllPlanner.fullResyncAvailable"));
        assertTrue(activity.contains("runFullTraversal("));
        assertTrue(planner.contains("fullResync ? ArchiveFamilySyncState.SyncMode.FULL_RESYNC"));
        assertTrue(planner.contains("return state.baselineComplete()"));
    }

    @Test public void temporaryHistoryDebugSurfaceIsGone() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertFalse(activity.contains("TEMP DEBUG"));
        assertFalse(activity.contains("HistorySyncTemporaryDebug"));
        assertFalse(Files.exists(Paths.get(
                "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncTemporaryDebug.java")));
    }

    @Test public void syncAllIsAnExplicitUserActionAndRemainsAvailableForUpdates() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("allButton = actionButton(R.string.m3_filter_all, v -> offerAllSync())"));
        assertTrue(activity.contains("private void offerAllSync()"));
        assertTrue(activity.contains("R.string.v2_sync_all_update"));
        assertTrue(activity.contains("allButton.setEnabled(idle)"));
    }

    @Test public void exactValidatedMonthEntryPointIsStillUsedForFullTraversal() throws Exception {
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
