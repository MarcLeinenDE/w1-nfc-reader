# W1 NFC Reader v2.1 — real-device validation

Status: current physical release gate for PR #22 / v2.1.0.

The authoritative product-time decision is `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md`. The former peer `LOCAL / METER` product mode is retired. Use `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md` for the canonical timeline, timezone-picker and final Live-read checks.

## Goal

Confirm on a real compatible Qalcosonic W1 that all v2.1 work remains safely downstream of the already validated NFC acquisition path while History/Statistics, canonical timing and product presentation behave correctly.

Deterministic DST behavior, database migration, backup compatibility, source-record identity, chart-axis collision policy and most edge semantics are covered by automated tests. Physical testing focuses on the few behaviors that genuinely require a real meter/device.

## Current functional candidate

- branch: `dev/v2.1.0-real-time-timeline`
- functional code commit: `b38006f9dab270a574d7e08cb9e3785a231a2ef8`
- Android CI: `34715215357` — **SUCCESS**
- unit/Robolectric suite: **SUCCESS**
- product translation check across 6 locales: **SUCCESS**
- artifact: `w1-nfc-reader-debug`
- artifact id: `10304503134`
- artifact ZIP SHA-256: `cc48ffd4548fcff663583c1d722309b36584b4285de66ad24a3b4cfc1a367059`
- APK SHA-256: `4488e1b1d500cf52ff03cc4ab294a9152e5623a111133533e3cd3ef4003b1686`

This candidate contains the single-active-anchor fix, deterministic `SourceRecordId` primitive and centralized Statistics X-axis collision handling.

## Safety rules

- Do not add experimental NFC commands for validation.
- Normal NFC contact remains the protected fast Live/default read.
- History synchronization starts only after explicit user action.
- Do not alter meter/radio/calibration/firmware state.
- If NFC behavior differs from the validated v2.0 path, stop and treat it as a regression.
- Do not commit private meter IDs, consumption values, backups, captures or raw NFC traffic to the public repository.

## Accepted / retained physical evidence

The following downstream behaviors have already been physically exercised during v2.1 development and should not be needlessly repeated unless a later code change touches them:

- protected normal Live/default NFC path;
- explicit-only History synchronization;
- Hour/Day/Month History ownership on reconstructed real time;
- empty/out-of-range archive family handling without false time-resolution warnings;
- final History Default/Live verification refreshing the time model;
- native Statistics interval handling across time anchors;
- natural oldest-retention edge handling;
- known normal-day Hour Statistics case with fully-contained buckets and partial-edge context;
- no-Hour-data period without false warning;
- long-range chart presentation after the centralized label-collision fix;
- mixed History → All Live baseline selection;
- canonical date/time presentation and contextual-help cleanup.

## Single-active-anchor regression

The older boundary-by-boundary anchor selection is retired.

For each physical meter:

- only the newest fully verified Live/default anchor participates in projection;
- all stored archive points are projected backwards from that same anchor via native `ON_TIME`;
- a newer valid anchor replaces the previous active projection anchor;
- an archive `ON_TIME` newer than the active anchor fails closed;
- a backwards/reset `ON_TIME` does not silently replace the timeline.

Required invariant:

- two valid Hour archive boundaries separated by exactly 3,600 `ON_TIME` seconds must remain exactly 3,600 real seconds apart.

A newer anchor may shift the absolute reconstructed placement of older archive data as one coherent timeline; it must not stretch or compress individual native intervals.

## Statistics presentation contract

All metric charts use the shared `V2MetricChartView` policy.

- bar-chart labels are horizontal while they fit;
- when needed they rotate together;
- if even the rotated labels cannot be presented without collision, the X-axis text is hidden rather than becoming an unreadable block;
- dense line-chart labels are thinned/hidden based on available width;
- data bars/points are never discarded merely to make labels fit.

This applies across consumption, flow, temperature, battery and other Statistics metrics using the shared view.

## Canonical timeline check

The normal product UI has one canonical real-time axis.

Expected:

- no global `Local time / Meter time` selector in Settings;
- Live primary time = actual Android acquisition time;
- archive primary time = single active verified anchor + native `ON_TIME`, projected through the persisted per-meter IANA zone;
- raw meter/logger wall-clock remains secondary evidence;
- unsafe reconstruction fails closed rather than promoting raw meter time;
- no History synchronization starts simply by navigating product screens.

The detailed checklist is maintained in `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md`.

## Timezone picker check

Meter Details uses a searchable constrained IANA picker rather than an unrestricted free-text editor.

Expected:

- current persisted zone is preselected;
- supported zones can be searched/selected;
- only exact runtime-supported zone IDs can be persisted;
- unsupported arbitrary typed text cannot replace the valid zone;
- zone changes affect only local/civil interpretation, never raw meter/archive evidence;
- no NFC or History action is triggered by the picker.

The former mandatory free-text invalid-IANA test is retired.

## Final functional gate — Section F

Before RC preparation, perform exactly one final normal protected Live/default NFC read from Overview using the current candidate.

Expected:

- normal protected Live/default read succeeds;
- canonical real acquisition time is primary;
- raw meter time remains secondary when available;
- no stale archive selection leaks into the Live result;
- no automatic History synchronization starts;
- no protocol/mailbox/archive regression is observed;
- the successful read refreshes the active time anchor.

If this passes, functional debug-candidate validation is complete.

## Exact RC acceptance

After Section F passes:

1. set v2.1.0 version metadata and prepare changelog/release notes;
2. build the exact signed release candidate through the release workflow;
3. verify artifact/signature/hash provenance;
4. perform a short physical acceptance of **that exact signed RC**, including fresh/default-state smoke where appropriate;
5. only then move PR #22 out of Draft, merge, tag and release.

A passing debug APK does not replace physical acceptance of the exact signed RC.

## Deferred v2.2 cleanup

The hidden compatibility implementation for the retired `AppTimeBasis.METER` path is intentionally not removed during v2.1 RC hardening. That larger cleanup belongs to v2.2 after release.

The v2.2 cleanup should remove obsolete peer-mode runtime branches/resources/tests while retaining only the minimal compatibility required to read old development backup state and normalize it to canonical time.

Do not remove physically validated NFC/parser/archive regression tests merely because they originate from older development stages.

## Evidence to record

For final acceptance record at minimum:

- source commit;
- GitHub Actions run id;
- artifact id/digest and APK SHA-256;
- non-sensitive Android device/OS information;
- meter family/model without publishing private serial/meter ID;
- PASS/FAIL for final Live read and exact signed RC acceptance;
- any user-visible discrepancy with a short description.

Screenshots are optional and must not publish private meter IDs or personal consumption data without redaction.
