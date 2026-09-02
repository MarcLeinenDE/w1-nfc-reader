package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class ArchivePersistenceCoordinatorTest {
    @Test public void laterFailurePreservesEarlierIndependentCommitCount() {
        AtomicInteger writes = new AtomicInteger();
        ArchivePersistenceCoordinator.Store store = (meter, period) -> {
            int call = writes.incrementAndGet();
            if (call == 2) throw new IllegalStateException("db-stop");
            return ArchivePersistenceCoordinator.WriteOutcome.INSERTED;
        };
        ArchivePersistenceCoordinator.Result result = ArchivePersistenceCoordinator.persist(
                store, "M1", ArchiveFamilyPeriod.Family.DAY,
                Arrays.asList(period("2026-08-30 00:00"), period("2026-08-29 00:00")));
        assertEquals(1, result.committed);
        assertEquals(1, result.inserted);
        assertEquals("2026-08-29 00:00", result.failedLoggerTimestamp);
        assertFalse(result.complete());
    }

    private static ArchiveFamilyPeriod period(String timestamp) {
        return new ArchiveFamilyPeriod(ArchiveFamilyPeriod.Family.DAY, timestamp,
                "2026-08-31T12:00:00Z", "FP", "NFC_ARCHIVE", "COMPLETE",
                ArchiveNormalizedValues.builder().totalVolume("1.000 m3").build());
    }
}
