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
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** End-to-end LOCAL query/analytics regression for Hour, Day and Month families. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLocalQueryFamilyRegressionTest {
    private static final String METER = "TEST-METER";
    private static final long ANCHOR_MS = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();
    private static final long ANCHOR_ON_TIME = 80_000_000L;

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
        installTimeModel();
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void repeatedAutumnHourKeepsBothOccurrencesDistinctAndChronological() {
        insertArchive(101L, ArchiveFamilyPeriod.Family.HOUR,
                "2026-10-25 01:00", "2026-10-24T23:00:00Z", 100.0, 1);
        insertArchive(102L, ArchiveFamilyPeriod.Family.HOUR,
                "2026-10-25 02:00", "2026-10-25T00:00:00Z", 101.0, 1);
        insertArchive(103L, ArchiveFamilyPeriod.Family.HOUR,
                "2026-10-25 02:00", "2026-10-25T01:00:00Z", 103.0, 0);
        insertArchive(104L, ArchiveFamilyPeriod.Family.HOUR,
                "2026-10-25 03:00", "2026-10-25T02:00:00Z", 106.0, 0);
        insertArchive(105L, ArchiveFamilyPeriod.Family.HOUR,
                "2026-10-25 04:00", "2026-10-25T03:00:00Z", 110.0, 0);

        HistoryStatisticsAnalytics.ConsumptionSummary summary = statistics(
                HistorySemanticTimeline.Granularity.HOUR,
                HistoryPeriodNavigator.Scale.DAY,
                "2026-10-25 01:00",
                "2026-10-25 04:00");

        assertEquals(4, summary.availableBuckets);
        assertEquals(10.0, summary.total, 0.000001);
        assertEquals(4, summary.points.size());
        assertTrue(summary.points.get(0).timestamp.startsWith("@W1RT|I|"));
        assertTrue(summary.points.get(0).timestamp.contains("Europe/Berlin"));
        for (int i = 1; i < summary.points.size(); i++) {
            assertTrue(summary.points.get(i - 1).timestamp,
                    HistoryTimePresentation.floatingSortMs(summary.points.get(i - 1).timestamp)
                            < HistoryTimePresentation.floatingSortMs(summary.points.get(i).timestamp));
        }

        String summerFold = HistoryTimePresentation.formatAxis(
                Locale.US,
                HistorySemanticTimeline.Granularity.HOUR,
                summary.points.get(1).timestamp);
        String winterFold = HistoryTimePresentation.formatAxis(
                Locale.US,
                HistorySemanticTimeline.Granularity.HOUR,
                summary.points.get(2).timestamp);
        assertNotEquals(summerFold, winterFold);
        assertTrue(summerFold.contains("+02:00"));
        assertTrue(winterFold.contains("+01:00"));
    }

    @Test public void springDstDayBucketsRemainTwoPhysicalDaysWithoutFalseCivilRelabeling() {
        insertArchive(201L, ArchiveFamilyPeriod.Family.DAY,
                "2026-03-28 23:59", "2026-03-28T22:59:00Z", 200.0, 0);
        insertArchive(202L, ArchiveFamilyPeriod.Family.DAY,
                "2026-03-29 23:59", "2026-03-29T22:59:00Z", 202.0, 1);
        insertArchive(203L, ArchiveFamilyPeriod.Family.DAY,
                "2026-03-30 23:59", "2026-03-30T22:59:00Z", 205.0, 1);

        HistoryStatisticsAnalytics.ConsumptionSummary summary = statistics(
                HistorySemanticTimeline.Granularity.DAY,
                HistoryPeriodNavigator.Scale.MONTH,
                "2026-03-28 23:00",
                "2026-03-31 01:00");

        assertEquals(2, summary.availableBuckets);
        assertEquals(5.0, summary.total, 0.000001);
        assertEquals(2, summary.points.size());
        assertResolvedDuration(summary.points.get(0).timestamp, 24L * 60L * 60L * 1000L);
        assertResolvedDuration(summary.points.get(1).timestamp, 24L * 60L * 60L * 1000L);

        String firstAxis = HistoryTimePresentation.formatAxis(
                Locale.US, HistorySemanticTimeline.Granularity.DAY,
                summary.points.get(0).timestamp);
        String secondAxis = HistoryTimePresentation.formatAxis(
                Locale.US, HistorySemanticTimeline.Granularity.DAY,
                summary.points.get(1).timestamp);
        assertTrue(firstAxis, firstAxis.contains("28") && firstAxis.contains("30"));
        assertTrue(secondAxis, secondAxis.contains("30") && secondAxis.contains("31"));
    }

    @Test public void monthBucketsAcrossDstKeepValuesOnTheirOwnResolvedIntervals() {
        insertArchive(301L, ArchiveFamilyPeriod.Family.MONTH,
                "2026-02-28 23:59", "2026-02-28T22:59:00Z", 300.0, 0);
        insertArchive(302L, ArchiveFamilyPeriod.Family.MONTH,
                "2026-03-31 23:59", "2026-03-31T22:59:00Z", 307.0, 1);
        insertArchive(303L, ArchiveFamilyPeriod.Family.MONTH,
                "2026-04-30 23:59", "2026-04-30T22:59:00Z", 311.0, 1);

        HistoryStatisticsAnalytics.ConsumptionSummary summary = statistics(
                HistorySemanticTimeline.Granularity.MONTH,
                HistoryPeriodNavigator.Scale.YEAR,
                "2026-02-28 23:00",
                "2026-05-01 01:00");

        assertEquals(2, summary.availableBuckets);
        assertEquals(11.0, summary.total, 0.000001);
        assertEquals(7.0, summary.points.get(0).value, 0.000001);
        assertEquals(4.0, summary.points.get(1).value, 0.000001);
        assertResolvedDuration(summary.points.get(0).timestamp, 31L * 24L * 60L * 60L * 1000L);
        assertResolvedDuration(summary.points.get(1).timestamp, 30L * 24L * 60L * 60L * 1000L);

        String firstAxis = HistoryTimePresentation.formatAxis(
                Locale.US, HistorySemanticTimeline.Granularity.MONTH,
                summary.points.get(0).timestamp).toLowerCase(Locale.ROOT);
        String secondAxis = HistoryTimePresentation.formatAxis(
                Locale.US, HistorySemanticTimeline.Granularity.MONTH,
                summary.points.get(1).timestamp).toLowerCase(Locale.ROOT);
        assertTrue(firstAxis, firstAxis.contains("feb") && firstAxis.contains("apr"));
        assertTrue(secondAxis, secondAxis.contains("apr") && secondAxis.contains("may"));
        assertNotEquals(summary.points.get(0).timestamp, summary.points.get(1).timestamp);
    }

    private HistoryStatisticsAnalytics.ConsumptionSummary statistics(
            HistorySemanticTimeline.Granularity granularity,
            HistoryPeriodNavigator.Scale scale,
            String start,
            String end) {
        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                scale,
                false,
                true,
                2026,
                0,
                1,
                start,
                end,
                1L,
                2L,
                0);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            HistoryLocalQueryRepository.Result result = repository.queryArchive(
                    granularity,
                    window,
                    ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED);
            assertEquals(HistoryLocalQueryRepository.ResolutionIssue.NONE,
                    repository.lastResolutionIssue());
            List<HistoryStatisticsRepository.Observation> observations = result.observations();
            assertTrue(observations.size() >= result.rows.size());
            for (HistoryLocalQueryRepository.Row row : result.rows) {
                assertNotNull(row.observation);
                assertNotNull(row.time);
                assertTrue(row.time.fullyContained);
                assertEquals(row.time.endUtcMs, row.observation.sortMs);
                HistoryResolvedTimeToken.Parsed parsed =
                        HistoryResolvedTimeToken.parse(row.observation.timestamp);
                assertNotNull(parsed);
                assertEquals(row.time.startUtcMs, parsed.startUtcMs);
                assertEquals(row.time.endUtcMs, parsed.endUtcMs);
            }
            return HistoryStatisticsAnalytics.consumption(observations);
        }
    }

    private static void assertResolvedDuration(String token, long expectedMs) {
        HistoryResolvedTimeToken.Parsed parsed = HistoryResolvedTimeToken.parse(token);
        assertNotNull(parsed);
        assertEquals(expectedMs, parsed.endUtcMs - parsed.startUtcMs);
    }

    private void installTimeModel() {
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    METER,
                    "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    ANCHOR_MS));
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            METER,
                            ANCHOR_MS - 100L,
                            ANCHOR_MS + 100L,
                            "2026-11-01 13:00",
                            "04 6D 00 00",
                            false,
                            false,
                            ANCHOR_ON_TIME),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        }
    }

    private void insertArchive(
            long id,
            ArchiveFamilyPeriod.Family family,
            String rawLoggerTimestamp,
            String canonicalBoundaryUtc,
            double totalVolume,
            int summerFlag) {
        long boundaryMs = Instant.parse(canonicalBoundaryUtc).toEpochMilli();
        long elapsedSeconds = (ANCHOR_MS - boundaryMs) / 1000L;
        long onTimeSeconds = ANCHOR_ON_TIME - elapsedSeconds;
        assertTrue(onTimeSeconds >= 0L);

        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", METER);
            values.put("archive_family", family.name());
            values.put("logger_timestamp", rawLoggerTimestamp);
            values.put("occurrence_key", "OT:" + onTimeSeconds);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTimeSeconds);
            values.put("raw_type_f_hex", "04 6D 00 00");
            values.put("type_f_iv", 0);
            values.put("type_f_su", summerFlag);
            values.put("retrieved_at_utc", "2026-11-01T12:00:00Z");
            values.put("retrieved_at_ms", ANCHOR_MS);
            values.put("first_retrieved_at_utc", "2026-11-01T12:00:00Z");
            values.put("first_retrieved_at_ms", ANCHOR_MS);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "structure-" + family + "-" + id);
            values.put("content_fingerprint", "content-" + family + "-" + id);
            values.put("last_content_fingerprint", "content-" + family + "-" + id);
            values.put("last_structural_fingerprint", "structure-" + family + "-" + id);
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
