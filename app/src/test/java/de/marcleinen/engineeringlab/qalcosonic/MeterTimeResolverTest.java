package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MeterTimeResolverTest {
    @Test public void resolvesArchiveByOnTimeWithoutCivilTimezoneAssumption() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-09-06 12:00", 96_400L, false);

        MeterTimeResolver.Resolution result = MeterTimeResolver.resolve(anchor, archive);

        assertTrue(result.resolved());
        assertEquals(Instant.parse("2026-09-06T11:00:10Z").toEpochMilli(),
                result.epochMs.longValue());
        assertEquals(Long.valueOf(0L), result.rawOriginSkewMs);
        assertEquals(MeterTimeResolver.ClockRelation.ALIGNED, result.rawClockRelation);
        assertEquals(MeterTimeResolver.METHOD_ON_TIME_LIVE_ANCHOR, result.method);
    }

    @Test public void fixedRawClockRegimeRemainsResolvableAcrossCivilSpringDstChange() {
        long center = Instant.parse("2026-03-30T11:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-03-30 12:00", 200_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-03-29 12:00", 113_600L, false);

        MeterTimeResolver.Resolution result = MeterTimeResolver.resolve(anchor, archive);

        assertTrue(result.resolved());
        assertEquals(center - 86_400_000L, result.epochMs.longValue());
        assertEquals(Long.valueOf(0L), result.rawOriginSkewMs);
        assertEquals(MeterTimeResolver.ClockRelation.ALIGNED, result.rawClockRelation);
    }

    @Test public void rawClockJumpIsDiagnosticButDoesNotOverrideOnTimeUtc() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-09-06 13:00", 96_400L, false);

        MeterTimeResolver.Resolution result = MeterTimeResolver.resolve(anchor, archive);

        assertTrue(result.resolved());
        assertEquals(center - 3_600_000L, result.epochMs.longValue());
        assertEquals(Long.valueOf(3_600_000L), result.rawOriginSkewMs);
        assertEquals(MeterTimeResolver.ClockRelation.SHIFTED, result.rawClockRelation);
    }

    @Test public void realBackupShapedFiveMinuteRawOriginShiftRemainsResolvable() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-09-06 11:55", 96_400L, false);

        MeterTimeResolver.Resolution result = MeterTimeResolver.resolve(anchor, archive);

        assertTrue(result.resolved());
        assertEquals(center - 3_600_000L, result.epochMs.longValue());
        assertEquals(Long.valueOf(-300_000L), result.rawOriginSkewMs);
        assertEquals(MeterTimeResolver.ClockRelation.SHIFTED, result.rawClockRelation);
    }

    @Test public void onTimeAfterAnchorIsRejectedAsDiscontinuityEvidence() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-09-06 12:00", 100_001L, false);

        MeterTimeResolver.Resolution result = MeterTimeResolver.resolve(anchor, archive);

        assertEquals(MeterTimeResolver.Status.ON_TIME_AFTER_ANCHOR, result.status);
    }

    @Test public void meterReplacementCannotReuseAnotherMetersAnchor() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M2", "2026-09-06 12:00", 96_400L, false);

        assertEquals(MeterTimeResolver.Status.METER_MISMATCH,
                MeterTimeResolver.resolve(anchor, archive).status);
    }

    @Test public void invalidTypeFNeverProducesCanonicalUtc() {
        long center = Instant.parse("2026-09-06T12:00:10Z").toEpochMilli();
        VerifiedLiveTimeAnchor anchor = anchor(
                "M1", center, "2026-09-06 13:00", 100_000L);
        ArchiveTimeEvidence archive = archive(
                "M1", "2026-09-06 12:00", 96_400L, true);

        assertEquals(MeterTimeResolver.Status.TYPE_F_INVALID,
                MeterTimeResolver.resolve(anchor, archive).status);
    }

    private static VerifiedLiveTimeAnchor anchor(
            String meter, long center, String raw, long onTime) {
        return new VerifiedLiveTimeAnchor(
                meter, center - 100L, center + 100L, raw,
                "00 00 00 00", false, false, onTime);
    }

    private static ArchiveTimeEvidence archive(
            String meter, String raw, long onTime, boolean invalid) {
        return new ArchiveTimeEvidence(
                meter, ArchiveFamilyPeriod.Family.HOUR, raw,
                "00 00 00 00", invalid, false, onTime);
    }
}
