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

public final class ArchiveImmediatePersistenceTest {
    private static final String METER = "METER-1";
    private static final String RETRIEVED = "2026-09-06T09:30:00Z";
    private static final String FP = "month-fingerprint";

    @Test public void acceptedRecordIsPersistedBeforeNextSelectedRequest() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = defaults();
        QueueMapper mapper = new QueueMapper(
                period("2026-09-01 00:00", 19_500_000L),
                period("2026-08-01 00:00", 16_821_600L),
                ArchiveTraversalStateMachine.Candidate.terminal("E5 7D"));

        ArchivePersistenceCoordinator.ImmediateSession persistence =
                ArchivePersistenceCoordinator.beginImmediate(
                        (meterId, period) -> {
                            wire.events.add("PERSIST_" + period.loggerTimestamp);
                            return ArchivePersistenceCoordinator.WriteOutcome.INSERTED;
                        },
                        METER,
                        ArchiveFamilyPeriod.Family.MONTH);

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper,
                persistence::accept);

        assertTrue(index(wire.events, "ARCHIVE_SYNC_SELECTED_1_7B")
                < index(wire.events, "PERSIST_2026-09-01 00:00"));
        assertTrue(index(wire.events, "PERSIST_2026-09-01 00:00")
                < index(wire.events, "ARCHIVE_SYNC_SELECTED_2_5B"));
        assertTrue(index(wire.events, "ARCHIVE_SYNC_SELECTED_2_5B")
                < index(wire.events, "PERSIST_2026-08-01 00:00"));
        assertTrue(index(wire.events, "PERSIST_2026-08-01 00:00")
                < index(wire.events, "ARCHIVE_SYNC_SELECTED_3_7B"));

        ArchivePersistenceCoordinator.Result persisted = persistence.result();
        assertEquals(2, persisted.accepted);
        assertEquals(2, persisted.committed);
        assertEquals(2, persisted.inserted);
        assertTrue(persisted.complete());
        assertEquals(2, result.persistedAccepted);
        assertTrue(result.persistenceComplete());
        assertTrue(result.completeProductAttempt());
    }

    @Test public void persistenceFailureStopsBeforeAnotherArchiveRequestAndStillRestores() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = defaults();
        QueueMapper mapper = new QueueMapper(
                period("2026-09-01 00:00", 19_500_000L),
                period("2026-08-01 00:00", 16_821_600L),
                ArchiveTraversalStateMachine.Candidate.terminal("E5 7D"));

        final int[] writes = {0};
        ArchivePersistenceCoordinator.ImmediateSession persistence =
                ArchivePersistenceCoordinator.beginImmediate(
                        (meterId, period) -> {
                            writes[0]++;
                            if (writes[0] == 2) throw new IllegalStateException("disk full");
                            return ArchivePersistenceCoordinator.WriteOutcome.INSERTED;
                        },
                        METER,
                        ArchiveFamilyPeriod.Family.MONTH);

        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                null,
                0,
                mapper,
                persistence::accept);

        assertEquals(2, result.selectedRequestsAttempted());
        assertEquals(2, result.traversal.periods.size());
        assertEquals(1, result.persistedAccepted);
        assertEquals("disk full", result.persistenceDiagnostic);
        assertEquals(ArchiveFamilySyncState.StopReason.PERSISTENCE_ERROR,
                result.effectiveStopReason());
        assertTrue(result.finalRestoreVerified());
        assertFalse(result.persistenceComplete());
        assertFalse(result.completeProductAttempt());

        ArchivePersistenceCoordinator.Result persisted = persistence.result();
        assertEquals(2, persisted.accepted);
        assertEquals(1, persisted.committed);
        assertEquals("2026-08-01 00:00", persisted.failedLoggerTimestamp);
        assertEquals("disk full", persisted.failureDiagnostic);
    }

    @Test public void persistenceFailureOverridesKnownRecordSemanticSuccessForProductCompletion() {
        FakeWire wire = new FakeWire();
        QueueVerifier verifier = defaults();
        QueueMapper mapper = new QueueMapper(
                period("2026-09-01 00:00", 19_500_000L),
                period("2026-08-01 00:00", 16_821_600L));

        final int[] writes = {0};
        ArchiveFamilyTransportAdapter.Result result = ArchiveFamilyTransportAdapter.run(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.MONTH),
                wire,
                verifier,
                METER,
                RETRIEVED,
                ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                evidence -> true,
                2,
                mapper,
                period -> {
                    writes[0]++;
                    if (writes[0] == 2) throw new IllegalStateException("write failed");
                });

        assertEquals(ArchiveFamilySyncState.StopReason.KNOWN_RECORD_REACHED,
                result.traversal.stopReason);
        assertTrue(result.completeSafetyShell());
        assertEquals(ArchiveFamilySyncState.StopReason.PERSISTENCE_ERROR,
                result.effectiveStopReason());
        assertFalse(result.persistenceComplete());
        assertFalse(result.completeProductAttempt());
        assertEquals(2, result.selectedRequestsAttempted());
    }

    @Test public void immediateSessionTracksConfirmationsAndConflicts() {
        Deque<ArchivePersistenceCoordinator.WriteOutcome> outcomes = new ArrayDeque<>(Arrays.asList(
                ArchivePersistenceCoordinator.WriteOutcome.INSERTED,
                ArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL,
                ArchivePersistenceCoordinator.WriteOutcome.CONFLICT_RECORDED));
        ArchivePersistenceCoordinator.ImmediateSession persistence =
                ArchivePersistenceCoordinator.beginImmediate(
                        (meterId, period) -> outcomes.removeFirst(),
                        METER,
                        ArchiveFamilyPeriod.Family.MONTH);

        persistence.accept(periodValue("2026-09-01 00:00"));
        persistence.accept(periodValue("2026-08-01 00:00"));
        persistence.accept(periodValue("2026-07-01 00:00"));

        ArchivePersistenceCoordinator.Result result = persistence.result();
        assertEquals(3, result.accepted);
        assertEquals(3, result.committed);
        assertEquals(1, result.inserted);
        assertEquals(1, result.confirmed);
        assertEquals(1, result.conflicts);
        assertTrue(result.complete());
    }

    private static QueueVerifier defaults() {
        return new QueueVerifier(
                healthyDefault(METER, "2026-09-06 10:00", 20_000_000L),
                healthyDefault(METER, "2026-09-06 10:01", 20_000_060L));
    }

    private static ArchiveTraversalStateMachine.Candidate period(String timestamp, long onTime) {
        return ArchiveTraversalStateMachine.Candidate.period(
                new ArchiveTraversalStateMachine.PeriodEvidence(
                        periodValue(timestamp), onTime, true, false));
    }

    private static ArchiveFamilyPeriod periodValue(String timestamp) {
        return new ArchiveFamilyPeriod(
                ArchiveFamilyPeriod.Family.MONTH,
                timestamp,
                RETRIEVED,
                FP,
                MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE,
                MonthlyArchivePeriod.VALIDATION_COMPLETE,
                ArchiveNormalizedValues.builder().build());
    }

    private static DefaultReadObservation healthyDefault(
            String meterId, String rawMeterTime, long onTime) {
        return new DefaultReadObservation(
                true,
                true,
                FP,
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

    private static int index(List<String> events, String expected) {
        int index = events.indexOf(expected);
        if (index < 0) throw new AssertionError("missing event: " + expected + " in " + events);
        return index;
    }

    private static final class QueueMapper implements ArchiveFamilyTransportAdapter.ResponseMapper {
        private final Deque<ArchiveTraversalStateMachine.Candidate> queue = new ArrayDeque<>();

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
        private final Deque<DefaultReadObservation> queue = new ArrayDeque<>();

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

        @Override public void prepare() {
            events.add("PREPARE");
        }

        @Override public byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) {
            events.add(label);
            elapsedMs += 50L;
            return new byte[]{(byte) 0xE5};
        }

        @Override public void coolDown(long millis) {
            elapsedMs += Math.max(0L, millis);
        }

        @Override public boolean transportHealthy() {
            return true;
        }

        @Override public long elapsedRealtimeMs() {
            return elapsedMs;
        }
    }
}
