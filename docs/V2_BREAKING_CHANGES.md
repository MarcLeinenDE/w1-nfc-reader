# W1 NFC Reader 2.0 — breaking changes and upgrade guidance

Status: final 2.0.0 release contract.

W1 NFC Reader 2.0 is a major release because the History synchronization model is rebuilt from the first public 1.0.0 implementation to the family-separated model validated during archive research.

The goal is to preserve trustworthy user data where that is safe, but **1.x History-state and portable-backup compatibility are not preserved when doing so would weaken the v2 safety model**.

## What remains stable

- Android application ID remains `de.marcleinen.w1nfcreader`.
- Stable release signing identity remains the same, so Android package upgrades remain possible.
- A normal NFC presentation remains a fast Live/default read only.
- History/archive synchronization remains an explicit user action.
- The app remains read-focused and does not intentionally introduce persistent meter/radio/calibration/firmware writes.
- Meter IDs and archive-family boundaries remain part of data identity; cumulative readings from different meter IDs are never directly subtracted.

## Breaking History semantics

The v1 whole-history synchronization state is replaced by independent per-meter/per-family state for:

- Hour;
- Day;
- Month.

Each family has its own baseline completeness, latest-attempt outcome, stop reason, coverage and recovery state.

A later partial/failed incremental or Full Re-Sync attempt does not erase a previously completed baseline. Conversely, stored data alone is never interpreted as proof that a full baseline completed.

### v1 completeness is not authoritative

The 1.0.0 Month implementation used a different whole-history model. Therefore:

- v1 meter-level `COMPLETE` metadata is not promoted automatically to v2 `MONTH COMPLETE`;
- fixed record counts are not valid v2 completion reasons;
- a v2 family baseline becomes complete only after a supported semantic archive end plus successful final default/Live verification;
- users upgrading from 1.x must establish new v2 Hour, Day and Month baselines through explicit History synchronization;
- legacy v1 Month archive data is not treated as an authoritative v2 family baseline.

## v2 successful and incomplete stop model

Successful full traversal:

- `PROTOCOL_TERMINAL`;
- strongly confirmed `RING_WRAP_DETECTED`.

Successful later incremental traversal:

- `KNOWN_RECORD_REACHED`;
- terminal/ring remain valid if encountered before the known overlap.

Protective/incomplete outcomes include boundary guard/head advance, transport loss, no-host/I/O failures, parser/structure/progression problems, invalid/missing Type-F time, ON_TIME inconsistency, unverified default state and technical watchdog/failsafe stops.

A technical watchdog is never a successful archive completion condition.

## Portable-data compatibility

### `.qw1backup`

**W1 NFC Reader 2.0.0 does not restore 1.x `.qw1backup` files.**

2.0.0 writes and accepts backup schema 2. An unsupported backup schema, malformed archive or checksum mismatch is rejected before normal restore mutation begins.

Before upgrading from 1.x, create a human-readable CSV export if you need an external copy of the old dataset. After 2.0.0 is installed and new v2 family baselines have been established, create a new `.qw1backup`; that v2 backup contains the independent family synchronization state required for a lossless v2 restore.

### CSV

CSV remains a human-readable interoperability/export format, not the canonical restore format. 2.0.0 uses CSV schema 3 with explicit family/completed-period and consumption-reference semantics. Cross-version CSV import/restore compatibility is not promised.

## Installed-app upgrade behavior

The public package ID and stable signing identity remain unchanged, so the official 2.0.0 APK can upgrade an official 1.0.0 installation in place.

The legacy Live-reading database is retained by the application architecture, while v2 archive acquisition uses a separate family-neutral archive store. The release intentionally does not promote v1 whole-history completeness into v2 family completeness. Users should therefore plan to establish fresh v2 Hour, Day and Month baselines after upgrading.

If preserving an external view of 1.x archive data matters, export CSV with 1.x before installing 2.0.0. Do not rely on a 1.x `.qw1backup` as a v2 restore path.

## Release requirement

The 2.0.0 release notes and `CHANGELOG.md` must keep these breaking points visible:

- new independent Hour/Day/Month baseline model;
- fresh v2 family baselines required after a 1.x upgrade;
- 1.x `.qw1backup` files unsupported by 2.0.0;
- CSV is export/interoperability rather than canonical restore;
- stable package/signing identity retained for normal Android in-place upgrade.
