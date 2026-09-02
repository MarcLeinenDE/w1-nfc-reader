package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.Test;

public final class MonthlyArchiveTransportAdapterTest {
    private static final String FP = "2bce7a12cfee64cf8f255093af4e76b8d437b367f6cfbbc183e414bcad54e382";
    private static final String RETRIEVED = "2026-08-31T11:31:00Z";

    @Test public void terminalStopsWithoutExtraSelectedRequestAndRestoresOnce() {
        FakeWire wire = new FakeWire();
        QueueMapper mapper = new QueueMapper(
                period("2026-08-01 00:00"),
                period("2026-07-01 00:00"),
                MonthlyArchiveEnumerator.Candidate.w1E57d());
        QueueVerifier verifier = new QueueVerifier(healthyDefault(
                MonthlyArchiveTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED, mapper);

        assertEquals(3, result.selectedRequestsAttempted());
        assertEquals(Arrays.asList("7B", "5B", "7B"), result.selectedControls);
        assertEquals(MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D,
                result.enumeration.stopReason);
        assertEquals(2, result.enumeration.periods.size());
        assertEquals(1, result.restore.applicationResetCommands);
        assertEquals(1, result.restore.defaultVerifyAttempts);
        assertTrue(result.restore.defaultVerified);
        assertEquals("DEFAULT_VERIFIED", result.restore.status);
        assertArrayEquals(MbusFrameSupport.reqUd2Fcb1(), wire.selectedFrames.get(0));
        assertArrayEquals(MbusFrameSupport.reqUd2Fcb0(), wire.selectedFrames.get(1));
        assertArrayEquals(MbusFrameSupport.reqUd2Fcb1(), wire.selectedFrames.get(2));
    }

    @Test public void selectedIoFailureRetainsProgressAndNeverRetriesAmbiguousRequest() {
        FakeWire wire = new FakeWire();
        wire.throwOnSelectedIndex = 2;
        QueueMapper mapper = new QueueMapper(period("2026-08-01 00:00"));
        QueueVerifier verifier = new QueueVerifier(healthyDefault(
                MonthlyArchiveTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED, mapper);

        assertEquals(2, result.selectedRequestsAttempted());
        assertEquals(Arrays.asList("7B", "5B"), result.selectedControls);
        assertEquals(1, result.enumeration.periods.size());
        assertEquals(MonthlyArchiveEnumerator.StopReason.IO_ERROR, result.enumeration.stopReason);
        assertEquals(2, wire.selectedFrames.size());
        assertTrue(result.restore.defaultVerified);
    }

    @Test public void hardCapCannotEmitOneExtraSelectedRequest() {
        FakeWire wire = new FakeWire();
        QueueMapper mapper = new QueueMapper(
                period("2026-08-01 00:00"), period("2026-07-01 00:00"));
        QueueVerifier verifier = new QueueVerifier(healthyDefault(
                MonthlyArchiveTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                2, wire, verifier, RETRIEVED, mapper);

        assertEquals(2, result.selectedRequestsAttempted());
        assertEquals(2, wire.selectedFrames.size());
        assertEquals(MonthlyArchiveEnumerator.StopReason.HARD_CAP_REACHED,
                result.enumeration.stopReason);
        assertEquals(2, result.enumeration.periods.size());
    }

    @Test public void failedHealthyDefaultVerifyAllowsExactlyOneResetRetry() {
        FakeWire wire = new FakeWire();
        QueueMapper mapper = new QueueMapper(MonthlyArchiveEnumerator.Candidate.w1E57d());
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault("wrong-fingerprint"),
                healthyDefault(MonthlyArchiveTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED, mapper);

        assertEquals(2, result.restore.applicationResetCommands);
        assertEquals(2, result.restore.defaultVerifyAttempts);
        assertTrue(result.restore.defaultVerified);
        assertEquals("DEFAULT_VERIFIED", result.restore.status);
    }

    @Test public void defaultVerifyIoFailureDoesNotTriggerBlindResetRetry() {
        FakeWire wire = new FakeWire();
        QueueMapper mapper = new QueueMapper(MonthlyArchiveEnumerator.Candidate.w1E57d());
        QueueVerifier verifier = new QueueVerifier(new IOException("Tag was lost."));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED, mapper);

        assertEquals(1, result.restore.applicationResetCommands);
        assertEquals(1, result.restore.defaultVerifyAttempts);
        assertFalse(result.restore.defaultVerified);
        assertEquals("RESTORE_UNVERIFIED_TRANSPORT_LOST", result.restore.status);
    }

    @Test public void exactShortTerminalSignaturesRemainProfileBound() {
        assertEquals(MonthlyArchiveEnumerator.CandidateKind.STANDARD_E5_NO_DATA,
                MonthlyArchiveTransportAdapter.mapResponse(new byte[]{(byte) 0xE5}, RETRIEVED).kind);
        assertEquals(MonthlyArchiveEnumerator.CandidateKind.W1_E5_7D_TERMINAL,
                MonthlyArchiveTransportAdapter.mapResponse(
                        new byte[]{(byte) 0xE5, 0x7D}, RETRIEVED).kind);
        assertEquals(MonthlyArchiveEnumerator.CandidateKind.OTHER_RESPONSE,
                MonthlyArchiveTransportAdapter.mapResponse(
                        new byte[]{(byte) 0xE5, 0x7C}, RETRIEVED).kind);
    }

    @Test public void productionMbusFramesMatchPhysicallyValidatedMonthlyFrames() {
        assertArrayEquals(new byte[]{0x10, 0x40, (byte) 0xFE, 0x3E, 0x16},
                MbusFrameSupport.reset());
        assertArrayEquals(new byte[]{0x10, 0x7B, (byte) 0xFE, 0x79, 0x16},
                MbusFrameSupport.reqUd2Fcb1());
        assertArrayEquals(new byte[]{0x10, 0x5B, (byte) 0xFE, 0x59, 0x16},
                MbusFrameSupport.reqUd2Fcb0());
        assertArrayEquals(
                new byte[]{0x68, 0x04, 0x04, 0x68, 0x73, (byte) 0xFE, 0x50, 0x40,
                        0x01, 0x16},
                MbusFrameSupport.monthlyApplicationSelect());
        assertArrayEquals(
                new byte[]{0x68, 0x03, 0x03, 0x68, 0x73, (byte) 0xFE, 0x50, (byte) 0xC1, 0x16},
                MbusFrameSupport.applicationResetDefault());
    }

    @Test public void preflightResetFailureDoesNotAttemptMonthlySelectOrRestore() {
        FakeWire wire = new FakeWire();
        wire.returnNullForPrepareReset = true;
        QueueVerifier verifier = new QueueVerifier();

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED, new QueueMapper());

        assertEquals("PREPARE_RESET_NO_HOST_RESPONSE", result.sessionStopReason);
        assertEquals(0, result.selectedRequestsAttempted());
        assertEquals(0, result.restore.applicationResetCommands);
        assertFalse(result.monthlySelectAttempted);
    }

    private static MonthlyArchiveEnumerator.Candidate period(String timestamp) {
        return MonthlyArchiveEnumerator.Candidate.period(
                new MonthlyArchivePeriod(timestamp, RETRIEVED, FP, null));
    }

    private static DefaultReadObservation healthyDefault(String fingerprint) {
        return new DefaultReadObservation(true, true, fingerprint);
    }

    private static final class QueueMapper implements MonthlyArchiveTransportAdapter.ResponseMapper {
        final Deque<MonthlyArchiveEnumerator.Candidate> queue = new ArrayDeque<>();
        QueueMapper(MonthlyArchiveEnumerator.Candidate... values) {
            queue.addAll(Arrays.asList(values));
        }
        @Override public MonthlyArchiveEnumerator.Candidate map(byte[] response, String retrievedAtUtc) {
            if (queue.isEmpty()) throw new AssertionError("unexpected selected response");
            return queue.removeFirst();
        }
    }

    private static final class QueueVerifier implements MonthlyArchiveTransportAdapter.DefaultVerifier {
        final Deque<Object> queue = new ArrayDeque<>();
        QueueVerifier(Object... values) { queue.addAll(Arrays.asList(values)); }
        @Override public DefaultReadObservation readDefault() throws IOException {
            if (queue.isEmpty()) throw new AssertionError("unexpected default verify");
            Object value = queue.removeFirst();
            if (value instanceof IOException) throw (IOException) value;
            return (DefaultReadObservation) value;
        }
    }

    private static final class FakeWire implements MonthlyArchiveTransportAdapter.Wire {
        final List<byte[]> selectedFrames = new ArrayList<>();
        int selectedIndex;
        int throwOnSelectedIndex = -1;
        boolean returnNullForPrepareReset;
        boolean healthy = true;

        @Override public void prepare() {}

        @Override public byte[] exchange(String label, byte[] frame, long timeoutMs) throws IOException {
            if ("MONTHLY_SYNC_MBUS_RESET".equals(label) && returnNullForPrepareReset) return null;
            if (label.startsWith("MONTHLY_SYNC_SELECTED_")) {
                selectedIndex++;
                selectedFrames.add(frame.clone());
                if (selectedIndex == throwOnSelectedIndex) {
                    healthy = false;
                    throw new IOException("Tag was lost.");
                }
                healthy = true;
                return new byte[]{0x01};
            }
            healthy = true;
            return new byte[]{(byte) 0xE5};
        }

        @Override public void coolDown(long millis) {}
        @Override public boolean transportHealthy() { return healthy; }
    }
}
