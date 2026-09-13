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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class DataPortabilityCsvOccurrenceIdentityTest {
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

    @Test public void csvExportRowsKeepRepeatedRawHourIdentitiesDistinct() throws Exception {
        insertArchive(1L, "OT:22800", 22_800L, "10.000");
        insertArchive(2L, "OT:26400", 26_400L, "10.100");

        Method exportRows = DataPortabilityCsvV3.class.getDeclaredMethod("exportRows", Context.class);
        exportRows.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) exportRows.invoke(null, context);

        Set<String> identities = new HashSet<>();
        int hourRows = 0;
        for (Object row : rows) {
            Field type = row.getClass().getDeclaredField("type");
            Field identity = row.getClass().getDeclaredField("identity");
            type.setAccessible(true);
            identity.setAccessible(true);
            if (!"HOUR".equals(type.get(row))) continue;
            hourRows++;
            identities.add((String) identity.get(row));
        }

        assertEquals(2, hourRows);
        assertEquals(2, identities.size());
        assertTrue(identities.contains("M1|HOUR|2026-10-25 02:00|OT:22800"));
        assertTrue(identities.contains("M1|HOUR|2026-10-25 02:00|OT:26400"));
    }

    private void insertArchive(long id, String occurrenceKey, long onTimeSeconds, String totalVolume) {
        try (ArchiveFamilyStore store = new ArchiveFamilyStore(context)) {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("meter_id", "M1");
            values.put("archive_family", ArchiveFamilyPeriod.Family.HOUR.name());
            values.put("logger_timestamp", "2026-10-25 02:00");
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
