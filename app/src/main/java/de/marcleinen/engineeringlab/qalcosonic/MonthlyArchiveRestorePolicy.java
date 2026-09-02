package de.marcleinen.engineeringlab.qalcosonic;

/** Pure decision rules derived from physical 0.7.20 restore/default verification. */
final class MonthlyArchiveRestorePolicy {
    private MonthlyArchiveRestorePolicy() {}

    static boolean restored(boolean defaultHealthy, boolean protectedFingerprintMatch) {
        return defaultHealthy && protectedFingerprintMatch;
    }

    static boolean shouldRetryApplicationReset(
            boolean defaultHealthy,
            boolean protectedFingerprintMatch,
            boolean transportHealthy,
            int resetRetriesAlreadyUsed) {
        if (restored(defaultHealthy, protectedFingerprintMatch)) return false;
        return transportHealthy && resetRetriesAlreadyUsed == 0;
    }

    static String status(
            boolean defaultHealthy,
            boolean protectedFingerprintMatch,
            boolean transportHealthy,
            int resetRetriesAlreadyUsed) {
        if (restored(defaultHealthy, protectedFingerprintMatch)) return "DEFAULT_VERIFIED";
        if (shouldRetryApplicationReset(defaultHealthy, protectedFingerprintMatch,
                transportHealthy, resetRetriesAlreadyUsed)) return "RETRY_RESET_ONCE";
        return transportHealthy ? "RESTORE_UNVERIFIED" : "RESTORE_UNVERIFIED_TRANSPORT_LOST";
    }
}
