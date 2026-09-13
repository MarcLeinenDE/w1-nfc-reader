package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level product guard for explicit per-meter timezone management. */
public final class MeterDetailsTimeZoneUiTest {
    private static final String ACTIVITY =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/MeterDetailsActivity.java";

    @Test public void meterDetailsShowsPersistedZoneAndProvenance() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("timeModel.getProfile(meterId)"));
        assertTrue(source.contains("R.string.v21_meter_timezone"));
        assertTrue(source.contains("profile.zoneId"));
        assertTrue(source.contains("ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE"));
        assertTrue(source.contains("ZONE_SOURCE_USER_SELECTED"));
        assertTrue(source.contains("R.string.v21_meter_timezone_note"));
    }

    @Test public void manualChangeUsesSearchableSupportedZonePickerOnly() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("MaterialAutoCompleteTextView"));
        assertTrue(source.contains("ZoneId.getAvailableZoneIds()"));
        assertTrue(source.contains("TextInputLayout.END_ICON_DROPDOWN_MENU"));
        assertTrue(source.contains("input.setThreshold(0)"));
        assertTrue(source.contains("input.showDropDown()"));
        assertTrue(source.contains("if (!zones.contains(candidate))"));
        assertTrue(source.contains("MeterTimeModelStore.normalizeZoneId(candidate)"));
        assertTrue(source.contains("store.setZone(meterId, normalized,"));
        assertTrue(source.contains("MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED"));
        assertTrue(source.contains("field.setError(getString(R.string.v21_meter_timezone_invalid))"));
        assertTrue(source.contains("dialog.dismiss();"));
        assertTrue(source.contains("recreate();"));
        assertFalse(source.contains("new EditText(this)"));
        assertFalse(source.contains("archive.upsert("));
        assertFalse(source.contains("history.insert"));
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
