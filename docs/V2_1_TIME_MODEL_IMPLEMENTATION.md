# W1 NFC Reader v2.1 — real-time timeline implementation

## Purpose

v2.1 introduces a conservative real-time timeline without changing the proven NFC command, mailbox, archive traversal or parser semantics. Raw meter/logger wall-clock values remain evidence; canonical event ordering and normal product presentation are derived only from persisted, explicit timing evidence.

## Canonical normal-product time

The normal Android product UI exposes one canonical time axis: reconstructed real time.

- Live primary time is the actual Android acquisition epoch.
- Archive primary time is reconstructed from verified Live/default anchors plus monotonic meter `ON_TIME`, producing canonical UTC boundaries that are then projected through the persisted per-meter IANA zone.
- Raw meter/logger wall-clock time remains preserved source evidence and may be displayed secondarily, in diagnostics, export and backup.
- The former global `LOCAL / METER` peer selector is retired from normal Settings. Old/restored development state that still carries `METER` is normalized back to `LOCAL` when a normal product Activity starts.
- If real-time reconstruction is unsafe or unavailable, the app fails closed rather than silently promoting the drifting raw meter clock to canonical time.

The internal `AppTimeBasis.METER` compatibility path may remain temporarily for regression/backward-compatibility coverage during v2.1 hardening, but it is no longer a normal-product user choice.

See `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md`.

## Per-meter time model

`MeterTimeModelStore` persists, per meter:

- IANA `ZoneId` and assignment provenance;
- original assignment timestamp and last update timestamp;
- verified Live acquisition anchors with Android before/after epoch bounds and uncertainty;
- anchor validation/provenance metadata.

The first automatic zone assignment is stable: a later phone timezone change cannot silently replace an already assigned meter zone. Meter Details exposes the persisted zone and provenance and permits an explicit validated user correction. Manual changes use `USER_SELECTED`, preserve the original assignment time and never rewrite raw meter/archive data. Fixed numeric offsets remain rejected as durable meter zones.

## Archive occurrence identity

Archive identity is occurrence-safe and does not use derived UTC as the native key:

`meter_id + archive_family + raw_logger_timestamp + occurrence_key`

Occurrence-key precedence is:

1. `OT:<on_time_seconds>` when ON_TIME is unambiguously available;
2. `TF:<raw_type_f_hex>` when suitable Type-F evidence exists;
3. `LEGACY` only when stronger occurrence evidence is unavailable.

This permits two physical archive periods to keep the same raw wall-clock timestamp during a DST fall-back while remaining distinct records.

## UTC projection

`ArchiveUtcProjection` / `ArchiveUtcRepository` derive real intervals from stored evidence without mutating raw archive rows.

Key rules:

- ON_TIME progression is preferred for occurrence ordering and UTC reconstruction when supported by verified anchors.
- raw meter/logger clock alignment is diagnostic evidence only; a stable wall-clock origin offset is not allowed to rewrite canonical UTC.
- unresolved evidence stays unresolved; the implementation does not invent UTC, offsets or timezone information.
- an archive boundary newer in ON_TIME than every available verified anchor is not extrapolated from an older anchor.
- native archive adjacency is validated from native ON_TIME evidence before derived UTC boundaries are joined; small origin differences between independent valid anchors do not invalidate an otherwise native-adjacent bucket.
- missing native records remain explicit `NATIVE_GAP` coverage and are never compressed.

## LOCAL range semantics

LOCAL navigator input is interpreted in the meter-assigned IANA zone and converted to canonical UTC boundaries. DST transitions are explicit:

- nonexistent local times are rejected;
- repeated local times are not guessed;
- archive selection and ordering operate on UTC occurrences, not formatted wall time;
- Live selection/order operates on the actual Android acquisition epoch.

Coverage distinguishes `EXACT`, `PARTIAL_EDGES`, `OVERLAP_ONLY`, `GAP` and `UNRESOLVED_TIME`.

The natural open prefix at the oldest retained archive boundary is expected retention geometry and does not by itself constitute time ambiguity. An unresolved boundary inside the retained archive series remains fail-closed.

## History and Statistics routing

`HistoryStatisticsRepository` is the single query router.

For the canonical product path:

- archive History/Statistics delegates to the resolved UTC read model;
- Live rows retain their real device epoch;
- deltas receive a predecessor from the same canonical chronology;
- raw meter/logger wall-clock remains available as secondary evidence;
- values are never shifted to a different physical archive occurrence merely to fit civil labels.

The internal METER compatibility route remains for regression/backward-compatibility coverage but is not exposed as an equal normal-product setting.

## DST-aware Statistics completeness

Hourly Statistics do not assume every civil day contains 24 real hours. `HistoryLocalBucketExpectation` resolves selected local calendar boundaries through the persisted meter zone and counts only complete real hourly intervals:

- normal civil day: 24;
- spring-forward day: 23;
- fall-back day: 25.

If participating archive evidence has no persisted zone, a boundary is unsafe/ambiguous, or participating zones produce inconsistent expectations, the expected-bucket count fails closed instead of inventing 24.

Physical archive intervals can also cut the edges of a selected civil window. Edge intervals that are only partly contained are not prorated because the app must not fabricate fractional consumption. Thus a normal day can legitimately show e.g. `22/24` fully-contained Hour buckets even when the stored archive evidence is complete for the physical sequence.

## Presentation and failure transparency

- Overview uses actual real acquisition time primary for Live and raw meter time secondary when available.
- Archive History uses reconstructed real/civil intervals primary and raw meter/logger time secondary.
- Raw Live meter time is locale-formatted before display. Storage text such as `yyyy-MM-dd HH:mm` remains unchanged in persistence/backup.
- Meter Details exposes the per-meter IANA zone/provenance and validated manual correction.
- LOCAL History/Statistics distinguish missing zone, unsafe window boundaries and genuinely unresolved archive timing.
- Empty/out-of-range archive families and normal known coverage gaps do not create false time-resolution warnings.
- The natural oldest retained archive boundary with no stored predecessor does not create a false all-period warning.
- Raw data remains preserved even when canonical presentation cannot be reconstructed safely.

## Verified History anchors

Each History family attempt already ends with a protected Default/Live verification. When that final verification is valid, the same already-read evidence is persisted as a fresh verified time anchor with **zero additional NFC commands/reads**.

This applies to Hour, Day, Month and each family inside Sync All / Full Re-Sync, including partial archive attempts whose final protected Default/Live verification itself is valid.

## Portability

Schema-3 `.qw1backup` preserves:

- per-meter timezone profiles;
- verified Live anchors;
- occurrence-safe archive envelope;
- raw Live meter time already stored with successful Live readings;
- the legacy time-basis preference for compatibility with development backups.

Normal product Activity startup now normalizes any old/restored `METER` preference to the canonical real-time path. Schema-2 restore remains supported and does not fabricate missing zone, Type-F, IV/SU or anchor evidence.

## Public supporting evidence

Axioma Metering's public Qalcosonic W1 manual `QW1_V22.4_EN` dated 2026-05-11 documents total operating time and operating time without error among parameters retained in Hour, Day and Month archives. This is consistent with independently observed ON_TIME evidence. It does not by itself prove the application's UTC reconstruction formula; that remains based on repository and real-device evidence plus verified Live anchors.

The same manual lists nominal capacities of up to 1480 Hour, 1130 Day and 36 Month records. Those are informative only and are **not synchronization/traversal limits**. Existing semantic terminal handling and secure known-overlap termination remain authoritative.

See `docs/research/PUBLIC_QW1_EVIDENCE.md`.

## Current verification checkpoint

Current functional head:

- commit `205720b5bb70cd8d0436d43a5de805aa0dc72b33`
- Android CI `34693507298`: **SUCCESS**
- product translation contract: 227 translatable keys across 6 locales: **SUCCESS**
- complete unit/Robolectric suite: **SUCCESS**
- debug APK build/signature/hash/artifact upload: **SUCCESS**
- artifact id `10298341211`
- artifact ZIP SHA-256 `65ffcad2c5a29209d472fb3757fafe9931f8488127901cca232e5f7d4e38412a`
- APK SHA-256 `122965a45956013b44df4e8dde6cacfac5c573cf0bdfa38cf190a384d9555a86`

The downloaded artifact was independently re-hashed; ZIP digest matches GitHub and the APK matches `SHA256SUMS.txt`.

## Safety boundary

The timeline implementation is passive with respect to the meter protocol. It introduces no new NFC command, does not alter mailbox behavior, archive traversal semantics, terminal/overlap rules or protected parser behavior. `QalcosonicReader.java`, `MbusParser.java` and the physically validated NFC/mailbox/archive traversal paths remain protected.

## Remaining release gates

1. physically confirm the revised canonical-real-time Section D in `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md` using the exact current candidate;
2. pass invalid-IANA-zone smoke test (E);
3. pass final normal protected Live NFC regression (F);
4. implement the already-recorded UI/i18n/accessibility polish pass;
5. prepare version metadata/changelog/release notes and the exact signed v2.1.0 release candidate;
6. physically accept that exact signed candidate before PR #22 leaves Draft / merge / tag / release.
