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
6. `docs/V2_1_REAL_DEVICE_VALIDATION.md` on `dev/v2.1.0-real-time-timeline` — Section D is superseded by the addendum above
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

## 2. Repository / candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current functional head / exact next physical candidate:
- `205720b5bb70cd8d0436d43a5de805aa0dc72b33`
- Android CI `34693507298`: **SUCCESS**
- translations: **227 keys / 6 locales PASS**
- unit/Robolectric suite: **PASS** including canonical-real-time product contract
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10298341211`
- artifact ZIP SHA-256 `65ffcad2c5a29209d472fb3757fafe9931f8488127901cca232e5f7d4e38412a`
- APK SHA-256 `122965a45956013b44df4e8dde6cacfac5c573cf0bdfa38cf190a384d9555a86`
- downloaded artifact independently re-hashed; ZIP digest matches GitHub and APK matches `SHA256SUMS.txt`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- must remain Draft until revised D, E, F and the final signed RC gate pass.

## 3. Accepted product decision — one real timeline

The former concept of two equal normal-product presentation modes (`LOCAL` and raw `METER`) is retired.

Normal v2.1 UI now has **one canonical real timeline**:

- Live primary time = actual acquisition epoch.
- Archive primary time = verified Live/default anchor + monotonic `ON_TIME` -> canonical UTC -> persisted per-meter IANA-zone presentation.
- Raw meter/logger wall-clock remains preserved evidence and may be shown secondarily, in diagnostics, export and backup.
- The former global `Local time / Meter time` selector is removed from normal Settings.
- Old/restored development state that still contains `METER` is normalized to `LOCAL` when a normal product Activity starts.
- If reconstruction is unsafe/unavailable, fail closed. Never silently promote raw meter clock to canonical real time.
- `ON_TIME` by itself is not a civil timestamp; a suitable verified anchor is required.

This directly addresses meter-clock drift and is also the intended future Home Assistant contract: canonical UTC interval identity plus IANA projection; raw meter time only as auxiliary evidence.

No NFC command, parser, mailbox, traversal, overlap or terminal semantics were changed by this product decision.

## 4. Physical validation completed so far

### A

Debug app installed and usable; no coexistence problem. A completely fresh-state default check was not explicitly recorded and can be done on the final exact release candidate without deleting current test data.

### B — PASS

Protected normal Live NFC read passed on the real Qalcosonic W1. No automatic History sync. `Europe/Berlin` zone/provenance and localized meter time were visible. Final protected regression is still repeated in F.

### C — PASS on the real reproduction cases

Physical validation found and corrected five distinct issues:

1. LOCAL period ownership / DST projection error.
2. False warning from an empty/out-of-range archive family.
3. Final History Default/Live verification was not persisted as a fresh time anchor.
4. A native-valid statistics bucket could be dropped when two boundaries used different verified anchors.
5. `Alle Zeiträume` could warn solely because the oldest retained archive boundary naturally has no stored predecessor.

Current real-device evidence after the fixes:

- fresh post-History time anchors work;
- September 2026 Statistics = **10/30** completed Day buckets;
- 10 September Statistics = **22/24** fully-contained Hour buckets;
- `22/24` is expected because two physical Hour intervals cut the LOCAL civil-day edges and are not fractionally invented;
- February no-Hour-data case has normal empty state without false red warning;
- `Alle Zeiträume` no longer shows the natural-oldest false warning;
- History/Statistics values and chronology are plausible on the user's real backup/data.

## 5. Revised D gate

The old `LOCAL ↔ METER` switching gate is superseded by `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md`.

With exact candidate `205720b...`, check without needing a new History sync:

- Settings no longer exposes the former global time-basis selector;
- Overview uses real time as primary and raw meter time as secondary evidence;
- History → Live uses actual acquisition time primary and raw meter time secondary;
- Hour/Day/Month use reconstructed real/civil intervals primary and raw logger time secondary;
- existing values/identity remain unchanged;
- old/restored METER preference cannot leave the normal UI on raw meter primary time;
- navigation itself triggers no History sync or NFC work.

## 6. Remaining functional gates

### E — invalid timezone smoke

Open Meter Details -> timezone. Enter an obviously invalid IANA value. Save must be rejected; dialog stays open and existing valid zone remains unchanged. No successful manual zone replacement is required.

### F — final protected normal Live regression

Return to Overview and perform exactly one normal Live NFC read. Expected:

- normal Live/default read succeeds;
- real and raw meter timestamps are plausible and locale formatted;
- no archive state leaks into Live;
- no History synchronization starts automatically;
- no protocol regression.

## 7. Post-functional-gate UI / UX polish

After revised D + E + F pass, do one combined polish candidate before stable release preparation:

- repository-wide user-facing date/time audit; whenever one instant contains date + time use a shared locale-aware `date · time` presentation convention;
- contextual `i` explanations everywhere technically correct behavior is not self-evident;
- explain canonical real-time reconstruction versus raw meter-clock evidence;
- Statistics wording must distinguish fully-contained buckets, edge intervals and actual gaps where the model can distinguish them;
- locale-aware timezone abbreviations such as German MEZ/MESZ or English CET/CEST instead of exposing only `+01:00/+02:00` in ordinary UI; exact IANA zone/offset remains diagnostic truth;
- visible non-misleading marker for genuine zero-consumption chart buckets;
- `History -> All` Live delta baseline: nearest trustworthy previous archive observation regardless of Hour/Day/Month; if no archive predecessor exists, fall back to previous Live; otherwise no invented delta.

Do not implement these by hiding real failures behind help text. Primary wording must itself be correct.

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

## 9. Release gate

Do **not** yet:

- bump stable version to 2.1.0;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag or release.

First pass revised D, E and F on `205720b...`. Then implement the combined UI/i18n/accessibility polish, prepare version metadata/changelog/release notes and the exact signed v2.1.0 release candidate, and physically accept that exact candidate before publication.

## 10. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:

- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.

## Next action

Install exact CI-green candidate `205720b5bb70cd8d0436d43a5de805aa0dc72b33` and perform the revised D checks. Then E and F. Keep PR #22 Draft.
