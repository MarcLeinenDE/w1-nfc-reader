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
        UiPreferences.applyThemeMode(this);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        if (this instanceof ProductDashboardActivity) {
            LiveShareAction.attach(this);
        }
        if (this instanceof ProductDashboardActivity
                || this instanceof HistoryStatisticsActivity
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
