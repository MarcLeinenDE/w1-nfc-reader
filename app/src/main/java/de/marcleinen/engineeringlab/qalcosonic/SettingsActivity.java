package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Material 3 normal-product settings. No protocol behavior lives here. */
public final class SettingsActivity extends MaterialBaseActivity {
    static final String BACKUP_CREATE_MIME = "application/octet-stream";

    private View root;
    private ActivityResultLauncher<String> csvCreate;
    private ActivityResultLauncher<String> backupCreate;
    private ActivityResultLauncher<String[]> backupOpen;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerFileLaunchers();

        LinearLayout shell = MaterialUi.vertical(this);
        shell.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        shell.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = MaterialUi.vertical(this);
        int p = MaterialUi.dp(this, 16);
        content.setPadding(p, p, p, MaterialUi.dp(this, 32));
        content.addView(MaterialUi.headline(this, getString(R.string.m3_settings_title)));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addSectionTitle(content, R.string.m3_settings_appearance);
        MaterialCardView appearance = MaterialUi.card(this);
        LinearLayout appearanceContent = MaterialUi.vertical(this);
        appearanceContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_settings,
                getString(R.string.m3_settings_theme), themeSummary(), v -> chooseTheme()));
        appearanceContent.addView(MaterialUi.divider(this));
        appearanceContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_settings,
                getString(R.string.m3_settings_language), languageSummary(), v -> chooseLanguage()));
        appearance.addView(appearanceContent);
        MaterialUi.addTopMargin(content, appearance, 8);

        addSectionTitle(content, R.string.m3_settings_data);
        MaterialCardView data = MaterialUi.card(this);
        LinearLayout dataContent = MaterialUi.vertical(this);
        dataContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_settings,
                getString(R.string.m3_sync_history), getString(R.string.m3_history_subtitle),
                v -> startActivity(new Intent(this, HistorySyncActivity.class))));
        dataContent.addView(MaterialUi.divider(this));
        dataContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_backup,
                getString(R.string.m3_export_csv), getString(R.string.m3_export_csv_summary),
                v -> csvCreate.launch(csvFileName())));
        dataContent.addView(MaterialUi.divider(this));
        dataContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_backup,
                getString(R.string.m3_create_backup), getString(R.string.m3_backup_summary_brand_neutral),
                v -> backupCreate.launch(backupFileName())));
        dataContent.addView(MaterialUi.divider(this));
        dataContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_restore,
                getString(R.string.m3_restore_backup), getString(R.string.m3_restore_backup_summary),
                v -> backupOpen.launch(new String[]{"application/zip", "application/octet-stream", "application/x-zip-compressed", "*/*"})));
        data.addView(dataContent);
        MaterialUi.addTopMargin(content, data, 8);

        addSectionTitle(content, R.string.m3_settings_advanced);
        MaterialCardView advanced = MaterialUi.card(this);
        LinearLayout advancedContent = MaterialUi.vertical(this);
        advancedContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_diagnostics,
                getString(R.string.m3_diagnostics), getString(R.string.m3_diagnostics_summary),
                v -> startActivity(new Intent(this, DiagnosticsActivity.class))));
        advancedContent.addView(MaterialUi.divider(this));
        advancedContent.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_info,
                getString(R.string.m3_about), getString(R.string.m3_about_summary),
                v -> startActivity(new Intent(this, AboutActivity.class))));
        advanced.addView(advancedContent);
        MaterialUi.addTopMargin(content, advanced, 8);

        root = shell;
        setContentView(shell);
        applySystemInsets(shell);
    }

    private void registerFileLaunchers() {
        csvCreate = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("no output stream");
                DataPortability.writeCsv(this, out);
                message(R.string.m3_export_success);
            } catch (Exception error) {
                message(R.string.m3_export_failed);
            }
        });
        backupCreate = registerForActivityResult(new ActivityResultContracts.CreateDocument(BACKUP_CREATE_MIME), uri -> {
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("no output stream");
                DataPortability.writeBackup(this, out);
                message(R.string.m3_backup_success);
            } catch (Exception error) {
                message(R.string.m3_backup_failed);
            }
        });
        backupOpen = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("no input stream");
                DataPortability.BackupPreview preview = DataPortability.inspectBackup(in);
                showRestorePreview(preview);
            } catch (Exception error) {
                message(R.string.m3_restore_failed);
            }
        });
    }

    private void showRestorePreview(DataPortability.BackupPreview preview) {
        String body = getString(R.string.m3_backup_preview, preview.createdUtc, preview.activeMeterId,
                preview.liveReadings, preview.archivePeriods)
                + "\n\n"
                + getString(R.string.m3_filter_hour) + ": " + preview.hourPeriods + " · "
                + baselineLabel(preview.hourBaselineState)
                + "\n"
                + getString(R.string.m3_filter_day) + ": " + preview.dayPeriods + " · "
                + baselineLabel(preview.dayBaselineState)
                + "\n"
                + getString(R.string.m3_filter_month) + ": " + preview.monthPeriods + " · "
                + baselineLabel(preview.monthBaselineState);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_backup_preview_title)
                .setMessage(body)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.m3_restore, (dialog, which) -> {
                    try {
                        DataPortability.restoreBackup(this, preview.bytes);
                        message(R.string.m3_restore_success);
                        getWindow().getDecorView().postDelayed(this::recreate, 250L);
                    } catch (Exception error) {
                        message(R.string.m3_restore_failed);
                    }
                }).show();
    }

    private String baselineLabel(ArchiveFamilySyncState.BaselineState state) {
        if (state == ArchiveFamilySyncState.BaselineState.COMPLETE) {
            return getString(R.string.m3_state_sync_ok_title);
        }
        if (state == ArchiveFamilySyncState.BaselineState.PARTIAL) {
            return getString(R.string.m3_state_sync_partial_title);
        }
        return getString(R.string.m3_no_archive);
    }

    private void chooseTheme() {
        String[] values = {UiPreferences.THEME_SYSTEM, UiPreferences.THEME_LIGHT, UiPreferences.THEME_DARK};
        String[] labels = {getString(R.string.m3_theme_system), getString(R.string.m3_theme_light),
                getString(R.string.m3_theme_dark)};
        String current = UiPreferences.getTheme(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_settings_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    UiPreferences.setTheme(this, values[which]);
                }).show();
    }

    private void chooseLanguage() {
        List<String> tags = new ArrayList<>();
        tags.add(UiPreferences.LANGUAGE_SYSTEM);
        tags.addAll(UiPreferences.getSupportedLocaleTags(this));
        String[] labels = new String[tags.size()];
        labels[0] = getString(R.string.m3_language_system);
        Locale displayLocale = getResources().getConfiguration().getLocales().get(0);
        for (int i = 1; i < tags.size(); i++) {
            Locale locale = Locale.forLanguageTag(tags.get(i));
            String name = locale.getDisplayName(displayLocale);
            labels[i] = name.isEmpty() ? tags.get(i) : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        String current = UiPreferences.getSelectedLanguageTag(this);
        int checked = 0;
        for (int i = 0; i < tags.size(); i++) if (tags.get(i).equalsIgnoreCase(current)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_settings_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    UiPreferences.setLanguage(this, tags.get(which));
                }).show();
    }

    private CharSequence themeSummary() {
        String theme = UiPreferences.getTheme(this);
        if (UiPreferences.THEME_LIGHT.equals(theme)) return getString(R.string.m3_theme_light);
        if (UiPreferences.THEME_DARK.equals(theme)) return getString(R.string.m3_theme_dark);
        return getString(R.string.m3_theme_system);
    }

    private CharSequence languageSummary() {
        String tag = UiPreferences.getSelectedLanguageTag(this);
        if (tag == null || UiPreferences.LANGUAGE_SYSTEM.equals(tag)) return getString(R.string.m3_language_system);
        Locale displayLocale = getResources().getConfiguration().getLocales().get(0);
        return Locale.forLanguageTag(tag).getDisplayName(displayLocale);
    }

    private void addSectionTitle(LinearLayout content, int titleRes) {
        View title = MaterialUi.title(this, getString(titleRes));
        MaterialUi.addTopMargin(content, title, 24);
    }

    private void message(int stringRes) {
        Snackbar.make(root == null ? getWindow().getDecorView() : root, stringRes, Snackbar.LENGTH_LONG).show();
    }

    private static String csvFileName() {
        return "w1-nfc-reader-data-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date()) + ".csv";
    }

    static String backupFileName() {
        return "w1-nfc-reader-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date()) + ".qw1backup";
    }
}
