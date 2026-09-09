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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveOccurrenceIdentityIntegrationTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterHistoryStore.DB_NAME);
        new MeterLifecycleStore(context).clear();
    }

    @Test public void repeatedRawDstHourRemainsTwoHistoryObservations() {
        insertArchive(1L, "2026-10-25 02:00", "OT:22800", 22_800L, "10.000");
        insertArchive(2L, "2026-10-25 02:00", "OT:26400", 26_400L, "10.100");

        try (HistoryStatisticsRepository repository = new HistoryStatisticsRepository(context)) {
            List<HistoryStatisticsRepository.Observation> rows = repository.queryHistory(
                    HistorySemanticTimeline.Granularity.HOUR, null, false);

            assertEquals(2, rows.size());
            assertEquals("2026-10-25 02:00", rows.get(0).timestamp);
            assertEquals("2026-10-25 02:00", rows.get(1).timestamp);
            assertNotEquals(rows.get(0).identity, rows.get(1).identity);
            assertTrue(rows.get(0).identity.contains("OT:22800"));
            assertTrue(rows.get(1).identity.contains("OT:26400"));
        }
    }

    @Test public void identityFallsBackToLegacyOccurrenceWithoutGuessing() {
        assertEquals(
                "M1|HOUR|2026-10-25 02:00|LEGACY",
                ArchiveRecordIdentity.of(
                        ArchiveFamilyPeriod.Family.HOUR,
                        "M1",
                        "2026-10-25 02:00",
                        null));
    }

    private void insertArchive(
            long id,
            String timestamp,
            String occurrenceKey,
            long onTimeSeconds,
            String totalVolume) {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", ArchiveFamilyPeriod.Family.HOUR.name());
            values.put("logger_timestamp", timestamp);
            values.put("occurrence_key", occurrenceKey);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTimeSeconds);
            values.put("retrieved_at_utc", "2026-10-25T03:30:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-10-25T03:30:00Z");
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
                    ArchiveFamilyStore.TABLE_PERIODS, null, values);
            assertTrue(inserted > 0L);
        }
    }
}
