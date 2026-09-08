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

The ST25 mailbox requires volatile host/RF mailbox operations to transport M-Bus queries. Those transport operations are not treated as permission to add persistent meter writes.

## Protected protocol baseline

`QalcosonicReader.java`, `MbusParser.java` and the NFC/mailbox/archive transport path are treated as protected, real-device-validated baseline code.

Changes should be:

- small and focused;
- justified by protocol/device evidence;
- covered by targeted regression tests;
- revalidated on a physical meter when behavior on the NFC wire or decoded payload changes.

Pure UI/database/analytics changes should not create a new physical-meter gate when they do not alter meter communication.

## Archive identity and persistence

Archive identity is based on:

`meter_id + archive_family + logger_timestamp`

Expected persistence semantics:

- same identity + same content: confirmation/idempotent repeat;
- new identity: insert;
- same identity + different content: retain conflict/revision evidence rather than silently overwrite;
- successfully decoded periods persist independently even when a later part of the same synchronization fails.

## Time provenance

Live readings use the Android acquisition time as their primary observation time; a meter-provided time is secondary provenance.

Archive records use the logger timestamp as the end boundary of the completed archive period; retrieval time is secondary provenance.

Do not silently reinterpret meter timestamps through DST/time-zone conversions that change the recorded logger value.

## Consumption semantics

- A Live delta uses only the newest strictly earlier **Live** observation from the same physical meter.
- Hidden Hour/Day/Month observations must not become predecessors for a Live card.
- Hour, Day and Month archive deltas remain same-meter and same-granularity.
- No consumption delta crosses a confirmed meter replacement.
- Negative/reset-like differences are not presented as normal consumption.

## Real-device validation

A unit test can protect a known invariant but cannot prove physical NFC behavior. Changes affecting transport timing, selected-response sequencing, terminal handling, known-overlap termination or default restore should be tested on the real meter before a stable release.
