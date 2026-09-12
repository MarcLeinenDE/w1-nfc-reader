package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private HistorySemanticTimeline.Granularity statsResolutionOverride;
    private HistorySemanticTimeline.Granularity statsDisplayGranularity =
            HistorySemanticTimeline.Granularity.DAY;
    private AppTimeBasis renderedTimeBasis;

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

    @Override protected void onResume() {
        super.onResume();
        AppTimeBasis current = UiPreferences.getTimeBasis(this);
        if (repository != null && pageHost != null && renderedTimeBasis != null
                && current != renderedTimeBasis) {
            render();
        }
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
        renderedTimeBasis = UiPreferences.getTimeBasis(this);
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
        filters.setSingleLine(true);
        addHistoryChip(filters, null, R.string.m3_filter_all);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.LIVE, R.string.m3_filter_live);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.HOUR, R.string.m3_filter_hour);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.DAY, R.string.m3_filter_day);
        addHistoryChip(filters, HistorySemanticTimeline.Granularity.MONTH, R.string.m3_filter_month);
        MaterialUi.addTopMargin(root, filters, 12);

        HistoryPeriodNavigator.Scale expectedScale = HistoryPeriodNavigator.forHistory(historyFilter);
        if (historyPeriod.scale() != expectedScale) historyPeriod.setScale(expectedScale);
        addPeriodNavigator(root, historyPeriod, true);

        HistoryPeriodNavigator.Window window = historyPeriod.window();
        if (window.customRange) addAvailabilityLine(root, repository.availability(window));

        List<HistoryStatisticsRepository.Observation> observations =
                repository.queryHistory(historyFilter, window, true);
        addLocalResolutionNotice(root, repository.lastLocalResolutionIssue());
        Map<String, HistoryStatisticsAnalytics.Delta> deltas =
                HistoryStatisticsAnalytics.deltas(observations);
        List<HistoryRow> rows = historyRows(observations, deltas, window);

        ListView list = new ListView(this);
        list.setDividerHeight(MaterialUi.dp(this, 8));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setClipToPadding(false);
        list.setPadding(0, MaterialUi.dp(this, 8), 0, 0);
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

        addPeriodNavigator(content, statsPeriod, false);
        HistoryPeriodNavigator.Window window = statsPeriod.window();
        HistoryStatisticsRepository.Availability availability = window.customRange
                ? repository.availability(window) : null;
        HistorySemanticTimeline.Granularity automatic = window.customRange
                ? HistoryCustomRangeSemantics.automaticResolution(window, availability)
                : HistoryStatisticsRepository.targetGranularity(window.scale);
        HistorySemanticTimeline.Granularity effective = window.customRange
                && statsResolutionOverride != null ? statsResolutionOverride : automatic;
        statsDisplayGranularity = effective;
        int expected = repository.expectedBuckets(window, effective);

        addStatisticsSelectors(content, window, availability, automatic, effective);
        if (window.customRange) {
            addAvailabilityLine(content, availability);
            addCustomRangePrecision(content, window, availability, effective, expected);
        }

        List<HistoryStatisticsRepository.Observation> observations =
                repository.queryStatistics(window, effective);
        addLocalResolutionNotice(content, repository.lastLocalResolutionIssue());
        addStatisticsMetric(content, observations, window, effective, expected);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addStatisticsSelectors(LinearLayout parent,
                                        HistoryPeriodNavigator.Window window,
                                        HistoryStatisticsRepository.Availability availability,
                                        HistorySemanticTimeline.Granularity automatic,
                                        HistorySemanticTimeline.Granularity effective) {
        LinearLayout row = MaterialUi.horizontal(this);
        MaterialButton metricButton = outlinedButton(metricLabel(metric) + " ▾");
        metricButton.setOnClickListener(v -> showMetricDialog());
        row.addView(metricButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (window.customRange) {
            String resolutionText = statsResolutionOverride == null
                    ? getString(R.string.v2_resolution_auto_value, typeLabel(automatic))
                    : getString(R.string.v2_resolution_value, typeLabel(effective));
            MaterialButton resolution = outlinedButton(resolutionText + " ▾");
            resolution.setOnClickListener(v -> showResolutionDialog(window, availability));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(MaterialUi.dp(this, 8));
            row.addView(resolution, lp);
        }
        MaterialUi.addTopMargin(parent, row, 8);
    }

    private void addStatisticsMetric(LinearLayout parent,
                                     List<HistoryStatisticsRepository.Observation> observations,
                                     HistoryPeriodNavigator.Window window,
                                     HistorySemanticTimeline.Granularity granularity,
                                     int expected) {
        switch (metric) {
            case TEMPERATURE:
                addTemperature(parent, observations, expected);
                break;
            case FLOW:
                addFlow(parent, observations, expected);
                break;
            case BATTERY:
                addBattery(parent, observations, expected);
                break;
            case ALARMS:
                addAlarms(parent, observations, expected);
                break;
            case CONSUMPTION:
            default:
                addConsumption(parent, observations, window, granularity, expected);
                break;
        }
    }

    private void addConsumption(LinearLayout parent,
                                List<HistoryStatisticsRepository.Observation> observations,
                                HistoryPeriodNavigator.Window window,
                                HistorySemanticTimeline.Granularity granularity,
                                int expected) {
        HistoryStatisticsAnalytics.ConsumptionSummary summary =
                HistoryStatisticsAnalytics.consumption(observations);
        int totalLabel = R.string.v2_kpi_total;
        if (window.customRange && !HistoryCustomRangeSemantics.exactEdges(window, granularity)) {
            totalLabel = R.string.v2_kpi_contained_total;
        } else if (expected > 0 && summary.availableBuckets < expected) {
            totalLabel = R.string.v2_kpi_known_total;
        }
        addKpis(parent,
                getString(totalLabel), formatM3(summary.total),
                getString(R.string.v2_kpi_average), formatM3(summary.average),
                getString(R.string.v2_kpi_highest), formatMetricWithTime(summary.maximum, summary.maximumAt, "m³"),
                getString(R.string.v2_kpi_lowest), formatMetricWithTime(summary.minimum, summary.minimumAt, "m³"));
        addChart(parent, summary.points, V2MetricChartView.Mode.BARS, "m³",
                R.string.v2_consumption_note, summary.availableBuckets, expected);
    }

    private void addTemperature(LinearLayout parent,
                                List<HistoryStatisticsRepository.Observation> observations,
                                int expected) {
        HistoryStatisticsAnalytics.TemperatureSummary summary =
                HistoryStatisticsAnalytics.temperature(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_latest), formatTemperatureWithTime(summary.latest, summary.latestAt),
                getString(R.string.v2_kpi_minimum), formatTemperatureWithTime(summary.minimum, summary.minimumAt),
                getString(R.string.v2_kpi_maximum), formatTemperatureWithTime(summary.maximum, summary.maximumAt),
                getString(R.string.v2_kpi_span), summary.span == null ? getString(R.string.m3_not_available)
                        : String.format(locale(), "%.1f K", summary.span));
        addChart(parent, summary.points, V2MetricChartView.Mode.LINE, "°C",
                R.string.v2_temperature_note, summary.points.size(), expected);
    }

    private void addFlow(LinearLayout parent,
                         List<HistoryStatisticsRepository.Observation> observations,
                         int expected) {
        HistoryStatisticsAnalytics.FlowSummary summary = HistoryStatisticsAnalytics.flow(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_highest_peak), formatFlowWithTime(summary.maximum, summary.maximumAt),
                getString(R.string.v2_kpi_average_peak), formatFlow(summary.averagePeak),
                getString(R.string.v2_kpi_latest_peak), formatFlowWithTime(summary.latest, summary.latestAt),
                getString(R.string.v2_kpi_data_points), Integer.toString(summary.points.size()));
        addChart(parent, summary.points, V2MetricChartView.Mode.BARS, "m³/h",
                R.string.v2_flow_note, summary.points.size(), expected);
    }

    private void addBattery(LinearLayout parent,
                            List<HistoryStatisticsRepository.Observation> observations,
                            int expected) {
        HistoryStatisticsAnalytics.BatterySummary summary = HistoryStatisticsAnalytics.battery(observations);
        addKpis(parent,
                getString(R.string.v2_kpi_start), formatPercentWithTime(summary.start, summary.startAt),
                getString(R.string.v2_kpi_end), formatPercentWithTime(summary.end, summary.endAt),
                getString(R.string.v2_kpi_change), summary.change == null ? getString(R.string.m3_not_available)
                        : String.format(locale(), "%+d %s", summary.change,
                        getString(R.string.v2_percentage_points_short)),
                getString(R.string.v2_kpi_data_points), Integer.toString(summary.points.size()));
        addChart(parent, summary.points, V2MetricChartView.Mode.LINE, "%",
                R.string.v2_battery_note, summary.points.size(), expected);
    }

    private void addAlarms(LinearLayout parent,
                           List<HistoryStatisticsRepository.Observation> observations,
                           int expected) {
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
        addCoverage(inside, countSelected(observations), expected);
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

    private void addLocalResolutionNotice(
            LinearLayout parent,
            HistoryLocalQueryRepository.ResolutionIssue issue) {
        if (issue == null || issue == HistoryLocalQueryRepository.ResolutionIssue.NONE
                || UiPreferences.getTimeBasis(this) != AppTimeBasis.LOCAL) return;
        int textRes;
        if (issue == HistoryLocalQueryRepository.ResolutionIssue.ZONE_MISSING) {
            textRes = R.string.v21_local_zone_missing_notice;
        } else if (issue == HistoryLocalQueryRepository.ResolutionIssue.WINDOW_UNRESOLVED) {
            textRes = R.string.v21_local_window_unresolved_notice;
        } else {
            textRes = R.string.v21_local_archive_time_unresolved_notice;
        }
        TextView warning = MaterialUi.body(this, getString(textRes));
        warning.setTextColor(MaterialUi.color(this,
                com.google.android.material.R.attr.colorError, getColor(R.color.app_error)));
        warning.setPadding(0, MaterialUi.dp(this, 8), 0, MaterialUi.dp(this, 2));
        parent.addView(warning);
    }

    private void addAvailabilityLine(LinearLayout parent,
                                     HistoryStatisticsRepository.Availability availability) {
        if (availability == null) return;
        TextView value = MaterialUi.body(this, getString(R.string.v2_available_data_format,
                availability.live, availability.hour, availability.day, availability.month));
        value.setPadding(0, MaterialUi.dp(this, 6), 0, 0);
        parent.addView(value);
    }

    private void addCustomRangePrecision(LinearLayout parent,
                                         HistoryPeriodNavigator.Window window,
                                         HistoryStatisticsRepository.Availability availability,
                                         HistorySemanticTimeline.Granularity granularity,
                                         int expected) {
        TextView note;
        if (expected <= 0) {
            note = MaterialUi.body(this, getString(R.string.v2_range_no_full_buckets,
                    typeLabel(granularity)));
            note.setTextColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorError, getColor(R.color.app_error)));
        } else if (!HistoryCustomRangeSemantics.exactEdges(window, granularity)) {
            String start = HistoryCustomRangeSemantics.exactInteriorStart(window, granularity);
            String end = HistoryCustomRangeSemantics.exactInteriorEnd(window, granularity);
            note = MaterialUi.body(this, getString(R.string.v2_range_edges_not_exact,
                    typeLabel(granularity),
                    HistoryTimePresentation.formatExactFloatingDateTime(locale(), start),
                    HistoryTimePresentation.formatExactFloatingDateTime(locale(), end)));
            note.setTextColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorError, getColor(R.color.app_error)));
        } else {
            int available = availability == null ? 0 : availability.count(granularity);
            note = MaterialUi.body(this, getString(R.string.v2_range_archive_coverage,
                    typeLabel(granularity), available, expected));
        }
        note.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        parent.addView(note);
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
        previous.setEnabled(!navigator.allPeriods() && !navigator.customRange());
        previous.setOnClickListener(v -> { navigator.move(-1); render(); });
        row.addView(previous, new LinearLayout.LayoutParams(
                MaterialUi.dp(this, 48), LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton center = outlinedButton(navigator.label(locale(), getString(R.string.v2_all_periods)));
        center.setContentDescription(getString(R.string.v2_choose_period));
        center.setSingleLine(true);
        center.setEllipsize(TextUtils.TruncateAt.END);
        center.setOnClickListener(v -> HistoryRangePickerUi.show(this, navigator, !history, () -> {
            if (!history && !navigator.customRange()) statsResolutionOverride = null;
            render();
        }));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        centerLp.setMarginStart(MaterialUi.dp(this, 6));
        centerLp.setMarginEnd(MaterialUi.dp(this, 6));
        row.addView(center, centerLp);

        MaterialButton next = outlinedButton("›");
        next.setContentDescription(getString(R.string.v2_next_period));
        next.setEnabled(!navigator.allPeriods() && !navigator.customRange());
        next.setOnClickListener(v -> { navigator.move(1); render(); });
        row.addView(next, new LinearLayout.LayoutParams(
                MaterialUi.dp(this, 48), LinearLayout.LayoutParams.WRAP_CONTENT));

        if (history) {
            MaterialButton filter = outlinedButton(historyAlarmsOnly
                    ? getString(R.string.v2_filter_active_count, 1)
                    : getString(R.string.v2_filter));
            filter.setOnClickListener(v -> showHistoryFilterDialog());
            LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(
                    MaterialUi.dp(this, 78), LinearLayout.LayoutParams.WRAP_CONTENT);
            filterLp.setMarginStart(MaterialUi.dp(this, 6));
            row.addView(filter, filterLp);
        }
        MaterialUi.addTopMargin(parent, row, 8);
    }

    private void showHistoryFilterDialog() {
        final boolean[] selected = {historyAlarmsOnly};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.v2_filter)
                .setMultiChoiceItems(new CharSequence[]{getString(R.string.v2_only_alarms)},
                        new boolean[]{historyAlarmsOnly},
                        (dialog, which, checked) -> selected[0] = checked)
                .setNegativeButton(R.string.m3_cancel, null)
                .setPositiveButton(R.string.v2_apply, (dialog, which) -> {
                    historyAlarmsOnly = selected[0];
                    render();
                })
                .show();
    }

    private void showMetricDialog() {
        Metric[] values = Metric.values();
        CharSequence[] labels = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = metricLabel(values[i]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.v2_metric_choose)
                .setItems(labels, (dialog, which) -> {
                    metric = values[which];
                    render();
                })
                .show();
    }

    private void showResolutionDialog(HistoryPeriodNavigator.Window window,
                                      HistoryStatisticsRepository.Availability availability) {
        HistorySemanticTimeline.Granularity automatic =
                HistoryCustomRangeSemantics.automaticResolution(window, availability);
        CharSequence[] labels = {
                getString(R.string.v2_resolution_auto_value, typeLabel(automatic)),
                getString(R.string.v2_resolution_with_count,
                        typeLabel(HistorySemanticTimeline.Granularity.HOUR),
                        availability == null ? 0 : availability.hour),
                getString(R.string.v2_resolution_with_count,
                        typeLabel(HistorySemanticTimeline.Granularity.DAY),
                        availability == null ? 0 : availability.day),
                getString(R.string.v2_resolution_with_count,
                        typeLabel(HistorySemanticTimeline.Granularity.MONTH),
                        availability == null ? 0 : availability.month)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.v2_resolution)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) statsResolutionOverride = null;
                    else if (which == 1) statsResolutionOverride = HistorySemanticTimeline.Granularity.HOUR;
                    else if (which == 2) statsResolutionOverride = HistorySemanticTimeline.Granularity.DAY;
                    else statsResolutionOverride = HistorySemanticTimeline.Granularity.MONTH;
                    render();
                })
                .show();
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
                historyPrimaryTime(observation) + " · " + typeLabel(observation.granularity)));
        TextView reading = MaterialUi.headline(this, formatM3(observation.totalM3));
        reading.setTextSize(22f);
        reading.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(reading);

        if (row.delta != null && row.delta.consumptionM3 != null && row.delta.previous != null) {
            String consumptionText;
            if (observation.live) {
                consumptionText = getString(R.string.m3_consumption_since,
                        formatM3(row.delta.consumptionM3),
                        historyPredecessorTime(observation, row.delta.previous));
            } else {
                consumptionText = getString(archiveConsumptionTextRes(observation.granularity),
                        formatM3(row.delta.consumptionM3));
            }
            TextView consumption = MaterialUi.body(this, consumptionText);
            consumption.setTypeface(consumption.getTypeface(), Typeface.BOLD);
            consumption.setTextColor(MaterialUi.color(this,
                    com.google.android.material.R.attr.colorPrimary, getColor(R.color.app_primary)));
            content.addView(consumption);
        }
        TextView meter = MaterialUi.body(this, getString(R.string.m3_meter_id, observation.meterId));
        meter.setPadding(0, MaterialUi.dp(this, 4), 0, 0);
        content.addView(meter);
        AppTimeBasis basis = UiPreferences.getTimeBasis(this);
        if (basis == AppTimeBasis.LOCAL
                && observation.meterTime != null && !observation.meterTime.isEmpty()) {
            String rawMeterTime = observation.live
                    ? HistoryTimePresentation.formatExactFloatingDateTime(locale(), observation.meterTime)
                    : HistoryTimePresentation.formatArchivePeriod(
                            locale(), observation.granularity, observation.meterTime);
            content.addView(MaterialUi.body(this,
                    getString(R.string.m3_meter_time, rawMeterTime)));
        } else if (basis == AppTimeBasis.METER) {
            String localTime = historyLocalSecondary(observation);
            if (localTime != null && !localTime.isEmpty()) {
                content.addView(MaterialUi.body(this,
                        getString(R.string.v21_time_basis_local) + ": " + localTime));
            }
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

    private String historyPrimaryTime(HistoryStatisticsRepository.Observation observation) {
        if (observation == null) return "";
        if (UiPreferences.getTimeBasis(this) != AppTimeBasis.METER) {
            return HistoryTimePresentation.formatPrimary(locale(), observation);
        }
        if (observation.live) {
            if (observation.meterTime == null || observation.meterTime.trim().isEmpty()) {
                return getString(R.string.m3_not_available);
            }
            return HistoryTimePresentation.formatExactFloatingDateTime(locale(), observation.meterTime);
        }
        return HistoryTimePresentation.formatArchivePeriod(
                locale(), observation.granularity, observation.timestamp);
    }

    private String historyPredecessorTime(
            HistoryStatisticsRepository.Observation current,
            HistoryStatisticsRepository.Observation previous) {
        if (UiPreferences.getTimeBasis(this) == AppTimeBasis.METER
                && previous != null && previous.live) {
            if (previous.meterTime == null || previous.meterTime.trim().isEmpty()) {
                return getString(R.string.m3_not_available);
            }
            return HistoryTimePresentation.formatExactFloatingDateTime(locale(), previous.meterTime);
        }
        return HistoryTimePresentation.formatPredecessor(locale(), current, previous);
    }

    private String historyLocalSecondary(HistoryStatisticsRepository.Observation observation) {
        if (observation == null) return null;
        if (observation.live) {
            if (observation.deviceTimeMs <= 0L) return null;
            return HistoryTimePresentation.formatPrimary(locale(), observation);
        }
        String resolved = repository.resolvedLocalArchiveTimestamp(observation);
        return resolved == null || resolved.isEmpty() ? null
                : HistoryTimePresentation.formatArchivePeriod(
                        locale(), observation.granularity, resolved);
    }

    private int archiveConsumptionTextRes(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return R.string.v2_history_consumption_hour;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return R.string.v2_history_consumption_day;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return R.string.v2_history_consumption_month;
        if (granularity == HistorySemanticTimeline.Granularity.YEAR) return R.string.v2_history_consumption_year;
        return R.string.v2_history_consumption_period;
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

    private String metricLabel(Metric value) {
        if (value == Metric.TEMPERATURE) return getString(R.string.v2_metric_temperature);
        if (value == Metric.FLOW) return getString(R.string.v2_metric_flow);
        if (value == Metric.BATTERY) return getString(R.string.m3_battery);
        if (value == Metric.ALARMS) return getString(R.string.v2_metric_alarms);
        return getString(R.string.m3_chart_consumption);
    }

    private String formatFloatingEvent(String timestamp) {
        return HistoryTimePresentation.formatFloatingPrimary(locale(), statsDisplayGranularity, timestamp);
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
