package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.Test;

/** Guards the temporary dashboard compatibility seam: real dual-interface wire must use v2. */
public final class MonthlyArchiveV2CompatibilityFacadeTest {
    private static final String RETRIEVED = "2026-09-06T09:10:00Z";

    @Test public void productionFourArgRunRoutesToV2AndDiscardsLegacyHardCap() {
        DualWire wire = new DualWire(
                timeFrame("2026-09-01 00:00", 19_500_000L),
                timeFrame("2026-08-01 00:00", 16_821_600L),
                new byte[]{(byte) 0xE5, 0x7D});
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault("METER-1", "2026-09-06 10:00", 20_000_000L),
                healthyDefault("METER-1", "2026-09-06 10:01", 20_000_060L));

        // Legacy cap=1 is deliberately smaller than the two supplied Month records. The real
        // dual-interface production path must still read both records and request the terminal.
        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                1, wire, verifier, RETRIEVED);

        assertEquals(0, result.hardCap);
        assertEquals(3, result.selectedRequestsAttempted());
        assertEquals(2, result.enumeration.periods.size());
        assertEquals("2026-09-01 00:00", result.enumeration.periods.get(0).loggerTimestamp);
        assertEquals("2026-08-01 00:00", result.enumeration.periods.get(1).loggerTimestamp);
        assertTrue(result.enumeration.terminalConfirmed());
        assertEquals(MonthlyArchiveEnumerator.StopReason.TERMINAL_W1_E5_7D,
                result.enumeration.stopReason);
        assertTrue(result.restore.defaultVerified);
        assertTrue(wire.startResetSeen);
        assertTrue(wire.finalResetSeen);
        assertTrue(wire.familySelectSeen);
    }

    @Test public void bridgeStillRejectsDifferentMeterAtFinalVerification() {
        DualWire wire = new DualWire();
        QueueVerifier verifier = new QueueVerifier(
                healthyDefault("METER-1", "2026-09-06 10:00", 20_000_000L),
                healthyDefault("METER-2", "2026-09-06 10:01", 20_000_060L));

        MonthlyArchiveTransportAdapter.Result result = MonthlyArchiveTransportAdapter.run(
                36, wire, verifier, RETRIEVED);

        assertTrue(result.enumeration.terminalConfirmed());
        assertFalse(result.restore.defaultVerified);
        assertEquals("V2_METER_ID_MISMATCH", result.restore.status);
    }

    private static DefaultReadObservation healthyDefault(
            String meterId, String rawMeterTime, long onTime) {
        return new DefaultReadObservation(
                true,
                true,
                MonthlyArchiveTransportAdapter.PROTECTED_DEFAULT_FINGERPRINT,
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

    private static final class QueueVerifier implements MonthlyArchiveTransportAdapter.DefaultVerifier,
            ArchiveFamilyTransportAdapter.DefaultVerifier {
        private final Deque<DefaultReadObservation> observations = new ArrayDeque<>();

        QueueVerifier(DefaultReadObservation... observations) {
            this.observations.addAll(Arrays.asList(observations));
        }

        @Override public DefaultReadObservation readDefault() throws IOException {
            if (observations.isEmpty()) throw new AssertionError("unexpected default verification");
            return observations.removeFirst();
        }
    }

    private static final class DualWire implements MonthlyArchiveTransportAdapter.Wire,
            ArchiveFamilyTransportAdapter.Wire {
        private final Deque<byte[]> selectedResponses = new ArrayDeque<>();
        long elapsedMs;
        boolean healthy = true;
        boolean startResetSeen;
        boolean finalResetSeen;
        boolean familySelectSeen;

        DualWire(byte[]... selectedResponses) {
            if (selectedResponses == null || selectedResponses.length == 0) {
                this.selectedResponses.add(new byte[]{(byte) 0xE5, 0x7D});
            } else {
                this.selectedResponses.addAll(Arrays.asList(selectedResponses));
            }
        }

        @Override public void prepare() { }

        @Override public byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) {
            elapsedMs += 50L;
            if ("ARCHIVE_SYNC_START_RESET_DEFAULT".equals(label)) startResetSeen = true;
            if ("ARCHIVE_SYNC_FINAL_RESET_DEFAULT".equals(label)) finalResetSeen = true;
            if ("ARCHIVE_SYNC_SELECT_50_40".equals(label)) familySelectSeen = true;
            if (label.startsWith("ARCHIVE_SYNC_SELECTED_")) {
                if (selectedResponses.isEmpty()) {
                    throw new AssertionError("unexpected selected request " + label);
                }
                return selectedResponses.removeFirst();
            }
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
