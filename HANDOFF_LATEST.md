# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-08

This is the canonical entry point for the next project chat starting W1 NFC Reader v2.1 after the successful public v2.0.0 release, post-release cleanup, and the final v2.1 time-basis product decision.

Repository, GitHub Actions, canonical docs and real-device evidence are authoritative over stale chat history.

## 1. Read this first — required order

Before proposing or changing v2.1 code, read in this order:

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `docs/product-decisions/v2.1-real-time-timeline-and-coverage.md` on current `main`
4. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
5. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
6. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
7. `docs/V2_BREAKING_CHANGES.md`
8. `docs/PROTOCOL_SAFETY.md`
9. `docs/RELEASING.md`
10. `CHANGELOG.md`
11. `AGENTS.md`

Then inspect the current implementation before designing the migration:

- `MeterTimeEvidence.java`
- `DefaultReadObservation.java`
- `ArchiveFamilyPeriod.java`
- `ArchiveFamilyStore.java`
- `ArchiveFamilyPolicy.java`
- `HistoryTimePresentation.java`
- `HistoryPeriodNavigator.java`
- `HistoryCustomRangeSemantics.java`
- `HistoryStatisticsRepository.java`
- `HistoryStatisticsActivity.java`
- `SettingsActivity.java`
- `UiPreferences.java`
- `ProductUiHardening.java`
- `DataPortability.java`
- `DataPortabilityCsvV3.java`
- backup/restore schema implementation and migration tests

For protocol/time-evidence assumptions, also read the frozen private Research authority in section 13 before changing behavior.

## 2. Current public repository state

Public repository:
- `MarcLeinenDE/w1-nfc-reader`

Current `main` after v2.0 cleanup plus the final v2.1 time-basis decision:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- commit: `docs: add selectable time basis to v2.1 plan (#21)`
- PR #21 changed documentation/product direction only; no NFC/protocol/runtime code.
- PR CI run `34271568904`: SUCCESS
- PR CI job `102214163106`: SUCCESS
- post-merge main CI run `34271768845`: SUCCESS
- post-merge main CI job `102214816296`: SUCCESS
- translations, unit/Robolectric tests, debug build, APK signature verification and artifact upload all passed.

Previous cleanup baseline:
- `f1e648196090933ecb97c236bc49f8f0f1a2b628`
- PR #20: `chore: post-v2.0.0 repository cleanup`

The handoff branch is coordination-only. Do not develop directly on it.

## 3. Public v2.0.0 release — immutable baseline

Release:
- tag/name: `v2.0.0`
- release id: `384651482`
- published: `2026-09-08T18:53:57Z`
- URL: `https://github.com/MarcLeinenDE/w1-nfc-reader/releases/tag/v2.0.0`

Immutable source/tag commit:
- `5c094a70e75cba52fbdaef96d39b6c72e2011f9d`

Stable Android identity:
- applicationId: `de.marcleinen.w1nfcreader`
- versionCode: `43`
- versionName: `2.0.0`

Exact public APK:
- `w1-nfc-reader-2.0.0.apk`
- asset id: `550332677`
- size: `5,048,100` bytes
- SHA-256: `211c5dcb94047dcc12d0aa68534b6eaf154103a8c08c600f293c097c2188d2f4`
- signer certificate SHA-256: `3877ec6714ae64b46421f63df9f4d022b865f7f06ac4d28c77707b3d7a1d1896`

Checksum asset:
- asset id: `550332678`

The published APK was downloaded after publication and verified byte-identical to the physically accepted signed candidate.

Never move, recreate or replace `v2.0.0`. `v1.0.0` is also immutable.

## 4. Why v2.1 is needed

The final normal-use v2.0 smoke exposed a product-time-axis limitation, not a read/synchronization failure.

Observed problem:
- Live primary timestamp uses Android/device acquisition time.
- Hour/Day/Month archive periods use raw meter/logger wall-clock time.
- The tested meter's logger clock was materially offset from normal civil time.
- A user cannot reliably ask for a real-world interval such as `17:00–18:00` without knowing the meter-clock offset.
- The same problem would make future Home Assistant statistics misleading if raw meter wall-clock time were used as the integration timestamp.

v2.0.0 remains valid and intentionally documents this limitation. v2.1 must solve it without destroying raw meter evidence.

## 5. Canonical v2.1 product direction

Authoritative decision:

`docs/product-decisions/v2.1-real-time-timeline-and-coverage.md`

The earlier idea of forcing one single real/local timeline has been refined. v2.1 now keeps both views and gives the user a **global app time-basis setting**:

- `LOCAL` — **Local time (recommended/default)**
- `METER` — **Meter time**

The setting affects Android presentation and History/Statistics navigation semantics only. It never rewrites raw meter evidence or the canonical UTC timeline.

### LOCAL mode

- Primary display: real/local time.
- Secondary display: raw meter time.
- History navigator/custom ranges are entered in the meter-assigned local timezone and converted to UTC.
- History uses derived UTC overlap semantics.
- Statistics uses full-containment/coverage semantics and does not invent fractional edge consumption.
- Coverage states include `EXACT`, `PARTIAL_EDGES`, `OVERLAP_ONLY`, `GAP`, `UNRESOLVED_TIME`.

### METER mode

- Primary display: raw meter/logger time.
- Secondary display: resolved local time when trustworthy.
- History navigator/custom ranges are interpreted directly as meter wall-clock time.
- Statistics uses the raw meter-time interval basis.
- Do not apply LOCAL edge-coverage warnings solely because real time is shifted.
- Actual missing data, meter replacement and incompatible granularity rules still apply.

### Live presentation

The same global preference applies to Live:

LOCAL:
- primary = Android/real acquisition local time;
- secondary = meter wall-clock.

METER:
- primary = meter wall-clock;
- secondary = real/local acquisition time.

The user should have one consistent time model across Live, History and Statistics.

## 6. Meter-assigned local timezone

For v2.1, **local time means civil time in the timezone assigned to the physical meter**, not whatever timezone the phone happens to use later.

### First assignment

At the first successful verified Live/default read for a meter without a timezone assignment:

- read Android's current IANA `ZoneId`, e.g. `Europe/Berlin`;
- persist it for that meter;
- record provenance such as `DEVICE_AT_FIRST_VERIFIED_LIVE`;
- do not request GPS/location permission for this purpose.

Do not store only a numeric offset such as `+02:00`; preserve an IANA timezone so DST/history rules remain available.

Conceptual fields:
- `meter_zone_id`
- `meter_zone_source`
- `meter_zone_assigned_at_utc`
- optional timezone validation/quality state

### Stability

The assigned meter timezone must not silently change merely because:
- the user travels;
- Android changes timezone;
- a backup is opened on another device.

Settings or Meter details must let the user review/change the assigned timezone.

Changing the timezone may re-resolve derived time presentation, but must never rewrite raw meter/logger timestamps.

A v2.0-origin meter with no stored timezone may receive one on the next successful verified Live read or through explicit user selection.

## 7. Settings UX contract

Add a dedicated settings section for the global time basis with localized explanatory text.

Recommended/default option:

**Local time (recommended)**

Meaning:
- use the meter's assigned local timezone for primary display, History navigation and Statistics;
- show meter time as secondary technical information;
- if a trustworthy real-time mapping is unavailable, do not guess.

Alternative:

**Meter time**

Meaning:
- use the time stored by the water meter for primary display, History navigation and Statistics;
- if the meter clock is fast/slow, intervals can differ from real local time;
- show resolved local time as secondary information when available.

Also expose the assigned meter timezone and an action to change it.

The global time-basis preference is a UI/query projection; the per-meter timezone is data interpretation metadata.

## 8. Evidence and required v2.1 data model

Never overwrite the original archive timestamp.

Preserve raw evidence at minimum:
- meter ID;
- archive family;
- raw logger wall-clock timestamp;
- Type-F SU/summer-time flag;
- On-Time;
- relevant validity/provenance evidence;
- retrieval/acquisition evidence;
- meter-assigned IANA timezone and provenance.

Derived fields should remain separate, conceptually:
- `derived_start_utc`;
- `derived_end_utc`;
- `time_derivation_method`;
- `time_quality` / uncertainty;
- anchor identity / resolution segment.

The current code already exposes important evidence inputs:
- raw Type-F meter wall clock;
- Type-F `summerTime` / SU flag in `MeterTimeEvidence`;
- Type-F validity evidence;
- typed On-Time;
- Android/device acquisition epoch on Live/default reads;
- meter identity;
- archive family and raw logger timestamp.

A verified Live/default read can become a strong anchor because it provides real acquisition time and meter/On-Time evidence.

Preferred direction, subject to evidence validation:

`archive_real_time = live_anchor_real_time - (live_OnTime - archive_OnTime)`

Do not implement that formula blindly until On-Time continuity and SU/DST behavior are validated for the W1 evidence model.

Do not bridge time-resolution segments across:
- meter replacement;
- On-Time reset/discontinuity;
- invalid Type-F evidence;
- unresolved DST ambiguity;
- conflicting evidence.

Design DB migration and backup-schema evolution before production migration code.

An explicit decision is required on whether existing v2.0 archive rows can be resolved safely after a new valid anchor or require re-sync/reconfirmation.

## 9. LOCAL History / Navigator / Statistics contract

Navigator/custom-range input means local time in the meter-assigned timezone.

Internally:
- convert the requested range to UTC;
- query archive records against trustworthy derived UTC intervals;
- never silently shift the user's requested range to meter boundaries.

History uses overlap semantics.

Example requested LOCAL range:
- 17:00–18:00

Possible derived archive intervals:
- 16:55–17:55
- 17:55–18:55

History shows both, with local time primary, meter time secondary and a friendly partial-overlap state.

Statistics does not invent fractional Hour/Day/Month consumption at bucket edges.

For interval totals:
- exact totals use fully contained buckets;
- partial first/last buckets are not fractionally estimated;
- missing internal buckets are explicit gaps;
- if no complete bucket is contained, do not show `0` as though it were measured consumption.

Important required scenario:
- requested range previous day 10:00 to next day 12:00;
- shifted archive boundaries may create partial first/last buckets plus fully contained inner buckets;
- History shows overlapping records;
- Statistics reports exact contained coverage without fabricating edge consumption.

## 10. METER History / Navigator / Statistics contract

Navigator/custom-range input means raw meter/logger wall-clock time.

Example:
- requested Meter range 17:00–18:00
- show/select the raw archive period 17:00–18:00 according to completed-period semantics.

Presentation:
- primary = raw meter interval;
- secondary = resolved local interval if trustworthy.

Statistics operates on the selected meter-time basis. It must not manufacture a partial-edge warning merely because the same bucket maps to a shifted real/local interval.

However:
- real missing buckets are still gaps;
- never interpolate missing data;
- never calculate cumulative deltas across meter replacement;
- never mix incompatible archive granularities unsafely.

Raw meter-time mode may be discontinuous across physical meter replacement. Do not smooth or invent continuity.

## 11. DST / timezone / repeated local hours

Canonical UTC must disambiguate repeated local times at autumn DST.

If two LOCAL intervals would otherwise look identical, the UI may add localized CET/CEST-style labels.

The SU flag must be preserved as evidence and tested against real W1 behavior before being relied on for identity/conversion.

In METER mode, repeated raw wall-clock intervals must remain distinct using preserved evidence/record identity; do not collapse physical records solely because displayed clock text matches.

## 12. Home Assistant double-purpose contract

Do not create separate time-conversion logic for Home Assistant.

Android LOCAL mode and future Home Assistant integration consume the same canonical UTC interval model.

Integration-facing data should conceptually expose:
- `period_start_utc`;
- `period_end_utc`;
- measurement/value fields;
- `time_quality`;
- meter timezone where useful;
- optional raw meter-time diagnostics.

Home Assistant must not use raw meter wall-clock time as the primary statistics/automation timestamp.

**Important:** the Android global `LOCAL` / `METER` setting must not change the Home Assistant/export UTC contract. METER is an Android presentation/navigation preference, not a redefinition of canonical integration time.

The Home Assistant connector itself does not need to be implemented in the first v2.1 slice; the data/time contract must make it possible without another conversion model later.

## 13. Frozen private Research authority

Private repository:
- `MarcLeinenDE/engineering-lab`
- path: `android/qalcosonic-nfc-reader/`
- frozen branch: `research/w1-nfc-archive-analysis`
- frozen Research head: `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`
- integration handoff branch: `handoff/w1-post-1.0-research-integration`

Canonical rule:

> Extend the public product architecture with frozen Research semantics; do not port/merge the Research architecture wholesale.

Required private reading before protocol/time-evidence changes:
1. private `HANDOFF_LATEST.md`
2. `PRODUCT_INTEGRATION_HANDOFF.md`
3. `CURRENT_STATE.json`
4. `docs/DOCUMENT_STATUS.md`
5. `docs/product-integration/IMPLEMENTATION_DELTA.md`
6. `ArchiveFullReconnectActivity.java`
7. `ArchiveMultiEndScoutReader.java`
8. `ArchiveFamilyReadPolicy.java`
9. referenced product decisions, especially History sync/data model and archive-family reset/reconnect decisions.

Never publish private meter IDs, captures, raw NFC traffic, consumption data or private Research evidence.

## 14. Protected product/protocol invariants from v2.0

These remain authoritative unless separately re-researched and explicitly changed:

- Normal NFC contact = protected fast Live/default read only.
- History starts only from explicit user action.
- Hour, Day and Month remain independent synchronization families.
- COMPLETE requires valid traversal terminal state plus successful final Default Restore/Live verification.
- Incremental `KNOWN_RECORD_REACHED` requires secure known overlap; timestamp-only overlap is forbidden.
- Accepted archive records are persisted immediately before the next selected request.
- Ambiguous selected requests are never blindly retried.
- Failed later Incremental/Full Re-Sync does not erase an authoritative COMPLETE baseline.
- Live consumption deltas are same-meter Live -> strictly earlier Live only.
- Archive deltas are same-meter + same-granularity only.
- Never calculate cumulative consumption across meter replacement.
- Logical NFC-V reconnect alone does not establish default application state.
- No intentional persistent meter/radio/calibration/firmware writes.
- `QalcosonicReader.java`, `MbusParser.java` and physically validated NFC/mailbox/archive traversal paths are protected.
- Raw meter logger time remains native archive identity/source evidence even when v2.1 derives UTC.

## 15. Persistence / backup implications

The v2.1 design must explicitly audit and decide persistence/backup behavior for:
- raw meter/logger timestamps;
- SU/summer-time evidence;
- On-Time;
- Android acquisition UTC anchors;
- per-meter IANA timezone + provenance;
- derived UTC intervals and quality/provenance where persisted rather than recomputed;
- global app time-basis preference if product policy chooses to back up user preferences.

The meter timezone is part of data interpretation and should survive canonical `.qw1backup` restore.

A restore must not silently replace the backed-up meter timezone with the receiving phone's current timezone.

## 16. Mandatory scenario tests before product freeze

At minimum design deterministic tests for:

1. LOCAL exact one-hour match;
2. LOCAL one-hour request crossing two shifted archive buckets;
3. LOCAL multi-hour range with partial start/end buckets;
4. LOCAL previous-day 10:00 to next-day 12:00;
5. LOCAL exact full-day request;
6. missing bucket inside the range;
7. DST spring-forward;
8. DST fall-back/repeated local hour;
9. both modes across meter replacement;
10. On-Time reset/discontinuity;
11. unresolved/low-quality real-time mapping;
12. long-range Hour/Day/Month availability;
13. METER primary/secondary times swapped correctly;
14. METER navigator uses raw meter time and does not apply LOCAL edge coverage;
15. first verified Live read assigns Android IANA ZoneId to an unassigned meter;
16. later phone timezone change does not silently alter meter timezone;
17. explicit timezone change re-resolves derived presentation without rewriting raw meter time;
18. backup/restore preserves meter timezone;
19. Android and Home Assistant/export use identical UTC boundaries regardless of LOCAL/METER preference.

## 17. Recommended implementation sequence for the next chat

Do **not** start by changing NFC transport.

Recommended sequence:

1. Read all required material and inspect current implementation.
2. Audit exact persistence/backup fields currently retained for Type-F raw time, SU, On-Time, Android acquisition time and meter identity.
3. Audit current Android timezone/time APIs and existing preference/storage architecture; confirm no location permission is needed.
4. Re-read frozen private Research evidence for Type-F/On-Time/family semantics.
5. If needed, verify SU/DST and Type-F semantics against manufacturer/protocol sources before freezing derivation logic.
6. Define a pure `TimeResolver` / time-segment model and quality states independent of Android UI.
7. Define per-meter timezone assignment/provenance and global app `LOCAL` / `METER` preference.
8. Define DB schema/migration and backup/export evolution, including existing v2.0 rows.
9. Add deterministic unit tests for time resolution, drift, DST, timezone assignment/change, discontinuities and coverage before wiring UI.
10. Implement read-only derived UTC timeline without changing raw period identity.
11. Implement the selected time-basis projection so Live/History/Statistics swap primary/secondary time consistently.
12. LOCAL navigator/query: local meter zone -> UTC overlap; Statistics coverage/full-containment.
13. METER navigator/query: raw logger time; preserve existing completed-period semantics.
14. Integrate final controls/text directly into owning screens. `ProductUiHardening` is active today; remove it only after its behavior is absorbed with regression coverage.
15. Define/export the same canonical UTC contract for future Home Assistant consumption.
16. Only then decide focused real-device validation.

Prefer coherent CI-backed slices rather than many tiny physical-test builds.

## 18. Post-v2.0 cleanup already completed

PR #20 cleaned the public repository after release:
- obsolete `WaterUsageChartView` removed after no production references were found;
- obsolete intermediate `UnifiedHistoryModel` + dedicated test removed;
- v2.0-dev.4 checkpoint moved to explicit historical archive;
- current v2 History docs marked released/current;
- future release workflow requires curated release notes and exact tagged draft creation;
- v2.0 `untagged-*` release lesson documented;
- `CHANGELOG.md` has an `Unreleased` maintenance section.

Intentional non-cleanups:
- `ProductUiHardening` is active behavior, not dead code; fold it into owning screens when v2.1 touches them, then remove with regression coverage.
- Month-prefixed compatibility classes are not wholesale dead code; audit separately before removal.
- Java namespace/file-name cosmetics remain low priority.

## 19. README screenshot task — deferred, not forgotten

Post-release README screenshots are still desired:
- use ADB to navigate the released app;
- capture Overview/Live, History, Statistics, History Sync, Custom Range/Filter and About;
- omit status and navigation bars;
- use one consistent device/resolution/theme;
- store them in one documented repository location and add to README.

This visual documentation task must not block v2.1 architecture work.

## 20. Branch hygiene

Steady-state handoff target:
- `main`
- `handoff/v2.1.0-current`

PR #21 used temporary branch `docs/v2.1-time-basis-preference`. After confirming the merge and green main CI, delete that temporary branch if it is still present.

When implementation starts, create a focused development branch from current `main`, preferably:
- `dev/v2.1.0-real-time-timeline`

Do not develop on the handoff branch.

## 21. First task for the next chat

The next chat must first determine from public repository + frozen Research evidence exactly which of these values are currently decoded, persisted and portable:

- raw Type-F/meter wall-clock;
- archive logger timestamp;
- SU/summer-time flag;
- On-Time for Live and archive records;
- Android acquisition UTC/epoch;
- meter identity;
- any current timezone/ZoneId evidence;
- current DB fields;
- `.qw1backup` fields/schema;
- CSV fields/schema.

Then identify what additional evidence is needed to support:

1. trustworthy UTC derivation;
2. per-meter IANA timezone assignment from the first verified Live read;
3. global `LOCAL` / `METER` Android preference;
4. LOCAL overlap/coverage navigation;
5. METER raw-wall-clock navigation;
6. a future Home Assistant UTC contract independent of the Android display preference.

Only after that evidence inventory should the new chat freeze the concrete `TimeResolver`, migration, backup and Navigator implementation plan.