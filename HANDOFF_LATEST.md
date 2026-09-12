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
7. `docs/V2_1_REAL_DEVICE_VALIDATION.md` — old Section D is superseded by the canonical-real-time addendum
8. `docs/product-decisions/v2.1-real-time-timeline-and-coverage.md`
9. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
10. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
11. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
12. `docs/V2_BREAKING_CHANGES.md`
13. `docs/PROTOCOL_SAFETY.md`
14. `docs/research/PUBLIC_QW1_EVIDENCE.md`
15. `docs/RELEASING.md`
16. `CHANGELOG.md`
17. `AGENTS.md`

The handoff branch is coordination-only. Development stays on the dev branch.

## 2. Repository / current exact candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current exact candidate:
- commit `a6cd9463abdebe4fdeae710d1005397a87683e0b`
- Android CI `34704383843`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10301735560`
- artifact ZIP SHA-256 `546cef95cd11fd5ab2ef0fb22aab9ddb625c65fa88c7c30f98dfa7c033597b1d`
- APK SHA-256 `f4a8c422b4b73c1939cfac1683c5efb83320f8088bda2db1eff293308c6d1b37`
- artifact independently downloaded/re-hashed; ZIP digest matches GitHub and APK matches `SHA256SUMS.txt`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until remaining physical gates and exact signed RC acceptance pass.

## 3. Accepted product decision — one canonical real timeline

Normal v2.1 UI has one canonical real timeline:

- Live primary time = actual acquisition epoch.
- Archive primary time = verified Live/default anchor + monotonic `ON_TIME` -> canonical UTC -> persisted per-meter IANA-zone presentation.
- Raw meter/logger wall-clock remains preserved secondary/diagnostic/export/backup evidence.
- Former global `Local time / Meter time` selector is removed from normal Settings.
- Legacy/restored `METER` preference is normalized back to the canonical product path.
- Unsafe reconstruction fails closed; raw meter clock is never silently promoted to canonical real time.

No NFC command, parser, mailbox, traversal, overlap or terminal semantics were changed by this product decision.

## 4. Physical validation completed so far

### A

Debug app installed and usable. A completely fresh-state default check was not explicitly recorded and may be done on the final exact signed RC without deleting current test data.

### B — PASS

Protected normal Live NFC read passed on the real Qalcosonic W1. No automatic History sync. Final protected regression is repeated in F.

### C — PASS

Confirmed real-device corrections include:

- LOCAL period ownership / DST projection;
- false warning from empty/out-of-range family;
- fresh post-History verified time anchor;
- native-valid statistics bucket across anchor handover;
- natural-oldest retained boundary no longer causing false warning.

Known physical reproductions:
- September 2026 Statistics = **10/30** fully-contained Day buckets;
- 10 September 2026 Statistics = **22/24** fully-contained Hour buckets;
- February no-Hour-data case warning-free;
- `Alle Zeiträume` natural-oldest false warning removed.

### Revised D — canonical real-time product surface

Physical product presentation with the single canonical real timeline was accepted. Raw meter/logger time remains secondary evidence.

## 5. Statistics edge presentation — implemented and physically plausible

- fully-contained archive buckets = filled bars and included in selected-window KPIs;
- partial edge overlaps = dashed outline bars using the complete measured archive delta as context only;
- partial edge values are never prorated and are excluded from KPIs;
- genuine zero consumption = visible baseline marker;
- native gaps remain gaps;
- coverage wording says **fully contained**.

User spot-checks showed the presentation basically functioning as intended.

## 6. Combined History / UI polish — implemented

### History -> All Live baseline

Only in mixed **History -> All**, a visible Live card uses the chronologically nearest trustworthy earlier archive cumulative reading from the same physical meter, across Hour/Day/Month. If none exists, it falls back to the previous valid Live read. Dedicated Live filter stays Live-to-Live; archive cards retain same-family semantics.

### Statistics x-axis ownership

- one visible bar = one centered x-axis label;
- dense bar charts rotate all labels together instead of skipping labels;
- chart reserves sufficient height for rotated labels.

### Date/time and timezone presentation

- shared locale-aware `date · time` convention;
- ordinary local display prefers localized zone abbreviations such as `MEZ/MESZ` or `CET/CEST`;
- ambiguous DST-fold occurrences retain enough numeric offset information for disambiguation.

### Contextual help — corrected after physical UI review

Candidate `e696788...` functionally worked but over-applied info affordances because word matching attached icons to repeated `Historie` / `Statistik` occurrences, including drawer/navigation labels.

Current candidate `a6cd946...` fixes that:

- **History info appears only on the actual History page heading.**
- **Statistics info appears only on the actual Statistics page heading.**
- No info icon is added merely because `Historie` or `Statistik` appears in drawer/menu/overview/settings text.
- The generic Statistics explanation that duplicated the info dialog is removed from the chart body.
- The visible coverage line is shortened to the count only; its generic coverage-vs-sync explanation is moved into the Statistics info dialog.
- Metric-specific caveats for temperature, flow, battery and alarms remain inline because they contain information not duplicated by the generic page help.
- The earlier extra Meter Details time-model icon injection is no longer part of the generic hardening path.

All six product locales contain the consolidated help copy.

## 7. Current candidate change boundary

Compared with the previous combined polish candidate, the current cleanup touches only downstream presentation/help resources:

- `ProductUiHardening.java`
- six localized `v21_polish_strings.xml` files.

Protected `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state-machine behavior remain untouched.

## 8. Remaining physical checks on `a6cd946...`

No History sync is required for the UI check.

1. Open the hamburger drawer: **no info icons** beside History/Statistics navigation entries.
2. Open History: exactly one info affordance on the History page heading; no duplicated generic help lower on the page.
3. Open Statistics: exactly one info affordance on the Statistics page heading; generic bar/coverage explanatory paragraph is not repeated under the chart; concise coverage count remains.
4. Confirm metric-specific caveats still appear where useful for temperature/flow/battery/alarms.
5. Quick regression spot-check: History-All delta, statistics bars/labels and `date · time` remain intact.
6. **E — invalid timezone smoke**: invalid IANA value must be rejected without changing the existing valid zone.
7. **F — final protected Live regression**: exactly one normal Live/default NFC read; no automatic History sync, no archive-state leak, no protocol regression.

## 9. Safety boundary

Remain protected:

- normal NFC contact = fast Live/default read only;
- History starts only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive observations persist immediately;
- COMPLETE requires semantic terminal plus verified final Default Restore/Live;
- incremental `KNOWN_RECORD_REACHED` requires secure overlap; timestamp-only overlap forbidden;
- manufacturer capacities are not traversal limits;
- raw Type-F/logger time, ON_TIME and occurrence identity are never overwritten by derived presentation;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state machine remain protected.

## 10. Release gate

Do **not** yet:

- bump stable version to 2.1.0;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag or release.

First physically confirm the reduced help density, then pass E and F. After that prepare version metadata/changelog/release notes and the exact signed v2.1.0 RC, and physically accept that exact RC before publication.

## 11. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Next action

Install exact CI-green candidate `a6cd9463abdebe4fdeae710d1005397a87683e0b`. Verify only the reduced help density and that no presentation regression was introduced. Then pass E and exactly one final protected Live/default NFC read for F. Keep PR #22 Draft; do not bump/merge/release v2.1 yet.
