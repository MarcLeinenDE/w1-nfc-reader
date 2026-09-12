# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-12

This is the canonical coordination handoff for W1 NFC Reader v2.1. Repository state, GitHub Actions and real-device evidence are authoritative over stale chat history.

## 1. Required reading order

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `UX_CONTEXTUAL_HELP_CONTRACT.md` on this handoff branch
4. `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` on `dev/v2.1.0-real-time-timeline`
5. `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md`
6. `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md`
7. `docs/V2_1_REAL_DEVICE_VALIDATION.md`
8. `docs/product-decisions/v2.1-real-time-timeline-and-coverage.md`
9. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
10. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
11. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
12. `docs/PROTOCOL_SAFETY.md`
13. `docs/RELEASING.md`
14. `CHANGELOG.md`
15. `AGENTS.md`

The handoff branch is coordination-only. Development stays on the dev branch.

## 2. Repository / current exact candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current exact candidate:
- commit `b38006f9dab270a574d7e08cb9e3785a231a2ef8`
- Android CI `34715215357`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS — 227 keys across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10304503134`
- artifact ZIP SHA-256 `cc48ffd4548fcff663583c1d722309b36584b4285de66ad24a3b4cfc1a367059`
- APK SHA-256 `4488e1b1d500cf52ff03cc4ab294a9152e5623a111133533e3cd3ef4003b1686`
- artifact independently downloaded/re-hashed; ZIP digest matches GitHub and APK matches `SHA256SUMS.txt`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until remaining physical gates and exact signed RC acceptance pass.

## 3. Accepted time model — one canonical timeline, one active anchor

- Live primary time = actual acquisition epoch.
- Archive primary time = **single newest fully verified Live/default anchor per physical meter** + monotonic `ON_TIME` -> canonical UTC -> persisted IANA-zone presentation.
- `ON_TIME` defines native timeline geometry; the active anchor only places the complete geometry on UTC.
- Every accepted normal Live/default read may replace the active anchor when `ON_TIME` advances.
- The already-required verified final Default/Live read after each History family likewise refreshes the active anchor.
- All Hour/Day/Month/Year boundaries for one meter project backwards from that same active anchor.
- Archive `ON_TIME` newer than active anchor fails closed as `ON_TIME_AFTER_ANCHOR`.
- Backwards/reset `ON_TIME` cannot silently replace the active timeline.
- Raw meter/logger wall clock remains preserved secondary diagnostic/export/backup evidence.
- Unsafe reconstruction never silently promotes raw meter wall clock.

Legacy time-model databases/backups containing multiple anchors remain readable. Projection exposes only the newest active anchor; a later accepted write/import normalization collapses the meter to one active anchor.

## 4. Release-blocking stretched-Hour finding — code-fixed, physical retest pending

A real-device History view showed one Hour interval longer than one hour (`19:00–20:04`). Private backup evidence confirmed the native archive itself was correct: consecutive Hour occurrences had exact 3,600-second `ON_TIME` spacing and no archive conflict.

Root cause was the former boundary-by-boundary anchor selection: start and end could use different historical Live anchors, so their phase difference stretched/compressed a physical archive interval.

The single-active-anchor implementation removes that handover. Regression tests require native Hour 3,600 s to remain exactly 3,600 real-time seconds, with analogous duration preservation for Day/Month/Year. Protected NFC/parser/archive traversal code was not changed.

Physical acceptance is still required on the exact current candidate: reopen the previously affected History range; every valid consecutive Hour interval must be exactly one hour. Absolute clock labels may shift coherently because the newest anchor places the complete native timeline.

## 5. Home Assistant source identity — primitive implemented

`SourceRecordId` is now implemented and CI-green. It gives every archive occurrence a deterministic external identity independent of the phone and independent of reconstructed UTC.

External form:

`w1:v1:<sha256>`

Canonical SHA-256 input is a length-prefixed sequence of preserved meter-native identity evidence:
- physical meter identity;
- archive family;
- raw logger timestamp;
- native occurrence key, normally `OT:<on_time_seconds>` when valid `ON_TIME` exists.

The ID deliberately excludes derived UTC/local time, SQLite row IDs, retrieval time, phone/install identity and measurement content. A fresh phone rereading the same physical archive occurrence therefore regenerates the same ID. Different meter/family or a reused `ON_TIME` with another logger occurrence generates another ID.

`SourceRecordIdTest` protects those invariants.

Future transport rule is explicit: **Android does not keep an authoritative already-sent ledger.** It may send every locally available eligible record on every transfer. Home Assistant is the idempotence authority and must enforce unique-key/UPSERT/no-op behavior on `source_record_id`. A known ID with improved derived metadata such as a better UTC reconstruction updates the existing record rather than creating a duplicate.

If a real future reset could reproduce both the same `ON_TIME` and same raw logger occurrence under an unchanged physical identity, introduce an explicit meter-generation discriminator; never derive it from phone-local state.

## 6. Statistics x-axis — shared adaptive policy now implemented in v2.1 candidate

The previously deferred chart-label hardening is now implemented centrally in `V2MetricChartView` and protected by `V2MetricChartAxisPolicyTest`.

Current shared behavior:
- bar metrics (consumption and flow): complete label set horizontal when it fits -> complete set rotated when that fits -> complete set hidden when even rotated text would collide;
- line metrics (temperature and battery): measured-fit thinning; if the remaining labels still collide, hide the x-axis labels;
- fit uses actual plot width, visible item count, rendered text/font metrics and minimum spacing;
- hiding/thinning labels changes presentation only; no data point/bar is dropped, merged or aggregated;
- rotated labels reserve extra chart height; hidden labels reclaim unnecessary label space.

The alarm metric currently renders a textual event timeline, not an x-axis `V2MetricChartView` chart.

This supersedes the earlier “next version” deferral. Future new chart metrics must inherit the same central policy rather than implement their own x-axis behavior.

## 7. Existing v2.1 UI/product polish

- fully-contained Statistics buckets = filled and included in selected-window KPIs;
- partial edge overlaps = dashed full measured context only, never prorated and excluded from KPIs;
- genuine zero = visible baseline marker; native gaps remain gaps;
- History -> All Live delta uses chronologically nearest trustworthy earlier archive cumulative observation; previous-Live fallback only if no earlier archive exists;
- `date · time` presentation is locale-aware and centralized;
- ordinary zone labels prefer localized abbreviations with DST-fold disambiguation;
- History/Statistics contextual help appears only at actual page headings; generic Statistics explanation is not duplicated in page body;
- timezone editor is a searchable runtime-supported IANA picker; arbitrary unsupported free text cannot persist.

## 8. Physical validation state

- A: partial pass; fresh-state default check can be done on exact signed RC.
- B: protected normal Live NFC path passed earlier.
- Previous C reproductions: pass, including September `10/30`, known Hour `22/24`, no-Hour February warning-free and natural-oldest handling.
- Canonical product presentation: accepted so far.
- Single-active-anchor stretched-Hour regression: **code-fixed/CI-green, physical retest pending**.
- Adaptive chart-axis policy: **code-fixed/CI-green, physical spot-check pending**.
- Timezone picker/help-density spot-check: complete if already observed; otherwise quick-check on current candidate.
- F: final protected normal Live/default NFC regression still pending after the UI/time retests.

## 9. Safety boundary

Remain protected:
- normal NFC contact = fast Live/default read only;
- History starts only after explicit user action;
- Hour/Day/Month sync families remain independent;
- COMPLETE requires semantic terminal + verified final Default Restore/Live;
- incremental `KNOWN_RECORD_REACHED` requires secure overlap; timestamp-only overlap forbidden;
- manufacturer capacities are not traversal limits;
- raw Type-F/logger time, `ON_TIME` and occurrence identity are never overwritten by derived presentation;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state machine remain protected.

## 10. Release gate

Do **not** yet bump to v2.1.0, mark PR #22 ready, merge, tag or release.

First on exact candidate `b38006f...`:
1. confirm the previously stretched Hour is now exactly one hour;
2. check Statistics normal and very long ranges: consumption/flow horizontal -> rotated -> hidden as density increases; temperature/battery thin -> hidden rather than collide; data remains complete;
3. complete any remaining timezone/help UI spot-check;
4. perform exactly one final protected normal Live/default NFC read for F.

Then prepare version metadata/changelog/release notes and the exact signed v2.1.0 RC, and physically accept that exact RC before publication.

## 11. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.
