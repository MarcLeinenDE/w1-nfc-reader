package de.marcleinen.engineeringlab.qalcosonic;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit History synchronization surface reached from Settings.
 *
 * <p>Normal NFC contact elsewhere in the product remains Live-only. Hour, Day and Month initial
 * baselines use their individually physically validated v2 safety-shell entry points. Sync-all is
 * an orchestration layer only: HOUR -> logical reconnect -> DAY -> logical reconnect -> MONTH.
 * Every family still performs its own Reset/Live preflight, boundary guard, traversal, immediate
 * persistence and final Reset/Live verification. A failed family stops the sequence before the
 * next family. Completed baselines are skipped on a later Sync-all retry. Incremental update
 * remains a later explicit slice.</p>
 */
public final class HistorySyncActivity extends MaterialBaseActivity implements NfcAdapter.ReaderCallback {
    private static final long BETWEEN_FAMILY_RECONNECT_MS = 1500L;
    private static final ArchiveFamilyPeriod.Family[] SYNC_ALL_ORDER = {
            ArchiveFamilyPeriod.Family.HOUR,
            ArchiveFamilyPeriod.Family.DAY,
            ArchiveFamilyPeriod.Family.MONTH
    };

    private final AtomicBoolean reading = new AtomicBoolean(false);

    private NfcAdapter nfcAdapter;
    private ArchiveFamilyStore archiveStore;
    private ArchiveFamilySyncStateStore syncStateStore;
    private MeterLifecycleStore lifecycleStore;

    private View root;
    private TextView meterText;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView debugText;
    private TextView monthState;
    private TextView dayState;
    private TextView hourState;
    private MaterialButton monthButton;
    private MaterialButton dayButton;
    private MaterialButton hourButton;
    private MaterialButton allButton;

    private volatile ArchiveFamilyPeriod.Family armedFamily;
    private volatile boolean armedAll;
    private volatile String targetMeterId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        archiveStore = new ArchiveFamilyStore(getApplicationContext());
        syncStateStore = new ArchiveFamilySyncStateStore(getApplicationContext());
        lifecycleStore = new MeterLifecycleStore(getApplicationContext());
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
        refreshState();
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
        }
        refreshState();
    }

    @Override protected void onPause() {
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
        if (!reading.get()) disarmSync();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (archiveStore != null) archiveStore.close();
        super.onDestroy();
    }

    @Override public void onTagDiscovered(Tag tag) {
        final boolean runAll = armedAll;
        final ArchiveFamilyPeriod.Family family = armedFamily;
        final String expectedMeter = targetMeterId;
        if ((!runAll && family == null) || expectedMeter == null) return;
        if (!reading.compareAndSet(false, true)) return;

        if (NfcV.get(tag) == null) {
            reading.set(false);
            disarmSync();
            runOnUiThread(() -> {
                setStatus(R.string.m3_state_wrong_tag_title, R.string.m3_state_wrong_tag_body);
                if (runAll) {
                    setDebug("TEMP DEBUG — wird vor Release entfernt\nmode=ALL\nphase=WRONG_TAG");
                } else {
                    setDebug("TEMP DEBUG — wird vor Release entfernt\nphase=WRONG_TAG\nfamily="
                            + family.name());
                }
            });
            return;
        }

        try {
            runOnUiThread(() -> setStatus(
                    R.string.m3_state_sync_title,
                    R.string.m3_state_sync_body));
            if (runAll) {
                runAllBaselines(tag, expectedMeter);
            } else {
                runSingleBaseline(tag, family, expectedMeter);
            }
        } catch (Exception error) {
            String debug = runAll
                    ? allException(expectedMeter, null, error)
                    : HistorySyncTemporaryDebug.exception(expectedMeter, family, error);
            runOnUiThread(() -> {
                setStatus(R.string.m3_state_sync_failed_title, R.string.m3_state_sync_failed_body);
                setDebug(debug);
            });
        } finally {
            reading.set(false);
            disarmSync();
            runOnUiThread(this::refreshState);
        }
    }

    private void runSingleBaseline(
            Tag tag,
            ArchiveFamilyPeriod.Family family,
            String expectedMeter) throws Exception {
        runOnUiThread(() -> setDebug(HistorySyncTemporaryDebug.running(expectedMeter, family)));
        NfcV nfcv = NfcV.get(tag);
        if (nfcv == null) throw new IOException("NFC_V_MISSING");
        try {
            nfcv.connect();
            FamilyAttempt attempt = performFamilyBaseline(nfcv, tag, family, expectedMeter);
            runOnUiThread(() -> {
                setDebug(attempt.debug);
                showAttemptStatus(attempt.complete, attempt.partial);
                refreshState();
            });
        } finally {
            closeQuietly(nfcv);
        }
    }

    private void runAllBaselines(Tag tag, String expectedMeter) {
        StringBuilder debug = new StringBuilder();
        debug.append("TEMP DEBUG — wird vor Release entfernt\n")
                .append("mode=ALL\n")
                .append("expectedMeter=").append(expectedMeter).append('\n')
                .append("order=HOUR->DAY->MONTH\n")
                .append("reconnectMs=").append(BETWEEN_FAMILY_RECONNECT_MS).append('\n')
                .append("phase=RUNNING");
        runOnUiThread(() -> setDebug(debug.toString()));

        boolean anyProgress = false;
        boolean previousFamilyRan = false;
        boolean allComplete = true;

        for (ArchiveFamilyPeriod.Family family : SYNC_ALL_ORDER) {
            ArchiveFamilySyncState existing = syncStateStore.get(expectedMeter, family);
            if (existing.baselineComplete()) {
                appendAllLine(debug, family, "SKIPPED_BASELINE_COMPLETE");
                runOnUiThread(() -> setDebug(debug.toString()));
                continue;
            }

            if (previousFamilyRan) {
                appendAllLine(debug, family, "RECONNECT_WAIT");
                runOnUiThread(() -> setDebug(debug.toString()));
                SystemClock.sleep(BETWEEN_FAMILY_RECONNECT_MS);
            }

            previousFamilyRan = true;
            appendAllLine(debug, family, "CONNECTING");
            runOnUiThread(() -> setDebug(debug.toString()));

            NfcV nfcv = NfcV.get(tag);
            if (nfcv == null) {
                allComplete = false;
                debug.append("\n\n").append(allException(expectedMeter, family,
                        new IOException("NFC_V_MISSING")));
                break;
            }

            FamilyAttempt attempt;
            try {
                nfcv.connect();
                runOnUiThread(() -> setStatus(
                        R.string.m3_state_sync_title,
                        R.string.m3_state_sync_body));
                attempt = performFamilyBaseline(nfcv, tag, family, expectedMeter);
            } catch (Exception error) {
                allComplete = false;
                debug.append("\n\n").append(allException(expectedMeter, family, error));
                closeQuietly(nfcv);
                runOnUiThread(() -> setDebug(debug.toString()));
                break;
            } finally {
                closeQuietly(nfcv);
            }

            anyProgress = anyProgress
                    || attempt.complete
                    || attempt.partial
                    || attempt.persisted.committed > 0;
            debug.append("\n\n--- ").append(family.name()).append(" ---\n")
                    .append(attempt.debug);
            runOnUiThread(() -> {
                setDebug(debug.toString());
                refreshState();
            });

            if (!attempt.complete) {
                allComplete = false;
                break;
            }
        }

        final boolean finalComplete = allComplete && allBaselinesComplete(expectedMeter);
        final boolean finalPartial = !finalComplete && anyProgress;
        debug.append("\n\nALL_RESULT=")
                .append(finalComplete ? "COMPLETE" : (finalPartial ? "PARTIAL" : "FAILED"));
        runOnUiThread(() -> {
            setDebug(debug.toString());
            showAttemptStatus(finalComplete, finalPartial);
            refreshState();
        });
    }

    private FamilyAttempt performFamilyBaseline(
            NfcV nfcv,
            Tag tag,
            ArchiveFamilyPeriod.Family family,
            String expectedMeter) {
        String retrievedAtUtc = utcNow();
        MonthlyArchiveNfcWire wire = new MonthlyArchiveNfcWire(nfcv, tag.getId());
        ArchivePersistenceCoordinator.ImmediateSession persistence =
                ArchivePersistenceCoordinator.beginImmediate(
                        archiveStore,
                        expectedMeter,
                        family);

        ArchiveFamilyTransportAdapter.Result transport = runInitialBaseline(
                family,
                wire,
                expectedMeter,
                retrievedAtUtc,
                persistence::accept);
        ArchivePersistenceCoordinator.Result persisted = persistence.result();
        String debug = HistorySyncTemporaryDebug.format(expectedMeter, transport, persisted);

        boolean complete = transport.completeProductAttempt();
        boolean partial = !complete
                && (!transport.traversal.periods.isEmpty() || persisted.committed > 0);
        recordFamilyAttempt(expectedMeter, family, transport, persisted, complete, partial);
        return new FamilyAttempt(complete, partial, persisted, debug);
    }

    private void recordFamilyAttempt(
            String expectedMeter,
            ArchiveFamilyPeriod.Family family,
            ArchiveFamilyTransportAdapter.Result transport,
            ArchivePersistenceCoordinator.Result persisted,
            boolean complete,
            boolean partial) {
        ArchiveFamilySyncState.AttemptOutcome outcome = complete
                ? ArchiveFamilySyncState.AttemptOutcome.COMPLETE
                : (partial
                ? ArchiveFamilySyncState.AttemptOutcome.PARTIAL
                : ArchiveFamilySyncState.AttemptOutcome.FAILED);

        String oldest = null;
        String newest = null;
        for (ArchiveTraversalStateMachine.PeriodEvidence evidence : transport.traversal.periods) {
            if (evidence == null || evidence.period == null) continue;
            String timestamp = evidence.period.loggerTimestamp;
            if (oldest == null || timestamp.compareTo(oldest) < 0) oldest = timestamp;
            if (newest == null || timestamp.compareTo(newest) > 0) newest = timestamp;
        }

        syncStateStore.recordAttempt(
                expectedMeter,
                family,
                ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                System.currentTimeMillis(),
                outcome,
                transport.effectiveStopReason(),
                transport.finalRestoreVerified(),
                oldest,
                newest,
                transport.traversal.periods.size(),
                persisted.inserted,
                persisted.confirmed,
                persisted.conflicts);
    }

    private ArchiveFamilyTransportAdapter.Result runInitialBaseline(
            ArchiveFamilyPeriod.Family family,
            MonthlyArchiveNfcWire wire,
            String expectedMeter,
            String retrievedAtUtc,
            ArchiveFamilyTransportAdapter.AcceptedPeriodSink sink) {
        switch (family) {
            case MONTH:
                // Keep the exact physically validated Month entry point unchanged.
                return ArchiveFamilyTransportAdapter.runMonth(
                        wire,
                        wire,
                        expectedMeter,
                        retrievedAtUtc,
                        ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                        sink);
            case DAY:
                return ArchiveFamilyProductionRunner.runDay(
                        wire,
                        wire,
                        expectedMeter,
                        retrievedAtUtc,
                        ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                        sink);
            case HOUR:
                return ArchiveFamilyProductionRunner.runHour(
                        wire,
                        wire,
                        expectedMeter,
                        retrievedAtUtc,
                        ArchiveFamilySyncState.SyncMode.INITIAL_FULL,
                        sink);
            default:
                throw new IllegalArgumentException("family is not product-enabled: " + family);
        }
    }

    private void buildUi() {
        LinearLayout shell = MaterialUi.vertical(this);
        shell.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.m3_sync_history);
        toolbar.setNavigationIcon(R.drawable.ic_m3_back);
        toolbar.setNavigationContentDescription(R.string.m3_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        shell.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = MaterialUi.vertical(this);
        int p = MaterialUi.dp(this, 16);
        content.setPadding(p, p, p, MaterialUi.dp(this, 32));

        meterText = MaterialUi.body(this, getString(R.string.m3_no_meter));
        content.addView(meterText);

        MaterialCardView statusCard = MaterialUi.card(this);
        LinearLayout statusContent = MaterialUi.cardContent(this);
        statusTitle = MaterialUi.title(this, getString(R.string.m3_sync_history));
        statusBody = MaterialUi.body(this, getString(R.string.m3_state_sync_body));
        statusBody.setPadding(0, MaterialUi.dp(this, 5), 0, 0);
        statusContent.addView(statusTitle);
        statusContent.addView(statusBody);
        statusCard.addView(statusContent);
        MaterialUi.addTopMargin(content, statusCard, 12);

        if (BuildConfig.DEBUG) {
            MaterialCardView debugCard = MaterialUi.card(this);
            LinearLayout debugContent = MaterialUi.cardContent(this);
            debugContent.addView(MaterialUi.title(this, "TEMP DEBUG"));
            debugText = MaterialUi.body(this,
                    "Wird nach der physischen v2-Validierung wieder entfernt.");
            debugText.setTextIsSelectable(true);
            debugText.setPadding(0, MaterialUi.dp(this, 6), 0, 0);
            debugContent.addView(debugText);
            debugCard.addView(debugContent);
            MaterialUi.addTopMargin(content, debugCard, 10);
        }

        MaterialCardView monthCard = MaterialUi.card(this);
        LinearLayout monthContent = MaterialUi.cardContent(this);
        monthContent.addView(MaterialUi.title(this, getString(R.string.m3_filter_month)));
        monthState = MaterialUi.body(this, getString(R.string.m3_no_archive));
        monthState.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        monthContent.addView(monthState);
        monthButton = actionButton(R.string.m3_filter_month,
                v -> offerFamilySync(ArchiveFamilyPeriod.Family.MONTH));
        MaterialUi.addTopMargin(monthContent, monthButton, 8);
        monthCard.addView(monthContent);
        MaterialUi.addTopMargin(content, monthCard, 12);

        MaterialCardView dayCard = MaterialUi.card(this);
        LinearLayout dayContent = MaterialUi.cardContent(this);
        dayContent.addView(MaterialUi.title(this, getString(R.string.m3_filter_day)));
        dayState = MaterialUi.body(this, getString(R.string.m3_no_archive));
        dayState.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        dayContent.addView(dayState);
        dayButton = actionButton(R.string.m3_filter_day,
                v -> offerFamilySync(ArchiveFamilyPeriod.Family.DAY));
        MaterialUi.addTopMargin(dayContent, dayButton, 8);
        dayCard.addView(dayContent);
        MaterialUi.addTopMargin(content, dayCard, 10);

        MaterialCardView hourCard = MaterialUi.card(this);
        LinearLayout hourContent = MaterialUi.cardContent(this);
        hourContent.addView(MaterialUi.title(this, getString(R.string.m3_filter_hour)));
        hourState = MaterialUi.body(this, getString(R.string.m3_no_archive));
        hourState.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        hourContent.addView(hourState);
        hourButton = actionButton(R.string.m3_filter_hour,
                v -> offerFamilySync(ArchiveFamilyPeriod.Family.HOUR));
        MaterialUi.addTopMargin(hourContent, hourButton, 8);
        hourCard.addView(hourContent);
        MaterialUi.addTopMargin(content, hourCard, 10);

        allButton = actionButton(R.string.m3_filter_all, v -> offerAllSync());
        MaterialUi.addTopMargin(content, allButton, 12);

        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root = shell;
        setContentView(shell);
        applySystemInsets(shell);
    }

    private MaterialButton actionButton(int familyLabel, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(getString(familyLabel) + " · " + getString(R.string.m3_sync_history));
        button.setMinHeight(MaterialUi.dp(this, 48));
        button.setOnClickListener(listener);
        return button;
    }

    private void offerFamilySync(ArchiveFamilyPeriod.Family family) {
        if (family == null || reading.get() || armedFamily != null || armedAll) return;
        if (family != ArchiveFamilyPeriod.Family.MONTH
                && family != ArchiveFamilyPeriod.Family.DAY
                && family != ArchiveFamilyPeriod.Family.HOUR) {
            return;
        }
        String meter = lifecycleStore.activeMeterId();
        if (meter == null || meter.trim().isEmpty()) {
            Snackbar.make(root, R.string.m3_no_meter, Snackbar.LENGTH_LONG).show();
            return;
        }

        ArchiveFamilySyncState current = syncStateStore.get(meter, family);
        if (current.baselineComplete()) {
            Snackbar.make(root, R.string.m3_not_available, Snackbar.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_sync_history)
                .setMessage(R.string.m3_state_sync_body)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.m3_sync_history,
                        (dialog, which) -> armFamily(meter, family))
                .show();
    }

    private void offerAllSync() {
        if (reading.get() || armedFamily != null || armedAll) return;
        String meter = lifecycleStore.activeMeterId();
        if (meter == null || meter.trim().isEmpty()) {
            Snackbar.make(root, R.string.m3_no_meter, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (allBaselinesComplete(meter)) {
            Snackbar.make(root, R.string.m3_not_available, Snackbar.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.m3_sync_history)
                .setMessage(R.string.m3_state_sync_body)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.m3_sync_history,
                        (dialog, which) -> armAll(meter))
                .show();
    }

    private void armFamily(String meterId, ArchiveFamilyPeriod.Family family) {
        targetMeterId = meterId;
        armedFamily = family;
        armedAll = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setStatus(R.string.m3_state_verify_title, R.string.m3_state_verify_body);
        setDebug(HistorySyncTemporaryDebug.running(meterId, family));
        refreshState();
    }

    private void armAll(String meterId) {
        targetMeterId = meterId;
        armedFamily = null;
        armedAll = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setStatus(R.string.m3_state_verify_title, R.string.m3_state_verify_body);
        setDebug("TEMP DEBUG — wird vor Release entfernt\nmode=ALL\nexpectedMeter="
                + meterId + "\nphase=ARMED");
        refreshState();
    }

    private void disarmSync() {
        armedFamily = null;
        armedAll = false;
        targetMeterId = null;
        runOnUiThread(() -> getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    }

    private void refreshState() {
        if (meterText == null) return;
        String meter = lifecycleStore == null ? null : lifecycleStore.activeMeterId();
        if (meter == null || meter.trim().isEmpty()) {
            meterText.setText(R.string.m3_no_meter);
            monthState.setText(R.string.m3_no_archive);
            dayState.setText(R.string.m3_no_archive);
            hourState.setText(R.string.m3_no_archive);
            monthButton.setEnabled(false);
            dayButton.setEnabled(false);
            hourButton.setEnabled(false);
            allButton.setEnabled(false);
            return;
        }

        meterText.setText(getString(R.string.m3_meter_id, meter));
        ArchiveFamilySyncState month = updateFamilyState(
                meter, ArchiveFamilyPeriod.Family.MONTH, monthState);
        ArchiveFamilySyncState day = updateFamilyState(
                meter, ArchiveFamilyPeriod.Family.DAY, dayState);
        ArchiveFamilySyncState hour = updateFamilyState(
                meter, ArchiveFamilyPeriod.Family.HOUR, hourState);

        boolean idle = armedFamily == null && !armedAll && !reading.get();
        monthButton.setEnabled(idle && !month.baselineComplete());
        dayButton.setEnabled(idle && !day.baselineComplete());
        hourButton.setEnabled(idle && !hour.baselineComplete());
        allButton.setEnabled(idle
                && (!hour.baselineComplete() || !day.baselineComplete() || !month.baselineComplete()));
    }

    private boolean allBaselinesComplete(String meter) {
        if (meter == null || meter.trim().isEmpty()) return false;
        for (ArchiveFamilyPeriod.Family family : SYNC_ALL_ORDER) {
            if (!syncStateStore.get(meter, family).baselineComplete()) return false;
        }
        return true;
    }

    private ArchiveFamilySyncState updateFamilyState(
            String meter,
            ArchiveFamilyPeriod.Family family,
            TextView stateView) {
        ArchiveFamilySyncState state = syncStateStore.get(meter, family);
        if (state.baselineComplete()) {
            stateView.setText(state.lastSuccessMs > 0L
                    ? getString(R.string.m3_last_read, formatDateTime(state.lastSuccessMs))
                    : getString(R.string.m3_state_sync_ok_title));
        } else if (state.lastAttemptOutcome == ArchiveFamilySyncState.AttemptOutcome.PARTIAL) {
            stateView.setText(R.string.m3_state_sync_partial_title);
        } else if (state.lastAttemptOutcome == ArchiveFamilySyncState.AttemptOutcome.FAILED) {
            stateView.setText(R.string.m3_state_sync_failed_title);
        } else {
            stateView.setText(R.string.m3_no_archive);
        }
        return state;
    }

    private void showAttemptStatus(boolean complete, boolean partial) {
        if (complete) {
            setStatus(R.string.m3_state_sync_ok_title, R.string.m3_state_sync_ok_body);
        } else if (partial) {
            setStatus(R.string.m3_state_sync_partial_title, R.string.m3_state_sync_partial_body);
        } else {
            setStatus(R.string.m3_state_sync_failed_title, R.string.m3_state_sync_failed_body);
        }
    }

    private void setStatus(int titleRes, int bodyRes) {
        if (statusTitle != null) statusTitle.setText(titleRes);
        if (statusBody != null) statusBody.setText(bodyRes);
    }

    private void setDebug(String text) {
        if (BuildConfig.DEBUG && debugText != null) debugText.setText(text);
    }

    private String formatDateTime(long atMs) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                getResources().getConfiguration().getLocales().get(0));
        return format.format(new Date(atMs));
    }

    private static void appendAllLine(
            StringBuilder out,
            ArchiveFamilyPeriod.Family family,
            String phase) {
        out.append("\n").append(family.name()).append("_phase=").append(phase);
    }

    private static String allException(
            String expectedMeter,
            ArchiveFamilyPeriod.Family family,
            Throwable error) {
        return "TEMP DEBUG — wird vor Release entfernt\n"
                + "mode=ALL\n"
                + "family=" + (family == null ? "null" : family.name()) + "\n"
                + "expectedMeter=" + expectedMeter + "\n"
                + "phase=EXCEPTION\n"
                + "exception=" + (error == null ? "null" : error.getClass().getSimpleName()) + "\n"
                + "message=" + (error == null || error.getMessage() == null
                ? "null" : error.getMessage().replace('\n', ' ').replace('\r', ' ').trim());
    }

    private static void closeQuietly(NfcV nfcv) {
        if (nfcv == null) return;
        try {
            nfcv.close();
        } catch (IOException ignored) {
            // Best effort only. Every next family obtains a new NfcV technology instance.
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class FamilyAttempt {
        final boolean complete;
        final boolean partial;
        final ArchivePersistenceCoordinator.Result persisted;
        final String debug;

        FamilyAttempt(
                boolean complete,
                boolean partial,
                ArchivePersistenceCoordinator.Result persisted,
                String debug) {
            this.complete = complete;
            this.partial = partial;
            this.persisted = persisted;
            this.debug = debug;
        }
    }
}
