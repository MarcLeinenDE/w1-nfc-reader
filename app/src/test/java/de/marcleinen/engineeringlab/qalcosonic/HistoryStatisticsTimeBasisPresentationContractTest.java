package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level guard for the global LOCAL/METER presentation contract. */
public final class HistoryStatisticsTimeBasisPresentationContractTest {
    private static final String ACTIVITY =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryStatisticsActivity.java";

    @Test public void statisticsUsesSelectedTimeBasisExpectedBuckets() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("int expected = repository.expectedBuckets(window, effective);"));
        assertFalse(source.contains("int expected = window.customRange\n"
                + "                ? HistoryCustomRangeSemantics.expectedFullBuckets(window, effective)\n"
                + "                : window.expectedBuckets;"));
    }

    @Test public void returningFromSettingsRerendersWhenTimeBasisChanged() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("private AppTimeBasis renderedTimeBasis;"));
        assertTrue(source.contains("current != renderedTimeBasis"));
        assertTrue(source.contains("renderedTimeBasis = UiPreferences.getTimeBasis(this);"));
    }

    @Test public void localHistoryShowsRawMeterTimeAsSecondaryEvidence() throws Exception {
        String source = read(ACTIVITY);
        assertTrue(source.contains("UiPreferences.getTimeBasis(this) == AppTimeBasis.LOCAL"));
        assertTrue(source.contains("observation.meterTime"));
        assertTrue(source.contains("R.string.m3_meter_time"));
        assertTrue(source.contains("HistoryTimePresentation.formatArchivePeriod("));
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
