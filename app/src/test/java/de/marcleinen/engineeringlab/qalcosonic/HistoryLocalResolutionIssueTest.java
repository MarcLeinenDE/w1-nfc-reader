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

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLocalResolutionIssueTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void relevantArchiveWithoutMeterZoneReportsZoneMissing() {
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-09-09 10:00", 42_500L, 103.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    regularWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.ZONE_MISSING,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void missingZoneOnUnrelatedGranularityDoesNotCreateFalseWarning() {
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-09-09 10:00", 42_500L, 103.0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.DAY,
                    regularWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(0, result.meterStates.size());
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.NONE,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void unresolvedArchiveTimingEvidenceIsReportedSeparately() {
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-09-09 10:00", 42_500L, 103.0);
        installZoneOnly("Europe/Berlin");

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    regularWindow(),
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.ARCHIVE_TIME_UNRESOLVED,
                    repository.lastResolutionIssue());
        }
    }

    @Test public void ambiguousDstBoundaryIsReportedAsWindowIssue() {
        insertArchive(ArchiveFamilyPeriod.Family.HOUR, 1L,
                "2026-10-25 02:00", 42_500L, 103.0);
        installZoneOnly("Europe/Berlin");
        HistoryPeriodNavigator.Window ambiguous = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                9,
                25,
                "2026-10-25 02:30",
                "2026-10-25 03:30",
                1L,
                2L,
                0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    HistorySemanticTimeline.Granularity.HOUR,
                    ambiguous,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP);

            assertEquals(LocalTimeWindowResolver.Status.AMBIGUOUS_LOCAL_TIME,
                    result.meterStates.get(0).windowStatus);
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.WINDOW_UNRESOLVED,
                    repository.lastResolutionIssue());
        }
    }

    private HistoryPeriodNavigator.Window regularWindow() {
        return new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 10:00",
                "2026-09-09 11:00",
                1L,
                2L,
                0);
    }

    private void installZoneOnly(String zoneId) {
        long at = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli();
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    "M1",
                    zoneId,
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    at));
        }
    }

    private void insertArchive(
            ArchiveFamilyPeriod.Family family,
            long id,
            String timestamp,
            long onTimeSeconds,
            double totalVolume) {
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
            values.put("retrieved_at_utc", "2026-09-09T12:00:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-09-09T12:00:00Z");
            values.put("first_retrieved_at_ms", 1L);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "structure");
            values.put("content_fingerprint", "content-" + id);
            values.put("last_content_fingerprint", "content-" + id);
            values.put("last_structural_fingerprint", "structure");
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
