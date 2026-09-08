package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistorySyncAllUiRegressionTest {
    @Test public void combinedSyncRunsEveryFamilyWithModePolicyAndReconnect() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));

        assertTrue(source.contains(
                "private void runAllFamilies(Tag tag, String expectedMeter, boolean fullResync)"));
        assertTrue(source.contains("HistorySyncAllPlanner.modeFor(existing, fullResync)"));
        assertTrue(source.contains("SystemClock.sleep(BETWEEN_FAMILY_RECONNECT_MS)"));
        assertTrue(source.contains("performFamilyAttempt("));
        assertTrue(source.contains("HistorySyncAllPlanner.mayContinue("));
        assertFalse(source.contains("SKIPPED_BASELINE_COMPLETE"));
        assertFalse(source.contains("mode=ALL_BASELINES"));
    }

    @Test public void normalSyncAllRemainsAvailableAfterAllBaselinesAreComplete() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));

        assertTrue(source.contains("R.string.v2_sync_all_body"));
        assertTrue(source.contains("R.string.v2_sync_all_update"));
        assertTrue(source.contains("armAll(meter, false)"));
        assertTrue(source.contains("allButton.setEnabled(idle)"));
        assertFalse(source.contains("SKIPPED_BASELINE_COMPLETE"));
    }

    @Test public void fullResyncUsesFullModeWithoutChangingNormalPlannerPolicy() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));
        String planner = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncAllPlanner.java"));

        assertTrue(source.contains("ArchiveFamilySyncState.SyncMode.FULL_RESYNC"));
        assertTrue(source.contains("armAll(meter, true)"));
        assertTrue(source.contains("runFullTraversal("));
        assertTrue(planner.contains("fullResync ? ArchiveFamilySyncState.SyncMode.FULL_RESYNC"));
        assertTrue(planner.contains("? ArchiveFamilySyncState.SyncMode.INCREMENTAL"));
        assertTrue(planner.contains(": ArchiveFamilySyncState.SyncMode.INITIAL_FULL"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path projectFile(String relative) {
        Path module = Paths.get(relative);
        if (Files.exists(module)) return module;
        Path root = Paths.get("app").resolve(relative);
        if (Files.exists(root)) return root;
        throw new AssertionError(relative + " not found from " + Paths.get("").toAbsolutePath());
    }
}
