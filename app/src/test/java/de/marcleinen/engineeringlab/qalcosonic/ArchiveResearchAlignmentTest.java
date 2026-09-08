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

public final class ArchiveResearchAlignmentTest {
    private static final String METER = "07288628";
    private static final String RETRIEVED = "2026-09-06T10:30:00Z";

    @Test public void preflightAdoptsObservedStructureAndRepreparesBeforeArchive() {
        List<String> events = new ArrayList<>();
        FakeWire wire = new FakeWire(events);
        QueueVerifier verifier = new QueueVerifier(events,
                healthy("research-observed-default", "2026-09-06 12:15", 20_000_000L),
                healthy("research-observed-default", "2026-09-06 12:16", 20_000_060L));
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

        assertTrue(result.preflight.verified);
        assertTrue(result.archivePrepareAttempted);
        assertTrue(result.archivePrepareSucceeded);
        assertEquals(2, count(events, "PREPARE"));
        assertTrue(index(events, "LIVE_1") < secondIndex(events, "PREPARE"));
        assertTrue(secondIndex(events, "PREPARE") < index(events, "ARCHIVE_SYNC_MBUS_RESET"));
        assertTrue(index(events, "ARCHIVE_SYNC_MBUS_RESET")
                < index(events, "ARCHIVE_SYNC_SELECT_50_40"));
        assertTrue(result.completeSafetyShell());
    }

    @Test public void finalDefaultMustMatchPreflightSessionStructure() {
        List<String> events = new ArrayList<>();
        FakeWire wire = new FakeWire(events);
        QueueVerifier verifier = new QueueVerifier(events,
                healthy("session-structure-A", "2026-09-06 12:15", 20_000_000L),
                healthy("session-structure-B", "2026-09-06 12:16", 20_000_060L),
                healthy("session-structure-B", "2026-09-06 12:16", 20_000_061L));
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
        assertEquals(ArchiveFamilySyncState.StopReason.DEFAULT_STATE_UNVERIFIED,
                result.finalVerification.failureReason);
        assertEquals(2, result.finalVerification.applicationResetCommands);
        assertEquals(2, result.finalVerification.liveReadAttempts);
        assertFalse(result.completeSafetyShell());
    }

    @Test public void missingPreflightResetHostResponseStopsBeforeLiveAndSelect() {
        List<String> events = new ArrayList<>();
        FakeWire wire = new FakeWire(events);
        wire.noHostOnStartReset = true;
        QueueVerifier verifier = new QueueVerifier(events,
                healthy("restore-structure", "2026-09-06 12:15", 20_000_000L));

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

        assertEquals(ArchiveFamilySyncState.StopReason.NO_HOST_RESPONSE,
                result.traversal.stopReason);
        assertEquals(0, result.preflight.liveReadAttempts);
        assertFalse(result.applicationSelectAttempted);
        assertEquals(0, result.selectedRequestsAttempted());
        // The finally block still makes a best-effort restore; only that final verification reads Live.
        assertEquals(1, result.finalVerification.liveReadAttempts);
    }

    private static int index(List<String> events, String value) {
        int i = events.indexOf(value);
        if (i < 0) throw new AssertionError("missing event " + value + ": " + events);
        return i;
    }

    private static int secondIndex(List<String> events, String value) {
        int first = index(events, value);
        int second = events.subList(first + 1, events.size()).indexOf(value);
        if (second < 0) throw new AssertionError("missing second event " + value + ": " + events);
        return first + 1 + second;
    }

    private static int count(List<String> events, String value) {
        int result = 0;
        for (String event : events) if (value.equals(event)) result++;
        return result;
    }

    private static DefaultReadObservation healthy(
            String fingerprint, String rawMeterTime, long onTime) {
        return new DefaultReadObservation(
                true,
                true,
                fingerprint,
                METER,
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
        final List<String> events;
        final Deque<DefaultReadObservation> queue = new ArrayDeque<>();
        int reads;

        QueueVerifier(List<String> events, DefaultReadObservation... observations) {
            this.events = events;
            queue.addAll(Arrays.asList(observations));
        }

        @Override public DefaultReadObservation readDefault() throws IOException {
            reads++;
            events.add("LIVE_" + reads);
            if (queue.isEmpty()) throw new AssertionError("unexpected default verification");
            return queue.removeFirst();
        }
    }

    private static final class FakeWire implements ArchiveFamilyTransportAdapter.Wire {
        final List<String> events;
        long elapsedMs;
        boolean noHostOnStartReset;

        FakeWire(List<String> events) {
            this.events = events;
        }

        @Override public void prepare() {
            events.add("PREPARE");
        }

        @Override public byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) {
            events.add(label);
            elapsedMs += 50L;
            if (noHostOnStartReset && "ARCHIVE_SYNC_START_RESET_DEFAULT".equals(label)) return null;
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
