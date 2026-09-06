package de.marcleinen.engineeringlab.qalcosonic;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * Transitional v2 UI cleanup.
 *
 * <p>The old dashboard still contains the pre-family History action internally while the new
 * Settings-based family flow is being physically validated. Keep that legacy action unreachable
 * in the UI so there is only one user-facing History synchronization entry point. Delete this
 * helper together with the old dashboard action code after the migration is complete.</p>
 */
final class LegacyOverviewHistoryActionHider {
    private LegacyOverviewHistoryActionHider() {}

    static void hide(Activity activity) {
        if (activity == null) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        String actionText = activity.getString(R.string.m3_sync_history);
        String hintText = activity.getString(R.string.m3_calculated_note);
        hideFirstAction(root, actionText, hintText);
    }

    private static boolean hideFirstAction(View view, String actionText, String hintText) {
        if (view instanceof MaterialButton) {
            CharSequence text = ((MaterialButton) view).getText();
            if (text != null && actionText.contentEquals(text)) {
                view.setVisibility(View.GONE);
                View parent = (View) view.getParent();
                if (parent instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) parent;
                    int index = group.indexOfChild(view);
                    if (index >= 0 && index + 1 < group.getChildCount()) {
                        View next = group.getChildAt(index + 1);
                        if (next instanceof TextView) {
                            CharSequence nextText = ((TextView) next).getText();
                            if (nextText != null && hintText.contentEquals(nextText)) {
                                next.setVisibility(View.GONE);
                            }
                        }
                    }
                }
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hideFirstAction(group.getChildAt(i), actionText, hintText)) return true;
            }
        }
        return false;
    }
}
