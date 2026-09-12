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
- commit `67aafb713a0b09d23058b31640ba94bc0353bb21`
- Android CI `34705241442`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10300648753`
- artifact ZIP SHA-256 `77e244d729e0f6cca41d59a43f0f2e3624b32af02c066d3219e821164bd64134`
- APK SHA-256 `ee4ab5f352cef17891c3f1d29bce5d5e120956810df92f127955eb429e470678`
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

Confirmed real-device corrections include LOCAL period ownership/DST projection, empty-family false warning, fresh post-History verified time anchor, anchor-handover statistics bucket ownership, and natural-oldest retention handling.

Known physical reproductions:
- September 2026 Statistics = **10/30** fully-contained Day buckets;
- 10 September 2026 Statistics = **22/24** fully-contained Hour buckets;
- February no-Hour-data case warning-free;
- `Alle Zeiträume` natural-oldest false warning removed.

### Revised D — canonical real-time product surface

Physical product presentation with the single canonical real timeline was accepted. Raw meter/logger time remains secondary evidence.

## 5. Statistics / History / UI polish — implemented

- fully-contained Statistics buckets = filled bars and included in selected-window KPIs;
- partial edge overlaps = dashed outline bars using the complete measured archive delta as context only, never prorated and excluded from KPIs;
- genuine zero consumption = visible baseline marker; native gaps remain gaps;
- History -> All Live delta uses the chronologically nearest trustworthy earlier archive cumulative reading on the same meter across Hour/Day/Month; fallback to previous Live only if no earlier archive exists;
- dedicated Live filter remains Live-to-Live; archive cards remain same-family;
- one visible bar = one centered x-axis label; dense bar charts rotate the complete label set instead of skipping labels;
- user-facing combined date/time follows locale-aware `date · time`;
- ordinary local display prefers localized zone abbreviations, with DST-fold numeric disambiguation where necessary;
- History/Statistics page help is scoped to the actual page heading only, not drawer/menu repetitions;
- generic Statistics help is consolidated in the page info dialog; metric-specific caveats stay inline.

## 6. Timezone editor — searchable supported-zone picker

The former free-text IANA timezone editor is retired from the normal product UI.

Current behavior:

- Meter Details loads the runtime-supported timezone IDs from `ZoneId.getAvailableZoneIds()` and includes `UTC`;
- the list is sorted and shown through a searchable Material exposed dropdown;
- the current persisted zone is preselected;
- typing filters the choices, but Save accepts only an exact ID from the runtime-supported set;
- arbitrary unsupported/free-text IDs cannot be persisted through the normal UI;
- the selected ID is still normalized and persisted through `MeterTimeModelStore.setZone(..., ZONE_SOURCE_USER_SELECTED, ...)`;
- changing the presentation zone never rewrites raw meter/logger evidence or archive data.

Regression `MeterDetailsTimeZoneUiTest` protects the dropdown/list-only product contract and verifies no archive/history write path is involved.

This supersedes the old Section E smoke test that deliberately entered an invalid free-text IANA ID. The stronger product contract is now that the normal UI does not offer a path to persist such an invalid ID.

## 7. Current candidate change boundary

Compared with the previously physically spot-checked product behavior, the timezone-picker change touches only:

- `MeterDetailsActivity.java`;
- six localized `v2_meter_timezone_strings.xml` files;
- `MeterDetailsTimeZoneUiTest.java`.

Protected `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state-machine behavior remain untouched.

## 8. Remaining physical checks on `67aafb7...`

No History sync or NFC contact is required for the UI checks.

1. Confirm the earlier help-density cleanup remains correct: no drawer/menu info-icon duplicates; one page-level info affordance on History and Statistics; no duplicated generic Statistics paragraph.
2. Meter Details -> Time zone: tapping the row opens the searchable dropdown; current zone is preselected; typing e.g. `Berlin` filters to matching supported IDs; selecting `Europe/Berlin` and saving works.
3. Confirm an arbitrary unsupported typed value cannot be saved and the current valid zone is not replaced. This is optional defensive UI confirmation rather than the old free-text product flow.
4. Quick regression spot-check: History-All delta, Statistics bars/labels, `date · time` and zone display remain intact.
5. **F — final protected Live regression**: exactly one normal Live/default NFC read; no automatic History sync, no archive-state leak, no protocol regression.

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

First physically confirm the timezone picker and final UI state, then perform F. After that prepare version metadata/changelog/release notes and the exact signed v2.1.0 RC, and physically accept that exact RC before publication.

## 11. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Deferred for the next app version — complete chart-axis audit

Do **not** churn the current v2.1 release candidate only for this deferred polish item. At the start of the next app version, audit every Statistics chart so the x-axis label behavior is not only correct for consumption.

Required scope includes consumption, battery, flow, water temperature, ambient temperature and any alarm/event chart with an x-axis. Battery and flow are explicit spot-check targets because inconsistent/leftover vertical x-axis text was noticed during the current physical review.

The next-version acceptance rule is global rather than metric-by-metric and now includes an explicit **too-dense fallback**:

- if the complete visible label set fits horizontally, render it horizontally;
- otherwise rotate the complete set together and reserve enough chart height;
- if even the rotated/vertical labels no longer have enough horizontal slot width and would visually merge into a continuous text block, hide the x-axis labels for that chart state entirely;
- calculate this from real plot width, visible item count, measured text/font scale and minimum spacing, not only from a hard-coded number of periods;
- do not arbitrarily skip isolated bar labels or mix horizontal and vertical labels in one chart state;
- hiding labels is presentation-only: never drop, merge or aggregate chart data just to make the axis fit;
- line charts may thin labels only while point ownership remains visually unambiguous; if the remaining labels still collide, hide them;
- explicitly test very long selected ranges, because on-device review showed that dense labels can otherwise merge into one unreadable block.

Prefer centralizing this in the shared chart renderer/policy and protect it with regression tests so consumption, battery, flow and future metrics inherit the same behavior automatically.

This deferred item is also recorded in `CURRENT_STATE.json` and `UX_CONTEXTUAL_HELP_CONTRACT.md` so a future chat must carry it forward.

## Next action

Install exact CI-green candidate `67aafb713a0b09d23058b31640ba94bc0353bb21`. Verify the searchable supported-zone picker and quick UI regressions without History sync/NFC. Then perform exactly one final protected Live/default NFC read for F. Keep PR #22 Draft; do not bump/merge/release v2.1 yet.
