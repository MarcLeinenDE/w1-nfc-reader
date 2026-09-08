package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Locks the shared Warm White + Graphite + Muted Red color-only design contract. */
public final class ColorDesignRegressionTest {
    @Test public void lightAndDarkPalettesUseSharedBrandRoles() throws Exception {
        String light = read(projectFile("src/main/res/values/colors.xml"));
        String dark = read(projectFile("src/main/res/values-night/colors.xml"));

        assertTrue(light.contains("<color name=\"app_primary\">#B64A4D</color>"));
        assertTrue(light.contains("<color name=\"app_surface\">#FFF9F8</color>"));
        assertTrue(light.contains("<color name=\"app_surface_container_low\">#FFFCFB</color>"));
        assertTrue(light.contains("<color name=\"app_on_surface\">#251B1C</color>"));
        assertTrue(light.contains("<color name=\"app_outline\">#8A7778</color>"));
        assertTrue(light.contains("<color name=\"app_outline_variant\">#D8C5C5</color>"));

        assertTrue(dark.contains("<color name=\"app_primary\">#EF686B</color>"));
        assertTrue(dark.contains("<color name=\"app_surface\">#121010</color>"));
        assertTrue(dark.contains("<color name=\"app_surface_container_low\">#1B1717</color>"));
        assertTrue(dark.contains("<color name=\"app_on_surface\">#FFF8F7</color>"));
        assertTrue(dark.contains("<color name=\"app_outline\">#9A8586</color>"));
        assertTrue(dark.contains("<color name=\"app_outline_variant\">#594A4B</color>"));

        assertFalse(light.contains("#0061A4"));
        assertFalse(dark.contains("#9ECAFF"));
    }

    @Test public void semanticStatusColorsStaySeparateFromBrandPrimary() throws Exception {
        String light = read(projectFile("src/main/res/values/colors.xml"));
        String dark = read(projectFile("src/main/res/values-night/colors.xml"));
        String dashboard = read(projectFile(
                "src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java"));

        assertTrue(light.contains("<color name=\"app_success\">#2E6B3A</color>"));
        assertTrue(light.contains("<color name=\"app_warning\">#8A5A00</color>"));
        assertTrue(light.contains("<color name=\"app_error\">#B3261E</color>"));
        assertTrue(dark.contains("<color name=\"app_success\">#9CD49D</color>"));
        assertTrue(dark.contains("<color name=\"app_warning\">#FFC56D</color>"));
        assertTrue(dark.contains("<color name=\"app_error\">#FFB4AB</color>"));

        assertTrue(dashboard.contains("R.color.app_success"));
        assertTrue(dashboard.contains("R.attr.colorError"));
    }

    @Test public void material3ThemeExplicitlyMapsSurfaceAndContainerRoles() throws Exception {
        String lightStyle = read(projectFile("src/main/res/values/styles.xml"));
        String darkStyle = read(projectFile("src/main/res/values-night/styles.xml"));

        for (String style : new String[] { lightStyle, darkStyle }) {
            assertTrue(style.contains("<item name=\"colorPrimary\">@color/app_primary</item>"));
            assertTrue(style.contains("<item name=\"colorSecondary\">@color/app_secondary</item>"));
            assertTrue(style.contains("<item name=\"colorTertiary\">@color/app_tertiary</item>"));
            assertTrue(style.contains("<item name=\"colorSurfaceContainerLow\">@color/app_surface_container_low</item>"));
            assertTrue(style.contains("<item name=\"colorSurfaceContainer\">@color/app_surface_container</item>"));
            assertTrue(style.contains("<item name=\"colorSurfaceContainerHigh\">@color/app_surface_container_high</item>"));
            assertTrue(style.contains("<item name=\"colorSurfaceContainerHighest\">@color/app_surface_container_highest</item>"));
            assertTrue(style.contains("<item name=\"colorOutlineVariant\">@color/app_outline_variant</item>"));
            assertTrue(style.contains("<item name=\"colorErrorContainer\">@color/app_error_container</item>"));
            assertTrue(style.contains("<item name=\"colorSurfaceInverse\">@color/app_inverse_surface</item>"));
            assertTrue(style.contains("<item name=\"colorPrimaryInverse\">@color/app_inverse_primary</item>"));
        }
    }

    @Test public void launcherKeepsGeometryButDropsOldBluePalette() throws Exception {
        String foreground = read(projectFile("src/main/res/drawable/ic_launcher_foreground.xml"));
        String legacy = read(projectFile("src/main/res/mipmap-anydpi/ic_launcher.xml"));
        String round = read(projectFile("src/main/res/mipmap-anydpi/ic_launcher_round.xml"));

        for (String icon : new String[] { foreground, legacy, round }) {
            assertTrue(icon.contains("#B64A4D"));
            assertTrue(icon.contains("#EF686B"));
            assertFalse(icon.contains("#0B4F9C"));
            assertFalse(icon.contains("#20D5E8"));
            assertFalse(icon.contains("#15A8E2"));
            assertTrue(icon.contains("M54,24 C48,34 36,48 36,63"));
            assertTrue(icon.contains("M57,47 C69,58 69,72 57,82"));
        }
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
