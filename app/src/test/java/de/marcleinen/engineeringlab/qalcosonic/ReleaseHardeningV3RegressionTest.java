package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReleaseHardeningV3RegressionTest {
    @Test public void historyStatisticsKeepsUiStateAcrossRotation() throws Exception {
        String manifest = read(projectFile("src/main/AndroidManifest.xml"));
        int activity = manifest.indexOf("android:name=\".HistoryStatisticsActivity\"");
        assertTrue(activity >= 0);
        String tail = manifest.substring(activity, Math.min(manifest.length(), activity + 320));
        assertTrue(tail.contains("android:configChanges=\"orientation|screenSize\""));
    }

    @Test public void voluntarySupportStaysExternalAndOffline() throws Exception {
        String manifest = read(projectFile("src/main/AndroidManifest.xml"));
        String support = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/DeveloperSupport.java"));
        String seasonal = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/SeasonalSupportPrompt.java"));
        String about = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/AboutActivity.java"));

        assertFalse(manifest.contains("android.permission.INTERNET"));
        assertTrue(support.contains("Intent.ACTION_VIEW"));
        assertTrue(support.contains("https://www.paypal.me/ccaa/"));
        assertFalse(support.contains("WebView"));
        assertFalse(support.toLowerCase().contains("paypal sdk"));
        assertTrue(about.contains("DeveloperSupport.openExternal(this)"));
        assertTrue(seasonal.contains("DeveloperSupport.openExternal(activity)"));
    }

    @Test public void christmasEligibilityComesOnlyFromSuccessfulLiveMetadataRecord() throws Exception {
        String live = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/LiveReadMetadataStore.java"));
        String seasonal = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/SeasonalSupportPrompt.java"));

        assertTrue(live.contains("SeasonalSupportPrompt.recordSuccessfulNormalLive(appContext, readAtMs)"));
        assertTrue(seasonal.contains("claimFirstDashboardInProcess()"));
        assertTrue(seasonal.contains("isNormalLauncherIntent(activity.getIntent())"));
        assertTrue(seasonal.contains("putInt(KEY_HANDLED_YEAR, year)"));
        assertFalse(seasonal.contains("isPaid"));
        assertFalse(seasonal.contains("paymentResult"));
    }

    @Test public void diagnosticsDoesNotRepeatGenericPrivacyExplanationFromAbout() throws Exception {
        String diagnostics = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/DiagnosticsActivity.java"));
        assertFalse(diagnostics.contains("R.string.v2_diag_privacy_title"));
        assertFalse(diagnostics.contains("R.string.v2_diag_privacy_body"));
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
