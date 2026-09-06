package de.marcleinen.engineeringlab.qalcosonic;

import android.nfc.tech.NfcV;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * ST25DV mailbox wire shared by the legacy Month adapter and the v2 family safety shell.
 *
 * <p>The transport behavior intentionally mirrors the physically successful 0.7.19/0.7.20
 * Monthly depth-32 path: unaddressed-first addressing discovery, volatile mailbox enable,
 * 100 ms mailbox polling, 100 ms inter-query quiet time and no automatic selected-request retry.</p>
 */
final class MonthlyArchiveNfcWire implements MonthlyArchiveTransportAdapter.Wire,
        MonthlyArchiveTransportAdapter.DefaultVerifier,
        ArchiveFamilyTransportAdapter.Wire,
        ArchiveFamilyTransportAdapter.DefaultVerifier {
    static final int MAILBOX_STATUS_POLL_DELAY_MS = 100;
    static final int INTER_QUERY_QUIET_MS = 100;

    private static final int ST25_MFG_CODE=0x02, ST25_WRITE_MESSAGE=0xAA,
            ST25_READ_MSG_LENGTH=0xAB, ST25_READ_MSG=0xAC,
            ST25_READ_DYN_CONFIG=0xAD, ST25_WRITE_DYN_CONFIG=0xAE;
    private static final int ST25_EH_CTRL_DYN=0x02, ST25_MB_CTRL_DYN=0x0D,
            ST25_MB_EN=0x01, ST25_HOST_PUT_MSG=0x02;
    private static final int FLAGS_UNADDRESSED=0x02, FLAGS_ADDRESSED=0x22;

    private final NfcV nfc;
    private final byte[] androidUid;
    private Addressing addressing;
    private boolean healthy = true;

    MonthlyArchiveNfcWire(NfcV nfc, byte[] androidUid) {
        if (nfc == null) throw new IllegalArgumentException("nfc == null");
        this.nfc = nfc;
        this.androidUid = androidUid == null ? new byte[0] : androidUid.clone();
    }

    @Override
    public void prepare() throws IOException {
        selectAddressing();
        readDynamic(ST25_EH_CTRL_DYN);
        int mailbox = readDynamic(ST25_MB_CTRL_DYN);
        if ((mailbox & ST25_MB_EN) == 0) {
            writeDynamic(ST25_MB_CTRL_DYN, ST25_MB_EN);
            mailbox = readDynamic(ST25_MB_CTRL_DYN);
            if ((mailbox & ST25_MB_EN) == 0) {
                healthy = false;
                throw new IOException("MONTHLY_SYNC_ST25_MAILBOX_ENABLE_FAILED");
            }
        }
        if ((mailbox & ST25_HOST_PUT_MSG) != 0) readMailboxMessage();
    }

    @Override
    public byte[] exchange(String label, byte[] mbusFrame, long timeoutMs) throws IOException {
        boolean valid = mbusFrame != null && mbusFrame.length > 0 &&
                ((mbusFrame[0] & 0xFF) == 0x68
                        ? MbusFrameSupport.isValidLongFrame(mbusFrame)
                        : MbusFrameSupport.isValidShortFrame(mbusFrame));
        if (!valid) throw new IOException("MONTHLY_SYNC_INVALID_MBUS_FRAME=" + label);

        try {
            writeMailboxMessage(mbusFrame);
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(MAILBOX_STATUS_POLL_DELAY_MS);
                int state = readDynamic(ST25_MB_CTRL_DYN);
                if ((state & ST25_HOST_PUT_MSG) != 0) {
                    byte[] response = readMailboxMessage();
                    SystemClock.sleep(INTER_QUERY_QUIET_MS);
                    healthy = true;
                    return response;
                }
            }
            healthy = true;
            return null;
        } catch (IOException error) {
            healthy = false;
            throw new IOException(label + ":" + safe(error), error);
        }
    }

    @Override
    public void coolDown(long millis) {
        if (millis > 0) SystemClock.sleep(millis);
    }

    @Override
    public boolean transportHealthy() {
        return healthy;
    }

    @Override
    public long elapsedRealtimeMs() {
        return SystemClock.elapsedRealtime();
    }

    @Override
    public DefaultReadObservation readDefault() throws IOException {
        try {
            QalcosonicReader.Readout readout = new QalcosonicReader(nfc, androidUid).read();
            healthy = true;
            return DefaultReadObservation.fromReadout(readout);
        } catch (IOException error) {
            healthy = false;
            throw error;
        }
    }

    private void selectAddressing() throws IOException {
        IOException last = null;
        Addressing[] candidates = {
                Addressing.unaddressed(),
                Addressing.addressed(androidUid, "ADDRESSED_ANDROID_UID"),
                Addressing.addressed(reverse(androidUid), "ADDRESSED_REVERSED_UID")
        };
        for (Addressing candidate : candidates) {
            if (candidate.uid != null && candidate.uid.length != 8) continue;
            try {
                byte[] response = transceive(buildCustomCommand(candidate, ST25_READ_DYN_CONFIG,
                        new byte[]{(byte) ST25_EH_CTRL_DYN}));
                requireData(response, 1, "MONTHLY_SYNC_ADDRESSING_PROBE");
                addressing = candidate;
                healthy = true;
                return;
            } catch (IOException error) {
                last = error;
            }
        }
        healthy = false;
        throw new IOException("MONTHLY_SYNC_ST25_ADDRESSING_MODE_NOT_FOUND" +
                (last == null ? "" : ":" + safe(last)), last);
    }

    private int readDynamic(int register) throws IOException {
        byte[] response = transceive(buildCustomCommand(addressing, ST25_READ_DYN_CONFIG,
                new byte[]{(byte) register}));
        requireData(response, 1, String.format(Locale.US,
                "MONTHLY_SYNC_READ_DYN_0x%02X", register));
        return response[1] & 0xFF;
    }

    private void writeDynamic(int register, int value) throws IOException {
        byte[] response = transceive(buildCustomCommand(addressing, ST25_WRITE_DYN_CONFIG,
                new byte[]{(byte) register, (byte) value}));
        requireSuccess(response, String.format(Locale.US,
                "MONTHLY_SYNC_WRITE_DYN_0x%02X", register));
    }

    private void writeMailboxMessage(byte[] message) throws IOException {
        if (message.length < 1 || message.length > 256) {
            throw new IOException("MONTHLY_SYNC_MAILBOX_MESSAGE_LENGTH=" + message.length);
        }
        byte[] payload = new byte[message.length + 1];
        payload[0] = (byte) (message.length - 1);
        System.arraycopy(message, 0, payload, 1, message.length);
        requireSuccess(transceive(buildCustomCommand(addressing, ST25_WRITE_MESSAGE, payload)),
                "MONTHLY_SYNC_WRITE_MAILBOX");
    }

    private byte[] readMailboxMessage() throws IOException {
        byte[] lengthResponse = transceive(buildCustomCommand(addressing,
                ST25_READ_MSG_LENGTH, new byte[0]));
        requireData(lengthResponse, 1, "MONTHLY_SYNC_READ_MESSAGE_LENGTH");
        int total = (lengthResponse[1] & 0xFF) + 1;
        if (total < 1 || total > 256) throw new IOException("MONTHLY_SYNC_BAD_LENGTH=" + total);

        ByteArrayOutputStream out = new ByteArrayOutputStream(total);
        int pointer = 0;
        int maxChunk = Math.max(1, Math.min(48, nfc.getMaxTransceiveLength() - 8));
        while (pointer < total) {
            int chunk = Math.min(maxChunk, total - pointer);
            byte[] response = transceive(buildCustomCommand(addressing, ST25_READ_MSG,
                    new byte[]{(byte) pointer, (byte) (chunk - 1)}));
            requireData(response, chunk, "MONTHLY_SYNC_READ_MESSAGE_CHUNK");
            out.write(response, 1, chunk);
            pointer += chunk;
        }
        return out.toByteArray();
    }

    private byte[] transceive(byte[] command) throws IOException {
        try {
            return nfc.transceive(command);
        } catch (IOException error) {
            throw new IOException("MONTHLY_SYNC_NFC_TRANSCEIVE:" + safe(error), error);
        }
    }

    private static byte[] buildCustomCommand(Addressing mode, int command, byte[] payload) {
        int uidLength = mode.uid == null ? 0 : mode.uid.length;
        byte[] result = new byte[3 + uidLength + payload.length];
        result[0] = (byte) (mode.uid == null ? FLAGS_UNADDRESSED : FLAGS_ADDRESSED);
        result[1] = (byte) command;
        result[2] = (byte) ST25_MFG_CODE;
        int p = 3;
        if (mode.uid != null) {
            System.arraycopy(mode.uid, 0, result, p, uidLength);
            p += uidLength;
        }
        System.arraycopy(payload, 0, result, p, payload.length);
        return result;
    }

    private static void requireSuccess(byte[] response, String operation) throws IOException {
        if (response == null || response.length == 0) {
            throw new IOException(operation + ":EMPTY_RESPONSE");
        }
        if ((response[0] & 0x01) != 0) {
            throw new IOException(operation + ":ISO15693_ERROR");
        }
    }

    private static void requireData(byte[] response, int dataBytes, String operation)
            throws IOException {
        requireSuccess(response, operation);
        if (response.length < dataBytes + 1) {
            throw new IOException(operation + ":SHORT_RESPONSE");
        }
    }

    private static byte[] reverse(byte[] input) {
        byte[] result = input == null ? new byte[0] : input.clone();
        for (int i = 0, j = result.length - 1; i < j; i++, j--) {
            byte tmp = result[i]; result[i] = result[j]; result[j] = tmp;
        }
        return result;
    }

    private static String safe(IOException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class Addressing {
        final byte[] uid;
        final String label;
        Addressing(byte[] uid, String label) { this.uid = uid; this.label = label; }
        static Addressing unaddressed() { return new Addressing(null, "UNADDRESSED"); }
        static Addressing addressed(byte[] uid, String label) {
            return new Addressing(uid == null ? new byte[0] : uid.clone(), label);
        }
    }
}
