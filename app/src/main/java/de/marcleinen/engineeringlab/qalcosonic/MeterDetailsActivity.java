package de.marcleinen.engineeringlab.qalcosonic;

import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Dedicated normal-product details screen; replaces the legacy MainActivity details path. */
public final class MeterDetailsActivity extends MaterialBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        content.addView(MaterialUi.headline(this, getString(R.string.m3_meter_details)));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        MeterLifecycleStore lifecycle = new MeterLifecycleStore(this);
        String meterId = lifecycle.activeMeterId();
        try (MeterHistoryStore history = new MeterHistoryStore(this);
             ArchiveFamilyStore archive = new ArchiveFamilyStore(this);
             MeterTimeModelStore timeModel = new MeterTimeModelStore(this)) {
            if (meterId == null) meterId = history.getLatestMeterId();
            LiveDetailMetadataStore.Summary details = new LiveDetailMetadataStore(this).get(meterId);
            MeterHistoryStore.Reading latest = latest(history, meterId);
            LiveReadMetadataStore.Summary freshness = new LiveReadMetadataStore(this).get(meterId);
            List<ArchiveFamilyStore.StoredPeriod> periods = meterId == null
                    ? java.util.Collections.emptyList() : archive.getPeriods(meterId, null);

            addSection(content, R.string.m3_details_identity,
                    row(R.string.m3_details_meter_number, meterId == null ? getString(R.string.m3_details_no_value) : meterId),
                    row(R.string.m3_details_manufacturer, value(details.manufacturer)),
                    row(R.string.m3_details_version, details.meterVersion == null ? getString(R.string.m3_details_no_value) : Integer.toString(details.meterVersion)));

            addSection(content, R.string.m3_details_measurements,
                    row(R.string.m3_meter_reading, total(details, latest)),
                    row(R.string.m3_details_flow, flow(details, latest)),
                    row(R.string.m3_details_water_temp, temperature(details.waterTemperatureC,
                            latest == null ? null : latest.waterTemperatureC)),
                    row(R.string.m3_details_ambient_temp, temperature(details.ambientTemperatureC,
                            latest == null ? null : latest.ambientTemperatureC)),
                    row(R.string.m3_battery, battery(details, latest)),
                    row(R.string.m3_details_on_time, duration(details.onTimeSeconds)),
                    row(R.string.m3_details_operating_time, duration(details.operatingTimeSeconds)));

            addSection(content, R.string.m3_details_provenance,
                    row(R.string.m3_details_phone_time, acquisition(details, freshness, latest)),
                    row(R.string.m3_details_meter_time, meterTime(details, latest)),
                    row(R.string.m3_details_archive_coverage, archiveCoverage(periods)));

            addTimeZoneSection(content, meterId,
                    meterId == null ? null : timeModel.getProfile(meterId));

            if (freshness.available()) {
                String alarm = freshness.alarmCodes == null || freshness.alarmCodes.isEmpty()
                        ? getString(R.string.m3_alarm_clear_title)
                        : MeterStatusPresentation.localizedAlarmCodes(this, freshness.alarmCodes);
                addSection(content, R.string.m3_alarm_active_title,
                        row(R.string.m3_history_quality, alarm));
            }
        }

        setContentView(shell);
        applySystemInsets(shell);
    }

    private void addSection(LinearLayout parent, int titleRes, DetailRow... rows) {
        TextView heading = MaterialUi.title(this, getString(titleRes));
        MaterialUi.addTopMargin(parent, heading, 22);
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.vertical(this);
        for (int i = 0; i < rows.length; i++) {
            DetailRow row = rows[i];
            LinearLayout line = MaterialUi.vertical(this);
            line.setPadding(MaterialUi.dp(this, 16), MaterialUi.dp(this, 12),
                    MaterialUi.dp(this, 16), MaterialUi.dp(this, 12));
            line.addView(MaterialUi.label(this, getString(row.labelRes)));
            TextView value = MaterialUi.body(this, row.value);
            value.setPadding(0, MaterialUi.dp(this, 3), 0, 0);
            line.addView(value);
            inside.addView(line);
            if (i + 1 < rows.length) inside.addView(MaterialUi.divider(this));
        }
        card.addView(inside);
        MaterialUi.addTopMargin(parent, card, 8);
    }

    private void addTimeZoneSection(LinearLayout parent, String meterId,
                                    MeterTimeModelStore.Profile profile) {
        TextView heading = MaterialUi.title(this, getString(R.string.v21_meter_time_model));
        MaterialUi.addTopMargin(parent, heading, 22);

        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.vertical(this);
        android.view.View.OnClickListener listener = meterId == null
                ? null : v -> showTimeZoneDialog(meterId);
        inside.addView(MaterialUi.settingRow(this, R.drawable.ic_m3_settings,
                getString(R.string.v21_meter_timezone), timeZoneSummary(profile), listener));
        inside.addView(MaterialUi.divider(this));
        TextView note = MaterialUi.body(this, getString(R.string.v21_meter_timezone_note));
        note.setPadding(MaterialUi.dp(this, 16), MaterialUi.dp(this, 10),
                MaterialUi.dp(this, 16), MaterialUi.dp(this, 14));
        inside.addView(note);
        card.addView(inside);
        MaterialUi.addTopMargin(parent, card, 8);
    }

    private String timeZoneSummary(MeterTimeModelStore.Profile profile) {
        if (profile == null) return getString(R.string.v21_meter_timezone_not_set);
        return getString(R.string.v21_meter_timezone_value,
                profile.zoneId, timeZoneSource(profile.source));
    }

    private String timeZoneSource(String source) {
        if (MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED.equals(source)) {
            return getString(R.string.v21_meter_timezone_source_user);
        }
        if (MeterTimeModelStore.ZONE_SOURCE_DEVICE_AT_FIRST_VERIFIED_LIVE.equals(source)) {
            return getString(R.string.v21_meter_timezone_source_device);
        }
        return source == null || source.trim().isEmpty()
                ? getString(R.string.v21_meter_timezone_not_set) : source;
    }

    private void showTimeZoneDialog(String meterId) {
        MeterTimeModelStore.Profile current;
        try (MeterTimeModelStore store = new MeterTimeModelStore(this)) {
            current = store.getProfile(meterId);
        }

        // Use the tzdb shipped by the running device instead of maintaining a hand-written list.
        // The exposed dropdown is searchable, but Save accepts only an exact value from this set,
        // so normal product UI cannot persist an unsupported/free-text time-zone identifier.
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        if (!zones.contains("UTC")) zones.add("UTC");
        Collections.sort(zones);

        TextInputLayout field = new TextInputLayout(this);
        field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        field.setHint(getString(R.string.v21_meter_timezone_hint));

        MaterialAutoCompleteTextView input = new MaterialAutoCompleteTextView(this);
        input.setSingleLine(true);
        input.setThreshold(0);
        input.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, zones));
        input.setOnClickListener(v -> input.showDropDown());
        input.setOnItemClickListener((parent, view, position, id) -> field.setError(null));
        if (current != null && current.zoneId != null) {
            input.setText(current.zoneId, false);
            input.setSelection(input.getText().length());
        }
        field.addView(input, new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT,
                TextInputLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.v21_meter_timezone_edit)
                .setMessage(R.string.v21_meter_timezone_dialog_message)
                .setView(field)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.v21_meter_timezone_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String candidate = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (!zones.contains(candidate)) {
                        field.setError(getString(R.string.v21_meter_timezone_invalid));
                        return;
                    }
                    try {
                        String normalized = MeterTimeModelStore.normalizeZoneId(candidate);
                        try (MeterTimeModelStore store = new MeterTimeModelStore(this)) {
                            store.setZone(meterId, normalized,
                                    MeterTimeModelStore.ZONE_SOURCE_USER_SELECTED,
                                    System.currentTimeMillis());
                        }
                        dialog.dismiss();
                        recreate();
                    } catch (IllegalArgumentException error) {
                        field.setError(getString(R.string.v21_meter_timezone_invalid));
                    }
                }));
        dialog.show();
    }

    private DetailRow row(int label, String value) { return new DetailRow(label, value); }

    private MeterHistoryStore.Reading latest(MeterHistoryStore store, String meterId) {
        if (meterId == null) return null;
        List<MeterHistoryStore.Reading> readings = store.getReadings(meterId, 0L);
        return readings.isEmpty() ? null : readings.get(0);
    }

    private String total(LiveDetailMetadataStore.Summary d, MeterHistoryStore.Reading r) {
        Double value = d.totalM3 != null ? d.totalM3 : r == null ? null : r.totalM3;
        return value == null ? getString(R.string.m3_details_no_value) : getString(R.string.m3_unit_m3, value);
    }
    private String flow(LiveDetailMetadataStore.Summary d, MeterHistoryStore.Reading r) {
        Double value = d.flowM3h != null ? d.flowM3h : r == null ? null : r.flowM3h;
        return value == null ? getString(R.string.m3_details_no_value) : getString(R.string.m3_unit_flow, value);
    }
    private String temperature(Double primary, Double fallback) {
        Double value = primary != null ? primary : fallback;
        return value == null ? getString(R.string.m3_details_no_value) : getString(R.string.m3_unit_temperature, value);
    }
    private String battery(LiveDetailMetadataStore.Summary d, MeterHistoryStore.Reading r) {
        Integer value = d.batteryPercent != null ? d.batteryPercent : r == null ? null : r.batteryPercent;
        return value == null ? getString(R.string.m3_details_no_value) : getString(R.string.m3_unit_percent, value);
    }
    private String acquisition(LiveDetailMetadataStore.Summary d, LiveReadMetadataStore.Summary f,
                               MeterHistoryStore.Reading r) {
        long ms = d.readAtMs > 0 ? d.readAtMs : f.available() ? f.readAtMs : r == null ? 0L : r.readAtMs;
        return ms <= 0 ? getString(R.string.m3_details_no_value) : formatDate(ms);
    }
    private String meterTime(LiveDetailMetadataStore.Summary d, MeterHistoryStore.Reading r) {
        String raw = d.meterTime != null ? d.meterTime : r == null ? null : r.meterTime;
        if (raw == null || raw.trim().isEmpty()) return getString(R.string.m3_details_no_value);
        return HistoryTimePresentation.formatExactFloatingDateTime(
                getResources().getConfiguration().getLocales().get(0), raw);
    }
    private String duration(Long seconds) {
        if (seconds == null) return getString(R.string.m3_details_no_value);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        return getString(R.string.m3_duration_days, days, hours);
    }
    private String archiveCoverage(List<ArchiveFamilyStore.StoredPeriod> periods) {
        if (periods == null || periods.isEmpty()) return getString(R.string.m3_no_archive);
        String newest = null, oldest = null;
        for (ArchiveFamilyStore.StoredPeriod period : periods) {
            String t = period.loggerTimestamp;
            if (t == null) continue;
            if (newest == null || t.compareTo(newest) > 0) newest = t;
            if (oldest == null || t.compareTo(oldest) < 0) oldest = t;
        }
        if (newest == null) return getString(R.string.m3_no_archive);
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        return HistoryTimePresentation.formatExactFloatingDateTime(locale, oldest)
                + " – " + HistoryTimePresentation.formatExactFloatingDateTime(locale, newest)
                + " · " + periods.size();
    }
    private String formatDate(long ms) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                getResources().getConfiguration().getLocales().get(0)).format(new Date(ms));
    }
    private String value(String value) { return value == null || value.trim().isEmpty() ? getString(R.string.m3_details_no_value) : value; }

    private static final class DetailRow {
        final int labelRes; final String value;
        DetailRow(int labelRes, String value) { this.labelRes=labelRes; this.value=value; }
    }
}