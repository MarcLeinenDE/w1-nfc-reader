/*
 * W1 NFC Reader
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This parser's Qalcosonic M-Bus frame handling, field mappings and decoding behavior were
 * informed by and adapted/translated from dbmaxpayne/esphome_qalcosonicnfc,
 * Copyright (c) 2025 Mark Hermann, licensed LGPL-2.1-or-later.
 *
 * Adapted and extended for the Android application in 2026 by Marc Leinen.
 * Distribution under GPL-3.0-or-later uses the GPL conversion option in LGPL-2.1 section 3.
 * See THIRD_PARTY_NOTICES.md for the upstream revision and full provenance record.
 */
package de.marcleinen.engineeringlab.qalcosonic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Parser for the variable-data M-Bus response used by Qalcosonic W1. */
final class MbusParser {
    private MbusParser() {}

    static MeterData parse(byte[] input) throws IOException {
        if (input == null || input.length < 10) {
            throw new IOException("MBUS_FRAME_TOO_SHORT");
        }

        int start = findLongFrame(input);
        if (start < 0) {
            throw new IOException("MBUS_LONG_FRAME_NOT_FOUND");
        }

        int l = u8(input[start + 1]);
        int totalLength = 4 + l + 2;
        if (start + totalLength > input.length) {
            throw new IOException("MBUS_FRAME_INCOMPLETE:L=" + l + ";AVAILABLE=" + (input.length - start));
        }

        int checksum = 0;
        for (int i = start + 4; i < start + 4 + l; i++) {
            checksum = (checksum + u8(input[i])) & 0xFF;
        }
        int receivedChecksum = u8(input[start + 4 + l]);
        if (checksum != receivedChecksum) {
            throw new IOException(String.format(Locale.US,
                    "MBUS_CHECKSUM_MISMATCH:CALCULATED=%02X;RECEIVED=%02X", checksum, receivedChecksum));
        }
        if (u8(input[start + 4 + l + 1]) != 0x16) {
            throw new IOException("MBUS_STOP_BYTE_MISSING");
        }

        MeterData out = new MeterData();
        out.rawFrame = slice(input, start, start + totalLength);
        out.ciField = u8(input[start + 6]);

        if (start + 10 < input.length) {
            out.meterId = decodeHeaderBcdId(input, start + 7);
        }

        int payload = start + 7;
        if (out.ciField == 0x72 || out.ciField == 0x7A) {
            if (start + 18 >= input.length) {
                throw new IOException("MBUS_VARIABLE_HEADER_INCOMPLETE");
            }
            int manufacturerCode = u8(input[start + 11]) | (u8(input[start + 12]) << 8);
            out.manufacturer = manufacturer(manufacturerCode);
            out.meterVersion = u8(input[start + 13]);
            payload += 12;
        }

        int end = start + 4 + l;
        int p = payload;
        while (p < end) {
            int dif = u8(input[p++]);

            if (dif == 0x2F) {
                continue;
            }
            if (dif == 0x0F || dif == 0x1F) {
                break;
            }

            int difExtension = dif;
            while ((difExtension & 0x80) != 0) {
                if (p >= end) return out;
                difExtension = u8(input[p++]);
            }

            if (p >= end) break;
            int vif = u8(input[p++]);
            int vife = 0;
            int lastVifByte = vif;
            if ((vif & 0x80) != 0) {
                if (p >= end) break;
                vife = u8(input[p++]);
                lastVifByte = vife;
            }
            while ((lastVifByte & 0x80) != 0) {
                if (p >= end) break;
                lastVifByte = u8(input[p++]);
            }

            int dataSize = dataSize(dif, input, p, end);
            if (dataSize < 0 || p + dataSize > end) {
                break;
            }

            int key = (vif << 8) | vife;
            switch (key) {
                case 0x1300:
                    if (dataSize >= 4) out.waterUsageM3 = leInt32(input, p) / 1000.0;
                    break;
                case 0x933B:
                    if (dataSize >= 4) out.positiveUsageM3 = leInt32(input, p) / 1000.0;
                    break;
                case 0x933C:
                    if (dataSize >= 4) out.negativeUsageM3 = leInt32(input, p) / 1000.0;
                    break;
                case 0x3B00:
                    if (dataSize >= 2) out.flowM3h = leInt16(input, p) / 1000.0;
                    break;
                case 0x5900:
                    if (dataSize >= 2) out.waterTemperatureC = leInt16(input, p) / 100.0;
                    break;
                case 0x6600:
                    if (dataSize >= 2) out.externalTemperatureC = leInt16(input, p) / 10.0;
                    break;
                case 0x6D00:
                    if (dataSize >= 4) out.timepoint = decodeTimepoint(input, p);
                    break;
                case 0xFD74:
                    if (dataSize >= 1) out.batteryPercent = u8(input[p]);
                    break;
                case 0x7800:
                    if ((dif & 0x0F) == 0x0C && dataSize >= 4) {
                        out.serialNumber = decodeBcd8(input, p);
                    }
                    break;
                case 0xFD17:
                    if (dataSize >= 4) decodeErrors(input, p, out);
                    break;
                case 0x2400:
                    if (dataSize >= 4) out.operatingTimeSeconds = leUInt32(input, p);
                    break;
                case 0x2000:
                    if (dataSize >= 4) out.onTimeSeconds = leUInt32(input, p);
                    break;
                default:
                    break;
            }

            p += dataSize;
        }

        return out;
    }

    private static int findLongFrame(byte[] data) {
        for (int i = 0; i <= data.length - 6; i++) {
            if (u8(data[i]) == 0x68 && u8(data[i + 3]) == 0x68 && data[i + 1] == data[i + 2]) {
                return i;
            }
        }
        return -1;
    }

    private static int dataSize(int dif, byte[] data, int p, int end) {
        switch (dif & 0x0F) {
            case 0x00: return 0;
            case 0x01: return 1;
            case 0x02: return 2;
            case 0x03: return 3;
            case 0x04: return 4;
            case 0x05: return 4;
            case 0x06: return 6;
            case 0x07: return 8;
            case 0x08: return 0;
            case 0x09: return 1;
            case 0x0A: return 2;
            case 0x0B: return 3;
            case 0x0C: return 4;
            case 0x0D: return p < end ? u8(data[p]) + 1 : -1;
            case 0x0E: return 6;
            case 0x0F: return 0;
            default: return -1;
        }
    }

    private static String decodeHeaderBcdId(byte[] data, int p) {
        if (p + 3 >= data.length) return null;
        long value = ((u8(data[p + 3]) >> 4) & 0x0F) * 10_000_000L
                + (u8(data[p + 3]) & 0x0F) * 1_000_000L
                + ((u8(data[p + 2]) >> 4) & 0x0F) * 100_000L
                + (u8(data[p + 2]) & 0x0F) * 10_000L
                + ((u8(data[p + 1]) >> 4) & 0x0F) * 1_000L
                + (u8(data[p + 1]) & 0x0F) * 100L
                + ((u8(data[p]) >> 4) & 0x0F) * 10L
                + (u8(data[p]) & 0x0F);
        return String.format(Locale.US, "%08d", value);
    }

    private static String decodeBcd8(byte[] data, int p) {
        long value = ((u8(data[p + 3]) >> 4) & 0x0F) * 10_000_000L
                + (u8(data[p + 3]) & 0x0F) * 1_000_000L
                + ((u8(data[p + 2]) >> 4) & 0x0F) * 100_000L
                + (u8(data[p + 2]) & 0x0F) * 10_000L
                + ((u8(data[p + 1]) >> 4) & 0x0F) * 1_000L
                + (u8(data[p + 1]) & 0x0F) * 100L
                + ((u8(data[p]) >> 4) & 0x0F) * 10L
                + (u8(data[p]) & 0x0F);
        return String.format(Locale.US, "%08d", value);
    }

    private static String manufacturer(int code) {
        char c1 = (char) (((code & 0x7C00) >> 10) + 64);
        char c2 = (char) (((code & 0x03E0) >> 5) + 64);
        char c3 = (char) ((code & 0x001F) + 64);
        return new String(new char[]{c1, c2, c3});
    }

    private static String decodeTimepoint(byte[] data, int p) {
        int minute = u8(data[p]) & 0x3F;
        int hour = u8(data[p + 1]) & 0x1F;
        int day = u8(data[p + 2]) & 0x1F;
        int month = u8(data[p + 3]) & 0x0F;
        int year = ((u8(data[p + 2]) >> 5) | ((u8(data[p + 3]) >> 1) & 0xF8)) + 2000;
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
    }

    private static void decodeErrors(byte[] data, int p, MeterData out) {
        int b0 = u8(data[p]);
        int b1 = u8(data[p + 1]);
        int b2 = u8(data[p + 2]);
        int b3 = u8(data[p + 3]);
        out.errorFlagsRaw = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3);

        addIf(out.errors, (b0 & (1 << 1)) != 0, MeterAlarm.RECONFIGURATION_WARNING);
        addIf(out.errors, (b0 & (1 << 2)) != 0, MeterAlarm.NO_CONSUMPTION);
        addIf(out.errors, (b0 & (1 << 3)) != 0, MeterAlarm.METER_HOUSING_DAMAGE);
        addIf(out.errors, (b0 & (1 << 4)) != 0, MeterAlarm.CALCULATOR_HARDWARE_FAILURE);
        addIf(out.errors, (b1 & (1 << 1)) != 0, MeterAlarm.LEAKAGE);
        addIf(out.errors, (b1 & (1 << 2)) != 0, MeterAlarm.BURST);
        addIf(out.errors, (b1 & (1 << 3)) != 0, MeterAlarm.OPTICAL_COMMUNICATION);
        addIf(out.errors, (b1 & (1 << 4)) != 0, MeterAlarm.LOW_BATTERY);
        addIf(out.errors, (b2 & (1 << 3)) != 0, MeterAlarm.SOFTWARE_FAILURE);
        addIf(out.errors, (b2 & (1 << 4)) != 0, MeterAlarm.HARDWARE_FAILURE);
        addIf(out.errors, (b3 & (1 << 1)) != 0, MeterAlarm.NO_SIGNAL);
        addIf(out.errors, (b3 & (1 << 2)) != 0, MeterAlarm.REVERSE_FLOW);
        addIf(out.errors, (b3 & (1 << 3)) != 0, MeterAlarm.FLOW_RATE_ALERT);
        addIf(out.errors, (b3 & (1 << 4)) != 0, MeterAlarm.FREEZE_ALERT);
    }

    private static void addIf(List<MeterAlarm> list, boolean condition, MeterAlarm value) {
        if (condition) list.add(value);
    }

    private static int leInt16(byte[] data, int p) {
        return (short) (u8(data[p]) | (u8(data[p + 1]) << 8));
    }

    private static int leInt32(byte[] data, int p) {
        return u8(data[p])
                | (u8(data[p + 1]) << 8)
                | (u8(data[p + 2]) << 16)
                | (u8(data[p + 3]) << 24);
    }

    private static long leUInt32(byte[] data, int p) {
        return ((long) u8(data[p]))
                | ((long) u8(data[p + 1]) << 8)
                | ((long) u8(data[p + 2]) << 16)
                | ((long) u8(data[p + 3]) << 24);
    }

    private static int u8(byte value) {
        return value & 0xFF;
    }

    private static byte[] slice(byte[] input, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(input, from, out, 0, out.length);
        return out;
    }

    enum MeterAlarm {
        RECONFIGURATION_WARNING,
        NO_CONSUMPTION,
        METER_HOUSING_DAMAGE,
        CALCULATOR_HARDWARE_FAILURE,
        LEAKAGE,
        BURST,
        OPTICAL_COMMUNICATION,
        LOW_BATTERY,
        SOFTWARE_FAILURE,
        HARDWARE_FAILURE,
        NO_SIGNAL,
        REVERSE_FLOW,
        FLOW_RATE_ALERT,
        FREEZE_ALERT
    }

    static final class MeterData {
        String meterId;
        String serialNumber;
        String manufacturer;
        Integer meterVersion;
        Integer ciField;
        Double waterUsageM3;
        Double positiveUsageM3;
        Double negativeUsageM3;
        Double flowM3h;
        Double waterTemperatureC;
        Double externalTemperatureC;
        Integer batteryPercent;
        Long operatingTimeSeconds;
        Long onTimeSeconds;
        String timepoint;
        String errorFlagsRaw;
        final List<MeterAlarm> errors = new ArrayList<>();
        byte[] rawFrame;

        List<MeterAlarm> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }
}
