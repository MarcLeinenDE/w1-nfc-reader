package de.marcleinen.engineeringlab.qalcosonic;

import android.content.ContentValues;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class LiveHistoryReferenceRepositoryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void liveOnlyHistoryDoesNotLoadArchiveRowsAsDeltaContext() {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            insertArchive(store, ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 18:00", "205.000");
            insertArchive(store, ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", "204.000");
            insertArchive(store, ArchiveFamilyPeriod.Family.MONTH, "2026-09-01 00:00", "196.000");
        }
        insertLive("TEST-METER", 1_788_722_180_000L, 205.126);

        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setAllPeriods(true);

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.LIVE, navigator.window(), true);

            assertEquals(1, rows.size());
            assertFalse(rows.get(0).contextOnly);
            assertEquals(HistorySemanticTimeline.Granularity.LIVE, rows.get(0).granularity);
        }
    }

    private void insertLive(String meterId, long readAtMs, double totalM3) {
        try (MeterHistoryStore store = new MeterHistoryStore(context)) {
            ContentValues values = new ContentValues();
            values.put("meter_id", meterId);
            values.put("read_at_ms", readAtMs);
            values.put("total_m3", totalM3);
            values.put("alarm_codes", "");
            store.getWritableDatabase().insertOrThrow(MeterHistoryStore.TABLE_READINGS, null, values);
        }
    }

    private static void insertArchive(ArchiveFamilyStore store, ArchiveFamilyPeriod.Family family,
                                      String timestamp, String total) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder().totalVolume(total);
        values.batteryPercent = "88 %";
        values.errorFlags = "0x00000000";
        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                family, timestamp, "2026-09-07T03:00:00Z",
                "fp-live-ref-" + family + "-" + timestamp, "TEST", "TEST", values.build());
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("TEST-METER", period));
    }
}
