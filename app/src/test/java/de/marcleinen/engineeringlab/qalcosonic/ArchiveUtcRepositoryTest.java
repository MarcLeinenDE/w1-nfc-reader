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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveUtcRepositoryTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @After public void tearDown() {
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @Test public void realStoresProjectRepeatedRawHourOccurrencesToDistinctUtc() {
        insertArchive(1L, "2026-10-25 02:00", 22_800L, "OT:22800", 1);
        insertArchive(2L, "2026-10-25 02:00", 26_400L, "OT:26400", 0);

        long center = Instant.parse("2026-10-25T02:30:00Z").toEpochMilli();
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            VerifiedLiveTimeAnchor anchor = new VerifiedLiveTimeAnchor(
                    "M1", center - 100L, center + 100L,
                    "2026-10-25 03:30", "04 6D 1E 03",
                    false, false, 30_000L);
            timeStore.recordAnchor(
                    anchor,
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        } finally {
            timeStore.close();
        }

        try (ArchiveUtcRepository repository = new ArchiveUtcRepository(context)) {
            List<ArchiveUtcProjection.Period> projected = repository.project(
                    "M1", ArchiveFamilyPeriod.Family.HOUR);

            assertEquals(2, projected.size());
            assertNotEquals(projected.get(0).identity, projected.get(1).identity);
            assertEquals(ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                    projected.get(0).status);
            assertEquals(ArchiveUtcProjection.PeriodStatus.RESOLVED,
                    projected.get(1).status);
            assertTrue(projected.get(1).resolvedInterval());
            assertEquals(3_600_000L,
                    projected.get(1).endUtcMs.longValue() - projected.get(1).startUtcMs.longValue());
        }
    }

    @Test public void archiveWithoutMatchingMeterAnchorStaysUnresolved() {
        insertArchive(3L, "2026-09-06 12:00", 20_000L, "OT:20000", 0);

        long center = Instant.parse("2026-09-06T13:00:00Z").toEpochMilli();
        MeterTimeModelStore timeStore = new MeterTimeModelStore(context);
        try {
            timeStore.recordAnchor(
                    new VerifiedLiveTimeAnchor(
                            "M2", center - 100L, center + 100L,
                            "2026-09-06 14:00", "04 6D 00 00",
                            false, false, 21_000L),
                    MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                    MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
        } finally {
            timeStore.close();
        }

        try (ArchiveUtcRepository repository = new ArchiveUtcRepository(context)) {
            List<ArchiveUtcProjection.Period> projected = repository.project(
                    "M1", ArchiveFamilyPeriod.Family.HOUR);
            assertEquals(1, projected.size());
            assertEquals(ArchiveUtcProjection.BoundaryStatus.NO_SUITABLE_ANCHOR,
                    projected.get(0).endBoundary.status);
            assertEquals(ArchiveUtcProjection.PeriodStatus.END_BOUNDARY_UNRESOLVED,
                    projected.get(0).status);
        }
    }

    private void insertArchive(
            long id, String timestamp, long onTime, String occurrenceKey, int summerTime) {
        ArchiveFamilyStore store = new ArchiveFamilyStore(context);
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", ArchiveFamilyPeriod.Family.HOUR.name());
            values.put("logger_timestamp", timestamp);
            values.put("occurrence_key", occurrenceKey);
            values.put("logger_time_basis", ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL);
            values.put("on_time_seconds", onTime);
            values.put("raw_type_f_hex", "04 6D 00 00");
            values.put("type_f_iv", 0);
            values.put("type_f_su", summerTime);
            values.put("retrieved_at_utc", "2026-09-09T00:00:00Z");
            values.put("retrieved_at_ms", 1L);
            values.put("first_retrieved_at_utc", "2026-09-09T00:00:00Z");
            values.put("first_retrieved_at_ms", 1L);
            values.put("source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            values.put("structural_fingerprint", "structure");
            values.put("content_fingerprint", "content-" + id);
            values.put("last_content_fingerprint", "content-" + id);
            values.put("last_structural_fingerprint", "structure");
            values.put("last_source", MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE);
            values.put("last_validation", MonthlyArchivePeriod.VALIDATION_COMPLETE);
            long inserted = store.getWritableDatabase().insertOrThrow(
                    ArchiveFamilyStore.TABLE_PERIODS, null, values);
            assertTrue(inserted > 0L);
        } finally {
            store.close();
        }
    }
}
