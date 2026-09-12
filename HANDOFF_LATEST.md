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
- commit `92c7c24e717a8e250c87385eb88faace08d0449c`
- Android CI `34713492978`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS — 227 keys across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10304500771`
- artifact ZIP SHA-256 `1c56163329ef28ed15d47d5f3240f49bc8845e7944565cc811b8dc9983b3c519`
- APK SHA-256 `26c96aa8dd66561acbc671f6f34ef27f1737875890ec929a777ba17752493e74`
- artifact independently downloaded/re-hashed; ZIP digest matches GitHub and APK matches `SHA256SUMS.txt`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until remaining physical gates and exact signed RC acceptance pass.

## 3. Accepted product decision — one canonical real timeline with one active anchor

Normal v2.1 UI has one canonical real timeline:

- Live primary time = actual acquisition epoch.
- Archive primary time = **single newest verified Live/default anchor per physical meter** + monotonic `ON_TIME` -> canonical UTC -> persisted per-meter IANA-zone presentation.
- `ON_TIME` defines native timeline geometry; the active anchor only places that geometry on the real UTC axis.
- Raw meter/logger wall-clock remains preserved secondary/diagnostic/export/backup evidence.
- Former global `Local time / Meter time` selector is removed from normal Settings.
- Legacy/restored `METER` preference is normalized back to the canonical product path.
- Unsafe reconstruction fails closed; raw meter clock is never silently promoted to canonical real time.

Every accepted normal Live/default read can replace the active anchor when its `ON_TIME` advances. The already-required verified final Default/Live read after each History family likewise refreshes the active anchor. All Hour/Day/Month/Year archive boundaries for that meter are then projected backwards from this same anchor.

An archive `ON_TIME` newer than the active anchor is an invariant failure and remains unresolved (`ON_TIME_AFTER_ANCHOR`) rather than falling back to another historical anchor. Backwards/reset `ON_TIME` likewise must not silently replace the active timeline.

Legacy time-model databases/backups containing several anchors remain readable. Projection exposes only the newest active anchor; a later accepted anchor write or import normalization collapses that meter to one active anchor.

## 4. New release-blocking physical finding and correction

After the previously accepted canonical-time UI pass, a fresh real-device History read exposed one Hour card whose reconstructed interval was longer than one hour.

Private backup evidence confirmed the underlying native archive was correct: consecutive Hour occurrences had exact 3,600-second `ON_TIME` spacing and no archive conflict. The defect was entirely in the app projection layer: the former boundary-by-boundary anchor selection could resolve the start boundary from one historical Live anchor and the end boundary from a newer anchor. Small phase differences between those Live anchors then stretched/compressed a physical archive interval.

This behavior is superseded by the single-active-anchor contract above. Regression coverage now requires exact native duration preservation for Hour, Day, Month and Year even when historical anchors disagree.

Protected NFC/parser/archive traversal code was not changed for this correction.

## 5. Physical validation completed so far

### A

Debug app installed and usable. A completely fresh-state default check was not explicitly recorded and may be done on the final exact signed RC without deleting current test data.

### B — PASS

Protected normal Live NFC read passed on the real Qalcosonic W1. No automatic History sync. Final protected regression is repeated in F.

### C — previous cases PASS; new single-anchor regression pending physical confirmation

Previously confirmed corrections include LOCAL period ownership/DST projection, empty-family false warning, fresh post-History verified time anchor, statistics bucket ownership and natural-oldest retention handling.

Known physical reproductions:
- September 2026 Statistics = **10/30** fully-contained Day buckets;
- 10 September 2026 Statistics = **22/24** fully-contained Hour buckets;
- February no-Hour-data case warning-free;
- `Alle Zeiträume` natural-oldest false warning removed.

The newly discovered stretched-Hour defect is code-fixed and CI-green on `92c7c24...`, but must be physically rechecked before F/RC.

### Revised D — canonical real-time product surface

Physical product presentation with the canonical real timeline was accepted. Raw meter/logger time remains secondary evidence. The projection engine underneath it has since moved to the stricter single-active-anchor model described above.

## 6. Statistics / History / UI polish — implemented

- fully-contained Statistics buckets = filled bars and included in selected-window KPIs;
- partial edge overlaps = dashed outline bars using the complete measured archive delta as context only, never prorated and excluded from KPIs;
- genuine zero consumption = visible baseline marker; native gaps remain gaps;
- History -> All Live delta uses the chronologically nearest trustworthy earlier archive cumulative reading on the same meter across Hour/Day/Month; fallback to previous Live only if no earlier archive exists;
- dedicated Live filter remains Live-to-Live; archive cards remain same-family;
- one visible consumption bar = one centered x-axis label; dense consumption bars rotate the complete label set instead of skipping labels;
- user-facing combined date/time follows locale-aware `date · time`;
- ordinary local display prefers localized zone abbreviations, with DST-fold numeric disambiguation where necessary;
- History/Statistics page help is scoped to the actual page heading only, not drawer/menu repetitions;
- generic Statistics help is consolidated in the page info dialog; metric-specific caveats stay inline.

## 7. Timezone editor — searchable supported-zone picker

The former free-text IANA timezone editor is retired from the normal product UI.

- Meter Details loads runtime-supported timezone IDs from `ZoneId.getAvailableZoneIds()` and includes `UTC`;
- sorted searchable Material exposed dropdown;
- current persisted zone preselected;
- Save accepts only an exact runtime-supported ID;
- arbitrary unsupported/free-text IDs cannot be persisted through normal UI;
- changing presentation zone never rewrites raw meter/logger evidence or archive data.

Regression `MeterDetailsTimeZoneUiTest` protects this contract.

## 8. Future Home Assistant / integration identity contract

Do **not** use reconstructed canonical UTC as the duplicate identity. A newer active anchor may legitimately shift the absolute UTC placement of an older physical archive occurrence while the occurrence itself is unchanged.

Future integration should export a deterministic, versioned meter-native `source_record_id`, reproducible on a new phone without any app backup. Existing building blocks already support this:

- physical meter identity;
- archive family (Hour/Day/Month/Year);
- `ArchiveOccurrenceKey`, normally `OT:<on_time_seconds>` when valid `ON_TIME` exists;
- raw logger timestamp / `ArchiveRecordIdentity` as stronger collision evidence;
- explicit generation/reset discriminator if the same physical identity ever reuses `ON_TIME` after a reset.

Suggested semantic identity shape: `w1:v1:<meter-identity>:<family>:<occurrence-key>` with stronger evidence available for collision protection.

Home Assistant must enforce idempotence server-side through a unique key / UPSERT / ignore-known-ID contract. An app-local “already sent” set may optimize traffic but must never be the authority. Therefore a fresh phone can reread and resend the retained meter archive; the same physical occurrences regenerate the same IDs and Home Assistant does not create duplicates.

`ON_TIME` alone is not globally unique: different meters and different archive families can legitimately share the same value, and a real reset/generation scenario could eventually reuse it.

## 9. Safety boundary

Remain protected:

- normal NFC contact = fast Live/default read only;
- History starts only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive observations persist immediately;
- COMPLETE requires semantic terminal plus verified final Default Restore/Live;
- incremental `KNOWN_RECORD_REACHED` requires secure overlap; timestamp-only overlap forbidden;
- manufacturer capacities are not traversal limits;
- raw Type-F/logger time, `ON_TIME` and occurrence identity are never overwritten by derived presentation;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state machine remain protected.

## 10. Release gate

Do **not** yet:

- bump stable version to 2.1.0;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag or release.

First physically confirm the single-active-anchor regression on the exact CI-green candidate, then perform F. After that prepare version metadata/changelog/release notes and the exact signed v2.1.0 RC, and physically accept that exact RC before publication.

## 11. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Deferred for the next app version — complete chart-axis audit

Do **not** churn the current v2.1 release candidate only for this deferred polish item. At the start of the next app version, audit every Statistics chart so the x-axis label behavior is not only correct for consumption.

Required scope includes consumption, battery, flow, water temperature, ambient temperature and any alarm/event chart with an x-axis. Battery and flow are explicit spot-check targets.

Global acceptance rule:

- if the complete visible label set fits horizontally, render it horizontally;
- otherwise rotate the complete set together and reserve enough chart height;
- if even rotated/vertical labels would merge into an unreadable continuous text block, hide the x-axis labels for that chart state entirely;
- calculate from real plot width, visible item count, measured text/font scale and minimum spacing, not only a hard-coded number of periods;
- do not arbitrarily skip isolated bar labels or mix horizontal/vertical labels in one chart state;
- hiding labels is presentation-only: never drop, merge or aggregate chart data to make the axis fit;
- line charts may thin labels only while point ownership remains visually unambiguous; if remaining labels still collide, hide them;
- explicitly test very long selected ranges.

Prefer centralizing this in the shared chart renderer/policy and protect it with regression tests.

## Next action

Install exact CI-green candidate `92c7c24e717a8e250c87385eb88faace08d0449c`. No new History sync/NFC is required for the first check. Reopen the History range that previously showed an Hour interval longer than one hour.

Expected: every valid consecutive Hour archive interval is exactly one hour long, with no stretch/compression at former historical-anchor handovers. The absolute clock labels may shift slightly as one coherent timeline because the newest active anchor now places the whole native `ON_TIME` geometry.

If this physical regression passes, complete any remaining timezone-picker UI spot-check if needed and then perform exactly one final protected Live/default NFC read for F. Keep PR #22 Draft; do not bump/merge/release v2.1 yet.
