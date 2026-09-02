package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class UnifiedHistoryModelTest {
    @Test public void liveAndMonthAtSameTimestampRemainSeparateRows() {
        UnifiedHistoryModel.Row live = new UnifiedHistoryModel.Row("live-1", "2026-08-01 00:00",
                HistorySemanticTimeline.Granularity.LIVE, HistorySemanticTimeline.Provenance.LIVE_NFC,
                "10.000 m3", null, null);
        UnifiedHistoryModel.Row month = new UnifiedHistoryModel.Row("month-1", "2026-08-01 00:00",
                HistorySemanticTimeline.Granularity.MONTH, HistorySemanticTimeline.Provenance.METER_ARCHIVE,
                "9.000 m3", null, null);
        List<UnifiedHistoryModel.Row> rows = UnifiedHistoryModel.newestFirst(Arrays.asList(live, month));
        assertEquals(2, rows.size());
        assertEquals("live-1", rows.get(0).identity);
        assertEquals("month-1", rows.get(1).identity);
    }
}
