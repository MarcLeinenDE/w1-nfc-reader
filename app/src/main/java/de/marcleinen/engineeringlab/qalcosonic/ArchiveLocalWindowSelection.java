package de.marcleinen.engineeringlab.qalcosonic;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only selection of canonical UTC archive intervals for one user-entered LOCAL window.
 *
 * <p>History uses overlap semantics. Statistics uses only fully contained intervals. The coverage
 * result remains authoritative for gaps, partial edges and unresolved time; this selector never
 * interpolates, snaps or invents period boundaries.</p>
 */
final class ArchiveLocalWindowSelection {
    enum Semantics {
        HISTORY_OVERLAP,
        STATISTICS_FULLY_CONTAINED
    }

    static final class Result {
        final LocalTimeWindowResolver.Window window;
        final UtcCoverage.Result coverage;
        final List<ArchiveUtcProjection.Period> periods;
        final boolean relevantUnresolvedTime;

        Result(
                LocalTimeWindowResolver.Window window,
                UtcCoverage.Result coverage,
                List<ArchiveUtcProjection.Period> periods,
                boolean relevantUnresolvedTime) {
            this.window = window;
            this.coverage = coverage;
            this.periods = Collections.unmodifiableList(new ArrayList<>(periods));
            this.relevantUnresolvedTime = relevantUnresolvedTime;
        }

        boolean windowResolved() {
            return window != null && window.resolved();
        }
    }

    private ArchiveLocalWindowSelection() { }

    static Result select(
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            ZoneId meterZone,
            ZoneOffset explicitStartOffset,
            ZoneOffset explicitEndOffset,
            List<ArchiveUtcProjection.Period> projected,
            Semantics semantics) {
        ArchiveWindowCoverage.Result coverage = ArchiveWindowCoverage.evaluateLocal(
                startLocal,
                endLocal,
                meterZone,
                explicitStartOffset,
                explicitEndOffset,
                projected);
        if (!coverage.windowResolved()) {
            return new Result(
                    coverage.window,
                    coverage.coverage,
                    Collections.emptyList(),
                    coverage.relevantUnresolvedTime);
        }

        long requestStart = coverage.window.startUtcMs;
        long requestEnd = coverage.window.endUtcMs;
        List<ArchiveUtcProjection.Period> selected = new ArrayList<>();
        if (projected != null) {
            for (ArchiveUtcProjection.Period period : projected) {
                if (period == null || !period.resolvedInterval()) continue;
                boolean include;
                if (semantics == Semantics.STATISTICS_FULLY_CONTAINED) {
                    include = period.startUtcMs >= requestStart && period.endUtcMs <= requestEnd;
                } else {
                    include = period.endUtcMs > requestStart && period.startUtcMs < requestEnd;
                }
                if (include) selected.add(period);
            }
        }
        selected.sort((first, second) -> {
            int byStart = Long.compare(first.startUtcMs, second.startUtcMs);
            if (byStart != 0) return byStart;
            int byEnd = Long.compare(first.endUtcMs, second.endUtcMs);
            if (byEnd != 0) return byEnd;
            return first.identity.compareTo(second.identity);
        });
        return new Result(
                coverage.window,
                coverage.coverage,
                selected,
                coverage.relevantUnresolvedTime);
    }
}
