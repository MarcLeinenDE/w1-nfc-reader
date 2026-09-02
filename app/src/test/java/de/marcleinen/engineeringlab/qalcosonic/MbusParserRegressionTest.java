package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class MbusParserRegressionTest {
    private static final byte[] SYNTHETIC_REAL_DEVICE_SHAPE = hex(
            "68 58 58 68 08 0A 72 78 56 34 12 09 07 0A 07 1F 00 00 00 "
                    + "04 6D 04 03 42 31 "
                    + "34 FD 17 00 00 00 00 "
                    + "04 24 4E 61 BC 00 "
                    + "04 20 20 4B BC 00 "
                    + "04 FF 20 1F 00 00 00 "
                    + "04 13 40 E2 01 00 "
                    + "04 93 3B 40 E2 01 00 "
                    + "04 93 3C 05 00 00 00 "
                    + "02 3B 7D 00 "
                    + "02 59 53 07 "
                    + "02 66 D7 00 "
                    + "02 FB 1B 00 00 "
                    + "01 FD 74 4D B7 16");

    @Test
    public void parsesSyntheticFrameMatchingValidatedRealDeviceShape() throws Exception {
        MbusParser.MeterData meter = MbusParser.parse(SYNTHETIC_REAL_DEVICE_SHAPE);

        assertEquals("12345678", meter.meterId);
        assertEquals("AXI", meter.manufacturer);
        assertEquals(Integer.valueOf(10), meter.meterVersion);
        assertEquals(123.456, meter.waterUsageM3, 0.000001);
        assertEquals(123.456, meter.positiveUsageM3, 0.000001);
        assertEquals(0.005, meter.negativeUsageM3, 0.000001);
        assertEquals(0.125, meter.flowM3h, 0.000001);
        assertEquals(18.75, meter.waterTemperatureC, 0.000001);
        assertEquals(21.5, meter.externalTemperatureC, 0.000001);
        assertEquals(Integer.valueOf(77), meter.batteryPercent);
        assertEquals(Long.valueOf(12_345_678L), meter.operatingTimeSeconds);
        assertEquals(Long.valueOf(12_340_000L), meter.onTimeSeconds);
        assertEquals("2026-01-02 03:04", meter.timepoint);
        assertEquals("00 00 00 00", meter.errorFlagsRaw);
        assertTrue(meter.getErrors().isEmpty());
        assertEquals(94, meter.rawFrame.length);
    }

    @Test
    public void ignoresTrailingMailboxByteLikeValidatedRealReadout() throws Exception {
        byte[] mailboxPayload = Arrays.copyOf(
                SYNTHETIC_REAL_DEVICE_SHAPE,
                SYNTHETIC_REAL_DEVICE_SHAPE.length + 1);
        mailboxPayload[mailboxPayload.length - 1] = 0x00;

        MbusParser.MeterData meter = MbusParser.parse(mailboxPayload);

        assertEquals("12345678", meter.meterId);
        assertEquals(94, meter.rawFrame.length);
        assertEquals(123.456, meter.waterUsageM3, 0.000001);
    }

    @Test
    public void rejectsChecksumRegression() throws Exception {
        byte[] broken = SYNTHETIC_REAL_DEVICE_SHAPE.clone();
        broken[53] ^= 0x01;

        try {
            MbusParser.parse(broken);
            fail("Expected checksum failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("MBUS_CHECKSUM_MISMATCH:"));
        }
    }

    private static byte[] hex(String value) {
        String compact = value.replace(" ", "").replace("\n", "");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
