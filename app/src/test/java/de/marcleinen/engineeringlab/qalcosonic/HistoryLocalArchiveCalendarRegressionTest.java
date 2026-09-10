package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for the shifted real-time pattern found during the v2.1 physical gate.
 *
 * <p>All values and identities are synthetic. The test intentionally models a meter clock whose
 * archive boundaries are offset from civil midnight and whose physical Day/Month intervals cross
 * IANA DST changes. No private meter identifier, consumption history or backup content is used.</p>
 */
public final class HistoryLocalArchiveCalendarRegressionTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test public void resolvedElapsedAdjacencySurvivesDstAndStillRejectsMissingBuckets() {
        long dayStart = Instant.parse("2026-03-28T22:59:00Z").toEpochMilli();
        long dayEnd = Instant.ofEpochMilli(dayStart).plus(Duration.ofHours(24)).toEpochMilli();
        String dayPrevious = HistoryResolvedTimeToken.boundary(dayStart, BERLIN);
        String day = HistoryResolvedTimeToken.interval(dayStart, dayEnd, BERLIN);

        assertTrue(HistoryResolvedTimeToken.adjacent(
                dayPrevious, day, HistorySemanticTimeline.Granularity.DAY));
        assertFalse(HistoryResolvedTimeToken.adjacent(
                dayPrevious,
                HistoryResolvedTimeToken.interval(
                        dayStart,
                        Instant.ofEpochMilli(dayStart).plus(Duration.ofHours(48)).toEpochMilli(),
                        BERLIN),
                HistorySemanticTimeline.Granularity.DAY));

        long monthStart = Instant.parse("2026-02-28T22:59:00Z").toEpochMilli();
        long monthEnd = Instant.ofEpochMilli(monthStart).plus(Duration.ofDays(31)).toEpochMilli();
        String monthPrevious = HistoryResolvedTimeToken.boundary(monthStart, BERLIN);
        String month = HistoryResolvedTimeToken.interval(monthStart, monthEnd, BERLIN);

        assertTrue(HistoryResolvedTimeToken.adjacent(
                monthPrevious, month, HistorySemanticTimeline.Granularity.MONTH));
        assertFalse(HistoryResolvedTimeToken.adjacent(
                monthPrevious,
                HistoryResolvedTimeToken.interval(
                        monthStart,
                        Instant.ofEpochMilli(monthStart).plus(Duration.ofDays(59)).toEpochMilli(),
                        BERLIN),
                HistorySemanticTimeline.Granularity.MONTH));
    }

    @Test public void localConsumptionKeepsCurrentResolvedIntervalInsteadOfPredecessorLabel() {
        long start = Instant.parse("2026-02-28T22:59:00Z").toEpochMilli();
        long end = Instant.ofEpochMilli(start).plus(Duration.ofDays(31)).toEpochMilli();
        String previousToken = HistoryResolvedTimeToken.boundary(start, BERLIN);
        String currentToken = HistoryResolvedTimeToken.interval(start, end, BERLIN);

        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("previous", HistorySemanticTimeline.Granularity.MONTH,
                        previousToken, true, 100.0, null, null, null, "", null,
                        "0x00000000"),
                observation("current", HistorySemanticTimeline.Granularity.MONTH,
                        currentToken, false, 107.0, null, null, null, "", null,
                        "0x00000000"));

        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(values);

        assertEquals(1, summary.availableBuckets);
        assertEquals(7.0, summary.total, 0.000001);
        assertEquals(currentToken, summary.points.get(0).timestamp);
        assertEquals(currentToken, summary.maximumAt);
        assertFalse(previousToken.equals(summary.points.get(0).timestamp));
    }

    @Test public void allStatisticsMetricsRetainResolvedIntervalOwnership() {
        long start = Instant.parse("2026-03-02T22:59:00Z").toEpochMilli();
        long end = Instant.ofEpochMilli(start).plus(Duration.ofHours(24)).toEpochMilli();
        String token = HistoryResolvedTimeToken.interval(start, end, BERLIN);
        HistoryStatisticsRepository.Observation value = observation(
                "current",
                HistorySemanticTimeline.Granularity.DAY,
                token,
                false,
                101.0,
                18.5,
                0.1,
                0.4,
                "",
                91,
                "0x00000200");
        List<HistoryStatisticsRepository.Observation> values = List.of(value);

        assertEquals(token, HistoryStatisticsAnalytics.temperature(values).points.get(0).timestamp);
        assertEquals(token, HistoryStatisticsAnalytics.flow(values).points.get(0).timestamp);
        assertEquals(token, HistoryStatisticsAnalytics.battery(values).points.get(0).timestamp);
        HistoryStatisticsAnalytics.AlarmSummary alarms = HistoryStatisticsAnalytics.alarms(values);
        assertEquals(1, alarms.events.size());
        assertEquals(token, alarms.events.get(0).timestamp);
    }

    @Test public void shiftedDayAndMonthAreDisplayedAsRealIntervalsNotFalseCalendarBuckets() {
        long dayStart = Instant.parse("2026-03-02T22:59:00Z").toEpochMilli();
        String day = HistoryResolvedTimeToken.interval(
                dayStart,
                Instant.ofEpochMilli(dayStart).plus(Duration.ofHours(24)).toEpochMilli(),
                BERLIN);
        String dayLabel = HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.DAY, day);
        assertTrue(dayLabel.contains("02.03.2026"));
        assertTrue(dayLabel.contains("03.03.2026"));
        assertTrue(dayLabel.contains("23:59"));

        long monthStart = Instant.parse("2026-02-28T22:59:00Z").toEpochMilli();
        String month = HistoryResolvedTimeToken.interval(
                monthStart,
                Instant.ofEpochMilli(monthStart).plus(Duration.ofDays(31)).toEpochMilli(),
                BERLIN);
        String monthLabel = HistoryTimePresentation.formatArchivePeriod(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.MONTH, month);
        assertTrue(monthLabel.contains("28.02.2026"));
        assertTrue(monthLabel.contains("01.04.2026"));
        assertTrue(monthLabel.contains("+01:00"));
        assertTrue(monthLabel.contains("+02:00"));
        assertEquals(monthLabel, HistoryTimePresentation.formatFloatingPrimary(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.MONTH, month));

        String axis = HistoryTimePresentation.formatAxis(
                Locale.GERMANY, HistorySemanticTimeline.Granularity.MONTH, month);
        assertTrue(axis.toLowerCase(Locale.ROOT).contains("feb"));
        assertTrue(axis.toLowerCase(Locale.ROOT).contains("apr"));
    }

    @Test public void sameCanonicalUtcIntervalRemainsValidAcrossRepresentativeIanaZones() {
        long start = Instant.parse("2026-02-28T22:59:00Z").toEpochMilli();
        long end = Instant.ofEpochMilli(start).plus(Duration.ofDays(31)).toEpochMilli();
        String[] zones = {
                "UTC",
                "Europe/Berlin",
                "Europe/Vilnius",
                "America/New_York",
                "Asia/Kolkata",
                "Australia/Lord_Howe",
                "Pacific/Auckland"
        };

        for (String zoneId : zones) {
            ZoneId zone = ZoneId.of(zoneId);
            String previous = HistoryResolvedTimeToken.boundary(start, zone);
            String current = HistoryResolvedTimeToken.interval(start, end, zone);
            assertTrue(zoneId, HistoryResolvedTimeToken.adjacent(
                    previous, current, HistorySemanticTimeline.Granularity.MONTH));

            HistoryResolvedTimeToken.Parsed parsed = HistoryResolvedTimeToken.parse(current);
            assertNotNull(parsed);
            assertEquals(start, parsed.startUtcMs);
            assertEquals(end, parsed.endUtcMs);
            assertFalse(HistoryTimePresentation.formatArchivePeriod(
                    Locale.US, HistorySemanticTimeline.Granularity.MONTH, current).isEmpty());
        }
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id,
            HistorySemanticTimeline.Granularity granularity,
            String timestamp,
            boolean contextOnly,
            double total,
            Double temperature,
            Double flow,
            Double maxFlow,
            String maxFlowAt,
            Integer battery,
            String rawAlarm) {
        return new HistoryStatisticsRepository.Observation(
                id,
                "TEST-METER",
                granularity,
                timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp),
                0L,
                false,
                contextOnly,
                "",
                total,
                null,
                null,
                null,
                flow,
                maxFlow,
                maxFlowAt,
                null,
                "",
                temperature,
                null,
                null,
                "",
                null,
                "",
                battery,
                "",
                rawAlarm,
                "",
                "",
                1,
                0,
                0,
                0,
                "TEST",
                "TEST");
    }
}
