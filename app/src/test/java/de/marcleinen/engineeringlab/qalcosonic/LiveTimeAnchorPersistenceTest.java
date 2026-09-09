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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class LiveTimeAnchorPersistenceTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @After public void tearDown() {
        context.deleteDatabase(MeterTimeModelStore.DB_NAME);
    }

    @Test public void candidateUsesOnlyAlreadyReceivedVerifiedLiveResponse() {
        byte[] response = frame(
                typeFRecord(2026, 10, 25, 2, 30, false, true),
                onTimeRecord(12_345L));
        QalcosonicReader.Readout readout = new QalcosonicReader.Readout(
                new byte[8], "TEST", 0, 0, new byte[]{(byte)0xE5}, response, "");

        VerifiedLiveTimeAnchor anchor = LiveTimeAnchorPersistence.candidate(
                "00000000", 1_000L, 1_200L, readout);

        assertNotNull(anchor);
        assertEquals("00000000", anchor.meterId);
        assertEquals(1_100L, anchor.anchorEpochMs);
        assertEquals(100L, anchor.uncertaintyMs);
        assertEquals("2026-10-25 02:30", anchor.rawMeterWallClock);
        assertEquals(Long.valueOf(12_345L), anchor.onTimeSeconds);
        assertTrue(anchor.summerTime);
        assertFalse(anchor.invalidTime);
    }

    @Test public void candidateRejectsMeterMismatchBeforeAnythingCanBePersisted() {
        byte[] response = frame(
                typeFRecord(2026, 10, 25, 2, 30, false, false),
                onTimeRecord(12_345L));
        QalcosonicReader.Readout readout = new QalcosonicReader.Readout(
                new byte[8], "TEST", 0, 0, new byte[]{(byte)0xE5}, response, "");

        assertNull(LiveTimeAnchorPersistence.candidate(
                "OTHER-METER", 1_000L, 1_200L, readout));
    }

    @Test public void firstAcceptedAnchorPinsMeterZoneAndLaterDeviceZoneDoesNotOverwriteIt() {
        VerifiedLiveTimeAnchor first = new VerifiedLiveTimeAnchor(
                "M1", 10_000L, 10_200L,
                "2026-01-02 03:04", "04 03 42 31", false, false, 1_000L);
        VerifiedLiveTimeAnchor second = new VerifiedLiveTimeAnchor(
                "M1", 20_000L, 20_200L,
                "2026-01-02 03:05", "05 03 42 31", false, false, 1_060L);

        assertTrue(LiveTimeAnchorPersistence.persist(context, first, "Europe/Berlin"));
        assertTrue(LiveTimeAnchorPersistence.persist(context, second, "America/New_York"));

        MeterTimeModelStore store = new MeterTimeModelStore(context);
        try {
            MeterTimeModelStore.Profile profile = store.getProfile("M1");
            assertNotNull(profile);
            assertEquals("Europe/Berlin", profile.zoneId);
            assertEquals(MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE,
                    profile.source);
            assertEquals(2, store.anchors("M1").size());
            assertEquals(20_100L, store.latestAnchor("M1").anchorEpochMs);
        } finally {
            store.close();
        }
    }

    @Test public void absentCandidateCreatesNeitherProfileNorAnchor() {
        assertFalse(LiveTimeAnchorPersistence.persist(context, null, "Europe/Berlin"));
        MeterTimeModelStore store = new MeterTimeModelStore(context);
        try {
            assertNull(store.getProfile("M1"));
            assertTrue(store.anchors("M1").isEmpty());
        } finally {
            store.close();
        }
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
