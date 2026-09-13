# W1 NFC Reader v2.1 — canonical real-time validation addendum

Date: 2026-09-13
Status: current; supersedes the old dual `LOCAL ↔ METER` validation in Section D of `docs/V2_1_REAL_DEVICE_VALIDATION.md`

The accepted product decision `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` defines one canonical reconstructed real-time product timeline. Raw meter/logger wall-clock remains secondary evidence and is not a peer normal-product mode.

## Revised Section D — canonical real timeline + raw evidence

Use the current v2.1 candidate.

1. Open Settings and confirm the former global `Local time / Meter time` selector is absent.
2. Open Overview and confirm actual/reconstructed real local time is primary while raw meter time remains secondary evidence when available.
3. Open History → Live and confirm actual acquisition time is primary while raw meter time remains secondary.
4. Open History → Hour, Day and Month and confirm reconstructed real/civil intervals are primary while raw logger/meter time remains secondary evidence.
5. Confirm the raw clock may differ from reconstructed real time without changing archive identity or measurement values.
6. Confirm old/restored development state that carried `METER` returns to the canonical product timeline on normal Activity startup.
7. Confirm navigating Settings/History/Statistics does not start History synchronization.

Expected:

- exactly one normal-product timeline;
- archive primary time comes from the active verified Live/default anchor + native `ON_TIME` reconstruction + persisted IANA zone;
- Live primary time comes from actual acquisition epoch;
- raw meter/logger wall-clock remains visible as secondary evidence;
- unsafe reconstruction fails closed rather than promoting raw wall-clock to canonical time;
- no NFC/protocol behavior changes.

## Section E — timezone picker

The former free-text "enter an invalid IANA zone" smoke test is **retired**. Meter Details now uses a searchable Material exposed dropdown populated from `ZoneId.getAvailableZoneIds()` on the running device, with `UTC` ensured.

Current physical UI check:

1. Open Meter Details → Time zone.
2. Confirm the current persisted zone is preselected.
3. Search/filter for a supported zone and select it.
4. Save and confirm the selected supported zone persists.
5. Optional defensive check: type unsupported arbitrary text and confirm Save refuses to persist it.

Expected:

- only exact runtime-supported IANA IDs can be persisted;
- changing the zone changes only local/civil interpretation;
- raw meter/logger timestamps, `ON_TIME`, occurrence identity and archive payloads remain unchanged;
- no NFC/History action is triggered by the picker.

## Section F — final protected Live regression

Section F remains the last functional real-device gate before RC preparation.

Return to Overview and perform exactly one normal Live/default NFC read.

Expected:

- protected normal Live/default read succeeds;
- canonical real acquisition time remains primary;
- raw meter time remains secondary evidence when available;
- no automatic History synchronization starts;
- no archive/traversal/protocol regression is observed;
- the successful read refreshes the single active time anchor for that physical meter.

## Current candidate notes

The current functional candidate is `b38006f9dab270a574d7e08cb9e3785a231a2ef8`.

It includes:

- single-active-anchor projection;
- deterministic Home Assistant `SourceRecordId` primitive;
- centralized Statistics X-axis collision handling;
- contextual-help density cleanup;
- searchable constrained timezone picker.

After Section F passes, the next step is to prepare the exact signed v2.1.0 RC and physically accept that exact RC before PR #22 leaves Draft / merge / tag / release.
