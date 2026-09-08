package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class SeasonalSupportPromptTest {
    @Before public void setUp() {
        SeasonalSupportPrompt.resetProcessEligibilityForTest();
    }

    @After public void tearDown() {
        SeasonalSupportPrompt.resetProcessEligibilityForTest();
    }

    @Test public void christmasWindowIsDecemberFirstThroughTwentySixthInclusive() {
        assertFalse(SeasonalSupportPrompt.isChristmasWindow(Calendar.NOVEMBER, 30));
        assertTrue(SeasonalSupportPrompt.isChristmasWindow(Calendar.DECEMBER, 1));
        assertTrue(SeasonalSupportPrompt.isChristmasWindow(Calendar.DECEMBER, 26));
        assertFalse(SeasonalSupportPrompt.isChristmasWindow(Calendar.DECEMBER, 27));
        assertFalse(SeasonalSupportPrompt.isChristmasWindow(Calendar.JANUARY, 1));
    }

    @Test public void eligibilityRequiresReadInSameSeasonAndNeverShowsInSameProcess() {
        int year = 2026;
        assertFalse(SeasonalSupportPrompt.shouldShow(year, Calendar.DECEMBER, 10,
                SeasonalSupportPrompt.NONE, SeasonalSupportPrompt.NONE, SeasonalSupportPrompt.NONE));
        assertFalse(SeasonalSupportPrompt.shouldShow(year, Calendar.DECEMBER, 10,
                year, SeasonalSupportPrompt.NONE, year));
        assertTrue(SeasonalSupportPrompt.shouldShow(year, Calendar.DECEMBER, 10,
                year, SeasonalSupportPrompt.NONE, SeasonalSupportPrompt.NONE));
    }

    @Test public void handledSeasonAndOutsideWindowNeverShowAgain() {
        int year = 2026;
        assertFalse(SeasonalSupportPrompt.shouldShow(year, Calendar.DECEMBER, 10,
                year, year, SeasonalSupportPrompt.NONE));
        assertFalse(SeasonalSupportPrompt.shouldShow(year, Calendar.DECEMBER, 27,
                year, SeasonalSupportPrompt.NONE, SeasonalSupportPrompt.NONE));
    }

    @Test public void followingChristmasCanBecomeEligibleAgain() {
        assertTrue(SeasonalSupportPrompt.shouldShow(2027, Calendar.DECEMBER, 10,
                2027, 2026, SeasonalSupportPrompt.NONE));
        assertFalse(SeasonalSupportPrompt.shouldShow(2027, Calendar.DECEMBER, 10,
                2026, 2026, SeasonalSupportPrompt.NONE));
    }

    @Test public void dashboardColdStartIsClaimedOnlyOncePerProcess() {
        assertTrue(SeasonalSupportPrompt.claimFirstDashboardInProcess());
        assertFalse(SeasonalSupportPrompt.claimFirstDashboardInProcess());
    }

    @Test public void onlyNormalLauncherIntentCountsAsPromptColdStart() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        assertTrue(SeasonalSupportPrompt.isNormalLauncherIntent(launcher));
        assertFalse(SeasonalSupportPrompt.isNormalLauncherIntent(new Intent(Intent.ACTION_VIEW)));
        assertFalse(SeasonalSupportPrompt.isNormalLauncherIntent(new Intent()));
        assertFalse(SeasonalSupportPrompt.isNormalLauncherIntent(null));
    }

    @Test public void supportLinkIsExternalActionViewOnly() {
        Intent intent = DeveloperSupport.browserIntent();
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://www.paypal.me/ccaa/", intent.getDataString());
    }
}
