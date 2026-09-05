package de.marcleinen.engineeringlab.qalcosonic;

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
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchiveFamilyStoreTest {
    private Context context;
    private ArchiveFamilyStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        store = new ArchiveFamilyStore(context);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
    }

    @Test public void sameTimestampAcrossFamiliesRemainsThreeCanonicalPeriods() {
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.HOUR, "2026-08-01 00:00", "1.000 m3", "h")));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.DAY, "2026-08-01 00:00", "2.000 m3", "d")));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH, "2026-08-01 00:00", "3.000 m3", "m")));

        List<ArchiveFamilyStore.StoredPeriod> rows = store.getPeriods("M1", null);
        assertEquals(3, rows.size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.HOUR).size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.DAY).size());
        assertEquals(1, store.getKnownTimestamps("M1", ArchiveFamilyPeriod.Family.MONTH).size());
    }

    @Test public void presentationNumberDoesNotTreatUnitSuffixDigitAsMeasurement() {
        assertEquals("0", ArchiveFamilyStore.measurementNumber("0 m3"));
        assertEquals("203.518", ArchiveFamilyStore.measurementNumber("203.518 m3"));
        assertEquals("1.931", ArchiveFamilyStore.measurementNumber("1,931m3"));

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                store.upsert("M1", period(ArchiveFamilyPeriod.Family.MONTH,
                        "2024-09-01 00:00", "0 m3", "zero")));
        assertEquals("0", store.getPeriods("M1", ArchiveFamilyPeriod.Family.MONTH).get(0).totalVolume);
    }

    @Test public void identicalResyncAggregatesConfirmationWithoutConflictRows() {
        ArchiveFamilyPeriod first = period(ArchiveFamilyPeriod.Family.MONTH,
                "2026-08-01 00:00", "196.668 m3", "custom-A");
        ArchiveFamilyPeriod second = new ArchiveFamilyPeriod(first.family, first.loggerTimestamp,
                "2026-08-31T13:00:00Z", first.structuralFingerprint, first.source,
                first.validation, first.values);
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", first));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL, store.upsert("M1", second));

        ArchiveFamilyStore.StoredPeriod row = store.getPeriods("M1", ArchiveFamilyPeriod.Family.MONTH).get(0);
        assertEquals(2, row.observationCount);
        assertEquals(1, row.identicalContentConfirmations);
        assertEquals(0, row.revisionCount);
        assertEquals(0, store.conflictCount("M1", ArchiveFamilyPeriod.Family.MONTH, first.loggerTimestamp));
        assertEquals("42", row.extraValues.get("future:custom-A"));
    }

    @Test public void changedFutureExtraValueIsConflictAndCanonicalEvidenceStays() {
        ArchiveFamilyPeriod first = period(ArchiveFamilyPeriod.Family.HOUR,
                "2026-08-31 12:00", "200.000 m3", "x");
        ArchiveNormalizedValues changedValues = ArchiveNormalizedValues.builder()
                .totalVolume("200.000 m3")
                .extra("future:x", "99")
                .build();
        ArchiveFamilyPeriod changed = new ArchiveFamilyPeriod(ArchiveFamilyPeriod.Family.HOUR,
                "2026-08-31 12:00", "2026-08-31T13:00:00Z", "FP-HOUR",
                "NFC_ARCHIVE", "COMPLETE", changedValues);

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", first));
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED, store.upsert("M1", changed));
        ArchiveFamilyStore.StoredPeriod canonical = store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals("42", canonical.extraValues.get("future:x"));
        assertEquals(2, canonical.observationCount);
        assertEquals(1, canonical.revisionCount);
        assertTrue((canonical.conflictFlags & ArchiveFamilyStore.CONFLICT_CONTENT) != 0);
        assertEquals(1, store.conflictCount("M1", ArchiveFamilyPeriod.Family.HOUR, "2026-08-31 12:00"));
    }

    private static ArchiveFamilyPeriod period(ArchiveFamilyPeriod.Family family, String timestamp,
                                               String total, String extraName) {
        ArchiveNormalizedValues values = ArchiveNormalizedValues.builder()
                .totalVolume(total)
                .batteryPercent("91 %")
                .extra("future:" + extraName, "42")
                .build();
        return new ArchiveFamilyPeriod(family, timestamp, "2026-08-31T12:00:00Z",
                family == ArchiveFamilyPeriod.Family.HOUR ? "FP-HOUR" : "FP-" + family.name(),
                "NFC_ARCHIVE", "COMPLETE", values);
    }
}
