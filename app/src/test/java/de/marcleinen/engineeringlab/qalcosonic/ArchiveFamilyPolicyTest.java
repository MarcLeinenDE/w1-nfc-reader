package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ArchiveFamilyPolicyTest {
    @Test public void applicationSelectFramesMatchFrozenPhysicalResearch() {
        assertArrayEquals(
                hex("68 04 04 68 73 FE 50 60 21 16"),
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR).applicationSelectFrame());
        assertArrayEquals(
                hex("68 04 04 68 73 FE 50 30 F1 16"),
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.DAY).applicationSelectFrame());
        assertArrayEquals(
                hex("68 04 04 68 73 FE 50 40 01 16"),
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH).applicationSelectFrame());
        assertArrayEquals(
                hex("68 04 04 68 73 FE 50 20 E1 16"),
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.YEAR).applicationSelectFrame());
    }

    @Test public void progressionUsesFamilyNativeRawMeterPeriods() {
        ArchiveFamilyPolicy hour = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR);
        ArchiveFamilyPolicy day = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.DAY);
        ArchiveFamilyPolicy month = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH);
        ArchiveFamilyPolicy year = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.YEAR);

        assertEquals("2026-09-05 23:00", hour.expectedPreviousRawTimestamp("2026-09-06 00:00"));
        assertEquals("2026-02-28 00:00", day.expectedPreviousRawTimestamp("2026-03-01 00:00"));
        assertEquals("2024-02-29 00:00", day.expectedPreviousRawTimestamp("2024-03-01 00:00"));
        assertEquals("2025-12-01 00:00", month.expectedPreviousRawTimestamp("2026-01-01 00:00"));
        assertEquals("2025-01-01 00:00", year.expectedPreviousRawTimestamp("2026-01-01 00:00"));

        assertEquals("2026-09-06 01:00", hour.expectedNextRawTimestamp("2026-09-06 00:00"));
        assertEquals("2026-09-07 00:00", day.expectedNextRawTimestamp("2026-09-06 00:00"));
        assertEquals("2026-10-01 00:00", month.expectedNextRawTimestamp("2026-09-01 00:00"));
        assertEquals("2027-01-01 00:00", year.expectedNextRawTimestamp("2026-01-01 00:00"));
    }

    @Test public void onTimeMustMatchRawBackwardProgression() {
        ArchiveFamilyPolicy hour = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR);
        assertTrue(hour.isExpectedPreviousIdentity(
                "2026-09-06 10:00", 100_000L,
                "2026-09-06 09:00", 96_400L));
        assertFalse(hour.isExpectedPreviousIdentity(
                "2026-09-06 10:00", 100_000L,
                "2026-09-06 09:00", 96_399L));
        assertFalse(hour.isExpectedPreviousIdentity(
                "2026-09-06 10:00", 100_000L,
                "2026-09-06 08:00", 92_800L));

        ArchiveFamilyPolicy month = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH);
        long augustSeconds = 31L * 24L * 60L * 60L;
        assertTrue(month.isExpectedPreviousIdentity(
                "2026-09-01 00:00", 50_000_000L,
                "2026-08-01 00:00", 50_000_000L - augustSeconds));
    }

    @Test public void boundariesAreCalculatedFromRawMeterClockPerFamily() {
        ArchiveFamilyPolicy.Boundary hour = ArchiveFamilyPolicy
                .forFamily(ArchiveFamilyPeriod.Family.HOUR)
                .nextBoundary("2026-09-06 13:08");
        assertTrue(hour.valid);
        assertEquals("2026-09-06 14:00", hour.nextRawBoundary);
        assertEquals(52L * 60L * 1000L, hour.exactFromMinuteMarkMs);
        assertEquals(51L * 60L * 1000L, hour.conservativeRemainingMs);

        ArchiveFamilyPolicy.Boundary day = ArchiveFamilyPolicy
                .forFamily(ArchiveFamilyPeriod.Family.DAY)
                .nextBoundary("2026-09-06 23:58");
        assertTrue(day.valid);
        assertEquals("2026-09-07 00:00", day.nextRawBoundary);
        assertEquals(60_000L, day.conservativeRemainingMs);

        ArchiveFamilyPolicy.Boundary month = ArchiveFamilyPolicy
                .forFamily(ArchiveFamilyPeriod.Family.MONTH)
                .nextBoundary("2026-09-30 23:58");
        assertTrue(month.valid);
        assertEquals("2026-10-01 00:00", month.nextRawBoundary);
        assertEquals(60_000L, month.conservativeRemainingMs);

        ArchiveFamilyPolicy.Boundary year = ArchiveFamilyPolicy
                .forFamily(ArchiveFamilyPeriod.Family.YEAR)
                .nextBoundary("2026-12-31 23:58");
        assertTrue(year.valid);
        assertEquals("2027-01-01 00:00", year.nextRawBoundary);
        assertEquals(60_000L, year.conservativeRemainingMs);
    }

    @Test public void rawBoundaryMathDoesNotApplyCivilDstRules() {
        ArchiveFamilyPolicy day = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.DAY);
        ArchiveFamilyPolicy.Boundary spring = day.nextBoundary("2026-03-29 00:00");
        ArchiveFamilyPolicy.Boundary autumn = day.nextBoundary("2026-10-25 00:00");

        assertEquals(24L * 60L * 60L * 1000L - 60_000L, spring.conservativeRemainingMs);
        assertEquals(24L * 60L * 60L * 1000L - 60_000L, autumn.conservativeRemainingMs);
    }

    @Test public void invalidRawTimestampNeverProducesBoundaryOrProgression() {
        ArchiveFamilyPolicy hour = ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR);
        assertNull(hour.expectedPreviousRawTimestamp("not-a-time"));
        assertFalse(hour.nextBoundary("2026-02-30 12:00").valid);
        assertFalse(hour.isExpectedPreviousIdentity(
                "not-a-time", 100L, "also-bad", 50L));
    }

    private static byte[] hex(String value) {
        String[] parts = value.split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }
}
