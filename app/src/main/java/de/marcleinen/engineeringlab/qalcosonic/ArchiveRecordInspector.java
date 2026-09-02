package de.marcleinen.engineeringlab.qalcosonic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Privacy-safe DIF/DIFE/VIF/VIFE decoder for the isolated first archive response. */
final class ArchiveRecordInspector {
    private static final int MAX_EXTENSIONS = 10;

    private ArchiveRecordInspector() {}

    static Inspection inspectSelectedResponse(byte[] mailboxPayload) {
        if (mailboxPayload == null) return Inspection.failure("MAILBOX_PAYLOAD_MISSING", -1, 0);
        for (int start = 0; start + 8 < mailboxPayload.length; start++) {
            if (u8(mailboxPayload[start]) != 0x68) continue;
            int l = u8(mailboxPayload[start + 1]);
            if (l != u8(mailboxPayload[start + 2]) || start + l + 6 > mailboxPayload.length) continue;
            byte[] frame = new byte[l + 6];
            System.arraycopy(mailboxPayload, start, frame, 0, frame.length);
            if (validLongFrame(frame)) return inspectLongFrame(frame);
        }
        return Inspection.failure("MBUS_LONG_FRAME_NOT_FOUND", -1, 0);
    }

    static Inspection inspectLongFrame(byte[] frame) {
        if (!validLongFrame(frame)) return Inspection.failure("MBUS_LONG_FRAME_INVALID", -1, 0);
        int ci = u8(frame[6]);
        int l = u8(frame[1]);
        int recordBytes = (ci == 0x72 || ci == 0x76) && l >= 15 ? l - 15 : 0;
        if (ci != 0x72) {
            return Inspection.failure(String.format(Locale.US, "UNSUPPORTED_CI_0x%02X", ci), ci, recordBytes);
        }

        int p = 19;            // C,A,CI + 12-byte CI=72 fixed variable-data header.
        int end = 4 + l;       // checksum begins here.
        if (p > end) return Inspection.failure("CI72_VARIABLE_HEADER_INCOMPLETE", ci, recordBytes);

        List<Record> records = new ArrayList<>();
        boolean manufacturerRemainder = false;
        boolean moreTelegramsHint = false;
        int manufacturerBytes = 0;
        String error = null;

        while (p < end) {
            int dif = u8(frame[p++]);
            if (dif == 0x2F) continue;
            if (dif == 0x0F || dif == 0x1F) {
                manufacturerRemainder = true;
                moreTelegramsHint = dif == 0x1F;
                manufacturerBytes = end - p;
                break;
            }
            if ((dif & 0x0F) == 0x0F) {
                error = String.format(Locale.US, "UNSUPPORTED_SPECIAL_DIF_0x%02X", dif);
                break;
            }

            long storage = (dif >> 6) & 0x01;
            long tariff = 0;
            long subunit = 0;
            int function = (dif >> 4) & 0x03;
            List<Integer> dife = new ArrayList<>();
            int lastDif = dif;
            int ext = 0;
            while ((lastDif & 0x80) != 0) {
                if (ext >= MAX_EXTENSIONS) { error = "TOO_MANY_DIFE"; break; }
                if (p >= end) { error = "PREMATURE_END_IN_DIFE"; break; }
                int value = u8(frame[p++]);
                dife.add(value);
                storage |= ((long) (value & 0x0F)) << (1 + 4 * ext);
                tariff |= ((long) ((value >> 4) & 0x03)) << (2 * ext);
                subunit |= ((long) ((value >> 6) & 0x01)) << ext;
                lastDif = value;
                ext++;
            }
            if (error != null) break;
            if (p >= end) { error = "PREMATURE_END_BEFORE_VIF"; break; }

            int vif = u8(frame[p++]);
            List<Integer> vife = new ArrayList<>();
            boolean plainTextVif = (vif & 0x7F) == 0x7C;
            if (plainTextVif) {
                if (p >= end) { error = "PREMATURE_END_IN_PLAIN_TEXT_VIF"; break; }
                int textLength = u8(frame[p++]);
                if (p + textLength > end) { error = "PLAIN_TEXT_VIF_LENGTH_EXCEEDS_RECORD_AREA"; break; }
                p += textLength;
            } else {
                int last = vif;
                int count = 0;
                while ((last & 0x80) != 0) {
                    if (count++ >= MAX_EXTENSIONS) { error = "TOO_MANY_VIFE"; break; }
                    if (p >= end) { error = "PREMATURE_END_IN_VIFE"; break; }
                    last = u8(frame[p++]);
                    vife.add(last);
                }
                if (error != null) break;
            }

            DataSlice data = dataSlice(dif & 0x0F, frame, p, end);
            if (data.error != null) { error = data.error; break; }
            if (p + data.encodedLength > end) { error = "RECORD_DATA_EXCEEDS_RECORD_AREA"; break; }

            Meaning meaning = meaning(vif, vife, plainTextVif);
            String value = decodeValue(meaning, dif & 0x0F, frame, data);
            records.add(new Record(
                    records.size(), dif, dataType(dif & 0x0F), immutable(dife), storage,
                    functionName(function), tariff, subunit, vif, immutable(vife),
                    meaning.semantic, meaning.vifeSemantics, meaning.unit,
                    data.encodedLength, value, meaning.identifierLike));
            p += data.encodedLength;
        }

        return new Inspection(error == null, error, ci, recordBytes, immutableRecords(records),
                manufacturerRemainder, manufacturerBytes, moreTelegramsHint);
    }

    static List<String> privacySafeTraceLines(Inspection inspection) {
        List<String> lines = new ArrayList<>();
        if (inspection == null) {
            lines.add("ARCHIVE_RECORD_DECODE_COMPLETE=false");
            lines.add("ARCHIVE_RECORD_DECODE_ERROR=NO_INSPECTION");
            return lines;
        }
        lines.add("ARCHIVE_RECORD_DECODE_COMPLETE=" + inspection.parseComplete);
        if (inspection.errorCode != null) lines.add("ARCHIVE_RECORD_DECODE_ERROR=" + inspection.errorCode);
        lines.add("ARCHIVE_RECORD_COUNT=" + inspection.records.size());
        lines.add("ARCHIVE_RECORD_MANUFACTURER_SPECIFIC_REMAINDER=" + inspection.manufacturerSpecificRemainder);
        if (inspection.manufacturerSpecificRemainder) {
            lines.add("ARCHIVE_RECORD_MANUFACTURER_SPECIFIC_BYTES=" + inspection.manufacturerSpecificBytes);
        }
        lines.add("ARCHIVE_RECORD_MORE_TELEGRAMS_HINT=" + inspection.moreRecordsFollow);
        for (Record r : inspection.records) {
            lines.add(String.format(Locale.US,
                    "ARCHIVE_RECORD_%d=DIF=0x%02X;TYPE=%s;DIFE=%s;STORAGE=%d;FUNCTION=%s;TARIFF=%d;SUBUNIT=%d;VIF=0x%02X;VIFE=%s;SEMANTIC=%s;VIFE_SEMANTICS=%s;UNIT=%s;DATA_LENGTH=%d;VALUE=%s",
                    r.index, r.dif, r.dataType, hexList(r.dife), r.storageNumber, r.function,
                    r.tariff, r.subunit, r.vif, hexList(r.vife), r.semantic, r.vifeSemantics,
                    r.unit == null ? "NONE" : r.unit, r.dataLength,
                    r.value == null ? "NOT_DECODED" : r.value));
        }
        return Collections.unmodifiableList(lines);
    }

    private static DataSlice dataSlice(int field, byte[] data, int p, int end) {
        int fixed;
        switch (field) {
            case 0x00: case 0x08: fixed = 0; break;
            case 0x01: case 0x09: fixed = 1; break;
            case 0x02: case 0x0A: fixed = 2; break;
            case 0x03: case 0x0B: fixed = 3; break;
            case 0x04: case 0x05: case 0x0C: fixed = 4; break;
            case 0x06: case 0x0E: fixed = 6; break;
            case 0x07: fixed = 8; break;
            case 0x0D:
                if (p >= end) return DataSlice.error("PREMATURE_END_BEFORE_LVAR");
                int lvar = u8(data[p]);
                int valueLength;
                if (lvar <= 0xBF) valueLength = lvar;
                else if (lvar <= 0xCF) valueLength = lvar - 0xC0;
                else if (lvar <= 0xDF) valueLength = lvar - 0xD0;
                else if (lvar <= 0xEF) valueLength = lvar - 0xE0;
                else if (lvar <= 0xFA) valueLength = lvar - 0xF0;
                else return DataSlice.error(String.format(Locale.US, "RESERVED_LVAR_0x%02X", lvar));
                return new DataSlice(1 + valueLength, p + 1, valueLength, lvar, null);
            default: return DataSlice.error(String.format(Locale.US, "UNSUPPORTED_DATA_FIELD_0x%X", field));
        }
        return new DataSlice(fixed, p, fixed, -1, null);
    }

    private static Meaning meaning(int vif, List<Integer> vife, boolean plainTextVif) {
        if (plainTextVif) return Meaning.identifier("PLAIN_TEXT_VIF", "PLAIN_TEXT_REDACTED");
        int base = vif & 0x7F;

        // Real W1 evidence plus Axioma logger protocol: BB 6D / D9 6D encode the
        // Type-F date/time at which a flow/temperature extremum occurred. 0.7.9
        // treated these as orthogonal numeric VIFEs and therefore produced huge
        // impossible values. Keep this narrow and descriptor-based.
        if (!vife.isEmpty() && (vife.get(0) & 0x7F) == 0x6D) {
            if (base >= 0x38 && base <= 0x3F) {
                return Meaning.dateTime("VOLUME_FLOW_EXTREMUM_DATE_TIME")
                        .withVifeSemantics("QALCOSONIC_TYPE_F_EXTREMUM_DATE_TIME");
            }
            if (base >= 0x58 && base <= 0x5F) {
                return Meaning.dateTime("TEMPERATURE_EXTREMUM_DATE_TIME")
                        .withVifeSemantics("QALCOSONIC_TYPE_F_EXTREMUM_DATE_TIME");
            }
        }

        if (base == 0x7D) {
            if (vife.isEmpty()) return Meaning.none("FD_EXTENSION_MISSING_VIFE", "NONE");
            Meaning result = fdMeaning(vife.get(0) & 0x7F);
            return result.withVifeSemantics(join(result.vifeSemantics, vifeSemantics(vife, 1)));
        }
        if (base == 0x7B) {
            if (vife.isEmpty()) return Meaning.none("FB_EXTENSION_MISSING_VIFE", "NONE");
            int code = vife.get(0) & 0x7F;
            return Meaning.none(String.format(Locale.US, "FB_EXTENSION_0x%02X", code),
                    join(String.format(Locale.US, "FB_0x%02X", code), vifeSemantics(vife, 1)));
        }

        Meaning result = primaryMeaning(base);
        if (base == 0x13 && !vife.isEmpty()) {
            int first = vife.get(0) & 0x7F;
            if (first == 0x3B) result = result.withSemantic("VOLUME_POSITIVE");
            if (first == 0x3C) result = result.withSemantic("VOLUME_NEGATIVE");
        }
        return result.withVifeSemantics(join("NONE", vifeSemantics(vife, 0)));
    }

    private static Meaning primaryMeaning(int v) {
        if (v <= 0x07) return Meaning.numeric("ENERGY", "Wh", (v & 7) - 3, false);
        if (v <= 0x0F) return Meaning.numeric("ENERGY", "J", v & 7, false);
        if (v <= 0x17) return Meaning.numeric("VOLUME", "m3", (v & 7) - 6, false);
        if (v <= 0x1F) return Meaning.numeric("MASS", "kg", (v & 7) - 3, false);
        if (v <= 0x23) return Meaning.numeric("ON_TIME", duration(v & 3), 0, true);
        if (v <= 0x27) return Meaning.numeric("OPERATING_TIME", duration(v & 3), 0, true);
        if (v <= 0x2F) return Meaning.numeric("POWER", "W", (v & 7) - 3, false);
        if (v <= 0x37) return Meaning.numeric("POWER", "J/h", v & 7, false);
        if (v <= 0x3F) return Meaning.numeric("VOLUME_FLOW", "m3/h", (v & 7) - 6, false);
        if (v <= 0x47) return Meaning.numeric("VOLUME_FLOW", "m3/min", (v & 7) - 7, false);
        if (v <= 0x4F) return Meaning.numeric("VOLUME_FLOW", "m3/s", (v & 7) - 9, false);
        if (v <= 0x57) return Meaning.numeric("MASS_FLOW", "kg/h", (v & 7) - 3, false);
        if (v <= 0x5B) return Meaning.numeric("FLOW_TEMPERATURE", "degC", (v & 3) - 3, false);
        if (v <= 0x5F) return Meaning.numeric("RETURN_TEMPERATURE", "degC", (v & 3) - 3, false);
        if (v <= 0x63) return Meaning.numeric("TEMPERATURE_DIFFERENCE", "K", (v & 3) - 3, false);
        if (v <= 0x67) return Meaning.numeric("EXTERNAL_TEMPERATURE", "degC", (v & 3) - 3, false);
        if (v <= 0x6B) return Meaning.numeric("PRESSURE", "bar", (v & 3) - 3, false);
        if (v == 0x6C) return Meaning.date("TIME_POINT_DATE");
        if (v == 0x6D) return Meaning.dateTime("TIME_POINT_DATE_TIME");
        if (v == 0x6E) return Meaning.numeric("HCA_UNITS", "HCA", 0, true);
        if (v == 0x6F) return Meaning.none("RESERVED_PRIMARY_VIF", "RESERVED");
        if (v <= 0x73) return Meaning.numeric("AVERAGING_DURATION", duration(v & 3), 0, true);
        if (v <= 0x77) return Meaning.numeric("ACTUALITY_DURATION", duration(v & 3), 0, true);
        if (v == 0x78) return Meaning.identifier("FABRICATION_NUMBER", "IDENTIFIER");
        if (v == 0x79) return Meaning.identifier("ENHANCED_IDENTIFICATION", "IDENTIFIER");
        if (v == 0x7A) return Meaning.identifier("BUS_ADDRESS", "IDENTIFIER");
        if (v == 0x7F) return Meaning.none("MANUFACTURER_SPECIFIC_VIF", "MANUFACTURER_SPECIFIC");
        return Meaning.none(String.format(Locale.US, "PRIMARY_VIF_0x%02X", v), "UNKNOWN");
    }

    private static Meaning fdMeaning(int code) {
        if (code == 0x74) return Meaning.numeric("BATTERY_PERCENT_QALCOSONIC", "%", 0, true)
                .withVifeSemantics("FD_BATTERY_PERCENT_QALCOSONIC");
        switch (code) {
            case 0x08: return Meaning.numeric("ACCESS_NUMBER", null, 0, true).withVifeSemantics("FD_ACCESS_NUMBER");
            case 0x09: return Meaning.numeric("MEDIUM", null, 0, true).withVifeSemantics("FD_MEDIUM");
            case 0x0A: return Meaning.numeric("MANUFACTURER_CODE", null, 0, true).withVifeSemantics("FD_MANUFACTURER");
            case 0x0B: return Meaning.identifier("PARAMETER_SET_IDENTIFICATION", "FD_PARAMETER_SET_IDENTIFICATION");
            case 0x0C: return Meaning.numeric("MODEL_VERSION", null, 0, true).withVifeSemantics("FD_MODEL_VERSION");
            case 0x0D: return Meaning.numeric("HARDWARE_VERSION", null, 0, true).withVifeSemantics("FD_HARDWARE_VERSION");
            case 0x0E: return Meaning.numeric("FIRMWARE_VERSION", null, 0, true).withVifeSemantics("FD_FIRMWARE_VERSION");
            case 0x0F: return Meaning.numeric("SOFTWARE_VERSION", null, 0, true).withVifeSemantics("FD_SOFTWARE_VERSION");
            case 0x10: return Meaning.identifier("CUSTOMER_LOCATION", "FD_CUSTOMER_LOCATION");
            case 0x11: return Meaning.identifier("CUSTOMER", "FD_CUSTOMER");
            case 0x12: case 0x13: case 0x14: case 0x15: case 0x16:
                return Meaning.identifier("ACCESS_OR_PASSWORD", String.format(Locale.US, "FD_0x%02X_REDACTED", code));
            case 0x17: return Meaning.flags("ERROR_FLAGS", "FD_ERROR_FLAGS");
            case 0x18: return Meaning.flags("ERROR_MASK", "FD_ERROR_MASK");
            case 0x20: return Meaning.numeric("FIRST_STORAGE_NUMBER", null, 0, true).withVifeSemantics("FD_FIRST_STORAGE_NUMBER");
            case 0x21: return Meaning.numeric("LAST_STORAGE_NUMBER", null, 0, true).withVifeSemantics("FD_LAST_STORAGE_NUMBER");
            case 0x22: return Meaning.numeric("STORAGE_BLOCK_SIZE", null, 0, true).withVifeSemantics("FD_STORAGE_BLOCK_SIZE");
            case 0x24: return Meaning.numeric("STORAGE_INTERVAL", "s", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_SECONDS");
            case 0x25: return Meaning.numeric("STORAGE_INTERVAL", "min", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_MINUTES");
            case 0x26: return Meaning.numeric("STORAGE_INTERVAL", "h", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_HOURS");
            case 0x27: return Meaning.numeric("STORAGE_INTERVAL", "d", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_DAYS");
            case 0x28: return Meaning.numeric("STORAGE_INTERVAL", "month", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_MONTHS");
            case 0x29: return Meaning.numeric("STORAGE_INTERVAL", "year", 0, true).withVifeSemantics("FD_STORAGE_INTERVAL_YEARS");
            default: return Meaning.none(String.format(Locale.US, "FD_EXTENSION_0x%02X", code),
                    String.format(Locale.US, "FD_0x%02X", code));
        }
    }

    private static String decodeValue(Meaning m, int field, byte[] data, DataSlice slice) {
        if (m.identifierLike) return "REDACTED_IDENTIFIER_LIKE";
        if (m.kind == Kind.NONE) return null;
        if (m.kind == Kind.DATE) return slice.valueLength >= 2 ? decodeDate(data, slice.valueOffset) : null;
        if (m.kind == Kind.DATE_TIME) return slice.valueLength >= 4 ? decodeDateTime(data, slice.valueOffset) : null;
        if (m.kind == Kind.FLAGS) return hexValue(data, slice.valueOffset, slice.valueLength);
        if (field == 0x05 && slice.valueLength >= 4) {
            int bits = u8(data[slice.valueOffset]) | (u8(data[slice.valueOffset + 1]) << 8)
                    | (u8(data[slice.valueOffset + 2]) << 16) | (u8(data[slice.valueOffset + 3]) << 24);
            float value = Float.intBitsToFloat(bits);
            return Float.isFinite(value) ? appendUnit(BigDecimal.valueOf(value).scaleByPowerOfTen(m.scale), m.unit) : null;
        }
        Long number = integerValue(field, data, slice, m.unsigned);
        if (number == null) return null;
        return appendUnit(BigDecimal.valueOf(number).scaleByPowerOfTen(m.scale), m.unit);
    }

    private static Long integerValue(int field, byte[] data, DataSlice slice, boolean unsigned) {
        if (field >= 0x01 && field <= 0x07 && field != 0x05) {
            if (slice.valueLength == 8 && unsigned && (data[slice.valueOffset + 7] & 0x80) != 0) return null;
            long value = 0;
            for (int i = 0; i < slice.valueLength; i++) value |= ((long) u8(data[slice.valueOffset + i])) << (8 * i);
            if (!unsigned && slice.valueLength < 8 && slice.valueLength > 0) {
                int bits = slice.valueLength * 8;
                long sign = 1L << (bits - 1);
                if ((value & sign) != 0) value |= (-1L) << bits;
            }
            return value;
        }
        if ((field >= 0x09 && field <= 0x0C) || field == 0x0E) return bcd(data, slice.valueOffset, slice.valueLength, false);
        if (field == 0x0D && slice.lvar >= 0xC0 && slice.lvar <= 0xDF) {
            return bcd(data, slice.valueOffset, slice.valueLength, slice.lvar >= 0xD0);
        }
        return null;
    }

    private static Long bcd(byte[] data, int p, int length, boolean negative) {
        long value = 0, place = 1;
        for (int i = 0; i < length; i++) {
            int b = u8(data[p + i]), lo = b & 0x0F, hi = (b >> 4) & 0x0F;
            if (lo > 9 || hi > 9 || place > 100_000_000_000_000_000L) return null;
            value += lo * place; place *= 10;
            value += hi * place; place *= 10;
        }
        return negative ? -value : value;
    }

    private static String decodeDate(byte[] d, int p) {
        int db = u8(d[p]), mb = u8(d[p + 1]);
        int day = db & 0x1F, month = mb & 0x0F, year = ((db >> 5) | ((mb >> 1) & 0xF8)) + 2000;
        return day < 1 || day > 31 || month < 1 || month > 12 ? "INVALID_DATE"
                : String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    private static String decodeDateTime(byte[] d, int p) {
        int minute = u8(d[p]) & 0x3F, hour = u8(d[p + 1]) & 0x1F;
        int db = u8(d[p + 2]), mb = u8(d[p + 3]);
        int day = db & 0x1F, month = mb & 0x0F, year = ((db >> 5) | ((mb >> 1) & 0xF8)) + 2000;
        return minute > 59 || hour > 23 || day < 1 || day > 31 || month < 1 || month > 12
                ? "INVALID_DATE_TIME" : String.format(Locale.US, "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
    }

    private static List<String> vifeSemantics(List<Integer> vife, int start) {
        List<String> out = new ArrayList<>();
        for (int i = start; i < vife.size(); i++) out.add(orthogonal(vife.get(i) & 0x7F));
        return out;
    }

    private static String orthogonal(int code) {
        switch (code) {
            case 0x20: return "PER_SECOND"; case 0x21: return "PER_MINUTE"; case 0x22: return "PER_HOUR";
            case 0x23: return "PER_DAY"; case 0x24: return "PER_WEEK"; case 0x25: return "PER_MONTH";
            case 0x26: return "PER_YEAR"; case 0x27: return "PER_REVOLUTION_OR_MEASUREMENT";
            case 0x39: return "START_DATE_TIME_OF"; case 0x3A: return "UNCORRECTED_UNIT";
            case 0x3B: return "ACCUMULATION_POSITIVE_ONLY"; case 0x3C: return "ACCUMULATION_ABS_NEGATIVE_ONLY";
            case 0x7E: return "FUTURE_VALUE"; case 0x7F: return "FOLLOWING_VIFE_AND_DATA_MANUFACTURER_SPECIFIC";
            default: return String.format(Locale.US, "VIFE_0x%02X", code);
        }
    }

    private static String dataType(int field) {
        String[] names = {"NO_DATA","INT8","INT16","INT24","INT32","REAL32","INT48","INT64",
                "READOUT_SELECTION","BCD2","BCD4","BCD6","BCD8","VARIABLE_LENGTH","BCD12","SPECIAL_FUNCTION"};
        return field >= 0 && field < names.length ? names[field] : "UNKNOWN";
    }

    private static String functionName(int function) {
        return new String[]{"INSTANTANEOUS","MAXIMUM","MINIMUM","ERROR_STATE"}[function & 3];
    }

    private static String duration(int code) { return new String[]{"s","min","h","d"}[code & 3]; }

    private static String appendUnit(BigDecimal value, String unit) {
        String text = value.stripTrailingZeros().toPlainString();
        return unit == null ? text : text + " " + unit;
    }

    private static String hexValue(byte[] data, int p, int length) {
        if (length <= 0) return "0x0";
        StringBuilder out = new StringBuilder("0x");
        for (int i = length - 1; i >= 0; i--) out.append(String.format(Locale.US, "%02X", u8(data[p + i])));
        return out.toString();
    }

    private static String hexList(List<Integer> values) {
        if (values.isEmpty()) return "NONE";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(String.format(Locale.US, "0x%02X", values.get(i)));
        }
        return out.toString();
    }

    private static String join(String base, List<String> extra) {
        if (extra.isEmpty()) return base;
        return "NONE".equals(base) ? String.join(",", extra) : base + "," + String.join(",", extra);
    }

    private static boolean validLongFrame(byte[] f) {
        if (f == null || f.length < 9 || u8(f[0]) != 0x68 || u8(f[3]) != 0x68) return false;
        int l = u8(f[1]);
        if (l != u8(f[2]) || f.length != l + 6 || u8(f[f.length - 1]) != 0x16) return false;
        int sum = 0;
        for (int i = 4; i < f.length - 2; i++) sum = (sum + u8(f[i])) & 0xFF;
        return sum == u8(f[f.length - 2]);
    }

    private static int u8(byte b) { return b & 0xFF; }
    private static List<Integer> immutable(List<Integer> v) { return Collections.unmodifiableList(new ArrayList<>(v)); }
    private static List<Record> immutableRecords(List<Record> v) { return Collections.unmodifiableList(new ArrayList<>(v)); }

    enum Kind { NONE, NUMERIC, DATE, DATE_TIME, FLAGS }

    static final class Inspection {
        final boolean parseComplete;
        final String errorCode;
        final int ci;
        final int variableRecordBytes;
        final List<Record> records;
        final boolean manufacturerSpecificRemainder;
        final int manufacturerSpecificBytes;
        final boolean moreRecordsFollow;

        Inspection(boolean complete, String error, int ci, int bytes, List<Record> records,
                   boolean manufacturer, int manufacturerBytes, boolean more) {
            this.parseComplete = complete;
            this.errorCode = error;
            this.ci = ci;
            this.variableRecordBytes = bytes;
            this.records = records;
            this.manufacturerSpecificRemainder = manufacturer;
            this.manufacturerSpecificBytes = manufacturerBytes;
            this.moreRecordsFollow = more;
        }

        static Inspection failure(String error, int ci, int bytes) {
            return new Inspection(false, error, ci, bytes, Collections.emptyList(), false, 0, false);
        }
    }

    static final class Record {
        final int index, dif, vif, dataLength;
        final String dataType, function, semantic, vifeSemantics, unit, value;
        final List<Integer> dife, vife;
        final long storageNumber, tariff, subunit;
        final boolean identifierLike;

        Record(int index, int dif, String type, List<Integer> dife, long storage, String function, long tariff,
               long subunit, int vif, List<Integer> vife, String semantic, String vifeSemantics, String unit,
               int dataLength, String value, boolean identifierLike) {
            this.index = index;
            this.dif = dif;
            this.dataType = type;
            this.dife = dife;
            this.storageNumber = storage;
            this.function = function;
            this.tariff = tariff;
            this.subunit = subunit;
            this.vif = vif;
            this.vife = vife;
            this.semantic = semantic;
            this.vifeSemantics = vifeSemantics;
            this.unit = unit;
            this.dataLength = dataLength;
            this.value = value;
            this.identifierLike = identifierLike;
        }
    }

    private static final class DataSlice {
        final int encodedLength, valueOffset, valueLength, lvar;
        final String error;

        DataSlice(int encoded, int offset, int length, int lvar, String error) {
            this.encodedLength = encoded;
            this.valueOffset = offset;
            this.valueLength = length;
            this.lvar = lvar;
            this.error = error;
        }

        static DataSlice error(String error) { return new DataSlice(0, 0, 0, -1, error); }
    }

    private static final class Meaning {
        final String semantic, unit, vifeSemantics;
        final int scale;
        final Kind kind;
        final boolean identifierLike, unsigned;

        Meaning(String semantic, String unit, int scale, Kind kind, boolean identifier, boolean unsigned, String vife) {
            this.semantic = semantic;
            this.unit = unit;
            this.scale = scale;
            this.kind = kind;
            this.identifierLike = identifier;
            this.unsigned = unsigned;
            this.vifeSemantics = vife;
        }

        static Meaning numeric(String s, String u, int scale, boolean unsigned) {
            return new Meaning(s, u, scale, Kind.NUMERIC, false, unsigned, "NONE");
        }
        static Meaning date(String s) { return new Meaning(s, null, 0, Kind.DATE, false, false, "NONE"); }
        static Meaning dateTime(String s) { return new Meaning(s, null, 0, Kind.DATE_TIME, false, false, "NONE"); }
        static Meaning flags(String s, String v) { return new Meaning(s, null, 0, Kind.FLAGS, false, true, v); }
        static Meaning identifier(String s, String v) { return new Meaning(s, null, 0, Kind.NONE, true, false, v); }
        static Meaning none(String s, String v) { return new Meaning(s, null, 0, Kind.NONE, false, false, v); }
        Meaning withSemantic(String s) { return new Meaning(s, unit, scale, kind, identifierLike, unsigned, vifeSemantics); }
        Meaning withVifeSemantics(String v) { return new Meaning(semantic, unit, scale, kind, identifierLike, unsigned, v); }
    }
}
