package de.marcleinen.engineeringlab.qalcosonic;

import android.os.SystemClock;
import android.view.View;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.snackbar.Snackbar;

/** Root-only accidental-exit guard for the Overview screen. */
final class DashboardExitGuard {
    private static final long SECOND_BACK_WINDOW_MS = 2000L;

    private DashboardExitGuard() { }

    static void attach(ProductDashboardActivity activity) {
        if (activity == null) return;
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            private long firstBackAt;

            @Override public void handleOnBackPressed() {
                long now = SystemClock.elapsedRealtime();
                if (firstBackAt > 0L && now - firstBackAt <= SECOND_BACK_WINDOW_MS) {
                    setEnabled(false);
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                firstBackAt = now;
                View anchor = activity.findViewById(android.R.id.content);
                if (anchor != null) {
                    Snackbar.make(anchor, R.string.v2_back_again_to_exit, Snackbar.LENGTH_SHORT).show();
                }
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, callback);
    }
}
