# W1 NFC Reader v2.1 — real-time timeline implementation

## Status

Current functional code candidate: `b38006f9dab270a574d7e08cb9e3785a231a2ef8` on `dev/v2.1.0-real-time-timeline`.

This document describes the implemented v2.1 contract. The older dual `LOCAL / METER` product proposal is superseded by `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md`.

## Purpose

v2.1 adds a conservative canonical real-time timeline without changing the proven NFC command, mailbox, archive traversal or parser semantics. Raw meter/logger wall-clock values remain lossless source evidence; normal product ordering and presentation use explicit verified timing evidence.

## Canonical normal-product time

The normal Android product UI exposes one canonical time axis: reconstructed real time.

- Live primary time is the actual Android acquisition epoch.
- Archive primary time is reconstructed from the **single newest fully verified Live/default anchor for that physical meter** plus monotonic meter `ON_TIME`.
- Reconstructed UTC is projected through the persisted per-meter IANA zone for civil/local presentation.
- Raw meter/logger wall-clock remains preserved secondary evidence in UI, diagnostics, export and backup where useful.
- The former global `LOCAL / METER` peer selector is retired from normal Settings.
- Old/restored development state that still contains `METER` is normalized to the canonical path before normal product presentation.
- If real-time reconstruction is unsafe or unavailable, the app fails closed rather than silently promoting raw meter wall-clock to canonical time.

The internal `AppTimeBasis.METER` compatibility route still exists temporarily for old development-backup/regression compatibility. It is intentionally scheduled for code cleanup after the v2.1 release rather than being removed during RC hardening.

## Single-active-anchor model

`MeterTimeModelStore` persists per-meter timezone state and the active verified Live/default anchor.

For projection there is exactly one active anchor per physical meter:

- every accepted normal Live/default read may replace the active anchor when its `ON_TIME` is newer;
- the protected final Default/Live verification after each History family also refreshes the active anchor with zero additional NFC commands;
- old databases/backups may physically contain historical anchors, but only the newest active anchor participates in projection/export and older rows are collapsed on subsequent writes/merge;
- a backwards `ON_TIME` never silently replaces the active anchor;
- archive evidence newer in `ON_TIME` than the active anchor fails closed.

Projection is therefore conceptually:

`archive_epoch = active_anchor_epoch - (active_anchor_on_time - archive_on_time)`

The active anchor defines the absolute position of the complete native `ON_TIME` axis. Native `ON_TIME` defines its geometry. A newer verified anchor may shift the whole reconstructed archive timeline, but it must not stretch or compress individual native intervals.

In particular, two valid native Hour boundaries separated by exactly 3,600 `ON_TIME` seconds must remain exactly 3,600 real seconds apart.

## Per-meter timezone

The first successful verified Live/default read may assign Android's current IANA `ZoneId` when the meter has no persisted zone. The assignment is stable and is not silently replaced when the phone later changes timezone.

Meter Details exposes a searchable Material dropdown populated from the device-supported `ZoneId.getAvailableZoneIds()` set. The current zone is preselected; users may type to filter/search; only an exact supported ID may be saved. `MeterTimeModelStore.normalizeZoneId` remains defense-in-depth validation. Fixed numeric offsets are not accepted as durable meter zones.

Changing the timezone changes only civil/local interpretation. It never rewrites raw logger time, `ON_TIME`, occurrence identity, archive payloads or consumption values.

## Archive occurrence identity

Archive identity is occurrence-safe and does not use reconstructed UTC as the native key:

`meter_id + archive_family + raw_logger_timestamp + occurrence_key`

Occurrence-key precedence is:

1. `OT:<on_time_seconds>` when valid `ON_TIME` exists;
2. `TF:<raw_type_f_hex>` when suitable Type-F evidence exists;
3. `LEGACY` only when stronger occurrence evidence is unavailable.

This keeps repeated raw wall-clock timestamps distinct at DST fall-back and protects identity independently of later improvements to UTC reconstruction.

## Home Assistant / external source identity

`SourceRecordId` provides the deterministic integration identity primitive:

`w1:v1:<sha256>`

The SHA-256 input is a canonical length-prefixed sequence derived only from meter-native occurrence identity:

- physical meter identity;
- archive family;
- preserved raw logger timestamp;
- preserved occurrence key, normally `OT:<on_time_seconds>`.

The ID deliberately excludes:

- reconstructed UTC/local time;
- Android/phone/install identity;
- database row IDs;
- retrieval timestamps;
- measurement content.

Therefore a fresh phone that rereads the same physical archive occurrence regenerates the same `source_record_id`, while a better/newer time anchor does not create a duplicate identity.

Android must not maintain an authoritative "already sent" ledger for Home Assistant. The future transport may send all eligible locally available records on every transfer. Home Assistant is the idempotence authority and must apply unique-key / UPSERT / ignore-known-ID semantics on `source_record_id`.

If future real-device evidence proves that one unchanged physical meter can reuse both the same `ON_TIME` and the same raw logger occurrence after a true reset, an explicit meter-generation discriminator must be introduced. That discriminator must not be phone-local state.

## UTC projection and fail-closed rules

`ArchiveUtcProjection` / `ArchiveUtcRepository` derive real intervals without mutating raw archive rows.

Key rules:

- one active verified anchor is used for the whole stored meter timeline;
- raw meter/logger clock alignment is diagnostic evidence only;
- unresolved evidence stays unresolved;
- archive evidence newer than the active anchor is not extrapolated;
- native adjacency is determined from native occurrence/`ON_TIME` evidence;
- genuine missing native records remain gaps and are never compressed;
- meter replacement or real `ON_TIME` reset is never bridged by guessing.

## History and Statistics routing

`HistoryStatisticsRepository` remains the central query router.

For the canonical product path:

- archive History/Statistics uses the resolved UTC read model;
- Live rows retain actual Android acquisition epoch;
- chronology and predecessors use the canonical real timeline;
- raw meter/logger time remains available as secondary evidence;
- records are never shifted to another physical occurrence merely to fit civil labels.

The mixed History → All Live delta uses the chronologically nearest trustworthy earlier archive cumulative reading from the same physical meter, regardless of Hour/Day/Month family. If none exists it may fall back to the previous valid Live reading. It never crosses meter replacement, identity conflict or unsafe chronology.

## DST-aware statistics and edge intervals

Hourly expected-bucket counts are resolved from the meter's IANA zone rather than assuming every civil day contains 24 hours:

- normal day: 24;
- spring-forward day: 23;
- fall-back day: 25.

Physical archive intervals that only overlap a selected civil-window edge are shown as context but are not prorated. Fully contained intervals alone contribute to exact selected-window KPIs. Real zero-consumption buckets remain visible; genuine gaps remain gaps.

The shared chart view prevents unreadable X-axis text: labels are shown only while they can be presented without overlap. Bar charts use the shared horizontal → rotated → hidden policy; dense line-chart labels are thinned/hidden based on available width. Data points/bars are never dropped merely to make labels fit.

## Presentation

- Overview Live: real acquisition time primary, raw meter time secondary when available.
- History Live: real acquisition time primary, raw meter time secondary.
- Archive History: reconstructed real/civil interval primary, raw logger/meter time secondary.
- Ordinary date/time presentation uses the centralized `date · time` convention.
- Ordinary timezone abbreviations are locale-aware where trustworthy; ambiguous DST folds retain explicit offset disambiguation.
- History and Statistics each expose one page-level contextual info affordance; generic explanatory prose is not duplicated throughout the page.

## Portability

Schema-3 `.qw1backup` preserves:

- per-meter timezone profiles;
- the active verified Live/default anchor per physical meter;
- occurrence-safe archive evidence;
- raw Live meter time;
- legacy time-basis state only for backward compatibility with development backups.

Normal product startup normalizes old/restored `METER` state to the canonical path. Schema-2 restore remains supported and does not fabricate timezone, Type-F, IV/SU or anchor evidence.

## Public supporting evidence

Axioma Metering's public Qalcosonic W1 manual `QW1_V22.4_EN` dated 2026-05-11 documents total operating time and operating time without error among parameters retained in Hour, Day and Month archives. This is consistent with independently observed `ON_TIME` evidence. It does not by itself prove the application's UTC reconstruction formula; that remains based on repository and real-device evidence plus verified Live/default anchors.

The same manual lists nominal capacities of up to 1480 Hour, 1130 Day and 36 Month records. These are informative only and are **not** synchronization/traversal limits. Semantic terminal handling and secure known-overlap termination remain authoritative.

See `docs/research/PUBLIC_QW1_EVIDENCE.md`.

## Current verified candidate

Functional code candidate:

- commit: `b38006f9dab270a574d7e08cb9e3785a231a2ef8`
- Android CI: `34715215357` — **SUCCESS**
- unit/Robolectric suite: **SUCCESS**
- product translation check across 6 locales: **SUCCESS**
- debug APK build/signature/hash/artifact upload: **SUCCESS**
- artifact id: `10304503134`
- artifact ZIP SHA-256: `cc48ffd4548fcff663583c1d722309b36584b4285de66ad24a3b4cfc1a367059`
- APK SHA-256: `4488e1b1d500cf52ff03cc4ab294a9152e5623a111133533e3cd3ef4003b1686`

This candidate contains the single-active-anchor fix, deterministic `SourceRecordId`, and centralized chart-axis collision handling.

## Safety boundary

The v2.1 work remains downstream of the protected meter protocol path. It introduces no new NFC command and does not alter protected mailbox/archive traversal semantics, terminal/overlap rules, `QalcosonicReader.java` or `MbusParser.java`.

Normal NFC contact remains the fast Live/default read. History synchronization remains explicit user action only.

## Remaining release gates

Before v2.1 release:

1. perform one final normal protected Live/default NFC read on the current candidate (Section F);
2. prepare v2.1.0 version metadata, changelog/release notes and the exact signed RC;
3. physically accept that exact signed RC, including fresh/default-state smoke as appropriate;
4. only then move PR #22 out of Draft, merge, tag and release.

The former invalid-IANA free-text test is retired because the normal UI now uses a constrained searchable runtime-supported timezone picker. Unsupported typed filter text may exist transiently, but cannot be persisted.

## Deferred v2.2 cleanup

Do not widen the v2.1 RC regression surface merely to remove compatibility scaffolding. After v2.1 release, v2.2 should perform the dedicated code cleanup:

- remove the hidden `AppTimeBasis.METER` runtime route and peer-mode branches;
- retain only minimal import compatibility needed to read old development backup state and normalize it to canonical time;
- remove obsolete `v2_time_basis_strings.xml` resources and tests that exist solely for the retired peer mode;
- review other now-redundant compatibility helpers only after proving they are no longer required by public backup/migration contracts.

Physically validated NFC/parser/traversal regression tests are not cleanup targets merely because they are old.
