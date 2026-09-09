package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only LOCAL-time History/Statistics query facade.
 *
 * <p>Canonical UTC selection is joined back to the proven History measurement mapping by the
 * occurrence-safe archive identity. Measurement semantics stay single-sourced while selection,
 * ordering and presentation use the resolved real timeline.</p>
 */
final class HistoryLocalQueryRepository implements AutoCloseable {
    static final class Row {
        final HistoryStatisticsRepository.Observation observation;
        final HistoryStatisticsRepository.Observation previousObservation;
        final HistoryLocalArchiveReadModel.Row time;

        Row(HistoryStatisticsRepository.Observation observation,
            HistoryStatisticsRepository.Observation previousObservation,
            HistoryLocalArchiveReadModel.Row time) {
            this.observation = observation;
            this.previousObservation = previousObservation;
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

        /** Selected rows plus one occurrence-safe predecessor boundary where analytics needs it. */
        List<HistoryStatisticsRepository.Observation> observations() {
            Map<String, HistoryStatisticsRepository.Observation> byIdentity = new LinkedHashMap<>();
            for (Row row : rows) {
                if (row.previousObservation != null && !byIdentity.containsKey(row.previousObservation.identity)) {
                    byIdentity.put(row.previousObservation.identity, row.previousObservation);
                }
                if (row.observation != null) byIdentity.put(row.observation.identity, row.observation);
            }
            List<HistoryStatisticsRepository.Observation> out = new ArrayList<>(byIdentity.values());
            out.sort((first, second) -> {
                int byTime = Long.compare(first.sortMs, second.sortMs);
                if (byTime != 0) return byTime;
                int byGranularity = first.granularity.name().compareTo(second.granularity.name());
                if (byGranularity != 0) return byGranularity;
                return first.identity.compareTo(second.identity);
            });
            return out;
        }
    }

    private final HistoryStatisticsRepository history;
    private final ArchiveUtcRepository utc;

    HistoryLocalQueryRepository(Context context) {
        Context app = context.getApplicationContext();
        history = new HistoryStatisticsRepository(app, true);
        utc = new ArchiveUtcRepository(app);
    }

    List<HistoryStatisticsRepository.Observation> queryHistory(
            HistorySemanticTimeline.Granularity filter,
            HistoryPeriodNavigator.Window window) {
        List<HistoryStatisticsRepository.Observation> out = new ArrayList<>();
        if (filter == null || filter == HistorySemanticTimeline.Granularity.LIVE) {
            out.addAll(history.queryHistory(
                    HistorySemanticTimeline.Granularity.LIVE,
                    window,
                    true));
        }
        for (HistorySemanticTimeline.Granularity granularity : archiveGranularities(filter)) {
            out.addAll(queryArchive(
                    granularity,
                    window,
                    ArchiveLocalWindowSelection.Semantics.HISTORY_OVERLAP).observations());
        }
        return deduplicateAndSort(out);
    }

    List<HistoryStatisticsRepository.Observation> queryStatistics(
            HistoryPeriodNavigator.Window window,
            HistorySemanticTimeline.Granularity granularity) {
        return queryArchive(
                granularity,
                window,
                ArchiveLocalWindowSelection.Semantics.STATISTICS_FULLY_CONTAINED).observations();
    }

    HistoryStatisticsRepository.Availability availability(HistoryPeriodNavigator.Window window) {
        int live = visibleCount(history.queryHistory(
                HistorySemanticTimeline.Granularity.LIVE, window, false));
        int hour = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.HOUR));
        int day = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.DAY));
        int month = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.MONTH));
        return new HistoryStatisticsRepository.Availability(live, hour, day, month);
    }

    /**
     * Returns a fail-closed real-hour expectation for LOCAL statistics. Only meters that actually
     * have hourly archive evidence participate. Missing zones, ambiguous boundaries or different
     * DST outcomes across participating meter zones deliberately produce null instead of a guess.
     */
    Integer expectedFullHours(HistoryPeriodNavigator.Window window) {
        HistoryLocalWindowInput local = HistoryLocalWindowInput.from(window);
        if (local == null) return null;

        Integer expected = null;
        boolean hasHourlyArchiveEvidence = false;
        for (String meterId : history.meterIds()) {
            List<ArchiveUtcProjection.Period> periods = utc.project(
                    meterId, ArchiveFamilyPeriod.Family.HOUR);
            if (periods.isEmpty()) continue;
            hasHourlyArchiveEvidence = true;

            ZoneId zone = utc.meterZone(meterId);
            if (zone == null) return null;
            Integer candidate = HistoryLocalBucketExpectation.fullHours(local.start, local.end, zone);
            if (candidate == null) return null;
            if (expected == null) {
                expected = candidate;
            } else if (!expected.equals(candidate)) {
                return null;
            }
        }
        return hasHourlyArchiveEvidence ? expected : null;
    }

    Result queryArchive(
            HistorySemanticTimeline.Granularity granularity,
            HistoryPeriodNavigator.Window window,
            ArchiveLocalWindowSelection.Semantics semantics) {
        ArchiveFamilyPeriod.Family family = family(granularity);
        if (family == null) {
            return new Result(Collections.emptyList(), Collections.emptyList(), false);
        }

        Map<String, HistoryStatisticsRepository.Observation> measurementByIdentity =
                allArchiveMeasurements(granularity);
        if (window != null && window.allPeriods) {
            return queryAllPeriods(family, measurementByIdentity);
        }

        HistoryLocalWindowInput local = HistoryLocalWindowInput.from(window);
        if (local == null) {
            return new Result(Collections.emptyList(), Collections.emptyList(), false);
        }

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
            appendRows(rows, HistoryLocalArchiveReadModel.rows(selection), measurementByIdentity);
        }
        sortRows(rows);
        return new Result(rows, states, true);
    }

    private Result queryAllPeriods(
            ArchiveFamilyPeriod.Family family,
            Map<String, HistoryStatisticsRepository.Observation> measurementByIdentity) {
        List<Row> rows = new ArrayList<>();
        List<MeterState> states = new ArrayList<>();
        for (String meterId : history.meterIds()) {
            ZoneId zone = utc.meterZone(meterId);
            List<ArchiveUtcProjection.Period> projected = utc.project(meterId, family);
            boolean unresolved = zone == null;
            for (ArchiveUtcProjection.Period period : projected) {
                if (period != null && !period.resolvedInterval()) {
                    unresolved = true;
                    break;
                }
            }
            states.add(new MeterState(
                    meterId,
                    zone == null ? LocalTimeWindowResolver.Status.ZONE_MISSING
                            : LocalTimeWindowResolver.Status.RESOLVED,
                    null,
                    unresolved));
            appendRows(rows, HistoryLocalArchiveReadModel.rows(projected, zone), measurementByIdentity);
        }
        sortRows(rows);
        return new Result(rows, states, false);
    }

    private static void appendRows(
            List<Row> out,
            List<HistoryLocalArchiveReadModel.Row> times,
            Map<String, HistoryStatisticsRepository.Observation> measurementByIdentity) {
        for (HistoryLocalArchiveReadModel.Row time : times) {
            HistoryStatisticsRepository.Observation rawCurrent = measurementByIdentity.get(time.identity);
            if (rawCurrent == null) continue;
            HistoryStatisticsRepository.Observation current =
                    HistoryResolvedObservationAdapter.selected(rawCurrent, time);
            HistoryStatisticsRepository.Observation rawPrevious =
                    measurementByIdentity.get(time.startIdentity);
            HistoryStatisticsRepository.Observation previous =
                    HistoryResolvedObservationAdapter.context(rawPrevious, time.startUtcMs, time.zoneId);
            out.add(new Row(current, previous, time));
        }
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

    private static List<HistorySemanticTimeline.Granularity> archiveGranularities(
            HistorySemanticTimeline.Granularity filter) {
        if (filter == null) return Arrays.asList(
                HistorySemanticTimeline.Granularity.HOUR,
                HistorySemanticTimeline.Granularity.DAY,
                HistorySemanticTimeline.Granularity.MONTH);
        return family(filter) == null
                ? Collections.emptyList()
                : Collections.singletonList(filter);
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

    private static List<HistoryStatisticsRepository.Observation> deduplicateAndSort(
            List<HistoryStatisticsRepository.Observation> observations) {
        Map<String, HistoryStatisticsRepository.Observation> byIdentity = new LinkedHashMap<>();
        for (HistoryStatisticsRepository.Observation observation : observations) {
            if (observation == null) continue;
            HistoryStatisticsRepository.Observation existing = byIdentity.get(observation.identity);
            if (existing == null || (existing.contextOnly && !observation.contextOnly)) {
                byIdentity.put(observation.identity, observation);
            }
        }
        List<HistoryStatisticsRepository.Observation> out = new ArrayList<>(byIdentity.values());
        out.sort((first, second) -> {
            int byTime = Long.compare(first.sortMs, second.sortMs);
            if (byTime != 0) return byTime;
            int byGranularity = first.granularity.name().compareTo(second.granularity.name());
            if (byGranularity != 0) return byGranularity;
            return first.identity.compareTo(second.identity);
        });
        return out;
    }

    private static int visibleCount(List<HistoryStatisticsRepository.Observation> observations) {
        int count = 0;
        if (observations != null) {
            for (HistoryStatisticsRepository.Observation observation : observations) {
                if (observation != null && !observation.contextOnly) count++;
            }
        }
        return count;
    }

    private static void sortRows(List<Row> rows) {
        rows.sort((first, second) -> {
            int byEnd = Long.compare(first.time.endUtcMs, second.time.endUtcMs);
            if (byEnd != 0) return byEnd;
            return first.observation.identity.compareTo(second.observation.identity);
        });
    }

    @Override public void close() {
        history.close();
        utc.close();
    }
}
