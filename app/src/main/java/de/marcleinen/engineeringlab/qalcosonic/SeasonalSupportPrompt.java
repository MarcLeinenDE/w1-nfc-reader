package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

/**
 * Minimal local state for the optional once-per-Christmas-season support prompt.
 *
 * <p>The prompt is eligible only after a successful normal Live read in the same Christmas
 * window, never appears in that same process after the read, and is shown only on a later normal
 * launcher cold start. Showing the prompt consumes the season cap, regardless of the button
 * chosen. No payment status is queried or stored.</p>
 */
final class SeasonalSupportPrompt {
    static final int NONE = Integer.MIN_VALUE;
    private static final String PREFS = "seasonal_support_prompt";
    private static final String KEY_ELIGIBLE_YEAR = "christmas_eligible_year";
    private static final String KEY_HANDLED_YEAR = "christmas_handled_year";

    private static volatile int liveReadEligibleYearThisProcess = NONE;
    private static volatile boolean dashboardSeenThisProcess;

    private SeasonalSupportPrompt() { }

    static void recordSuccessfulNormalLive(Context context, long readAtMs) {
        if (context == null || readAtMs <= 0L) return;
        int year = christmasYear(readAtMs);
        if (year == NONE) return;
        prefs(context).edit().putInt(KEY_ELIGIBLE_YEAR, year).commit();
        liveReadEligibleYearThisProcess = year;
    }

    static void attach(ProductDashboardActivity activity) {
        if (activity == null || !claimFirstDashboardInProcess()
                || !isNormalLauncherIntent(activity.getIntent())) return;

        long nowMs = System.currentTimeMillis();
        int year = christmasYear(nowMs);
        if (year == NONE) return;

        SharedPreferences prefs = prefs(activity);
        int eligibleYear = prefs.getInt(KEY_ELIGIBLE_YEAR, NONE);
        int handledYear = prefs.getInt(KEY_HANDLED_YEAR, NONE);
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        if (!shouldShow(year, now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),
                eligibleYear, handledYear, liveReadEligibleYearThisProcess)) return;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.v2_christmas_support_title)
                .setMessage(R.string.v2_christmas_support_body)
                .setPositiveButton(R.string.v2_christmas_support_positive, (dialog, which) -> {
                    if (!DeveloperSupport.openExternal(activity)) {
                        Snackbar.make(activity.getWindow().getDecorView(),
                                R.string.about080_open_link_failed, Snackbar.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.v2_christmas_support_negative, null)
                .show();

        // Frequency cap is consumed by displaying the prompt, not by a payment outcome. This keeps
        // dismissal, "No thanks" and opening the external browser equivalent for the season.
        prefs.edit().putInt(KEY_HANDLED_YEAR, year).commit();
    }

    static boolean isNormalLauncherIntent(Intent intent) {
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    static int christmasYear(long atMs) {
        if (atMs <= 0L) return NONE;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(atMs);
        return isChristmasWindow(calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                ? calendar.get(Calendar.YEAR) : NONE;
    }

    static boolean isChristmasWindow(int month0, int dayOfMonth) {
        return month0 == Calendar.DECEMBER && dayOfMonth >= 1 && dayOfMonth <= 26;
    }

    static boolean shouldShow(int currentYear, int month0, int dayOfMonth,
                              int eligibleYear, int handledYear, int eligibleThisProcessYear) {
        return isChristmasWindow(month0, dayOfMonth)
                && eligibleYear == currentYear
                && handledYear != currentYear
                && eligibleThisProcessYear != currentYear;
    }

    static synchronized boolean claimFirstDashboardInProcess() {
        if (dashboardSeenThisProcess) return false;
        dashboardSeenThisProcess = true;
        return true;
    }

    static synchronized void resetProcessEligibilityForTest() {
        liveReadEligibleYearThisProcess = NONE;
        dashboardSeenThisProcess = false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
