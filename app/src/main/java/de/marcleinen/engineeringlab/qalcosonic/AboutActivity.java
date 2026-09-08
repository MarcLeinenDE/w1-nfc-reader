package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Material 3 About, licensing, attribution and local-data notices. */
public final class AboutActivity extends MaterialBaseActivity {
    private static final String MAINTAINER_URL = "https://github.com/MarcLeinenDE";
    private static final String UPSTREAM_URL = "https://github.com/dbmaxpayne/esphome_qalcosonicnfc";
    private static final String CC_BY_SA_URL = "https://creativecommons.org/licenses/by-sa/4.0/";

    private View root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout shell = MaterialUi.vertical(this);
        shell.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.about080_page_title);
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
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(MaterialUi.headline(this, getString(R.string.app_name)));
        TextView intro = MaterialUi.body(this, getString(R.string.about080_intro));
        intro.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(intro);

        addBuildCard(content);
        addIndependentNotice(content);
        addLicenseCard(content);
        addThirdPartyCard(content);
        addPrivacyCards(content);

        root = shell;
        setContentView(shell);
        applySystemInsets(shell);
    }

    private void addBuildCard(LinearLayout parent) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout box = MaterialUi.cardContent(this);
        box.addView(MaterialUi.title(this, getString(R.string.about080_build_title)));
        addDetail(box, R.string.about080_version_label, BuildConfig.VERSION_NAME);
        String commit = BuildConfig.BUILD_COMMIT == null ? "" : BuildConfig.BUILD_COMMIT.trim();
        addDetail(box, R.string.about080_build_label,
                commit.isEmpty() ? getString(R.string.m3_details_no_value) : commit);
        addDetail(box, R.string.about080_maintainer_label, getString(R.string.about080_maintainer_value));
        addButton(box, R.string.about080_open_maintainer, v -> openUrl(MAINTAINER_URL));
        addSupportBlock(box);
        card.addView(box);
        MaterialUi.addTopMargin(parent, card, 14);
    }

    private void addSupportBlock(LinearLayout parent) {
        TextView title = MaterialUi.title(this, getString(R.string.v2_support_developer_title));
        title.setPadding(0, MaterialUi.dp(this, 14), 0, 0);
        parent.addView(title);
        addBody(parent, R.string.v2_support_developer_body);
        addButton(parent, R.string.v2_support_developer_button, v -> {
            if (!DeveloperSupport.openExternal(this)) {
                Snackbar.make(root == null ? getWindow().getDecorView() : root,
                        R.string.about080_open_link_failed, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void addIndependentNotice(LinearLayout parent) {
        MaterialCardView card = MaterialUi.card(this);
        card.setStrokeColor(MaterialUi.color(this, com.google.android.material.R.attr.colorPrimary,
                getColor(R.color.app_primary)));
        card.setCardBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                getColor(R.color.app_primary_container)));
        LinearLayout box = MaterialUi.cardContent(this);
        TextView notice = MaterialUi.body(this, getString(R.string.about080_independent_notice));
        notice.setTypeface(notice.getTypeface(), Typeface.BOLD);
        notice.setTextColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                getColor(R.color.app_on_primary_container)));
        box.addView(notice);
        card.addView(box);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private void addLicenseCard(LinearLayout parent) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout box = MaterialUi.cardContent(this);
        box.addView(MaterialUi.title(this, getString(R.string.about080_license_title)));
        TextView copyright = MaterialUi.body(this, getString(R.string.about080_copyright));
        copyright.setTypeface(copyright.getTypeface(), Typeface.BOLD);
        copyright.setPadding(0, MaterialUi.dp(this, 6), 0, 0);
        box.addView(copyright);
        addDetail(box, R.string.about080_software_license_label, getString(R.string.about080_software_license_value));
        addDetail(box, R.string.about080_docs_license_label, getString(R.string.about080_docs_license_value));
        addBody(box, R.string.about080_license_terms);

        boolean sourceConfigured = BuildConfig.SOURCE_REPOSITORY_URL != null
                && !BuildConfig.SOURCE_REPOSITORY_URL.trim().isEmpty();
        addBody(box, sourceConfigured ? R.string.about080_source_available : R.string.about080_source_pending);
        if (sourceConfigured) {
            TextView source = MaterialUi.body(this, BuildConfig.SOURCE_REPOSITORY_URL);
            source.setTextIsSelectable(true);
            source.setPadding(0, MaterialUi.dp(this, 6), 0, 0);
            box.addView(source);
            addButton(box, R.string.about080_open_source,
                    v -> openUrl(BuildConfig.SOURCE_REPOSITORY_URL));
        }
        addButton(box, R.string.about080_view_gpl,
                v -> showBundledLicense(R.raw.gpl_3_0, R.string.about080_gpl_title));
        addButton(box, R.string.about080_open_cc, v -> openUrl(CC_BY_SA_URL));
        card.addView(box);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private void addThirdPartyCard(LinearLayout parent) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout box = MaterialUi.cardContent(this);
        box.addView(MaterialUi.title(this, getString(R.string.about080_third_party_title)));
        addDetail(box, R.string.about080_upstream_project_label, getString(R.string.about080_upstream_name));
        addDetail(box, R.string.about080_upstream_author_label, getString(R.string.about080_upstream_author));
        addDetail(box, R.string.about080_upstream_license_label, getString(R.string.about080_upstream_license));
        addBody(box, R.string.about080_upstream_explanation);
        addBody(box, R.string.about080_upstream_acknowledgement);
        addButton(box, R.string.about080_open_upstream, v -> openUrl(UPSTREAM_URL));
        addButton(box, R.string.about080_view_lgpl,
                v -> showBundledLicense(R.raw.lgpl_2_1, R.string.about080_lgpl_title));
        card.addView(box);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private void addPrivacyCards(LinearLayout parent) {
        TextView heading = MaterialUi.title(this, getString(R.string.about080_privacy_title));
        MaterialUi.addTopMargin(parent, heading, 22);
        addNoticeCard(parent, R.string.about080_privacy_local_title, R.string.about080_privacy_local_body);
        addNoticeCard(parent, R.string.about080_privacy_history_title, R.string.about080_privacy_history_body);
        addNoticeCard(parent, R.string.about080_privacy_not_stored_title, R.string.about080_privacy_not_stored_body);
        addNoticeCard(parent, R.string.about080_privacy_backup_title, R.string.about080_privacy_backup_body);
        addNoticeCard(parent, R.string.about080_privacy_portable_title, R.string.about080_privacy_portable_body);
        addNoticeCard(parent, R.string.about080_privacy_sharing_title, R.string.about080_privacy_sharing_body);
    }

    private void addNoticeCard(LinearLayout parent, int titleRes, int bodyRes) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout box = MaterialUi.cardContent(this);
        box.addView(MaterialUi.title(this, getString(titleRes)));
        addBody(box, bodyRes);
        card.addView(box);
        MaterialUi.addTopMargin(parent, card, 8);
    }

    private void addDetail(LinearLayout parent, int labelRes, String value) {
        TextView label = MaterialUi.label(this, getString(labelRes));
        label.setPadding(0, MaterialUi.dp(this, 10), 0, 0);
        parent.addView(label);
        TextView valueView = MaterialUi.body(this, value);
        valueView.setTypeface(valueView.getTypeface(), Typeface.BOLD);
        valueView.setTextIsSelectable(true);
        valueView.setPadding(0, MaterialUi.dp(this, 2), 0, 0);
        parent.addView(valueView);
    }

    private void addBody(LinearLayout parent, int bodyRes) {
        TextView body = MaterialUi.body(this, getString(bodyRes));
        body.setPadding(0, MaterialUi.dp(this, 9), 0, 0);
        parent.addView(body);
    }

    private void addButton(LinearLayout parent, int labelRes, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(labelRes);
        button.setMinHeight(MaterialUi.dp(this, 48));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = MaterialUi.dp(this, 8);
        parent.addView(button, lp);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Snackbar.make(root == null ? getWindow().getDecorView() : root,
                    R.string.about080_open_link_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showBundledLicense(int rawRes, int titleRes) {
        try (InputStream in = getResources().openRawResource(rawRes);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');

            TextView license = MaterialUi.body(this, text.toString());
            license.setTypeface(Typeface.MONOSPACE);
            license.setTextIsSelectable(true);
            int p = MaterialUi.dp(this, 16);
            license.setPadding(p, p, p, p);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(license);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(titleRes)
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Exception error) {
            Snackbar.make(root == null ? getWindow().getDecorView() : root,
                    R.string.about080_license_load_failed, Snackbar.LENGTH_LONG).show();
        }
    }
}
