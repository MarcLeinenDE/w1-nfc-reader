package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Objects;

/** Product bridge that enables conservative incremental traversal without altering baseline paths. */
final class ArchiveIncrementalProductionRunner {
    static final int REQUIRED_KNOWN_OVERLAP = 2;

    private ArchiveIncrementalProductionRunner() { }

    static ArchiveFamilyTransportAdapter.Result run(
            ArchiveFamilyPeriod.Family family,
            ArchiveFamilyTransportAdapter.Wire wire,
            ArchiveFamilyTransportAdapter.DefaultVerifier verifier,
            String expectedMeterId,
            String retrievedAtUtc,
            ArchiveTraversalStateMachine.KnownRecordMatcher knownMatcher,
            ArchiveFamilyTransportAdapter.AcceptedPeriodSink acceptedPeriodSink) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(knownMatcher, "knownMatcher");
        ArchiveFamilyPolicy policy = ArchiveFamilyPolicy.forFamily(family);
        switch (family) {
            case MONTH:
                return ArchiveFamilyTransportAdapter.run(
                        policy,
                        wire,
                        verifier,
                        expectedMeterId,
                        retrievedAtUtc,
                        ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                        knownMatcher,
                        REQUIRED_KNOWN_OVERLAP,
                        ArchiveFamilyTransportAdapter::mapProductionResponse,
                        acceptedPeriodSink);
            case DAY:
            case HOUR:
                return ArchiveFamilyTransportAdapter.run(
                        policy,
                        wire,
                        verifier,
                        expectedMeterId,
                        retrievedAtUtc,
                        ArchiveFamilySyncState.SyncMode.INCREMENTAL,
                        knownMatcher,
                        REQUIRED_KNOWN_OVERLAP,
                        ArchiveFamilyProductionRunner::mapResponse,
                        acceptedPeriodSink);
            default:
                throw new IllegalArgumentException("family is not product-enabled: " + family);
        }
    }
}
