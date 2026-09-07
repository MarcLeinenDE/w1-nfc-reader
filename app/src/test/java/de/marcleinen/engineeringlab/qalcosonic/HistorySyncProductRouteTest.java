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
        // The user-visible progress wrapper must still persist first and only then count the record
        // as safely stored. Do not regress this to delayed/batch persistence for UI convenience.
        assertTrue(activity.contains("persistence.accept(period);"));
        assertTrue(activity.contains("ArchiveFamilyTransportAdapter.AcceptedPeriodSink progressSink"));
        assertTrue(activity.contains("progressSink"));
        assertTrue(activity.contains("completeProductAttempt()"));
        assertTrue(activity.contains("FLAG_KEEP_SCREEN_ON"));
        assertTrue(activity.contains("SyncMode.INITIAL_FULL"));
        assertTrue(activity.contains("SyncMode.INCREMENTAL"));
        assertFalse(activity.contains("SyncMode.FULL_RESYNC"));
    }

    @Test public void incrementalSnapshotIsTakenBeforeCurrentAttemptPersistence() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        int snapshot = activity.indexOf("new ArchiveKnownRecordMatcher(");
        int persistence = activity.indexOf("ArchivePersistenceCoordinator.beginImmediate(", snapshot);
        assertTrue(snapshot >= 0);
        assertTrue(persistence > snapshot);
        assertTrue(activity.contains("ArchiveIncrementalProductionRunner.REQUIRED_KNOWN_OVERLAP"));
    }

    @Test public void syncAllUsesValidatedFamilyOrderAndRemainsBaselineOnly() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        int hour = activity.indexOf("ArchiveFamilyPeriod.Family.HOUR,\n            ArchiveFamilyPeriod.Family.DAY");
        int day = activity.indexOf("ArchiveFamilyPeriod.Family.DAY,\n            ArchiveFamilyPeriod.Family.MONTH");
        assertTrue(hour >= 0);
        assertTrue(day > hour);
        assertTrue(activity.contains("BETWEEN_FAMILY_RECONNECT_MS = 1500L"));
        assertTrue(activity.contains("SystemClock.sleep(BETWEEN_FAMILY_RECONNECT_MS)"));
        assertTrue(activity.contains("NfcV nfcv = NfcV.get(tag)"));
        assertTrue(activity.contains("if (!attempt.complete)"));
        assertTrue(activity.contains("existing.baselineComplete()"));
        assertTrue(activity.contains("SKIPPED_BASELINE_COMPLETE"));
        assertTrue(activity.contains("mode=ALL_BASELINES"));
    }

    @Test public void completedIndividualFamilyIsRoutedToIncrementalMode() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("current.baselineComplete()\n                ? ArchiveFamilySyncState.SyncMode.INCREMENTAL"));
        assertTrue(activity.contains("R.string.m3_sync_update_history"));
        assertTrue(activity.contains("armFamily(meter, family, mode)"));
    }

    @Test public void syncAllIsAnExplicitUserAction() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("allButton = actionButton(R.string.m3_filter_all, v -> offerAllSync())"));
        assertTrue(activity.contains("private void offerAllSync()"));
        assertTrue(activity.contains("private void armAll(String meterId)"));
        assertFalse(activity.contains("allButton.setEnabled(false);\n        MaterialUi.addTopMargin"));
    }

    @Test public void exactValidatedMonthBaselineEntryPointIsStillUsed() throws Exception {
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
