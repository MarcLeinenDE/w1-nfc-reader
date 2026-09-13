package de.marcleinen.engineeringlab.qalcosonic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ProductUiHardeningTest {
    @Test public void numericDateTimeGetsCenteredSeparator() {
        assertEquals("Lokale Zeit: 11.09.2026 · 19:50",
                ProductUiHardening.normalizeDateTimeSeparators(
                        "Lokale Zeit: 11.09.2026 19:50"));
    }

    @Test public void alreadyNormalizedDateTimeStaysStable() {
        assertEquals("Zählerzeit: 11.09.2026 · 18:55",
                ProductUiHardening.normalizeDateTimeSeparators(
                        "Zählerzeit: 11.09.2026 · 18:55"));
    }

    @Test public void englishMonthDateTimeGetsCenteredSeparator() {
        assertEquals("Last read: Sep 11, 2026 · 7:50 PM",
                ProductUiHardening.normalizeDateTimeSeparators(
                        "Last read: Sep 11, 2026, 7:50 PM"));
    }
}
