package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure planning helpers for full sync, optional fast overlap sync and gap detection. No meter I/O. */
final class MonthlyArchiveSyncPlanner {
    enum Mode { FULL_SYNC, FAST_SYNC, GAP_HEALING }

    static final class Plan {
        final Mode mode;
        final int hardCap;
        final int requiredKnownOverlap;
        final List<String> targetGaps;

        Plan(Mode mode, int hardCap, int requiredKnownOverlap, List<String> targetGaps) {
            this.mode = mode;
            this.hardCap = hardCap;
            this.requiredKnownOverlap = requiredKnownOverlap;
            this.targetGaps = Collections.unmodifiableList(new ArrayList<>(targetGaps));
        }
    }

    /** Canonical user-requested Monthly plan: local completeness never suppresses meter retrieval. */
    static Plan fullSync(int hardCap) {
        validateHardCap(hardCap);
        return new Plan(Mode.FULL_SYNC, hardCap, 0, Collections.emptyList());
    }

    /** Optional optimization only. This must never replace the explicit full-sync plan. */
    static Plan fastSync(int hardCap, int requiredKnownOverlap) {
        validateHardCap(hardCap);
        if (requiredKnownOverlap < 1) throw new IllegalArgumentException("requiredKnownOverlap >= 1");
        return new Plan(Mode.FAST_SYNC, hardCap, requiredKnownOverlap, Collections.emptyList());
    }

    static Plan gapHealing(int hardCap, Collection<String> knownMonthlyTimestamps) {
        validateHardCap(hardCap);
        return new Plan(Mode.GAP_HEALING, hardCap, 0, findGaps(knownMonthlyTimestamps));
    }

    static boolean fastContinuityProven(
            List<MonthlyArchivePeriod> enumeratedNewestToOldest,
            Collection<String> knownMonthlyTimestamps,
            int requiredKnownOverlap) {
        if (requiredKnownOverlap < 1) return false;
        if (enumeratedNewestToOldest == null || enumeratedNewestToOldest.isEmpty()) return false;
        Set<String> known = new HashSet<>(knownMonthlyTimestamps == null
                ? Collections.emptySet() : knownMonthlyTimestamps);
        int tailOverlap = 0;
        for (int i = enumeratedNewestToOldest.size() - 1; i >= 0; i--) {
            MonthlyArchivePeriod p = enumeratedNewestToOldest.get(i);
            if (p != null && known.contains(p.loggerTimestamp)) tailOverlap++;
            else break;
        }
        return tailOverlap >= requiredKnownOverlap;
    }

    static List<String> unseenPeriods(
            List<MonthlyArchivePeriod> enumeratedNewestToOldest,
            Collection<String> knownMonthlyTimestamps) {
        Set<String> known = new HashSet<>(knownMonthlyTimestamps == null
                ? Collections.emptySet() : knownMonthlyTimestamps);
        List<String> unseen = new ArrayList<>();
        if (enumeratedNewestToOldest == null) return Collections.unmodifiableList(unseen);
        for (MonthlyArchivePeriod p : enumeratedNewestToOldest) {
            if (p != null && !known.contains(p.loggerTimestamp)) unseen.add(p.loggerTimestamp);
        }
        return Collections.unmodifiableList(unseen);
    }

    static List<String> findGaps(Collection<String> knownMonthlyTimestamps) {
        if (knownMonthlyTimestamps == null || knownMonthlyTimestamps.size() < 2) {
            return Collections.emptyList();
        }
        List<String> valid = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String raw : knownMonthlyTimestamps) {
            if (MonthlyArchiveEnumerator.previousCalendarMonth(raw) != null && unique.add(raw)) valid.add(raw);
        }
        if (valid.size() < 2) return Collections.emptyList();
        valid.sort(Collections.reverseOrder());
        String newest = valid.get(0);
        String oldest = valid.get(valid.size() - 1);
        List<String> gaps = new ArrayList<>();
        String cursor = MonthlyArchiveEnumerator.previousCalendarMonth(newest);
        int safety = MonthlyArchiveEnumerator.VENDOR_HARD_CAP_CANDIDATE + 12;
        while (cursor != null && cursor.compareTo(oldest) > 0 && safety-- > 0) {
            if (!unique.contains(cursor)) gaps.add(cursor);
            cursor = MonthlyArchiveEnumerator.previousCalendarMonth(cursor);
        }
        return Collections.unmodifiableList(gaps);
    }

    private static void validateHardCap(int hardCap) {
        if (hardCap < 1 || hardCap > MonthlyArchiveEnumerator.VENDOR_HARD_CAP_CANDIDATE) {
            throw new IllegalArgumentException("hardCap out of range");
        }
    }
}
