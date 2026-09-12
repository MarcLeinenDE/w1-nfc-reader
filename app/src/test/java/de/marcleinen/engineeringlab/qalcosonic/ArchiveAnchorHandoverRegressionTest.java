package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Regression coverage for real-time projection when consecutive archive boundaries use different anchors. */
public final class ArchiveAnchorHandoverRegressionTest {
    private static final String METER = "SYNTHETIC";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final long HOUR = 3_600L;
    private static final long DAY = 86_400L;

    @Test public void hourRemainsValidWhenAnchorHandoverAddsProjectionSkew() {
        assertValidAcrossAnchorHandover(ArchiveFamilyPeriod.Family.HOUR, HOUR);
    }

    @Test public void dayRemainsValidWhenAnchorHandoverAddsProjectionSkew() {
        assertValidAcrossAnchorHandover(ArchiveFamilyPeriod.Family.DAY, DAY);
    }

    @Test public void monthRemainsValidWhenAnchorHandoverAddsProjectionSkew() {
        assertValidAcrossAnchorHandover(ArchiveFamilyPeriod.Family.MONTH, 30L * DAY);
    }

    @Test public void missingNativeHourRecordIsKnownCoverageGapNotCompressedBucket() {
        long firstOnTime = 100_000L;
        ArchiveFamilyStore.StoredPeriod first = period(
                1L, ArchiveFamilyPeriod.Family.HOUR, firstOnTime, "2026-09-10 10:00");
        ArchiveFamilyStore.StoredPeriod afterMissing = period(
                2L, ArchiveFamilyPeriod.Family.HOUR, firstOnTime + 2L * HOUR, "2026-09-10 12:00");
        MeterTimeModelStore.AnchorRecord anchor = anchor(
                10L, 1_800_000_000_000L, firstOnTime + 3L * HOUR, 100L);

        List<ArchiveUtcProjection.Period> projected = ArchiveUtcProjection.resolveFamily(
                Arrays.asList(first, afterMissing), Arrays.asList(anchor));

        assertEquals(2, projected.size());
        ArchiveUtcProjection.Period oldest = projected.get(0);
        ArchiveUtcProjection.Period gap = projected.get(1);
        assertEquals(ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE, oldest.status);
        assertNull(oldest.startBoundary);
        assertEquals(ArchiveUtcProjection.PeriodStatus.NATIVE_GAP, gap.status);
        assertFalse(gap.resolvedInterval());
        assertNull(gap.coverageInterval());

        ArchiveWindowCoverage.Result coverage = ArchiveWindowCoverage.evaluateUtc(
                gap.startUtcMs,
                gap.endUtcMs,
                projected);
        assertFalse(coverage.relevantUnresolvedTime);
        assertEquals(UtcCoverage.Status.GAP, coverage.coverage.status);

        // An all-periods view naturally has no predecessor for the oldest retained boundary.
        // Neither that open retention edge nor a known native gap is LOCAL time ambiguity.
        assertFalse(HistoryLocalQueryRepository.contributesUnresolvedAllPeriodsWarning(oldest));
        assertFalse(HistoryLocalQueryRepository.contributesUnresolvedAllPeriodsWarning(gap));
    }

    @Test public void interiorUnresolvedStartStillWarnsInAllPeriodsView() {
        ArchiveFamilyStore.StoredPeriod source = period(
                20L, ArchiveFamilyPeriod.Family.HOUR, 200_000L, "2026-09-10 13:00");
        ArchiveUtcProjection.Boundary unresolvedPrevious = new ArchiveUtcProjection.Boundary(
                source,
                ArchiveUtcProjection.BoundaryStatus.NO_SUITABLE_ANCHOR,
                null,
                -1L,
                null,
                null,
                null,
                null,
                null);
        ArchiveUtcProjection.Boundary resolvedEnd = new ArchiveUtcProjection.Boundary(
                source,
                ArchiveUtcProjection.BoundaryStatus.RESOLVED,
                1_800_000_000_000L,
                100L,
                30L,
                null,
                null,
                null,
                "TEST");
        ArchiveUtcProjection.Period interior = new ArchiveUtcProjection.Period(
                source,
                ArchiveUtcProjection.PeriodStatus.START_BOUNDARY_UNAVAILABLE,
                unresolvedPrevious,
                resolvedEnd);

        assertTrue(HistoryLocalQueryRepository.contributesUnresolvedAllPeriodsWarning(interior));
    }

    private static void assertValidAcrossAnchorHandover(
            ArchiveFamilyPeriod.Family family,
            long nativeDeltaSeconds) {
        long firstOnTime = 100_000L;
        long oldAnchorOnTime = firstOnTime + nativeDeltaSeconds / 2L;
        long currentOnTime = firstOnTime + nativeDeltaSeconds;
        long newAnchorOnTime = currentOnTime + nativeDeltaSeconds / 2L;
        long oldAnchorEpoch = 1_800_000_000_000L;
        long anchorOnTimeAdvance = newAnchorOnTime - oldAnchorOnTime;
        long newAnchorEpoch = oldAnchorEpoch + anchorOnTimeAdvance * 1000L + 62_323L;

        ArchiveFamilyStore.StoredPeriod previous = period(
                1L, family, firstOnTime, "2026-09-10 00:00");
        ArchiveFamilyStore.StoredPeriod current = period(
                2L, family, currentOnTime, "2026-09-11 00:00");
        MeterTimeModelStore.AnchorRecord oldAnchor = anchor(
                10L, oldAnchorEpoch, oldAnchorOnTime, 600L);
        MeterTimeModelStore.AnchorRecord newAnchor = anchor(
                11L, newAnchorEpoch, newAnchorOnTime, 150L);

        List<ArchiveUtcProjection.Period> projected = ArchiveUtcProjection.resolveFamily(
                Arrays.asList(previous, current), Arrays.asList(oldAnchor, newAnchor));

        assertEquals(2, projected.size());
        ArchiveUtcProjection.Period interval = projected.get(1);
        assertEquals(ArchiveUtcProjection.PeriodStatus.RESOLVED, interval.status);
        assertTrue(interval.resolvedInterval());
        assertEquals(Long.valueOf(oldAnchor.id), interval.startBoundary.anchorId);
        assertEquals(Long.valueOf(newAnchor.id), interval.endBoundary.anchorId);
        long nominalMs = nativeDeltaSeconds * 1000L;
        long projectedMs = interval.endUtcMs - interval.startUtcMs;
        assertNotEquals(nominalMs, projectedMs);
        assertEquals(nominalMs + 62_323L, projectedMs);

        String predecessor = HistoryResolvedTimeToken.boundary(interval.startUtcMs, BERLIN);
        String currentToken = HistoryResolvedTimeToken.interval(
                interval.startUtcMs, interval.endUtcMs, BERLIN);
        assertTrue(HistoryResolvedTimeToken.adjacent(
                predecessor,
                currentToken,
                granularity(family)));
    }

    private static HistorySemanticTimeline.Granularity granularity(
            ArchiveFamilyPeriod.Family family) {
        switch (family) {
            case HOUR:
                return HistorySemanticTimeline.Granularity.HOUR;
            case DAY:
                return HistorySemanticTimeline.Granularity.DAY;
            case MONTH:
                return HistorySemanticTimeline.Granularity.MONTH;
            case YEAR:
                return HistorySemanticTimeline.Granularity.YEAR;
            default:
                throw new IllegalArgumentException("unsupported family");
        }
    }

    private static MeterTimeModelStore.AnchorRecord anchor(
            long id,
            long epochMs,
            long onTimeSeconds,
            long uncertaintyMs) {
        return new MeterTimeModelStore.AnchorRecord(
                id,
                METER,
                epochMs - uncertaintyMs,
                epochMs + uncertaintyMs,
                epochMs,
                uncertaintyMs,
                "2026-09-11 18:54",
                "04 6D 00 00",
                false,
                false,
                onTimeSeconds,
                MeterTimeModelStore.ANCHOR_PROVENANCE_VERIFIED_LIVE_DEFAULT,
                MeterTimeModelStore.ANCHOR_VALIDATION_COMPLETE);
    }

    private static ArchiveFamilyStore.StoredPeriod period(
            long id,
            ArchiveFamilyPeriod.Family family,
            long onTimeSeconds,
            String rawTimestamp) {
        return new ArchiveFamilyStore.StoredPeriod(
                id,
                METER,
                family,
                rawTimestamp,
                "OT:" + onTimeSeconds,
                ArchiveFamilyPeriod.TIME_BASIS_METER_LOCAL,
                onTimeSeconds,
                "04 6D 00 00",
                0,
                0,
                "2026-09-11T18:00:00Z",
                1L,
                "2026-09-11T18:00:00Z",
                1L,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                "structure-" + id,
                "content-" + id,
                1,
                0,
                0,
                0,
                0,
                0,
                "content-" + id,
                "structure-" + id,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                id + ".000 m3",
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
                onTimeSeconds + " s",
                null,
                new LinkedHashMap<>());
    }
}
