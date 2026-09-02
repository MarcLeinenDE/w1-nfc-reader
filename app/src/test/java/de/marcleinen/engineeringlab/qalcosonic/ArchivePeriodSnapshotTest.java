package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ArchivePeriodSnapshotTest {
    @Test
    public void realCapturePatternDecodesExtremumDateTimesAndBuildsMonthlySnapshot() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(realCapturePatternRecords()));

        assertTrue(inspection.parseComplete);
        assertEquals(20, inspection.records.size());

        assertEquals("VOLUME_FLOW_EXTREMUM_DATE_TIME", inspection.records.get(9).semantic);
        assertEquals("2026-07-28 16:25", inspection.records.get(9).value);
        assertEquals("VOLUME_FLOW_EXTREMUM_DATE_TIME", inspection.records.get(11).semantic);
        assertEquals("2026-07-31 00:00", inspection.records.get(11).value);
        assertEquals("TEMPERATURE_EXTREMUM_DATE_TIME", inspection.records.get(14).semantic);
        assertEquals("2026-07-20 05:27", inspection.records.get(14).value);
        assertEquals("TEMPERATURE_EXTREMUM_DATE_TIME", inspection.records.get(16).semantic);
        assertEquals("2026-07-12 18:46", inspection.records.get(16).value);

        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                ArchivePeriodSnapshot.PeriodType.MONTH, inspection, "2026-08-31T05:05:02Z");

        assertEquals("2026-08-01 00:00", snapshot.loggerDateTime);
        assertEquals("2026-08-31T05:05:02Z", snapshot.retrievedAtUtc);
        assertEquals("196.668 m3", snapshot.totalVolume);
        assertEquals("196.668 m3", snapshot.positiveVolume);
        assertEquals("0.001 m3", snapshot.reverseVolume);
        assertEquals("0.959 m3/h", snapshot.maxFlow);
        assertEquals("2026-07-28 16:25", snapshot.maxFlowAt);
        assertEquals("0 m3/h", snapshot.minFlow);
        assertEquals("2026-07-31 00:00", snapshot.minFlowAt);
        assertEquals("23.95 degC", snapshot.maxTemperature);
        assertEquals("2026-07-20 05:27", snapshot.maxTemperatureAt);
        assertEquals("18.74 degC", snapshot.minTemperature);
        assertEquals("2026-07-12 18:46", snapshot.minTemperatureAt);
        assertEquals("89 %", snapshot.batteryPercent);
        assertEquals("0x00000000", snapshot.errorFlags);
        assertEquals("MONTH|2026-08-01 00:00", snapshot.periodKeyWithinMeter());

        String preview = snapshot.toUserPreview();
        assertTrue(preview.contains("Monatsarchiv"));
        assertTrue(preview.contains("Max. Durchfluss: 0.959 m3/h · 2026-07-28 16:25"));
        assertTrue(preview.contains("Min. Temperatur: 18.74 degC · 2026-07-12 18:46"));
        assertFalse(preview.contains("928780"));
        assertFalse(preview.contains("928253"));
    }

    @Test
    public void periodKeyRequiresLoggerTimestampAndIsOnlyMeterLocal() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0x04, 0x13, 0xE8, 0x03, 0x00, 0x00)));
        ArchivePeriodSnapshot snapshot = ArchivePeriodSnapshot.fromInspection(
                ArchivePeriodSnapshot.PeriodType.MONTH, inspection);
        assertEquals(null, snapshot.periodKeyWithinMeter());
    }

    private static byte[] realCapturePatternRecords() {
        return bytes(
                0x04, 0x6D, 0x00, 0x00, 0x41, 0x38,
                0x04, 0x20, 0xC8, 0x66, 0x9F, 0x04,
                0x34, 0xFD, 0x17, 0x00, 0x00, 0x00, 0x00,
                0x04, 0x24, 0x80, 0xDE, 0x28, 0x00,
                0x04, 0x13, 0x3C, 0x00, 0x03, 0x00,
                0x04, 0x93, 0x3B, 0x3C, 0x00, 0x03, 0x00,
                0x04, 0x93, 0x3C, 0x01, 0x00, 0x00, 0x00,
                0x81, 0x10, 0x13, 0x12,
                0x12, 0x3B, 0xBF, 0x03,
                0x14, 0xBB, 0x6D, 0x19, 0x10, 0x5C, 0x37,
                0x22, 0x3B, 0x00, 0x00,
                0x24, 0xBB, 0x6D, 0x00, 0x00, 0x5F, 0x37,
                0x02, 0x3B, 0x08, 0x00,
                0x12, 0x59, 0x5B, 0x09,
                0x14, 0xD9, 0x6D, 0x1B, 0x05, 0x54, 0x37,
                0x22, 0x59, 0x52, 0x07,
                0x24, 0xD9, 0x6D, 0x2E, 0x12, 0x4C, 0x37,
                0x02, 0x59, 0xC4, 0x08,
                0x02, 0x66, 0xDC, 0x00,
                0x02, 0xFD, 0x74, 0x59, 0x00);
    }

    private static byte[] ci72Frame(byte[] records) {
        int l = 15 + records.length;
        byte[] frame = new byte[l + 6];
        frame[0] = 0x68;
        frame[1] = (byte) l;
        frame[2] = (byte) l;
        frame[3] = 0x68;
        frame[4] = 0x08;
        frame[5] = (byte) 0xFE;
        frame[6] = 0x72;
        frame[13] = 0x10;
        frame[14] = 0x07;
        System.arraycopy(records, 0, frame, 19, records.length);
        int checksumIndex = 4 + l;
        int checksum = 0;
        for (int i = 4; i < checksumIndex; i++) checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        frame[checksumIndex] = (byte) checksum;
        frame[checksumIndex + 1] = 0x16;
        return frame;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
