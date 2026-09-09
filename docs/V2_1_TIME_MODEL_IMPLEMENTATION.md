# W1 NFC Reader v2.1 — real-time timeline implementation

## Purpose

v2.1 introduces a conservative real-time timeline without changing the proven NFC command, mailbox, archive traversal or parser semantics. Raw meter/logger wall-clock values remain evidence; canonical event ordering and LOCAL presentation are derived only from persisted, explicit timing evidence.

## Time bases

The application exposes one global presentation/query preference:

- `LOCAL` — default. Archive periods are selected and ordered on the resolved UTC timeline and presented in the persisted per-meter IANA zone.
- `METER` — compatibility path. Existing floating/raw meter/logger wall-clock semantics remain available unchanged.

Changing this preference never rewrites stored archive measurements or raw meter timestamps.

## Per-meter time model

`MeterTimeModelStore` persists, per meter:

- IANA `ZoneId` and assignment provenance;
- original assignment timestamp and last update timestamp;
- verified Live acquisition anchors with Android before/after epoch bounds and uncertainty;
- anchor validation/provenance metadata.

The first automatic zone assignment is stable: a later phone timezone change cannot silently replace an already assigned meter zone. Manual changes use the explicit `USER_SELECTED` provenance.

## Archive occurrence identity

Archive identity is occurrence-safe and does not use derived UTC as the native key. The persisted identity is based on:

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
- raw meter/logger clock alignment is diagnostic evidence only; a small stable wall-clock origin offset is not allowed to rewrite canonical UTC.
- unresolved evidence stays unresolved; the implementation does not invent UTC, offsets or timezone information.

## LOCAL range semantics

LOCAL navigator input is interpreted in the meter-assigned IANA zone and converted to canonical UTC boundaries. DST transitions are explicit:

- nonexistent local times are rejected;
- repeated local times require disambiguation where an exact boundary would otherwise be ambiguous;
- selection and ordering operate on UTC occurrences, not formatted wall time.

Coverage distinguishes exact, partial-edge, overlap-only, gap and unresolved-time states.

## History and Statistics routing

`HistoryStatisticsRepository` is the single query router.

- In `METER`, the previous SQL/floating-time path remains active.
- In `LOCAL`, archive queries delegate to the resolved UTC read model while Live rows retain their real device epoch.
- The LOCAL adapter uses an internal raw-only repository to avoid recursion and keeps measurement mapping single-sourced.
- Deltas receive the occurrence-safe real predecessor as context.

The same repository instance reacts dynamically to a changed global time-basis preference.

## DST-aware Statistics completeness

Hourly Statistics no longer assume every civil day contains 24 real hours in LOCAL mode.

`HistoryLocalBucketExpectation` resolves the selected local calendar boundaries through the persisted meter zone and counts only complete real hourly intervals:

- normal civil day: 24;
- spring-forward day: 23;
- fall-back day: 25.

If a participating meter has no persisted zone, a boundary is unsafe/ambiguous, or participating meter zones produce inconsistent expectations, the LOCAL expected-bucket count fails closed to count-only coverage instead of guessing 24.

`METER` retains the previous floating-time expected-bucket semantics.

The same expected-bucket value drives chart coverage and the Statistics KPI decision between total and known-total, preventing competing completeness definitions.

## Presentation

- Settings exposes the global `Local time / Meter time` switch; `LOCAL` is the default.
- History/Statistics rerenders after returning from Settings only when the time basis actually changed.
- LOCAL History cards retain the raw meter/logger time as secondary evidence rather than hiding it.
- Ambiguous DST periods expose offsets only when needed for disambiguation; ordinary rows avoid unnecessary UTC-offset noise.

## Portability

Schema-3 `.qw1backup` preserves:

- per-meter timezone profiles;
- verified Live anchors;
- occurrence-safe archive envelope;
- global time-basis preference.

Schema-2 restore remains supported and does not fabricate missing zone, Type-F, IV/SU or anchor evidence. Restoring on a device in another timezone preserves the backed-up meter timezone rather than adopting the current phone timezone.

## Current verification checkpoint

Android CI run `34313028533` is fully green for commit `ceec923ce90804689d350c4ff92ddcf7a4cf2b1d`:

- product translation contract: success;
- 332 unit tests: success;
- debug APK build: success;
- APK signature verification: success;
- APK SHA-256 recording: success;
- artifact upload: success.

Artifact `w1-nfc-reader-debug` has digest:

`sha256:6aa2c2c3b1afcc1272209f593639a2e66dc7383a281e76aab783bf4c72657262`

## Safety boundary

The timeline implementation is passive with respect to the meter protocol. It does not introduce new NFC commands, alter mailbox behavior, change archive traversal semantics, or reinterpret protected parser fields. Time-model persistence, UTC projection, query routing and presentation are downstream of the existing acquisition path.

## Remaining implementation gates

1. expose the persisted per-meter IANA timezone and provenance in Meter Details and allow an explicit, validated user correction without changing raw meter data;
2. make missing/unresolved meter-zone state clearly visible where LOCAL presentation cannot be resolved;
3. perform limited real-device validation of the complete v2.1 time-model/query/UI path;
4. update release/handoff documentation and complete release hardening.
