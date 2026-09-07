package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public final class HistoryStatisticsAnalyticsTest {
    @Test public void consumptionUsesExactStartBoundaryContextAndNeverCrossesMeterIdentity() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("a0", "A", "2026-09-06 09:00", true, 100.0,
                        null, null, null, "", null, "0x00000000"),
                observation("a1", "A", "2026-09-06 10:00", false, 100.4,
                        null, null, null, "", null, "0x00000000"),
                observation("a2", "A", "2026-09-06 11:00", false, 101.0,
                        null, null, null, "", null, "0x00000000"),
                observation("b1", "B", "2026-09-06 11:00", false, 2.0,
                        null, null, null, "", null, "0x00000000"));

        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(values);

        assertEquals(1.0, summary.total, 0.000001);
        assertEquals(2, summary.availableBuckets);
        assertEquals(0.5, summary.average, 0.000001);
        assertEquals("2026-09-06 09:00", summary.points.get(0).timestamp);
        assertEquals("A", summary.points.get(0).segment);
    }

    @Test public void consumptionDoesNotCompressMissingBuckets() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("h10", "A", "2026-09-06 10:00", true, 100.0,
                        null, null, null, "", null, "0x00000000"),
                observation("h12", "A", "2026-09-06 12:00", false, 101.0,
                        null, null, null, "", null, "0x00000000"),
                observation("h13", "A", "2026-09-06 13:00", false, 101.4,
                        null, null, null, "", null, "0x00000000"));

        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(values);

        // 10 -> 12 spans a missing Hour bucket and is intentionally not collapsed into one bar.
        // 12 -> 13 is adjacent and represents the 12:00-13:00 completed Hour period.
        assertEquals(1, summary.availableBuckets);
        assertEquals(0.4, summary.total, 0.000001);
        assertEquals("2026-09-06 12:00", summary.points.get(0).timestamp);
    }

    @Test public void temperatureDropsEstablishedMinus100SentinelAndUsesPeriodStart() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("t0", "A", "2026-09-06 10:00", false, 100.0,
                        -100.0, null, null, "", null, "0x00000000"),
                observation("t1", "A", "2026-09-06 11:00", false, 100.1,
                        18.5, null, null, "", null, "0x00000000"),
                observation("t2", "A", "2026-09-06 12:00", false, 100.2,
                        20.0, null, null, "", null, "0x00000000"));

        HistoryStatisticsAnalytics.TemperatureSummary summary =
                HistoryStatisticsAnalytics.temperature(values);

        assertEquals(2, summary.points.size());
        assertEquals("2026-09-06 10:00", summary.points.get(0).timestamp);
        assertEquals("2026-09-06 11:00", summary.points.get(1).timestamp);
        assertEquals(18.5, summary.minimum, 0.000001);
        assertEquals(20.0, summary.maximum, 0.000001);
        assertEquals(1.5, summary.span, 0.000001);
        assertNull(HistoryStatisticsAnalytics.validTemperature(-100.0));
    }

    @Test public void flowUsesStoredMaximumFlowAndExplicitExtremumTime() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("f1", "A", "2026-09-06 10:00", false, 100.0,
                        null, 0.0, 0.42, "2026-09-06 09:37", null, "0x00000000"),
                observation("f2", "A", "2026-09-06 11:00", false, 100.1,
                        null, 0.0, 0.75, "2026-09-06 10:42", null, "0x00000000"));

        HistoryStatisticsAnalytics.FlowSummary summary = HistoryStatisticsAnalytics.flow(values);

        assertEquals(2, summary.points.size());
        assertEquals("2026-09-06 09:00", summary.points.get(0).timestamp);
        assertEquals(0.75, summary.maximum, 0.000001);
        assertEquals("2026-09-06 10:42", summary.maximumAt);
        assertEquals(0.585, summary.averagePeak, 0.000001);
    }

    @Test public void batteryReportsPercentagePointChangeOnCompletedPeriods() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("b1", "A", "2026-09-01 00:00", false, 100.0,
                        null, null, null, "", 90, "0x00000000"),
                observation("b2", "A", "2026-09-02 00:00", false, 100.1,
                        null, null, null, "", 89, "0x00000000"));

        HistoryStatisticsAnalytics.BatterySummary summary = HistoryStatisticsAnalytics.battery(values);

        assertEquals(Integer.valueOf(90), summary.start);
        assertEquals(Integer.valueOf(89), summary.end);
        assertEquals(Integer.valueOf(-1), summary.change);
        assertEquals("2026-08-31 23:00", summary.startAt);
        assertEquals("2026-09-01 23:00", summary.endAt);
    }

    @Test public void historicalAlarmFlagsRemainPeriodEvidenceNotInventedTransitions() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("p", "A", "2026-09-06 09:00", true, 100.0,
                        null, null, null, "", null, "0x00000000"),
                observation("a1", "A", "2026-09-06 10:00", false, 100.1,
                        null, null, null, "", null, "0x00000200"),
                observation("a2", "A", "2026-09-06 11:00", false, 100.2,
                        null, null, null, "", null, "0x00000200"),
                observation("clear", "A", "2026-09-06 12:00", false, 100.3,
                        null, null, null, "", null, "0x00000000"));

        HistoryStatisticsAnalytics.AlarmSummary summary = HistoryStatisticsAnalytics.alarms(values);

        assertEquals(2, summary.events.size());
        assertEquals(HistoryStatisticsAnalytics.AlarmEventType.OBSERVED, summary.events.get(0).type);
        assertEquals(HistoryStatisticsAnalytics.AlarmEventType.OBSERVED, summary.events.get(1).type);
        assertEquals("2026-09-06 09:00", summary.events.get(0).timestamp);
        assertEquals("2026-09-06 10:00", summary.events.get(1).timestamp);
        assertEquals(2, summary.alarmObservations);
        assertFalse(summary.activeAtEnd);
    }

    @Test public void batteryAndTemperatureKeepSeparateMeterSegmentsAvailableForChartGaps() {
        List<HistoryStatisticsRepository.Observation> values = List.of(
                observation("a", "A", "2026-09-01 00:00", false, 100.0,
                        18.0, null, null, "", 80, "0x00000000"),
                observation("b", "B", "2026-09-02 00:00", false, 2.0,
                        19.0, null, null, "", 100, "0x00000000"));

        HistoryStatisticsAnalytics.TemperatureSummary temp = HistoryStatisticsAnalytics.temperature(values);
        HistoryStatisticsAnalytics.BatterySummary battery = HistoryStatisticsAnalytics.battery(values);

        assertEquals("A", temp.points.get(0).segment);
        assertEquals("B", temp.points.get(1).segment);
        assertEquals("A", battery.points.get(0).segment);
        assertEquals("B", battery.points.get(1).segment);
        assertNull(battery.change);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, String meter, String timestamp, boolean contextOnly, double total,
            Double waterTemperature, Double flow, Double maxFlow, String maxFlowAt,
            Integer battery, String rawAlarm) {
        return new HistoryStatisticsRepository.Observation(
                id, meter, HistorySemanticTimeline.Granularity.HOUR, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), 0L, false, contextOnly, "",
                total, null, null, null, flow, maxFlow, maxFlowAt, null, "",
                waterTemperature, null, null, "", null, "", battery,
                "", rawAlarm, "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
