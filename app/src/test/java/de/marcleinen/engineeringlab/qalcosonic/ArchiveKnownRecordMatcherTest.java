package de.marcleinen.engineeringlab.qalcosonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ArchiveKnownRecordMatcherTest {
    private static final String METER = "12345678";
    private static final ArchiveFamilyPeriod.Family FAMILY = ArchiveFamilyPeriod.Family.HOUR;

    private Application app;
    private ArchiveFamilyStore store;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        DataPortability.clearMeterData(app);
        store = new ArchiveFamilyStore(app);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        DataPortability.clearMeterData(app);
    }

    @Test public void exactCanonicalRecordWithTypedOnTimeIsSecurelyKnown() {
        ArchiveFamilyPeriod known = period("2026-09-07 10:00", "12.345 m3", "100000 s", "shape-a");
        store.upsert(METER, known);

        ArchiveKnownRecordMatcher matcher = new ArchiveKnownRecordMatcher(store, METER, FAMILY);

        assertEquals(1, matcher.snapshotSize());
        assertTrue(matcher.securelyKnown(evidence(known, 100_000L)));
    }

    @Test public void timestampOnlyOrWrongOnTimeNeverQualifies() {
        ArchiveFamilyPeriod known = period("2026-09-07 10:00", "12.345 m3", "100000 s", "shape-a");
        store.upsert(METER, known);
        ArchiveKnownRecordMatcher matcher = new ArchiveKnownRecordMatcher(store, METER, FAMILY);

        ArchiveFamilyPeriod changedContent = period(
                "2026-09-07 10:00", "12.346 m3", "100000 s", "shape-a");
        assertFalse(matcher.securelyKnown(evidence(changedContent, 100_000L)));
        assertFalse(matcher.securelyKnown(evidence(known, 100_001L)));
    }

    @Test public void storedConflictDisqualifiesOverlapAnchor() {
        ArchiveFamilyPeriod original = period(
                "2026-09-07 10:00", "12.345 m3", "100000 s", "shape-a");
        store.upsert(METER, original);
        store.upsert(METER, period(
                "2026-09-07 10:00", "12.346 m3", "100000 s", "shape-a"));

        ArchiveKnownRecordMatcher matcher = new ArchiveKnownRecordMatcher(store, METER, FAMILY);

        assertFalse(matcher.securelyKnown(evidence(original, 100_000L)));
    }

    @Test public void matcherSnapshotCannotLearnRecordsInsertedByCurrentAttempt() {
        ArchiveFamilyPeriod old = period(
                "2026-09-07 09:00", "12.300 m3", "96400 s", "shape-a");
        store.upsert(METER, old);
        ArchiveKnownRecordMatcher matcher = new ArchiveKnownRecordMatcher(store, METER, FAMILY);

        ArchiveFamilyPeriod newlyInserted = period(
                "2026-09-07 10:00", "12.345 m3", "100000 s", "shape-a");
        store.upsert(METER, newlyInserted);

        assertEquals(1, matcher.snapshotSize());
        assertFalse(matcher.securelyKnown(evidence(newlyInserted, 100_000L)));
        assertTrue(matcher.securelyKnown(evidence(old, 96_400L)));
    }

    @Test public void durationUnitsAreConvertedExactlyToSeconds() {
        assertEquals(Long.valueOf(12L), ArchiveKnownRecordMatcher.durationSeconds("12 s"));
        assertEquals(Long.valueOf(120L), ArchiveKnownRecordMatcher.durationSeconds("2 min"));
        assertEquals(Long.valueOf(10_800L), ArchiveKnownRecordMatcher.durationSeconds("3 h"));
        assertEquals(Long.valueOf(172_800L), ArchiveKnownRecordMatcher.durationSeconds("2 d"));
        assertEquals(2, ArchiveIncrementalProductionRunner.REQUIRED_KNOWN_OVERLAP);
    }

    private static ArchiveFamilyPeriod period(
            String timestamp,
            String totalVolume,
            String onTime,
            String structure) {
        ArchiveNormalizedValues.Builder values = ArchiveNormalizedValues.builder();
        values.totalVolume(totalVolume);
        values.onTime = onTime;
        values.batteryPercent("88 %");
        values.errorFlags("0x00000000");
        return new ArchiveFamilyPeriod(
                FAMILY,
                timestamp,
                "2026-09-07T10:30:00Z",
                structure,
                "NFC_ARCHIVE",
                "COMPLETE",
                values.build());
    }

    private static ArchiveTraversalStateMachine.PeriodEvidence evidence(
            ArchiveFamilyPeriod period,
            long onTimeSeconds) {
        return new ArchiveTraversalStateMachine.PeriodEvidence(
                period,
                onTimeSeconds,
                true,
                false);
    }
}
