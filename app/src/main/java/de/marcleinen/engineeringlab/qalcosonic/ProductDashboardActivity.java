package de.marcleinen.engineeringlab.qalcosonic;

import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 0.8.0 Material 3 product surface on the protected Live + Monthly protocol baseline.
 * Day/Hour/Year acquisition remains disabled until separately physically validated.
 */
public final class ProductDashboardActivity extends MaterialBaseActivity implements NfcAdapter.ReaderCallback {
    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_HISTORY = 1;
    private static final int PAGE_STATS = 2;
    private static final int NAV_OVERVIEW = 0x8101;
    private static final int NAV_HISTORY = 0x8102;
    private static final int NAV_STATS = 0x8103;
    private static final int STATS_CURRENT_YEAR = 0;
    private static final int STATS_LAST_12 = 1;

    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final HistorySyncFlow syncFlow = new HistorySyncFlow();

    private NfcAdapter nfcAdapter;
    private MeterHistoryStore liveStore;
    private ArchiveFamilyStore archiveStore;
    private HistorySyncMetadataStore syncMetadataStore;
    private LiveReadMetadataStore liveMetadataStore;
    private LiveDetailMetadataStore liveDetailStore;
    private MeterLifecycleStore lifecycleStore;
    private String displayMeterId;

    private LinearLayout root;
    private FrameLayout pageHost;
    private final View[] pages = new View[3];
    private int currentPage = PAGE_OVERVIEW;

    private MaterialCardView statusCard;
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
    private MaterialButton historyButton;
    private TextView syncHint;

    private ChipGroup historyFilterHost;
    private LinearLayout historyList;
    private HistorySemanticTimeline.Granularity historyFilter;

    private TextView statsMonth;
    private TextView statsYear;
    private TextView statsAverage;
    private TextView statsLastMonth;
    private ChipGroup statsRangeGroup;
    private ChipGroup statsModeGroup;
    private WaterUsageChartView statsChart;
    private TextView statsChartNote;
    private int statsRange = STATS_CURRENT_YEAR;
    private WaterUsageChartView.Mode statsMode = WaterUsageChartView.Mode.BARS;

    private ActivityResultLauncher<String> replacementCsvCreate;
    private PendingMeter pendingMeter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerReplacementExport();
        liveStore = new MeterHistoryStore(getApplicationContext());
        archiveStore = new ArchiveFamilyStore(getApplicationContext());
        syncMetadataStore = new HistorySyncMetadataStore(getApplicationContext());
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
            if (syncFlow.state() == HistorySyncFlow.State.ARMED_WAITING_FOR_METER) {
                performHistoryContact(nfcv, tag);
            } else {
                performLiveContact(nfcv, tag);
            }
        } catch (Exception error) {
            if (syncFlow.state() == HistorySyncFlow.State.RUNNING) {
                try { syncFlow.onSyncFailed(); } catch (RuntimeException ignored) { }
            }
            runOnUiThread(() -> {
                setState(R.string.m3_state_failed_title, R.string.m3_state_failed_body);
                updateHistoryButton();
                refreshAll();
            });
        } finally {
            try { nfcv.close(); } catch (IOException ignored) { }
            reading.set(false);
        }
    }

    private void performLiveContact(NfcV nfcv, Tag tag) throws Exception {
        runOnUiThread(() -> {
            setState(R.string.m3_state_reading_title, R.string.m3_state_reading_body);
            showPage(PAGE_OVERVIEW);
        });
        QalcosonicReader.Readout readout = new QalcosonicReader(nfcv, tag.getId()).read();
        MbusParser.MeterData meter = MbusParser.parse(readout.meterResponse);
        long now = System.currentTimeMillis();
        String active = lifecycleStore.activeMeterId();
        if (active == null) {
            lifecycleStore.adoptInitialMeter(meter.meterId);
            persistLive(meter, now);
            displayMeterId = meter.meterId;
            syncFlow.onLiveReadSucceeded(meter.meterId);
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
        syncFlow.onLiveReadSucceeded(meter.meterId);
        runOnUiThread(this::liveAcceptedUi);
    }

    private void performHistoryContact(NfcV nfcv, Tag tag) throws Exception {
        runOnUiThread(() -> setState(R.string.m3_state_verify_title, R.string.m3_state_verify_body));
        QalcosonicReader.Readout verification = new QalcosonicReader(nfcv, tag.getId()).read();
        MbusParser.MeterData meter = MbusParser.parse(verification.meterResponse);
        HistorySyncFlow.MeterPresentationResult presented = syncFlow.onMeterPresented(meter.meterId);
        if (presented == HistorySyncFlow.MeterPresentationResult.WRONG_METER) {
            runOnUiThread(() -> {
                statusTitle.setText(R.string.m3_wrong_meter_title);
                statusBody.setText(getString(R.string.m3_wrong_meter_body, syncFlow.targetMeterId(), meter.meterId));
                updateHistoryButton();
            });
            return;
        }
        if (presented != HistorySyncFlow.MeterPresentationResult.STARTED) return;

        long verificationReadAt = System.currentTimeMillis();
        persistLive(meter, verificationReadAt);
        runOnUiThread(() -> setState(R.string.m3_state_sync_title, R.string.m3_state_sync_body));

        String retrievedAtUtc = utcNow();
        MonthlyArchiveNfcWire wire = new MonthlyArchiveNfcWire(nfcv, tag.getId());
        MonthlyArchiveTransportAdapter.Result transport = MonthlyArchiveTransportAdapter.run(
                MonthlyArchiveEnumerator.VENDOR_HARD_CAP_CANDIDATE, wire, wire, retrievedAtUtc);

        List<ArchiveFamilyPeriod> generic = new ArrayList<>();
        for (MonthlyArchivePeriod period : transport.enumeration.periods) {
            generic.add(ArchiveFamilyPeriod.fromMonthly(period));
        }
        ArchivePersistenceCoordinator.Result persisted = ArchivePersistenceCoordinator.persist(
                archiveStore, meter.meterId, ArchiveFamilyPeriod.Family.MONTH, generic);

        boolean traversalComplete = transport.enumeration.terminalConfirmed()
                || transport.enumeration.stopReason == MonthlyArchiveEnumerator.StopReason.HARD_CAP_REACHED;
        boolean restoreComplete = transport.restore != null && transport.restore.defaultVerified;
        boolean persistenceComplete = persisted.complete();
        boolean hasAccepted = !transport.enumeration.periods.isEmpty();

        boolean complete;
        boolean partial;
        if (traversalComplete && restoreComplete && persistenceComplete) {
            syncFlow.onSyncCompleted();
            complete = true;
            partial = false;
        } else if (hasAccepted || persisted.committed > 0) {
            syncFlow.onSyncPartialSuccess();
            complete = false;
            partial = true;
        } else {
            syncFlow.onSyncFailed();
            complete = false;
            partial = false;
        }

        long finishedAt = System.currentTimeMillis();
        syncMetadataStore.record(meter.meterId, finishedAt, complete, partial,
                transport.enumeration.periods.size(), persisted.inserted, persisted.confirmed, persisted.conflicts);
        displayMeterId = meter.meterId;
        runOnUiThread(() -> {
            if (complete) setState(R.string.m3_state_sync_ok_title, R.string.m3_state_sync_ok_body);
            else if (partial) setState(R.string.m3_state_sync_partial_title, R.string.m3_state_sync_partial_body);
            else setState(R.string.m3_state_sync_failed_title, R.string.m3_state_sync_failed_body);
            updateHistoryButton();
            showPage(PAGE_OVERVIEW);
            refreshAll();
        });
    }

    private void persistLive(MbusParser.MeterData meter, long atMs) {
        liveMetadataStore.record(meter, atMs);
        liveDetailStore.record(meter, atMs);
        try { liveStore.insertSuccessful(meter, atMs); } catch (RuntimeException ignored) { }
    }

    private void liveAcceptedUi() {
        setState(R.string.m3_state_live_ok_title, R.string.m3_state_live_ok_body);
        updateHistoryButton();
        showPage(PAGE_OVERVIEW);
        refreshAll();
    }

    private void showNewMeterDialog(PendingMeter candidate) {
        if (candidate == null || candidate != pendingMeter) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_new_meter_title)
                .setMessage(getString(R.string.m3_new_meter_body, candidate.meter.meterId, candidate.previousMeterId))
                .setPositiveButton(R.string.m3_new_meter_replace, (dialog, which) -> acceptReplacement(candidate))
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
            syncFlow.onLiveReadSucceeded(displayMeterId);
            liveAcceptedUi();
        } catch (RuntimeException error) {
            pendingMeter = null;
            setState(R.string.m3_state_failed_title, R.string.m3_state_failed_body);
            refreshAll();
        }
    }

    private void registerReplacementExport() {
        replacementCsvCreate = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            PendingMeter candidate = pendingMeter;
            if (uri == null || candidate == null) return;
            boolean saved = false;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out != null) {
                    DataPortability.writeCsv(this, out);
                    saved = true;
                }
            } catch (Exception ignored) { }
            if (!saved) {
                Snackbar.make(root, R.string.m3_export_failed, Snackbar.LENGTH_LONG).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.m3_delete_after_export_title)
                    .setMessage(getString(R.string.m3_delete_after_export_body, candidate.meter.meterId))
                    .setNegativeButton(R.string.m3_cancel, null)
                    .setPositiveButton(R.string.m3_delete_and_continue, (dialog, which) -> startFreshWith(candidate))
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
        syncFlow.onLiveReadSucceeded(displayMeterId);
        liveAcceptedUi();
    }

    private void buildUi() {
        root = MaterialUi.vertical(this);
        root.setBackgroundColor(MaterialUi.color(this, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pageHost = new FrameLayout(this);
        pages[PAGE_OVERVIEW] = buildOverviewPage();
        pages[PAGE_HISTORY] = buildHistoryPage();
        pages[PAGE_STATS] = buildStatsPage();
        for (View page : pages) pageHost.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(pageHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applySystemInsets(root);
        showPage(PAGE_OVERVIEW);
    }

    private View buildOverviewPage() {
        LinearLayout content = pageContent();

        statusCard = MaterialUi.card(this);
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
                com.google.android.material.R.attr.colorPrimaryContainer, getColor(R.color.app_primary_container)));
        LinearLayout heroContent = MaterialUi.cardContent(this);
        TextView heroLabel = MaterialUi.label(this, getString(R.string.m3_meter_reading));
        heroLabel.setTextColor(MaterialUi.color(this, com.google.android.material.R.attr.colorOnPrimaryContainer,
                getColor(R.color.app_on_primary_container)));
        heroContent.addView(heroLabel);
        overviewReading = MaterialUi.headline(this, getString(R.string.m3_not_available));
        overviewReading.setTextSize(38f);
        overviewReading.setTextColor(MaterialUi.color(this, com.google.android.material.R.attr.colorOnPrimaryContainer,
                getColor(R.color.app_on_primary_container)));
        overviewReading.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        heroContent.addView(overviewReading);
        overviewMeter = MaterialUi.title(this, getString(R.string.m3_no_meter));
        overviewMeter.setTextColor(overviewReading.getCurrentTextColor());
        overviewMeter.setPadding(0, MaterialUi.dp(this, 9), 0, 0);
        heroContent.addView(overviewMeter);
        overviewLastLive = MaterialUi.body(this, "");
        overviewLastLive.setTextColor(overviewReading.getCurrentTextColor());
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

        historyButton = new MaterialButton(this);
        historyButton.setText(R.string.m3_sync_history);
        historyButton.setMinHeight(MaterialUi.dp(this, 48));
        historyButton.setOnClickListener(v -> toggleHistorySync());
        MaterialUi.addTopMargin(content, historyButton, 12);
        syncHint = MaterialUi.body(this, getString(R.string.m3_calculated_note));
        syncHint.setGravity(Gravity.CENTER_HORIZONTAL);
        syncHint.setPadding(MaterialUi.dp(this, 8), MaterialUi.dp(this, 6), MaterialUi.dp(this, 8), 0);
        content.addView(syncHint);
        return scroll(content);
    }

    private View buildHistoryPage() {
        LinearLayout content = pageContent();
        content.addView(MaterialUi.headline(this, getString(R.string.m3_history_title)));
        TextView subtitle = MaterialUi.body(this, getString(R.string.m3_history_subtitle));
        subtitle.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(subtitle);
        historyFilterHost = new ChipGroup(this);
        historyFilterHost.setSingleSelection(true);
        historyFilterHost.setSelectionRequired(false);
        historyFilterHost.setChipSpacingHorizontal(MaterialUi.dp(this, 6));
        historyFilterHost.setChipSpacingVertical(MaterialUi.dp(this, 6));
        MaterialUi.addTopMargin(content, historyFilterHost, 12);
        historyList = MaterialUi.vertical(this);
        MaterialUi.addTopMargin(content, historyList, 10);
        return scroll(content);
    }

    private View buildStatsPage() {
        LinearLayout content = pageContent();
        content.addView(MaterialUi.headline(this, getString(R.string.m3_stats_title)));
        TextView subtitle = MaterialUi.body(this, getString(R.string.m3_stats_subtitle));
        subtitle.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(subtitle);

        statsMonth = addStandaloneMetric(content, R.string.m3_this_month, 12);
        statsYear = addStandaloneMetric(content, R.string.m3_this_year, 8);
        statsAverage = addStandaloneMetric(content, R.string.m3_average_complete_month, 8);
        statsLastMonth = addStandaloneMetric(content, R.string.m3_latest_complete_month, 8);

        statsRangeGroup = new ChipGroup(this);
        statsRangeGroup.setSingleSelection(true);
        statsRangeGroup.addView(statsChip(R.string.m3_range_current_year, true, v -> { statsRange = STATS_CURRENT_YEAR; refreshAll(); }));
        statsRangeGroup.addView(statsChip(R.string.m3_range_last_12, false, v -> { statsRange = STATS_LAST_12; refreshAll(); }));
        MaterialUi.addTopMargin(content, statsRangeGroup, 14);

        statsModeGroup = new ChipGroup(this);
        statsModeGroup.setSingleSelection(true);
        statsModeGroup.addView(statsChip(R.string.m3_chart_consumption, true, v -> { statsMode = WaterUsageChartView.Mode.BARS; refreshAll(); }));
        statsModeGroup.addView(statsChip(R.string.m3_chart_reading, false, v -> { statsMode = WaterUsageChartView.Mode.LINE; refreshAll(); }));
        MaterialUi.addTopMargin(content, statsModeGroup, 8);

        MaterialCardView chartCard = MaterialUi.card(this);
        LinearLayout chartContent = MaterialUi.cardContent(this);
        statsChart = new WaterUsageChartView(this);
        statsChart.setEmptyText(getString(R.string.m3_not_available));
        statsChart.setContentDescription(getString(R.string.m3_chart_accessibility));
        chartContent.addView(statsChart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        statsChartNote = MaterialUi.body(this, getString(R.string.m3_chart_accessibility));
        chartContent.addView(statsChartNote);
        chartCard.addView(chartContent);
        MaterialUi.addTopMargin(content, chartCard, 10);
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
        List<HistoryItem> items = buildHistoryItems(chain);
        List<WaterUsageAnalytics.Point> allPoints = new ArrayList<>();
        List<WaterUsageAnalytics.Point> monthly = new ArrayList<>();
        for (HistoryItem item : items) {
            allPoints.add(item.point);
            if (item.point.granularity == HistorySemanticTimeline.Granularity.MONTH) monthly.add(item.point);
        }
        MeterHistoryStore.Reading latestLive = latestLive(displayMeterId);
        LiveReadMetadataStore.Summary liveMetadata = liveMetadataStore.get(displayMeterId);
        WaterUsageAnalytics.Point latestLivePoint = currentLivePoint(latestLive, liveMetadata, displayMeterId);
        WaterUsageAnalytics.Statistics statistics = WaterUsageAnalytics.statistics(monthly, latestLivePoint, System.currentTimeMillis());
        ConsumptionView consumption = consumptionView(statistics, allPoints);
        refreshOverview(liveMetadata, latestLive, chain, consumption);
        refreshHistory(items);
        refreshStatistics(monthly, latestLivePoint, statistics, consumption);
        updateHistoryButton();
    }

    private ConsumptionView consumptionView(WaterUsageAnalytics.Statistics statistics,
                                             List<WaterUsageAnalytics.Point> allPoints) {
        List<MeterLifecycleStore.Transition> transitions = lifecycleStore.transitions();
        if (transitions.isEmpty()) {
            return new ConsumptionView(statistics.currentMonthM3, false, statistics.currentYearM3, false);
        }
        long now = System.currentTimeMillis();
        Calendar month = Calendar.getInstance();
        month.setTimeInMillis(now); month.set(Calendar.DAY_OF_MONTH, 1); month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0); month.set(Calendar.SECOND, 0); month.set(Calendar.MILLISECOND, 0);
        Calendar year = Calendar.getInstance();
        year.setTimeInMillis(now); year.set(Calendar.MONTH, Calendar.JANUARY); year.set(Calendar.DAY_OF_MONTH, 1);
        year.set(Calendar.HOUR_OF_DAY, 0); year.set(Calendar.MINUTE, 0); year.set(Calendar.SECOND, 0); year.set(Calendar.MILLISECOND, 0);
        ReplacementChainAnalytics.Result monthResult = ReplacementChainAnalytics.periodConsumption(
                allPoints, transitions, month.getTimeInMillis(), now);
        ReplacementChainAnalytics.Result yearResult = ReplacementChainAnalytics.periodConsumption(
                allPoints, transitions, year.getTimeInMillis(), now);
        return new ConsumptionView(monthResult.knownConsumptionM3, monthResult.partial,
                yearResult.knownConsumptionM3, yearResult.partial);
    }

    private void refreshOverview(LiveReadMetadataStore.Summary meta, MeterHistoryStore.Reading latest,
                                 List<String> chain, ConsumptionView consumption) {
        boolean useMeta = meta.available() && (latest == null || meta.readAtMs >= latest.readAtMs);
        if (useMeta) {
            overviewReading.setText(formatM3(meta.totalM3));
            overviewMeter.setText(getString(R.string.m3_meter_id, displayMeterId));
            overviewLastLive.setText(getString(R.string.m3_last_read, formatDateTime(meta.readAtMs)));
            overviewBattery.setText(meta.batteryPercent == null ? getString(R.string.m3_not_available)
                    : getString(R.string.m3_unit_percent, meta.batteryPercent));
            setAlarm(meta.alarmCodes, meta.readAtMs, true);
        } else if (latest != null) {
            overviewReading.setText(formatM3(latest.totalM3));
            overviewMeter.setText(getString(R.string.m3_meter_id, latest.meterId));
            overviewLastLive.setText(getString(R.string.m3_last_read, formatDateTime(latest.readAtMs)));
            overviewBattery.setText(latest.batteryPercent == null ? getString(R.string.m3_not_available)
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

        long lastArchive = 0L;
        int count = 0, confirmed = 0, conflicts = 0;
        for (String meter : chain) {
            for (ArchiveFamilyStore.StoredPeriod period : archiveStore.getPeriods(meter, null)) {
                count++;
                lastArchive = Math.max(lastArchive, period.retrievedAtMs);
                if (period.identicalContentConfirmations > 0) confirmed++;
                if (period.conflictFlags != 0) conflicts++;
            }
        }
        overviewLastArchive.setText(lastArchive <= 0 ? getString(R.string.m3_no_archive) : formatDateTime(lastArchive));
        overviewHistoryState.setText(count == 0 ? getString(R.string.m3_no_archive)
                : getString(R.string.m3_archive_summary, count, confirmed, conflicts));

        HistorySyncMetadataStore.Summary sync = syncMetadataStore.get(displayMeterId);
        if (sync.lastAttemptComplete()) {
            syncHint.setText(getString(R.string.m3_state_sync_ok_body));
        } else if (sync.lastAttemptPartial()) {
            syncHint.setText(getString(R.string.m3_state_sync_partial_body));
        } else if (sync.lastAttemptFailed()) {
            syncHint.setText(getString(R.string.m3_state_sync_failed_body));
        } else {
            syncHint.setText(R.string.m3_calculated_note);
        }
    }

    private void setAlarm(String codes, long atMs, boolean available) {
        if (!available) {
            alarmTitle.setText(R.string.m3_alarm_unknown_title);
            alarmBody.setText(R.string.m3_alarm_unknown_body);
            alarmCard.setStrokeColor(MaterialUi.color(this, com.google.android.material.R.attr.colorOutline, getColor(R.color.app_outline)));
            return;
        }
        if (codes != null && !codes.trim().isEmpty()) {
            alarmTitle.setText(R.string.m3_alarm_active_title);
            alarmBody.setText(getString(R.string.m3_alarm_active_body,
                    MeterStatusPresentation.localizedAlarmCodes(this, codes), formatDateTime(atMs)));
            alarmCard.setStrokeColor(MaterialUi.color(this, com.google.android.material.R.attr.colorError, getColor(R.color.app_error)));
        } else {
            alarmTitle.setText(R.string.m3_alarm_clear_title);
            alarmBody.setText(getString(R.string.m3_alarm_clear_body, formatDateTime(atMs)));
            alarmCard.setStrokeColor(getColor(R.color.app_success));
        }
    }

    private void refreshHistory(List<HistoryItem> items) {
        historyFilterHost.removeAllViews();
        Set<HistorySemanticTimeline.Granularity> available = new LinkedHashSet<>();
        for (HistoryItem item : items) available.add(item.point.granularity);
        if (historyFilter != null && !available.contains(historyFilter)) historyFilter = null;
        addHistoryFilter(null, R.string.m3_filter_all);
        HistorySemanticTimeline.Granularity[] order = {HistorySemanticTimeline.Granularity.LIVE,
                HistorySemanticTimeline.Granularity.HOUR, HistorySemanticTimeline.Granularity.DAY,
                HistorySemanticTimeline.Granularity.MONTH, HistorySemanticTimeline.Granularity.YEAR};
        for (HistorySemanticTimeline.Granularity g : order) {
            if (available.contains(g)) addHistoryFilter(g, filterLabel(g));
        }

        List<WaterUsageAnalytics.Point> analytics = new ArrayList<>();
        Map<String, HistoryItem> byId = new HashMap<>();
        for (HistoryItem item : items) { analytics.add(item.point); byId.put(item.point.identity, item); }
        List<TimelineEntry> timeline = new ArrayList<>();
        for (WaterUsageAnalytics.HistoryDelta delta : WaterUsageAnalytics.historyNewestFirst(analytics)) {
            HistoryItem item = byId.get(delta.point.identity);
            if (item != null && (historyFilter == null || item.point.granularity == historyFilter)) {
                timeline.add(TimelineEntry.data(item.point.sortMs, item, delta));
            }
        }
        if (historyFilter == null) {
            for (MeterLifecycleStore.Transition transition : lifecycleStore.transitions()) {
                timeline.add(TimelineEntry.transition(transition));
            }
        }
        timeline.sort((a,b) -> Long.compare(b.sortMs, a.sortMs));
        historyList.removeAllViews();
        for (TimelineEntry entry : timeline) {
            if (entry.transition != null) addReplacementCard(entry.transition);
            else addHistoryCard(entry.item, entry.delta);
        }
        if (timeline.isEmpty()) historyList.addView(emptyCard(R.string.m3_history_empty));
    }

    private void addHistoryCard(HistoryItem item, WaterUsageAnalytics.HistoryDelta delta) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout content = MaterialUi.cardContent(this);
        TextView head = MaterialUi.label(this, item.primaryDisplay + " · " + typeLabel(item.point.granularity));
        content.addView(head);
        TextView reading = MaterialUi.headline(this, formatM3(item.point.totalM3));
        reading.setTextSize(22f);
        reading.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(reading);
        if (delta.consumptionSincePreviousM3 != null && delta.previousPoint != null) {
            TextView consumption = MaterialUi.body(this, getString(R.string.m3_consumption_since,
                    formatM3(delta.consumptionSincePreviousM3), shortTimestamp(delta.previousPoint.timestamp)));
            consumption.setTypeface(consumption.getTypeface(), Typeface.BOLD);
            consumption.setTextColor(MaterialUi.color(this, com.google.android.material.R.attr.colorPrimary,
                    getColor(R.color.app_primary)));
            content.addView(consumption);
        }
        TextView meter = MaterialUi.body(this, getString(R.string.m3_meter_id, item.point.meterId));
        meter.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(meter);
        if (item.meterTime != null && !item.meterTime.isEmpty()) {
            content.addView(MaterialUi.body(this, getString(R.string.m3_meter_time, item.meterTime)));
        }
        if (item.batteryPercent != null) {
            content.addView(MaterialUi.body(this, getString(R.string.m3_battery) + ": "
                    + getString(R.string.m3_unit_percent, item.batteryPercent)));
        }
        if (item.liveAlarmCodes != null && !item.liveAlarmCodes.isEmpty()) {
            TextView warning = MaterialUi.body(this, getString(R.string.m3_alarm_active_title) + ": "
                    + MeterStatusPresentation.localizedAlarmCodes(this, item.liveAlarmCodes));
            warning.setTextColor(MaterialUi.color(this, com.google.android.material.R.attr.colorError,
                    getColor(R.color.app_error)));
            content.addView(warning);
        } else if (item.historical != null && item.historical.hasAnyStatus()) {
            MaterialButton notice = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            notice.setText(getString(R.string.m3_historical_notice, item.historical.summary(this)));
            notice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            notice.setOnClickListener(v -> showHistoricalStatus(item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = MaterialUi.dp(this, 6);
            content.addView(notice, lp);
        }
        card.addView(content);
        MaterialUi.addTopMargin(historyList, card, historyList.getChildCount() == 0 ? 0 : 8);
    }

    private void addReplacementCard(MeterLifecycleStore.Transition transition) {
        MaterialCardView card = MaterialUi.card(this);
        card.setStrokeColor(MaterialUi.color(this, com.google.android.material.R.attr.colorPrimary,
                getColor(R.color.app_primary)));
        LinearLayout content = MaterialUi.cardContent(this);
        content.addView(MaterialUi.title(this, getString(R.string.m3_meter_replacement_event)));
        content.addView(MaterialUi.body(this, formatDateTime(transition.confirmedAtMs)));
        content.addView(MaterialUi.body(this, getString(R.string.m3_meter_replacement_ids,
                transition.predecessorMeterId, transition.successorMeterId)));
        TextView partial = MaterialUi.body(this, getString(R.string.m3_partial_period));
        partial.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(partial);
        card.addView(content);
        MaterialUi.addTopMargin(historyList, card, historyList.getChildCount() == 0 ? 0 : 8);
    }

    private void showHistoricalStatus(HistoryItem item) {
        StringBuilder body = new StringBuilder();
        body.append(getString(R.string.m3_historical_notice, item.historical.summary(this))).append("\n\n")
                .append(getString(R.string.m3_raw_status, item.historical.raw)).append("\n")
                .append(getString(R.string.m3_archive_time, item.primaryDisplay)).append("\n")
                .append(getString(R.string.m3_archive_source));
        if (item.historical.hasUnknownBits) body.append("\n\n").append(getString(R.string.m3_unknown_status_explanation));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_more_info)
                .setMessage(body.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void refreshStatistics(List<WaterUsageAnalytics.Point> monthly,
                                   WaterUsageAnalytics.Point latestLive,
                                   WaterUsageAnalytics.Statistics statistics,
                                   ConsumptionView consumption) {
        statsMonth.setText(formatConsumption(consumption.monthM3, consumption.monthPartial));
        statsYear.setText(formatConsumption(consumption.yearM3, consumption.yearPartial));
        statsAverage.setText(formatM3(statistics.averageCompleteMonthM3));
        if (statistics.lastCompleteMonthM3 == null) {
            statsLastMonth.setText(R.string.m3_not_available);
        } else if (statistics.lastCompleteMonthChangePercent == null) {
            statsLastMonth.setText(formatM3(statistics.lastCompleteMonthM3));
        } else {
            String percent = String.format(Locale.getDefault(), "%+.0f %%", statistics.lastCompleteMonthChangePercent);
            statsLastMonth.setText(formatM3(statistics.lastCompleteMonthM3) + " · "
                    + getString(R.string.m3_change_previous_month, percent));
        }
        syncStatsChips();
        List<WaterUsageChartView.Entry> entries = statsMode == WaterUsageChartView.Mode.BARS
                ? consumptionChartEntries(statistics, consumption)
                : readingChartEntries(monthly, latestLive);
        statsChart.setData(entries, statsMode);
    }

    private List<WaterUsageChartView.Entry> consumptionChartEntries(WaterUsageAnalytics.Statistics statistics,
                                                                    ConsumptionView consumption) {
        String year = new SimpleDateFormat("yyyy", Locale.US).format(new Date());
        List<WaterUsageAnalytics.MonthBucket> buckets = statsRange == STATS_CURRENT_YEAR
                ? statistics.currentYear(year) : statistics.latestMonths(12);
        List<WaterUsageChartView.Entry> result = new ArrayList<>();
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        boolean currentSeen = false;
        for (WaterUsageAnalytics.MonthBucket bucket : buckets) {
            boolean partial = bucket.partial;
            double value = bucket.consumptionM3;
            if (currentMonth.equals(bucket.monthKey) && consumption.monthM3 != null) {
                value = consumption.monthM3;
                partial = consumption.monthPartial || partial;
                currentSeen = true;
            }
            result.add(new WaterUsageChartView.Entry(monthLabel(bucket.monthKey) + (partial ? "*" : ""), value, partial));
        }
        if (!currentSeen && consumption.monthM3 != null && statsRange == STATS_CURRENT_YEAR) {
            result.add(new WaterUsageChartView.Entry(monthLabel(currentMonth) + (consumption.monthPartial ? "*" : ""),
                    consumption.monthM3, consumption.monthPartial));
        }
        return result;
    }

    private List<WaterUsageChartView.Entry> readingChartEntries(List<WaterUsageAnalytics.Point> monthly,
                                                                WaterUsageAnalytics.Point latestLive) {
        // A cumulative line is meaningful only within one physical meter. After replacement, show
        // the active meter's segment rather than drawing a misleading line across the reset.
        List<WaterUsageAnalytics.Point> filtered = new ArrayList<>();
        for (WaterUsageAnalytics.Point p : monthly) {
            if (displayMeterId != null && displayMeterId.equals(p.meterId)) filtered.add(p);
        }
        filtered.sort((a,b) -> Long.compare(a.sortMs, b.sortMs));
        String year = new SimpleDateFormat("yyyy", Locale.US).format(new Date());
        List<WaterUsageAnalytics.Point> selected = new ArrayList<>();
        if (statsRange == STATS_CURRENT_YEAR) {
            for (WaterUsageAnalytics.Point p : filtered) {
                String key = WaterUsageAnalytics.monthKey(p.timestamp);
                if (key != null && key.startsWith(year + "-")) selected.add(p);
            }
        } else {
            int from = Math.max(0, filtered.size() - 12);
            selected.addAll(filtered.subList(from, filtered.size()));
        }
        List<WaterUsageChartView.Entry> result = new ArrayList<>();
        for (WaterUsageAnalytics.Point p : selected) {
            result.add(new WaterUsageChartView.Entry(monthLabel(WaterUsageAnalytics.monthKey(p.timestamp)), p.totalM3, false));
        }
        if (latestLive != null && displayMeterId != null && displayMeterId.equals(latestLive.meterId)) {
            result.add(new WaterUsageChartView.Entry(getString(R.string.m3_filter_live), latestLive.totalM3, true));
        }
        return result;
    }

    private List<HistoryItem> buildHistoryItems(List<String> meters) {
        List<HistoryItem> result = new ArrayList<>();
        for (String meter : meters) {
            for (MeterHistoryStore.Reading reading : liveStore.getReadings(meter, 0L)) {
                WaterUsageAnalytics.Point point = new WaterUsageAnalytics.Point("LIVE|" + reading.id,
                        reading.meterId, localMinute(reading.readAtMs), reading.readAtMs,
                        HistorySemanticTimeline.Granularity.LIVE, reading.totalM3);
                result.add(new HistoryItem(point, formatDateTime(reading.readAtMs), reading.meterTime,
                        reading.batteryPercent, reading.alarmCodes, null));
            }
            for (ArchiveFamilyStore.StoredPeriod archive : archiveStore.getPeriods(meter, null)) {
                Double total = parseMeasurement(archive.totalVolume);
                long sort = parseMeterLocal(archive.loggerTimestamp);
                if (total == null || sort <= 0L) continue;
                WaterUsageAnalytics.Point point = new WaterUsageAnalytics.Point(
                        archive.family.name() + "|" + archive.meterId + "|" + archive.loggerTimestamp,
                        archive.meterId, archive.loggerTimestamp, sort, granularity(archive.family), total);
                result.add(new HistoryItem(point, archive.loggerTimestamp, null,
                        parseInteger(archive.batteryPercent), null,
                        MeterStatusPresentation.historical(archive.errorFlags)));
            }
        }
        return result;
    }

    private MeterHistoryStore.Reading latestLive(String meterId) {
        if (meterId == null) return null;
        List<MeterHistoryStore.Reading> values = liveStore.getReadings(meterId, 0L);
        return values.isEmpty() ? null : values.get(0);
    }

    private WaterUsageAnalytics.Point currentLivePoint(MeterHistoryStore.Reading latest,
                                                       LiveReadMetadataStore.Summary meta,
                                                       String meterId) {
        if (meterId == null) return null;
        if (meta.available() && (latest == null || meta.readAtMs >= latest.readAtMs)) {
            return new WaterUsageAnalytics.Point("LIVE_CURRENT|" + meta.readAtMs, meterId,
                    localMinute(meta.readAtMs), meta.readAtMs, HistorySemanticTimeline.Granularity.LIVE, meta.totalM3);
        }
        return latest == null ? null : new WaterUsageAnalytics.Point("LIVE|" + latest.id, meterId,
                localMinute(latest.readAtMs), latest.readAtMs, HistorySemanticTimeline.Granularity.LIVE, latest.totalM3);
    }

    private void toggleHistorySync() {
        if (syncFlow.state() == HistorySyncFlow.State.ARMED_WAITING_FOR_METER) {
            syncFlow.cancelArmedSync();
            showReady();
            updateHistoryButton();
            return;
        }
        if (!syncFlow.canRequestFullSync()) return;
        syncFlow.requestFullSync();
        setState(R.string.m3_state_sync_wait_title, R.string.m3_state_sync_wait_body);
        updateHistoryButton();
    }

    private void updateHistoryButton() {
        if (historyButton == null) return;
        if (syncFlow.state() == HistorySyncFlow.State.ARMED_WAITING_FOR_METER) {
            historyButton.setText(R.string.m3_cancel_sync);
            historyButton.setEnabled(true);
        } else {
            historyButton.setText(R.string.m3_sync_history);
            historyButton.setEnabled(syncFlow.canRequestFullSync());
        }
    }

    private void showReady() {
        if (nfcAdapter == null) setState(R.string.m3_state_no_nfc_title, R.string.m3_state_no_nfc_body);
        else if (!nfcAdapter.isEnabled()) setState(R.string.m3_state_nfc_off_title, R.string.m3_state_nfc_off_body);
        else setState(R.string.m3_state_ready_title, R.string.m3_state_ready_body);
        updateHistoryButton();
    }

    private void setState(int titleRes, int bodyRes) {
        if (statusTitle == null) return;
        statusTitle.setText(titleRes);
        statusBody.setText(bodyRes);
    }

    private void showPage(int page) {
        currentPage = page;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
        }
    }

    void selectNavigationItem(int navItemId) {
        if (navItemId == NAV_HISTORY) showPage(PAGE_HISTORY);
        else if (navItemId == NAV_STATS) showPage(PAGE_STATS);
        else showPage(PAGE_OVERVIEW);
    }

    int currentNavigationItemId() {
        if (currentPage == PAGE_HISTORY) return NAV_HISTORY;
        if (currentPage == PAGE_STATS) return NAV_STATS;
        return NAV_OVERVIEW;
    }

    private LinearLayout pageContent() {
        LinearLayout content = MaterialUi.vertical(this);
        int p = MaterialUi.dp(this, 16);
        content.setPadding(p, p, p, MaterialUi.dp(this, 32));
        return content;
    }

    private View scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (parent.getChildCount() > 0) lp.setMarginStart(MaterialUi.dp(this, 8));
        parent.addView(card, lp);
        return value;
    }

    private TextView addStandaloneMetric(LinearLayout parent, int labelRes, int topMargin) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout content = MaterialUi.cardContent(this);
        content.addView(MaterialUi.label(this, getString(labelRes)));
        TextView value = MaterialUi.headline(this, getString(R.string.m3_not_available));
        value.setTextSize(22f);
        value.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(value);
        card.addView(content);
        MaterialUi.addTopMargin(parent, card, topMargin);
        return value;
    }

    private TextView addDataLine(LinearLayout parent, int labelRes) {
        LinearLayout row = MaterialUi.horizontal(this);
        row.setPadding(0, MaterialUi.dp(this, 10), 0, 0);
        TextView label = MaterialUi.body(this, getString(labelRes));
        TextView value = MaterialUi.body(this, getString(R.string.m3_not_available));
        value.setGravity(Gravity.END);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueLp.setMarginStart(MaterialUi.dp(this, 12));
        row.addView(value, valueLp);
        parent.addView(row);
        return value;
    }

    private View emptyCard(int textRes) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.cardContent(this);
        inside.addView(MaterialUi.body(this, getString(textRes)));
        card.addView(inside);
        return card;
    }

    private void addHistoryFilter(HistorySemanticTimeline.Granularity granularity, int labelRes) {
        Chip chip = new Chip(this);
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setChecked(historyFilter == granularity);
        chip.setOnClickListener(v -> {
            historyFilter = granularity;
            refreshAll();
        });
        historyFilterHost.addView(chip);
    }

    private Chip statsChip(int labelRes, boolean checked, View.OnClickListener listener) {
        Chip chip = new Chip(this);
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(listener);
        return chip;
    }

    private void syncStatsChips() {
        if (statsRangeGroup != null) {
            for (int i=0;i<statsRangeGroup.getChildCount();i++) {
                Chip chip=(Chip)statsRangeGroup.getChildAt(i);
                chip.setChecked((i==0 && statsRange==STATS_CURRENT_YEAR)||(i==1&&statsRange==STATS_LAST_12));
            }
        }
        if (statsModeGroup != null) {
            for (int i=0;i<statsModeGroup.getChildCount();i++) {
                Chip chip=(Chip)statsModeGroup.getChildAt(i);
                chip.setChecked((i==0 && statsMode==WaterUsageChartView.Mode.BARS)||(i==1&&statsMode==WaterUsageChartView.Mode.LINE));
            }
        }
    }

    private int filterLabel(HistorySemanticTimeline.Granularity g) {
        switch (g) {
            case LIVE: return R.string.m3_filter_live;
            case HOUR: return R.string.m3_filter_hour;
            case DAY: return R.string.m3_filter_day;
            case YEAR: return R.string.m3_filter_year;
            case MONTH:
            default: return R.string.m3_filter_month;
        }
    }

    private String typeLabel(HistorySemanticTimeline.Granularity g) { return getString(filterLabel(g)); }

    private String formatM3(Double value) {
        return value == null || value.isNaN() ? getString(R.string.m3_not_available) : getString(R.string.m3_unit_m3, value);
    }
    private String formatM3(double value) { return getString(R.string.m3_unit_m3, value); }
    private String formatConsumption(Double value, boolean partial) {
        if (value == null) return getString(R.string.m3_not_available);
        return formatM3(value) + (partial ? "*" : "");
    }
    private String formatDateTime(long ms) {
        if (ms <= 0L) return getString(R.string.m3_not_available);
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                getResources().getConfiguration().getLocales().get(0)).format(new Date(ms));
    }
    private String shortTimestamp(String timestamp) {
        if (timestamp == null) return "";
        return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
    }
    private String monthLabel(String key) {
        if (key == null || key.length() != 7) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM", Locale.US);
            in.setLenient(false);
            Date date = in.parse(key);
            if (date == null) return key;
            return new SimpleDateFormat("MMM", getResources().getConfiguration().getLocales().get(0)).format(date);
        } catch (ParseException e) { return key; }
    }
    private static Double parseMeasurement(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replace(',', '.').replaceAll("[^0-9+\\-.]", "");
        if (cleaned.isEmpty()) return null;
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException e) { return null; }
    }
    private static Integer parseInteger(String value) {
        Double d = parseMeasurement(value);
        return d == null ? null : (int)Math.round(d);
    }
    private static long parseMeterLocal(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        f.setLenient(false);
        try { Date date=f.parse(value); return date==null?0L:date.getTime(); }
        catch (ParseException e) { return 0L; }
    }
    private static String localMinute(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }
    private static String utcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
    private static HistorySemanticTimeline.Granularity granularity(ArchiveFamilyPeriod.Family family) {
        switch (family) {
            case HOUR: return HistorySemanticTimeline.Granularity.HOUR;
            case DAY: return HistorySemanticTimeline.Granularity.DAY;
            case YEAR: return HistorySemanticTimeline.Granularity.YEAR;
            case MONTH:
            default: return HistorySemanticTimeline.Granularity.MONTH;
        }
    }
    private static String replacementCsvFileName() {
        return "qalcosonic-w1-old-data-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".csv";
    }

    private static final class PendingMeter {
        final MbusParser.MeterData meter; final long readAtMs; final String previousMeterId;
        PendingMeter(MbusParser.MeterData meter, long readAtMs, String previousMeterId) {
            this.meter=meter; this.readAtMs=readAtMs; this.previousMeterId=previousMeterId;
        }
    }
    private static final class HistoryItem {
        final WaterUsageAnalytics.Point point; final String primaryDisplay; final String meterTime;
        final Integer batteryPercent; final String liveAlarmCodes; final MeterStatusPresentation.Historical historical;
        HistoryItem(WaterUsageAnalytics.Point point, String primaryDisplay, String meterTime,
                    Integer batteryPercent, String liveAlarmCodes, MeterStatusPresentation.Historical historical) {
            this.point=point; this.primaryDisplay=primaryDisplay; this.meterTime=meterTime;
            this.batteryPercent=batteryPercent; this.liveAlarmCodes=liveAlarmCodes; this.historical=historical;
        }
    }
    private static final class TimelineEntry {
        final long sortMs; final HistoryItem item; final WaterUsageAnalytics.HistoryDelta delta;
        final MeterLifecycleStore.Transition transition;
        private TimelineEntry(long sortMs, HistoryItem item, WaterUsageAnalytics.HistoryDelta delta,
                              MeterLifecycleStore.Transition transition) {
            this.sortMs=sortMs; this.item=item; this.delta=delta; this.transition=transition;
        }
        static TimelineEntry data(long ms, HistoryItem item, WaterUsageAnalytics.HistoryDelta delta) {
            return new TimelineEntry(ms,item,delta,null);
        }
        static TimelineEntry transition(MeterLifecycleStore.Transition transition) {
            return new TimelineEntry(transition.confirmedAtMs,null,null,transition);
        }
    }
    private static final class ConsumptionView {
        final Double monthM3; final boolean monthPartial; final Double yearM3; final boolean yearPartial;
        ConsumptionView(Double monthM3, boolean monthPartial, Double yearM3, boolean yearPartial) {
            this.monthM3=monthM3; this.monthPartial=monthPartial; this.yearM3=yearM3; this.yearPartial=yearPartial;
        }
    }
}
