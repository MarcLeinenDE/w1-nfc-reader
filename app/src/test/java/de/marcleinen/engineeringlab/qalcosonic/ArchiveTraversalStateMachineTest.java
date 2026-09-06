package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ArchiveTraversalStateMachineTest {
    private static final String FP = "synthetic-archive-shape-v2";

    @Test public void selectedControlsAlternateWithoutAnyRecordCountCap() {
        ArchiveTraversalStateMachine.Session session = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        String timestamp = "2026-09-06 10:00";
        long onTime = 1_000_000L;

        for (int i = 0; i < 50; i++) {
            assertEquals(i % 2 == 0 ? "7B" : "5B", session.nextControl());
            session.accept(period(ArchiveFamilyPeriod.Family.HOUR, timestamp, onTime));
            assertFalse(session.stopped());
            timestamp = shiftHours(timestamp, -1);
            onTime -= 3600L;
        }

        assertEquals(50, session.acceptedPeriods().size());
        assertEquals(50, session.selectedRequestsAttempted());
        assertEquals("7B", session.nextControl());
        assertEquals(ArchiveFamilySyncState.StopReason.NONE, session.stopReason());
    }

    @Test public void protocolTerminalIsSemanticSuccessAndEmitsNoExtraControl() {
        ArchiveTraversalStateMachine.Session session = fullSession(ArchiveFamilyPeriod.Family.MONTH);
        session.accept(period(ArchiveFamilyPeriod.Family.MONTH, "2026-09-01 00:00", 20_000_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.MONTH, "2026-08-01 00:00",
                20_000_000L - 31L * 86400L));
        session.accept(ArchiveTraversalStateMachine.Candidate.terminal("E5 7D"));

        ArchiveTraversalStateMachine.Result result = session.result();
        assertEquals(ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL, result.stopReason);
        assertTrue(result.semanticSuccess());
        assertFalse(result.partialSuccess());
        assertNull(session.nextControl());
        assertEquals(3, result.selectedRequestsAttempted);
        assertEquals(2, result.periods.size());
    }

    @Test public void plusOneNewHeadIsProtectiveHeadAdvanceNotRing() {
        ArchiveTraversalStateMachine.Session session = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 09:00", 96_400L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 11:00", 103_600L));

        assertEquals(ArchiveFamilySyncState.StopReason.HEAD_ADVANCED_DURING_TRAVERSAL,
                session.stopReason());
        assertFalse(session.result().semanticSuccess());
        assertTrue(session.result().partialSuccess());
        assertEquals(2, session.acceptedPeriods().size());
    }

    @Test public void ringNeedsExactInitialHeadAndOneContinuationProof() {
        ArchiveTraversalStateMachine.Session session = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 09:00", 96_400L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 08:00", 92_800L));

        // Returning to the exact session head is only a candidate. It does not stop the session.
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        assertFalse(session.stopped());
        assertEquals("7B", session.nextControl());

        // One more request must resume normal backward progression from that returned head.
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 09:00", 96_400L));
        assertEquals(ArchiveFamilySyncState.StopReason.RING_WRAP_DETECTED, session.stopReason());
        assertTrue(session.result().semanticSuccess());
        assertEquals(3, session.acceptedPeriods().size());
    }

    @Test public void inconclusiveRingCandidateContinuationFailsProtectively() {
        ArchiveTraversalStateMachine.Session session = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 09:00", 96_400L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 08:00", 92_800L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 07:00", 89_200L));

        assertEquals(ArchiveFamilySyncState.StopReason.PROGRESSION_INCONSISTENT,
                session.stopReason());
        assertFalse(session.result().semanticSuccess());
    }

    @Test public void incrementalRequiresTwoSecureKnownRecordsAndNeverTimestampOnlyByItself() {
        Set<String> securelyKnown = new HashSet<>();
        securelyKnown.add("2026-09-06 08:00");
        securelyKnown.add("2026-09-06 07:00");
        ArchiveTraversalStateMachine.KnownRecordMatcher matcher =
                evidence -> securelyKnown.contains(evidence.rawTimestamp());
        ArchiveTraversalStateMachine.Session session = new ArchiveTraversalStateMachine.Session(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR),
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                matcher,
                2);

        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 09:00", 96_400L));
        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 08:00", 92_800L));
        assertFalse(session.stopped());
        assertEquals(1, session.consecutiveKnownOverlap());

        session.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 07:00", 89_200L));
        assertEquals(ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED, session.stopReason());
        assertEquals(2, session.consecutiveKnownOverlap());
        assertTrue(session.result().semanticSuccess());
    }

    @Test public void incrementalOverlapOfOneIsRejectedByContract() {
        assertThrows(IllegalArgumentException.class, () -> new ArchiveTraversalStateMachine.Session(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR),
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                evidence -> true,
                1));
    }

    @Test public void structureTypeFAndOnTimeIntegrityFailuresAreExplicit() {
        ArchiveTraversalStateMachine.Session structure = fullSession(ArchiveFamilyPeriod.Family.DAY);
        structure.accept(period(ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", 100_000L));
        structure.accept(periodWith(
                ArchiveFamilyPeriod.Family.DAY, "2026-09-05 00:00", 13_600L,
                true, false, "different-shape"));
        assertEquals(ArchiveFamilySyncState.StopReason.STRUCTURE_ERROR, structure.stopReason());

        ArchiveTraversalStateMachine.Session missingTypeF = fullSession(ArchiveFamilyPeriod.Family.DAY);
        missingTypeF.accept(periodWith(
                ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", 100_000L,
                false, false, FP));
        assertEquals(ArchiveFamilySyncState.StopReason.TYPE_F_MISSING, missingTypeF.stopReason());

        ArchiveTraversalStateMachine.Session invalidTypeF = fullSession(ArchiveFamilyPeriod.Family.DAY);
        invalidTypeF.accept(periodWith(
                ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", 100_000L,
                true, true, FP));
        assertEquals(ArchiveFamilySyncState.StopReason.TYPE_F_INVALID, invalidTypeF.stopReason());

        ArchiveTraversalStateMachine.Session invalidOnTime = fullSession(ArchiveFamilyPeriod.Family.DAY);
        invalidOnTime.accept(periodWith(
                ArchiveFamilyPeriod.Family.DAY, "2026-09-06 00:00", -1L,
                true, false, FP));
        assertEquals(ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT,
                invalidOnTime.stopReason());
    }

    @Test public void transportAndBoundaryFailuresRetainEarlierAcceptedPeriods() {
        ArchiveTraversalStateMachine.Session lost = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        lost.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        lost.accept(ArchiveTraversalStateMachine.Candidate.failure(
                ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST,
                "Tag was lost"));
        assertEquals(1, lost.acceptedPeriods().size());
        assertEquals(ArchiveFamilySyncState.StopReason.TRANSPORT_SESSION_LOST, lost.stopReason());
        assertTrue(lost.result().partialSuccess());

        ArchiveTraversalStateMachine.Session boundary = fullSession(ArchiveFamilyPeriod.Family.HOUR);
        boundary.accept(period(ArchiveFamilyPeriod.Family.HOUR, "2026-09-06 10:00", 100_000L));
        boundary.accept(ArchiveTraversalStateMachine.Candidate.failure(
                ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                "deadline reached"));
        assertEquals(1, boundary.acceptedPeriods().size());
        assertEquals(ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED, boundary.stopReason());
    }

    private static ArchiveTraversalStateMachine.Session fullSession(ArchiveFamilyPeriod.Family family) {
        return new ArchiveTraversalStateMachine.Session(
                ArchiveFamilyPolicy.forFamily(family),
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0);
    }

    private static ArchiveTraversalStateMachine.Candidate period(
            ArchiveFamilyPeriod.Family family, String timestamp, long onTime) {
        return periodWith(family, timestamp, onTime, true, false, FP);
    }

    private static ArchiveTraversalStateMachine.Candidate periodWith(
            ArchiveFamilyPeriod.Family family,
            String timestamp,
            long onTime,
            boolean typeFPresent,
            boolean typeFInvalid,
            String fingerprint) {
        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                family,
                timestamp,
                "2026-09-06T08:00:00Z",
                fingerprint,
                "SYNTHETIC_TEST",
                "VALIDATED_SYNTHETIC",
                ArchiveNormalizedValues.builder().build());
        return ArchiveTraversalStateMachine.Candidate.period(
                new ArchiveTraversalStateMachine.PeriodEvidence(
                        period, onTime, typeFPresent, typeFInvalid));
    }

    private static String shiftHours(String timestamp, int hours) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
            calendar.setTime(format.parse(timestamp));
            calendar.add(Calendar.HOUR_OF_DAY, hours);
            return format.format(calendar.getTime());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
