# W1 NFC Reader v2.1 — real-time timeline implementation

## Purpose

v2.1 introduces a conservative real-time timeline without changing the proven NFC command, mailbox, archive traversal or parser semantics. Raw meter/logger wall-clock values remain evidence; canonical event ordering and LOCAL presentation are derived only from persisted, explicit timing evidence.

## Time bases

The Android app exposes one global presentation/query preference:

- `LOCAL` — default. Real/local time is primary. Archive periods are selected and ordered on the resolved UTC timeline and presented in the persisted per-meter IANA zone. Live selection/order uses the actual Android acquisition epoch. Raw meter time remains secondary evidence.
- `METER` — raw meter/logger wall-clock time is primary. Bounded Live History selection/order uses persisted `meter_time`; archive selection retains the established raw/floating path. Trustworthy real/local time is shown as secondary evidence where available.

Changing this preference never rewrites stored archive measurements, raw meter timestamps or the canonical integration-facing UTC contract. Missing meter time is not replaced by guessed Android time in bounded METER Live queries.

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

### Public supporting evidence for operating-time fields

Axioma Metering's public Qalcosonic W1 manual `QW1_V22.4_EN` dated 2026-05-11 documents **total operating time** and **operating time without error** among the parameters retained in Hour, Day and Month archives.

Public mirror of the Axioma document:
https://device.report/m/d4a0014787f6accc0035156bf0096d94be5bb5429de79aded8d0422b322e7436

This is consistent with the application's independently observed `ON_TIME` evidence and supports retaining operating time as first-class timing evidence. It does **not** prove the application's UTC reconstruction formula or timezone model; those remain derived from repository/real-device evidence and verified Live anchors.

The same manual lists nominal capacities of up to 1480 Hour, 1130 Day and 36 Month records. Those values are informative only and are **not synchronization/traversal limits**. Existing semantic terminal handling and secure known-overlap termination remain authoritative.

See `docs/research/PUBLIC_QW1_EVIDENCE.md` for source interpretation and explicit non-implications.

## LOCAL range semantics

LOCAL navigator input is interpreted in the meter-assigned IANA zone and converted to canonical UTC boundaries. DST transitions are explicit:

- nonexistent local times are rejected;
- repeated local times are not guessed;
- archive selection and ordering operate on UTC occurrences, not formatted wall time;
- Live selection/order operates on the actual Android acquisition epoch.

Coverage distinguishes `EXACT`, `PARTIAL_EDGES`, `OVERLAP_ONLY`, `GAP` and `UNRESOLVED_TIME`.

## History and Statistics routing

`HistoryStatisticsRepository` is the single query router.

- In `METER`, archive queries retain the previous SQL/floating-time path.
- In `METER`, Live History uses persisted raw `meter_time` for bounded range selection, predecessor lookup and ordering.
- In `LOCAL`, archive queries delegate to the resolved UTC read model while Live rows retain their real device epoch.
- The LOCAL adapter uses an internal raw-only repository to avoid recursion and keeps measurement mapping single-sourced.
- Deltas receive a predecessor from the same selected time basis.
- The same repository instance reacts dynamically to a changed global time-basis preference.
- For METER archive cards, a passive secondary projection provides trustworthy resolved local time without changing the raw METER query semantics.

## DST-aware Statistics completeness

Hourly Statistics no longer assume every civil day contains 24 real hours in LOCAL mode. `HistoryLocalBucketExpectation` resolves selected local calendar boundaries through the persisted meter zone and counts only complete real hourly intervals:

- normal civil day: 24;
- spring-forward day: 23;
- fall-back day: 25.

If participating archive evidence has no persisted zone, a boundary is unsafe/ambiguous, or participating zones produce inconsistent expectations, the expected-bucket count fails closed instead of inventing 24. `METER` retains the previous floating-time expectation semantics. Chart coverage and KPI total/known-total decisions use the same expected-bucket definition.

## Presentation and failure transparency

- Settings exposes the global `Local time / Meter time` switch; `LOCAL` is the default.
- Overview, Live History and archive History now honor the same global time-basis contract.
- LOCAL Overview/Live History use real acquisition time as primary and raw meter time as secondary when available.
- METER Overview/Live History use raw meter time as primary and real/local acquisition time as secondary.
- METER archive History keeps raw meter/logger time primary and shows trustworthy resolved local time as secondary evidence when available.
- History/Statistics rerenders after returning from Settings when the time basis changed; Overview refreshes on resume and therefore reflects the same setting without requiring an app restart.
- Raw Live meter time is always formatted for the active locale before display. Storage text such as `yyyy-MM-dd HH:mm` remains unchanged in persistence/backup but is not exposed directly as product date formatting. Meter Details uses the same locale-aware formatter.
- Meter Details exposes per-meter IANA zone and provenance and permits validated manual correction.
- LOCAL History/Statistics distinguish and explicitly explain:
  - missing per-meter timezone;
  - unsafe/ambiguous/nonexistent LOCAL range boundaries;
  - archive periods whose timing evidence is insufficient for a safe LOCAL projection.
- Meters without archive evidence for the currently selected granularity do not create false missing-zone warnings.
- Raw data remains accessible through `METER` when LOCAL projection is not trustworthy.

## Portability

Schema-3 `.qw1backup` preserves:

- per-meter timezone profiles;
- verified Live anchors;
- occurrence-safe archive envelope;
- raw Live meter time already stored with successful Live readings;
- global time-basis preference.

Schema-2 restore remains supported and does not fabricate missing zone, Type-F, IV/SU or anchor evidence. Restoring on a device in another timezone preserves the backed-up meter timezone rather than adopting the receiving phone timezone.

## Verification checkpoints

### Time-basis / DST checkpoint

Android CI run `34313028533` was fully green for commit `ceec923ce90804689d350c4ff92ddcf7a4cf2b1d`, including translations, unit tests, debug APK build, signature verification, SHA recording and artifact upload.

### Per-meter timezone UI checkpoint

Android CI run `34313630120` was fully green for commit `3f134673b636c7464a6e0f3a9a2596b05867de16`.

### LOCAL resolution transparency checkpoint

Android CI run `34316796172` was fully green for commit `2191147a96db59b2857af7526fa07826bffc8967`. That APK is superseded as a physical candidate because the later product-contract audit found incomplete Live METER presentation/query routing.

### Completed Live LOCAL/METER + locale checkpoint — pinned physical candidate

Android CI run `34320334896` is fully green for functional commit `e4f458d9ff47a088926772a13d9bf19946f4bc48`:

- product translation contract: success — 227 translatable keys across 6 locales;
- 346 unit/Robolectric tests: success;
- debug APK build: success;
- APK signature verification: success;
- APK SHA-256 recording: success;
- artifact upload: success.

Artifact:
- name: `w1-nfc-reader-debug`
- artifact id: `10091656517`
- ZIP digest: `sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775`
- independently recalculated APK SHA-256: `44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a`

This is the candidate to use for the limited real-device gate in `docs/V2_1_REAL_DEVICE_VALIDATION.md` unless a later functional change explicitly supersedes it.

## Safety boundary

The timeline implementation is passive with respect to the meter protocol. It does not introduce new NFC commands, alter mailbox behavior, change archive traversal semantics, or reinterpret protected parser fields. `QalcosonicReader.java`, `MbusParser.java` and the physically validated NFC/mailbox/archive traversal paths remain protected.

Current Axioma documentation independently supports the read-focused NFC boundary, and a public 2020 QW1 FCC-era parts list documents an ST25DV04K Fast Transfer Mode device in that historical hardware revision. These public references are supporting evidence only and do not create hardware-specific application assumptions. See `docs/research/PUBLIC_QW1_EVIDENCE.md`.

## Remaining release gates

1. perform limited real-device validation of the complete v2.1 time-model/query/UI path using the pinned candidate in `docs/V2_1_REAL_DEVICE_VALIDATION.md`;
2. after physical acceptance, advance the stable release identity/version metadata and curated changelog/release notes;
3. run the normal signed release-candidate/release workflow and preserve the existing immutable v2.0.0 release/tag.

Do not mark PR #22 ready or merge it solely from CI evidence. Real-device evidence remains a release gate.
