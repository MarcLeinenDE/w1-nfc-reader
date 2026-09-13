# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-13

This is the canonical coordination handoff for W1 NFC Reader v2.1. Repository state, GitHub Actions and real-device evidence are authoritative over stale chat history.

## 1. Required reading order

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `UX_CONTEXTUAL_HELP_CONTRACT.md` on this handoff branch
4. `ROADMAP.md` on `dev/v2.1.0-real-time-timeline`
5. `docs/product-decisions/v2.1-canonical-real-time-product-timeline.md` on `dev/v2.1.0-real-time-timeline`
6. `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md`
7. `docs/V2_1_CANONICAL_REAL_TIME_VALIDATION_ADDENDUM.md`
8. `docs/V2_1_REAL_DEVICE_VALIDATION.md`
9. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
10. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
11. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
12. `docs/PROTOCOL_SAFETY.md`
13. `docs/RELEASING.md`
14. `CHANGELOG.md`
15. `AGENTS.md`

`docs/product-decisions/v2.1-real-time-timeline-and-coverage.md` is retained only as an explicitly **SUPERSEDED historical proposal**. It is not authoritative.

The handoff branch is coordination-only. Development stays on the dev branch.

## 2. Repository / current exact functional candidate

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable main baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`
- stable release remains `v2.0.0` / versionCode `43`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current physically tested **functional code candidate**:
- commit `b38006f9dab270a574d7e08cb9e3785a231a2ef8`
- Android CI `34715215357`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS — 227 keys across 6 locales**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10304503134`
- artifact ZIP SHA-256 `cc48ffd4548fcff663583c1d722309b36584b4285de66ad24a3b4cfc1a367059`
- APK SHA-256 `4488e1b1d500cf52ff03cc4ab294a9152e5623a111133533e3cd3ef4003b1686`

The dev branch also contains documentation-only cleanup/roadmap commits after that functional code candidate. No app/runtime code changed in those documentation commits. Current dev documentation head: `24aece01684ee78c913b5fd6c98e273e1d4b29e4`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until final protected Live regression F and exact signed RC acceptance pass.

## 3. Accepted time model — one canonical timeline, one active anchor

- Live primary time = actual acquisition epoch.
- Archive primary time = **single newest fully verified Live/default anchor per physical meter** + monotonic `ON_TIME` -> canonical UTC -> persisted IANA-zone presentation.
- `ON_TIME` defines native timeline geometry; the active anchor only places the complete geometry on UTC.
- Every accepted normal Live/default read may replace the active anchor when `ON_TIME` advances.
- The already-required verified final Default/Live read after each History family likewise refreshes the active anchor.
- All Hour/Day/Month/Year boundaries for one meter project backwards from that same active anchor.
- Archive `ON_TIME` newer than active anchor fails closed.
- Backwards/reset `ON_TIME` cannot silently replace the active timeline.
- Raw meter/logger wall clock remains preserved secondary diagnostic/export/backup evidence.
- Unsafe reconstruction never silently promotes raw meter wall clock.

Legacy time-model databases/backups containing multiple anchors remain readable. Projection/export exposes only the active newest anchor; later accepted write/import normalization collapses the meter to one active anchor.

## 4. Stretched-Hour regression — fixed and physically accepted

A real-device History view previously showed one native Hour interval longer than one hour (`19:00–20:04`). Private backup evidence proved that the source archive itself had exact 3,600-second `ON_TIME` adjacency and no conflict.

Root cause was boundary-by-boundary selection of different historical Live anchors. The single-active-anchor implementation removes that handover.

Current invariant:
- native Hour `ON_TIME` difference of 3,600 seconds remains exactly 3,600 real seconds;
- analogous native duration preservation applies to Day/Month/Year;
- newer anchor may shift the complete reconstructed timeline coherently, but cannot stretch/compress individual intervals.

Physical retest of the current candidate was reported as good. This regression is no longer a release blocker.

## 5. Home Assistant source identity

`SourceRecordId` is implemented and CI-green.

External form:

`w1:v1:<sha256>`

Canonical input is preserved meter-native identity evidence:
- physical meter identity;
- archive family;
- raw logger timestamp;
- native occurrence key, normally `OT:<on_time_seconds>`.

The ID excludes derived UTC/local time, SQLite row IDs, retrieval time, phone/install identity and measurement content. A fresh phone rereading the same physical occurrence regenerates the same ID without a backup.

Future transport rule: **Android keeps no authoritative already-sent ledger.** It may resend every locally available eligible record. Home Assistant owns idempotence using `source_record_id` unique-key / UPSERT / no-op semantics.

## 6. Statistics x-axis — implemented and physically accepted

Shared `V2MetricChartView` behavior:
- bar metrics: full labels horizontal while they fit -> full set rotated when needed -> full set hidden before collision;
- line metrics: measured-fit thinning -> hide if the remaining set would still collide;
- actual plot width and rendered text/font metrics drive the decision;
- no data point/bar is dropped, merged or aggregated merely to make labels fit.

The current alarm metric is a textual event timeline, not an x-axis chart.

The user physically spot-checked the revised candidate and reported it looks good. The former “next version” chart-label deferral is closed.

## 7. Existing v2.1 product polish

- fully-contained Statistics buckets = filled and included in selected-window KPIs;
- partial edge overlaps = dashed full measured context only, never prorated and excluded from KPIs;
- genuine zero = visible baseline marker; native gaps remain gaps;
- History -> All Live delta uses chronologically nearest trustworthy earlier archive cumulative observation; previous-Live fallback only if no archive predecessor exists;
- `date · time` presentation is locale-aware and centralized;
- ordinary zone labels prefer localized abbreviations with DST-fold disambiguation;
- History/Statistics contextual help appears only at actual page headings; generic Statistics explanation is not duplicated in page body;
- timezone editor is a searchable runtime-supported IANA picker; arbitrary unsupported free text cannot persist.

## 8. Documentation cleanup completed before RC

On 2026-09-13 the public dev-branch documentation was aligned with the actual implemented product:

- the old dual `LOCAL / METER` product decision is explicitly marked superseded;
- `V2_1_TIME_MODEL_IMPLEMENTATION.md` now documents single-active-anchor projection, `SourceRecordId`, chart policy and the current release gate;
- the validation addendum now describes the constrained timezone picker instead of the obsolete free-text invalid-IANA test;
- `V2_1_REAL_DEVICE_VALIDATION.md` now reflects the current canonical timeline and final gate;
- root `ROADMAP.md` records the v2.1 release path, v2.2 cleanup/maintenance target and future v3.0 Home Assistant architecture.

No runtime/NFC/parser/archive code changed in this documentation pass.

## 9. Physical validation state

- A: partial pass; fresh/default-state check can be done on exact signed RC.
- B: protected normal Live NFC path passed earlier.
- C: accepted physical cases including Statistics/retention/warning fixes.
- D revised canonical real-time presentation: accepted.
- single-active-anchor stretched-Hour regression: **PASS** on current candidate.
- adaptive chart-axis spot-check: **PASS** on current candidate.
- timezone/help/presentation spot-check: no current blocker reported.
- F: **only remaining functional debug-candidate gate** — one final protected normal Live/default NFC read.

## 10. Safety boundary

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

## 11. Immediate next action / release gate

Do **not** yet bump to v2.1.0, mark PR #22 ready, merge, tag or release.

Next action:
1. perform exactly one final normal protected Live/default NFC read from Overview using the current installed functional candidate;
2. confirm read succeeds, canonical real time remains primary, raw meter time remains secondary, no automatic History sync starts and no protocol regression appears;
3. if F passes, prepare v2.1.0 version metadata/changelog/release notes and the exact signed RC;
4. physically accept that exact signed RC, including fresh/default-state smoke as appropriate;
5. only then mark PR ready, merge, tag and release.

## 12. Deferred v2.2 code cleanup / maintenance baseline

The actual code cleanup is intentionally deferred until after v2.1 release to avoid widening the RC regression surface.

v2.2 cleanup target:
- remove hidden `AppTimeBasis.METER` runtime/peer-mode branches;
- retain only minimal compatibility needed to read old development backup state and normalize it to canonical real time;
- remove obsolete `v2_time_basis_strings.xml` resources and tests that exist solely for the retired peer mode;
- audit other compatibility helpers/dead code for genuine redundancy;
- keep meaningful regression coverage for real bugs, protocol safety, DST, backup compatibility and archive traversal;
- complete a final repository hygiene pass and then treat the current local-NFC feature set as feature-complete/maintenance mode.

## 13. Planned v3.0 Home Assistant integration

`ROADMAP.md` is authoritative for the future integration direction.

Accepted high-level direction:
- v3.0 is the deliberate network-capability boundary; Home Assistant transport is not part of v2.x;
- local/cloud-free flow: `W1 -> NFC -> Android -> local connector -> Home Assistant`;
- Home Assistant custom integration creates a short-lived one-time pairing session and QR code;
- QR contains local connector/pairing information, **not Wi-Fi credentials** and not a normal HA administrator/long-lived user token;
- phone must be able to reach the HA connector over the same LAN/routable local network; mDNS discovery is optional convenience, not a requirement;
- successful pairing yields a dedicated least-privilege app/device credential;
- Android may resend all eligible local observations every time; HA deduplicates/UPSERTs by deterministic `source_record_id`;
- a fresh/replacement phone can pair again, reread the meter and resend retained history without creating duplicates;
- multiple independently paired phones are allowed;
- current state becomes normal HA entities where appropriate; historical archive data is intended for HA long-term statistics rather than thousands of permanent entities;
- v3.0 will intentionally add Android network access/`INTERNET` permission; v2.x remains local NFC only.

Exact HA APIs/storage interfaces must be revalidated against the then-current Home Assistant platform before implementation.

## 14. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.
