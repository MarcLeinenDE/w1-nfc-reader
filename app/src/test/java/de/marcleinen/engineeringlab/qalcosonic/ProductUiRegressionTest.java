package de.marcleinen.engineeringlab.qalcosonic;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ProductUiRegressionTest {
    @Before public void setUp() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @After public void tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test public void dashboardHasNoHiddenLegacyNavigationDispatcher() throws Exception {
        String source = read(dashboardSource());
        assertFalse(source.contains("BottomNavigationView"));
        assertFalse(source.contains("bottomNavigation"));
        assertFalse(source.contains("MENU_DETAILS"));
        assertFalse(source.contains("MENU_SETTINGS"));
        assertFalse(source.contains("toolbar.setSubtitle("));
        assertFalse(source.contains("setOnMenuItemClickListener("));
        assertTrue(source.contains("void selectNavigationItem(int navItemId)"));
        assertTrue(source.contains("int currentNavigationItemId()"));
    }

    @Test public void hamburgerDrawerIsTheOnlyVisibleTopLevelNavigation() throws Exception {
        String drawer = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDrawerNavigation.java"));
        String base = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/MaterialBaseActivity.java"));
        String settings = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/SettingsActivity.java"));
        String details = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/MeterDetailsActivity.java"));
        String about = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/AboutActivity.java"));
        String diagnostics = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/DiagnosticsActivity.java"));
        String dayStyle = read(projectFile("src/main/res/values/styles.xml"));
        String nightStyle = read(projectFile("src/main/res/values-night/styles.xml"));
        String appStrings = read(projectFile("src/main/res/values/strings.xml"));
        String germanBrand = read(projectFile("src/main/res/values-de/brand_strings.xml"));

        assertFalse(drawer.contains("BottomNavigationView"));
        assertFalse(drawer.contains("bottomNavigation"));
        assertTrue(drawer.contains("toolbar.getMenu().clear()"));
        assertTrue(drawer.contains("toolbar.setNavigationIcon(R.drawable.ic_m3_menu)"));
        assertTrue(drawer.contains("toolbar.setSubtitle((CharSequence) null)"));
        assertTrue(drawer.contains("getString(R.string.m3_app_subtitle)"));
        assertTrue(drawer.contains("new NavigationView(activity)"));
        assertTrue(drawer.contains("new DrawerLayout(activity)"));
        assertTrue(drawer.contains("activity instanceof MeterDetailsActivity"));
        assertTrue(drawer.contains("activity instanceof SettingsActivity"));

        // Cross-Activity navigation must wait for the animated drawer close. Otherwise the
        // secondary Activities appear to lose the scrim/fade compared with dashboard pages.
        assertTrue(drawer.contains("drawerLayout.setScrimColor("));
        assertTrue(drawer.contains("closeThenRun(drawerLayout, afterDrawerClose"));
        assertTrue(drawer.contains("afterDrawerClose[0] = action"));
        assertTrue(drawer.contains("onDrawerClosed(View drawerView)"));
        assertTrue(drawer.contains("Runnable pending = afterDrawerClose[0]"));
        assertTrue(drawer.contains("closeDrawer(GravityCompat.START, true)"));

        assertTrue(base.contains("this instanceof ProductDashboardActivity"));
        assertTrue(base.contains("this instanceof MeterDetailsActivity"));
        assertTrue(base.contains("this instanceof SettingsActivity"));
        assertTrue(base.contains("ProductDrawerNavigation.attach(this)"));
        assertTrue(base.contains("ProductUiHardening.attach(this)"));
        assertTrue(base.contains("SeasonalSupportPrompt.attach((ProductDashboardActivity) this)"));

        assertFalse(settings.contains("setNavigationIcon(R.drawable.ic_m3_back)"));
        assertFalse(settings.contains("setNavigationOnClickListener(v -> finish())"));
        assertTrue(settings.contains("MaterialUi.headline(this, getString(R.string.m3_settings_title))"));
        assertTrue(settings.contains("scroll.setFillViewport(true)"));
        assertTrue(settings.contains("colorSurface"));
        assertFalse(details.contains("setNavigationIcon(R.drawable.ic_m3_back)"));
        assertFalse(details.contains("setNavigationOnClickListener(v -> finish())"));
        assertTrue(details.contains("MaterialUi.headline(this, getString(R.string.m3_meter_details))"));
        assertTrue(details.contains("row(R.string.m3_details_meter_number"));
        assertFalse(details.contains("row(R.string.m3_details_serial"));
        assertTrue(details.contains("scroll.setFillViewport(true)"));
        assertTrue(details.contains("colorSurface"));

        // Secondary pages intentionally keep back navigation but share the same surface/scroll
        // treatment as the main product surface.
        assertTrue(about.contains("setNavigationIcon(R.drawable.ic_m3_back)"));
        assertTrue(about.contains("scroll.setFillViewport(true)"));
        assertTrue(about.contains("colorSurface"));
        assertTrue(diagnostics.contains("setNavigationIcon(R.drawable.ic_m3_back)"));
        assertTrue(diagnostics.contains("scroll.setFillViewport(true)"));
        assertTrue(diagnostics.contains("colorSurface"));
        assertFalse(diagnostics.contains("addCard(content, R.string.m3_about"));

        assertFalse(dayStyle.contains("bottomNavigationStyle"));
        assertFalse(nightStyle.contains("bottomNavigationStyle"));
        assertTrue(appStrings.contains(">W1 NFC Reader<"));
        assertTrue(germanBrand.contains("NFC-Auslesung für kompatible W1-Wasserzähler"));
        assertTrue(germanBrand.contains(">Zählernummer<"));
    }

    @Test public void overviewShareUsesOnlyLatestSuccessfulLiveReading() throws Exception {
        String base = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/MaterialBaseActivity.java"));
        String share = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/LiveShareAction.java"));

        assertTrue(base.contains("LiveShareAction.attach(this)"));
        assertTrue(share.contains("R.string.m3_meter_reading"));
        assertTrue(share.contains("R.drawable.ic_m3_share"));
        assertTrue(share.contains("new LiveReadMetadataStore(activity).get(meterId)"));
        assertTrue(share.contains("live.getReadings(meterId, 0L)"));
        assertTrue(share.contains("Intent.ACTION_SEND"));
        assertTrue(share.contains("Intent.createChooser"));
        assertTrue(share.contains("R.string.m3_share_live_text"));
        assertFalse(share.contains("ArchiveFamilyStore"));
        assertFalse(share.contains("MonthlyArchive"));
    }

    // This test intentionally guards release identity/privacy policy as source invariants.
    @Test public void publicReleaseIdentityAndPrivacyBoundaryAreLocked() throws Exception {
        String build = read(appBuildGradle());
        String manifest = read(projectFile("src/main/AndroidManifest.xml"));

        assertTrue(build.contains("applicationId = \"de.marcleinen.w1nfcreader\""));
        assertTrue(build.contains("versionCode = 43"));
        assertTrue(build.contains("versionName = \"2.0.0\""));

        assertTrue(manifest.contains("android.permission.NFC"));
        assertFalse(manifest.contains("android.permission.INTERNET"));
        assertTrue(manifest.contains("android:allowBackup=\"false\""));
    }

    @Test public void themePreferenceMapsToAppCompatDayNightModes() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                UiPreferences.appCompatNightModeForTheme(UiPreferences.THEME_SYSTEM));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                UiPreferences.appCompatNightModeForTheme(UiPreferences.THEME_LIGHT));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                UiPreferences.appCompatNightModeForTheme(UiPreferences.THEME_DARK));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path dashboardSource() {
        return projectFile("src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java");
    }

    private static Path appBuildGradle() {
        Path fromRoot = Paths.get("app/build.gradle.kts");
        if (Files.exists(fromRoot)) return fromRoot;
        Path fromModule = Paths.get("build.gradle.kts");
        if (Files.exists(fromModule)) return fromModule;
        throw new AssertionError("app/build.gradle.kts not found from " + Paths.get("").toAbsolutePath());
    }

    private static Path projectFile(String relative) {
        Path module = Paths.get(relative);
        if (Files.exists(module)) return module;
        Path root = Paths.get("app").resolve(relative);
        if (Files.exists(root)) return root;
        throw new AssertionError(relative + " not found from " + Paths.get("").toAbsolutePath());
    }
}
