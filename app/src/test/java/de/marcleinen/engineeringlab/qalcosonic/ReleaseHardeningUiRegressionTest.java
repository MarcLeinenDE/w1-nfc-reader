package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Guards the v2 release-hardening UI/navigation contracts that do not require a physical meter. */
public final class ReleaseHardeningUiRegressionTest {
    @Test public void drawerDestinationsPreserveAndroidBackStack() throws Exception {
        String source = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDrawerNavigation.java"));

        assertFalse(source.contains("activity.finish()"));
        assertTrue(source.contains("FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP"));
        assertTrue(source.contains("openHistoryStatistics(activity, id)"));
        assertTrue(source.contains("activity.startActivity(new Intent(activity, target))"));
        assertTrue(source.contains("historyStatistics.currentNavigationItemId() == id"));
    }

    @Test public void periodAndFilterControlsUseCompactIcons() throws Exception {
        String helper = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductUiHardening.java"));

        assertTrue(helper.contains("R.drawable.ic_m3_chevron_back"));
        assertTrue(helper.contains("R.drawable.ic_m3_chevron"));
        assertTrue(helper.contains("R.drawable.ic_m3_filter"));
        assertTrue(helper.contains("MaterialUi.dp(activity, 48)"));
        assertTrue(Files.exists(projectPath("src/main/res/drawable/ic_m3_chevron_back.xml")));
        assertTrue(Files.exists(projectPath("src/main/res/drawable/ic_m3_filter.xml")));
    }

    @Test public void temporaryDebugCompatibilityIsGoneFromStableSurface() throws Exception {
        String helper = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductUiHardening.java"));
        String sync = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistorySyncActivity.java"));
        assertFalse(helper.contains("TEMP DEBUG"));
        assertFalse(helper.contains("removeCardWithExactText"));
        assertFalse(sync.contains("TEMP DEBUG"));
    }

    @Test public void diagnosticsNoLongerPresentsMonthOnlyAsValidatedScope() throws Exception {
        String diagnostics = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/DiagnosticsActivity.java"));
        assertTrue(diagnostics.contains("R.string.v2_diag_protocol_value"));
        assertTrue(diagnostics.contains("ArchiveFamilyPeriod.Family.HOUR"));
        assertTrue(diagnostics.contains("ArchiveFamilyPeriod.Family.DAY"));
        assertTrue(diagnostics.contains("ArchiveFamilyPeriod.Family.MONTH"));
        assertFalse(diagnostics.contains("m3_diagnostics_other_families"));
    }

    @Test public void debugBuildAboutUsesPublicRepositoryFallback() throws Exception {
        String gradle = read(projectFile("build.gradle.kts"));
        assertTrue(gradle.contains("https://github.com/MarcLeinenDE/w1-nfc-reader"));
        assertTrue(gradle.contains("versionName = \"2.0.0\""));
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
