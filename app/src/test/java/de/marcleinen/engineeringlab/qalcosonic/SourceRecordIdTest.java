package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class SourceRecordIdTest {
    @Test public void sameNativeOccurrenceAlwaysProducesSameId() {
        String a = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2026-09-12 18:00",
                "OT:12345678");
        String b = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2026-09-12 18:00",
                "OT:12345678");

        assertEquals(a, b);
        assertTrue(a.startsWith(SourceRecordId.PREFIX));
        assertEquals(SourceRecordId.PREFIX.length() + 64, a.length());
    }

    @Test public void sameOnTimeAcrossFamiliesIsNotTheSameSourceRecord() {
        String hour = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2026-09-12 18:00",
                "OT:12345678");
        String day = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.DAY,
                "METER-1",
                "2026-09-12 18:00",
                "OT:12345678");

        assertNotEquals(hour, day);
    }

    @Test public void sameOccurrenceEvidenceOnAnotherMeterIsNotTheSameSourceRecord() {
        String first = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2026-09-12 18:00",
                "OT:12345678");
        String replacement = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-2",
                "2026-09-12 18:00",
                "OT:12345678");

        assertNotEquals(first, replacement);
    }

    @Test public void rawLoggerOccurrenceSeparatesPossibleOnTimeReuseAfterReset() {
        String beforeReset = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2026-01-10 05:00",
                "OT:3600");
        String afterReset = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.HOUR,
                "METER-1",
                "2027-02-20 09:00",
                "OT:3600");

        assertNotEquals(beforeReset, afterReset);
    }

    @Test public void legacyOccurrenceStillHasDeterministicMeterNativeIdentity() {
        String legacy = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.MONTH,
                "METER-1",
                "2025-12-01 00:00",
                null);
        String sameLegacy = SourceRecordId.forArchive(
                ArchiveFamilyPeriod.Family.MONTH,
                "METER-1",
                "2025-12-01 00:00",
                ArchiveOccurrenceKey.LEGACY);

        assertEquals(legacy, sameLegacy);
    }
}
