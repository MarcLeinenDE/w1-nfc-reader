# W1 NFC Reader 2.0 — History synchronization architecture

Status: implementation contract for the `2.0.0-dev` development line.

This document defines the public product architecture for transferring the validated archive behavior into W1 NFC Reader 2.0. It is intentionally implementation-focused and contains no private meter identifiers or raw field captures.

## Product scope

Primary History synchronization families in 2.0:

- Hour — selector `50 60`;
- Day — selector `50 30`;
- Month — selector `50 40`.

Year (`50 20`) is represented by the family policy/data model but is not a required primary History synchronization control for the first 2.0 release. It may later be used for annual/statistics/report features.

A normal NFC presentation remains a fast protected Live/default read only. Archive work starts only after explicit user intent.

## Settings UX target

History synchronization moves to a dedicated Settings subpage rather than being a normal dashboard NFC action.

Target actions:

- synchronize Hours;
- synchronize Days;
- synchronize Months;
- synchronize All.

`Synchronize all` is only a convenience sequencer. It is not a fourth archive mode and it never owns canonical completeness state. It chains independent family sessions, and a failure in one family does not invalidate another family that already completed safely.

A separate advanced Full Re-Sync remains available for intentionally rebuilding/reconfirming the complete exposed archive.

## Independent family state

Canonical persistent synchronization truth is scoped by:

`meter_id + archive_family`

Each family tracks separately:

- baseline: never synced / partial / complete;
- latest attempt outcome;
- initial full / incremental / full re-sync mode;
- oldest/newest raw logger timestamp;
- last attempt / last successful attempt;
- baseline completion time;
- last stop reason;
- incremental anchor;
- useful persistence counters;
- final restore verification;
- recovery recommendation.

Baseline completeness and latest attempt outcome are deliberately separate. A later failed incremental update must not erase a previously completed baseline.

The v1 whole-History `COMPLETE` metadata is not authoritative for v2 family completeness.

## Mandatory per-family safety shell

Every family session begins from an unknown W1 application state, including the first family after a fresh physical NFC contact.

Required sequence:

1. prepare the volatile NFC/ST25 mailbox transport;
2. explicitly send `APPLICATION_RESET_DEFAULT`;
3. allow a short stabilization interval;
4. perform a protected Live/default read;
5. verify the intended meter identity;
6. require valid Type-F evidence, `IV=false` and typed `ON_TIME`;
7. capture the Android acquisition epoch as the real-world execution anchor;
8. calculate this family's next boundary from the verified **raw meter clock**;
9. start only when conservative family runtime + reserve fits before the boundary;
10. select and traverse only the requested archive family;
11. persist validated periods independently as they are accepted;
12. restore the default application;
13. perform a final protected Live/default read and verify same-meter/time continuity;
14. only then permit a successful family attempt to be recorded `COMPLETE`.

Physical phone removal/recontact and logical NFC-V close/connect do not by themselves establish default meter application state.

## Family boundary safety

Family boundaries are calculated from the post-reset verified raw meter time:

- Hour -> next raw-meter full hour;
- Day -> next raw-meter midnight;
- Month -> next raw-meter month start;
- optional Year -> next raw-meter January 1.

The raw meter clock is used because that is the clock that creates logger periods. A derived real/civil time must never be substituted for the logger-boundary decision.

The Android epoch captured with the verified Live read anchors how much real execution time remains before that raw boundary.

Product rule:

`conservative measured family runtime + safety reserve < time until family boundary`

Temporary Research minute thresholds are not product constants. A second in-run guard remains active so a slower-than-expected traversal stops incomplete before the boundary.

## Selected-request progression

Selected archive requests alternate exactly:

`FCB1 7B -> FCB0 5B -> FCB1 7B -> FCB0 5B -> ...`

An ambiguous selected request is never blindly retried. An I/O/transport uncertainty ends that attempt protectively so FCB progression cannot silently desynchronize.

There is no normal record-count completion cap.

## Traversal integrity

Every accepted archive period requires at least:

- structurally valid M-Bus frame;
- complete/accepted archive decoding;
- family matches the selected family;
- raw logger timestamp present;
- stable within-session structural evidence;
- Type-F present;
- Type-F `IV=false`;
- typed non-negative ON_TIME;
- exact family-native backward timestamp progression;
- matching ON_TIME progression.

Unknown/reserved meter fields remain opaque rather than receiving invented semantics.

## Successful termination

Successful initial/full re-sync traversal may end only by:

- `PROTOCOL_TERMINAL`;
- strongly confirmed `RING_WRAP_DETECTED`.

Successful later incremental traversal may additionally end by:

- `KNOWN_RECORD_REACHED`.

A fixed request/record count is never a successful end.

A technical watchdog is mandatory as a failsafe but always yields partial/incomplete state if it fires.

## Head advance versus ring wrap

A candidate exactly one family period newer than the session's initial head with coherent ON_TIME is:

`HEAD_ADVANCED_DURING_TRAVERSAL`

and is not ring proof.

A ring is accepted only after:

1. clean backward progression has already been established;
2. a healthy candidate returns to the exact initial session head identity (raw timestamp + ON_TIME + structural identity);
3. one additional selected request resumes the expected backward progression from that returned head.

Only then is the stop `RING_WRAP_DETECTED`.

## Incremental synchronization

The first successful synchronization of a family establishes a complete baseline by reaching a supported full end.

Later normal synchronization starts at the current archive head, walks through new observations and stops after a conservative securely-known overlap.

The product matcher must not treat one matching timestamp as sufficient evidence. A known overlap must compare enough stable identity/content/ON_TIME/provenance information to exclude a silent gap or clock discontinuity.

The generic v2 traversal contract therefore requires at least two consecutive securely-known observations. The exact final product overlap depth may be increased if product validation shows that a larger value is warranted.

## Partial persistence and recovery

Validated periods are useful even if a later request, restore, persistence step or NFC session fails.

Already committed records are never rolled back merely because the family attempt did not reach complete status.

A partial attempt records its explicit stop reason and remains recoverable. Later synchronization can continue/reconfirm the family according to the baseline state and known archive data.

## Raw meter time and real time

The meter clock and real-world time are separate concepts.

Preserve where available:

- raw logger timestamp;
- raw Type-F bytes;
- SU flag;
- IV flag;
- typed ON_TIME;
- phone acquisition epoch;
- Live anchor ON_TIME/raw meter time;
- clock-regime/origin evidence;
- clock-model state/version/confidence;
- derived absolute epoch/UTC time and uncertainty/provenance.

Do not encode a universal W1 timezone or DST rule.

The preferred archive real-time mapping when the per-meter model is sufficiently confident is:

`archive_epoch_ms = phone_anchor_epoch_ms - (live_ON_TIME_seconds - archive_ON_TIME_seconds) * 1000`

The normal UI may render the derived absolute instant in the user's timezone. Raw meter time remains source evidence and native archive identity.

Boundary safety continues to use raw meter time, not the derived UI time.

## Stable archive identity

Native archive identity remains:

`meter_id + archive_family + raw_logger_timestamp`

Do not deduplicate Hour/Day/Month/Year simply because their raw boundary times or cumulative values coincide.

Repeated identical observations are confirmations. Same stable identity with changed content is revision/conflict evidence and must not be silently overwritten.

## Protected protocol boundary

The v2 architecture is designed to avoid modifying `QalcosonicReader` and `MbusParser` merely for new History/time features.

Raw Type-F/ON_TIME evidence is extracted by an additional read-only evidence component. Family traversal logic is a pure state machine. NFC transport changes remain small, targeted and subject to regression plus real-device validation.

No persistent meter/radio/calibration/firmware write is authorized by this architecture.

## Validation gates

Before stable 2.0 release, off-meter CI must cover at least:

- exact family select frames;
- FCB alternation and no ambiguous retry;
- no fixed-count completion;
- Hour/Day/Month native progression;
- Type-F/IV/ON_TIME requirements;
- terminal/ring/head-advance separation;
- family boundary arithmetic;
- incremental known-overlap behavior;
- partial-success persistence;
- per-family state independence;
- time/provenance storage and mapping;
- DB/backup/export schema behavior;
- localized UI/state strings.

Final product code then requires limited real-device validation for:

1. initial integrated Month/Day/Hour synchronization;
2. incremental known-overlap behavior;
3. interrupted synchronization/resume with already accepted records retained;
4. default restore/final Live verification;
5. final History/Statistics/export/backup presentation.
