package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ArchiveRecordInspectorTest {
    @Test
    public void decodesDifeStorageTariffAndScaledVolume() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0xC4, 0x11, 0x13, 0x39, 0x30, 0x00, 0x00)));

        assertTrue(inspection.parseComplete);
        assertEquals(1, inspection.records.size());
        ArchiveRecordInspector.Record record = inspection.records.get(0);
        assertEquals(3L, record.storageNumber);
        assertEquals(1L, record.tariff);
        assertEquals(0L, record.subunit);
        assertEquals("INSTANTANEOUS", record.function);
        assertEquals("VOLUME", record.semantic);
        assertEquals("12.345 m3", record.value);
    }

    @Test
    public void decodesOrthogonalPositiveAccumulationVife() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0x04, 0x93, 0x3B, 0xE8, 0x03, 0x00, 0x00)));

        ArchiveRecordInspector.Record record = inspection.records.get(0);
        assertEquals("VOLUME_POSITIVE", record.semantic);
        assertTrue(record.vifeSemantics.contains("ACCUMULATION_POSITIVE_ONLY"));
        assertEquals("1 m3", record.value);
    }

    @Test
    public void decodesCi72DateTimeRecord() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0x04, 0x6D, 0x2C, 0x02, 0x5F, 0x38)));

        ArchiveRecordInspector.Record record = inspection.records.get(0);
        assertEquals("TIME_POINT_DATE_TIME", record.semantic);
        assertEquals("2026-08-31 02:44", record.value);
    }

    @Test
    public void redactsIdentifierLikeRecordValue() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0x0C, 0x78, 0xDE, 0xAD, 0xBE, 0xEF)));

        ArchiveRecordInspector.Record record = inspection.records.get(0);
        assertTrue(record.identifierLike);
        assertEquals("REDACTED_IDENTIFIER_LIKE", record.value);
        String trace = String.join("\n", ArchiveRecordInspector.privacySafeTraceLines(inspection));
        assertFalse(trace.contains("DE AD BE EF"));
    }

    @Test
    public void oneFIsOnlyReportedAsMoreTelegramHint() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(
                        0x04, 0x13, 0xE8, 0x03, 0x00, 0x00,
                        0x1F, 0xAA, 0xBB, 0xCC)));

        assertTrue(inspection.parseComplete);
        assertTrue(inspection.moreRecordsFollow);
        assertTrue(inspection.manufacturerSpecificRemainder);
        assertEquals(3, inspection.manufacturerSpecificBytes);
        List<String> trace = ArchiveRecordInspector.privacySafeTraceLines(inspection);
        assertTrue(trace.contains("ARCHIVE_RECORD_MORE_TELEGRAMS_HINT=true"));
    }

    @Test
    public void fdStorageMetadataIsDecodedUnsigned() {
        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectLongFrame(
                ci72Frame(bytes(0x01, 0xFD, 0x20, 0xFE)));

        ArchiveRecordInspector.Record record = inspection.records.get(0);
        assertEquals("FIRST_STORAGE_NUMBER", record.semantic);
        assertEquals("254", record.value);
    }

    @Test
    public void syntheticMonthlySizedRecordAreaIsAcceptedWithoutPagination() {
        byte[] records = new byte[110];
        byte[] first = bytes(
                0xC4, 0x11, 0x13, 0x39, 0x30, 0x00, 0x00,
                0x04, 0x6D, 0x2C, 0x02, 0x5F, 0x38);
        System.arraycopy(first, 0, records, 0, first.length);
        for (int i = first.length; i < records.length; i++) records[i] = 0x2F;

        ArchiveRecordInspector.Inspection inspection = ArchiveRecordInspector.inspectSelectedResponse(
                withMailboxTrailer(ci72Frame(records), 0x55));

        assertTrue(inspection.parseComplete);
        assertEquals(110, inspection.variableRecordBytes);
        assertEquals(2, inspection.records.size());
        assertFalse(inspection.moreRecordsFollow);
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
        // 12-byte fixed header is synthetic; no real meter identifier is used in tests.
        frame[13] = 0x10;
        frame[14] = 0x07;
        System.arraycopy(records, 0, frame, 19, records.length);
        int checksumIndex = 4 + l;
        int checksum = 0;
        for (int i = 4; i < checksumIndex; i++) {
            checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        }
        frame[checksumIndex] = (byte) checksum;
        frame[checksumIndex + 1] = 0x16;
        return frame;
    }

    private static byte[] withMailboxTrailer(byte[] frame, int trailer) {
        byte[] mailbox = new byte[frame.length + 1];
        System.arraycopy(frame, 0, mailbox, 0, frame.length);
        mailbox[mailbox.length - 1] = (byte) trailer;
        return mailbox;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
