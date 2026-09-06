package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MeterTimeEvidenceTest {
    @Test public void extractsRawLoggerTypeFAndTypedOnTimeWithoutChangingProtectedParser() {
        byte[] frame = frame(
                typeFRecord(2026, 9, 6, 13, 8, false, false),
                onTimeRecord(0x01020304L));

        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame);

        assertTrue(evidence.frameValid);
        assertTrue(evidence.typeFPresent);
        assertEquals("08 0D 46 39", evidence.rawTypeFHex);
        assertEquals("2026-09-06 13:08", evidence.decodedRawWallClock);
        assertFalse(evidence.invalidTime);
        assertFalse(evidence.summerTime);
        assertEquals(Long.valueOf(0x01020304L), evidence.onTimeSeconds);
        assertNull(evidence.error);
        assertTrue(evidence.suitableForFamilyBoundaryAnchor());
    }

    @Test public void preservesSummerFlagButDoesNotTreatItAsTimezoneConversion() {
        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 7, 1, 9, 15, false, true),
                onTimeRecord(123456L)));

        assertTrue(evidence.typeFPresent);
        assertTrue(evidence.summerTime);
        assertFalse(evidence.invalidTime);
        assertEquals("2026-07-01 09:15", evidence.decodedRawWallClock);
        assertTrue(evidence.suitableForFamilyBoundaryAnchor());
    }

    @Test public void invalidTimeFlagBlocksBoundaryAnchorButRawEvidenceIsRetained() {
        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 9, 6, 13, 8, true, false),
                onTimeRecord(987654L)));

        assertTrue(evidence.typeFPresent);
        assertTrue(evidence.invalidTime);
        assertEquals("88 0D 46 39", evidence.rawTypeFHex);
        assertEquals(Long.valueOf(987654L), evidence.onTimeSeconds);
        assertFalse(evidence.suitableForFamilyBoundaryAnchor());
    }

    @Test public void extremaTypeFExtensionDoesNotReplaceLoggerTimestamp() {
        byte[] extremum = concat(
                new byte[]{0x04, (byte) 0xBB, 0x6D},
                rawTypeF(2025, 12, 31, 23, 59, false, false));
        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame(
                extremum,
                typeFRecord(2026, 1, 1, 0, 0, false, false),
                onTimeRecord(500000L)));

        assertEquals("2026-01-01 00:00", evidence.decodedRawWallClock);
        assertEquals(Long.valueOf(500000L), evidence.onTimeSeconds);
    }

    @Test public void missingOnTimeIsExplicitAndCannotBeUsedAsSafetyAnchor() {
        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 9, 6, 13, 8, false, false)));

        assertTrue(evidence.typeFPresent);
        assertNull(evidence.onTimeSeconds);
        assertEquals("ON_TIME_VIF_20_NOT_FOUND", evidence.error);
        assertFalse(evidence.suitableForFamilyBoundaryAnchor());
    }

    @Test public void missingLoggerTypeFIsExplicitEvenWhenOnTimeExists() {
        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame(onTimeRecord(123L)));

        assertTrue(evidence.frameValid);
        assertFalse(evidence.typeFPresent);
        assertEquals(Long.valueOf(123L), evidence.onTimeSeconds);
        assertEquals("TYPE_F_VIF_6D_NOT_FOUND", evidence.error);
        assertFalse(evidence.suitableForFamilyBoundaryAnchor());
    }

    @Test public void invalidMbusChecksumIsRejectedBeforeTimeEvidenceIsTrusted() {
        byte[] frame = frame(
                typeFRecord(2026, 9, 6, 13, 8, false, false),
                onTimeRecord(123L));
        frame[frame.length - 2] ^= 0x01;

        MeterTimeEvidence evidence = MeterTimeEvidence.inspect(frame);
        assertFalse(evidence.frameValid);
        assertFalse(evidence.typeFPresent);
        assertEquals("MBUS_LONG_FRAME_NOT_FOUND", evidence.error);
    }

    private static byte[] typeFRecord(
            int year, int month, int day, int hour, int minute, boolean invalid, boolean summer) {
        return concat(new byte[]{0x04, 0x6D}, rawTypeF(year, month, day, hour, minute, invalid, summer));
    }

    private static byte[] rawTypeF(
            int year, int month, int day, int hour, int minute, boolean invalid, boolean summer) {
        int offset = year - 2000;
        int b0 = minute & 0x3F;
        int b1 = hour & 0x1F;
        if (invalid) b0 |= 0x80;
        if (summer) b1 |= 0x80;
        int b2 = (day & 0x1F) | ((offset & 0x07) << 5);
        int b3 = (month & 0x0F) | ((offset & 0x78) << 1);
        return new byte[]{(byte) b0, (byte) b1, (byte) b2, (byte) b3};
    }

    private static byte[] onTimeRecord(long seconds) {
        return new byte[]{
                0x04, 0x20,
                (byte) (seconds & 0xFF),
                (byte) ((seconds >> 8) & 0xFF),
                (byte) ((seconds >> 16) & 0xFF),
                (byte) ((seconds >> 24) & 0xFF)};
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
        // Bytes 7..18 are a synthetic zeroed CI=72 fixed variable-data header.
        System.arraycopy(payload, 0, frame, 19, payload.length);
        int checksum = 0;
        for (int i = 4; i < 4 + l; i++) checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        frame[4 + l] = (byte) checksum;
        frame[5 + l] = 0x16;
        return frame;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }
}
