package de.marcleinen.engineeringlab.qalcosonic;

import java.util.Arrays;

/**
 * Production M-Bus frame helpers used by the released W1 read paths.
 *
 * <p>This class intentionally contains only commands and validation needed by the production
 * default and Monthly archive flows. Experimental archive-family discovery stays outside the
 * product path.</p>
 */
final class MbusFrameSupport {
    private static final byte[] RESET_MBUS = {
            0x10, 0x40, (byte) 0xFE, 0x3E, 0x16
    };
    private static final byte[] REQ_UD2_FCB1 = {
            0x10, 0x7B, (byte) 0xFE, 0x79, 0x16
    };
    private static final byte[] REQ_UD2_FCB0 = {
            0x10, 0x5B, (byte) 0xFE, 0x59, 0x16
    };
    private static final byte[] APPLICATION_RESET_DEFAULT = {
            0x68, 0x03, 0x03, 0x68, 0x73, (byte) 0xFE, 0x50, (byte) 0xC1, 0x16
    };

    private MbusFrameSupport() {}

    static byte[] reset() {
        return RESET_MBUS.clone();
    }

    static byte[] reqUd2Fcb1() {
        return REQ_UD2_FCB1.clone();
    }

    static byte[] reqUd2Fcb0() {
        return REQ_UD2_FCB0.clone();
    }

    static byte[] applicationResetDefault() {
        return APPLICATION_RESET_DEFAULT.clone();
    }

    static byte[] monthlyApplicationSelect() {
        byte[] frame = {
                0x68, 0x04, 0x04, 0x68,
                0x73, (byte) 0xFE, 0x50, 0x40,
                0x00, 0x16
        };
        frame[8] = (byte) checksum(frame, 4, 8);
        return frame;
    }

    static FrameInspection inspectSelectedResponse(byte[] mailboxPayload) {
        if (mailboxPayload == null) return new FrameInspection(false, 0, -1, -1);
        for (int start = 0; start + 8 < mailboxPayload.length; start++) {
            if ((mailboxPayload[start] & 0xFF) != 0x68) continue;
            int l1 = mailboxPayload[start + 1] & 0xFF;
            int l2 = mailboxPayload[start + 2] & 0xFF;
            if (l1 != l2) continue;
            int total = l1 + 6;
            if (start + total > mailboxPayload.length) continue;
            byte[] candidate = Arrays.copyOfRange(mailboxPayload, start, start + total);
            if (!isValidLongFrame(candidate)) continue;

            int ci = candidate.length > 6 ? candidate[6] & 0xFF : -1;
            int recordBytes = -1;
            if ((ci == 0x72 || ci == 0x76) && l1 >= 15) recordBytes = l1 - 15;
            return new FrameInspection(true, candidate.length, ci, recordBytes);
        }
        return new FrameInspection(false, 0, -1, -1);
    }

    static boolean isValidLongFrame(byte[] frame) {
        if (frame == null || frame.length < 9 || (frame[0] & 0xFF) != 0x68) return false;
        int l1 = frame[1] & 0xFF;
        int l2 = frame[2] & 0xFF;
        if (l1 != l2 || frame.length != l1 + 6 || (frame[3] & 0xFF) != 0x68) return false;
        if ((frame[frame.length - 1] & 0xFF) != 0x16) return false;
        return checksum(frame, 4, frame.length - 2) == (frame[frame.length - 2] & 0xFF);
    }

    static boolean isValidShortFrame(byte[] frame) {
        if (frame == null || frame.length != 5 || (frame[0] & 0xFF) != 0x10) return false;
        if ((frame[4] & 0xFF) != 0x16) return false;
        return (((frame[1] & 0xFF) + (frame[2] & 0xFF)) & 0xFF) == (frame[3] & 0xFF);
    }

    private static int checksum(byte[] bytes, int fromInclusive, int toExclusive) {
        int sum = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            sum = (sum + (bytes[i] & 0xFF)) & 0xFF;
        }
        return sum;
    }

    static final class FrameInspection {
        final boolean validLongFrame;
        final int frameLength;
        final int ci;
        final int variableRecordBytes;

        FrameInspection(boolean validLongFrame, int frameLength, int ci, int variableRecordBytes) {
            this.validLongFrame = validLongFrame;
            this.frameLength = frameLength;
            this.ci = ci;
            this.variableRecordBytes = variableRecordBytes;
        }
    }
}
