# W1 NFC Reader v2.1 — time-model implementation foundation

Status: development implementation note for `dev/v2.1.0-real-time-timeline`.

This document translates the canonical product decision
`docs/product-decisions/v2.1-real-time-timeline-and-coverage.md` into the first implementation
boundaries. The product decision remains authoritative if this implementation note becomes stale.

## Safety boundary

The v2.1 time work is persistence/query/presentation work. It does not authorize NFC transport or
parser refactoring. In particular, do not change `QalcosonicReader.java`, `MbusParser.java` or the
physically validated mailbox/archive traversal paths merely to make the time model easier to wire.

## Canonical separation

Keep four concepts separate:

1. native raw meter/logger wall-clock evidence;
2. verified Android epoch/UTC acquisition anchors;
3. canonical derived UTC intervals where evidence is sufficient;
4. LOCAL/METER Android presentation and navigator projection.

The meter-assigned IANA timezone is used only to project canonical UTC to LOCAL civil time and to
interpret LOCAL navigator input. It is not the canonical rule that converts raw meter time to UTC.

Preferred archive UTC derivation remains:

`archive_epoch_ms = live_anchor_epoch_ms - (live_on_time_s - archive_on_time_s) * 1000`

with raw Type-F / raw-clock-origin consistency retained as independent validation evidence.

## Verified Live anchor

A persisted verified Live anchor must retain at least:

- meter ID;
- Android epoch immediately before the protected Live/default read;
- Android epoch immediately after the read;
- midpoint epoch used as the canonical acquisition anchor;
- acquisition uncertainty derived from the read window;
- raw meter Type-F bytes;
- decoded raw meter wall-clock;
- IV;
- SU;
- typed ON_TIME seconds;
- validation/provenance state.

Do not use an archive-family-wide `retrieved_at` value as a substitute for this anchor.

## Archive raw time evidence

Every newly persisted archive observation should retain, where available:

- raw logger wall-clock timestamp;
- raw Type-F bytes;
- IV;
- SU;
- typed ON_TIME seconds;
- retrieval provenance;
- family/granularity and meter identity.

Existing v2.0 display strings such as `on_time` remain compatibility/presentation data, but typed
numeric evidence becomes authoritative for time resolution.

## Native identity and repeated raw wall-clock periods

v2.0 uses:

`meter_id + archive_family + raw_logger_timestamp`

as a SQLite unique key. That cannot represent two physically distinct archive periods if a future
meter repeats the same raw wall-clock timestamp, for example during a DST fall-back regime.

v2.1 must not switch native identity to derived UTC. Instead extend native identity with an
occurrence discriminator while retaining the raw timestamp:

`meter_id + archive_family + raw_logger_timestamp + occurrence_key`

Occurrence-key precedence:

1. `OT:<on_time_seconds>` when valid typed ON_TIME is available;
2. `TF:<raw_type_f_hex>` when lossless valid Type-F exists but ON_TIME does not;
3. `LEGACY` only for migrated data where neither discriminator is available.

Consequences:

- repeated retrieval of the same raw timestamp + same ON_TIME remains one canonical period;
- changed normalized content for that same physical occurrence remains revision/conflict evidence;
- same raw timestamp with a different valid ON_TIME is representable as a distinct physical period;
- legacy v2.0 data is never duplicated merely because richer evidence was unavailable when stored;
- derived UTC is never part of the native unique key.

The first v2.1 schema migration must rebuild the archive-period unique constraint rather than trying
to alter it in place.

## Derived UTC fields

Derived values are additional interpretation, not source evidence. The persistence design may store
or cache fields such as:

- `derived_start_utc_ms`;
- `derived_end_utc_ms`;
- `time_resolution_status`;
- `time_derivation_method`;
- `time_uncertainty_ms`;
- anchor identity/version;
- raw clock-regime consistency result.

They must be safely recomputable from retained raw evidence and anchors. A resolver/model-version
change must never rewrite the original raw logger timestamp or Type-F evidence.

## Per-meter timezone profile

Each physical meter owns an IANA timezone profile, separate from the global Android time-basis
preference. Minimum durable fields:

- meter ID;
- IANA `zone_id`;
- source/provenance, initially `DEVICE_AT_FIRST_VERIFIED_LIVE` or `USER_SELECTED`;
- assigned/changed epoch;
- optional quality state.

Automatic assignment is allowed only after a successful verified Live/default observation of a meter
that has no existing assignment. Later phone timezone changes must not mutate it. Backup/restore must
preserve the saved meter zone.

## Global Android preference

The Android UI preference is global and has exactly two semantic values:

- `LOCAL` — default/recommended;
- `METER`.

It changes rendering and navigator/query projection only. It must not alter raw meter data, derived
UTC values or future Home Assistant timestamps.

## LOCAL navigator contract

LOCAL input is interpreted in the relevant meter's assigned IANA zone and converted to a UTC range.

DST rules are explicit:

- nonexistent spring-forward local times are rejected; do not silently shift them;
- repeated fall-back local times are ambiguous until an explicit valid offset/occurrence is chosen;
- a civil day may therefore cover 23, 24 or 25 real hours.

History uses UTC interval overlap. Statistics exact totals use full containment. No fractional bucket
consumption is interpolated.

## Migration rules for v2.0 data

- preserve every raw logger timestamp exactly;
- parse existing `on_time` display strings into typed seconds only when the parse is unambiguous;
- never invent SU, IV or raw Type-F bytes that were not persisted;
- keep old unresolved occurrence identity as `LEGACY` when richer evidence cannot be recovered;
- existing Live `read_at_ms` remains a valid real UTC observation timestamp;
- old archive rows may be backfilled to canonical UTC after a new verified same-meter Live anchor if
  ON_TIME/raw-clock-regime evidence is sufficiently consistent;
- otherwise retain the data as `UNRESOLVED_TIME` rather than guessing;
- later incremental/full re-sync may enrich old canonical rows with newly observed evidence.

## Backup/export direction

The v2.1 `.qw1backup` schema must preserve meter timezone profiles, verified anchors, typed archive
raw-time evidence and any resolver provenance needed to reconstruct canonical UTC. Restore of older
v2.0 schema must remain supported without inventing missing evidence.

CSV remains human-readable rather than the canonical restore format. Its next schema should expose
raw meter time plus resolved UTC/local interval fields and time quality/provenance where available.

## First implementation gate

Before productive History/Statistics queries switch to LOCAL UTC semantics, CI must prove at least:

- ON_TIME anchor resolution without civil-timezone assumptions;
- raw-clock-regime discontinuity detection;
- ON_TIME reset/discontinuity rejection;
- meter replacement isolation;
- spring DST nonexistent local time handling;
- fall DST repeated-hour disambiguation;
- 23-hour and 25-hour civil-day conversion;
- exact, partial-edge, overlap-only, gap and unresolved coverage classification;
- v2.0 archive schema migration preserving all old data;
- repeated raw timestamp with distinct ON_TIME remains representable;
- backup/restore preserves per-meter IANA timezone and does not replace it from the restore device.
