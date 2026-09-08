package de.marcleinen.engineeringlab.qalcosonic;

/** Lossless time evidence associated with one native archive period. */
final class ArchiveTimeEvidence {
    final String meterId;
    final ArchiveFamilyPeriod.Family family;
    final String rawLoggerWallClock;
    final String rawTypeFHex;
    final boolean invalidTime;
    final boolean summerTime;
    final Long onTimeSeconds;

    ArchiveTimeEvidence(
            String meterId,
            ArchiveFamilyPeriod.Family family,
            String rawLoggerWallClock,
            String rawTypeFHex,
            boolean invalidTime,
            boolean summerTime,
            Long onTimeSeconds) {
        this.meterId = meterId == null ? null : meterId.trim();
        this.family = family;
        this.rawLoggerWallClock = rawLoggerWallClock;
        this.rawTypeFHex = rawTypeFHex;
        this.invalidTime = invalidTime;
        this.summerTime = summerTime;
        this.onTimeSeconds = onTimeSeconds;
    }
}
