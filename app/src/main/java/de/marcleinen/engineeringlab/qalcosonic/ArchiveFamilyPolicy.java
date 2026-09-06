package de.marcleinen.engineeringlab.qalcosonic;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Family-specific archive rules transferred from the frozen real-device research.
 *
 * <p>Raw meter timestamps are intentionally treated as timezone-unresolved wall-clock values.
 * UTC is used only as a DST-free calendar arithmetic container; these values are not thereby
 * reinterpreted as real UTC instants.</p>
 */
final class ArchiveFamilyPolicy {
    private static final TimeZone RAW_CALENDAR_ZONE = TimeZone.getTimeZone("UTC");
    private static final long RAW_MINUTE_UNCERTAINTY_MS = 60_000L;

    final ArchiveFamilyPeriod.Family family;
    final int selectorSubcode;

    private ArchiveFamilyPolicy(ArchiveFamilyPeriod.Family family, int selectorSubcode) {
        this.family = family;
        this.selectorSubcode = selectorSubcode;
    }

    static ArchiveFamilyPolicy forFamily(ArchiveFamilyPeriod.Family family) {
        if (family == null) throw new IllegalArgumentException("family required");
        switch (family) {
            case HOUR:
                return new ArchiveFamilyPolicy(family, 0x60);
            case DAY:
                return new ArchiveFamilyPolicy(family, 0x30);
            case MONTH:
                return new ArchiveFamilyPolicy(family, 0x40);
            case YEAR:
                return new ArchiveFamilyPolicy(family, 0x20);
            default:
                throw new IllegalArgumentException("unsupported archive family " + family);
        }
    }

    byte[] applicationSelectFrame() {
        byte[] frame = new byte[]{
                0x68, 0x04, 0x04, 0x68,
                0x73, (byte) 0xFE, 0x50, (byte) selectorSubcode,
                0x00, 0x16
        };
        frame[8] = (byte) checksum(frame, 4, 8);
        return frame;
    }

    String expectedPreviousRawTimestamp(String rawTimestamp) {
        Calendar calendar = parseRawCalendar(rawTimestamp);
        if (calendar == null) return null;
        switch (family) {
            case HOUR:
                calendar.add(Calendar.HOUR_OF_DAY, -1);
                break;
            case DAY:
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, -1);
                break;
            case YEAR:
                calendar.add(Calendar.YEAR, -1);
                break;
            default:
                return null;
        }
        return formatRaw(calendar.getTime());
    }

    String expectedNextRawTimestamp(String rawTimestamp) {
        Calendar calendar = parseRawCalendar(rawTimestamp);
        if (calendar == null) return null;
        switch (family) {
            case HOUR:
                calendar.add(Calendar.HOUR_OF_DAY, 1);
                break;
            case DAY:
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, 1);
                break;
            case YEAR:
                calendar.add(Calendar.YEAR, 1);
                break;
            default:
                return null;
        }
        return formatRaw(calendar.getTime());
    }

    boolean isExpectedPreviousIdentity(
            String currentRawTimestamp,
            Long currentOnTimeSeconds,
            String candidateRawTimestamp,
            Long candidateOnTimeSeconds) {
        if (currentOnTimeSeconds == null || candidateOnTimeSeconds == null) return false;
        String expected = expectedPreviousRawTimestamp(currentRawTimestamp);
        if (expected == null || !expected.equals(candidateRawTimestamp)) return false;
        Long currentRawMs = parseRawMillis(currentRawTimestamp);
        Long candidateRawMs = parseRawMillis(candidateRawTimestamp);
        if (currentRawMs == null || candidateRawMs == null || currentRawMs <= candidateRawMs) {
            return false;
        }
        long rawDeltaSeconds = (currentRawMs - candidateRawMs) / 1000L;
        return currentOnTimeSeconds - candidateOnTimeSeconds == rawDeltaSeconds;
    }

    Boundary nextBoundary(String verifiedRawMeterTime) {
        Calendar raw = parseRawCalendar(verifiedRawMeterTime);
        if (raw == null) return Boundary.invalid(family, verifiedRawMeterTime);
        long rawAnchorMs = raw.getTimeInMillis();
        Calendar boundary = (Calendar) raw.clone();
        boundary.set(Calendar.SECOND, 0);
        boundary.set(Calendar.MILLISECOND, 0);

        switch (family) {
            case HOUR:
                boundary.set(Calendar.MINUTE, 0);
                boundary.add(Calendar.HOUR_OF_DAY, 1);
                break;
            case DAY:
                boundary.set(Calendar.HOUR_OF_DAY, 0);
                boundary.set(Calendar.MINUTE, 0);
                boundary.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case MONTH:
                boundary.set(Calendar.DAY_OF_MONTH, 1);
                boundary.set(Calendar.HOUR_OF_DAY, 0);
                boundary.set(Calendar.MINUTE, 0);
                boundary.add(Calendar.MONTH, 1);
                break;
            case YEAR:
                boundary.set(Calendar.MONTH, Calendar.JANUARY);
                boundary.set(Calendar.DAY_OF_MONTH, 1);
                boundary.set(Calendar.HOUR_OF_DAY, 0);
                boundary.set(Calendar.MINUTE, 0);
                boundary.add(Calendar.YEAR, 1);
                break;
            default:
                return Boundary.invalid(family, verifiedRawMeterTime);
        }

        long exactFromMinuteMarkMs = boundary.getTimeInMillis() - rawAnchorMs;
        if (exactFromMinuteMarkMs <= 0L) return Boundary.invalid(family, verifiedRawMeterTime);

        // Type-F logger evidence in the current product/research path is minute-granular. The
        // current minute may already be at second 59, so do not credit that unobserved remainder.
        long conservativeRemainingMs = Math.max(0L,
                exactFromMinuteMarkMs - RAW_MINUTE_UNCERTAINTY_MS);
        return new Boundary(
                family,
                verifiedRawMeterTime,
                formatRaw(boundary.getTime()),
                exactFromMinuteMarkMs,
                conservativeRemainingMs,
                true);
    }

    private static Calendar parseRawCalendar(String value) {
        Long millis = parseRawMillis(value);
        if (millis == null) return null;
        Calendar out = Calendar.getInstance(RAW_CALENDAR_ZONE, Locale.US);
        out.setLenient(false);
        out.setTimeInMillis(millis);
        return out;
    }

    private static Long parseRawMillis(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(RAW_CALENDAR_ZONE);
        try {
            Date parsed = format.parse(value.trim());
            return parsed == null ? null : parsed.getTime();
        } catch (ParseException error) {
            return null;
        }
    }

    private static String formatRaw(Date value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        format.setTimeZone(RAW_CALENDAR_ZONE);
        return format.format(value);
    }

    private static int checksum(byte[] bytes, int fromInclusive, int toExclusive) {
        int sum = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            sum = (sum + (bytes[i] & 0xFF)) & 0xFF;
        }
        return sum;
    }

    static final class Boundary {
        final ArchiveFamilyPeriod.Family family;
        final String verifiedRawMeterTime;
        final String nextRawBoundary;
        final long exactFromMinuteMarkMs;
        final long conservativeRemainingMs;
        final boolean valid;

        Boundary(
                ArchiveFamilyPeriod.Family family,
                String verifiedRawMeterTime,
                String nextRawBoundary,
                long exactFromMinuteMarkMs,
                long conservativeRemainingMs,
                boolean valid) {
            this.family = family;
            this.verifiedRawMeterTime = verifiedRawMeterTime;
            this.nextRawBoundary = nextRawBoundary;
            this.exactFromMinuteMarkMs = exactFromMinuteMarkMs;
            this.conservativeRemainingMs = conservativeRemainingMs;
            this.valid = valid;
        }

        static Boundary invalid(ArchiveFamilyPeriod.Family family, String rawMeterTime) {
            return new Boundary(family, rawMeterTime, null, -1L, -1L, false);
        }
    }
}
