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

Current exact **functional code candidate**:
- commit `571e225e17f69d94ec6b09b92d748ef3c712939b`
- Android CI `34744809727`: **SUCCESS**
- unit/Robolectric suite: **PASS**
- product translations: **PASS**
- debug build/signature/hash/artifact upload: **PASS**
- artifact `w1-nfc-reader-debug`
- artifact id `10313108673`
- artifact ZIP SHA-256 `63260084c3648ee7d7c118a2edc3526a15795d7f6abc1eab48e614203451d5c4`
- APK SHA-256 `81b16bd97205b565e5fc4fa00b0ecd42d162ab934c3da544f7933738cd927baa`

The dev branch contains documentation-only commits after that functional code candidate. Current dev documentation head after the Live-delta documentation alignment: `b8655ebf5351f6d1bf652250a6800ebd53eae46a`.

Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- remains Draft until the corrected mixed-History Live baseline is physically spot-checked and the exact signed RC is accepted.

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

Physical retest was reported as good. This regression is no longer a release blocker.

## 5. Mixed History `All` Live delta — chronological predecessor rule

A final protected Live read on 2026-09-13 succeeded, but its History card exposed a downstream presentation defect: when two Live reads followed one another after an archive point, the newer Live card still preferred the older archive baseline.

Accepted rule:
- in mixed `History -> All`, a visible Live card uses the chronologically nearest trustworthy earlier **cumulative observation** on the same physical meter;
- valid Live, Hour, Day and Month observations participate together;
- chronology wins; there is no preference for archive over Live and no archive-family priority;
- therefore consecutive Live reads without an archive occurrence between them use Live-to-Live consumption;
- do not cross meter replacement, known conflicts or unsafe chronology;
- archive cards keep same-family archive-series semantics; dedicated Live remains Live-to-Live.

Implementation:
- `HistoryStatisticsAnalytics.nearestTrustworthyPredecessor(...)` now considers both Live and archive cumulative observations;
- `HistoryAllLiveBaselineTest` explicitly covers `archive -> live1 -> live2` and requires `live2` to use `live1` as predecessor;
- a negative/backwards total at the nearest trustworthy predecessor remains fail-closed and is not silently re-baselined to an older source.

CI for this fix is green at functional commit `571e225e...`. The fix is downstream History analytics/test code only; NFC/parser/mailbox/archive acquisition code is untouched.

Physical spot-check still required: install the current candidate and reopen the already stored consecutive Live reads. The newest Live card must say consumption **since the immediately preceding Live read**, not since the older archive interval. No additional NFC contact is required.

## 6. Home Assistant source identity

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

## 7. Statistics x-axis — implemented and physically accepted

Shared `V2MetricChartView` behavior:
- bar metrics: full labels horizontal while they fit -> full set rotated when needed -> full set hidden before collision;
- line metrics: measured-fit thinning -> hide if the remaining set would still collide;
- actual plot width and rendered text/font metrics drive the decision;
- no data point/bar is dropped, merged or aggregated merely to make labels fit.

The current alarm metric is a textual event timeline, not an x-axis chart.

The user physically spot-checked the revised candidate and reported it looks good. The former “next version” chart-label deferral is closed.

## 8. Existing v2.1 product polish

- fully-contained Statistics buckets = filled and included in selected-window KPIs;
- partial edge overlaps = dashed full measured context only, never prorated and excluded from KPIs;
- genuine zero = visible baseline marker; native gaps remain gaps;
- `date · time` presentation is locale-aware and centralized;
- ordinary zone labels prefer localized abbreviations with DST-fold disambiguation;
- History/Statistics contextual help appears only at actual page headings; generic Statistics explanation is not duplicated in page body;
- timezone editor is a searchable runtime-supported IANA picker; arbitrary unsupported free text cannot persist.

## 9. Documentation / roadmap

The public dev-branch documentation is aligned with the implemented product:
- old dual `LOCAL / METER` product decision explicitly superseded;
- time model documents single-active-anchor projection, `SourceRecordId`, chart policy and chronological Live baseline;
- validation documents use the constrained timezone picker rather than obsolete invalid-free-text flow;
- root `ROADMAP.md` records v2.1 release, v2.2 cleanup/maintenance and planned v3.0 Home Assistant connector/QR pairing.

## 10. Physical validation state

- A: partial pass; fresh/default-state check can be done on exact signed RC.
- B: protected normal Live NFC path passed earlier.
- C: accepted physical cases including Statistics/retention/warning fixes.
- D revised canonical real-time presentation: accepted.
- single-active-anchor stretched-Hour regression: **PASS**.
- adaptive chart-axis spot-check: **PASS**.
- timezone/help/presentation spot-check: no current blocker reported.
- F final protected normal Live/default NFC regression: **PASS on 2026-09-13**; read succeeded and no automatic History sync/protocol regression was reported.
- downstream consecutive-Live History baseline: code-fixed/CI-green; **physical spot-check pending on current candidate using stored data**.

## 11. Safety boundary

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

## 12. Immediate next action / release gate

Do **not** yet bump to v2.1.0, mark PR #22 ready, merge, tag or release.

Next action:
1. install exact functional candidate `571e225e...`;
2. reopen the already stored consecutive Live reads in `History -> All`;
3. confirm the newest Live delta is based on the immediately previous Live observation when no newer archive point lies between them;
4. no new NFC read is required because F already passed and this fix is downstream analytics only;
5. if the spot-check passes, prepare v2.1.0 version metadata/changelog/release notes and exact signed RC;
6. physically accept that exact signed RC, including fresh/default-state smoke as appropriate;
7. only then mark PR ready, merge, tag and release.

## 13. Deferred v2.2 code cleanup / maintenance baseline

The actual code cleanup is intentionally deferred until after v2.1 release to avoid widening the RC regression surface.

v2.2 cleanup target:
- remove hidden `AppTimeBasis.METER` runtime/peer-mode branches;
- retain only minimal compatibility needed to read old development backup state and normalize it to canonical real time;
- remove obsolete `v2_time_basis_strings.xml` resources and tests that exist solely for the retired peer mode;
- audit other compatibility helpers/dead code for genuine redundancy;
- keep meaningful regression coverage for real bugs, protocol safety, DST, backup compatibility and archive traversal;
- complete a final repository hygiene pass and then treat the current local-NFC feature set as feature-complete/maintenance mode.

## 14. Planned v3.0 Home Assistant integration

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

## 15. Private research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, consumption data, captures, backup payloads or NFC traffic.
