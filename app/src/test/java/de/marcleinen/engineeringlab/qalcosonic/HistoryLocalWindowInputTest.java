package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class HistoryLocalWindowInputTest {
    @Test public void boundedNavigatorUsesFloatingCalendarFieldsNotDeviceEpochs() {
        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 10:00",
                "2026-09-09 13:00",
                1L,
                2L,
                0);

        HistoryLocalWindowInput input = HistoryLocalWindowInput.from(window);

        assertEquals(LocalDateTime.of(2026, 9, 9, 10, 0), input.start);
        assertEquals(LocalDateTime.of(2026, 9, 9, 13, 0), input.end);
    }

    @Test public void allPeriodsHasNoBoundedLocalWindow() {
        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.YEAR,
                true,
                false,
                2026,
                0,
                1,
                null,
                null,
                0L,
                Long.MAX_VALUE,
                0);

        assertNull(HistoryLocalWindowInput.from(window));
    }

    @Test public void invalidFloatingRangeFailsClosed() {
        HistoryPeriodNavigator.Window window = new HistoryPeriodNavigator.Window(
                HistoryPeriodNavigator.Scale.DAY,
                false,
                true,
                2026,
                8,
                9,
                "2026-09-09 13:00",
                "2026-09-09 10:00",
                1L,
                2L,
                0);

        assertNull(HistoryLocalWindowInput.from(window));
    }
}
