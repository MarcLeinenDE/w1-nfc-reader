package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Read-only local data access for the v2 History/Statistics surface. */
final class HistoryStatisticsRepository implements AutoCloseable {
    static final class Observation {
        final String identity;
        final String meterId;
        final HistorySemanticTimeline.Granularity granularity;
        final String timestamp;
        final long sortMs;
        final long deviceTimeMs;
        final boolean live;
        final boolean contextOnly;
        final String meterTime;
        final Double totalM3;
        final Double positiveM3;
        final Double reverseM3;
        final Double tariff1M3;
        final Double flowM3h;
        final Double maxFlowM3h;
        final String maxFlowAt;
        final Double minFlowM3h;
        final String minFlowAt;
        final Double waterTemperatureC;
        final Double externalTemperatureC;
        final Double maxTemperatureC;
        final String maxTemperatureAt;
        final Double minTemperatureC;
        final String minTemperatureAt;
        final Integer batteryPercent;
        final String liveAlarmCodes;
        final String archiveErrorFlags;
        final String onTime;
        final String operatingTime;
        final int observationCount;
        final int identicalConfirmations;
        final int revisionCount;
        final int conflictFlags;
        final String source;
        final String validation;

        Observation(String identity, String meterId,
                    HistorySemanticTimeline.Granularity granularity, String timestamp,
                    long sortMs, long deviceTimeMs, boolean live, boolean contextOnly,
                    String meterTime, Double totalM3, Double positiveM3, Double reverseM3,
                    Double tariff1M3, Double flowM3h, Double maxFlowM3h, String maxFlowAt,
                    Double minFlowM3h, String minFlowAt, Double waterTemperatureC,
                    Double externalTemperatureC, Double maxTemperatureC, String maxTemperatureAt,
                    Double minTemperatureC, String minTemperatureAt, Integer batteryPercent,
                    String liveAlarmCodes, String archiveErrorFlags, String onTime,
                    String operatingTime, int observationCount, int identicalConfirmations,
                    int revisionCount, int conflictFlags, String source, String validation) {
            this.identity = identity;
            this.meterId = meterId;
            this.granularity = granularity;
            this.timestamp = timestamp;
            this.sortMs = sortMs;
            this.deviceTimeMs = deviceTimeMs;
            this.live = live;
            this.contextOnly = contextOnly;
            this.meterTime = meterTime;
            this.totalM3 = totalM3;
            this.positiveM3 = positiveM3;
            this.reverseM3 = reverseM3;
            this.tariff1M3 = tariff1M3;
            this.flowM3h = flowM3h;
            this.maxFlowM3h = maxFlowM3h;
            this.maxFlowAt = maxFlowAt;
            this.minFlowM3h = minFlowM3h;
            this.minFlowAt = minFlowAt;
            this.waterTemperatureC = waterTemperatureC;
            this.externalTemperatureC = externalTemperatureC;
            this.maxTemperatureC = maxTemperatureC;
            this.maxTemperatureAt = maxTemperatureAt;
            this.minTemperatureC = minTemperatureC;
            this.minTemperatureAt = minTemperatureAt;
            this.batteryPercent = batteryPercent;
            this.liveAlarmCodes = liveAlarmCodes == null ? "" : liveAlarmCodes;
            this.archiveErrorFlags = archiveErrorFlags;
            this.onTime = onTime;
            this.operatingTime = operatingTime;
            this.observationCount = observationCount;
            this.identicalConfirmations = identicalConfirmations;
            this.revisionCount = revisionCount;
            this.conflictFlags = conflictFlags;
            this.source = source == null ? "" : source;
            this.validation = validation == null ? "" : validation;
        }

        boolean hasAlarm() {
            if (live) return !liveAlarmCodes.trim().isEmpty();
            return MeterStatusPresentation.historical(archiveErrorFlags).hasAnyStatus();
        }

        String alarmKey() {
            if (live) return liveAlarmCodes.trim();
            return MeterStatusPresentation.historical(archiveErrorFlags).raw;
        }
    }

    static final class Availability {
        final int live;
        final int hour;
        final int day;
        final int month;

        Availability(int live, int hour, int day, int month) {
            this.live = live;
            this.hour = hour;
            this.day = day;
            this.month = month;
        }

        int count(HistorySemanticTimeline.Granularity granularity) {
            if (granularity == HistorySemanticTimeline.Granularity.LIVE) return live;
            if (granularity == HistorySemanticTimeline.Granularity.HOUR) return hour;
            if (granularity == HistorySemanticTimeline.Granularity.DAY) return day;
            if (granularity == HistorySemanticTimeline.Granularity.MONTH) return month;
            return 0;
        }
    }

    private static final String[] ARCHIVE_COLUMNS = {
            "meter_id", "archive_family", "logger_timestamp",
            "total_volume", "positive_volume", "reverse_volume", "tariff1_volume",
            "max_flow", "max_flow_at", "min_flow", "min_flow_at", "flow",
            "max_temperature", "max_temperature_at", "min_temperature", "min_temperature_at",
            "temperature", "external_temperature", "battery_percent", "error_flags",
            "on_time", "operating_time", "observation_count", "identical_content_confirmations",
            "revision_count", "conflict_flags", "source", "validation", "occurrence_key"
    };

    private static final String[] LIVE_COLUMNS = {
            "id", "meter_id", "read_at_ms", "meter_time", "total_m3", "positive_m3",
            "negative_m3", "flow_m3h", "water_temp_c", "ambient_temp_c",
            "battery_percent", "alarm_codes"
    };

    private final MeterHistoryStore liveStore;
    private final ArchiveFamilyStore archiveStore;
    private final MeterLifecycleStore lifecycleStore;

    HistoryStatisticsRepository(Context context) {
        Context app = context.getApplicationContext();
        liveStore = new MeterHistoryStore(app);
        archiveStore = new ArchiveFamilyStore(app);
        lifecycleStore = new MeterLifecycleStore(app);
    }

    List<String> meterIds() {
        Set<String> ids = new LinkedHashSet<>(lifecycleStore.chainMeterIds());
        String active = lifecycleStore.activeMeterId();
        if (active != null) ids.add(active);
        ids.addAll(liveStore.getMeterIds());
        try (Cursor cursor = archiveStore.getReadableDatabase().rawQuery(
                "SELECT DISTINCT meter_id FROM " + ArchiveFamilyStore.TABLE_PERIODS
                        + " ORDER BY meter_id", null)) {
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) ids.add(value.trim());
            }
        }
        return new ArrayList<>(ids);
    }

    List<Observation> queryHistory(HistorySemanticTimeline.Granularity filter,
                                   HistoryPeriodNavigator.Window window,
                                   boolean includePredecessors) {
        List<Observation> result = new ArrayList<>();
        for (String meter : meterIds()) {
            boolean includesLive = filter == null || filter == HistorySemanticTimeline.Granularity.LIVE;
            if (includesLive) queryLive(result, meter, window, includePredecessors);
            for (ArchiveFamilyPeriod.Family family : families(filter)) {
                queryArchive(result, meter, family, window, includePredecessors);
            }
        }
        result.sort(observationOrder());
        return result;
    }

    List<Observation> queryStatistics(HistoryPeriodNavigator.Window window) {
        if (window == null) return new ArrayList<>();
        return queryStatistics(window, targetGranularity(window.scale));
    }

    List<Observation> queryStatistics(HistoryPeriodNavigator.Window window,
                                      HistorySemanticTimeline.Granularity granularity) {
        if (window == null || granularity == null) return new ArrayList<>();
        if (!window.customRange) return queryHistory(granularity, window, true);
        ArchiveFamilyPeriod.Family family = family(granularity);
        if (family == null) return new ArrayList<>();
        List<Observation> result = new ArrayList<>();
        for (String meter : meterIds()) {
            queryArchiveContained(result, meter, family, window, true);
        }
        result.sort(observationOrder());
        return result;
    }

    Availability availability(HistoryPeriodNavigator.Window window) {
        if (window == null) return new Availability(0, 0, 0, 0);
        int live = visibleCount(queryHistory(HistorySemanticTimeline.Granularity.LIVE, window, false));
        int hour = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.HOUR));
        int day = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.DAY));
        int month = visibleCount(queryStatistics(window, HistorySemanticTimeline.Granularity.MONTH));
        return new Availability(live, hour, day, month);
    }

    List<MeterLifecycleStore.Transition> transitions(HistoryPeriodNavigator.Window window) {
        List<MeterLifecycleStore.Transition> result = new ArrayList<>();
        for (MeterLifecycleStore.Transition transition : lifecycleStore.transitions()) {
            if (window == null || window.allPeriods
                    || (transition.confirmedAtMs >= window.deviceStartMs
                    && transition.confirmedAtMs < window.deviceEndMs)) {
                result.add(transition);
            }
        }
        return result;
    }

    static HistorySemanticTimeline.Granularity targetGranularity(HistoryPeriodNavigator.Scale scale) {
        if (scale == HistoryPeriodNavigator.Scale.DAY) return HistorySemanticTimeline.Granularity.HOUR;
        if (scale == HistoryPeriodNavigator.Scale.MONTH) return HistorySemanticTimeline.Granularity.DAY;
        return HistorySemanticTimeline.Granularity.MONTH;
    }

    @Override public void close() {
        liveStore.close();
        archiveStore.close();
    }

    private void queryArchive(List<Observation> out, String meter, ArchiveFamilyPeriod.Family family,
                              HistoryPeriodNavigator.Window window, boolean includePredecessor) {
        SQLiteDatabase db = archiveStore.getReadableDatabase();
        StringBuilder selection = new StringBuilder("meter_id = ? AND archive_family = ?");
        List<String> args = new ArrayList<>();
        args.add(meter);
        args.add(family.name());
        if (window != null && !window.allPeriods) {
            selection.append(" AND logger_timestamp > ? AND logger_timestamp <= ?");
            args.add(window.archiveStart);
            args.add(window.archiveEnd);
        }

        String firstDisplayedStart = null;
        try (Cursor cursor = db.query(ArchiveFamilyStore.TABLE_PERIODS, ARCHIVE_COLUMNS,
                selection.toString(), args.toArray(new String[0]), null, null,
                "logger_timestamp ASC, occurrence_key ASC")) {
            while (cursor.moveToNext()) {
                Observation observation = readArchive(cursor, false);
                out.add(observation);
                if (firstDisplayedStart == null) {
                    firstDisplayedStart = HistoryTimePresentation.periodStartTimestamp(
                            observation.timestamp, observation.granularity);
                }
            }
        }

        // A custom range may end in the middle of an archive period. History should show that
        // overlapping period as context instead of silently hiding what happened near the right
        // edge. Statistics uses queryArchiveContained() and never counts such partial edge buckets.
        if (window != null && window.customRange && window.archiveEnd != null) {
            try (Cursor cursor = db.query(ArchiveFamilyStore.TABLE_PERIODS, ARCHIVE_COLUMNS,
                    "meter_id = ? AND archive_family = ? AND logger_timestamp > ?",
                    new String[]{meter, family.name(), window.archiveEnd}, null, null,
                    "logger_timestamp ASC, occurrence_key ASC", "1")) {
                if (cursor.moveToFirst()) {
                    Observation candidate = readArchive(cursor, false);
                    if (HistoryCustomRangeSemantics.overlaps(candidate, window)) {
                        out.add(candidate);
                        if (firstDisplayedStart == null) {
                            firstDisplayedStart = HistoryTimePresentation.periodStartTimestamp(
                                    candidate.timestamp, candidate.granularity);
                        }
                    }
                }
            }
        }

        if (!includePredecessor || window == null || window.allPeriods) return;
        String predecessorBoundary = window.customRange ? firstDisplayedStart : window.archiveStart;
        if (predecessorBoundary == null) return;
        addArchiveContextAt(db, out, meter, family, predecessorBoundary);
    }

    private void queryArchiveContained(List<Observation> out, String meter,
                                       ArchiveFamilyPeriod.Family family,
                                       HistoryPeriodNavigator.Window window,
                                       boolean includePredecessor) {
        if (window == null || !window.customRange) {
            queryArchive(out, meter, family, window, includePredecessor);
            return;
        }
        SQLiteDatabase db = archiveStore.getReadableDatabase();
        String firstDisplayedStart = null;
        try (Cursor cursor = db.query(ArchiveFamilyStore.TABLE_PERIODS, ARCHIVE_COLUMNS,
                "meter_id = ? AND archive_family = ? AND logger_timestamp > ? AND logger_timestamp <= ?",
                new String[]{meter, family.name(), window.archiveStart, window.archiveEnd},
                null, null, "logger_timestamp ASC, occurrence_key ASC")) {
            while (cursor.moveToNext()) {
                Observation observation = readArchive(cursor, false);
                if (!HistoryCustomRangeSemantics.fullyContained(observation, window)) continue;
                out.add(observation);
                if (firstDisplayedStart == null) {
                    firstDisplayedStart = HistoryTimePresentation.periodStartTimestamp(
                            observation.timestamp, observation.granularity);
                }
            }
        }
        if (includePredecessor && firstDisplayedStart != null) {
            addArchiveContextAt(db, out, meter, family, firstDisplayedStart);
        }
    }

    private void addArchiveContextAt(SQLiteDatabase db, List<Observation> out, String meter,
                                     ArchiveFamilyPeriod.Family family, String boundary) {
        try (Cursor cursor = db.query(ArchiveFamilyStore.TABLE_PERIODS, ARCHIVE_COLUMNS,
                "meter_id = ? AND archive_family = ? AND logger_timestamp = ?",
                new String[]{meter, family.name(), boundary}, null, null,
                "occurrence_key ASC", "1")) {
            if (cursor.moveToFirst()) {
                Observation context = readArchive(cursor, true);
                boolean duplicate = false;
                for (Observation existing : out) {
                    if (existing.identity.equals(context.identity)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) out.add(context);
            }
        }
    }

    private void queryLive(List<Observation> out, String meter, HistoryPeriodNavigator.Window window,
                           boolean includePredecessor) {
        SQLiteDatabase db = liveStore.getReadableDatabase();
        StringBuilder selection = new StringBuilder("meter_id = ?");
        List<String> args = new ArrayList<>();
        args.add(meter);
        if (window != null && !window.allPeriods) {
            selection.append(" AND read_at_ms >= ? AND read_at_ms < ?");
            args.add(Long.toString(window.deviceStartMs));
            args.add(Long.toString(window.deviceEndMs));
        }
        try (Cursor cursor = db.query(MeterHistoryStore.TABLE_READINGS, LIVE_COLUMNS,
                selection.toString(), args.toArray(new String[0]), null, null, "read_at_ms ASC")) {
            while (cursor.moveToNext()) out.add(readLive(cursor, false));
        }

        if (!includePredecessor || window == null || window.allPeriods || window.deviceStartMs <= 0L) return;
        try (Cursor cursor = db.query(MeterHistoryStore.TABLE_READINGS, LIVE_COLUMNS,
                "meter_id = ? AND read_at_ms < ?",
                new String[]{meter, Long.toString(window.deviceStartMs)}, null, null,
                "read_at_ms DESC", "1")) {
            if (cursor.moveToFirst()) out.add(readLive(cursor, true));
        }
    }

    private static Observation readArchive(Cursor c, boolean contextOnly) {
        String meter = c.getString(0);
        ArchiveFamilyPeriod.Family family = ArchiveFamilyPeriod.Family.valueOf(c.getString(1));
        String timestamp = c.getString(2);
        String occurrenceKey = c.getString(28);
        HistorySemanticTimeline.Granularity granularity = granularity(family);
        return new Observation(
                ArchiveRecordIdentity.of(family, meter, timestamp, occurrenceKey),
                meter, granularity, timestamp, HistoryTimePresentation.floatingSortMs(timestamp),
                0L, false, contextOnly, "",
                measurement(c.getString(3)), measurement(c.getString(4)), measurement(c.getString(5)),
                measurement(c.getString(6)), measurement(c.getString(11)), measurement(c.getString(7)),
                c.getString(8), measurement(c.getString(9)), c.getString(10),
                measurement(c.getString(16)), measurement(c.getString(17)), measurement(c.getString(12)),
                c.getString(13), measurement(c.getString(14)), c.getString(15), integer(c.getString(18)),
                "", c.getString(19), c.getString(20), c.getString(21),
                c.getInt(22), c.getInt(23), c.getInt(24), c.getInt(25), c.getString(26), c.getString(27));
    }

    private static Observation readLive(Cursor c, boolean contextOnly) {
        long id = c.getLong(0);
        String meter = c.getString(1);
        long readAt = c.getLong(2);
        String timestamp = HistoryTimePresentation.localMinute(readAt);
        return new Observation(
                "LIVE|" + id, meter, HistorySemanticTimeline.Granularity.LIVE, timestamp,
                HistoryTimePresentation.floatingSortMs(timestamp), readAt, true, contextOnly,
                c.getString(3), nullableDouble(c, 4), nullableDouble(c, 5), nullableDouble(c, 6),
                null, nullableDouble(c, 7), null, "", null, "", nullableDouble(c, 8),
                nullableDouble(c, 9), null, "", null, "", nullableInteger(c, 10),
                c.getString(11), null, "", "", 1, 0, 0, 0, "LIVE", "");
    }

    private static List<ArchiveFamilyPeriod.Family> families(HistorySemanticTimeline.Granularity filter) {
        if (filter == null) return Arrays.asList(ArchiveFamilyPeriod.Family.HOUR,
                ArchiveFamilyPeriod.Family.DAY, ArchiveFamilyPeriod.Family.MONTH);
        ArchiveFamilyPeriod.Family one = family(filter);
        return one == null ? Collections.emptyList() : Collections.singletonList(one);
    }

    private static ArchiveFamilyPeriod.Family family(HistorySemanticTimeline.Granularity granularity) {
        if (granularity == HistorySemanticTimeline.Granularity.HOUR) return ArchiveFamilyPeriod.Family.HOUR;
        if (granularity == HistorySemanticTimeline.Granularity.DAY) return ArchiveFamilyPeriod.Family.DAY;
        if (granularity == HistorySemanticTimeline.Granularity.MONTH) return ArchiveFamilyPeriod.Family.MONTH;
        if (granularity == HistorySemanticTimeline.Granularity.YEAR) return ArchiveFamilyPeriod.Family.YEAR;
        return null;
    }

    private static HistorySemanticTimeline.Granularity granularity(ArchiveFamilyPeriod.Family family) {
        if (family == ArchiveFamilyPeriod.Family.HOUR) return HistorySemanticTimeline.Granularity.HOUR;
        if (family == ArchiveFamilyPeriod.Family.DAY) return HistorySemanticTimeline.Granularity.DAY;
        if (family == ArchiveFamilyPeriod.Family.YEAR) return HistorySemanticTimeline.Granularity.YEAR;
        return HistorySemanticTimeline.Granularity.MONTH;
    }

    private static int visibleCount(List<Observation> observations) {
        int count = 0;
        if (observations != null) {
            for (Observation observation : observations) {
                if (observation != null && !observation.contextOnly) count++;
            }
        }
        return count;
    }

    private static Comparator<Observation> observationOrder() {
        return Comparator.comparingLong((Observation value) -> value.sortMs)
                .thenComparing(value -> value.granularity.name())
                .thenComparing(value -> value.identity);
    }

    private static Double measurement(String value) {
        String number = ArchiveFamilyStore.measurementNumber(value);
        if (number == null || number.isEmpty()) return null;
        try { return Double.parseDouble(number); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Integer integer(String value) {
        Double parsed = measurement(value);
        return parsed == null ? null : (int) Math.round(parsed);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private static Integer nullableInteger(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getInt(index);
    }
}
