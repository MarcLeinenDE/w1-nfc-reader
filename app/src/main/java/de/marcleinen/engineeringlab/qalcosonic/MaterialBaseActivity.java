package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shared presentation-only base for the Material 3 product surface. */
abstract class MaterialBaseActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiPreferences.wrapContext(newBase));
    }

    @Override protected void onCreate(Bundle state) {
        // v2.1 product contract: the normal UI has one canonical real timeline. Archive time is
        // reconstructed from verified Live anchors + ON_TIME and then projected through the meter's
        // persisted IANA zone. Raw meter/logger wall-clock values remain preserved secondary
        // evidence, but an old/restored METER preference must not make the normal product surface
        // present that drifting raw clock as an alternative primary timeline.
        if (UiPreferences.getTimeBasis(this) != AppTimeBasis.LOCAL) {
            UiPreferences.setTimeBasis(this, AppTimeBasis.LOCAL);
        }
        UiPreferences.applyThemeMode(this);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        if (this instanceof ProductDashboardActivity) {
            LiveShareAction.attach(this);
            // Register before the drawer callback. The later drawer callback therefore wins while
            // the navigation drawer is open; otherwise Overview gets the double-back exit guard.
            DashboardExitGuard.attach((ProductDashboardActivity) this);
            SeasonalSupportPrompt.attach((ProductDashboardActivity) this);
        }
        if (this instanceof ProductDashboardActivity
                || this instanceof HistoryStatisticsActivity
                || this instanceof MeterDetailsActivity
                || this instanceof SettingsActivity) {
            ProductDrawerNavigation.attach(this);
        }
        ProductUiHardening.attach(this);
    }

    protected final void applySystemInsets(View root) {
        final int startLeft = root.getPaddingLeft();
        final int startTop = root.getPaddingTop();
        final int startRight = root.getPaddingRight();
        final int startBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    startLeft + bars.left,
                    startTop + bars.top,
                    startRight + bars.right,
                    startBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyWindowInsets(root);
    }
}
