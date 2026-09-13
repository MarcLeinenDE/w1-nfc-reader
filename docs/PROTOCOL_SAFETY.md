# Protocol and safety boundary

W1 NFC Reader talks directly to a metering device, so protocol changes are intentionally conservative.

## Product rule: Live and History are separate

A normal NFC contact performs the fast Live/default read only.

History synchronization begins only after an explicit user action. The next meter contact is used to verify that the presented meter matches the intended meter before archive traversal begins.

A failed later NFC attempt must not destroy the last successful Live state.

## Physically validated v2 archive scope

The v2 product line has physically validated **Hour, Day and Month** archive families. They are independent synchronization families with independent baseline state.

The production safety shell for each family includes:

- explicit default-application reset before accepting the preflight Live/default observation;
- meter identity verification before archive access;
- family application selection;
- alternating selected-response requests as required by the validated W1 flow;
- no blind retry of an ambiguous selected request;
- traversal until a semantic meter/protocol terminal condition rather than a hardcoded record count;
- immediate persistence of every accepted archive record;
- final restore of the default application;
- structural verification of the restored Live/default response.

An initial/full family traversal may mark a baseline COMPLETE only after a valid semantic terminal condition and successful final restore verification.

After an authoritative COMPLETE baseline, the normal update path is incremental. Incremental traversal may stop at `KNOWN_RECORD_REACHED` only through secure known overlap against the database snapshot that existed before the current run. Newly inserted records from the same run must not create their own stop evidence.

Year/Billing is not exposed as a normal v2 history synchronization control unless separately validated and intentionally released.

## Read-focused boundary

The app does not intentionally perform persistent writes to:

- meter configuration;
- radio configuration;
- calibration values;
- metering parameters;
- firmware.

The NFC mailbox requires volatile host/RF mailbox operations to transport M-Bus queries. Those transport operations are not treated as permission to add persistent meter writes.

### Public manufacturer evidence

Axioma Metering's current Qalcosonic W1 manual `QW1_V22.4_EN` dated 2026-05-11 distinguishes the interfaces explicitly: the optical interface is described for reading and parameter changes, while the integrated NFC interface is described as intended for **data reading only**.

Public mirror of the Axioma document:
https://device.report/m/d4a0014787f6accc0035156bf0096d94be5bb5429de79aded8d0422b322e7436

This independently supports the existing read-focused application policy. It does not expand the permitted NFC behavior or replace physical validation.

Additional public evidence and interpretation guardrails are recorded in `docs/research/PUBLIC_QW1_EVIDENCE.md`.

## Protected protocol baseline

`QalcosonicReader.java`, `MbusParser.java` and the NFC/mailbox/archive transport path are treated as protected, real-device-validated baseline code.

Changes should be:

- small and focused;
- justified by protocol/device evidence;
- covered by targeted regression tests;
- revalidated on a physical meter when behavior on the NFC wire or decoded payload changes.

Pure UI/database/analytics changes should not create a new physical-meter gate when they do not alter meter communication.

## Archive identity and persistence

Native archive identity is occurrence-safe and does not use derived UTC:

`meter_id + archive_family + raw_logger_timestamp + occurrence_key`

Occurrence evidence prefers typed ON_TIME, then suitable raw Type-F evidence, with `LEGACY` only when stronger evidence is unavailable. This preserves two physical archive periods that share the same raw wall-clock timestamp, such as a DST fall-back occurrence.

Expected persistence semantics:

- same native identity + same content: confirmation/idempotent repeat;
- new native identity: insert;
- same native identity + different content: retain conflict/revision evidence rather than silently overwrite;
- successfully decoded periods persist independently even when a later part of the same synchronization fails.

Derived UTC/local time is never part of native record identity.

## Time provenance

Raw meter/logger timestamps remain preserved source evidence and are never rewritten by derived UTC/local presentation.

The global Android presentation/query basis is explicit:

- `LOCAL` (default): Live selection/order uses Android acquisition epoch; archive selection/order uses the resolved UTC timeline and the persisted per-meter IANA zone. Raw meter time remains secondary evidence.
- `METER`: raw meter/logger wall-clock time is primary; bounded Live History uses persisted `meter_time` for selection/order/predecessor context. Trustworthy real/local time may be shown as secondary evidence.

Archive records use the raw logger timestamp as the meter-provided completed-period boundary while the v2.1 time model may derive canonical UTC intervals from retained timing evidence. Unresolved evidence stays unresolved; the app must not invent timezone/UTC information.

See `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md` for the canonical v2.1 time model.

## Consumption semantics

- A Live delta uses only the newest strictly earlier **Live** observation from the same physical meter and the same selected time basis.
- Hidden Hour/Day/Month observations must not become predecessors for a Live card.
- Hour, Day and Month archive deltas remain same-meter and same-granularity.
- No consumption delta crosses a confirmed meter replacement.
- Negative/reset-like differences are not presented as normal consumption.

## Public hardware evidence is informative, not normative

A public 2020 QW1 FCC-era parts list identifies an `ST25DV04K-IER6T3` dynamic NFC/RFID tag with Fast Transfer Mode in that historical hardware revision. STMicroelectronics documents the ST25DV04K family with a half-duplex 256-byte RF/I2C Fast Transfer Mode mailbox.

That architecture is consistent with the mailbox behavior independently observed by W1 NFC Reader, but it is **not an application dependency**. The app must not assume every QW1 revision uses the same NFC controller, RF transceiver or PCB.

See `docs/research/PUBLIC_QW1_EVIDENCE.md` for source links and scope limitations.

## Real-device validation

A unit test or public document can protect/support a known invariant but cannot prove physical NFC behavior. Changes affecting transport timing, selected-response sequencing, terminal handling, known-overlap termination or default restore should be tested on the real meter before a stable release.
