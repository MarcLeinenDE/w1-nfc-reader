# Public Qalcosonic W1 evidence

This document records public, independently retrievable evidence that is useful for understanding and documenting W1 NFC Reader.

It is **supporting evidence only**. It does not replace repository/CI/real-device evidence, does not define the meter protocol, and must not be used to introduce speculative protocol behavior.

## Source authority and interpretation

Prefer, in this order:

1. current Axioma Metering documentation;
2. public regulatory/FCC exhibits attributable to Axioma Metering;
3. component-vendor documentation for a component that is actually identified by public hardware evidence;
4. repository tests and physical W1 evidence for application behavior.

`device.report` is used here as a public mirror/index for Axioma/FCC documents where a stable first-party URL was not found. The mirrored document, not the mirror site itself, is the evidence.

## 1. Current Axioma manual: NFC is for data reading only

Public document:

- Axioma Metering, **Qalcosonic W1**, `QW1_V22.4_EN`, dated **2026-05-11**.
- Public mirror: https://device.report/m/d4a0014787f6accc0035156bf0096d94be5bb5429de79aded8d0422b322e7436

The manual distinguishes the integrated interfaces:

- the optical interface is intended for data reading, changing meter parameters and verification/test pulses;
- the integrated NFC interface is stated to be intended **for data reading only**.

### Repository implication

This independently supports the app's existing read-focused NFC safety boundary. It is **not** a reason to add or modify NFC commands. W1 NFC Reader must continue to avoid intentional persistent NFC writes to meter configuration, radio settings, calibration values, metering parameters or firmware.

## 2. Current Axioma manual: archived operating-time fields

The same `QW1_V22.4_EN` manual documents Hour, Day and Month archives and lists, among the stored parameters:

- total operating time;
- operating time without error.

It also gives nominal logger capacities:

- up to 1480 hourly records;
- up to 1130 daily records;
- up to 36 monthly records.

### Repository implication

The documented operating-time fields are consistent with the application's independently observed `ON_TIME` evidence and support retaining operating time as first-class timing evidence.

They do **not** prove the application's UTC reconstruction method. UTC derivation remains governed by the repository's verified Live-anchor/ON_TIME model and real-device evidence.

The nominal capacities are **informational only**. They are not traversal limits and must not replace the app's semantic terminal handling or secure known-overlap termination. A real meter may end traversal earlier or differ by revision/configuration; the application follows observed protocol state rather than a hardcoded record count.

## 3. Historical public QW1 hardware evidence: ST25DV04K

Public FCC-era parts list for a 2020 QW1 hardware revision:

- Axioma Metering UAB, QW1 parts list / `BOM_FLOW 05_-v19_-VAR_US915.xlsx`, dated 2020-03-24.
- Public mirror: https://device.report/m/9dd731eaa7d91940fb4dad96d729d567cff7714da58b323b9a472c0fd580059c

The parts list identifies, among other components:

- `ST25DV04K-IER6T3` dynamic NFC/RFID tag with Fast Transfer Mode;
- `AT25SF041` 4-Mbit SPI flash;
- `SX1276` RF transceiver.

### Repository implication

This is **historical hardware evidence only**. It shows that at least one publicly documented QW1 revision used an ST25DV04K. The application must not depend on a specific NFC controller, RF transceiver, PCB revision or BOM.

Newer QW1 revisions may use different components while preserving compatible externally observable behavior.

## 4. ST25DV04K vendor documentation: RF/I2C mailbox architecture

STMicroelectronics documents the ST25DV04K family as an ISO/IEC 15693 / NFC Forum Type 5 dynamic tag with Fast Transfer Mode between RF and I2C.

References:

- Product page: https://www.st.com/en/nfc/st25dv04k.html
- Datasheet: https://www.st.com/resource/en/datasheet/st25dv16k.pdf

ST documents a **half-duplex 256-byte volatile buffer (Mailbox)** for Fast Transfer Mode between the RF and contact/I2C worlds, including dedicated RF commands for mailbox length/read/write handling.

### Repository implication

That architecture is consistent with the mailbox-style communication independently observed and physically validated by W1 NFC Reader research.

It is **not an implementation dependency** and does not authorize ST25-specific assumptions for every W1 revision. The protected NFC/mailbox code remains governed by actual compatible-meter behavior and real-device validation.

## Non-implications / explicit guardrails

These public documents do **not** justify any of the following by themselves:

- changing `QalcosonicReader.java` or `MbusParser.java`;
- changing NFC command sequencing, mailbox timing or archive traversal;
- replacing semantic terminal detection with fixed archive-depth constants;
- treating `1480 / 1130 / 36` as hard synchronization limits;
- changing occurrence identity or known-overlap rules;
- assuming every W1 contains an ST25DV04K or SX1276;
- deriving UTC directly from the manual;
- adding meter configuration or other persistent NFC writes.

When public documentation and physical compatible-meter evidence differ, do not silently modify protocol behavior. Record the discrepancy and reopen the relevant validation gate.
