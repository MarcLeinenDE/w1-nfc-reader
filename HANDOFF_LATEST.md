# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-09

This is the canonical coordination handoff for W1 NFC Reader v2.1. Repository state, GitHub Actions and real-device evidence are authoritative over stale chat history.

## 1. Required reading order

Read strictly in this order before changing the v2.1 implementation:

1. `HANDOFF_LATEST.md` on `handoff/v2.1.0-current`
2. `CURRENT_STATE.json` on `handoff/v2.1.0-current`
3. `docs/V2_1_TIME_MODEL_IMPLEMENTATION.md` on `dev/v2.1.0-real-time-timeline`
4. `docs/V2_1_REAL_DEVICE_VALIDATION.md` on `dev/v2.1.0-real-time-timeline`
5. `docs/product-decisions/v2.1-real-time-timeline-and-coverage.md` on `main`
6. `docs/V2_ARCHIVE_PERIOD_SEMANTICS.md`
7. `docs/V2_HISTORY_NAVIGATION_FILTERS.md`
8. `docs/V2_HISTORY_SYNC_ARCHITECTURE.md`
9. `docs/V2_BREAKING_CHANGES.md`
10. `docs/PROTOCOL_SAFETY.md`
11. `docs/RELEASING.md`
12. `CHANGELOG.md`
13. `AGENTS.md`

Then inspect the actual development code at the exact development head. The handoff branch is coordination-only; never develop directly on it.

## 2. Repository / branch state

Public repository:
- `MarcLeinenDE/w1-nfc-reader`

Stable `main` baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current development head:
- `50852719e9e93e7a4f921e95156663920756fdc9`
- commit: `docs: pin corrected live-time validation candidate`
- Android CI run `34320664008`: SUCCESS
- this head is documentation-only on top of the functional candidate below.

Exact functional/code checkpoint:
- `e4f458d9ff47a088926772a13d9bf19946f4bc48`
- commit: `test: relax time-basis presentation source contracts`
- Android CI run `34320334896`: SUCCESS
- 346 tests passed
- product i18n contract: 227 translatable keys across 6 locales

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base: `main`
- head: `dev/v2.1.0-real-time-timeline`
- keep Draft until real-device validation succeeds.

## 3. Exact physical validation candidate

Use this exact CI artifact for the next physical A–F validation:

- source commit: `e4f458d9ff47a088926772a13d9bf19946f4bc48`
- Actions run: `34320334896`
- artifact: `w1-nfc-reader-debug`
- artifact id: `10091656517`
- artifact digest: `sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775`
- APK SHA-256: `44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a`

Debug uses the `.dev` application-id suffix and can coexist with the stable app.

The previous candidate:
- `2191147a96db59b2857af7526fa07826bffc8967`
- CI `34316796172`
- artifact id `10090392521`

is **obsolete and must not be used for v2.1 physical acceptance**. It predates the corrected Live LOCAL/METER routing and locale-aware Live meter-time presentation.

The physical sequence and acceptance rules are canonical in:
- `docs/V2_1_REAL_DEVICE_VALIDATION.md`

## 4. Current v2.1 implementation state

The implementation is feature-complete for the planned real-time timeline slice and CI-verified. It is **not release-accepted yet** because the limited real-device gate is still open.

Implemented:

- global Android `LOCAL` / `METER` time-basis preference; `LOCAL` is default;
- verified Live acquisition anchors from the production default-read path;
- stable per-meter IANA timezone assignment after first verified Live read;
- explicit per-meter timezone display/provenance/editing in Meter Details;
- fixed-offset-like zone input rejected; manual changes use `USER_SELECTED` provenance;
- occurrence-safe archive DB identity using raw timestamp + `occurrence_key`;
- repeated raw wall-clock periods remain representable across DST fall-back;
- passive archive timing evidence persisted without changing traversal semantics;
- canonical UTC projection from retained evidence, fail-closed when evidence is insufficient;
- LOCAL History/Statistics query routing over resolved UTC intervals;
- LOCAL Live rows use the real Android acquisition epoch for bounded selection/order;
- METER Live rows use `meter_time` for bounded selection, order and predecessor context;
- METER bounded periods do not silently include a Live row with missing meter time by falling back to phone time;
- occurrence-safe predecessor context for consumption deltas;
- LOCAL navigator/range DST handling without guessing nonexistent or repeated local times;
- hourly LOCAL Statistics expected buckets use real civil-day duration: 23/24/25 hours as applicable;
- missing zone/ambiguous boundary/inconsistent zone evidence fails closed instead of assuming 24;
- chart coverage and KPI known-total logic share one expected-bucket definition;
- raw meter/logger time remains visible as secondary evidence in LOCAL History;
- METER History can show resolved local time as secondary evidence when trustworthy;
- Live meter time is formatted locale-aware in Overview, History and Meter Details instead of exposing the raw stored `yyyy-MM-dd HH:mm` form;
- raw meter time remains unchanged in persistence/backup; only product presentation/query semantics changed;
- German example for raw `2026-09-09 14:05`: `09.09.2026 · 14:05`;
- History/Statistics rerender after a LOCAL/METER setting change;
- explicit LOCAL warnings distinguish missing meter timezone, unsafe/ambiguous/nonexistent LOCAL boundary and insufficient archive timing evidence;
- meters without data for the selected archive granularity do not create false zone warnings;
- schema-3 backup/restore preserves time-basis preference, per-meter zones, verified anchors and occurrence-safe archive data;
- schema-2 restore remains supported without inventing missing timing evidence;
- six-locale product translation contract remains green.

## 5. Safety boundary — do not regress

No v2.1 timeline work introduced a new NFC command or changed protected transport/parser/traversal behavior.

Remain protected:

- normal NFC contact = protected fast Live/default read only;
- History synchronization starts only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive records are persisted immediately;
- incremental known overlap must remain secure; timestamp-only overlap is forbidden;
- ambiguous selected requests are never blindly retried;
- final Default Restore/Live verification remains required for COMPLETE;
- no cumulative consumption across physical meter replacement;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and physically validated NFC/mailbox/archive traversal paths are protected;
- raw meter/logger time remains source evidence and is never overwritten by derived UTC/local presentation.

If the physical v2.1 test exposes a protocol/read regression, stop and reopen the safety gate. Do not patch around it by changing meter commands in the same validation pass.

## 6. Stable release remains immutable

Current public stable release:
- version/tag: `v2.0.0`
- immutable tag commit: `5c094a70e75cba52fbdaef96d39b6c72e2011f9d`
- versionCode: `43`
- public APK SHA-256: `211c5dcb94047dcc12d0aa68534b6eaf154103a8c08c600f293c097c2188d2f4`

Never move, recreate or replace `v2.0.0` (or `v1.0.0`).

The development branch intentionally still has stable identity `versionName = 2.0.0` / `versionCode = 43`. Do **not** bump to 2.1.0 until the physical validation candidate is accepted.

## 7. Remaining gates / next action

The next action is physical validation, not another architecture migration.

Run sections A–F from `docs/V2_1_REAL_DEVICE_VALIDATION.md` using the exact candidate in section 3:

A. install / confirm LOCAL default;
B. protected normal Live read + automatic zone assignment;
C. LOCAL History/Statistics smoke, including locale-aware raw meter-time secondary evidence;
D. LOCAL ↔ METER switch and immediate rerender; verify Live rows/ranges actually follow the selected basis, not only the label;
E. timezone edit-dialog invalid-input smoke (no successful production-zone mutation required);
F. final normal Live regression read.

During the physical smoke also verify on German UI that Live meter time is no longer shown as raw `yyyy-MM-dd HH:mm`, but in locale-aware German form.

After A–F pass:

1. record the exact physical evidence without publishing private meter ID/consumption/captures;
2. advance version metadata to the intended v2.1.0 stable identity;
3. curate `CHANGELOG.md` / release notes;
4. produce the normal signed release candidate;
5. physically accept that exact signed candidate before publication;
6. keep PR/release/tag handling consistent with `docs/RELEASING.md`.

Until A–F pass, PR #22 stays Draft and no stable v2.1.0 release/tag should be created.

## 8. Private Research authority

Only if protocol/time-evidence behavior itself must be revisited, consult the frozen private Research authority:

- private repo: `MarcLeinenDE/engineering-lab`
- path: `android/qalcosonic-nfc-reader/`
- branch: `research/w1-nfc-archive-analysis`
- frozen head: `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Rule: extend the public product architecture with frozen Research semantics; do not port/merge the private Research architecture wholesale. Never publish private meter IDs, captures, NFC traffic or consumption data.
