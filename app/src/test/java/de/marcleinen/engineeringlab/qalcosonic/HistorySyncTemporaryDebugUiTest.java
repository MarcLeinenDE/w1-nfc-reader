package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Guards the intentionally temporary physical-validation UI and retired legacy sync route. */
public final class HistorySyncTemporaryDebugUiTest {
    @Test public void temporaryDebugIsDebugOnlySelectableAndPrivacyBounded() throws Exception {
        String activity = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));
        String debug = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncTemporaryDebug.java"));

        assertTrue(activity.contains("if (BuildConfig.DEBUG)"));
        assertTrue(activity.contains("debugText.setTextIsSelectable(true)"));
        assertTrue(activity.contains("HistorySyncTemporaryDebug.format("));
        assertTrue(debug.contains("TEMP DEBUG — wird vor Release entfernt"));
        assertTrue(debug.contains("effectiveStop"));
        assertTrue(debug.contains("selectedRequests"));
        assertTrue(debug.contains("acceptedRecords"));
        assertTrue(debug.contains("persistedAccepted"));
        assertTrue(debug.contains("archivePrepare"));
        assertTrue(debug.contains("preflightFailure"));
        assertTrue(debug.contains("finalFailure"));

        // Deliberately never expose the transport identifiers/payloads used by development traces.
        assertFalse(debug.contains("tag.getId()"));
        assertFalse(debug.contains("getTagId"));
        assertFalse(debug.contains("meterResponse"));
        assertFalse(debug.contains("rawFrame"));
        assertFalse(debug.contains("mbusFrame"));
    }

    @Test public void overviewLegacyHistoryRouteIsRemovedAfterV2Migration() throws Exception {
        String base = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/MaterialBaseActivity.java"));
        String dashboard = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java"));

        assertFalse(base.contains("LegacyOverviewHistoryActionHider"));
        assertFalse(dashboard.contains("HistorySyncFlow"));
        assertFalse(dashboard.contains("performHistoryContact("));
        assertFalse(dashboard.contains("MonthlyArchiveNfcWire"));
        assertFalse(dashboard.contains("MonthlyArchiveTransportAdapter"));
        assertFalse(Files.exists(projectPath(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/LegacyOverviewHistoryActionHider.java")));
        assertFalse(Files.exists(projectPath(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncFlow.java")));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path projectFile(String relative) {
        Path path = projectPath(relative);
        if (Files.exists(path)) return path;
        throw new AssertionError(relative + " not found from " + Paths.get("").toAbsolutePath());
    }

    private static Path projectPath(String relative) {
        if (Files.exists(Paths.get("src"))) return Paths.get(relative);
        return Paths.get("app").resolve(relative);
    }
}
