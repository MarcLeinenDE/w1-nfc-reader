package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.Test;

public final class ArchiveFamilyTransportAdapterTest {
    private static final String METER = "METER-1";
    private static final String RETRIEVED = "2026-09-06T08:45:00Z";
    private static final String FP = "month-fingerprint";

    @Test public void fullMonthTerminalCompletesOnlyAfterFinalVerifiedLive() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault(METER, "2026-09-06 10:00", 20_000_000L),
                healthyDefault(METER, "2026-09-06 10:01", 20_000_060L));
        QueueMapper mapper = new QueueMapper(
                period("2026-09-01 00:00", 19_500_000L),
                period("2026-08-01 00:00", 16_821_600L),
                ArchiveTraversalStateMachine.Candidate.terminal("E5 7D"));

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper);

        assertEquals(Arrays.asList("7B", "5B", "7B"), result.selectedControls);
        assertEquals(3, result.selectedRequestsAttempted());
        assertEquals(2, result.traversal.periods.size());
        assertEquals(ArchiveFamilySyncState.StopReason.PROTOCOL_TERMINAL,
                result.traversal.stopReason);
        assertTrue(result.preflight.verified);
        assertTrue(result.finalRestoreVerified());
        assertTrue(result.completeSafetyShell());
        assertEquals(1, result.preflight.applicationResetCommands);
        assertEquals(1, result.preflight.liveReadAttempts);
        assertEquals(1, result.finalVerification.applicationResetCommands);
        assertEquals(1, result.finalVerification.liveReadAttempts);
        assertTrue(wire.events.indexOf("ARCHIVE_SYNC_START_RESET_DEFAULT")
                < wire.events.indexOf("ARCHIVE_SYNC_MBUS_RESET"));
        assertTrue(wire.events.indexOf("ARCHIVE_SYNC_MBUS_RESET")
                < wire.events.indexOf("ARCHIVE_SYNC_SELECT_50_40"));
        assertTrue(wire.events.indexOf("ARCHIVE_SYNC_FINAL_RESET_DEFAULT")
                > wire.events.indexOf("ARCHIVE_SYNC_SELECTED_3_7B"));
    }

    @Test public void monthBoundaryGuardStopsBeforeApplicationSelect() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault(METER, "2026-09-30 23:59", 30_000_000L),
                healthyDefault(METER, "2026-09-30 23:59", 30_000_001L));

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                new QueueMapper());

        assertEquals(ArchiveFamilySyncState.StopReason.BOUNDARY_GUARD_REACHED,
                result.traversal.stopReason);
        assertFalse(result.applicationSelectAttempted);
        assertEquals(0, result.selectedRequestsAttempted());
        assertFalse(result.completeSafetyShell());
        assertTrue(result.finalRestoreVerified());
    }

    @Test public void wrongMeterIsRejectedBeforeArchiveSelect() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault("OTHER-METER", "2026-09-06 10:00", 20_000_000L),
                healthyDefault("OTHER-METER", "2026-09-06 10:01", 20_000_060L));

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                new QueueMapper());

        assertEquals(ArchiveFamilySyncState.StopReason.METER_ID_MISMATCH,
                result.traversal.stopReason);
        assertFalse(result.applicationSelectAttempted);
        assertEquals(0, result.selectedRequestsAttempted());
        assertFalse(result.completeSafetyShell());
    }

    @Test public void technicalWatchdogIsIncompleteAndDoesNotInventAnotherRequest() {
        FakeWire wire = new FakeWire();
        wire.selectedAdvanceMs = ArchiveFamilyTransportAdapter.TECHNICAL_WATCHDOG_MS + 1L;
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault(METER, "2026-09-06 10:00", 20_000_000L),
                healthyDefault(METER, "2026-09-06 10:05", 20_000_300L));
        QueueMapper mapper = new QueueMapper(period("2026-09-01 00:00", 19_500_000L));

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper);

        assertEquals(ArchiveFamilySyncState.StopReason.WATCHDOG_REACHED,
                result.traversal.stopReason);
        assertEquals(1, result.selectedRequestsAttempted());
        assertEquals(1, result.traversal.periods.size());
        assertFalse(result.traversal.semanticSuccess());
        assertFalse(result.completeSafetyShell());
    }

    @Test public void protocolTerminalWithFinalOnTimeRegressionIsNotComplete() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault(METER, "2026-09-06 10:00", 20_000_000L),
                healthyDefault(METER, "2026-09-06 10:01", 19_999_999L));
        QueueMapper mapper = new QueueMapper(
                ArchiveTraversalStateMachine.Candidate.terminal("E5 7D"));

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper);

        assertTrue(result.traversal.semanticSuccess());
        assertFalse(result.finalRestoreVerified());
        assertEquals(ArchiveFamilySyncState.StopReason.ON_TIME_INCONSISTENT,
                result.finalVerification.failureReason);
        assertFalse(result.completeSafetyShell());
    }

    @Test public void exactW1TerminalMapsWithoutAnyPeriodCountRule() {
        ArchiveTraversalStateMachine.Candidate terminal =
                ArchiveFamilyTransportAdapter.mapProductionResponse(
                        ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                        new byte[]{(byte) 0xE5, 0x7D},
                        RETRIEVED);

        assertEquals(ArchiveTraversalStateMachine.CandidateKind.PROTOCOL_TERMINAL, terminal.kind);
        assertEquals("E5 7D", terminal.diagnostic);
    }

    private static ArchiveTraversalStateMachine.Candidate period(String timestamp, long onTime) {
        ArchiveFamilyPeriod period = new ArchiveFamilyPeriod(
                ArchiveFamilyPeriod.Family.MONTH,
                timestamp,
                RETRIEVED,
                FP,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                ArchiveNormalizedValues.builder().build());
        return ArchiveTraversalStateMachine.Candidate.period(
                new ArchiveTraversalStateMachine.PeriodEvidence(period, onTime, true, false));
    }

    private static DefaultReadObservation healthyDefault(
            String meterId, String rawMeterTime, long onTime) {
        return new DefaultReadObservation(
                true,
                true,
                ArchiveFamilyTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT,
                meterId,
                MeterTimeEvidence.inspect(timeFrame(rawMeterTime, onTime)));
    }

    private static byte[] timeFrame(String raw, long onTime) {
        String[] dateTime = raw.split(" ");
        String[] date = dateTime[0].split("-");
        String[] time = dateTime[1].split(":");
        int year = Integer.parseInt(date[0]);
        int month = Integer.parseInt(date[1]);
        int day = Integer.parseInt(date[2]);
        int hour = Integer.parseInt(time[0]);
        int minute = Integer.parseInt(time[1]);
        int offset = year - 2000;
        int b0 = minute & 0x3F;
        int b1 = hour & 0x1F;
        int b2 = (day & 0x1F) | ((offset & 0x07) << 5);
        int b3 = (month & 0x0F) | ((offset & 0x78) << 1);
        byte[] typeF = new byte[]{0x04, 0x6D, (byte) b0, (byte) b1, (byte) b2, (byte) b3};
        byte[] onTimeRecord = new byte[]{
                0x04, 0x20,
                (byte) (onTime & 0xFF),
                (byte) ((onTime >> 8) & 0xFF),
                (byte) ((onTime >> 16) & 0xFF),
                (byte) ((onTime >> 24) & 0xFF)};
        return frame(typeF, onTimeRecord);
    }

    private static byte[] frame(byte[]... records) {
        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream();
        for (byte[] record : records) recordBytes.write(record, 0, record.length);
        byte[] payload = recordBytes.toByteArray();
        int l = 3 + 12 + payload.length;
        byte[] frame = new byte[l + 6];
        frame[0] = 0x68;
        frame[1] = (byte) l;
        frame[2] = (byte) l;
        frame[3] = 0x68;
        frame[4] = 0x08;
        frame[5] = (byte) 0xFE;
        frame[6] = 0x72;
        System.arraycopy(payload, 0, frame, 19, payload.length);
        int checksum = 0;
        for (int i = 4; i < 4 + l; i++) checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        frame[4 + l] = (byte) checksum;
        frame[5 + l] = 0x16;
        return frame;
    }

    private static final class QueueMapper implements ArchiveFamilyTransportAdapter.ResponseMapper {
        final Deque<ArchiveTraversalStateMachine.Candidate> queue = new ArrayDeque<>();

        QueueMapper(ArchiveTraversalStateMachine.Candidate... candidates) {
            queue.addAll(Arrays.asList(candidates));
        }

        @Override public ArchiveTraversalStateMachine.Candidate map(
                ArchiveFamilyPolicy policy, byte[] response, String retrievedAtUtc) {
            if (queue.isEmpty()) throw new AssertionError("unexpected selected response");
            return queue.removeFirst();
        }
    }

    private static final class QueueVerifier implements ArchiveFamilyTransportAdapter.DefaultVerifier {
        final Deque<DefaultReadObservation> queue = new ArrayDeque<>();

        QueueVerifier(DefaultReadObservation... observations) {
            queue.addAll(Arrays.asList(observations));
        }

        @Override public DefaultReadObservation readDefault() throws IOException {
            if (queue.isEmpty()) throw new AssertionError("unexpected default verification");
            return queue.removeFirst();
        }
    }

    private static final class FakeWire implements ArchiveFamilyTransportAdapter.Wire {
        final List<String> events = new ArrayList<>();
        long elapsedMs;
        long selectedAdvanceMs = 100L;
        boolean healthy = true;

        @Override public void prepare() {
            events.add("PREPARE");
        }

        @Override public byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) {
            events.add(label);
            if (label.startsWith("ARCHIVE_SYNC_SELECTED_")) elapsedMs += selectedAdvanceMs;
            else elapsedMs += 50L;
            return new byte[]{(byte) 0xE5};
        }

        @Override public void coolDown(long millis) {
            elapsedMs += Math.max(0L, millis);
        }

        @Override public boolean transportHealthy() {
            return healthy;
        }

        @Override public long elapsedRealtimeMs() {
            return elapsedMs;
        }
    }
}
