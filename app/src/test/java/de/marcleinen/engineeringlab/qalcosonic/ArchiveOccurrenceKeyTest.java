package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ArchiveOccurrenceKeyTest {
    @Test public void onTimeIsPreferredOverTypeFForNativeOccurrenceIdentity() {
        assertEquals("OT:80734200",
                ArchiveOccurrenceKey.fromEvidence(80_734_200L, "01 02 03 04"));
    }

    @Test public void validTypeFFallsBackWhenOnTimeIsUnavailable() {
        assertEquals("TF:0102ABCD",
                ArchiveOccurrenceKey.fromEvidence(null, "01 02 ab cd"));
    }

    @Test public void legacyIsUsedOnlyWhenNeitherDiscriminatorIsAvailable() {
        assertEquals(ArchiveOccurrenceKey.LEGACY,
                ArchiveOccurrenceKey.fromEvidence(null, null));
        assertEquals(ArchiveOccurrenceKey.LEGACY,
                ArchiveOccurrenceKey.fromEvidence(null, "not-type-f"));
    }

    @Test public void v2DisplayDurationsOnlyMigrateWhenTheyMapExactlyToSeconds() {
        assertEquals(Long.valueOf(80_734_200L),
                ArchiveOccurrenceKey.parseDurationSeconds("80734200 s"));
        assertEquals(Long.valueOf(5_400L),
                ArchiveOccurrenceKey.parseDurationSeconds("1.5 h"));
        assertEquals(Long.valueOf(90L),
                ArchiveOccurrenceKey.parseDurationSeconds("1,5 min"));
        assertNull(ArchiveOccurrenceKey.parseDurationSeconds("1.5 s"));
        assertNull(ArchiveOccurrenceKey.parseDurationSeconds("-1 s"));
        assertNull(ArchiveOccurrenceKey.parseDurationSeconds("unknown"));
    }

    @Test public void migratedV2OnTimeDirectlyProducesStableOccurrenceKey() {
        assertEquals("OT:80853000",
                ArchiveOccurrenceKey.fromLegacyOnTime("80853000 s"));
    }
}
