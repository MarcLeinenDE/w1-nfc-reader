# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-12

This is the canonical coordination handoff for W1 NFC Reader v2.1. Repository state, GitHub Actions and real-device evidence are authoritative over stale chat history.

## 1. Required reading order

Read strictly in this order before changing implementation:

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` on `dev/v2.1.0-real-time-timeline`
4. `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md` on `dev/v2.1.0-real-time-timeline`
5. `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md` on `dev/v2.1.0-real-time-timeline`
6. `docs/V2_1_REAL_DEVICE_VALIDATION.md` on `dev/v2.1.0-real-time-timeline` — old Section D is superseded by the canonical-real-time addendum
7. `docs/product-decisions/v2.1-real-time-timeline-and-coverage.md` on `main`
8. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
9. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
10. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
11. `docs/V2_BREAKING_CHANGES.md`
12. `docs/PROTOCOL_SAFETY.md`
13. `docs/research/PUBLIC_QW1_EVIDENCE.md`
14. `docs/RELEASING.md`
15. `CHANGELOG.md`
16. `AGENTS.md`
17. `UX_CONTEXTUAL_HELP_CONTRACT.md` on this handoff branch

The handoff branch is coordination-only. Development stays on the dev branch.

## 2. Repository / current exact APK candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current exact functional candidate:
- `4f388f8fb9f3d759dbb8cc00be0e2c673d6f958f`
- Android CI `34696006781`: **SUCCESS**
- translations: **227 keys / 6 locales PASS**
- unit/Robolectric suite: **PASS**, including partial-edge statistics regression
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10299310144`
- artifact ZIP SHA-256 `6a2399514ac6d8bd4604e84d60316d85e528ad844780588fa1b04b70e36faf26`
- APK SHA-256 `bef486df8318a6d0baee79513b28cd4580d2c8c172e548226e4469151516861f`
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
- Old/restored development state that still contains `METER` is normalized to `LOCAL` when a normal product Activity starts.
- If reconstruction is unsafe/unavailable, fail closed. Never silently promote raw meter clock to canonical real time.
- `ON_TIME` by itself is not a civil timestamp; a suitable verified anchor is required.

This is also the intended future Home Assistant contract: canonical UTC interval identity plus IANA projection; raw meter time only as auxiliary evidence.

No NFC command, parser, mailbox, traversal, overlap or terminal semantics were changed by this product decision.

## 4. Physical validation completed so far

### A

Debug app installed and usable; no coexistence problem. A completely fresh-state default check was not explicitly recorded and can be done on the final exact release candidate without deleting current test data.

### B — PASS

Protected normal Live NFC read passed on the real Qalcosonic W1. No automatic History sync. `Europe/Berlin` zone/provenance and localized meter time were visible. Final protected regression is still repeated in F.

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
- History/Statistics values and chronology are plausible on real backup/data.

### Revised D — canonical-real-time product surface

Candidate `205720b5bb70cd8d0436d43a5de805aa0dc72b33` removed the normal Settings time-basis selector and made reconstructed real time the single primary product timeline. User physically reported the new product presentation **fits so far**. Raw meter/logger time remains secondary evidence.

The current `4f388f8...` candidate is the same protected time model plus Statistics edge-interval presentation; no NFC/protocol path changed.

## 5. Statistics edge-interval presentation — implemented, awaiting physical visual confirmation

The earlier `22/24` result was mathematically correct but visually looked like missing data because physical Hour intervals crossing the civil-day edges were omitted from the bar chart.

Current candidate `4f388f8...` changes **presentation only**:

- fully-contained archive buckets remain normal filled bars and are the only buckets included in selected-window total/average/minimum/maximum and the `availableBuckets` count;
- real physical archive intervals that overlap the selected civil window only partly are retained as chart context and drawn as **dashed outline bars** using their complete measured archive delta;
- those edge values are **not prorated** and are **not included in selected-window KPIs**;
- genuine zero-consumption buckets receive a visible baseline marker instead of disappearing;
- known missing native records remain gaps and are not fabricated into overlap bars;
- the same mechanism is generic for Hour/Day/Month consumption statistics;
- coverage wording now says **fully contained** instead of implying the other physical records are unavailable;
- the explanatory consumption note documents solid versus dashed bars in all six product locales.

Regression `StatisticsPartialEdgePresentationTest` proves that 24 physical chart intervals can remain visible while only 22 fully-contained intervals contribute to KPIs; deliberately large partial-edge values are excluded from totals/average/min/max.

## 6. Remaining physical checks

### Statistics overlap visual check

No new NFC contact is required. On current stored data, inspect the known 10 September 2026 statistics reproduction:

- fully-contained Hour bars stay solid;
- edge-overlapping physical Hour intervals are visible as dashed outlines;
- coverage still states **22/24 fully contained** for the known case;
- selected-period total/average/high/low do not change merely because edge bars are now visible;
- zero-consumption buckets are visibly distinguishable from missing data.

Also spot-check September Month/Day statistics for unchanged values and sensible edge presentation.

### E — invalid timezone smoke

Open Meter Details -> timezone. Enter an obviously invalid IANA value. Save must be rejected; dialog stays open and existing valid zone remains unchanged. No successful manual zone replacement is required.

### F — final protected normal Live regression

Return to Overview and perform exactly one normal Live NFC read. Expected:

- normal Live/default read succeeds;
- real and raw meter timestamps are plausible and locale formatted;
- no archive state leaks into Live;
- no History synchronization starts automatically;
- no protocol regression.

## 7. Remaining UI / UX polish

Before stable release preparation, perform the combined polish pass:

- repository-wide user-facing date/time audit; whenever one instant contains date + time use a shared locale-aware `date · time` convention;
- contextual `i` explanations wherever technically correct behavior is not self-evident;
- explain canonical real-time reconstruction versus raw meter-clock evidence;
- further distinguish fully-contained buckets, edge intervals and actual gaps wherever the model can expose those states directly;
- locale-aware timezone abbreviations such as German MEZ/MESZ or English CET/CEST instead of ordinary `+01:00/+02:00` labels; exact IANA zone/offset remains diagnostic truth;
- `History -> All` Live delta baseline: nearest trustworthy previous archive observation regardless of Hour/Day/Month; if no archive predecessor exists, fall back to previous Live; otherwise no invented delta.

Do not hide real failures behind help text. Primary wording must itself be correct.

## 8. Safety boundary

Remain protected:

- normal NFC contact = fast Live/default read only;
- History begins only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive observations persist immediately;
- COMPLETE requires semantic terminal plus verified final Default Restore/Live;
- incremental `KNOWN_RECORD_REACHED` requires secure overlap; timestamp-only overlap remains forbidden;
- semantic terminal/overlap behavior remains authoritative; public manufacturer capacities are not traversal limits;
- raw Type-F/logger time, ON_TIME and occurrence identity are never overwritten by derived UTC/LOCAL presentation;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state machine remain protected.

The diff from canonical-real-time functional candidate `205720b...` to `4f388f8...` touches only downstream History/Statistics read-model/chart code, six locale string files, tests and documentation; no protected NFC/parser/traversal file changed.

## 9. Release gate

Do **not** yet:

- bump stable version to 2.1.0;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag or release.

First physically confirm the new Statistics edge presentation, then pass E and F. After the remaining combined UI/i18n/accessibility polish, prepare version metadata/changelog/release notes and the exact signed v2.1.0 release candidate, and physically accept that exact candidate before publication.

## 10. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Next action

Install exact CI-green candidate `4f388f8fb9f3d759dbb8cc00be0e2c673d6f958f` and visually retest Statistics for 10 September 2026 and September 2026. No History sync/NFC contact is needed for that check. If the dashed edge bars and unchanged KPI semantics pass, continue with E and F. Keep PR #22 Draft.
