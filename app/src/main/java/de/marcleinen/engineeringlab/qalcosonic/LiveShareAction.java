package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Presentation-only Android share action for the last successful Live reading shown on Overview. */
final class LiveShareAction {
    private LiveShareAction() { }

    static void attach(MaterialBaseActivity activity) {
        ViewGroup androidContent = activity.findViewById(android.R.id.content);
        if (androidContent == null || androidContent.getChildCount() != 1) return;
        View productRoot = androidContent.getChildAt(0);
        LinearLayout heroContent = findLiveHeroContent(productRoot,
                activity.getString(R.string.m3_meter_reading));
        if (heroContent == null) return;

        LinearLayout row = MaterialUi.horizontal(activity);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        AppCompatImageButton button = new AppCompatImageButton(activity);
        button.setImageResource(R.drawable.ic_m3_share);
        button.setContentDescription(activity.getString(R.string.m3_share_live));
        int tintColor = MaterialUi.color(activity,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                activity.getColor(R.color.app_on_primary_container));
        ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(tintColor));
        int pad = MaterialUi.dp(activity, 12);
        button.setPadding(pad, pad, pad, pad);

        TypedValue selectable = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, selectable, true)
                && selectable.resourceId != 0) {
            button.setBackgroundResource(selectable.resourceId);
        } else {
            button.setBackgroundColor(Color.TRANSPARENT);
        }
        button.setOnClickListener(v -> shareLatestLive(activity, productRoot));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                MaterialUi.dp(activity, 48), MaterialUi.dp(activity, 48));
        buttonParams.topMargin = MaterialUi.dp(activity, 2);
        row.addView(button, buttonParams);
        heroContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static LinearLayout findLiveHeroContent(View view, String liveLabel) {
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            if (card.getChildCount() == 1 && card.getChildAt(0) instanceof LinearLayout) {
                LinearLayout content = (LinearLayout) card.getChildAt(0);
                if (content.getChildCount() > 0 && content.getChildAt(0) instanceof TextView) {
                    CharSequence text = ((TextView) content.getChildAt(0)).getText();
                    if (liveLabel.contentEquals(text)) return content;
                }
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            LinearLayout found = findLiveHeroContent(group.getChildAt(index), liveLabel);
            if (found != null) return found;
        }
        return null;
    }

    private static void shareLatestLive(MaterialBaseActivity activity, View anchor) {
        String meterId = new MeterLifecycleStore(activity).activeMeterId();
        MeterHistoryStore.Reading latest = null;
        try (MeterHistoryStore live = new MeterHistoryStore(activity)) {
            if (meterId == null) meterId = live.getLatestMeterId();
            if (meterId != null) {
                List<MeterHistoryStore.Reading> readings = live.getReadings(meterId, 0L);
                if (!readings.isEmpty()) latest = readings.get(0);
            }
        }

        LiveReadMetadataStore.Summary meta = new LiveReadMetadataStore(activity).get(meterId);
        boolean useMeta = meta.available() && (latest == null || meta.readAtMs >= latest.readAtMs);
        if (!useMeta && latest == null) {
            Snackbar.make(anchor, R.string.m3_share_live_unavailable, Snackbar.LENGTH_SHORT).show();
            return;
        }

        String sharedMeterId = useMeta ? meterId : latest.meterId;
        double totalM3 = useMeta ? meta.totalM3 : latest.totalM3;
        long readAtMs = useMeta ? meta.readAtMs : latest.readAtMs;
        String reading = activity.getString(R.string.m3_unit_m3, totalM3);
        String readAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                activity.getResources().getConfiguration().getLocales().get(0))
                .format(new Date(readAtMs));
        String body = activity.getString(
                R.string.m3_share_live_text, sharedMeterId, reading, readAt);

        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.m3_share_live))
                .putExtra(Intent.EXTRA_TEXT, body);
        activity.startActivity(Intent.createChooser(intent,
                activity.getString(R.string.m3_share_live_chooser)));
    }
}
