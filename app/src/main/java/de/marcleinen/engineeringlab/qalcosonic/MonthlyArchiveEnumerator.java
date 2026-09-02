package de.marcleinen.engineeringlab.qalcosonic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure state machine for production Monthly archive enumeration.
 *
 * <p>No NFC/M-Bus I/O lives here. A transport adapter asks {@link Session#nextControl()} and feeds
 * exactly one observation back through {@link Session#accept(Candidate)}. This keeps the physically
 * validated protocol decisions testable without touching the protected readers.</p>
 */
final class MonthlyArchiveEnumerator {
    static final int VENDOR_HARD_CAP_CANDIDATE = 36;
    static final String CONTROL_FCB1_7B = "7B";
    static final String CONTROL_FCB0_5B = "5B";
    static final byte[] W1_TERMINAL_E5_7D = {(byte) 0xE5, 0x7D};

    enum CandidateKind {
        PERIOD,
        STANDARD_E5_NO_DATA,
        W1_E5_7D_TERMINAL,
        NO_HOST_RESPONSE,
        IO_ERROR,
        OTHER_RESPONSE
    }

    enum StopReason {
        NONE,
        TERMINAL_STANDARD_E5_NO_DATA,
        TERMINAL_W1_E5_7D,
        HARD_CAP_REACHED,
        NO_HOST_RESPONSE,
        IO_ERROR,
        OTHER_RESPONSE,
        FIRST_PERIOD_INVALID,
        STRUCTURE_CHANGED,
        NON_MONTHLY_PROGRESSION,
        DUPLICATE_PERIOD
    }

    static final class Candidate {
        final CandidateKind kind;
        final MonthlyArchivePeriod period;
        final String diagnostic;

        private Candidate(CandidateKind kind, MonthlyArchivePeriod period, String diagnostic) {
            this.kind = kind;
            this.period = period;
            this.diagnostic = diagnostic;
        }

        static Candidate period(MonthlyArchivePeriod period) {
            return new Candidate(CandidateKind.PERIOD, period, null);
        }
        static Candidate standardE5() {
            return new Candidate(CandidateKind.STANDARD_E5_NO_DATA, null, "E5");
        }
        static Candidate w1E57d() {
            return new Candidate(CandidateKind.W1_E5_7D_TERMINAL, null, "E5 7D");
        }
        static Candidate noHost() {
            return new Candidate(CandidateKind.NO_HOST_RESPONSE, null, null);
        }
        static Candidate ioError(String code) {
            return new Candidate(CandidateKind.IO_ERROR, null, code);
        }
        static Candidate other(String diagnostic) {
            return new Candidate(CandidateKind.OTHER_RESPONSE, null, diagnostic);
        }
    }

    static Candidate classifyShortResponse(byte[] payload) {
        if (payload == null || payload.length == 0) return Candidate.noHost();
        if (payload.length == 1 && (payload[0] & 0xFF) == 0xE5) return Candidate.standardE5();
        if (payload.length == 2 && (payload[0] & 0xFF) == 0xE5 && (payload[1] & 0xFF) == 0x7D) {
            return Candidate.w1E57d();
        }
        return Candidate.other(hex(payload));
    }

    static final class Session {
        private final int hardCap;
        private final List<MonthlyArchivePeriod> accepted = new ArrayList<>();
        private final Set<String> timestamps = new HashSet<>();
        private String anchorFingerprint;
        private StopReason stopReason = StopReason.NONE;
        private String diagnostic;
        private int selectedRequests;

        Session(int hardCap) {
            if (hardCap < 1 || hardCap > VENDOR_HARD_CAP_CANDIDATE) {
                throw new IllegalArgumentException("hardCap must be 1.." + VENDOR_HARD_CAP_CANDIDATE);
            }
            this.hardCap = hardCap;
        }

        boolean stopped() { return stopReason != StopReason.NONE; }
        StopReason stopReason() { return stopReason; }
        String diagnostic() { return diagnostic; }
        int selectedRequestsAttempted() { return selectedRequests; }
        List<MonthlyArchivePeriod> acceptedPeriods() {
            return Collections.unmodifiableList(new ArrayList<>(accepted));
        }

        String nextControl() {
            if (stopped()) return null;
            if (accepted.size() >= hardCap) {
                stopReason = StopReason.HARD_CAP_REACHED;
                return null;
            }
            return (selectedRequests % 2 == 0) ? CONTROL_FCB1_7B : CONTROL_FCB0_5B;
        }

        void accept(Candidate candidate) {
            if (stopped()) throw new IllegalStateException("session already stopped: " + stopReason);
            if (accepted.size() >= hardCap) {
                stopReason = StopReason.HARD_CAP_REACHED;
                return;
            }
            selectedRequests++;
            if (candidate == null) {
                stopReason = StopReason.OTHER_RESPONSE;
                diagnostic = "candidate missing";
                return;
            }
            switch (candidate.kind) {
                case STANDARD_E5_NO_DATA:
                    stopReason = StopReason.TERMINAL_STANDARD_E5_NO_DATA;
                    diagnostic = candidate.diagnostic;
                    return;
                case W1_E5_7D_TERMINAL:
                    stopReason = StopReason.TERMINAL_W1_E5_7D;
                    diagnostic = candidate.diagnostic;
                    return;
                case NO_HOST_RESPONSE:
                    stopReason = StopReason.NO_HOST_RESPONSE;
                    return;
                case IO_ERROR:
                    stopReason = StopReason.IO_ERROR;
                    diagnostic = candidate.diagnostic;
                    return;
                case OTHER_RESPONSE:
                    stopReason = StopReason.OTHER_RESPONSE;
                    diagnostic = candidate.diagnostic;
                    return;
                case PERIOD:
                    acceptPeriod(candidate.period);
                    return;
                default:
                    throw new IllegalStateException("Unhandled kind " + candidate.kind);
            }
        }

        private void acceptPeriod(MonthlyArchivePeriod period) {
            if (period == null || period.loggerTimestamp == null || period.structuralFingerprint == null) {
                stopReason = accepted.isEmpty() ? StopReason.FIRST_PERIOD_INVALID : StopReason.OTHER_RESPONSE;
                diagnostic = "period missing required fields";
                return;
            }
            if (timestamps.contains(period.loggerTimestamp)) {
                stopReason = StopReason.DUPLICATE_PERIOD;
                diagnostic = period.loggerTimestamp;
                return;
            }
            if (accepted.isEmpty()) {
                anchorFingerprint = period.structuralFingerprint;
            } else {
                if (!anchorFingerprint.equals(period.structuralFingerprint)) {
                    stopReason = StopReason.STRUCTURE_CHANGED;
                    diagnostic = period.structuralFingerprint;
                    return;
                }
                MonthlyArchivePeriod previous = accepted.get(accepted.size() - 1);
                if (!isExactlyPreviousCalendarMonth(previous.loggerTimestamp, period.loggerTimestamp)) {
                    stopReason = StopReason.NON_MONTHLY_PROGRESSION;
                    diagnostic = previous.loggerTimestamp + " -> " + period.loggerTimestamp;
                    return;
                }
            }
            timestamps.add(period.loggerTimestamp);
            accepted.add(period);
            if (accepted.size() >= hardCap) stopReason = StopReason.HARD_CAP_REACHED;
        }

        Result result() {
            return new Result(acceptedPeriods(), stopReason, diagnostic, selectedRequests, hardCap);
        }
    }

    static final class Result {
        final List<MonthlyArchivePeriod> periods;
        final StopReason stopReason;
        final String diagnostic;
        final int selectedRequestsAttempted;
        final int hardCap;

        Result(List<MonthlyArchivePeriod> periods, StopReason stopReason, String diagnostic,
               int selectedRequestsAttempted, int hardCap) {
            this.periods = periods;
            this.stopReason = stopReason;
            this.diagnostic = diagnostic;
            this.selectedRequestsAttempted = selectedRequestsAttempted;
            this.hardCap = hardCap;
        }

        boolean terminalConfirmed() {
            return stopReason == StopReason.TERMINAL_W1_E5_7D
                    || stopReason == StopReason.TERMINAL_STANDARD_E5_NO_DATA;
        }

        boolean partialSuccess() {
            return !periods.isEmpty() && !terminalConfirmed() && stopReason != StopReason.HARD_CAP_REACHED;
        }
    }

    static boolean isExactlyPreviousCalendarMonth(String previous, String next) {
        MonthStamp p = MonthStamp.parse(previous);
        MonthStamp n = MonthStamp.parse(next);
        if (p == null || n == null || !p.suffix.equals(n.suffix)) return false;
        int expectedYear = p.year;
        int expectedMonth = p.month - 1;
        if (expectedMonth == 0) {
            expectedMonth = 12;
            expectedYear--;
        }
        return n.year == expectedYear && n.month == expectedMonth;
    }

    static String previousCalendarMonth(String timestamp) {
        MonthStamp p = MonthStamp.parse(timestamp);
        if (p == null) return null;
        int year = p.year;
        int month = p.month - 1;
        if (month == 0) {
            month = 12;
            year--;
        }
        return String.format(Locale.US, "%04d-%02d%s", year, month, p.suffix);
    }

    private static final class MonthStamp {
        final int year;
        final int month;
        final String suffix;

        MonthStamp(int year, int month, String suffix) {
            this.year = year;
            this.month = month;
            this.suffix = suffix;
        }

        static MonthStamp parse(String value) {
            if (value == null || value.length() != 16) return null;
            if (value.charAt(4) != '-' || value.charAt(7) != '-' || value.charAt(10) != ' '
                    || value.charAt(13) != ':') return null;
            try {
                int year = Integer.parseInt(value.substring(0, 4));
                int month = Integer.parseInt(value.substring(5, 7));
                int day = Integer.parseInt(value.substring(8, 10));
                int hour = Integer.parseInt(value.substring(11, 13));
                int minute = Integer.parseInt(value.substring(14, 16));
                if (month < 1 || month > 12 || day < 1 || day > 31
                        || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
                return new MonthStamp(year, month, value.substring(7));
            } catch (RuntimeException error) {
                return null;
            }
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) out.append(' ');
            out.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return out.toString();
    }
}
