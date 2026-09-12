package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LocalTimeWindowResolverTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test public void springForwardDayHasTwentyThreeRealHours() {
        LocalTimeWindowResolver.Window result = LocalTimeWindowResolver.resolveWindow(
                LocalDateTime.of(2026, 3, 29, 0, 0),
                LocalDateTime.of(2026, 3, 30, 0, 0),
                BERLIN,
                null,
                null);

        assertTrue(result.resolved());
        assertEquals(23L * 60L * 60L * 1000L,
                result.endUtcMs - result.startUtcMs);
    }

    @Test public void fallBackDayHasTwentyFiveRealHours() {
        LocalTimeWindowResolver.Window result = LocalTimeWindowResolver.resolveWindow(
                LocalDateTime.of(2026, 10, 25, 0, 0),
                LocalDateTime.of(2026, 10, 26, 0, 0),
                BERLIN,
                null,
                null);

        assertTrue(result.resolved());
        assertEquals(25L * 60L * 60L * 1000L,
                result.endUtcMs - result.startUtcMs);
    }

    @Test public void nonexistentSpringLocalTimeIsNotSilentlyShifted() {
        LocalTimeWindowResolver.Boundary result = LocalTimeWindowResolver.resolveBoundary(
                LocalDateTime.of(2026, 3, 29, 2, 30), BERLIN, null);

        assertEquals(LocalTimeWindowResolver.Status.NONEXISTENT_LOCAL_TIME, result.status);
    }

    @Test public void repeatedAutumnHourRequiresExplicitOffset() {
        LocalDateTime repeated = LocalDateTime.of(2026, 10, 25, 2, 30);
        LocalTimeWindowResolver.Boundary unresolved =
                LocalTimeWindowResolver.resolveBoundary(repeated, BERLIN, null);
        LocalTimeWindowResolver.Boundary summer =
                LocalTimeWindowResolver.resolveBoundary(repeated, BERLIN, ZoneOffset.ofHours(2));
        LocalTimeWindowResolver.Boundary winter =
                LocalTimeWindowResolver.resolveBoundary(repeated, BERLIN, ZoneOffset.ofHours(1));

        assertEquals(LocalTimeWindowResolver.Status.AMBIGUOUS_LOCAL_TIME, unresolved.status);
        assertTrue(summer.resolved());
        assertTrue(winter.resolved());
        assertEquals(60L * 60L * 1000L, winter.epochMs - summer.epochMs);
    }
}
