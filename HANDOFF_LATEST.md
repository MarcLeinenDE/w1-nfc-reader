# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-12

This is the canonical coordination handoff for W1 NFC Reader v2.1. Repository state, GitHub Actions and real-device evidence are authoritative over stale chat history.

## 1. Required reading order

Read strictly in this order before changing implementation:

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `UX_CONTEXTUAL_HELP_CONTRACT.md` on this handoff branch
4. `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` on `dev/v2.1.0-real-time-timeline`
5. `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md` on the dev branch
6. `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md` on the dev branch
7. `docs/V2_1_REAL_DEVICE_VALIDATION.md` on the dev branch — old Section D is superseded by the canonical-real-time addendum
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

## 2. Repository / current exact physical candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current exact candidate:
- `e6967885315283ed7943bb77b589b78d855fc6c1`
- Android CI `34702429499`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10300264718`
- artifact ZIP SHA-256 `4dee21419a179d994ee103f0170ede066edb3f095da0441e2eb08f6590d3bdfa`
- APK SHA-256 `bd7727fbe1cf940633939294c3516746dff2cedda56e28364503e2bace999336`
- downloaded artifact independently re-hashed; ZIP digest matches GitHub and APK matches `SHA256SUMS.txt`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until remaining physical gates and the exact signed RC acceptance pass.

## 3. Accepted product decision — one canonical real timeline

The former concept of two equal normal-product presentation modes (`LOCAL` and raw `METER`) is retired.

Normal v2.1 UI has **one canonical real timeline**:

- Live primary time = actual acquisition epoch.
- Archive primary time = verified Live/default anchor + monotonic `ON_TIME` -> canonical UTC -> persisted per-meter IANA-zone presentation.
- Raw meter/logger wall-clock remains preserved evidence and may be shown secondarily, in diagnostics, export and backup.
- The former global `Local time / Meter time` selector is removed from normal Settings.
- Old/restored development state containing `METER` is normalized to `LOCAL` on normal product Activity startup.
- If reconstruction is unsafe/unavailable, fail closed. Never silently promote raw meter clock to canonical real time.
- `ON_TIME` alone is not a civil timestamp; a suitable verified anchor is required.

No NFC command, parser, mailbox, traversal, overlap or terminal semantics were changed by this product decision.

## 4. Physical validation completed so far

### A

Debug app installed and usable; no coexistence problem. A completely fresh-state default check was not explicitly recorded and can be done on the exact signed RC without deleting current test data.

### B — PASS

Protected normal Live NFC read passed on the real Qalcosonic W1. No automatic History sync. `Europe/Berlin` zone/provenance and localized meter time were visible. Final protected regression is repeated in F.

### C — PASS on real reproduction cases

Physical validation found and corrected five distinct issues:

1. LOCAL period ownership / DST projection error.
2. False warning from an empty/out-of-range archive family.
3. Final History Default/Live verification was not persisted as a fresh time anchor.
4. A native-valid statistics bucket could be dropped when two boundaries used different verified anchors.
5. `Alle Zeiträume` could warn solely because the oldest retained archive boundary naturally has no stored predecessor.

Confirmed after fixes:

- fresh post-History time anchors work;
- September 2026 Statistics = **10/30** fully-contained Day buckets;
- 10 September Statistics = **22/24** fully-contained Hour buckets;
- February no-Hour-data case has normal empty state without false red warning;
- `Alle Zeiträume` no longer shows the natural-oldest false warning;
- History/Statistics values and chronology are plausible on real data.

### Revised D — canonical-real-time product surface

Candidate `205720b5bb70cd8d0436d43a5de805aa0dc72b33` removed the normal Settings time-basis selector and made reconstructed real time the single primary product timeline. Physical product presentation was accepted so far. Raw meter/logger time remains secondary evidence.

Current candidate `e696788...` inherits the same protected time model and adds only downstream History/Statistics/UI presentation behavior.

## 5. Statistics edge-interval presentation — implemented

The earlier `22/24` result was mathematically correct but visually looked like missing data because physical Hour intervals crossing civil-window edges were omitted from the chart.

Implemented behavior:

- fully-contained archive buckets are filled bars and are the only buckets included in selected-window total/average/minimum/maximum and `availableBuckets`;
- real physical archive intervals that overlap the selected civil window only partly are visible as **dashed outline bars** using their complete measured archive delta;
- partial-edge values are never prorated and are excluded from selected-window KPIs;
- genuine zero-consumption buckets receive a visible baseline marker;
- known native gaps remain gaps and are not fabricated into overlap bars;
- behavior is generic for Hour/Day/Month consumption statistics;
- coverage wording says **fully contained**.

Regression `StatisticsPartialEdgePresentationTest` protects these semantics.

## 6. Combined History / UI polish — implemented in current candidate

The remaining agreed polish is now folded into `e696788...`:

### History -> All Live baseline

Only in the mixed **History -> All** view, each visible Live card uses the **chronologically nearest trustworthy earlier archive cumulative reading** as its consumption baseline, regardless of Hour/Day/Month granularity.

Rules:
- same physical meter only;
- candidate archive row must be strictly earlier in canonical chronology;
- conflicted archive rows are skipped;
- nearest trustworthy archive row wins — no fixed Hour > Day > Month priority;
- if no earlier archive row exists, retain the previous-Live fallback;
- dedicated Live filter remains Live-to-Live;
- archive cards keep same-family archive delta semantics;
- a negative/unsafe resulting delta is not fabricated.

New regressions: `HistoryAllLiveBaselineTest` plus updated `LiveHistoryDeltaTest`.

### Statistics x-axis ownership

- every visible bar receives exactly one x-axis period label;
- labels remain centered on their bar slot;
- when labels no longer fit horizontally, all bar labels rotate 90 degrees together instead of skipping labels;
- chart height reserves extra room for the rotated labels.

This is specifically intended to remove ambiguity over which Hour/Day/Month period belongs to which bar.

### Date/time convention

Normal product UI now applies a shared locale-aware **`date · time`** convention for visible combined date/time strings. A central presentation hardening pass covers existing surfaces instead of independent one-off fixes.

### Timezone labels

Ordinary resolved local-time display now prefers localized zone abbreviations, e.g. German `MEZ/MESZ` or English `CET/CEST`, instead of ordinary numeric offsets. Exact IANA zone/offset truth is preserved internally. During ambiguous DST folds, the numeric offset remains included so duplicated wall-clock times stay distinguishable.

### Contextual info explanations

Localized Material info affordances are now attached where correct behavior is technically non-obvious:

- History — explains mixed All Live baseline versus dedicated Live/archive-series semantics;
- Statistics — explains filled full buckets, dashed edge intervals, KPI inclusion, zero-consumption marker and true gaps;
- Meter Details time model — explains canonical reconstructed real time versus raw meter/logger clock evidence.

The info affordances are clickable/focusable, have minimum touch height and accessibility descriptions. Warnings and actions remain primary UI and are not hidden behind help text. Help copy exists in all six product locales.

## 7. Current-candidate change boundary

Compare `4f388f8fb9f3d759dbb8cc00be0e2c673d6f958f` -> `e6967885315283ed7943bb77b589b78d855fc6c1`:

Changed only:
- `HistoryStatisticsAnalytics.java`
- `HistoryTimePresentation.java`
- `ProductUiHardening.java`
- `V2MetricChartView.java`
- six localized polish string files
- tests for the new presentation/History contracts.

Protected `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state-machine behavior are untouched.

## 8. Remaining physical checks on `e696788...`

No History sync is required for presentation checks.

1. **History -> Alle**: Live card must compare against the nearest earlier historical archive row; reference time/type must look plausible.
2. **Statistics 10 September 2026**: full bars solid, edge overlaps dashed, zero buckets visible, coverage still **22/24 fully contained**, KPIs unchanged.
3. **Axis labels**: every visible bar must map clearly to one period; dense Hour labels may be vertical.
4. Spot-check September Month/Day statistics for unchanged values and sensible labels.
5. Spot-check `date · time` on Overview, History and Meter Details.
6. Spot-check normal localized timezone abbreviations and DST ambiguity safety.
7. Tap the History, Statistics and Meter Details time-model info affordances; localized dialogs must be readable and non-blocking.
8. **E — invalid timezone smoke**: enter an obviously invalid IANA zone in Meter Details. Save must be rejected, dialog stays open and existing valid zone remains unchanged.
9. **F — final protected normal Live regression**: perform exactly one normal Live NFC read. Expected: Live/default read succeeds; plausible real/raw timestamps; no automatic History sync; no archive state leak; no protocol regression.

## 9. Safety boundary

Remain protected:

- normal NFC contact = fast Live/default read only;
- History begins only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive observations persist immediately;
- COMPLETE requires semantic terminal plus verified final Default Restore/Live;
- incremental `KNOWN_RECORD_REACHED` requires secure overlap; timestamp-only overlap remains forbidden;
- semantic terminal/overlap behavior remains authoritative; manufacturer capacities are not traversal limits;
- raw Type-F/logger time, ON_TIME and occurrence identity are never overwritten by derived UTC/LOCAL presentation;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state machine remain protected.

## 10. Release gate

Do **not** yet:

- bump stable version to 2.1.0;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag or release.

First physically confirm the combined candidate, including E and F. Then prepare version metadata/changelog/release notes and the exact signed v2.1.0 RC, and physically accept that exact RC before publication.

## 11. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Next action

Install exact CI-green candidate `e6967885315283ed7943bb77b589b78d855fc6c1`. First validate the presentation/UI checks without a new History sync. Then perform E and exactly one final protected Live/default NFC read for F. Keep PR #22 Draft; do not bump/merge/release v2.1 yet.
