package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MeterStatusPresentationTest {
    @Test public void knownLeakageBitIsDecodedWithoutUnknownFlag() {
        MeterStatusPresentation.Historical status = MeterStatusPresentation.historical("0x00000200");
        assertTrue(status.hasAnyStatus());
        assertFalse(status.hasUnknownBits);
        assertEquals("0x00000200", status.raw);
        assertEquals(1, status.knownLabelResources.size());
        assertEquals(R.string.m3_alarm_leakage, (int) status.knownLabelResources.get(0));
    }

    @Test public void knownCalculatorHardwareBitIsDecoded() {
        MeterStatusPresentation.Historical status = MeterStatusPresentation.historical("0x00000010");
        assertFalse(status.hasUnknownBits);
        assertEquals(R.string.m3_alarm_calculator_hardware,
                (int) status.knownLabelResources.get(0));
    }

    @Test public void unknownNonZeroBitStaysUnknownAndPreservesRawCode() {
        MeterStatusPresentation.Historical status = MeterStatusPresentation.historical("0x00000001");
        assertTrue(status.hasAnyStatus());
        assertTrue(status.hasUnknownBits);
        assertTrue(status.knownLabelResources.isEmpty());
        assertEquals("0x00000001", status.raw);
    }

    @Test public void zeroStatusIsNotPresentedAsHistoricalWarning() {
        MeterStatusPresentation.Historical status = MeterStatusPresentation.historical("0x00000000");
        assertFalse(status.hasAnyStatus());
        assertFalse(status.hasUnknownBits);
    }
}
