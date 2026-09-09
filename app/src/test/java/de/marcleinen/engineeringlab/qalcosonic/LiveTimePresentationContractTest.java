package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the global Live LOCAL/METER presentation contract and localized raw meter time. */
public final class LiveTimePresentationContractTest {
    private static final String DASHBOARD =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/ProductDashboardActivity.java";
    private static final String HISTORY =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryStatisticsActivity.java";
    private static final String DETAILS =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/MeterDetailsActivity.java";
    private static final String REPOSITORY =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryStatisticsRepository.java";

    @Test public void germanRawMeterTimeIsLocalizedInsteadOfRenderedAsStorageFormat() {
        String formatted = HistoryTimePresentation.formatExactFloatingDateTime(
                Locale.GERMANY, "2026-09-09 14:05");
        assertFalse(formatted.equals("2026-09-09 14:05"));
        assertTrue(formatted.contains("09.09.2026"));
        assertTrue(formatted.contains("14:05"));
    }

    @Test public void meterDetailsRoutesMeterTimeThroughLocaleAwarePresentation() throws Exception {
        String source = read(DETAILS);
        assertTrue(source.contains("HistoryTimePresentation.formatExactFloatingDateTime("));
        assertTrue(source.contains("rawMeterTime"));
        assertFalse(source.contains("return value(value);"));
    }

    @Test public void dashboardShowsPrimaryAndSecondaryLiveTimeAccordingToGlobalBasis() throws Exception {
        String source = read(DASHBOARD);
        assertTrue(source.contains("overviewSecondaryLiveTime"));
        assertTrue(source.contains("UiPreferences.getTimeBasis(this) == AppTimeBasis.METER"));
        assertTrue(source.contains("HistoryTimePresentation.formatExactFloatingDateTime("));
        assertTrue(source.contains("setOverviewLiveTimes"));
        assertTrue(source.contains("formatLivePrimaryTime"));
    }

    @Test public void liveHistoryUsesMeterTimeForMeterModeQueryAndPresentation() throws Exception {
        String repository = read(REPOSITORY);
        String activity = read(HISTORY);
        assertTrue(repository.contains("meter_time >= ? AND meter_time < ?"));
        assertTrue(repository.contains("meter_time < ?"));
        assertTrue(repository.contains("String orderBy = meterBasis ? \"meter_time ASC, read_at_ms ASC\""));
        assertTrue(activity.contains("historyPrimaryTime(observation)"));
        assertTrue(activity.contains("historyPredecessorTime(observation, row.delta.previous)"));
        assertTrue(activity.contains("repository.resolvedLocalArchiveTimestamp(observation)"));
    }

    @Test public void localLiveCanonicalOrderingUsesRealAcquisitionEpoch() throws Exception {
        String repository = read(REPOSITORY);
        assertTrue(repository.contains("long sortMs = usableMeterTime\n"
                + "                ? HistoryTimePresentation.floatingSortMs(timestamp)\n"
                + "                : readAt;"));
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
