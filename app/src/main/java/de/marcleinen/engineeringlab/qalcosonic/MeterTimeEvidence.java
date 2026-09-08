package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Arrays;
import java.util.Locale;

/**
 * Lossless meter-time evidence extractor for Live/default and archive CI=0x72 M-Bus frames.
 *
 * <p>This is intentionally separate from the protected {@link MbusParser}. It preserves the raw
 * Type-F bytes and SU/IV flags needed by the v2 safety/time model without changing established
 * normal value decoding.</p>
 */
final class MeterTimeEvidence {
    private static final int MAX_EXTENSIONS = 10;

    final boolean frameValid;
    final boolean typeFPresent;
    final String rawTypeFHex;
    final String decodedRawWallClock;
    final boolean invalidTime;
    final boolean summerTime;
    final boolean reservedMinuteBit6Set;
    final boolean reservedHourBitsSet;
    final Long onTimeSeconds;
    final String error;

    private MeterTimeEvidence(
            boolean frameValid,
            boolean typeFPresent,
            String rawTypeFHex,
            String decodedRawWallClock,
            boolean invalidTime,
            boolean summerTime,
            boolean reservedMinuteBit6Set,
            boolean reservedHourBitsSet,
            Long onTimeSeconds,
            String error) {
        this.frameValid = frameValid;
        this.typeFPresent = typeFPresent;
        this.rawTypeFHex = rawTypeFHex;
        this.decodedRawWallClock = decodedRawWallClock;
        this.invalidTime = invalidTime;
        this.summerTime = summerTime;
        this.reservedMinuteBit6Set = reservedMinuteBit6Set;
        this.reservedHourBitsSet = reservedHourBitsSet;
        this.onTimeSeconds = onTimeSeconds;
        this.error = error;
    }

    static MeterTimeEvidence inspect(byte[] mailboxPayload) {
        if (mailboxPayload == null) return failure(false, null, "MAILBOX_PAYLOAD_MISSING");
        for (int start = 0; start + 8 < mailboxPayload.length; start++) {
            if (u8(mailboxPayload[start]) != 0x68) continue;
            int l = u8(mailboxPayload[start + 1]);
            if (l != u8(mailboxPayload[start + 2])) continue;
            int total = l + 6;
            if (start + total > mailboxPayload.length) continue;
            byte[] frame = Arrays.copyOfRange(mailboxPayload, start, start + total);
            if (MbusFrameSupport.isValidLongFrame(frame)) return inspectLongFrame(frame);
        }
        return failure(false, null, "MBUS_LONG_FRAME_NOT_FOUND");
    }

    static MeterTimeEvidence inspectLongFrame(byte[] frame) {
        if (frame == null || !MbusFrameSupport.isValidLongFrame(frame)) {
            return failure(false, null, "MBUS_LONG_FRAME_INVALID");
        }
        int ci = u8(frame[6]);
        if (ci != 0x72) {
            return failure(true, null, String.format(Locale.US, "UNSUPPORTED_CI_0x%02X", ci));
        }

        int l = u8(frame[1]);
        int p = 19;      // C,A,CI + 12-byte variable-data header.
        int end = 4 + l; // checksum begins here.
        if (p > end) return failure(true, null, "CI72_VARIABLE_HEADER_INCOMPLETE");

        TypeF typeF = null;
        Long onTime = null;
        String parseError = null;

        while (p < end) {
            int dif = u8(frame[p++]);
            if (dif == 0x2F) continue;
            if (dif == 0x0F || dif == 0x1F || (dif & 0x0F) == 0x0F) break;

            int lastDif = dif;
            int difExtensions = 0;
            while ((lastDif & 0x80) != 0) {
                if (difExtensions++ >= MAX_EXTENSIONS || p >= end) {
                    parseError = "DIFE_PARSE_FAILED";
                    break;
                }
                lastDif = u8(frame[p++]);
            }
            if (parseError != null) break;
            if (p >= end) {
                parseError = "PREMATURE_END_BEFORE_VIF";
                break;
            }

            int vif = u8(frame[p++]);
            boolean plainTextVif = (vif & 0x7F) == 0x7C;
            int vifeCount = 0;
            if (plainTextVif) {
                if (p >= end) {
                    parseError = "PREMATURE_END_IN_PLAIN_TEXT_VIF";
                    break;
                }
                int textLength = u8(frame[p++]);
                if (p + textLength > end) {
                    parseError = "PLAIN_TEXT_VIF_LENGTH_EXCEEDS_RECORD_AREA";
                    break;
                }
                p += textLength;
            } else {
                int lastVif = vif;
                while ((lastVif & 0x80) != 0) {
                    if (vifeCount++ >= MAX_EXTENSIONS || p >= end) {
                        parseError = "VIFE_PARSE_FAILED";
                        break;
                    }
                    lastVif = u8(frame[p++]);
                }
                if (parseError != null) break;
            }

            int encodedLength = encodedLength(dif & 0x0F, frame, p, end);
            if (encodedLength < 0 || p + encodedLength > end) {
                parseError = "RECORD_DATA_LENGTH_INVALID";
                break;
            }

            int baseVif = vif & 0x7F;
            if (!plainTextVif && baseVif == 0x6D && encodedLength >= 4 && typeF == null) {
                typeF = decodeTypeF(frame, p);
            }
            if (!plainTextVif && baseVif == 0x20 && vifeCount == 0
                    && encodedLength >= 4 && onTime == null) {
                onTime = leUInt32(frame, p);
            }
            p += encodedLength;
        }

        if (parseError != null) return failure(true, onTime, parseError);
        if (typeF == null) return failure(true, onTime, "TYPE_F_VIF_6D_NOT_FOUND");
        return new MeterTimeEvidence(
                true,
                true,
                typeF.rawHex,
                typeF.decoded,
                typeF.invalid,
                typeF.summer,
                typeF.reservedMinuteBit6,
                typeF.reservedHourBits,
                onTime,
                onTime == null ? "ON_TIME_VIF_20_NOT_FOUND" : null);
    }

    boolean suitableForFamilyBoundaryAnchor() {
        return frameValid
                && typeFPresent
                && !invalidTime
                && decodedRawWallClock != null
                && onTimeSeconds != null
                && onTimeSeconds >= 0L;
    }

    private static TypeF decodeTypeF(byte[] frame, int p) {
        int b0 = u8(frame[p]);
        int b1 = u8(frame[p + 1]);
        int b2 = u8(frame[p + 2]);
        int b3 = u8(frame[p + 3]);
        int minute = b0 & 0x3F;
        int hour = b1 & 0x1F;
        int day = b2 & 0x1F;
        int month = b3 & 0x0F;
        int year = ((b2 >> 5) | ((b3 >> 1) & 0xF8)) + 2000;
        String decoded = String.format(Locale.US, "%04d-%02d-%02d %02d:%02d",
                year, month, day, hour, minute);
        String raw = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3);
        return new TypeF(
                raw,
                decoded,
                (b0 & 0x80) != 0,
                (b1 & 0x80) != 0,
                (b0 & 0x40) != 0,
                (b1 & 0x60) != 0);
    }

    private static int encodedLength(int field, byte[] data, int p, int end) {
        switch (field) {
            case 0x00: case 0x08: return 0;
            case 0x01: case 0x09: return 1;
            case 0x02: case 0x0A: return 2;
            case 0x03: case 0x0B: return 3;
            case 0x04: case 0x05: case 0x0C: return 4;
            case 0x06: case 0x0E: return 6;
            case 0x07: return 8;
            case 0x0D:
                if (p >= end) return -1;
                int lvar = u8(data[p]);
                int valueLength;
                if (lvar <= 0xBF) valueLength = lvar;
                else if (lvar <= 0xCF) valueLength = lvar - 0xC0;
                else if (lvar <= 0xDF) valueLength = lvar - 0xD0;
                else if (lvar <= 0xEF) valueLength = lvar - 0xE0;
                else if (lvar <= 0xFA) valueLength = lvar - 0xF0;
                else return -1;
                return 1 + valueLength;
            default: return -1;
        }
    }

    private static long leUInt32(byte[] data, int p) {
        return ((long) u8(data[p]))
                | ((long) u8(data[p + 1]) << 8)
                | ((long) u8(data[p + 2]) << 16)
                | ((long) u8(data[p + 3]) << 24);
    }

    private static MeterTimeEvidence failure(boolean frameValid, Long onTime, String error) {
        return new MeterTimeEvidence(
                frameValid, false, null, null,
                false, false, false, false, onTime, error);
    }

    private static int u8(byte value) {
        return value & 0xFF;
    }

    private static final class TypeF {
        final String rawHex;
        final String decoded;
        final boolean invalid;
        final boolean summer;
        final boolean reservedMinuteBit6;
        final boolean reservedHourBits;

        TypeF(
                String rawHex,
                String decoded,
                boolean invalid,
                boolean summer,
                boolean reservedMinuteBit6,
                boolean reservedHourBits) {
            this.rawHex = rawHex;
            this.decoded = decoded;
            this.invalid = invalid;
            this.summer = summer;
            this.reservedMinuteBit6 = reservedMinuteBit6;
            this.reservedHourBits = reservedHourBits;
        }
    }
}
