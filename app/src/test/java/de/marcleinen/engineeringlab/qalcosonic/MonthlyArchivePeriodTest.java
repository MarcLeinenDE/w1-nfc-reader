package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class MonthlyArchivePeriodTest {
    @Test public void identityKeepsLoggerAndRetrievalTimeDistinct() {
        MonthlyArchivePeriod p = new MonthlyArchivePeriod(
                "2026-08-01 00:00",
                "2026-08-31T11:07:12Z",
                "fp",
                null);
        assertEquals("2026-08-01 00:00", p.loggerTimestamp);
        assertEquals("2026-08-31T11:07:12Z", p.retrievedAtUtc);
        assertEquals("MONTH|2026-08-01 00:00", p.periodKeyWithinMeter());
        assertEquals(MonthlyArchivePeriod.SOURCE_NFC_ARCHIVE, p.source);
        assertEquals(MonthlyArchivePeriod.VALIDATION_COMPLETE, p.validation);
    }

    @Test public void requiredIdentityAndFingerprintCannotBeEmpty() {
        assertInvalid(null, "fp");
        assertInvalid("", "fp");
        assertInvalid("2026-08-01 00:00", null);
        assertInvalid("2026-08-01 00:00", "");
    }

    private static void assertInvalid(String timestamp, String fp) {
        try {
            new MonthlyArchivePeriod(timestamp, null, fp, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
