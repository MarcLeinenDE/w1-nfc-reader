package de.marcleinen.engineeringlab.qalcosonic;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Maps validated W1 warning bits to localized product labels without guessing unknown bits. */
final class MeterStatusPresentation {
    private static final long KNOWN_MASK = 0x1E181E1EL;

    static final class Historical {
        final String raw;
        final List<Integer> knownLabelResources;
        final boolean hasUnknownBits;

        Historical(String raw, List<Integer> knownLabelResources, boolean hasUnknownBits) {
            this.raw = raw;
            this.knownLabelResources = Collections.unmodifiableList(knownLabelResources);
            this.hasUnknownBits = hasUnknownBits;
        }

        boolean hasAnyStatus() {
            return !knownLabelResources.isEmpty() || hasUnknownBits;
        }

        String summary(Context context) {
            List<String> labels = new ArrayList<>();
            for (int res : knownLabelResources) labels.add(context.getString(res));
            if (hasUnknownBits || labels.isEmpty()) labels.add(context.getString(R.string.m3_historical_unknown));
            return android.text.TextUtils.join(", ", labels);
        }
    }

    private MeterStatusPresentation() {}

    static int label(MbusParser.MeterAlarm alarm) {
        if (alarm == null) return R.string.m3_historical_unknown;
        switch (alarm) {
            case RECONFIGURATION_WARNING: return R.string.m3_alarm_reconfiguration;
            case NO_CONSUMPTION: return R.string.m3_alarm_no_consumption;
            case METER_HOUSING_DAMAGE: return R.string.m3_alarm_housing_damage;
            case CALCULATOR_HARDWARE_FAILURE: return R.string.m3_alarm_calculator_hardware;
            case LEAKAGE: return R.string.m3_alarm_leakage;
            case BURST: return R.string.m3_alarm_burst;
            case OPTICAL_COMMUNICATION: return R.string.m3_alarm_optical;
            case LOW_BATTERY: return R.string.m3_alarm_low_battery;
            case SOFTWARE_FAILURE: return R.string.m3_alarm_software;
            case HARDWARE_FAILURE: return R.string.m3_alarm_hardware;
            case NO_SIGNAL: return R.string.m3_alarm_no_signal;
            case REVERSE_FLOW: return R.string.m3_alarm_reverse_flow;
            case FLOW_RATE_ALERT: return R.string.m3_alarm_flow_rate;
            case FREEZE_ALERT: return R.string.m3_alarm_freeze;
            default: return R.string.m3_historical_unknown;
        }
    }

    static String localizedAlarmCodes(Context context, String storedCodes) {
        if (storedCodes == null || storedCodes.trim().isEmpty()) return "";
        List<String> labels = new ArrayList<>();
        for (String token : storedCodes.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) continue;
            try {
                labels.add(context.getString(label(MbusParser.MeterAlarm.valueOf(name))));
            } catch (IllegalArgumentException ignored) {
                labels.add(context.getString(R.string.m3_historical_unknown));
            }
        }
        return android.text.TextUtils.join(", ", labels);
    }

    static Historical historical(String rawValue) {
        Long raw = parseRaw(rawValue);
        if (raw == null || raw == 0L) return new Historical(normalizeRaw(rawValue, 0L), new ArrayList<>(), false);
        List<Integer> labels = new ArrayList<>();
        add(labels, raw, 0x00000002L, R.string.m3_alarm_reconfiguration);
        add(labels, raw, 0x00000004L, R.string.m3_alarm_no_consumption);
        add(labels, raw, 0x00000008L, R.string.m3_alarm_housing_damage);
        add(labels, raw, 0x00000010L, R.string.m3_alarm_calculator_hardware);
        add(labels, raw, 0x00000200L, R.string.m3_alarm_leakage);
        add(labels, raw, 0x00000400L, R.string.m3_alarm_burst);
        add(labels, raw, 0x00000800L, R.string.m3_alarm_optical);
        add(labels, raw, 0x00001000L, R.string.m3_alarm_low_battery);
        add(labels, raw, 0x00080000L, R.string.m3_alarm_software);
        add(labels, raw, 0x00100000L, R.string.m3_alarm_hardware);
        add(labels, raw, 0x02000000L, R.string.m3_alarm_no_signal);
        add(labels, raw, 0x04000000L, R.string.m3_alarm_reverse_flow);
        add(labels, raw, 0x08000000L, R.string.m3_alarm_flow_rate);
        add(labels, raw, 0x10000000L, R.string.m3_alarm_freeze);
        return new Historical(normalizeRaw(rawValue, raw), labels, (raw & ~KNOWN_MASK) != 0L);
    }

    private static void add(List<Integer> out, long raw, long mask, int label) {
        if ((raw & mask) != 0L) out.add(label);
    }

    private static Long parseRaw(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.US).replace("_", "").replace(" ", "");
        if (v.isEmpty()) return null;
        try {
            if (v.startsWith("0x")) return Long.parseUnsignedLong(v.substring(2), 16) & 0xFFFFFFFFL;
            if (v.matches("[0-9a-f]{8}")) return Long.parseUnsignedLong(v, 16) & 0xFFFFFFFFL;
            return Long.parseLong(v) & 0xFFFFFFFFL;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeRaw(String original, long fallback) {
        Long parsed = parseRaw(original);
        long value = parsed == null ? fallback : parsed;
        return String.format(Locale.US, "0x%08X", value & 0xFFFFFFFFL);
    }
}
