package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistorySyncAllUiRegressionTest {
    @Test public void syncAllRunsEveryFamilyWithPerFamilyModeAndReconnect() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));

        assertTrue(source.contains("private void runAllFamilies(Tag tag, String expectedMeter)"));
        assertTrue(source.contains("HistorySyncAllPlanner.modeFor(existing)"));
        assertTrue(source.contains("SystemClock.sleep(BETWEEN_FAMILY_RECONNECT_MS)"));
        assertTrue(source.contains("performFamilyAttempt("));
        assertTrue(source.contains("mode,\n                        true,\n                        step"));
        assertTrue(source.contains("HistorySyncAllPlanner.mayContinue("));
        assertTrue(source.contains("STOP_DEFAULT_UNVERIFIED"));
        assertFalse(source.contains("SKIPPED_BASELINE_COMPLETE"));
        assertFalse(source.contains("mode=ALL_BASELINES"));
    }

    @Test public void syncAllRemainsAvailableAfterAllBaselinesAreComplete() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));

        assertTrue(source.contains("R.string.v2_sync_all_body"));
        assertTrue(source.contains("R.string.v2_sync_all_update"));
        assertTrue(source.contains("allButton.setEnabled(idle)"));
        assertFalse(source.contains("Snackbar.make(root, R.string.m3_not_available"));
        assertFalse(source.contains("allButton.setEnabled(idle\n                && (!hour.baselineComplete()"));
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
