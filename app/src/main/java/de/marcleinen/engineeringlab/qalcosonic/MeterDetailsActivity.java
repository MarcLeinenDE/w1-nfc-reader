package de.marcleinen.engineeringlab.qalcosonic;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
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
             ArchiveFamilyStore archive = new ArchiveFamilyStore(this)) {
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
        String value = d.meterTime != null ? d.meterTime : r == null ? null : r.meterTime;
        return value(value);
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
        return oldest + " – " + newest + " · " + periods.size();
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
