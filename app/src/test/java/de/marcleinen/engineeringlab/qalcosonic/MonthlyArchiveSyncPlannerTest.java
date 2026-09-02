package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class MonthlyArchiveSyncPlannerTest {
    private static final String FP = "fp";

    @Test public void fullFastAndGapPlansArePureAndBounded() {
        MonthlyArchiveSyncPlanner.Plan full = MonthlyArchiveSyncPlanner.fullSync(36);
        assertEquals(MonthlyArchiveSyncPlanner.Mode.FULL_SYNC, full.mode);
        assertEquals(36, full.hardCap);
        assertEquals(0, full.requiredKnownOverlap);

        MonthlyArchiveSyncPlanner.Plan fast = MonthlyArchiveSyncPlanner.fastSync(12, 2);
        assertEquals(MonthlyArchiveSyncPlanner.Mode.FAST_SYNC, fast.mode);
        assertEquals(2, fast.requiredKnownOverlap);

        MonthlyArchiveSyncPlanner.Plan gaps = MonthlyArchiveSyncPlanner.gapHealing(36, Arrays.asList(
                "2026-02-01 00:00", "2025-12-01 00:00"));
        assertEquals(Collections.singletonList("2026-01-01 00:00"), gaps.targetGaps);
    }

    @Test public void gapDetectionCrossesYearBoundaryAndIgnoresDuplicates() {
        List<String> gaps = MonthlyArchiveSyncPlanner.findGaps(Arrays.asList(
                "2026-02-01 00:00",
                "2026-02-01 00:00",
                "2025-11-01 00:00"));
        assertEquals(Arrays.asList("2026-01-01 00:00", "2025-12-01 00:00"), gaps);
    }

    @Test public void fastContinuityRequiresConfiguredKnownTailOverlap() {
        List<MonthlyArchivePeriod> enumerated = Arrays.asList(
                p("2026-08-01 00:00"),
                p("2026-07-01 00:00"),
                p("2026-06-01 00:00"),
                p("2026-05-01 00:00"));
        List<String> known = Arrays.asList("2026-06-01 00:00", "2026-05-01 00:00", "2026-04-01 00:00");

        assertTrue(MonthlyArchiveSyncPlanner.fastContinuityProven(enumerated, known, 2));
        assertFalse(MonthlyArchiveSyncPlanner.fastContinuityProven(enumerated, known, 3));
        assertFalse(MonthlyArchiveSyncPlanner.fastContinuityProven(enumerated,
                Collections.singletonList("2026-06-01 00:00"), 1));
    }

    @Test public void unseenPeriodsReturnOnlyNewLoggerTimestamps() {
        List<MonthlyArchivePeriod> enumerated = Arrays.asList(
                p("2026-08-01 00:00"),
                p("2026-07-01 00:00"),
                p("2026-06-01 00:00"));
        List<String> unseen = MonthlyArchiveSyncPlanner.unseenPeriods(
                enumerated, Arrays.asList("2026-06-01 00:00", "2026-05-01 00:00"));
        assertEquals(Arrays.asList("2026-08-01 00:00", "2026-07-01 00:00"), unseen);
    }

    private static MonthlyArchivePeriod p(String timestamp) {
        return new MonthlyArchivePeriod(timestamp, "2026-08-31T11:07:12Z", FP, null);
    }
}
