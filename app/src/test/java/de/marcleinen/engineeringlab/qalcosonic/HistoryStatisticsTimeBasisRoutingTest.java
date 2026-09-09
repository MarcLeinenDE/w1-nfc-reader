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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryStatisticsTimeBasisRoutingTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
        UiPreferences.setTimeBasis(context, AppTimeBasis.LOCAL);
    }

    @After public void tearDown() {
        UiPreferences.setTimeBasis(context, AppTimeBasis.LOCAL);
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void sameRepositorySwitchesBetweenResolvedLocalAndRawMeterTimeline() {
        insertArchive(1L, "2026-09-09 09:00", 38_900L, 102.0);
        insertArchive(2L, "2026-09-09 10:00", 42_500L, 103.0);
        installTimeModel();

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.YEAR);
        navigator.setAllPeriods(true);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context)) {
            List<HistoryStatisticsRepository.Observation> local = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.HOUR,
                    navigator.window(),
                    true);

            assertEquals(2, local.size());
            assertTrue(local.get(0).contextOnly);
            assertFalse(local.get(1).contextOnly);
            assertNotNull(HistoryResolvedTimeToken.parse(local.get(0).timestamp));
            assertNotNull(HistoryResolvedTimeToken.parse(local.get(1).timestamp));
            assertEquals("2026-09-09 10:00", local.get(1).meterTime);

            UiPreferences.setTimeBasis(context, AppTimeBasis.METER);
            List<HistoryStatisticsRepository.Observation> meter = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.HOUR,
                    navigator.window(),
                    true);

            assertEquals(2, meter.size());
            assertFalse(meter.get(0).contextOnly);
            assertFalse(meter.get(1).contextOnly);
            assertNull(HistoryResolvedTimeToken.parse(meter.get(0).timestamp));
            assertNull(HistoryResolvedTimeToken.parse(meter.get(1).timestamp));
            assertEquals("2026-09-09 09:00", meter.get(0).timestamp);
            assertEquals("2026-09-09 10:00", meter.get(1).timestamp);
        }
    }

    private void installTimeModel() {
        long center = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli();
        try (MeterTimeModelStore timeStore = new MeterTimeModelStore(context)) {
            assertTrue(timeStore.assignZoneIfMissing(
                    "M1",
                    "Europe/Berlin",
                    MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    center));
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            "M1",
                            center - 100L,
                            center + 100L,
                            "2026-09-09 14:00",
                            "04 6D 00 00",
                            false,
                            false,
                            50_000L),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        }
    }

    private void insertArchive(long id, String timestamp, long onTimeSeconds, double totalVolume) {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", ArchiveFamilyPeriod.Family.HOUR.name());
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
