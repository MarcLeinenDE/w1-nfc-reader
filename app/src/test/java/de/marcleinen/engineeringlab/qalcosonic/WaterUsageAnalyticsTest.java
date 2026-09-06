package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WaterUsageAnalyticsTest {
    @Test
    public void historyDeltaUsesPreviousPointFromSameGranularity() throws Exception {
        List<WaterUsageAnalytics.Point> points = List.of(
                point("aug-month", "2026-08-01 00:00", ms("2026-08-01 00:00"),
                        HistorySemanticTimeline.Granularity.MONTH, 196.668),
                point("aug30-day", "2026-08-30 00:00", ms("2026-08-30 00:00"),
                        HistorySemanticTimeline.Granularity.DAY, 203.200),
                point("aug31-day", "2026-08-31 00:00", ms("2026-08-31 00:00"),
                        HistorySemanticTimeline.Granularity.DAY, 203.518),
                point("aug31-22-hour", "2026-08-31 22:00", ms("2026-08-31 22:00"),
                        HistorySemanticTimeline.Granularity.HOUR, 203.400),
                point("aug31-23-hour", "2026-08-31 23:00", ms("2026-08-31 23:00"),
                        HistorySemanticTimeline.Granularity.HOUR, 203.518),
                point("sep-month", "2026-09-01 00:00", ms("2026-09-01 00:00"),
                        HistorySemanticTimeline.Granularity.MONTH, 203.518));

        List<WaterUsageAnalytics.HistoryDelta> result = WaterUsageAnalytics.historyNewestFirst(points);

        WaterUsageAnalytics.HistoryDelta september = deltaFor(result, "sep-month");
        assertNotNull(september);
        assertEquals(6.850, september.consumptionSincePreviousM3, 0.000001);
        assertEquals("aug-month", september.previousPoint.identity);
        assertEquals("2026-08-01 00:00", september.previousPoint.timestamp);

        WaterUsageAnalytics.HistoryDelta aug31Day = deltaFor(result, "aug31-day");
        assertNotNull(aug31Day);
        assertEquals(0.318, aug31Day.consumptionSincePreviousM3, 0.000001);
        assertEquals("aug30-day", aug31Day.previousPoint.identity);

        WaterUsageAnalytics.HistoryDelta aug31Hour = deltaFor(result, "aug31-23-hour");
        assertNotNull(aug31Hour);
        assertEquals(0.118, aug31Hour.consumptionSincePreviousM3, 0.000001);
        assertEquals("aug31-22-hour", aug31Hour.previousPoint.identity);
    }

    @Test
    public void historyDeltaDoesNotCrossMeterReplacement() throws Exception {
        WaterUsageAnalytics.Point oldMeter = point("old", "11111111", "2026-08-01 00:00",
                ms("2026-08-01 00:00"), HistorySemanticTimeline.Granularity.MONTH, 500.0);
        WaterUsageAnalytics.Point newMeter = point("new", "22222222", "2026-09-01 00:00",
                ms("2026-09-01 00:00"), HistorySemanticTimeline.Granularity.MONTH, 5.0);

        WaterUsageAnalytics.HistoryDelta delta = deltaFor(
                WaterUsageAnalytics.historyNewestFirst(List.of(oldMeter, newMeter)), "new");
        assertNotNull(delta);
        assertNull(delta.consumptionSincePreviousM3);
        assertNull(delta.previousPoint);
    }

    @Test
    public void sameTimestampDifferentGranularitiesDoNotBecomeEachOthersPreviousPoint() throws Exception {
        long feb = ms("2026-02-01 00:00");
        List<WaterUsageAnalytics.Point> points = List.of(
                point("jan-month", "2026-01-01 00:00", ms("2026-01-01 00:00"),
                        HistorySemanticTimeline.Granularity.MONTH, 100.0),
                point("jan31-day", "2026-01-31 00:00", ms("2026-01-31 00:00"),
                        HistorySemanticTimeline.Granularity.DAY, 109.0),
                point("feb-month", "2026-02-01 00:00", feb,
                        HistorySemanticTimeline.Granularity.MONTH, 110.0),
                point("feb-day", "2026-02-01 00:00", feb,
                        HistorySemanticTimeline.Granularity.DAY, 110.0));

        List<WaterUsageAnalytics.HistoryDelta> result = WaterUsageAnalytics.historyNewestFirst(points);
        assertEquals(10.0, deltaFor(result, "feb-month").consumptionSincePreviousM3, 0.000001);
        assertEquals(1.0, deltaFor(result, "feb-day").consumptionSincePreviousM3, 0.000001);
    }

    @Test
    public void statisticsCalculateCompleteMonthsCurrentMonthAndYear() throws Exception {
        List<WaterUsageAnalytics.Point> monthly = new ArrayList<>();
        monthly.add(month("jan", "2026-01-01 00:00", 100.0));
        monthly.add(month("feb", "2026-02-01 00:00", 110.0));
        monthly.add(month("mar", "2026-03-01 00:00", 125.0));
        monthly.add(month("apr", "2026-04-01 00:00", 130.0));
        monthly.add(month("may", "2026-05-01 00:00", 140.0));
        monthly.add(month("jun", "2026-06-01 00:00", 150.0));
        monthly.add(month("jul", "2026-07-01 00:00", 160.0));
        monthly.add(month("aug", "2026-08-01 00:00", 170.0));
        WaterUsageAnalytics.Point live = point("live", "2026-08-31 18:00",
                ms("2026-08-31 18:00"), HistorySemanticTimeline.Granularity.LIVE, 177.0);

        WaterUsageAnalytics.Statistics stats = WaterUsageAnalytics.statistics(
                monthly, live, ms("2026-08-31 18:00"));

        assertEquals(7.0, stats.currentMonthM3, 0.000001);
        assertEquals(77.0, stats.currentYearM3, 0.000001);
        assertEquals(8, stats.monthBuckets.size());
        assertEquals("2026-07", stats.monthBuckets.get(6).monthKey);
        assertEquals(10.0, stats.monthBuckets.get(6).consumptionM3, 0.000001);
        assertTrue(stats.monthBuckets.get(7).partial);
        assertEquals(7.0, stats.monthBuckets.get(7).consumptionM3, 0.000001);
    }

    @Test
    public void missingMonthDoesNotPretendMultiMonthGapIsOneMonthConsumption() throws Exception {
        List<WaterUsageAnalytics.Point> monthly = List.of(
                month("jan", "2026-01-01 00:00", 100.0),
                month("mar", "2026-03-01 00:00", 125.0));
        WaterUsageAnalytics.Statistics stats = WaterUsageAnalytics.statistics(
                monthly, null, ms("2026-03-10 12:00"));
        assertTrue(stats.monthBuckets.isEmpty());
    }

    private static WaterUsageAnalytics.HistoryDelta deltaFor(
            List<WaterUsageAnalytics.HistoryDelta> values, String identity) {
        for (WaterUsageAnalytics.HistoryDelta value : values) {
            if (identity.equals(value.point.identity)) return value;
        }
        return null;
    }

    private static WaterUsageAnalytics.Point month(String id, String timestamp, double total) throws Exception {
        return point(id, timestamp, ms(timestamp), HistorySemanticTimeline.Granularity.MONTH, total);
    }

    private static WaterUsageAnalytics.Point point(String id, String timestamp, long sortMs,
                                                   HistorySemanticTimeline.Granularity granularity,
                                                   double total) {
        return new WaterUsageAnalytics.Point(id, timestamp, sortMs, granularity, total);
    }

    private static WaterUsageAnalytics.Point point(String id, String meterId, String timestamp, long sortMs,
                                                   HistorySemanticTimeline.Granularity granularity,
                                                   double total) {
        return new WaterUsageAnalytics.Point(id, meterId, timestamp, sortMs, granularity, total);
    }

    private static long ms(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        return format.parse(value).getTime();
    }
}
