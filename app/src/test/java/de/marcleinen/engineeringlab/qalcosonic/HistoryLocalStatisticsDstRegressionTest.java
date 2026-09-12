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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** End-to-end LOCAL query/analytics regression using synthetic W1-like timing evidence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLocalStatisticsDstRegressionTest {
    private static final String METER = "SYNTHETIC-METER";
    private static final long BASE_ON_TIME = 10_000_000L;
    private static final LocalDateTime BASE_RAW = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final DateTimeFormatter RAW = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

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

    @Test public void monthlyStatisticsDoNotLoseTheSpringDstArchiveInterval() {
        String[] boundaries = {
                "2026-01-01 00:00",
                "2026-02-01 00:00",
                "2026-03-01 00:00",
                "2026-04-01 00:00",
                "2026-05-01 00:00",
                "2026-06-01 00:00",
                "2026-07-01 00:00",
                "2026-08-01 00:00",
                "2026-09-01 00:00"
        };
        for (int i = 0; i < boundaries.length; i++) {
            insertArchive(100L + i, ArchiveFamilyPeriod.Family.MONTH,
                    boundaries[i], 100.0 + i);
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.YEAR);
        navigator.setDate(2026, 0, 1);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            List<HistoryStatisticsRepository.Observation> selected = repository.queryStatistics(
                    navigator.window(), HistorySemanticTimeline.Granularity.MONTH);
            assertEquals(8, visibleCount(selected));

            HistoryStatisticsRepository.Observation spring = findByRawMeterTime(
                    selected, "2026-04-01 00:00");
            assertNotNull(spring);
            HistoryResolvedTimeToken.Parsed resolved = HistoryResolvedTimeToken.parse(spring.timestamp);
            assertNotNull(resolved);
            assertTrue(resolved.interval);
            assertEquals(0, resolved.startLocal().getHour());
            assertEquals(1, resolved.endLocal().getHour());
            assertTrue(resolved.startLocal().getOffset().getTotalSeconds()
                    != resolved.endLocal().getOffset().getTotalSeconds());

            HistoryStatisticsAnalytics.ConsumptionSummary summary =
                    HistoryStatisticsAnalytics.consumption(selected);
            assertEquals(8, summary.availableBuckets);
            assertTrue(summary.points.stream().anyMatch(point -> point.timestamp.equals(spring.timestamp)));
        }
    }

    @Test public void dailyStatisticsKeepPhysicalDayAcrossBerlinDstChange() {
        String[] boundaries = {
                "2026-03-27 00:00",
                "2026-03-28 00:00",
                "2026-03-29 00:00",
                "2026-03-30 00:00",
                "2026-03-31 00:00",
                "2026-04-01 00:00"
        };
        for (int i = 0; i < boundaries.length; i++) {
            insertArchive(200L + i, ArchiveFamilyPeriod.Family.DAY,
                    boundaries[i], 200.0 + i);
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);
        navigator.setDate(2026, 2, 1);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            List<HistoryStatisticsRepository.Observation> selected = repository.queryStatistics(
                    navigator.window(), HistorySemanticTimeline.Granularity.DAY);
            HistoryStatisticsRepository.Observation spring = findByRawMeterTime(
                    selected, "2026-03-30 00:00");
            assertNotNull(spring);

            HistoryResolvedTimeToken.Parsed resolved = HistoryResolvedTimeToken.parse(spring.timestamp);
            assertNotNull(resolved);
            assertEquals(Duration.ofHours(24).toMillis(), resolved.endUtcMs - resolved.startUtcMs);
            assertTrue(resolved.startLocal().getOffset().getTotalSeconds()
                    != resolved.endLocal().getOffset().getTotalSeconds());

            HistoryStatisticsAnalytics.ConsumptionSummary summary =
                    HistoryStatisticsAnalytics.consumption(selected);
            assertEquals(visibleCount(selected), summary.availableBuckets);
        }
    }

    @Test public void hourlyStatisticsRemainOnePhysicalHourOnResolvedTimeline() {
        String[] boundaries = {
                "2026-03-29 00:00",
                "2026-03-29 01:00",
                "2026-03-29 02:00",
                "2026-03-29 03:00",
                "2026-03-29 04:00",
                "2026-03-29 05:00"
        };
        for (int i = 0; i < boundaries.length; i++) {
            insertArchive(300L + i, ArchiveFamilyPeriod.Family.HOUR,
                    boundaries[i], 300.0 + i);
        }

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setDate(2026, 2, 29);

        try (HistoryLocalQueryRepository repository = new HistoryLocalQueryRepository(context)) {
            List<HistoryStatisticsRepository.Observation> selected = repository.queryStatistics(
                    navigator.window(), HistorySemanticTimeline.Granularity.HOUR);
            assertTrue(visibleCount(selected) > 0);
            for (HistoryStatisticsRepository.Observation observation : selected) {
                if (observation.contextOnly) continue;
                HistoryResolvedTimeToken.Parsed resolved = HistoryResolvedTimeToken.parse(observation.timestamp);
                assertNotNull(resolved);
                assertEquals(Duration.ofHours(1).toMillis(), resolved.endUtcMs - resolved.startUtcMs);
            }
            assertEquals(visibleCount(selected),
                    HistoryStatisticsAnalytics.consumption(selected).availableBuckets);
        }
    }

    private void installTimeModel() {
        long anchorEpoch = Instant.parse("2026-09-10T11:00:00Z").toEpochMilli();
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    METER,
                    "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    anchorEpoch));
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            METER,
                            anchorEpoch - 100L,
                            anchorEpoch + 100L,
                            "2026-09-10 12:00",
                            "04 6D 00 00",
                            false,
                            false,
                            onTime("2026-09-10 12:00")),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        }
    }

    private void insertArchive(
            long id,
            ArchiveFamilyPeriod.Family family,
            String timestamp,
            double totalVolume) {
        long onTime = onTime(timestamp);
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", METER);
            values.put("archive_family", family.name());
            values.put("logger_timestamp", timestamp);
            values.put("occurrence_key", "OT:" + onTime);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTime);
            values.put("raw_type_f_hex", "04 6D 00 00");
            values.put("type_f_iv", 0);
            values.put("type_f_su", 0);
            values.put("retrieved_at_utc", "2026-09-10T11:00:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-09-10T11:00:00Z");
            values.put("first_retrieved_at_ms", 1L);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "synthetic-structure");
            values.put("content_fingerprint", "synthetic-content-" + id);
            values.put("last_content_fingerprint", "synthetic-content-" + id);
            values.put("last_structural_fingerprint", "synthetic-structure");
            values.put("last_source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("last_validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("total_volume", totalVolume);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    ArchiveFamilyStore.TABLE_PERIODS, null, values);
            assertTrue(inserted > 0L);
        }
    }

    private static long onTime(String timestamp) {
        LocalDateTime raw = LocalDateTime.parse(timestamp, RAW);
        return BASE_ON_TIME + Duration.between(BASE_RAW, raw).getSeconds();
    }

    private static int visibleCount(List<HistoryStatisticsRepository.Observation> observations) {
        int count = 0;
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation != null && !observation.contextOnly) count++;
        }
        return count;
    }

    private static HistoryStatisticsRepository.Observation findByRawMeterTime(
            List<HistoryStatisticsRepository.Observation> observations,
            String rawMeterTime) {
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation != null && rawMeterTime.equals(observation.meterTime)) return observation;
        }
        return null;
    }
}
