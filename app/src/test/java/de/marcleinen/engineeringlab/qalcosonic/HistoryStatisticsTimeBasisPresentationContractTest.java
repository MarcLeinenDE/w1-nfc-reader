package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level guard for the global LOCAL/METER presentation contract. */
public final class HistoryStatisticsTimeBasisPresentationContractTest {
    private static final String ACTIVITY =
            "app/src/main/java/de/marcleinen/engineeringlab/qalcosonic/HistoryStatisticsActivity.java";

    @Test public void statisticsUsesSelectedTimeBasisExpectedBuckets() throws Exception {
        String source = TestSource.read(ACTIVITY);
        assertTrue(source.contains("int expected = repository.expectedBuckets(window, effective);"));
        assertFalse(source.contains("int expected = window.customRange\n"
                + "                ? HistoryCustomRangeSemantics.expectedFullBuckets(window, effective)\n"
                + "                : window.expectedBuckets;"));
    }

    @Test public void returningFromSettingsRerendersWhenTimeBasisChanged() throws Exception {
        String source = TestSource.read(ACTIVITY);
        assertTrue(source.contains("private AppTimeBasis renderedTimeBasis;"));
        assertTrue(source.contains("current != renderedTimeBasis"));
        assertTrue(source.contains("renderedTimeBasis = UiPreferences.getTimeBasis(this);"));
    }

    @Test public void localHistoryShowsRawMeterTimeAsSecondaryEvidence() throws Exception {
        String source = TestSource.read(ACTIVITY);
        assertTrue(source.contains("UiPreferences.getTimeBasis(this) == AppTimeBasis.LOCAL"));
        assertTrue(source.contains("observation.meterTime"));
        assertTrue(source.contains("R.string.m3_meter_time"));
        assertTrue(source.contains("HistoryTimePresentation.formatArchivePeriod("));
    }
}
