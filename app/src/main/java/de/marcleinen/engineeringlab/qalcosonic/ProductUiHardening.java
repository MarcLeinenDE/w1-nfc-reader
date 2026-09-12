package de.marcleinen.engineeringlab.qalcosonic;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small presentation-only release-hardening fixes shared across the v2 product surface. */
final class ProductUiHardening {
    private static final Pattern NUMERIC_DATE_TIME = Pattern.compile(
            "(\\b\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4})(?:,)?\\s+"
                    + "(\\d{1,2}:\\d{2}(?:\\s*[APap][Mm])?)");
    private static final Pattern MONTH_FIRST_DATE_TIME = Pattern.compile(
            "(\\b[\\p{L}.]{3,15}\\s+\\d{1,2},?\\s+\\d{4})(?:,)?\\s+"
                    + "(\\d{1,2}:\\d{2}(?:\\s*[APap][Mm])?)");
    private static final Pattern DAY_FIRST_DATE_TIME = Pattern.compile(
            "(\\b\\d{1,2}\\s+[\\p{L}.]{3,15}\\s+\\d{4})(?:,)?\\s+"
                    + "(\\d{1,2}:\\d{2}(?:\\s*[APap][Mm])?)");

    private ProductUiHardening() { }

    static void attach(MaterialBaseActivity activity) {
        if (activity == null) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        attachGlobalPolish(activity, root);
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

    private static void attachGlobalPolish(MaterialBaseActivity activity, View root) {
        Runnable patch = () -> patchGlobalPolish(activity, root);
        root.post(patch);
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        patch.run();
                    }
                });
    }

    private static void patchGlobalPolish(MaterialBaseActivity activity, View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence original = textView.getText();
            if (original != null && original.length() > 0) {
                String normalized = normalizeDateTimeSeparators(original.toString());
                if (!normalized.contentEquals(original)) textView.setText(normalized);
            }
            removeRedundantInlineHelp(activity, textView);
            attachContextualHelpIfNeeded(activity, textView);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            patchGlobalPolish(activity, group.getChildAt(i));
        }
    }

    static String normalizeDateTimeSeparators(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        String normalized = replaceDateTime(NUMERIC_DATE_TIME, value);
        normalized = replaceDateTime(MONTH_FIRST_DATE_TIME, normalized);
        return replaceDateTime(DAY_FIRST_DATE_TIME, normalized);
    }

    private static String replaceDateTime(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.replaceAll("$1 · $2");
    }

    /**
     * Contextual help belongs on the actual History/Statistics page heading only. Matching a word
     * globally made drawer/menu labels and unrelated occurrences look interactive, which is noisy
     * and semantically wrong. The generated page root is a plain LinearLayout and its headline is
     * the first child; drawer/navigation rows use their own Material container classes.
     */
    private static void attachContextualHelpIfNeeded(MaterialBaseActivity activity, TextView textView) {
        if (!(activity instanceof HistoryStatisticsActivity)) return;
        if (!isPrimaryHistoryStatisticsHeadline(textView)) return;
        if (textView.getCompoundDrawablesRelative()[2] != null) return;
        String text = textView.getText() == null ? "" : textView.getText().toString();
        if (text.equals(activity.getString(R.string.m3_history_title))) {
            attachInfo(activity, textView,
                    R.string.v21_info_history_title, R.string.v21_info_history_body);
        } else if (text.equals(activity.getString(R.string.m3_stats_title))) {
            attachInfo(activity, textView,
                    R.string.v21_info_statistics_title, R.string.v21_info_statistics_body);
        }
    }

    private static boolean isPrimaryHistoryStatisticsHeadline(TextView textView) {
        if (!(textView.getParent() instanceof LinearLayout)) return false;
        LinearLayout parent = (LinearLayout) textView.getParent();
        return parent.getClass() == LinearLayout.class
                && parent.getChildCount() > 0
                && parent.getChildAt(0) == textView;
    }

    /**
     * General Statistics explanations now live behind the single page-level info control. Keep
     * metric-specific caveats inline, but remove the duplicated generic consumption paragraph and
     * the repeated sync-completeness suffix from the coverage line.
     */
    private static void removeRedundantInlineHelp(MaterialBaseActivity activity, TextView textView) {
        if (!(activity instanceof HistoryStatisticsActivity)) return;
        CharSequence value = textView.getText();
        if (value == null || value.length() == 0) return;
        String text = value.toString();
        if (text.equals(activity.getString(R.string.v2_consumption_note))) {
            textView.setVisibility(View.GONE);
            return;
        }
        String suffix = " · " + activity.getString(R.string.v2_coverage_not_sync_state);
        if (text.endsWith(suffix)) {
            textView.setText(text.substring(0, text.length() - suffix.length()));
        }
    }

    private static void attachInfo(MaterialBaseActivity activity, TextView textView,
                                   int titleRes, int bodyRes) {
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_m3_info, 0);
        textView.setCompoundDrawablePadding(MaterialUi.dp(activity, 8));
        textView.setCompoundDrawableTintList(ColorStateList.valueOf(MaterialUi.color(activity,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                activity.getColor(R.color.app_on_surface_variant))));
        textView.setMinHeight(Math.max(textView.getMinHeight(), MaterialUi.dp(activity, 48)));
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setContentDescription(textView.getText() + ". " + activity.getString(titleRes));
        textView.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle(titleRes)
                .setMessage(bodyRes)
                .setPositiveButton(android.R.string.ok, null)
                .show());
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
