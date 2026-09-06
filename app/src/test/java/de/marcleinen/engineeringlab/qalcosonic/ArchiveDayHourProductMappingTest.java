package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

public final class ArchiveDayHourProductMappingTest {
    private static final String RETRIEVED = "2026-09-06T11:00:00Z";

    @Test public void dayResponseMapsToDayFamilyPeriod() {
        ArchiveTraversalStateMachine.Candidate candidate = ArchiveFamilyProductionRunner.mapResponse(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.DAY),
                timeFrame("2026-09-06 00:00", 80_670_000L),
                RETRIEVED);

        assertEquals(ArchiveTraversalStateMachine.CandidateKind.PERIOD, candidate.kind);
        assertNotNull(candidate.evidence);
        assertEquals(ArchiveFamilyPeriod.Family.DAY, candidate.evidence.period.family);
        assertEquals("2026-09-06 00:00", candidate.evidence.period.loggerTimestamp);
        assertEquals(80_670_000L, candidate.evidence.onTimeSeconds);
        assertTrue(candidate.evidence.typeFPresent);
    }

    @Test public void hourResponseMapsToHourFamilyPeriod() {
        ArchiveTraversalStateMachine.Candidate candidate = ArchiveFamilyProductionRunner.mapResponse(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR),
                timeFrame("2026-09-06 11:00", 80_709_600L),
                RETRIEVED);

        assertEquals(ArchiveTraversalStateMachine.CandidateKind.PERIOD, candidate.kind);
        assertNotNull(candidate.evidence);
        assertEquals(ArchiveFamilyPeriod.Family.HOUR, candidate.evidence.period.family);
        assertEquals("2026-09-06 11:00", candidate.evidence.period.loggerTimestamp);
        assertEquals(80_709_600L, candidate.evidence.onTimeSeconds);
        assertTrue(candidate.evidence.typeFPresent);
    }

    @Test public void exactKnownTerminalIsPreservedForDayAndHour() {
        byte[] terminal = new byte[]{(byte) 0xE5, 0x7D};
        ArchiveTraversalStateMachine.Candidate day = ArchiveFamilyProductionRunner.mapResponse(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.DAY), terminal, RETRIEVED);
        ArchiveTraversalStateMachine.Candidate hour = ArchiveFamilyProductionRunner.mapResponse(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.HOUR), terminal, RETRIEVED);

        assertEquals(ArchiveTraversalStateMachine.CandidateKind.PROTOCOL_TERMINAL, day.kind);
        assertEquals(ArchiveTraversalStateMachine.CandidateKind.PROTOCOL_TERMINAL, hour.kind);
        assertEquals("E5 7D", day.diagnostic);
        assertEquals("E5 7D", hour.diagnostic);
    }

    @Test public void yearIsStillNotProductEnabled() {
        ArchiveTraversalStateMachine.Candidate candidate = ArchiveFamilyProductionRunner.mapResponse(
                ArchiveFamilyPolicy.forFamily(ArchiveFamilyPeriod.Family.YEAR),
                timeFrame("2026-01-01 00:00", 59_000_000L),
                RETRIEVED);
        assertEquals(ArchiveTraversalStateMachine.CandidateKind.FAILURE, candidate.kind);
        assertEquals(ArchiveFamilySyncState.StopReason.PARSER_ERROR, candidate.failureReason);
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
}
