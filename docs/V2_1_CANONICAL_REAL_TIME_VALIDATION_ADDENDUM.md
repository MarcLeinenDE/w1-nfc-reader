# W1 NFC Reader v2.1 — canonical real-time validation addendum

Date: 2026-09-12
Status: supersedes Section D of `docs/V2_1_REAL_DEVICE_VALIDATION.md`

The accepted product decision `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` removes raw METER wall-clock as a peer normal-product time mode. Therefore the old LOCAL ↔ METER switch validation in Section D is no longer a release gate.

## Revised Section D — canonical real timeline + raw evidence

Use the current replacement candidate after the canonical-timeline implementation.

1. Open Settings and confirm the former global `Local time / Meter time` selector is no longer present.
2. Open Overview and confirm the actual/reconstructed real local time is primary while raw meter time remains visible as secondary evidence when available.
3. Open History → Live and confirm actual acquisition time is primary while raw meter time remains secondary.
4. Open History → Hour, Day and Month and confirm the reconstructed real/civil interval is primary while raw logger/meter time remains secondary evidence.
5. Confirm the displayed raw clock can differ from reconstructed real time without changing archive identity or consumption values.
6. Confirm an old/restored development state that previously selected METER returns to the canonical real-time product timeline on normal Activity startup.
7. No History synchronization may start merely by opening Settings or navigating these screens.

Expected:

- there is one normal-product timeline;
- archive primary time comes from verified anchor + ON_TIME reconstruction and the persisted IANA zone;
- Live primary time comes from actual acquisition epoch;
- raw meter/logger wall-clock remains visible as evidence but is not presented as equally authoritative real time;
- no silent fallback to raw wall-clock as primary when reconstruction is unsafe;
- no NFC/protocol behavior changes.

## Existing Sections E and F

Section E (invalid IANA-zone rejection) remains required.

Section F remains the final normal protected Live NFC regression read and must still pass before release hardening.

## UI-polish follow-up

The post-functional-gate polish pass should make the distinction clearer through contextual info controls and wording such as raw meter time / meter clock evidence where appropriate. The repository-wide date/time separator audit, locale-aware timezone abbreviations, visible zero-consumption markers, statistics coverage explanations and mixed-All Live baseline rule remain separate accepted polish items.
