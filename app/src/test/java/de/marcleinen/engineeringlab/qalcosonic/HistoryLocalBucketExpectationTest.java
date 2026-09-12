package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class HistoryLocalBucketExpectationTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test public void normalDayHasTwentyFourRealHours() {
        assertEquals(Integer.valueOf(24), HistoryLocalBucketExpectation.fullHours(
                LocalDateTime.of(2026, 9, 9, 0, 0),
                LocalDateTime.of(2026, 9, 10, 0, 0),
                BERLIN));
    }

    @Test public void springForwardDayHasTwentyThreeRealHours() {
        assertEquals(Integer.valueOf(23), HistoryLocalBucketExpectation.fullHours(
                LocalDateTime.of(2026, 3, 29, 0, 0),
                LocalDateTime.of(2026, 3, 30, 0, 0),
                BERLIN));
    }

    @Test public void fallBackDayHasTwentyFiveRealHours() {
        assertEquals(Integer.valueOf(25), HistoryLocalBucketExpectation.fullHours(
                LocalDateTime.of(2026, 10, 25, 0, 0),
                LocalDateTime.of(2026, 10, 26, 0, 0),
                BERLIN));
    }

    @Test public void partialEdgesCountOnlyCompleteRealHours() {
        assertEquals(Integer.valueOf(2), HistoryLocalBucketExpectation.fullHours(
                LocalDateTime.of(2026, 9, 9, 0, 15),
                LocalDateTime.of(2026, 9, 9, 3, 45),
                BERLIN));
    }

    @Test public void ambiguousBoundaryFailsClosedWithoutExplicitOffset() {
        assertNull(HistoryLocalBucketExpectation.fullHours(
                LocalDateTime.of(2026, 10, 25, 2, 30),
                LocalDateTime.of(2026, 10, 25, 4, 0),
                BERLIN));
    }
}
