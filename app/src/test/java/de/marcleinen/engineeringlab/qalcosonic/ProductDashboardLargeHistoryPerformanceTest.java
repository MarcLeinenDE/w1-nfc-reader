package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Regression contract for the real-device large-history ANR found after the first H+D+M baseline. */
public final class ProductDashboardLargeHistoryPerformanceTest {
    @Test public void historyUsesVirtualizedRowsInsteadOfMaterializingEveryCard() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(activity.contains("private ListView historyList;"));
        assertTrue(activity.contains("private final class HistoryListAdapter extends BaseAdapter"));
        assertTrue(activity.contains("historyAdapter.setEntries(timeline);"));
        assertFalse(activity.contains("historyList.removeAllViews()"));
        assertFalse(activity.contains("MaterialUi.addTopMargin(historyList"));
    }

    @Test public void hiddenPagesAreNotRebuiltDuringEveryDashboardRefresh() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(activity.contains("if (currentPage == PAGE_HISTORY) refreshHistory(items, transitions);"));
        assertTrue(activity.contains("if (currentPage == PAGE_STATS)"));
        assertTrue(activity.contains("refreshStatistics(monthly, latestLivePoint, statistics, consumption);"));
        assertTrue(activity.contains("refreshAll();\n    }\n\n    int currentNavigationItemId()"));
    }

    @Test public void overviewAndStatisticsAvoidLoadingHourDayRowsWithoutReplacementNeed() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(activity.contains("boolean needsAllArchiveFamilies = currentPage == PAGE_HISTORY || !transitions.isEmpty();"));
        assertTrue(activity.contains("needsAllArchiveFamilies ? null : ArchiveFamilyPeriod.Family.MONTH"));
        assertTrue(activity.contains("SELECT COUNT(*), MAX(retrieved_at_ms)"));
        assertTrue(activity.contains("ArchiveFamilyStore.TABLE_PERIODS"));
    }

    @Test public void historyFamilyFiltersReuseLoadedDatasetInsteadOfRequeryingSqlite() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
        assertTrue(activity.contains("historyItemsCache"));
        assertTrue(activity.contains("historyTransitionsCache"));
        assertTrue(activity.contains("refreshHistory(historyItemsCache, historyTransitionsCache);"));
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
