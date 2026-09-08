package de.marcleinen.engineeringlab.qalcosonic;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

/** Normal secondary diagnostic screen. Raw research/protocol traces are not exposed here. */
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
        addCard(content, R.string.v2_diag_signing_title,
                getString(BuildConfig.STABLE_SIGNING
                        ? R.string.v2_diag_signing_stable : R.string.v2_diag_signing_debug));
        addCard(content, R.string.m3_diagnostics_protocol,
                getString(R.string.v2_diag_protocol_value) + "\n"
                        + getString(R.string.v2_diag_year_not_exposed));
        addCard(content, R.string.v2_diag_data_title, localDataStatus());

        setContentView(shell);
        applySystemInsets(shell);
    }

    private String localDataStatus() {
        String active = new MeterLifecycleStore(this).activeMeterId();
        if (active == null || active.trim().isEmpty()) return getString(R.string.m3_no_meter);

        try (MeterHistoryStore live = new MeterHistoryStore(this);
             ArchiveFamilyStore archive = new ArchiveFamilyStore(this)) {
            int liveCount = live.getReadings(active, 0L).size();
            ArchiveFamilySyncStateStore states = new ArchiveFamilySyncStateStore(this);
            return getString(R.string.m3_meter_id, active)
                    + "\n" + getString(R.string.v2_diag_family_line,
                    getString(R.string.m3_filter_live), liveCount, getString(R.string.m3_provenance_live))
                    + "\n" + familyLine(archive, states, active, ArchiveFamilyPeriod.Family.HOUR)
                    + "\n" + familyLine(archive, states, active, ArchiveFamilyPeriod.Family.DAY)
                    + "\n" + familyLine(archive, states, active, ArchiveFamilyPeriod.Family.MONTH);
        }
    }

    private String familyLine(ArchiveFamilyStore archive,
                              ArchiveFamilySyncStateStore states,
                              String meter,
                              ArchiveFamilyPeriod.Family family) {
        int count = archive.getPeriods(meter, family).size();
        ArchiveFamilySyncState state = states.get(meter, family);
        String stateText;
        if (state.baselineComplete()) {
            stateText = getString(R.string.m3_history_baseline_complete);
        } else if (state.lastAttemptOutcome == ArchiveFamilySyncState.AttemptOutcome.PARTIAL) {
            stateText = getString(R.string.m3_state_sync_partial_title);
        } else if (state.lastAttemptOutcome == ArchiveFamilySyncState.AttemptOutcome.FAILED) {
            stateText = getString(R.string.m3_state_sync_failed_title);
        } else {
            stateText = getString(R.string.m3_no_archive);
        }
        return getString(R.string.v2_diag_family_line, familyLabel(family), count, stateText);
    }

    private String familyLabel(ArchiveFamilyPeriod.Family family) {
        if (family == ArchiveFamilyPeriod.Family.HOUR) return getString(R.string.m3_filter_hour);
        if (family == ArchiveFamilyPeriod.Family.DAY) return getString(R.string.m3_filter_day);
        return getString(R.string.m3_filter_month);
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
