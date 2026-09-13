package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ArchiveUtcProjectionTest {
    @Test public void repeatedRawHourOccurrencesRemainDistinctAndFormRealUtcInterval() {
        long anchorEpoch = Instant.parse("2026-10-25T02:30:00Z").toEpochMilli();
        MeterTimeModelStore.AnchorRecord anchor = anchor(7L, "M1", anchorEpoch,
                "2026-10-25 03:30", 30_000L, 100L);
        ArchiveFamilyStore.StoredPeriod older = period(1L, "M1", "2026-10-25 02:00",
                22_800L, "OT:22800", "04 6D 00 00", 0, 1);
        ArchiveFamilyStore.StoredPeriod newer = period(2L, "M1", "2026-10-25 02:00",
                26_400L, "OT:26400", "04 6D 00 00", 0, 0);

        List<ArchiveUtcProjection.Period> projected = ArchiveUtcProjection.resolveFamily(
                Arrays.asList(newer, older), Collections.singletonList(anchor));

        assertEquals(2, projected.size());
        assertEquals(ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                projected.get(0).status);
        assertEquals(ArchiveUtcProjection.PeriodStatus.RESOLVED, projected.get(1).status);
        assertNotEquals(projected.get(0).identity, projected.get(1).identity);
        assertTrue(projected.get(1).resolvedInterval());
        assertEquals(3_600_000L,
                projected.get(1).endUtcMs.longValue() - projected.get(1).startUtcMs.longValue());
        assertEquals(anchorEpoch - 7_200_000L, projected.get(0).endUtcMs.longValue());
        assertEquals(anchorEpoch - 3_600_000L, projected.get(1).endUtcMs.longValue());
        assertNotNull(projected.get(1).coverageInterval());
    }

    @Test public void newestVerifiedAnchorIsSelectedForWholeMeterTimeline() {
        long base = Instant.parse("2026-09-06T12:00:00Z").toEpochMilli();
        ArchiveFamilyStore.StoredPeriod source = period(3L, "M1", "2026-09-06 12:00",
                20_000L, "OT:20000", "04 6D 00 00", 0, 0);
        MeterTimeModelStore.AnchorRecord newest = anchor(10L, "M1", base + 10_000_000L,
                "2026-09-06 14:00", 30_000L, 50L);
        MeterTimeModelStore.AnchorRecord closerHistorical = anchor(11L, "M1", base + 1_000_000L,
                "2026-09-06 13:00", 21_000L, 200L);
        MeterTimeModelStore.AnchorRecord otherMeter = anchor(12L, "M2", base + 2_000_000L,
                "2026-09-06 13:00", 31_000L, 1L);

        ArchiveUtcProjection.Boundary boundary = ArchiveUtcProjection.resolveBoundary(
                source, Arrays.asList(newest, otherMeter, closerHistorical));

        assertTrue(boundary.resolved());
        assertEquals(Long.valueOf(10L), boundary.anchorId);
        assertEquals(Long.valueOf(base), boundary.utcMs);
    }

    @Test public void archiveNewerThanActiveAnchorFailsClosedWithoutOlderFallback() {
        long base = Instant.parse("2026-09-06T12:00:00Z").toEpochMilli();
        ArchiveFamilyStore.StoredPeriod source = period(30L, "M1", "2026-09-06 15:00",
                31_000L, "OT:31000", "04 6D 00 00", 0, 0);
        MeterTimeModelStore.AnchorRecord active = anchor(31L, "M1", base + 10_000_000L,
                "2026-09-06 14:00", 30_000L, 50L);
        MeterTimeModelStore.AnchorRecord historical = anchor(32L, "M1", base + 5_000_000L,
                "2026-09-06 13:00", 25_000L, 50L);

        ArchiveUtcProjection.Boundary boundary = ArchiveUtcProjection.resolveBoundary(
                source, Arrays.asList(historical, active));

        assertEquals(ArchiveUtcProjection.BoundaryStatus.RESOLUTION_FAILED, boundary.status);
        assertEquals(MeterTimeResolver.Status.ON_TIME_AFTER_ANCHOR, boundary.resolverStatus);
        assertEquals(Long.valueOf(active.id), boundary.anchorId);
        assertNull(boundary.utcMs);
    }

    @Test public void migratedOnTimeWithoutNativeTypeFEvidenceNeverClaimsUtc() {
        ArchiveFamilyStore.StoredPeriod legacy = period(4L, "M1", "2026-09-06 12:00",
                20_000L, "OT:20000", null, null, null);
        MeterTimeModelStore.AnchorRecord anchor = anchor(13L, "M1", 4_000_000L,
                "2026-09-06 13:00", 21_000L, 100L);

        ArchiveUtcProjection.Boundary result = ArchiveUtcProjection.resolveBoundary(
                legacy, Collections.singletonList(anchor));

        assertEquals(ArchiveUtcProjection.BoundaryStatus.NATIVE_EVIDENCE_MISSING, result.status);
        assertFalse(result.resolved());
        assertNull(result.utcMs);
    }

    @Test public void invalidArchiveTypeFIsRejectedBeforeResolver() {
        ArchiveFamilyStore.StoredPeriod invalid = period(5L, "M1", "2026-09-06 12:00",
                20_000L, "OT:20000", "04 6D 00 00", 1, 0);
        MeterTimeModelStore.AnchorRecord anchor = anchor(14L, "M1", 4_000_000L,
                "2026-09-06 13:00", 21_000L, 100L);

        ArchiveUtcProjection.Boundary result = ArchiveUtcProjection.resolveBoundary(
                invalid, Collections.singletonList(anchor));

        assertEquals(ArchiveUtcProjection.BoundaryStatus.TYPE_F_INVALID, result.status);
        assertEquals(MeterTimeResolver.Status.TYPE_F_INVALID, result.resolverStatus);
    }

    @Test public void anchorFromReplacementMeterCannotResolveOldMeterPeriod() {
        ArchiveFamilyStore.StoredPeriod source = period(6L, "OLD", "2026-09-06 12:00",
                20_000L, "OT:20000", "04 6D 00 00", 0, 0);
        MeterTimeModelStore.AnchorRecord replacement = anchor(15L, "NEW", 4_000_000L,
                "2026-09-06 13:00", 21_000L, 100L);

        ArchiveUtcProjection.Boundary result = ArchiveUtcProjection.resolveBoundary(
                source, Collections.singletonList(replacement));

        assertEquals(ArchiveUtcProjection.BoundaryStatus.NO_SUITABLE_ANCHOR, result.status);
        assertNull(result.anchorId);
    }

    private static MeterTimeModelStore.AnchorRecord anchor(
            long id, String meter, long epochMs, String raw, long onTime, long uncertaintyMs) {
        return new MeterTimeModelStore.AnchorRecord(
                id,
                meter,
                epochMs - uncertaintyMs,
                epochMs + uncertaintyMs,
                epochMs,
                uncertaintyMs,
                raw,
                "04 6D 00 00",
                false,
                false,
                onTime,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
    }

    private static ArchiveFamilyStore.StoredPeriod period(
            long id,
            String meter,
            String raw,
            Long onTimeSeconds,
            String occurrenceKey,
            String rawTypeFHex,
            Integer iv,
            Integer su) {
        return new ArchiveFamilyStore.StoredPeriod(
                id,
                meter,
                ArchiveFamilyPeriod.Family.HOUR,
                raw,
                occurrenceKey,
                ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL,
                onTimeSeconds,
                rawTypeFHex,
                iv,
                su,
                "2026-09-09T00:00:00Z",
                1L,
                "2026-09-09T00:00:00Z",
                1L,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                "structure",
                "content",
                1,
                0,
                0,
                0,
                0,
                0,
                "content",
                "structure",
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                "1.000 m3",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                onTimeSeconds == null ? null : onTimeSeconds + " s",
                null,
                new LinkedHashMap<>());
    }
}
