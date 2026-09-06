# W1 NFC Reader 2.0 — breaking-change policy

Status: development policy for the `2.0.0-dev` line.

W1 NFC Reader 2.0 is a major release because the History synchronization model is being rebuilt from the first public 1.0.0 implementation to the family-separated model validated during archive research.

The goal is to preserve trustworthy user data where that is safe and inexpensive, but **1.x behavioral/data-format compatibility is not a design constraint**. When preserving an old representation would weaken the v2 safety model or require carrying obsolete semantics indefinitely, v2 may intentionally break compatibility and the final release notes must say so clearly.

## What remains stable

- Android application ID remains `de.marcleinen.w1nfcreader`.
- Stable release signing identity remains the same, so Android package upgrades remain possible.
- A normal NFC presentation remains a fast Live/default read only.
- History/archive synchronization remains an explicit user action.
- The app remains read-focused and must not introduce intentional persistent meter/radio/calibration/firmware writes.
- Meter IDs and archive-family boundaries remain part of data identity; cumulative readings from different meter IDs are never directly subtracted.

## Breaking History semantics

The v1 whole-history synchronization state is replaced by independent per-meter/per-family state for at least:

- Hour;
- Day;
- Month.

Each family has its own baseline completeness, latest-attempt outcome, stop reason, coverage and recovery state.

A later partial/failed incremental update must not erase a previously completed baseline. Conversely, stored data alone must never be interpreted as proof that a full baseline completed.

### v1 completeness is not authoritative

The 1.0.0 Month implementation could classify the legacy fixed hard-cap outcome as traversal-complete. For that reason:

- v1 meter-level `COMPLETE` metadata must not be promoted automatically to v2 `MONTH COMPLETE`;
- fixed record counts are not valid v2 completion reasons;
- a v2 family baseline becomes complete only after a supported semantic archive end plus successful final default/Live verification;
- existing v1 archive rows may still be useful historical data and confirmations, but their presence alone does not establish v2 completeness.

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

The 2.0 portable-data schema may add or change representation for:

- independent family sync state;
- archive conflicts/confirmations;
- raw logger timestamp identity;
- raw Type-F/CP32 time evidence;
- SU/IV flags;
- typed ON_TIME;
- phone acquisition epoch;
- clock-model state/confidence;
- derived absolute time and its provenance;
- multiple native archive granularities in CSV.

### `.qw1backup`

Backward restore compatibility with 1.x `.qw1backup` files is **not a release requirement for 2.0.0**.

If the final v2 implementation does not support restoring a 1.x backup, the release notes and upgrade guidance must state this prominently and instruct users to create a human-readable CSV export before upgrading when they need an external copy of the old dataset.

The app must reject an unsupported/corrupt backup before modifying existing local data.

### CSV

CSV remains a human-readable interoperability/export format, not the canonical restore format. A new schema version may therefore be introduced without promising that old/new CSV files are mutually importable.

## Installed-app upgrade policy

An in-place Android upgrade may preserve existing Live readings, meter lifecycle, preferences and archive records where a safe migration is straightforward. This is a best-effort data-preservation goal, not permission to preserve invalid v1 synchronization semantics.

At minimum:

- v1 sync-completeness metadata is ignored/reinitialized under the v2 family model;
- v1 archive records must never be silently marked as a fully validated v2 baseline merely because they exist;
- any destructive local migration that is ultimately required must be explicitly documented before the stable 2.0.0 release.

## Public-release requirement

The final 2.0.0 `CHANGELOG.md`/release notes must contain a visible **Breaking changes** section describing the actual shipped behavior, including backup compatibility, any local-data reset/migration behavior and the requirement for a new initial family baseline where applicable.
