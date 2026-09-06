package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Regression guard: History sync is a Settings subpage, not a second top-level destination. */
public final class HistorySyncSubpageUiTest {
    @Test public void toolbarOwnsSinglePageTitleAndBackNavigation() throws Exception {
        String activity = read("app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java");
        assertTrue(activity.contains("toolbar.setTitle(R.string.m3_sync_history)"));
        assertTrue(activity.contains("toolbar.setNavigationIcon(R.drawable.ic_m3_back)"));
        assertTrue(activity.contains("toolbar.setNavigationContentDescription(R.string.m3_back)"));
        assertTrue(activity.contains("toolbar.setNavigationOnClickListener(v -> finish())"));
        assertFalse(activity.contains("content.addView(MaterialUi.headline(this, getString(R.string.m3_sync_history)))"));
    }

    private static String read(String path) throws Exception {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) return Files.readString(fromRoot, StandardCharsets.UTF_8);
        Path fromModule = Paths.get(path.replaceFirst("^app/", ""));
        if (Files.exists(fromModule)) return Files.readString(fromModule, StandardCharsets.UTF_8);
        throw new AssertionError("file not found: " + path);
    }
}
