package de.marcleinen.engineeringlab.qalcosonic;

import java.time.ZoneId;

/** Copies proven measurement rows onto the resolved LOCAL timeline without mutating persistence. */
final class HistoryResolvedObservationAdapter {
    private HistoryResolvedObservationAdapter() { }

    static HistoryStatisticsRepository.Observation selected(
            HistoryStatisticsRepository.Observation source,
            HistoryLocalArchiveReadModel.Row time) {
        return selected(source, time, false);
    }

    /**
     * Statistics may expose a physical interval that only overlaps a selected civil window.
     * Such an interval remains a real resolved interval, but contextOnly keeps it out of KPI totals
     * while still allowing chart presentation to show the measured edge value explicitly.
     */
    static HistoryStatisticsRepository.Observation selected(
            HistoryStatisticsRepository.Observation source,
            HistoryLocalArchiveReadModel.Row time,
            boolean contextOnly) {
        if (source == null || time == null || time.zoneId == null) return null;
        return copy(
                source,
                HistoryResolvedTimeToken.interval(time.startUtcMs, time.endUtcMs, time.zoneId),
                time.endUtcMs,
                contextOnly,
                source.timestamp);
    }

    static HistoryStatisticsRepository.Observation context(
            HistoryStatisticsRepository.Observation source,
            long boundaryUtcMs,
            ZoneId zoneId) {
        if (source == null || zoneId == null || boundaryUtcMs <= 0L) return null;
        return copy(
                source,
                HistoryResolvedTimeToken.boundary(boundaryUtcMs, zoneId),
                boundaryUtcMs,
                true,
                source.timestamp);
    }

    private static HistoryStatisticsRepository.Observation copy(
            HistoryStatisticsRepository.Observation source,
            String resolvedTimestamp,
            long sortMs,
            boolean contextOnly,
            String rawMeterTime) {
        return new HistoryStatisticsRepository.Observation(
                source.identity,
                source.meterId,
                source.granularity,
                resolvedTimestamp,
                sortMs,
                source.deviceTimeMs,
                source.live,
                contextOnly,
                rawMeterTime == null ? "" : rawMeterTime,
                source.totalM3,
                source.positiveM3,
                source.reverseM3,
                source.tariff1M3,
                source.flowM3h,
                source.maxFlowM3h,
                "",
                source.minFlowM3h,
                "",
                source.waterTemperatureC,
                source.externalTemperatureC,
                source.maxTemperatureC,
                "",
                source.minTemperatureC,
                "",
                source.batteryPercent,
                source.liveAlarmCodes,
                source.archiveErrorFlags,
                source.onTime,
                source.operatingTime,
                source.observationCount,
                source.identicalConfirmations,
                source.revisionCount,
                source.conflictFlags,
                source.source,
                source.validation);
    }
}
