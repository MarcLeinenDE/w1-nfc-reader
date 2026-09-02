package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MonthlyArchiveRestorePolicyTest {
    @Test public void verifiedDefaultIsAuthoritativeAndPreventsRetry() {
        assertTrue(MonthlyArchiveRestorePolicy.restored(true, true));
        assertFalse(MonthlyArchiveRestorePolicy.shouldRetryApplicationReset(true, true, true, 0));
        assertEquals("DEFAULT_VERIFIED", MonthlyArchiveRestorePolicy.status(true, true, true, 0));
    }

    @Test public void failedDefaultVerifyAllowsOneRetryOnlyWithHealthyTransport() {
        assertTrue(MonthlyArchiveRestorePolicy.shouldRetryApplicationReset(true, false, true, 0));
        assertEquals("RETRY_RESET_ONCE", MonthlyArchiveRestorePolicy.status(true, false, true, 0));
        assertFalse(MonthlyArchiveRestorePolicy.shouldRetryApplicationReset(true, false, true, 1));
        assertEquals("RESTORE_UNVERIFIED", MonthlyArchiveRestorePolicy.status(true, false, true, 1));
    }

    @Test public void transportLossNeverTriggersBlindResetRetry() {
        assertFalse(MonthlyArchiveRestorePolicy.shouldRetryApplicationReset(false, false, false, 0));
        assertEquals("RESTORE_UNVERIFIED_TRANSPORT_LOST",
                MonthlyArchiveRestorePolicy.status(false, false, false, 0));
    }
}
