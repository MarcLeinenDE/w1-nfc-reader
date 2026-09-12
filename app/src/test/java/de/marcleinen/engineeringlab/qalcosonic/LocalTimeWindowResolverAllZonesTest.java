package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exhaustive tzdb guard for LOCAL navigator input. */
public final class LocalTimeWindowResolverAllZonesTest {
    @Test public void everyAvailableZoneEitherRoundTripsExactlyOrFailsClosedAtCivilBoundaries() {
        Set<String> zoneIds = ZoneId.getAvailableZoneIds();
        assertTrue("expected a real IANA/tzdb zone set", zoneIds.size() > 100);

        LocalDateTime[][] windows = {
                {LocalDateTime.of(2026, 1, 15, 0, 0), LocalDateTime.of(2026, 1, 16, 0, 0)},
                {LocalDateTime.of(2026, 3, 29, 0, 0), LocalDateTime.of(2026, 3, 30, 0, 0)},
                {LocalDateTime.of(2026, 10, 25, 0, 0), LocalDateTime.of(2026, 10, 26, 0, 0)},
                {LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0)},
                {LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0)}
        };

        for (String zoneId : zoneIds) {
            ZoneId zone = ZoneId.of(zoneId);
            for (LocalDateTime[] requested : windows) {
                LocalTimeWindowResolver.Window result = LocalTimeWindowResolver.resolveWindow(
                        requested[0], requested[1], zone, null, null);
                if (result.resolved()) {
                    assertEquals(zoneId, requested[0],
                            Instant.ofEpochMilli(result.startUtcMs).atZone(zone).toLocalDateTime());
                    assertEquals(zoneId, requested[1],
                            Instant.ofEpochMilli(result.endUtcMs).atZone(zone).toLocalDateTime());
                    assertTrue(zoneId, result.endUtcMs > result.startUtcMs);
                    assertTrue(zoneId, zone.getRules().getValidOffsets(requested[0])
                            .contains(result.startOffset));
                    assertTrue(zoneId, zone.getRules().getValidOffsets(requested[1])
                            .contains(result.endOffset));
                } else {
                    assertNull(zoneId, result.startUtcMs);
                    assertNull(zoneId, result.endUtcMs);
                    assertTrue(zoneId,
                            result.status == LocalTimeWindowResolver.Status.AMBIGUOUS_LOCAL_TIME
                                    || result.status == LocalTimeWindowResolver.Status.NONEXISTENT_LOCAL_TIME);
                }
            }
        }
    }

    @Test public void everyAvailableZoneRoundTripsResolvedBoundaryAndNeverInventsGapOrFoldOffset() {
        LocalDateTime[] probes = {
                LocalDateTime.of(2026, 3, 29, 0, 0),
                LocalDateTime.of(2026, 3, 29, 2, 30),
                LocalDateTime.of(2026, 10, 25, 0, 0),
                LocalDateTime.of(2026, 10, 25, 2, 30),
                LocalDateTime.of(2026, 12, 31, 23, 59)
        };

        for (String zoneId : ZoneId.getAvailableZoneIds()) {
            ZoneId zone = ZoneId.of(zoneId);
            for (LocalDateTime local : probes) {
                LocalTimeWindowResolver.Boundary result =
                        LocalTimeWindowResolver.resolveBoundary(local, zone, null);
                int validOffsetCount = zone.getRules().getValidOffsets(local).size();
                if (validOffsetCount == 1) {
                    assertTrue(zoneId + " " + local, result.resolved());
                    assertEquals(zoneId, local,
                            Instant.ofEpochMilli(result.epochMs).atZone(zone).toLocalDateTime());
                    assertEquals(zoneId, zone.getRules().getValidOffsets(local).get(0), result.offset);
                } else {
                    assertFalse(zoneId + " " + local, result.resolved());
                    assertNull(zoneId, result.epochMs);
                    assertNull(zoneId, result.offset);
                    assertEquals(zoneId,
                            validOffsetCount == 0
                                    ? LocalTimeWindowResolver.Status.NONEXISTENT_LOCAL_TIME
                                    : LocalTimeWindowResolver.Status.AMBIGUOUS_LOCAL_TIME,
                            result.status);

                    if (validOffsetCount > 1) {
                        for (ZoneOffset explicit : zone.getRules().getValidOffsets(local)) {
                            LocalTimeWindowResolver.Boundary explicitResult =
                                    LocalTimeWindowResolver.resolveBoundary(local, zone, explicit);
                            assertTrue(zoneId + " " + local + " " + explicit,
                                    explicitResult.resolved());
                            assertEquals(zoneId, explicit, explicitResult.offset);
                            assertEquals(zoneId, local,
                                    Instant.ofEpochMilli(explicitResult.epochMs)
                                            .atZone(zone).toLocalDateTime());
                        }
                    }
                }
            }
        }
    }
}
