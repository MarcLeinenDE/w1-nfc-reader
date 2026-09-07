/*
 * W1 NFC Reader
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The ST25 mailbox / Qalcosonic M-Bus transport behavior in this Android implementation was
 * informed by and adapted from concepts in dbmaxpayne/esphome_qalcosonicnfc,
 * Copyright (c) 2025 Mark Hermann, licensed LGPL-2.1-or-later.
 *
 * Adapted and substantially reimplemented for Android NfcV in 2026 by Marc Leinen.
 * Distribution under GPL-3.0-or-later uses the GPL conversion option in LGPL-2.1 section 3.
 * See THIRD_PARTY_NOTICES.md for the upstream revision and full provenance record.
 */
package de.marcleinen.engineeringlab.qalcosonic;

import android.nfc.tech.NfcV;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Minimal ST25DV / Qalcosonic W1 NFC-V transport.
 *
 * The meter configuration itself is never written. The only ST25 write used here is the
 * volatile mailbox enable bit and temporary mailbox messages containing M-Bus read commands.
 */
final class QalcosonicReader {
    private static final int ST25_MFG_CODE = 0x02;
    private static final int ST25_WRITE_MESSAGE = 0xAA;
    private static final int ST25_READ_MSG_LENGTH = 0xAB;
    private static final int ST25_READ_MSG = 0xAC;
    private static final int ST25_READ_DYN_CONFIG = 0xAD;
    private static final int ST25_WRITE_DYN_CONFIG = 0xAE;

    private static final int ST25_EH_CTRL_DYN = 0x02;
    private static final int ST25_MB_CTRL_DYN = 0x0D;
    private static final int ST25_MB_EN = 0x01;
    private static final int ST25_HOST_PUT_MSG = 0x02;

    private static final int FLAGS_UNADDRESSED = 0x02;
    private static final int FLAGS_ADDRESSED = 0x22;

    private static final byte[] RESET_APP = {(byte) 0x10, (byte) 0x40, (byte) 0xFE};
    private static final byte[] GET_DATA_1 = {(byte) 0x10, (byte) 0x7B, (byte) 0xFE};
    private static final long APPLICATION_RESET_STABILIZATION_MS = 1000L;

    private final NfcV nfc;
    private final byte[] androidUid;
    private final StringBuilder trace = new StringBuilder();
    private Addressing addressing;

    QalcosonicReader(NfcV nfc, byte[] androidUid) {
        this.nfc = nfc;
        this.androidUid = androidUid == null ? new byte[0] : androidUid.clone();
    }

    String getTrace() {
        return trace.toString();
    }

    /**
     * Normal product Live read.
     *
     * <p>A physical NFC recontact does not prove that a prior archive application was restored.
     * Normalize the transient W1 application state first, then run the long-standing protected
     * M-Bus reset + default data request sequence. If the reset cannot be confirmed through the
     * mailbox, fail the read rather than accepting a parseable archive frame as Live data.</p>
     */
    Readout read() throws IOException {
        return readInternal(true);
    }

    /**
     * Protected Live read for a caller that has already issued APPLICATION_RESET_DEFAULT and
     * allowed the required stabilization interval as part of its own safety shell.
     */
    Readout readAssumingDefaultApplication() throws IOException {
        return readInternal(false);
    }

    private Readout readInternal(boolean normalizeDefaultApplication) throws IOException {
        selectAddressing();
        trace("TRANSPORT_SELECTED=" + addressing.label);

        int energyHarvesting = readDynamic(ST25_EH_CTRL_DYN);
        int mailboxState = readDynamic(ST25_MB_CTRL_DYN);
        trace(String.format(Locale.US, "DYN:EH_CTRL=0x%02X;MB_CTRL=0x%02X", energyHarvesting, mailboxState));

        if ((mailboxState & ST25_MB_EN) == 0) {
            trace("MAILBOX_ENABLE_VOLATILE");
            writeDynamic(ST25_MB_CTRL_DYN, ST25_MB_EN);
            mailboxState = readDynamic(ST25_MB_CTRL_DYN);
            if ((mailboxState & ST25_MB_EN) == 0) {
                throw new IOException("ST25_MAILBOX_ENABLE_FAILED");
            }
        }

        if ((mailboxState & ST25_HOST_PUT_MSG) != 0) {
            trace("MAILBOX_DRAIN_PENDING");
            try {
                readMailboxMessage();
            } catch (IOException oldMessageError) {
                trace("MAILBOX_DRAIN_FAILED=" + safeCode(oldMessageError));
            }
        }

        if (normalizeDefaultApplication) {
            byte[] defaultResetResponse = issueMeterFrame(MbusFrameSupport.applicationResetDefault());
            trace("APPLICATION_RESET_DEFAULT_RESPONSE=" + hex(defaultResetResponse));
            SystemClock.sleep(APPLICATION_RESET_STABILIZATION_MS);
        }

        byte[] resetResponse = issueMeterCommand(RESET_APP);
        trace("RESET_RESPONSE=" + hex(resetResponse));

        byte[] meterResponse = issueMeterCommand(GET_DATA_1);
        trace("METER_RESPONSE=" + hex(meterResponse));

        return new Readout(androidUid, addressing.label, energyHarvesting, mailboxState,
                resetResponse, meterResponse, trace.toString());
    }

    private void selectAddressing() throws IOException {
        IOException last = null;
        Addressing[] candidates = new Addressing[]{
                Addressing.unaddressed(),
                Addressing.addressed(androidUid, "ADDRESSED_ANDROID_UID"),
                Addressing.addressed(reverse(androidUid), "ADDRESSED_REVERSED_UID")
        };

        for (Addressing candidate : candidates) {
            if (candidate.uid != null && candidate.uid.length != 8) continue;
            try {
                trace("PROBE=" + candidate.label);
                byte[] response = transceive(buildCustomCommand(candidate, ST25_READ_DYN_CONFIG,
                        new byte[]{(byte) ST25_EH_CTRL_DYN}));
                requireData(response, 1, "DYN_REGISTER_PROBE");
                addressing = candidate;
                return;
            } catch (IOException e) {
                last = e;
                trace("PROBE_FAILED=" + candidate.label + ";CODE=" + safeCode(e));
            }
        }
        throw new IOException("ST25_ADDRESSING_MODE_NOT_FOUND", last);
    }

    private int readDynamic(int register) throws IOException {
        byte[] response = transceive(buildCustomCommand(addressing, ST25_READ_DYN_CONFIG,
                new byte[]{(byte) register}));
        requireData(response, 1, String.format(Locale.US, "READ_DYN_0x%02X", register));
        return response[1] & 0xFF;
    }

    private void writeDynamic(int register, int value) throws IOException {
        byte[] response = transceive(buildCustomCommand(addressing, ST25_WRITE_DYN_CONFIG,
                new byte[]{(byte) register, (byte) value}));
        requireSuccess(response, String.format(Locale.US, "WRITE_DYN_0x%02X", register));
    }

    private void writeMailboxMessage(byte[] message) throws IOException {
        if (message.length < 1 || message.length > 256) {
            throw new IOException("MAILBOX_MESSAGE_LENGTH_OUT_OF_RANGE:" + message.length);
        }
        byte[] payload = new byte[1 + message.length];
        payload[0] = (byte) (message.length - 1);
        System.arraycopy(message, 0, payload, 1, message.length);
        byte[] response = transceive(buildCustomCommand(addressing, ST25_WRITE_MESSAGE, payload));
        requireSuccess(response, "WRITE_MAILBOX_MESSAGE");
    }

    private int readMailboxMessageLength() throws IOException {
        byte[] response = transceive(buildCustomCommand(addressing, ST25_READ_MSG_LENGTH, new byte[0]));
        requireData(response, 1, "READ_MAILBOX_LENGTH");
        return (response[1] & 0xFF) + 1;
    }

    private byte[] readMailboxMessage() throws IOException {
        int totalLength = readMailboxMessageLength();
        if (totalLength < 1 || totalLength > 256) {
            throw new IOException("MAILBOX_MESSAGE_LENGTH_INVALID:" + totalLength);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(totalLength);
        int pointer = 0;
        int maxChunk = Math.max(1, Math.min(48, nfc.getMaxTransceiveLength() - 8));

        while (pointer < totalLength) {
            int chunk = Math.min(maxChunk, totalLength - pointer);
            byte[] payload = {(byte) pointer, (byte) (chunk - 1)};
            byte[] response = transceive(buildCustomCommand(addressing, ST25_READ_MSG, payload));
            requireData(response, chunk, "READ_MAILBOX_CHUNK");
            out.write(response, 1, chunk);
            pointer += chunk;
        }
        return out.toByteArray();
    }

    private byte[] issueMeterCommand(byte[] commandWithoutChecksum) throws IOException {
        return issueMeterFrame(makeMbusShortFrame(commandWithoutChecksum));
    }

    private byte[] issueMeterFrame(byte[] mbus) throws IOException {
        if (mbus == null || mbus.length == 0) {
            throw new IOException("MBUS_FRAME_MISSING");
        }
        boolean valid = (mbus[0] & 0xFF) == 0x68
                ? MbusFrameSupport.isValidLongFrame(mbus)
                : MbusFrameSupport.isValidShortFrame(mbus);
        if (!valid) {
            throw new IOException("MBUS_FRAME_INVALID");
        }

        trace("MBUS_TX=" + hex(mbus));
        writeMailboxMessage(mbus);

        long deadline = SystemClock.elapsedRealtime() + 1800L;
        boolean hostMessageReady = false;
        IOException lastPollError = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25L);
            try {
                int state = readDynamic(ST25_MB_CTRL_DYN);
                if ((state & ST25_HOST_PUT_MSG) != 0) {
                    hostMessageReady = true;
                    break;
                }
            } catch (IOException e) {
                lastPollError = e;
            }
        }

        if (!hostMessageReady) {
            trace("HOST_PUT_MSG_TIMEOUT");
            if (lastPollError != null) {
                trace("MAILBOX_POLL_LAST_ERROR=" + safeCode(lastPollError));
            }
        }

        byte[] response = readMailboxMessage();
        trace("MBUS_RX=" + hex(response));
        return response;
    }

    private static byte[] makeMbusShortFrame(byte[] commandWithoutChecksum) {
        int checksum = 0;
        for (int i = 1; i < commandWithoutChecksum.length; i++) {
            checksum = (checksum + (commandWithoutChecksum[i] & 0xFF)) & 0xFF;
        }
        byte[] frame = Arrays.copyOf(commandWithoutChecksum, commandWithoutChecksum.length + 2);
        frame[frame.length - 2] = (byte) checksum;
        frame[frame.length - 1] = 0x16;
        return frame;
    }

    private byte[] transceive(byte[] command) throws IOException {
        trace("NFC_TX=" + hex(command));
        byte[] response = nfc.transceive(command);
        trace("NFC_RX=" + hex(response));
        requireSuccess(response, "NFC_CUSTOM_COMMAND");
        return response;
    }

    private static void requireSuccess(byte[] response, String operation) throws IOException {
        if (response == null || response.length == 0) {
            throw new IOException(operation + ":EMPTY_RESPONSE");
        }
        int flags = response[0] & 0xFF;
        if ((flags & 0x01) != 0) {
            String code = response.length > 1
                    ? String.format(Locale.US, "0x%02X", response[1] & 0xFF)
                    : "UNKNOWN";
            throw new IOException(operation + ":ISO15693_ERROR=" + code);
        }
    }

    private static void requireData(byte[] response, int expectedDataBytes, String operation) throws IOException {
        requireSuccess(response, operation);
        if (response.length < 1 + expectedDataBytes) {
            throw new IOException(operation + ":RESPONSE_TOO_SHORT=" + response.length
                    + ";EXPECTED_DATA=" + expectedDataBytes);
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
            System.arraycopy(mode.uid, 0, result, p, mode.uid.length);
            p += mode.uid.length;
        }
        System.arraycopy(payload, 0, result, p, payload.length);
        return result;
    }

    private void trace(String line) {
        trace.append(line).append('\n');
    }

    private static String safeCode(IOException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    static String hex(byte[] data) {
        if (data == null) return "<NULL>";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] reverse(byte[] input) {
        if (input == null) return new byte[0];
        byte[] result = input.clone();
        for (int i = 0, j = result.length - 1; i < j; i++, j--) {
            byte tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return result;
    }

    static final class Readout {
        final byte[] uid;
        final String transport;
        final int energyHarvestingState;
        final int initialMailboxState;
        final byte[] resetResponse;
        final byte[] meterResponse;
        final String trace;

        Readout(byte[] uid, String transport, int energyHarvestingState, int initialMailboxState,
                byte[] resetResponse, byte[] meterResponse, String trace) {
            this.uid = uid.clone();
            this.transport = transport;
            this.energyHarvestingState = energyHarvestingState;
            this.initialMailboxState = initialMailboxState;
            this.resetResponse = resetResponse.clone();
            this.meterResponse = meterResponse.clone();
            this.trace = trace;
        }
    }

    private static final class Addressing {
        final byte[] uid;
        final String label;

        private Addressing(byte[] uid, String label) {
            this.uid = uid;
            this.label = label;
        }

        static Addressing unaddressed() {
            return new Addressing(null, "UNADDRESSED");
        }

        static Addressing addressed(byte[] uid, String label) {
            return new Addressing(uid == null ? new byte[0] : uid.clone(), label);
        }
    }
}
