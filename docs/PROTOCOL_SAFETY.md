# Protocol and safety boundary

W1 NFC Reader talks directly to a metering device, so protocol changes are intentionally conservative.

## Product rule: live and history are separate

A normal NFC contact performs the fast live/default read only.

History synchronization begins only after an explicit user action. The next meter contact is used to verify that the presented meter matches the intended meter before the archive traversal begins.

A failed later NFC attempt must not destroy the last successful live state.

## Production archive scope

The first public release supports the **Month** archive family only.

Validated Month behavior includes:

- application selection for Month;
- alternating FCB1/FCB0 selected-response requests;
- no blind retry of an ambiguous selected request;
- traversal until a terminal/protocol condition rather than a hardcoded period count;
- recognition of the W1 terminal behavior established by physical validation;
- restore of the default application after traversal;
- structural verification of the restored default response.

Day, Hour and Year/Billing archive acquisition are outside the public production feature set until separately validated and intentionally released.

## Read-focused boundary

The app does not intentionally perform persistent writes to:

- meter configuration;
- radio configuration;
- calibration values;
- metering parameters;
- firmware.

The ST25 mailbox requires volatile host/RF mailbox operations to transport M-Bus queries. Those transport operations are not treated as permission to add persistent meter writes.

## Protected protocol baseline

`QalcosonicReader.java` and `MbusParser.java` are treated as protected, real-device-validated baseline files.

Changes should be:

- small and focused;
- justified by protocol/device evidence;
- covered by targeted regression tests;
- revalidated on a physical meter when behavior on the NFC wire or decoded payload changes.

The Month production path is additionally guarded by regression tests for the validated M-Bus frame construction and archive traversal semantics.

## Archive identity and persistence

Archive identity is based on:

`meter_id + archive_family + logger_timestamp`

Expected persistence semantics:

- same identity + same content: confirmation/idempotent repeat;
- new identity: insert;
- same identity + different content: retain conflict/revision evidence rather than silently overwrite;
- successfully decoded periods may persist independently even when a later part of the same synchronization fails.

## Time provenance

Live readings use the Android read time as their primary observation time; a meter-provided time is secondary provenance.

Archive records use the logger timestamp as their primary period time; retrieval time is secondary provenance.

Do not silently reinterpret meter timestamps through DST/time-zone conversions that change the recorded logger value.

## Real-device validation

A unit test can protect a known invariant but cannot prove physical NFC behavior. Changes affecting transport timing, selected-response sequencing, terminal handling or default restore should be tested on the real meter before a stable release.
