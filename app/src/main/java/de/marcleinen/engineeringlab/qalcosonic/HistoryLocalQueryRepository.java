package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only LOCAL-time archive query facade.
 *
 * <p>Canonical UTC selection is joined back to the proven History measurement mapping by the
 * occurrence-safe archive identity. This keeps measurement semantics single-sourced while time
 * selection remains independent from the legacy raw meter-time query.</p>
 */
final class HistoryLocalQueryRepository implements AutoCloseable {
    static final class Row {
        final HistoryStatisticsRepository.Observation observation;
        final HistoryLocalArchiveReadModel.Row time;

        Row(HistoryStatisticsRepository.Observation observation,
            HistoryLocalArchiveReadModel.Row time) {
            this.observation = observation;
            this.time = time;
        }
    }

    static final class MeterState {
        final String meterId;
        final LocalTimeWindowResolver.Status windowStatus;
        final UtcCoverage.Status coverageStatus;
        final boolean relevantUnresolvedTime;

        MeterState(
                String meterId,
                LocalTimeWindowResolver.Status windowStatus,
                UtcCoverage.Status coverageStatus,
                boolean relevantUnresolvedTime) {
            this.meterId = meterId;
            this.windowStatus = windowStatus;
            this.coverageStatus = coverageStatus;
            this.relevantUnresolvedTime = relevantUnresolvedTime;
        }
    }

    static final class Result {
        final List<Row> rows;
        final List<MeterState> meterStates;
        final boolean boundedWindow;

        Result(List<Row> rows, List<MeterState> meterStates, boolean boundedWindow) {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.meterStates = Collections.unmodifiableList(new ArrayList<>(meterStates));
            this.boundedWindow = boundedWindow;
        }
    }

    private final HistoryStatisticsRepository history;
    private final ArchiveUtcRepository utc;

    HistoryLocalQueryRepository(Context context) {
        Context app = context.getApplicationContext();
        history = new HistoryStatisticsRepository(app);
        utc = new ArchiveUtcRepository(app);
    }

    Result queryArchive(
            HistorySemanticTimeline.Granularity granularity,
            HistoryPeriodNavigator.Window window,
            ArchiveLocalWindowSelection.Semantics semantics) {
        ArchiveFamilyPeriod.Family family = family(granularity);
        HistoryLocalWindowInput local = HistoryLocalWindowInput.from(window);
        if (family == null || local == null) {
            return new Result(Collections.emptyList(), Collections.emptyList(), false);
        }

        Map<String, HistoryStatisticsRepository.Observation> measurementByIdentity =
                allArchiveMeasurements(granularity);
        List<Row> rows = new ArrayList<>();
        List<MeterState> states = new ArrayList<>();
        for (String meterId : history.meterIds()) {
            ArchiveLocalWindowSelection.Result selection = utc.selectLocal(
                    meterId,
                    family,
                    local.start,
                    local.end,
                    null,
                    null,
                    semantics);
            states.add(new MeterState(
                    meterId,
                    selection.window == null ? null : selection.window.status,
                    selection.coverage == null ? null : selection.coverage.status,
                    selection.relevantUnresolvedTime));
            for (HistoryLocalArchiveReadModel.Row time : HistoryLocalArchiveReadModel.rows(selection)) {
                HistoryStatisticsRepository.Observation observation =
                        measurementByIdentity.get(time.identity);
                if (observation != null) rows.add(new Row(observation, time));
            }
        }
        rows.sort((first, second) -> {
            int byEnd = Long.compare(first.time.endUtcMs, second.time.endUtcMs);
            if (byEnd != 0) return byEnd;
            return first.observation.identity.compareTo(second.observation.identity);
        });
        return new Result(rows, states, true);
    }

    private Map<String, HistoryStatisticsRepository.Observation> allArchiveMeasurements(
            HistorySemanticTimeline.Granularity granularity) {
        HistoryPeriodNavigator all = new HistoryPeriodNavigator(HistoryPeriodNavigator.Scale.YEAR);
        all.setAllPeriods(true);
        List<HistoryStatisticsRepository.Observation> observations = history.queryHistory(
                granularity,
                all.window(),
                false);
        Map<String, HistoryStatisticsRepository.Observation> byIdentity = new HashMap<>();
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation == null || observation.live) continue;
            byIdentity.put(observation.identity, observation);
        }
        return byIdentity;
    }

    private static ArchiveFamilyPeriod.Family family(
            HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) {
            return ArchiveFamilyPeriod.Family.HOUR;
        }
        if (granularity == HistorySemanticTimeline.Granularity.DAY) {
            return ArchiveFamilyPeriod.Family.DAY;
        }
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) {
            return ArchiveFamilyPeriod.Family.MONTH;
        }
        if (granularity == HistorySemanticTimeline.Granularity.YEAR) {
            return ArchiveFamilyPeriod.Family.YEAR;
        }
        return null;
    }

    @Override public void close() {
        history.close();
        utc.close();
    }
}
