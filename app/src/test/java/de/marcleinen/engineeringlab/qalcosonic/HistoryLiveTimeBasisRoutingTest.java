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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class HistoryLiveTimeBasisRoutingTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
        UiPreferences.setTimeBasis(context, AppTimeBasis.LOCAL);
    }

    @After public void tearDown() {
        UiPreferences.setTimeBasis(context, AppTimeBasis.LOCAL);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void meterModeSelectsLiveRowsAndPredecessorByRawMeterTime() {
        insertLive(1L, 1000L, "2026-09-09 08:00", 100.0);
        insertLive(2L, 2000L, "2026-09-09 09:00", 101.0);
        insertLive(3L, 3000L, "2026-09-09 10:00", 102.0);
        insertLive(4L, 2500L, null, 103.0);
        UiPreferences.setTimeBasis(context, AppTimeBasis.METER);

        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 09:00",
                "2026-09-09 11:00",
                0L,
                Long.MAX_VALUE,
                0);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.LIVE, window, true);

            assertEquals(3, rows.size());
            assertEquals("2026-09-09 08:00", rows.get(0).timestamp);
            assertTrue(rows.get(0).contextOnly);
            assertEquals("2026-09-09 09:00", rows.get(1).timestamp);
            assertFalse(rows.get(1).contextOnly);
            assertEquals("2026-09-09 10:00", rows.get(2).timestamp);
            assertFalse(rows.get(2).contextOnly);
            assertEquals(HistoryTimePresentation.floatingSortMs("2026-09-09 10:00"),
                    rows.get(2).sortMs);
        }
    }

    @Test public void localModeKeepsRealEpochAsCanonicalLiveSortKey() {
        long first = 1_799_482_400_000L;
        long second = first + 60_000L;
        insertLive(1L, first, "2026-09-09 08:00", 100.0);
        insertLive(2L, second, "2026-09-09 08:01", 101.0);

        HistoryPeriodNavigator.Window all = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                true,
                false,
                2026,
                8,
                9,
                null,
                null,
                0L,
                Long.MAX_VALUE,
                0);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.LIVE, all, false);

            assertEquals(2, rows.size());
            assertEquals(first, rows.get(0).sortMs);
            assertEquals(second, rows.get(1).sortMs);
            assertEquals(first, rows.get(0).deviceTimeMs);
            assertEquals("2026-09-09 08:00", rows.get(0).meterTime);
        }
    }

    private void insertLive(long id, long readAtMs, String meterTime, double totalM3) {
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("read_at_ms", readAtMs);
            if (meterTime == null) values.putNull("meter_time");
            else values.put("meter_time", meterTime);
            values.put("total_m3", totalM3);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    MeterHistoryStore.TABLE_READINGS, null, values);
            assertTrue(inserted > 0L);
        }
    }
}
