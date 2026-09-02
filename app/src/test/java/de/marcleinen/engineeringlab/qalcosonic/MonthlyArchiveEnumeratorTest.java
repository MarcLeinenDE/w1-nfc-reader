package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class MonthlyArchiveEnumeratorTest {
    private static final String FP = "2bce7a12cfee64cf8f255093af4e76b8d437b367f6cfbbc183e414bcad54e382";

    @Test public void hardCapIsBoundedByVendorCandidate() {
        assertEquals(36, MonthlyArchiveEnumerator.VENDOR_HARD_CAP_CANDIDATE);
        new MonthlyArchiveEnumerator.Session(1);
        new MonthlyArchiveEnumerator.Session(36);
        assertThrowsHardCap(0);
        assertThrowsHardCap(37);
    }

    @Test public void controlsAlternateExactlyAndNoControlAfterTerminal() {
        MonthlyArchiveEnumerator.Session s = new MonthlyArchiveEnumerator.Session(36);
        assertEquals("7B", s.nextControl());
        s.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-08-01 00:00", FP)));
        assertEquals("5B", s.nextControl());
        s.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-07-01 00:00", FP)));
        assertEquals("7B", s.nextControl());
        s.accept(MonthlyArchiveEnumerator.Candidate.w1E57d());
        assertEquals(MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D, s.stopReason());
        assertNull(s.nextControl());
        assertEquals(3, s.selectedRequestsAttempted());
        assertEquals(2, s.acceptedPeriods().size());
    }

    @Test public void exactShortTerminalsAreDistinguishedFromUnknownResponses() {
        MonthlyArchiveEnumerator.Candidate standard = MonthlyArchiveEnumerator.classifyShortResponse(
                new byte[]{(byte)0xE5});
        MonthlyArchiveEnumerator.Candidate w1 = MonthlyArchiveEnumerator.classifyShortResponse(
                new byte[]{(byte)0xE5, 0x7D});
        MonthlyArchiveEnumerator.Candidate unknown = MonthlyArchiveEnumerator.classifyShortResponse(
                new byte[]{(byte)0xE5, 0x7C});

        assertEquals(MonthlyArchiveEnumerator.CandidateKind.STANDARD_E5_NO_DATA, standard.kind);
        assertEquals(MonthlyArchiveEnumerator.CandidateKind.W1_E5_7D_TERMINAL, w1.kind);
        assertEquals(MonthlyArchiveEnumerator.CandidateKind.OTHER_RESPONSE, unknown.kind);
        assertEquals("E5 7C", unknown.diagnostic);
    }

    @Test public void exactPreviousMonthHandlesYearBoundaryAndRejectsSkipOrDuplicate() {
        assertTrue(MonthlyArchiveEnumerator.isExactlyPreviousCalendarMonth(
                "2026-01-01 00:00", "2025-12-01 00:00"));
        assertTrue(MonthlyArchiveEnumerator.isExactlyPreviousCalendarMonth(
                "2025-01-01 00:00", "2024-12-01 00:00"));
        assertFalse(MonthlyArchiveEnumerator.isExactlyPreviousCalendarMonth(
                "2026-08-01 00:00", "2026-06-01 00:00"));
        assertFalse(MonthlyArchiveEnumerator.isExactlyPreviousCalendarMonth(
                "2026-08-01 00:00", "2026-08-01 00:00"));
    }

    @Test public void duplicateSkipAndStructureChangeStopWithoutDiscardingEarlierPeriods() {
        MonthlyArchiveEnumerator.Session duplicate = seededTwo();
        duplicate.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-07-01 00:00", FP)));
        assertEquals(MonthlyArchiveEnumerator.StopReason.DUPLICATE_PERIOD, duplicate.stopReason());
        assertEquals(2, duplicate.acceptedPeriods().size());

        MonthlyArchiveEnumerator.Session skip = seededTwo();
        skip.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-05-01 00:00", FP)));
        assertEquals(MonthlyArchiveEnumerator.StopReason.NON_MONTHLY_PROGRESSION, skip.stopReason());
        assertEquals(2, skip.acceptedPeriods().size());

        MonthlyArchiveEnumerator.Session structure = seededTwo();
        structure.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-06-01 00:00", "different")));
        assertEquals(MonthlyArchiveEnumerator.StopReason.STRUCTURE_CHANGED, structure.stopReason());
        assertEquals(2, structure.acceptedPeriods().size());
    }

    @Test public void partialSuccessRetainsAcceptedPeriodsOnIoNoHostAndOtherFailures() {
        MonthlyArchiveEnumerator.Session io = seededTwo();
        io.accept(MonthlyArchiveEnumerator.Candidate.ioError("Tag was lost."));
        MonthlyArchiveEnumerator.Result ioResult = io.result();
        assertEquals(2, ioResult.periods.size());
        assertTrue(ioResult.partialSuccess());
        assertEquals(MonthlyArchiveEnumerator.StopReason.IO_ERROR, ioResult.stopReason);

        MonthlyArchiveEnumerator.Session noHost = seededTwo();
        noHost.accept(MonthlyArchiveEnumerator.Candidate.noHost());
        assertEquals(2, noHost.result().periods.size());
        assertTrue(noHost.result().partialSuccess());

        MonthlyArchiveEnumerator.Session other = seededTwo();
        other.accept(MonthlyArchiveEnumerator.Candidate.other("AA BB"));
        assertEquals(2, other.result().periods.size());
        assertTrue(other.result().partialSuccess());
    }

    @Test public void terminalIsCompleteButHardCapNeverSendsAnExtraRequest() {
        MonthlyArchiveEnumerator.Session terminal = seededTwo();
        terminal.accept(MonthlyArchiveEnumerator.Candidate.w1E57d());
        assertTrue(terminal.result().terminalConfirmed());
        assertFalse(terminal.result().partialSuccess());

        MonthlyArchiveEnumerator.Session cap = new MonthlyArchiveEnumerator.Session(2);
        assertEquals("7B", cap.nextControl());
        cap.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-08-01 00:00", FP)));
        assertEquals("5B", cap.nextControl());
        cap.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-07-01 00:00", FP)));
        assertEquals(MonthlyArchiveEnumerator.StopReason.HARD_CAP_REACHED, cap.stopReason());
        assertNull(cap.nextControl());
        assertEquals(2, cap.selectedRequestsAttempted());
    }

    private static MonthlyArchiveEnumerator.Session seededTwo() {
        MonthlyArchiveEnumerator.Session s = new MonthlyArchiveEnumerator.Session(36);
        s.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-08-01 00:00", FP)));
        s.accept(MonthlyArchiveEnumerator.Candidate.period(period("2026-07-01 00:00", FP)));
        return s;
    }

    private static MonthlyArchivePeriod period(String timestamp, String fingerprint) {
        return new MonthlyArchivePeriod(timestamp, "2026-08-31T11:07:12Z", fingerprint, null);
    }

    private static void assertThrowsHardCap(int cap) {
        try {
            new MonthlyArchiveEnumerator.Session(cap);
            fail("Expected IllegalArgumentException for cap=" + cap);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
