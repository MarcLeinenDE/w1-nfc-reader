package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class ArchivePassiveTimeEvidenceTest {
    private Context context;
    private ArchiveFamilyStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
        store = new ArchiveFamilyStore(context);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        context.deleteDatabase(ArchiveFamilyStore.DB_NAME);
    }

    @Test public void onTimeWinsIdentityWhileRawTypeFAndFlagsRemainLosslessEvidence() {
        MeterTimeEvidence time = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 7, 1, 9, 15, false, true),
                onTimeRecord(1234L)));
        assertTrue(time.typeFPresent);
        assertEquals(Long.valueOf(1234L), time.onTimeSeconds);

        ArchiveFamilyPeriod period = period("2026-07-01 09:15", "1234 s", time);
        assertEquals("OT:1234", period.occurrenceKey());
        assertEquals("0F 89 41 37", period.rawTypeFHex);
        assertEquals(Integer.valueOf(0), period.typeFIv);
        assertEquals(Integer.valueOf(1), period.typeFSu);

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", period));
        ArchiveFamilyStore.StoredPeriod stored = store.getPeriods(
                "M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals("OT:1234", stored.occurrenceKey);
        assertEquals(Long.valueOf(1234L), stored.onTimeSeconds);
        assertEquals("0F 89 41 37", stored.rawTypeFHex);
        assertEquals(Integer.valueOf(0), stored.typeFIv);
        assertEquals(Integer.valueOf(1), stored.typeFSu);
    }

    @Test public void typeFBecomesFallbackOccurrenceWhenOnTimeIsAbsent() {
        MeterTimeEvidence time = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 1, 2, 3, 4, true, false)));
        assertTrue(time.typeFPresent);
        assertNull(time.onTimeSeconds);

        ArchiveFamilyPeriod period = period("2026-01-02 03:04", null, time);
        assertEquals("TF:84034231", period.occurrenceKey());
        assertEquals(Integer.valueOf(1), period.typeFIv);
        assertEquals(Integer.valueOf(0), period.typeFSu);

        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", period));
        ArchiveFamilyStore.StoredPeriod stored = store.getPeriods(
                "M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals("TF:84034231", stored.occurrenceKey);
        assertNull(stored.onTimeSeconds);
        assertEquals("84 03 42 31", stored.rawTypeFHex);
        assertEquals(Integer.valueOf(1), stored.typeFIv);
        assertEquals(Integer.valueOf(0), stored.typeFSu);
    }

    @Test public void laterIdenticalObservationCanEnrichTypeFWithoutCreatingContentConflict() {
        ArchiveFamilyPeriod legacyLike = period("2026-07-01 09:15", "1234 s", null);
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.INSERTED, store.upsert("M1", legacyLike));
        ArchiveFamilyStore.StoredPeriod first = store.getPeriods(
                "M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals("OT:1234", first.occurrenceKey);
        assertNull(first.rawTypeFHex);

        MeterTimeEvidence time = MeterTimeEvidence.inspect(frame(
                typeFRecord(2026, 7, 1, 9, 15, false, true),
                onTimeRecord(1234L)));
        ArchiveFamilyPeriod enriched = period("2026-07-01 09:15", "1234 s", time);
        assertEquals(ArchivePersistenceCoordinator.WriteOutcome.CONFIRMED_IDENTICAL,
                store.upsert("M1", enriched));

        ArchiveFamilyStore.StoredPeriod stored = store.getPeriods(
                "M1", ArchiveFamilyPeriod.Family.HOUR).get(0);
        assertEquals(1, store.getPeriods("M1", ArchiveFamilyPeriod.Family.HOUR).size());
        assertEquals(2, stored.observationCount);
        assertEquals(0, stored.revisionCount);
        assertEquals(0, stored.conflictFlags);
        assertEquals("0F 89 41 37", stored.rawTypeFHex);
        assertEquals(Integer.valueOf(0), stored.typeFIv);
        assertEquals(Integer.valueOf(1), stored.typeFSu);
    }

    private static ArchiveFamilyPeriod period(
            String rawLoggerTime, String onTimeDisplay, MeterTimeEvidence timeEvidence) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder()
                .totalVolume("10.000 m3")
                .batteryPercent("90 %");
        values.onTime = onTimeDisplay;
        return new ArchiveFamilyPeriod(
                ArchiveFamilyPeriod.Family.HOUR,
                rawLoggerTime,
                "2026-07-01T09:16:00Z",
                "FP-HOUR",
                "NFC_ARCHIVE",
                "COMPLETE",
                values.build(),
                timeEvidence);
    }

    private static byte[] typeFRecord(
            int year, int month, int day, int hour, int minute, boolean invalid, boolean summer) {
        int offset = year - 2000;
        int b0 = (minute & 0x3F) | (invalid ? 0x80 : 0);
        int b1 = (hour & 0x1F) | (summer ? 0x80 : 0);
        int b2 = (day & 0x1F) | ((offset & 0x07) << 5);
        int b3 = (month & 0x0F) | ((offset & 0x78) << 1);
        return new byte[]{0x04, 0x6D, (byte)b0, (byte)b1, (byte)b2, (byte)b3};
    }

    private static byte[] onTimeRecord(long seconds) {
        return new byte[]{
                0x04, 0x20,
                (byte)(seconds & 0xFF),
                (byte)((seconds >> 8) & 0xFF),
                (byte)((seconds >> 16) & 0xFF),
                (byte)((seconds >> 24) & 0xFF)};
    }

    private static byte[] frame(byte[]... records) {
        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream();
        for (byte[] record : records) recordBytes.write(record, 0, record.length);
        byte[] payload = recordBytes.toByteArray();
        int l = 3 + 12 + payload.length;
        byte[] frame = new byte[l + 6];
        frame[0] = 0x68;
        frame[1] = (byte)l;
        frame[2] = (byte)l;
        frame[3] = 0x68;
        frame[4] = 0x08;
        frame[5] = (byte)0xFE;
        frame[6] = 0x72;
        System.arraycopy(payload, 0, frame, 19, payload.length);
        int checksum = 0;
        for (int i = 4; i < 4 + l; i++) checksum = (checksum + (frame[i] & 0xFF)) & 0xFF;
        frame[4 + l] = (byte)checksum;
        frame[5 + l] = 0x16;
        return frame;
    }
}
