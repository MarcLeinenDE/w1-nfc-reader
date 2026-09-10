package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression matrix for the user-visible LOCAL resolution warning.
 *
 * <p>The warning is reserved for a real inability to interpret a relevant archive period/window.
 * Empty families, retention edges outside the requested range and normal data gaps must not create
 * a red time-resolution warning. Conversely, a relevant unresolved archive edge, missing zone or
 * ambiguous LOCAL boundary must continue to fail closed.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLocalResolutionWarningMatrixTest {
    private static final Instant ANCHOR_INSTANT = Instant.parse("2026-09-10T12:00:00Z");
    private static final long ANCHOR_ON_TIME_SECONDS = 100_000_000L;

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        reset();
    }

    @After public void tearDown() {
        reset();
    }

    @Test public void emptyArchiveFamilyDoesNotWarn() {
        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    februaryWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertTrue(result.rows.isEmpty());
            assertTrue(result.meterStates.isEmpty());
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.NONE,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void naturalOpenPrefixFarOutsideWindowDoesNotWarnForHourDayOrMonth() {
        installTimeModel();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 101.0);
        insertArchive(ArchiveFamilyPeriod.Family.DAY, 2L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 102.0);
        insertArchive(ArchiveFamilyPeriod.Family.MONTH, 3L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 103.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            for (HistorySemanticTimeline.Granularity granularity : new HistorySemanticTimeline.Granularity[]{
                    HistorySemanticTimeline.Granularity.HOUR,
                    HistorySemanticTimeline.Granularity.DAY,
                    HistorySemanticTimeline.Granularity.MONTH}) {
                HistoryLocalQueryRepository.Result result = repository.queryArchive(
                        granularity,
                        februaryWindow(),
                        ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
                assertTrue(result.rows.isEmpty());
                assertEquals("unexpected warning for " + granularity,
                        HistoryLocalQueryRepository.ResolutionIssue.NONE,
                        repository.lastResolutionIssue());
            }
        }
    }

    @Test public void naturalOpenPrefixStillWarnsWhenRequestedWindowCanTouchIt() {
        installTimeModel();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 101.0);
        insertArchive(ArchiveFamilyPeriod.Family.DAY, 2L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 102.0);
        insertArchive(ArchiveFamilyPeriod.Family.MONTH, 3L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 103.0);

        HistoryPeriodNavigator.Window nearEdge = customWindow(
                "2026-08-10 13:30", "2026-08-10 14:30");
        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            for (HistorySemanticTimeline.Granularity granularity : new HistorySemanticTimeline.Granularity[]{
                    HistorySemanticTimeline.Granularity.HOUR,
                    HistorySemanticTimeline.Granularity.DAY,
                    HistorySemanticTimeline.Granularity.MONTH}) {
                repository.queryArchive(
                        granularity,
                        nearEdge,
                        ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);
                assertEquals("missing fail-closed warning for " + granularity,
                        HistoryLocalQueryRepository.ResolutionIssue.ARCHIVE_TIME_UNRESOLVED,
                        repository.lastResolutionIssue());
            }
        }
    }

    @Test public void allViewIsNotPoisonedByLaterEmptyHourCoverage() {
        installTimeModel();

        // Hour evidence starts months after the requested February range. Its natural oldest
        // unresolved start must not contaminate otherwise resolvable Day data in the ALL view.
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 200.0);

        insertArchive(ArchiveFamilyPeriod.Family.DAY, 10L,
                "2026-02-11 00:00", "2026-02-10T23:00:00Z", 100.0);
        insertArchive(ArchiveFamilyPeriod.Family.DAY, 11L,
                "2026-02-12 00:00", "2026-02-11T23:00:00Z", 101.0);
        insertArchive(ArchiveFamilyPeriod.Family.DAY, 12L,
                "2026-02-13 00:00", "2026-02-12T23:00:00Z", 102.0);
        insertArchive(ArchiveFamilyPeriod.Family.DAY, 13L,
                "2026-02-14 00:00", "2026-02-13T23:00:00Z", 103.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            List<HistoryStatisticsRepository.Observation> observations =
                    repository.queryHistory(null, februaryWindow());

            assertFalse(observations.isEmpty());
            assertTrue(observations.stream().anyMatch(
                    observation -> observation.granularity == HistorySemanticTimeline.Granularity.DAY));
            assertFalse(observations.stream().anyMatch(
                    observation -> observation.granularity == HistorySemanticTimeline.Granularity.HOUR));
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.NONE,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void statisticsBeforeFamilyCoverageIsEmptyWithoutResolutionWarning() {
        installTimeModel();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-08-10 14:00", "2026-08-10T12:00:00Z", 101.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            List<HistoryStatisticsRepository.Observation> observations = repository.queryStatistics(
                    februaryWindow(), HistorySemanticTimeline.Granularity.HOUR);

            assertTrue(observations.isEmpty());
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.NONE,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void relevantMissingZoneStillWarns() {
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-02-12 12:00", "2026-02-12T11:00:00Z", 101.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    februaryWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.ZONE_MISSING,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void relevantArchiveWithoutUsableAnchorStillWarns() {
        installZoneOnly();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-02-12 12:00", "2026-02-12T11:00:00Z", 101.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    februaryWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.ARCHIVE_TIME_UNRESOLVED,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void ambiguousFallBackWindowStillWarnsFailClosed() {
        installTimeModel();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-10-25 02:00", "2026-09-01T12:00:00Z", 101.0);
        HistoryPeriodNavigator.Window ambiguous = customWindow(
                "2026-10-25 02:30", "2026-10-25 03:30");

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    ambiguous,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.WINDOW_UNRESOLVED,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void nonexistentSpringForwardWindowStillWarnsFailClosed() {
        installTimeModel();
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-03-29 03:00", "2026-03-20T12:00:00Z", 101.0);
        HistoryPeriodNavigator.Window nonexistent = customWindow(
                "2026-03-29 02:30", "2026-03-29 04:00");

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    nonexistent,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.WINDOW_UNRESOLVED,
                    repository.lastResolutionIssue());
        }
    }

    private void reset() {
        if (context == null) return;
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    private HistoryPeriodNavigator.Window februaryWindow() {
        return customWindow("2026-02-11 11:00", "2026-02-13 15:00");
    }

    private HistoryPeriodNavigator.Window customWindow(String start, String end) {
        return new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                1,
                11,
                start,
                end,
                1L,
                2L,
                0);
    }

    private void installTimeModel() {
        installZoneOnly();
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            "M1",
                            ANCHOR_INSTANT.toEpochMilli() - 100L,
                            ANCHOR_INSTANT.toEpochMilli() + 100L,
                            "2026-09-10 14:00",
                            "04 6D 00 00",
                            false,
                            false,
                            ANCHOR_ON_TIME_SECONDS),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        }
    }

    private void installZoneOnly() {
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    "M1",
                    "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    ANCHOR_INSTANT.toEpochMilli()));
        }
    }

    private long onTimeFor(String targetUtc) {
        long elapsed = Duration.between(Instant.parse(targetUtc), ANCHOR_INSTANT).getSeconds();
        return ANCHOR_ON_TIME_SECONDS - elapsed;
    }

    private void insertArchive(
            ArchiveFamilyPeriod.Family family,
            long id,
            String timestamp,
            String targetUtc,
            double totalVolume) {
        long onTimeSeconds = onTimeFor(targetUtc);
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", family.name());
            values.put("logger_timestamp", timestamp);
            values.put("occurrence_key", "OT:" + onTimeSeconds);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTimeSeconds);
            values.put("raw_type_f_hex", "04 6D 00 00");
            values.put("type_f_iv", 0);
            values.put("type_f_su", 1);
            values.put("retrieved_at_utc", "2026-09-10T12:00:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-09-10T12:00:00Z");
            values.put("first_retrieved_at_ms", 1L);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "warning-matrix-structure");
            values.put("content_fingerprint", "warning-matrix-content-" + id);
            values.put("last_content_fingerprint", "warning-matrix-content-" + id);
            values.put("last_structural_fingerprint", "warning-matrix-structure");
            values.put("last_source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("last_validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("total_volume", totalVolume);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    ArchiveFamilyStore.TABLE_PERIODS,
                    null,
                    values);
            assertTrue(inserted > 0L);
        }
    }
}
