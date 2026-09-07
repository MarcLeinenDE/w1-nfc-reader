package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class HistoryStatisticsBatteryReplacementTest {
    @Test public void batteryChangeIsUnavailableAcrossDifferentMeterIdentities() {
        HistoryStatisticsRepository.Observation oldMeter = observation(
                "old", "OLD", "2026-09-01 00:00", 80);
        HistoryStatisticsRepository.Observation newMeter = observation(
                "new", "NEW", "2026-09-02 00:00", 100);

        HistoryStatisticsAnalytics.BatterySummary summary =
                HistoryStatisticsAnalytics.battery(List.of(oldMeter, newMeter));

        assertEquals(Integer.valueOf(80), summary.start);
        assertEquals(Integer.valueOf(100), summary.end);
        assertNull(summary.change);
        assertEquals("OLD", summary.points.get(0).segment);
        assertEquals("NEW", summary.points.get(1).segment);
    }

    private static HistoryStatisticsRepository.Observation observation(
            String id, String meter, String timestamp, int battery) {
        return new HistoryStatisticsRepository.Observation(
                id, meter, HistorySemanticTimeline.Granularity.DAY, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), 0L, false, false, "",
                100.0, null, null, null, null, null, "", null, "",
                null, null, null, "", null, "", battery,
                "", "0x00000000", "", "", 1, 0, 0, 0, "TEST", "TEST");
    }
}
