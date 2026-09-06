package de.marcleinen.engineeringlab.qalcosonic;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared v2 product surface for bounded History browsing and multi-metric Statistics. */
public final class HistoryStatisticsActivity extends MaterialBaseActivity {
    static final String EXTRA_NAV_ITEM = "v2_nav_item";
    static final int NAV_HISTORY = 0x8102;
    static final int NAV_STATS = 0x8103;

    private enum Metric { CONSUMPTION, TEMPERATURE, FLOW, BATTERY, ALARMS }

    private final HistoryPeriodNavigator historyPeriod =
            new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);
    private final HistoryPeriodNavigator statsPeriod =
            new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.MONTH);

    private HistoryStatisticsRepository repository;
    private FrameLayout pageHost;
    private int currentNav = NAV_HISTORY;
    private HistorySemanticTimeline.Granularity historyFilter;
    private boolean historyAlarmsOnly;
    private Metric metric = Metric.CONSUMPTION;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new HistoryStatisticsRepository(getApplicationContext());
        buildRoot();
        applyIntent(getIntent());
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
        render();
    }

    @Override protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }

    void selectNavigationItem(int navItemId) {
        if (navItemId != NAV_HISTORY && navItemId != NAV_STATS) return;
        currentNav = navItemId;
        render();
    }

    int currentNavigationItemId() { return currentNav; }

    private void applyIntent(Intent intent) {
        if (intent == null) return;
        int requested = intent.getIntExtra(EXTRA_NAV_ITEM, NAV_HISTORY);
        if (requested == NAV_HISTORY || requested == NAV_STATS) currentNav = requested;
    }

    private void buildRoot() {
        LinearLayout root = MaterialUi.vertical(this);
        root.setBackgroundColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorSurface, Color.WHITE));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        applySystemInsets(root);
    }

    private void render() {
        if (pageHost == null || repository == null) return;
        pageHost.removeAllViews();
        pageHost.addView(currentNav == NAV_STATS ? buildStatisticsPage() : buildHistoryPage(),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private View buildHistoryPage() {
        LinearLayout root = MaterialUi.vertical(this);
        int side = MaterialUi.dp(this, 16);
        root.setPadding(side, side, side, MaterialUi.dp(this, 20));
        root.addView(MaterialUi.headline(this, getString(R.string.m3_history_title)));
        TextView subtitle = MaterialUi.body(this, getString(R.string.m3_history_subtitle));
        subtitle.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        root.addView(subtitle);

        ChipGroup filters = new ChipGroup(this);
        filters.setSingleSelection(true);
        addHistoryChip(filters, null, R.string.m3_filter_all);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.LIVE, R.string.m3_filter_live);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.HOUR, R.string.m3_filter_hour);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.DAY, R.string.m3_filter_day);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.MONTH, R.string.m3_filter_month);
        MaterialUi.addTopMargin(root, filters, 14);

        HistoryPeriodNavigator.Scale expectedScale = HistoryPeriodNavigator.forHistory(historyFilter);
        if (historyPeriod.scale() != expectedScale) historyPeriod.setScale(expectedScale);
        addPeriodNavigator(root, historyPeriod, true);

        ChipGroup quick = new ChipGroup(this);
        Chip alarms = new Chip(this);
        alarms.setText(R.string.v2_only_alarms);
        alarms.setCheckable(true);
        alarms.setChecked(historyAlarmsOnly);
        alarms.setOnClickListener(v -> { historyAlarmsOnly = alarms.isChecked(); render(); });
        quick.addView(alarms);

        Chip allPeriods = new Chip(this);
        allPeriods.setText(R.string.v2_all_periods);
        allPeriods.setCheckable(true);
        allPeriods.setChecked(historyPeriod.allPeriods());
        allPeriods.setOnClickListener(v -> { historyPeriod.setAllPeriods(true); render(); });
        quick.addView(allPeriods);
        MaterialUi.addTopMargin(root, quick, 6);

        HistoryPeriodNavigator.Window window = historyPeriod.window();
        List<HistoryStatisticsRepository.Observation> observations =
                repository.queryHistory(historyFilter, window, true);
        Map<String, HistoryStatisticsAnalytics.Delta> deltas =
                HistoryStatisticsAnalytics.deltas(observations);
        List<HistoryRow> rows = historyRows(observations, deltas, window);

        ListView list = new ListView(this);
        list.setDividerHeight(MaterialUi.dp(this, 8));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setClipToPadding(false);
        list.setPadding(0, MaterialUi.dp(this, 10), 0, 0);
        list.setAdapter(new HistoryAdapter(rows));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildStatisticsPage() {
        LinearLayout content = MaterialUi.vertical(this);
        int side = MaterialUi.dp(this, 16);
        content.setPadding(side, side, side, MaterialUi.dp(this, 28));
        content.addView(MaterialUi.headline(this, getString(R.string.m3_stats_title)));
        TextView subtitle = MaterialUi.body(this, getString(R.string.m3_stats_subtitle));
        subtitle.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(subtitle);

        ChipGroup periodScale = new ChipGroup(this);
        periodScale.setSingleSelection(true);
        addScaleChip(periodScale, HistoryPeriodNavigator.Scale.DAY, R.string.v2_period_day);
        addScaleChip(periodScale, HistoryPeriodNavigator.Scale.MONTH, R.string.v2_period_month);
        addScaleChip(periodScale, HistoryPeriodNavigator.Scale.YEAR, R.string.v2_period_year);
        MaterialUi.addTopMargin(content, periodScale, 14);
        addPeriodNavigator(content, statsPeriod, false);

        ChipGroup metrics = new ChipGroup(this);
        metrics.setSingleSelection(true);
        addMetricChip(metrics, Metric.CONSUMPTION, R.string.m3_chart_consumption);
        addMetricChip(metrics, Metric.TEMPERATURE, R.string.v2_metric_temperature);
        addMetricChip(metrics, Metric.FLOW, R.string.v2_metric_flow);
        addMetricChip(metrics, Metric.BATTERY, R.string.m3_battery);
        addMetricChip(metrics, Metric.ALARMS, R.string.v2_metric_alarms);
        MaterialUi.addTopMargin(content, metrics, 8);

        HistoryPeriodNavigator.Window window = statsPeriod.window();
        List<HistoryStatisticsRepository.Observation> observations = repository.queryStatistics(window);
        addStatisticsMetric(content, observations, window);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addStatisticsMetric(LinearLayout parent,
                                     List<HistoryStatisticsRepository.Observation> observations,
                                     HistoryPeriodNavigator.Window window) {
        switch (metric) {
            case TEMPERATURE:
                addTemperature(parent, observations, window);
                break;
            case FLOW:
                addFlow(parent, observations, window);
                break;
            case BATTERY:
                addBattery(parent, observations, window);
                break;
            case ALARMS:
                addAlarms(parent, observations, window);
                break;
            case CONSUMPTION:
            default:
                addConsumption(parent, observations, window);
                break;
        }
    }

    private void addConsumption(LinearLayout parent,
                                List<HistoryStatisticsRepository.Observation> observations,
                                HistoryPeriodNavigator.Window window) {
        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_total), formatM3(summary.total),
                getString(R.string.v2_kpi_average), formatM3(summary.average),
                getString(R.string.v2_kpi_highest), formatMetricWithTime(summary.maximum, summary.maximumAt, "m³"),
                getString(R.string.v2_kpi_lowest), formatMetricWithTime(summary.minimum, summary.minimumAt, "m³"));
        addChart(parent, summary.points, V2MetricChartView.Mode.BARS, "m³",
                R.string.v2_consumption_note, summary.availableBuckets, window.expectedBuckets);
    }

    private void addTemperature(LinearLayout parent,
                                List<HistoryStatisticsRepository.Observation> observations,
                                HistoryPeriodNavigator.Window window) {
        HistoryStatisticsAnalytics.TemperatureSummary summary =
                HistoryStatisticsAnalytics.temperature(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_latest), formatTemperatureWithTime(summary.latest, summary.latestAt),
                getString(R.string.v2_kpi_minimum), formatTemperatureWithTime(summary.minimum, summary.minimumAt),
                getString(R.string.v2_kpi_maximum), formatTemperatureWithTime(summary.maximum, summary.maximumAt),
                getString(R.string.v2_kpi_span), summary.span == null ? getString(R.string.m3_not_available)
                        : String.format(locale(), "%.1f K", summary.span));
        addChart(parent, summary.points, V2MetricChartView.Mode.LINE, "°C",
                R.string.v2_temperature_note, summary.points.size(), window.expectedBuckets);
    }

    private void addFlow(LinearLayout parent,
                         List<HistoryStatisticsRepository.Observation> observations,
                         HistoryPeriodNavigator.Window window) {
        HistoryStatisticsAnalytics.FlowSummary summary = HistoryStatisticsAnalytics.flow(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_highest_peak), formatFlowWithTime(summary.maximum, summary.maximumAt),
                getString(R.string.v2_kpi_average_peak), formatFlow(summary.averagePeak),
                getString(R.string.v2_kpi_latest_peak), formatFlowWithTime(summary.latest, summary.latestAt),
                getString(R.string.v2_kpi_data_points), Integer.toString(summary.points.size()));
        addChart(parent, summary.points, V2MetricChartView.Mode.BARS, "m³/h",
                R.string.v2_flow_note, summary.points.size(), window.expectedBuckets);
    }

    private void addBattery(LinearLayout parent,
                            List<HistoryStatisticsRepository.Observation> observations,
                            HistoryPeriodNavigator.Window window) {
        HistoryStatisticsAnalytics.BatterySummary summary = HistoryStatisticsAnalytics.battery(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_start), formatPercentWithTime(summary.start, summary.startAt),
                getString(R.string.v2_kpi_end), formatPercentWithTime(summary.end, summary.endAt),
                getString(R.string.v2_kpi_change), summary.change == null ? getString(R.string.m3_not_available)
                        : String.format(locale(), "%+d pp", summary.change),
                getString(R.string.v2_kpi_data_points), Integer.toString(summary.points.size()));
        addChart(parent, summary.points, V2MetricChartView.Mode.LINE, "%",
                R.string.v2_battery_note, summary.points.size(), window.expectedBuckets);
    }

    private void addAlarms(LinearLayout parent,
                           List<HistoryStatisticsRepository.Observation> observations,
                           HistoryPeriodNavigator.Window window) {
        HistoryStatisticsAnalytics.AlarmSummary summary = HistoryStatisticsAnalytics.alarms(observations);
        String first = summary.events.isEmpty() ? getString(R.string.m3_not_available)
                : formatFloatingEvent(summary.events.get(0).timestamp);
        String last = summary.events.isEmpty() ? getString(R.string.m3_not_available)
                : formatFloatingEvent(summary.events.get(summary.events.size() - 1).timestamp);
        addKpis(parent,
                getString(R.string.v2_kpi_transitions), Integer.toString(summary.events.size()),
                getString(R.string.v2_kpi_current_state), getString(summary.activeAtEnd
                        ? R.string.v2_alarm_state_active : R.string.v2_alarm_state_clear),
                getString(R.string.v2_kpi_first_event), first,
                getString(R.string.v2_kpi_last_event), last);

        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.cardContent(this);
        inside.addView(MaterialUi.title(this, getString(R.string.v2_alarm_timeline)));
        if (summary.events.isEmpty()) {
            TextView empty = MaterialUi.body(this, getString(R.string.v2_alarm_no_transitions));
            empty.setPadding(0, MaterialUi.dp(this, 8), 0, 0);
            inside.addView(empty);
        } else {
            for (HistoryStatisticsAnalytics.AlarmEvent event : summary.events) {
                TextView row = MaterialUi.body(this, "• " + formatFloatingEvent(event.timestamp)
                        + " — " + alarmEventText(event));
                row.setPadding(0, MaterialUi.dp(this, 8), 0, 0);
                inside.addView(row);
            }
        }
        TextView note = MaterialUi.body(this, getString(R.string.v2_alarm_note));
        note.setPadding(0, MaterialUi.dp(this, 10), 0, 0);
        inside.addView(note);
        addCoverage(inside, countSelected(observations), window.expectedBuckets);
        card.addView(inside);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private void addChart(LinearLayout parent, List<HistoryStatisticsAnalytics.MetricPoint> points,
                          V2MetricChartView.Mode mode, String unit, int noteRes,
                          int available, int expected) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout inside = MaterialUi.cardContent(this);
        V2MetricChartView chart = new V2MetricChartView(this);
        chart.setEmptyText(getString(R.string.m3_not_available));
        chart.setData(chartEntries(points, mode == V2MetricChartView.Mode.LINE), mode, unit);
        chart.setContentDescription(getString(R.string.m3_chart_accessibility));
        inside.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView note = MaterialUi.body(this, getString(noteRes));
        note.setPadding(0, MaterialUi.dp(this, 8), 0, 0);
        inside.addView(note);
        addCoverage(inside, available, expected);
        card.addView(inside);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private List<V2MetricChartView.Entry> chartEntries(
            List<HistoryStatisticsAnalytics.MetricPoint> points, boolean line) {
        List<V2MetricChartView.Entry> result = new ArrayList<>();
        HistoryStatisticsAnalytics.MetricPoint previous = null;
        for (HistoryStatisticsAnalytics.MetricPoint point : points) {
            boolean connect = line && previous != null
                    && previous.segment.equals(point.segment)
                    && HistoryTimePresentation.adjacent(previous.timestamp, point.timestamp,
                    point.granularity);
            result.add(new V2MetricChartView.Entry(
                    HistoryTimePresentation.formatAxis(locale(), point.granularity, point.timestamp),
                    point.value, point.partial, point.segment, connect));
            previous = point;
        }
        return result;
    }

    private void addCoverage(LinearLayout parent, int available, int expected) {
        String text = expected <= 0
                ? getString(R.string.v2_coverage_count_only, available)
                : getString(R.string.v2_coverage, available, expected);
        TextView coverage = MaterialUi.body(this, text + " · "
                + getString(R.string.v2_coverage_not_sync_state));
        coverage.setPadding(0, MaterialUi.dp(this, 8), 0, 0);
        parent.addView(coverage);
    }

    private void addKpis(LinearLayout parent,
                         String l1, String v1, String l2, String v2,
                         String l3, String v3, String l4, String v4) {
        MaterialCardView card = MaterialUi.card(this);
        LinearLayout content = MaterialUi.cardContent(this);
        LinearLayout first = MaterialUi.horizontal(this);
        first.addView(kpi(l1, v1), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        first.addView(kpi(l2, v2), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(first);
        LinearLayout second = MaterialUi.horizontal(this);
        second.setPadding(0, MaterialUi.dp(this, 12), 0, 0);
        second.addView(kpi(l3, v3), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        second.addView(kpi(l4, v4), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(second);
        card.addView(content);
        MaterialUi.addTopMargin(parent, card, 10);
    }

    private View kpi(String label, String value) {
        LinearLayout box = MaterialUi.vertical(this);
        box.addView(MaterialUi.label(this, label));
        TextView number = MaterialUi.title(this, value);
        number.setPadding(0, MaterialUi.dp(this, 3), MaterialUi.dp(this, 8), 0);
        box.addView(number);
        return box;
    }

    private void addPeriodNavigator(LinearLayout parent, HistoryPeriodNavigator navigator,
                                    boolean history) {
        LinearLayout row = MaterialUi.horizontal(this);
        MaterialButton previous = outlinedButton("‹");
        previous.setContentDescription(getString(R.string.v2_previous_period));
        previous.setEnabled(!navigator.allPeriods());
        previous.setOnClickListener(v -> { navigator.move(-1); render(); });
        row.addView(previous, new LinearLayout.LayoutParams(
                MaterialUi.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton center = outlinedButton(navigator.label(locale(), getString(R.string.v2_all_periods)));
        center.setContentDescription(getString(R.string.v2_choose_period));
        center.setOnClickListener(v -> showDatePicker(navigator));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        centerLp.setMarginStart(MaterialUi.dp(this, 8));
        centerLp.setMarginEnd(MaterialUi.dp(this, 8));
        row.addView(center, centerLp);

        MaterialButton next = outlinedButton("›");
        next.setContentDescription(getString(R.string.v2_next_period));
        next.setEnabled(!navigator.allPeriods());
        next.setOnClickListener(v -> { navigator.move(1); render(); });
        row.addView(next, new LinearLayout.LayoutParams(
                MaterialUi.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        MaterialUi.addTopMargin(parent, row, 8);
    }

    private void showDatePicker(HistoryPeriodNavigator navigator) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            navigator.setDate(year, month, day);
            render();
        }, navigator.year(), navigator.month0(), navigator.day()).show();
    }

    private void addHistoryChip(ChipGroup group, HistorySemanticTimeline.Granularity value,
                                int labelRes) {
        Chip chip = choiceChip(labelRes, historyFilter == value);
        chip.setOnClickListener(v -> {
            historyFilter = value;
            historyPeriod.setScale(HistoryPeriodNavigator.forHistory(value));
            render();
        });
        group.addView(chip);
    }

    private void addScaleChip(ChipGroup group, HistoryPeriodNavigator.Scale value, int labelRes) {
        Chip chip = choiceChip(labelRes, statsPeriod.scale() == value);
        chip.setOnClickListener(v -> { statsPeriod.setScale(value); render(); });
        group.addView(chip);
    }

    private void addMetricChip(ChipGroup group, Metric value, int labelRes) {
        Chip chip = choiceChip(labelRes, metric == value);
        chip.setOnClickListener(v -> { metric = value; render(); });
        group.addView(chip);
    }

    private Chip choiceChip(int labelRes, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setChecked(checked);
        return chip;
    }

    private MaterialButton outlinedButton(CharSequence text) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        return button;
    }

    private List<HistoryRow> historyRows(List<HistoryStatisticsRepository.Observation> observations,
                                         Map<String, HistoryStatisticsAnalytics.Delta> deltas,
                                         HistoryPeriodNavigator.Window window) {
        List<HistoryRow> rows = new ArrayList<>();
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation.contextOnly) continue;
            if (historyAlarmsOnly && !observation.hasAlarm()) continue;
            rows.add(HistoryRow.observation(observation, deltas.get(observation.identity)));
        }
        if (historyFilter == null && !historyAlarmsOnly) {
            for (MeterLifecycleStore.Transition transition : repository.transitions(window)) {
                rows.add(HistoryRow.transition(transition));
            }
        }
        rows.sort(Comparator.comparingLong((HistoryRow row) -> row.sortMs).reversed());
        return rows;
    }

    private void bindHistoryCard(MaterialCardView card, HistoryRow row) {
        card.removeAllViews();
        if (row == null) {
            LinearLayout empty = MaterialUi.cardContent(this);
            empty.addView(MaterialUi.body(this, getString(R.string.m3_history_empty)));
            card.addView(empty);
            return;
        }
        if (row.transition != null) {
            LinearLayout content = MaterialUi.cardContent(this);
            content.addView(MaterialUi.title(this, getString(R.string.m3_meter_replacement_event)));
            content.addView(MaterialUi.body(this,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                            java.text.DateFormat.SHORT, locale())
                            .format(new java.util.Date(row.transition.confirmedAtMs))));
            content.addView(MaterialUi.body(this, getString(R.string.m3_meter_replacement_ids,
                    row.transition.predecessorMeterId, row.transition.successorMeterId)));
            card.addView(content);
            return;
        }

        HistoryStatisticsRepository.Observation observation = row.observation;
        LinearLayout content = MaterialUi.cardContent(this);
        content.addView(MaterialUi.label(this,
                HistoryTimePresentation.formatPrimary(locale(), observation) + " · "
                        + typeLabel(observation.granularity)));
        TextView reading = MaterialUi.headline(this, formatM3(observation.totalM3));
        reading.setTextSize(22f);
        reading.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(reading);

        if (row.delta != null && row.delta.consumptionM3 != null && row.delta.previous != null) {
            TextView consumption = MaterialUi.body(this, getString(R.string.m3_consumption_since,
                    formatM3(row.delta.consumptionM3),
                    HistoryTimePresentation.formatPredecessor(locale(), observation,
                            row.delta.previous)));
            consumption.setTypeface(consumption.getTypeface(), Typeface.BOLD);
            consumption.setTextColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorPrimary, getColor(R.color.app_primary)));
            content.addView(consumption);
        }
        TextView meter = MaterialUi.body(this, getString(R.string.m3_meter_id, observation.meterId));
        meter.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(meter);
        if (observation.live && observation.meterTime != null && !observation.meterTime.isEmpty()) {
            content.addView(MaterialUi.body(this, getString(R.string.m3_meter_time, observation.meterTime)));
        }
        if (observation.batteryPercent != null) {
            content.addView(MaterialUi.body(this, getString(R.string.m3_battery) + ": "
                    + getString(R.string.m3_unit_percent, observation.batteryPercent)));
        }
        if (observation.hasAlarm()) {
            String alarm = observation.live
                    ? MeterStatusPresentation.localizedAlarmCodes(this, observation.liveAlarmCodes)
                    : MeterStatusPresentation.historical(observation.archiveErrorFlags).summary(this);
            TextView warning = MaterialUi.body(this, getString(R.string.m3_historical_notice, alarm));
            warning.setTextColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorError, getColor(R.color.app_error)));
            content.addView(warning);
        }
        card.addView(content);
    }

    private String alarmEventText(HistoryStatisticsAnalytics.AlarmEvent event) {
        if (event.type == HistoryStatisticsAnalytics.AlarmEventType.CLEARED)
            return getString(R.string.v2_alarm_cleared);
        String summary = MeterStatusPresentation.historical(event.raw).summary(this);
        if (event.type == HistoryStatisticsAnalytics.AlarmEventType.ACTIVATED)
            return getString(R.string.v2_alarm_started, summary);
        if (event.type == HistoryStatisticsAnalytics.AlarmEventType.CHANGED)
            return getString(R.string.v2_alarm_changed, summary);
        return getString(R.string.v2_alarm_observed, summary);
    }

    private String formatFloatingEvent(String timestamp) {
        return HistoryTimePresentation.formatFloatingPrimary(locale(),
                HistoryStatisticsRepository.targetGranularity(statsPeriod.scale()), timestamp);
    }

    private String formatM3(Double value) {
        return value == null ? getString(R.string.m3_not_available) : getString(R.string.m3_unit_m3, value);
    }

    private String formatFlow(Double value) {
        return value == null ? getString(R.string.m3_not_available) : getString(R.string.m3_unit_flow, value);
    }

    private String formatFlowWithTime(Double value, String timestamp) {
        if (value == null) return getString(R.string.m3_not_available);
        return formatFlow(value) + timeSuffix(timestamp);
    }

    private String formatTemperatureWithTime(Double value, String timestamp) {
        if (value == null) return getString(R.string.m3_not_available);
        return getString(R.string.m3_unit_temperature, value) + timeSuffix(timestamp);
    }

    private String formatPercentWithTime(Integer value, String timestamp) {
        if (value == null) return getString(R.string.m3_not_available);
        return getString(R.string.m3_unit_percent, value) + timeSuffix(timestamp);
    }

    private String formatMetricWithTime(Double value, String timestamp, String unit) {
        if (value == null) return getString(R.string.m3_not_available);
        String formatted = "m³".equals(unit) ? formatM3(value)
                : String.format(locale(), "%.3f %s", value, unit);
        return formatted + timeSuffix(timestamp);
    }

    private String timeSuffix(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        return " · " + formatFloatingEvent(timestamp);
    }

    private int countSelected(List<HistoryStatisticsRepository.Observation> observations) {
        int count = 0;
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (!observation.contextOnly) count++;
        }
        return count;
    }

    private String typeLabel(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.LIVE) return getString(R.string.m3_filter_live);
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return getString(R.string.m3_filter_hour);
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return getString(R.string.m3_filter_day);
        if (granularity == HistorySemanticTimeline.Granularity.YEAR) return getString(R.string.m3_filter_year);
        return getString(R.string.m3_filter_month);
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private final class HistoryAdapter extends BaseAdapter {
        private final List<HistoryRow> rows;

        HistoryAdapter(List<HistoryRow> rows) {
            this.rows = rows == null ? Collections.emptyList() : rows;
        }

        @Override public int getCount() { return rows.isEmpty() ? 1 : rows.size(); }
        @Override public HistoryRow getItem(int position) { return rows.isEmpty() ? null : rows.get(position); }
        @Override public long getItemId(int position) {
            HistoryRow row = getItem(position);
            return row == null ? 0L : row.sortMs;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            MaterialCardView card = convertView instanceof MaterialCardView
                    ? (MaterialCardView) convertView : MaterialUi.card(HistoryStatisticsActivity.this);
            bindHistoryCard(card, getItem(position));
            return card;
        }
    }

    private static final class HistoryRow {
        final HistoryStatisticsRepository.Observation observation;
        final HistoryStatisticsAnalytics.Delta delta;
        final MeterLifecycleStore.Transition transition;
        final long sortMs;

        private HistoryRow(HistoryStatisticsRepository.Observation observation,
                           HistoryStatisticsAnalytics.Delta delta,
                           MeterLifecycleStore.Transition transition, long sortMs) {
            this.observation = observation;
            this.delta = delta;
            this.transition = transition;
            this.sortMs = sortMs;
        }

        static HistoryRow observation(HistoryStatisticsRepository.Observation observation,
                                      HistoryStatisticsAnalytics.Delta delta) {
            return new HistoryRow(observation, delta, null, observation.sortMs);
        }

        static HistoryRow transition(MeterLifecycleStore.Transition transition) {
            String local = HistoryTimePresentation.localMinute(transition.confirmedAtMs);
            return new HistoryRow(null, null, transition,
                    HistoryTimePresentation.floatingSortMs(local));
        }
    }
}
