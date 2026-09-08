package de.marcleinen.engineeringlab.qalcosonic;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/** Small presentation-only release-hardening fixes shared across the v2 product surface. */
final class ProductUiHardening {
    private ProductUiHardening() { }

    static void attach(MaterialBaseActivity activity) {
        if (activity == null) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        if (activity instanceof HistoryStatisticsActivity) {
            attachHistoryControls((HistoryStatisticsActivity) activity, root);
        }
        if (activity instanceof SettingsActivity) {
            root.post(() -> replaceExactText(root,
                    activity.getString(R.string.m3_history_subtitle),
                    activity.getString(R.string.v2_sync_settings_summary)));
        }
        if (activity instanceof AboutActivity) {
            root.post(() -> {
                replaceExactText(root,
                        activity.getString(R.string.about080_privacy_sharing_body),
                        activity.getString(R.string.v2_about_privacy_sharing_body));
                replaceExactText(root,
                        activity.getString(R.string.about080_upstream_acknowledgement),
                        activity.getString(R.string.v2_about_research_lineage));
            });
        }
    }

    private static void attachHistoryControls(HistoryStatisticsActivity activity, View root) {
        Runnable patch = () -> patchHistoryControls(activity, root);
        root.post(patch);
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        patch.run();
                    }
                });
    }

    private static void patchHistoryControls(HistoryStatisticsActivity activity, View view) {
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            CharSequence description = button.getContentDescription();
            String previous = activity.getString(R.string.v2_previous_period);
            String next = activity.getString(R.string.v2_next_period);
            String text = button.getText() == null ? "" : button.getText().toString();

            if (description != null && previous.contentEquals(description) && !text.isEmpty()) {
                iconOnly(button, R.drawable.ic_m3_chevron_back, activity, false);
            } else if (description != null && next.contentEquals(description) && !text.isEmpty()) {
                iconOnly(button, R.drawable.ic_m3_chevron, activity, false);
            } else {
                String inactiveFilter = activity.getString(R.string.v2_filter);
                String activeFilter = activity.getString(R.string.v2_filter_active_count, 1);
                if (inactiveFilter.equals(text) || activeFilter.equals(text)) {
                    boolean active = activeFilter.equals(text);
                    button.setContentDescription(activity.getString(active
                            ? R.string.v2_filter_button_active_cd
                            : R.string.v2_filter_button_cd));
                    iconOnly(button, R.drawable.ic_m3_filter, activity, active);
                    ViewGroup.LayoutParams params = button.getLayoutParams();
                    if (params != null && params.width != MaterialUi.dp(activity, 48)) {
                        params.width = MaterialUi.dp(activity, 48);
                        button.setLayoutParams(params);
                    }
                }
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            patchHistoryControls(activity, group.getChildAt(i));
        }
    }

    private static void iconOnly(MaterialButton button, int iconRes,
                                 MaterialBaseActivity activity, boolean active) {
        button.setText("");
        button.setIconResource(iconRes);
        button.setIconPadding(0);
        button.setIconSize(MaterialUi.dp(activity, 24));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        int color = MaterialUi.color(activity,
                active ? com.google.android.material.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOnSurfaceVariant,
                active ? activity.getColor(R.color.app_primary)
                        : activity.getColor(R.color.app_on_surface_variant));
        button.setIconTint(ColorStateList.valueOf(color));
        if (active) button.setStrokeColor(ColorStateList.valueOf(color));
    }

    private static void replaceExactText(View view, String expected, String replacement) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) {
            ((TextView) view).setText(replacement);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            replaceExactText(group.getChildAt(i), expected, replacement);
        }
    }
}
