package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HistoryPeriodNavigatorCustomRangeTest {
    @Test public void customRangePreservesExactLocalWallClockAndDisablesArrowMove() {
        Calendar start = local(2026, Calendar.SEPTEMBER, 6, 10, 15);
        Calendar end = local(2026, Calendar.SEPTEMBER, 7, 12, 45);
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setCustomRange(start.getTimeInMillis(), end.getTimeInMillis());

        HistoryPeriodNavigator.Window before = navigator.window();
        navigator.move(1);
        HistoryPeriodNavigator.Window after = navigator.window();

        assertTrue(before.customRange);
        assertFalse(before.allPeriods);
        assertEquals("2026-09-06 10:15", before.archiveStart);
        assertEquals("2026-09-07 12:45", before.archiveEnd);
        assertEquals(before.deviceStartMs, after.deviceStartMs);
        assertEquals(before.deviceEndMs, after.deviceEndMs);
        assertTrue(navigator.label(Locale.GERMANY, "Alle").contains("–"));
    }

    @Test public void explicitCalendarScaleSelectionLeavesCustomRangeMode() {
        Calendar start = local(2026, Calendar.SEPTEMBER, 6, 10, 15);
        Calendar end = local(2026, Calendar.SEPTEMBER, 7, 12, 45);
        HistoryPeriodNavigator navigator = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.DAY);
        navigator.setCustomRange(start.getTimeInMillis(), end.getTimeInMillis());

        navigator.selectScale(HistoryPeriodNavigator.Scale.MONTH);

        assertFalse(navigator.customRange());
        assertFalse(navigator.allPeriods());
        assertEquals(HistoryPeriodNavigator.Scale.MONTH, navigator.scale());
    }

    private static Calendar local(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance();
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
