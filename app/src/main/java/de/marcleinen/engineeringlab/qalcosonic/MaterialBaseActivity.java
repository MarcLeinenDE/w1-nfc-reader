package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shared presentation-only base for the 0.8.0 Material 3 surface. */
abstract class MaterialBaseActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiPreferences.wrapContext(newBase));
    }

    @Override protected void onCreate(Bundle state) {
        // Theme.Material3.DayNight follows AppCompat's night mode. Apply the persisted app choice
        // before AppCompat creates the Activity decor so Light/Dark is deterministic on real phones.
        UiPreferences.applyThemeMode(this);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        if (this instanceof ProductDashboardActivity) {
            LiveShareAction.attach(this);
            // Transitional v2 cleanup: the only user-facing History synchronization entry point is
            // Settings -> History synchronization. Remove this helper when the legacy dashboard
            // action code itself is deleted after the family migration.
            LegacyOverviewHistoryActionHider.hide(this);
        }
        if (this instanceof ProductDashboardActivity
                || this instanceof MeterDetailsActivity
                || this instanceof SettingsActivity) {
            ProductDrawerNavigation.attach(this);
        }
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
        ViewCompat.requestApplyInsets(root);
    }
}
