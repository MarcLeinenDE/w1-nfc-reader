# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-08

This is the canonical entry point for the next project chat starting the W1 NFC Reader v2.1 work after the successful public v2.0.0 release and post-release repository cleanup.

Repository, GitHub Actions and real-device evidence are authoritative over stale chat history.

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

Then inspect the existing implementation before designing the migration:

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
- `DataPortability.java`
- `DataPortabilityCsvV3.java`
- backup/restore schema implementation and migration tests

For any protocol/time-evidence assumption, also read the frozen private Research authority listed below before changing behavior.

## 2. Current public repository state

Public repository:
- `MarcLeinenDE/w1-nfc-reader`

Cleaned current `main`:
- `f1e648196090933ecb97c236bc49f8f0f1a2b628`
- commit: `chore: post-v2.0.0 repository cleanup (#20)`
- PR #20 was squash-merged after green PR CI.
- post-merge main CI run `34268555870`: SUCCESS
- job `102204036451`: SUCCESS
- translations, unit/Robolectric tests, debug build, APK signature verification and artifact upload all passed.

The v2.1 handoff branch starts from this cleaned main. It is a handoff/coordination branch, not the long-lived implementation branch.

## 3. Public v2.0.0 release — immutable baseline

Release:
- tag/name: `v2.0.0`
- release id: `384651482`
- published: `2026-09-08T18:53:57Z`
- public release URL: `https://github.com/MarcLeinenDE/w1-nfc-reader/releases/tag/v2.0.0`

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

The final normal-use smoke of v2.0 exposed a product-time-axis limitation rather than a read/synchronization failure.

Observed user problem:
- Live primary timestamp is Android/device acquisition time.
- Hour/Day/Month archive periods are shown/navigated on the raw meter/logger wall-clock time.
- The tested meter's logger clock was materially offset from normal civil time.
- A user therefore cannot reliably ask, for example, “show me 17:00–18:00 real time” using the current archive navigator without mentally knowing the meter clock offset.
- The same problem would make a future Home Assistant integration misleading if raw meter wall-clock time were used as the primary statistic timestamp.

v2.0.0 remains valid and intentionally documents this limitation. v2.1 must solve it without destroying the original meter evidence.

## 5. Canonical v2.1 product direction

The authoritative decision is:

`docs/product-decisions/v2.1-real-time-timeline-and-coverage.md`

Core rule:

> User-facing navigation and primary presentation use real local time derived from a canonical UTC timeline. Raw meter time remains preserved as technical/source evidence.

Do not reduce this to a simple fixed “meter offset”. The model must be evidence-based and robust to clock drift, DST, meter replacement and On-Time discontinuity.

### Evidence already available

The current code already exposes important inputs:

- raw Type-F meter wall clock;
- Type-F `summerTime` / SU flag in `MeterTimeEvidence`;
- Type-F validity evidence;
- typed On-Time;
- Android/device acquisition epoch on Live/default reads;
- meter identity;
- archive family and raw logger timestamp.

A verified Live/default read can therefore become a strong real-time anchor because it provides both real acquisition time and meter/On-Time evidence.

Preferred direction, subject to evidence validation:

`archive_real_time = live_anchor_real_time - (live_OnTime - archive_OnTime)`

Do not implement that formula blindly until On-Time continuity and SU/DST behavior have been validated for the W1 evidence model.

## 6. Required v2.1 data model properties

Never overwrite the original archive timestamp.

Preserve raw evidence at minimum:
- meter ID;
- archive family;
- raw logger wall-clock timestamp;
- SU/summer-time flag;
- On-Time;
- relevant validity/provenance evidence;
- retrieval/acquisition evidence.

Derived fields should be separate, conceptually including:
- `derived_start_utc`;
- `derived_end_utc`;
- `time_derivation_method`;
- `time_quality` / uncertainty;
- anchor identity / resolution segment.

Do not bridge a time-resolution segment across:
- meter replacement;
- On-Time reset/discontinuity;
- invalid Type-F evidence;
- unresolved DST ambiguity;
- conflicting evidence.

Design DB migration and backup-schema evolution before writing production migration code.

An explicit decision is required on whether existing v2.0 archive rows can be safely resolved after a new valid anchor, or whether some/all data requires re-sync/reconfirmation.

## 7. Navigator and History UX contract

Navigator/custom-range input always means the user's real local time.

Internally:
- convert the requested range to UTC;
- query archive records against derived UTC intervals when trustworthy;
- never silently shift the user's requested range to meter boundaries.

History uses **overlap semantics**: show every archive interval overlapping the requested real-time range.

Example requested real range:
- 17:00–18:00

Possible derived archive intervals:
- 16:55–17:55
- 17:55–18:55

History should show both, with real time primary and raw meter time secondary, plus a friendly partial-overlap state.

## 8. Statistics coverage contract

Do not invent fractional Hour/Day/Month consumption for bucket edges.

For interval-based consumption totals:
- exact totals use fully contained buckets;
- partial first/last buckets are not fractionally estimated;
- missing internal buckets are explicit gaps;
- when no complete bucket is contained, do not show `0` as though it were measured consumption.

Required semantic coverage states:
- `EXACT`
- `PARTIAL_EDGES`
- `OVERLAP_ONLY`
- `GAP`
- `UNRESOLVED_TIME`

User wording should be simple, e.g. “Measurement coverage: 25 of 26 hours exact”, with technical details only on demand.

Important required scenario:
- requested range previous day 10:00 to next day 12:00;
- shifted archive boundaries can create partial first/last buckets with fully contained inner buckets;
- History shows overlapping records;
- Statistics reports exact contained coverage without fabricating edge consumption.

Longer-term Auto resolution may combine Hour/Day/Month only if intervals never overlap/double-count and the result remains explainable. Correctness comes before aggressive mixed-granularity optimization.

## 9. Home Assistant double-purpose contract

Do not create a separate time-conversion implementation for Home Assistant.

The Android app and any future Home Assistant connector must consume the same canonical UTC interval model.

Integration-facing data should conceptually expose:
- `period_start_utc`;
- `period_end_utc`;
- measurement/value fields;
- `time_quality`;
- optional raw meter-time diagnostics.

Home Assistant must not use raw meter wall-clock time as the primary timestamp for statistics, automations or correlation with other sensors.

The connector itself does not have to be implemented in the first v2.1 slice; the v2.1 data/time contract must make it possible without another conversion model later.

## 10. Mandatory scenario tests before product freeze

At minimum design deterministic tests for:

1. exact one-hour match;
2. one-hour request crossing two shifted archive buckets;
3. multi-hour range with partial start/end buckets;
4. previous-day 10:00 to next-day 12:00;
5. exact full-day request;
6. missing bucket inside the range;
7. DST spring-forward;
8. DST fall-back/repeated local hour;
9. meter replacement;
10. On-Time reset/discontinuity;
11. unresolved/low-quality time mapping;
12. long-range Hour/Day/Month availability;
13. Android and Home Assistant/export use identical UTC interval boundaries.

Repeated local clock hours at autumn DST must be distinguishable by canonical UTC; the UI may add localized CET/CEST-style labels when needed.

## 11. Recommended implementation sequence for the next chat

Do **not** start by changing NFC transport.

Recommended sequence:

1. Read all required material and inspect current implementation.
2. Audit the exact persistence/backup fields currently retained for Type-F, SU and On-Time. Identify evidence that is decoded but not persisted.
3. Re-read frozen private Research evidence for Type-F/On-Time/family semantics.
4. If needed, verify SU/DST and Type-F semantics against manufacturer/protocol sources before freezing derivation logic.
5. Define a pure `TimeResolver`/time-segment model and quality states independent of Android UI.
6. Define DB schema/migration and backup/export evolution, including behavior for existing v2.0 rows.
7. Add deterministic unit tests for time resolution, drift, DST, discontinuities and coverage before wiring UI.
8. Implement read-only derived UTC timeline without changing raw period identity.
9. Move History navigator/query semantics to real local input -> UTC overlap.
10. Move Statistics to explicit coverage/full-containment semantics.
11. Present real local time as primary and raw meter time as secondary evidence.
12. Define/export the same canonical UTC contract for future Home Assistant consumption.
13. Only then decide what focused real-device validation is necessary.

Prefer coherent slices and CI-backed PRs rather than many tiny physical-test builds.

## 12. Protected product/protocol invariants from v2.0

These remain authoritative unless separately re-researched and explicitly changed:

- Normal NFC contact = protected fast Live/default read only.
- History starts only from explicit user action.
- Hour, Day and Month are independent synchronization families.
- COMPLETE requires valid traversal terminal state plus successful final Default Restore/Live verification.
- Incremental `KNOWN_RECORD_REACHED` requires secure known overlap; timestamp-only overlap is forbidden.
- Accepted archive records are persisted immediately before requesting the next selected record.
- Ambiguous selected requests are never blindly retried.
- Failed later Incremental/Full Re-Sync does not erase a previously authoritative COMPLETE baseline.
- Live consumption deltas are same-meter Live -> strictly earlier Live only.
- Archive deltas are same-meter + same-granularity only.
- Never calculate cumulative consumption across meter replacement.
- Logical NFC-V reconnect alone does not establish default application state.
- No intentional persistent meter/radio/calibration/firmware writes.
- `QalcosonicReader.java`, `MbusParser.java` and physically validated NFC/mailbox/archive traversal paths are protected.
- Raw meter logger time remains native archive identity/source evidence even when v2.1 derives a separate UTC presentation/integration timeline.

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

## 14. Post-v2.0 cleanup already completed

PR #20 cleaned the public repository after release:
- obsolete `WaterUsageChartView` removed after no production references were found;
- obsolete intermediate `UnifiedHistoryModel` and dedicated test removed;
- v2.0-dev.4 checkpoint moved from current docs to explicit historical archive;
- current v2 History docs marked released/current;
- future release workflow now requires curated release notes and creates the exact tagged draft through `gh release create` instead of `softprops/action-gh-release`;
- v2.0 publication `untagged-*` lesson documented;
- `CHANGELOG.md` has a clean `Unreleased` maintenance section;
- v2.1 time/coverage decision is now on public `main`.

Intentional non-cleanups:
- `ProductUiHardening` is still active behavior. Do not call it dead code. Fold its History navigator/icon and Settings/About text behavior into the owning screens when v2.1 touches those screens, then remove it only with regression coverage.
- Month-prefixed compatibility classes are not wholesale dead code; parts still participate in the validated production path. Audit separately before any removal.
- Java namespace/file-name cosmetics remain low priority.

## 15. README screenshot task — deferred, not forgotten

Post-release README screenshots are still desired:
- use ADB to navigate the released app;
- capture important screens such as Overview/Live, History, Statistics, History Sync, Custom Range/Filter and About;
- omit status and navigation bars;
- use one consistent device/resolution/theme;
- store them in one documented repository location and add them to README.

This was not completed during cleanup because the available PC had `git`, `gh`, Python and Windows Terminal but no Android SDK/ADB tooling. It is a visual documentation task and must not block v2.1 architecture work.

## 16. Branch hygiene target

After the old release/cleanup branches are deleted, the desired handoff state is:
- `main`
- `handoff/v2.1.0-current`

When v2.1 implementation starts, create a focused development branch from cleaned current `main`, for example:
- `dev/v2.1.0-real-time-timeline`

Do not develop directly on the handoff branch.

## 17. First question for the next chat

The next chat should first determine, from repository + frozen Research evidence, exactly which raw Type-F/SU/On-Time values are currently persisted for each archive observation and Live anchor, and what additional evidence must be added to the persistence/backup schema to derive UTC safely.

Only after that evidence inventory should it freeze the concrete `TimeResolver`, migration and Navigator implementation plan.
