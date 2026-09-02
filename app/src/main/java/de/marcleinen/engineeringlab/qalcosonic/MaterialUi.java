package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;

/** Small View-based Material 3 factory. Layouts remain content-sized and translation-safe. */
final class MaterialUi {
    private MaterialUi() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int attr, int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }

    static LinearLayout vertical(Context context) {
        LinearLayout out = new LinearLayout(context);
        out.setOrientation(LinearLayout.VERTICAL);
        return out;
    }

    static LinearLayout horizontal(Context context) {
        LinearLayout out = new LinearLayout(context);
        out.setOrientation(LinearLayout.HORIZONTAL);
        out.setGravity(Gravity.CENTER_VERTICAL);
        return out;
    }

    static TextView headline(Context context, CharSequence value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        view.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface, 0xFF202124));
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    static TextView title(Context context, CharSequence value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        view.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface, 0xFF202124));
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    static TextView body(Context context, CharSequence value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        view.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368));
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    static TextView label(Context context, CharSequence value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        view.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368));
        return view;
    }

    static MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 20));
        card.setCardElevation(0f);
        card.setUseCompatPadding(false);
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(color(context, com.google.android.material.R.attr.colorOutlineVariant,
                color(context, com.google.android.material.R.attr.colorOutline, 0xFF777777)));
        card.setCardBackgroundColor(color(context, com.google.android.material.R.attr.colorSurfaceContainerLow,
                color(context, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF)));
        return card;
    }

    static LinearLayout cardContent(Context context) {
        LinearLayout content = vertical(context);
        int p = dp(context, 18);
        content.setPadding(p, p, p, p);
        return content;
    }

    static View settingRow(Context context, @DrawableRes int iconRes, CharSequence title,
                           CharSequence summary, View.OnClickListener listener) {
        LinearLayout row = horizontal(context);
        row.setMinimumHeight(dp(context, 64));
        row.setPadding(dp(context, 16), dp(context, 10), dp(context, 12), dp(context, 10));
        row.setClickable(listener != null);
        row.setFocusable(listener != null);
        if (listener != null) row.setOnClickListener(listener);

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(AppCompatResources.getDrawable(context, iconRes));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
        iconLp.setMarginEnd(dp(context, 16));
        row.addView(icon, iconLp);

        LinearLayout text = vertical(context);
        text.addView(title(context, title));
        if (summary != null && summary.length() > 0) {
            TextView summaryView = body(context, summary);
            summaryView.setPadding(0, dp(context, 2), 0, 0);
            text.addView(summaryView);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (listener != null) {
            ImageView chevron = new ImageView(context);
            chevron.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_m3_chevron));
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        }
        return row;
    }

    static MaterialDivider divider(Context context) {
        MaterialDivider divider = new MaterialDivider(context);
        divider.setDividerInsetStart(dp(context, 56));
        divider.setDividerThickness(dp(context, 1));
        return divider;
    }

    static void addTopMargin(ViewGroup parent, View child, int dp) {
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = MaterialUi.dp(parent.getContext(), dp);
        parent.addView(child, lp);
    }

    static Drawable icon(Context context, @DrawableRes int res) {
        return AppCompatResources.getDrawable(context, res);
    }

    static ColorStateList tint(Context context, int attr, int fallback) {
        return ColorStateList.valueOf(color(context, attr, fallback));
    }

    static CharSequence str(Context context, @StringRes int id) {
        return context.getString(id);
    }
}
