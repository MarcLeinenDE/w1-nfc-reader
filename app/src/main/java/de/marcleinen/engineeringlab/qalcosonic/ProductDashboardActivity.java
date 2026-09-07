package de.marcleinen.engineeringlab.qalcosonic;

import android.database.Cursor;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Material 3 Overview and protected normal Live NFC contact.
 *
 * <p>History browsing/statistics live in {@link HistoryStatisticsActivity}; explicit archive
 * synchronization lives in {@link HistorySyncActivity}. Keeping this Activity Overview-only
 * prevents the retired pre-family Month sync from becoming a second product route.</p>
 */
public final class ProductDashboardActivity extends MaterialBaseActivity implements NfcAdapter.ReaderCallback {
    private static final int NAV_OVERVIEW = 0x8101;

    private final AtomicBoolean reading = new AtomicBoolean(false);

    private NfcAdapter nfcAdapter;
    private MeterHistoryStore liveStore;
    private ArchiveFamilyStore archiveStore;
    private LiveReadMetadataStore liveMetadataStore;
    private LiveDetailMetadataStore liveDetailStore;
    private MeterLifecycleStore lifecycleStore;
    private String displayMeterId;

    private LinearLayout root;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView overviewReading;
    private TextView overviewMeter;
    private TextView overviewLastLive;
    private TextView overviewMonth;
    private TextView overviewYear;
    private TextView overviewBattery;
    private TextView overviewLastArchive;
    private TextView overviewHistoryState;
    private MaterialCardView alarmCard;
    private TextView alarmTitle;
    private TextView alarmBody;

    private ActivityResultLauncher<String> replacementCsvCreate;
    private PendingMeter pendingMeter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerReplacementExport();
        liveStore = new MeterHistoryStore(getApplicationContext());
        archiveStore = new ArchiveFamilyStore(getApplicationContext());
        liveMetadataStore = new LiveReadMetadataStore(getApplicationContext());
        liveDetailStore = new LiveDetailMetadataStore(getApplicationContext());
        lifecycleStore = new MeterLifecycleStore(getApplicationContext());
        displayMeterId = lifecycleStore.activeMeterId();
        if (displayMeterId == null) {
            displayMeterId = liveStore.getLatestMeterId();
            if (displayMeterId != null) lifecycleStore.adoptInitialMeter(displayMeterId);
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
        showReady();
        refreshAll();
    }

    @Override protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            Bundle options = new Bundle();
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 200);
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        } else if (nfcAdapter == null) {
            setState(R.string.m3_state_no_nfc_title, R.string.m3_state_no_nfc_body);
        } else {
            setState(R.string.m3_state_nfc_off_title, R.string.m3_state_nfc_off_body);
        }
        displayMeterId = lifecycleStore == null ? displayMeterId : lifecycleStore.activeMeterId();
        refreshAll();
    }

    @Override protected void onPause() {
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (liveStore != null) liveStore.close();
        if (archiveStore != null) archiveStore.close();
        super.onDestroy();
    }

    @Override public void onTagDiscovered(Tag tag) {
        if (!reading.compareAndSet(false, true)) return;
        NfcV nfcv = NfcV.get(tag);
        if (nfcv == null) {
            reading.set(false);
            runOnUiThread(() -> setState(R.string.m3_state_wrong_tag_title, R.string.m3_state_wrong_tag_body));
            return;
        }
        try {
            nfcv.connect();
            performLiveContact(nfcv, tag);
        } catch (Exception error) {
            runOnUiThread(() -> {
                setState(R.string.m3_state_failed_title, R.string.m3_state_failed_body);
                refreshAll();
            });
        } finally {
            try { nfcv.close(); } catch (IOException ignored) { }
            reading.set(false);
        }
    }

    private void performLiveContact(NfcV nfcv, Tag tag) throws Exception {
        runOnUiThread(() -> setState(R.string.m3_state_reading_title, R.string.m3_state_reading_body));
        QalcosonicReader.Readout readout = new QalcosonicReader(nfcv, tag.getId()).read();
        MbusParser.MeterData meter = MbusParser.parse(readout.meterResponse);
        long now = System.currentTimeMillis();
        String active = lifecycleStore.activeMeterId();
        if (active == null) {
            lifecycleStore.adoptInitialMeter(meter.meterId);
            persistLive(meter, now);
            displayMeterId = meter.meterId;
            runOnUiThread(this::liveAcceptedUi);
            return;
        }
        if (!active.equals(meter.meterId)) {
            pendingMeter = new PendingMeter(meter, now, active);
            runOnUiThread(() -> showNewMeterDialog(pendingMeter));
            return;
        }
        persistLive(meter, now);
        displayMeterId = meter.meterId;
        runOnUiThread(this::liveAcceptedUi);
    }

    private void persistLive(MbusParser.MeterData meter, long atMs) {
        liveMetadataStore.record(meter, atMs);
        liveDetailStore.record(meter, atMs);
        try { liveStore.insertSuccessful(meter, atMs); } catch (RuntimeException ignored) { }
    }

    private void liveAcceptedUi() {
        setState(R.string.m3_state_live_ok_title, R.string.m3_state_live_ok_body);
        refreshAll();
    }

    private void showNewMeterDialog(PendingMeter candidate) {
        if (candidate == null || candidate != pendingMeter) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_new_meter_title)
                .setMessage(getString(R.string.m3_new_meter_body,
                        candidate.meter.meterId, candidate.previousMeterId))
                .setPositiveButton(R.string.m3_new_meter_replace,
                        (dialog, which) -> acceptReplacement(candidate))
                .setNeutralButton(R.string.m3_new_meter_export_reset, (dialog, which) -> {
                    if (candidate == pendingMeter) replacementCsvCreate.launch(replacementCsvFileName());
                })
                .setNegativeButton(R.string.m3_new_meter_cancel, (dialog, which) -> {
                    if (candidate == pendingMeter) pendingMeter = null;
                    showReady();
                    refreshAll();
                })
                .setOnCancelListener(dialog -> {
                    if (candidate == pendingMeter) pendingMeter = null;
                    showReady();
                    refreshAll();
                })
                .show();
    }

    private void acceptReplacement(PendingMeter candidate) {
        if (candidate == null || candidate != pendingMeter) return;
        try {
            persistLive(candidate.meter, candidate.readAtMs);
            lifecycleStore.confirmReplacement(candidate.previousMeterId, candidate.meter.meterId,
                    candidate.readAtMs, candidate.readAtMs,
                    candidate.meter.waterUsageM3 == null ? 0.0 : candidate.meter.waterUsageM3);
            displayMeterId = candidate.meter.meterId;
            pendingMeter = null;
            liveAcceptedUi();
        } catch (RuntimeException error) {
            pendingMeter = null;
            setState(R.string.m3_state_failed_title, R.string.m3_state_failed_body);
            refreshAll();
        }
    }

    private void registerReplacementExport() {
        replacementCsvCreate = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                    PendingMeter candidate = pendingMeter;
                    if (uri == null || candidate == null) return;
                    boolean saved = false;
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out != null) {
                            DataPortabilityCsvV3.writeCsv(this, out);
                            saved = true;
                        }
                    } catch (Exception ignored) { }
                    if (!saved) {
                        Snackbar.make(root, R.string.m3_export_failed, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.m3_delete_after_export_title)
                            .setMessage(getString(R.string.m3_delete_after_export_body,
                                    candidate.meter.meterId))
                            .setNegativeButton(R.string.m3_cancel, null)
                            .setPositiveButton(R.string.m3_delete_and_continue,
                                    (dialog, which) -> startFreshWith(candidate))
                            .show();
                });
    }

    private void startFreshWith(PendingMeter candidate) {
        if (candidate == null || candidate != pendingMeter) return;
        DataPortability.clearMeterData(this);
        lifecycleStore.startFresh(candidate.meter.meterId);
        persistLive(candidate.meter, candidate.readAtMs);
        displayMeterId = candidate.meter.meterId;
        pendingMeter = null;
        liveAcceptedUi();
    }

    private void buildUi() {
        root = MaterialUi.vertical(this);
        root.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(buildOverviewPage(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        applySystemInsets(root);
    }

    private ScrollView buildOverviewPage() {
        LinearLayout content = pageContent();

        MaterialCardView statusCard = MaterialUi.card(this);
        LinearLayout status = MaterialUi.cardContent(this);
        statusTitle = MaterialUi.title(this, "");
        statusBody = MaterialUi.body(this, "");
        statusBody.setPadding(0, MaterialUi.dp(this, 5), 0, 0);
        status.addView(statusTitle);
        status.addView(statusBody);
        statusCard.addView(status);
        content.addView(statusCard);

        MaterialCardView hero = new MaterialCardView(this);
        hero.setRadius(MaterialUi.dp(this, 24));
        hero.setCardElevation(0f);
        hero.setCardBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                getColor(R.color.app_primary_container)));
        LinearLayout heroContent = MaterialUi.cardContent(this);
        TextView heroLabel = MaterialUi.label(this, getString(R.string.m3_meter_reading));
        heroLabel.setTextColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                getColor(R.color.app_on_primary_container)));
        heroContent.addView(heroLabel);
        overviewReading = MaterialUi.headline(this, getString(R.string.m3_not_available));
        overviewReading.setTextSize(38f);
        overviewReading.setTextColor(heroLabel.getCurrentTextColor());
        overviewReading.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        heroContent.addView(overviewReading);
        overviewMeter = MaterialUi.title(this, getString(R.string.m3_no_meter));
        overviewMeter.setTextColor(heroLabel.getCurrentTextColor());
        overviewMeter.setPadding(0, MaterialUi.dp(this, 9), 0, 0);
        heroContent.addView(overviewMeter);
        overviewLastLive = MaterialUi.body(this, "");
        overviewLastLive.setTextColor(heroLabel.getCurrentTextColor());
        overviewLastLive.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        heroContent.addView(overviewLastLive);
        hero.addView(heroContent);
        MaterialUi.addTopMargin(content, hero, 12);

        LinearLayout metrics = MaterialUi.horizontal(this);
        overviewMonth = addMetric(metrics, R.string.m3_this_month);
        overviewYear = addMetric(metrics, R.string.m3_this_year);
        MaterialUi.addTopMargin(content, metrics, 10);

        alarmCard = MaterialUi.card(this);
        LinearLayout alarm = MaterialUi.cardContent(this);
        alarmTitle = MaterialUi.title(this, getString(R.string.m3_alarm_unknown_title));
        alarmBody = MaterialUi.body(this, getString(R.string.m3_alarm_unknown_body));
        alarmBody.setPadding(0, MaterialUi.dp(this, 5), 0, 0);
        alarm.addView(alarmTitle);
        alarm.addView(alarmBody);
        alarmCard.addView(alarm);
        MaterialUi.addTopMargin(content, alarmCard, 10);

        MaterialCardView data = MaterialUi.card(this);
        LinearLayout dataContent = MaterialUi.cardContent(this);
        dataContent.addView(MaterialUi.title(this, getString(R.string.m3_history_quality)));
        overviewBattery = addDataLine(dataContent, R.string.m3_battery);
        overviewLastArchive = addDataLine(dataContent, R.string.m3_last_archive);
        overviewHistoryState = addDataLine(dataContent, R.string.m3_history_quality);
        data.addView(dataContent);
        MaterialUi.addTopMargin(content, data, 10);

        return scroll(content);
    }

    private void refreshAll() {
        if (overviewReading == null || liveStore == null) return;
        displayMeterId = lifecycleStore.activeMeterId();
        if (displayMeterId == null) {
            displayMeterId = liveStore.getLatestMeterId();
            if (displayMeterId != null) lifecycleStore.adoptInitialMeter(displayMeterId);
        }
        List<String> chain = lifecycleStore.chainMeterIds();
        if (chain.isEmpty() && displayMeterId != null) chain = Collections.singletonList(displayMeterId);

        List<MeterLifecycleStore.Transition> transitions = lifecycleStore.transitions();
        List<WaterUsageAnalytics.Point> monthly = buildAnalyticsPoints(
                chain, ArchiveFamilyPeriod.Family.MONTH, true);
        List<WaterUsageAnalytics.Point> allPoints = transitions.isEmpty()
                ? monthly
                : buildAnalyticsPoints(chain, null, true);

        MeterHistoryStore.Reading latestLive = latestLive(displayMeterId);
        LiveReadMetadataStore.Summary liveMetadata = liveMetadataStore.get(displayMeterId);
        WaterUsageAnalytics.Point latestLivePoint = currentLivePoint(
                latestLive, liveMetadata, displayMeterId);
        WaterUsageAnalytics.Statistics statistics = WaterUsageAnalytics.statistics(
                monthlyOnly(monthly), latestLivePoint, System.currentTimeMillis());
        ConsumptionView consumption = consumptionView(statistics, allPoints, transitions);
        refreshOverview(liveMetadata, latestLive, archiveSummary(chain), consumption);
    }

    private List<WaterUsageAnalytics.Point> buildAnalyticsPoints(
            List<String> meters,
            ArchiveFamilyPeriod.Family archiveFamilyFilter,
            boolean includeLive) {
        List<WaterUsageAnalytics.Point> result = new ArrayList<>();
        if (meters == null) return result;
        for (String meter : meters) {
            if (includeLive) {
                for (MeterHistoryStore.Reading reading : liveStore.getReadings(meter, 0L)) {
                    result.add(new WaterUsageAnalytics.Point(
                            "LIVE|" + reading.id,
                            reading.meterId,
                            localMinute(reading.readAtMs),
                            reading.readAtMs,
                            HistorySemanticTimeline.Granularity.LIVE,
                            reading.totalM3));
                }
            }
            for (ArchiveFamilyStore.StoredPeriod archive :
                    archiveStore.getPeriods(meter, archiveFamilyFilter)) {
                Double total = parseMeasurement(archive.totalVolume);
                long sort = HistoryTimePresentation.floatingSortMs(archive.loggerTimestamp);
                if (total == null || sort <= 0L) continue;
                result.add(new WaterUsageAnalytics.Point(
                        archive.family.name() + "|" + archive.meterId + "|" + archive.loggerTimestamp,
                        archive.meterId,
                        archive.loggerTimestamp,
                        sort,
                        granularity(archive.family),
                        total));
            }
        }
        return result;
    }

    private static List<WaterUsageAnalytics.Point> monthlyOnly(
            List<WaterUsageAnalytics.Point> points) {
        List<WaterUsageAnalytics.Point> result = new ArrayList<>();
        for (WaterUsageAnalytics.Point point : points) {
            if (point.granularity == HistorySemanticTimeline.Granularity.MONTH) result.add(point);
        }
        return result;
    }

    private ConsumptionView consumptionView(
            WaterUsageAnalytics.Statistics statistics,
            List<WaterUsageAnalytics.Point> allPoints,
            List<MeterLifecycleStore.Transition> transitions) {
        if (transitions.isEmpty()) {
            return new ConsumptionView(
                    statistics.currentMonthM3, false,
                    statistics.currentYearM3, false);
        }
        long now = System.currentTimeMillis();
        Calendar month = Calendar.getInstance();
        month.setTimeInMillis(now);
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        Calendar year = Calendar.getInstance();
        year.setTimeInMillis(now);
        year.set(Calendar.MONTH, Calendar.JANUARY);
        year.set(Calendar.DAY_OF_MONTH, 1);
        year.set(Calendar.HOUR_OF_DAY, 0);
        year.set(Calendar.MINUTE, 0);
        year.set(Calendar.SECOND, 0);
        year.set(Calendar.MILLISECOND, 0);
        ReplacementChainAnalytics.Result monthResult = ReplacementChainAnalytics.periodConsumption(
                allPoints, transitions, month.getTimeInMillis(), now);
        ReplacementChainAnalytics.Result yearResult = ReplacementChainAnalytics.periodConsumption(
                allPoints, transitions, year.getTimeInMillis(), now);
        return new ConsumptionView(
                monthResult.knownConsumptionM3, monthResult.partial,
                yearResult.knownConsumptionM3, yearResult.partial);
    }

    private void refreshOverview(
            LiveReadMetadataStore.Summary meta,
            MeterHistoryStore.Reading latest,
            ArchiveSummary archiveSummary,
            ConsumptionView consumption) {
        boolean useMeta = meta.available() && (latest == null || meta.readAtMs >= latest.readAtMs);
        if (useMeta) {
            overviewReading.setText(formatM3(meta.totalM3));
            overviewMeter.setText(getString(R.string.m3_meter_id, displayMeterId));
            overviewLastLive.setText(getString(R.string.m3_last_read, formatDateTime(meta.readAtMs)));
            overviewBattery.setText(meta.batteryPercent == null
                    ? getString(R.string.m3_not_available)
                    : getString(R.string.m3_unit_percent, meta.batteryPercent));
            setAlarm(meta.alarmCodes, meta.readAtMs, true);
        } else if (latest != null) {
            overviewReading.setText(formatM3(latest.totalM3));
            overviewMeter.setText(getString(R.string.m3_meter_id, latest.meterId));
            overviewLastLive.setText(getString(R.string.m3_last_read, formatDateTime(latest.readAtMs)));
            overviewBattery.setText(latest.batteryPercent == null
                    ? getString(R.string.m3_not_available)
                    : getString(R.string.m3_unit_percent, latest.batteryPercent));
            setAlarm(latest.alarmCodes, latest.readAtMs, true);
        } else {
            overviewReading.setText(R.string.m3_not_available);
            overviewMeter.setText(R.string.m3_no_meter);
            overviewLastLive.setText("");
            overviewBattery.setText(R.string.m3_not_available);
            setAlarm(null, 0L, false);
        }

        overviewMonth.setText(formatConsumption(consumption.monthM3, consumption.monthPartial));
        overviewYear.setText(formatConsumption(consumption.yearM3, consumption.yearPartial));
        overviewLastArchive.setText(archiveSummary.lastArchiveMs <= 0L
                ? getString(R.string.m3_no_archive)
                : formatDateTime(archiveSummary.lastArchiveMs));
        overviewHistoryState.setText(archiveSummary.count == 0
                ? getString(R.string.m3_no_archive)
                : getString(R.string.m3_archive_summary,
                        archiveSummary.count, archiveSummary.confirmed, archiveSummary.conflicts));
    }

    private ArchiveSummary archiveSummary(List<String> meters) {
        ArchiveSummary summary = new ArchiveSummary();
        if (meters == null || archiveStore == null) return summary;
        String sql = "SELECT COUNT(*), MAX(retrieved_at_ms), "
                + "COALESCE(SUM(CASE WHEN identical_content_confirmations > 0 THEN 1 ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN conflict_flags != 0 THEN 1 ELSE 0 END),0) "
                + "FROM " + ArchiveFamilyStore.TABLE_PERIODS + " WHERE meter_id = ?";
        for (String meter : meters) {
            if (meter == null || meter.trim().isEmpty()) continue;
            try (Cursor cursor = archiveStore.getReadableDatabase().rawQuery(
                    sql, new String[]{meter.trim()})) {
                if (!cursor.moveToFirst()) continue;
                summary.count += cursor.getInt(0);
                if (!cursor.isNull(1)) {
                    summary.lastArchiveMs = Math.max(summary.lastArchiveMs, cursor.getLong(1));
                }
                summary.confirmed += cursor.getInt(2);
                summary.conflicts += cursor.getInt(3);
            }
        }
        return summary;
    }

    private void setAlarm(String codes, long atMs, boolean available) {
        if (!available) {
            alarmTitle.setText(R.string.m3_alarm_unknown_title);
            alarmBody.setText(R.string.m3_alarm_unknown_body);
            alarmCard.setStrokeColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorOutline,
                    getColor(R.color.app_outline)));
            return;
        }
        if (codes != null && !codes.trim().isEmpty()) {
            alarmTitle.setText(R.string.m3_alarm_active_title);
            alarmBody.setText(getString(R.string.m3_alarm_active_body,
                    MeterStatusPresentation.localizedAlarmCodes(this, codes),
                    formatDateTime(atMs)));
            alarmCard.setStrokeColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorError,
                    getColor(R.color.app_error)));
        } else {
            alarmTitle.setText(R.string.m3_alarm_clear_title);
            alarmBody.setText(getString(R.string.m3_alarm_clear_body, formatDateTime(atMs)));
            alarmCard.setStrokeColor(getColor(R.color.app_success));
        }
    }

    private MeterHistoryStore.Reading latestLive(String meterId) {
        if (meterId == null) return null;
        List<MeterHistoryStore.Reading> values = liveStore.getReadings(meterId, 0L);
        return values.isEmpty() ? null : values.get(0);
    }

    private WaterUsageAnalytics.Point currentLivePoint(
            MeterHistoryStore.Reading latest,
            LiveReadMetadataStore.Summary meta,
            String meterId) {
        if (meterId == null) return null;
        if (meta.available() && (latest == null || meta.readAtMs >= latest.readAtMs)) {
            return new WaterUsageAnalytics.Point(
                    "LIVE_CURRENT|" + meta.readAtMs,
                    meterId,
                    localMinute(meta.readAtMs),
                    meta.readAtMs,
                    HistorySemanticTimeline.Granularity.LIVE,
                    meta.totalM3);
        }
        return latest == null ? null : new WaterUsageAnalytics.Point(
                "LIVE|" + latest.id,
                meterId,
                localMinute(latest.readAtMs),
                latest.readAtMs,
                HistorySemanticTimeline.Granularity.LIVE,
                latest.totalM3);
    }

    private void showReady() {
        if (nfcAdapter == null) {
            setState(R.string.m3_state_no_nfc_title, R.string.m3_state_no_nfc_body);
        } else if (!nfcAdapter.isEnabled()) {
            setState(R.string.m3_state_nfc_off_title, R.string.m3_state_nfc_off_body);
        } else {
            setState(R.string.m3_state_ready_title, R.string.m3_state_ready_body);
        }
    }

    private void setState(int titleRes, int bodyRes) {
        if (statusTitle == null) return;
        statusTitle.setText(titleRes);
        statusBody.setText(bodyRes);
    }

    void selectNavigationItem(int navItemId) {
        // History and Statistics are separate Activities. The dashboard owns Overview only.
        refreshAll();
    }

    int currentNavigationItemId() {
        return NAV_OVERVIEW;
    }

    private LinearLayout pageContent() {
        LinearLayout content = MaterialUi.vertical(this);
        int p = MaterialUi.dp(this, 16);
        content.setPadding(p, p, p, MaterialUi.dp(this, 32));
        return content;
    }

    private ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView addMetric(LinearLayout parent, int labelRes) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout content = MaterialUi.cardContent(this);
        content.addView(MaterialUi.label(this, getString(labelRes)));
        TextView value = MaterialUi.headline(this, getString(R.string.m3_not_available));
        value.setTextSize(22f);
        value.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(value);
        card.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (parent.getChildCount() > 0) lp.setMarginStart(MaterialUi.dp(this, 8));
        parent.addView(card, lp);
        return value;
    }

    private TextView addDataLine(LinearLayout parent, int labelRes) {
        LinearLayout row = MaterialUi.horizontal(this);
        row.setPadding(0, MaterialUi.dp(this, 10), 0, 0);
        TextView label = MaterialUi.body(this, getString(labelRes));
        TextView value = MaterialUi.body(this, getString(R.string.m3_not_available));
        value.setGravity(Gravity.END);
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueLp.setMarginStart(MaterialUi.dp(this, 12));
        row.addView(value, valueLp);
        parent.addView(row);
        return value;
    }

    private String formatM3(Double value) {
        return value == null || value.isNaN()
                ? getString(R.string.m3_not_available)
                : getString(R.string.m3_unit_m3, value);
    }

    private String formatConsumption(Double value, boolean partial) {
        if (value == null) return getString(R.string.m3_not_available);
        return formatM3(value) + (partial ? "*" : "");
    }

    private String formatDateTime(long ms) {
        if (ms <= 0L) return getString(R.string.m3_not_available);
        return DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                getResources().getConfiguration().getLocales().get(0))
                .format(new Date(ms));
    }

    private static Double parseMeasurement(String value) {
        String number = ArchiveFamilyStore.measurementNumber(value);
        if (number == null || number.isEmpty()) return null;
        try { return Double.parseDouble(number); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String localMinute(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }

    private static HistorySemanticTimeline.Granularity granularity(
            ArchiveFamilyPeriod.Family family) {
        switch (family) {
            case HOUR: return HistorySemanticTimeline.Granularity.HOUR;
            case DAY: return HistorySemanticTimeline.Granularity.DAY;
            case YEAR: return HistorySemanticTimeline.Granularity.YEAR;
            case MONTH:
            default: return HistorySemanticTimeline.Granularity.MONTH;
        }
    }

    private static String replacementCsvFileName() {
        return "qalcosonic-w1-old-data-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date())
                + ".csv";
    }

    private static final class PendingMeter {
        final MbusParser.MeterData meter;
        final long readAtMs;
        final String previousMeterId;

        PendingMeter(MbusParser.MeterData meter, long readAtMs, String previousMeterId) {
            this.meter = meter;
            this.readAtMs = readAtMs;
            this.previousMeterId = previousMeterId;
        }
    }

    private static final class ConsumptionView {
        final Double monthM3;
        final boolean monthPartial;
        final Double yearM3;
        final boolean yearPartial;

        ConsumptionView(Double monthM3, boolean monthPartial, Double yearM3, boolean yearPartial) {
            this.monthM3 = monthM3;
            this.monthPartial = monthPartial;
            this.yearM3 = yearM3;
            this.yearPartial = yearPartial;
        }
    }

    private static final class ArchiveSummary {
        long lastArchiveMs;
        int count;
        int confirmed;
        int conflicts;
    }
}
