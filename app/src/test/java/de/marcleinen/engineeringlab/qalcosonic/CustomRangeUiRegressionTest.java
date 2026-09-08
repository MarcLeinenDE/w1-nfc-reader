package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class CustomRangeUiRegressionTest {
    @Test public void historyAndStatisticsUseCompactCustomRangeFlow() throws Exception {
        String activity = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryStatisticsActivity.java"));
        String picker = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryRangePickerUi.java"));

        assertTrue(activity.contains("HistoryRangePickerUi.show("));
        assertTrue(activity.contains("HistoryCustomRangeSemantics.automaticResolution("));
        assertTrue(activity.contains("repository.queryStatistics(window, effective)"));
        assertTrue(activity.contains("HistoryCustomRangeSemantics.expectedFullBuckets(window, effective)"));
        assertTrue(activity.contains("R.string.v2_kpi_contained_total"));
        assertTrue(activity.contains("R.string.v2_range_edges_not_exact"));
        assertTrue(picker.contains("new DatePickerDialog("));
        assertTrue(picker.contains("new TimePickerDialog("));
        assertTrue(picker.contains("navigator.setCustomRange(start[0], end[0])"));
    }

    @Test public void dashboardShareAndBackGuardStayCompactAndDrawerSafe() throws Exception {
        String share = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/LiveShareAction.java"));
        String base = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/MaterialBaseActivity.java"));
        String guard = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/DashboardExitGuard.java"));

        assertTrue(share.contains("heroContent.removeView(lastRead)"));
        assertTrue(share.contains("heroContent.removeView(meter)"));
        assertTrue(share.contains("MaterialUi.dp(activity, 48), MaterialUi.dp(activity, 48)"));
        assertTrue(share.contains("Intent.ACTION_SEND"));
        int exitGuard = base.indexOf("DashboardExitGuard.attach");
        int drawer = base.indexOf("ProductDrawerNavigation.attach");
        assertTrue(exitGuard >= 0 && drawer > exitGuard);
        assertTrue(guard.contains("SECOND_BACK_WINDOW_MS = 2000L"));
        assertTrue(guard.contains("R.string.v2_back_again_to_exit"));
        assertTrue(guard.contains("activity.getOnBackPressedDispatcher().onBackPressed()"));
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
