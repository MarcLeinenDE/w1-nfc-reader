package de.marcleinen.engineeringlab.qalcosonic;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

/** Normal secondary diagnostic screen; research readers remain backend-only until separately surfaced. */
public final class DiagnosticsActivity extends MaterialBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout shell = MaterialUi.vertical(this);
        shell.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.m3_diagnostics);
        toolbar.setNavigationIcon(R.drawable.ic_m3_back);
        toolbar.setNavigationContentDescription(R.string.m3_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        shell.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = MaterialUi.vertical(this);
        int p = MaterialUi.dp(this, 16);
        content.setPadding(p, p, p, MaterialUi.dp(this, 32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addCard(content, R.string.m3_diagnostics_build,
                getString(R.string.m3_diagnostics_build_value, BuildConfig.VERSION_NAME, BuildConfig.BUILD_COMMIT));
        addCard(content, R.string.m3_diagnostics_signing,
                getString(BuildConfig.STABLE_SIGNING
                        ? R.string.m3_diagnostics_signing_stable : R.string.m3_diagnostics_signing_ephemeral));
        addCard(content, R.string.m3_diagnostics_protocol,
                getString(R.string.m3_diagnostics_protocol_value) + "\n" + getString(R.string.m3_diagnostics_other_families));

        String active = new MeterLifecycleStore(this).activeMeterId();
        String dataText;
        try (MeterHistoryStore live = new MeterHistoryStore(this);
             ArchiveFamilyStore archive = new ArchiveFamilyStore(this)) {
            int liveCount = active == null ? 0 : live.getReadings(active, 0L).size();
            int archiveCount = active == null ? 0 : archive.getPeriods(active, null).size();
            dataText = (active == null ? getString(R.string.m3_details_no_value) : active)
                    + "\n" + getString(R.string.m3_filter_live) + ": " + liveCount
                    + " · " + getString(R.string.m3_provenance_archive) + ": " + archiveCount;
        }
        addCard(content, R.string.m3_history_quality, dataText);

        setContentView(shell);
        applySystemInsets(shell);
    }

    private void addCard(LinearLayout parent, int titleRes, CharSequence body) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.cardContent(this);
        inside.addView(MaterialUi.title(this, getString(titleRes)));
        android.widget.TextView text = MaterialUi.body(this, body);
        text.setPadding(0, MaterialUi.dp(this, 6), 0, 0);
        inside.addView(text);
        card.addView(inside);
        MaterialUi.addTopMargin(parent, card, parent.getChildCount() == 0 ? 0 : 10);
    }
}
