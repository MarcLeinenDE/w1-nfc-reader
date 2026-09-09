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
- `2128ef5946d1a7c4886be68758f01113a96dc36e`
- commit: `docs: prepare v2.1 real-device validation gate`
- Android CI run `34317123342`: SUCCESS

Current functional/code checkpoint immediately below the docs-only commit:
- `2191147a96db59b2857af7526fa07826bffc8967`
- commit: `feat: explain unresolved local-time history`
- Android CI run `34316796172`: SUCCESS

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base: `main`
- head: `dev/v2.1.0-real-time-timeline`
- keep Draft until real-device validation succeeds.

## 3. Exact physical validation candidate

Use the CI artifact from the functional code checkpoint, not a later documentation-only build:

- source commit: `2191147a96db59b2857af7526fa07826bffc8967`
- Actions run: `34316796172`
- artifact: `w1-nfc-reader-debug`
- artifact id: `10090392521`
- artifact digest: `sha256:2f9f873bd426f3d38e9eb67939e52208d96eaa2d99bac8170d61a032d3192441`
- APK SHA-256 from the artifact's `SHA256SUMS.txt`: `773ef13392dc1eb6bb707138d7fb4f11087ad42ab3c08dff3e31430b459262e7`

Debug uses the `.dev` application-id suffix and can coexist with the stable app.

The physical sequence and acceptance rules are canonical in:
- `docs/V2_1_REAL_DEVICE_VALIDATION.md`

## 4. Current v2.1 implementation state

The implementation is feature-complete for the planned real-time timeline slice and CI-verified. It is **not release-accepted yet** because the limited real-device gate is still open.

Implemented:

- global Android `LOCAL` / `METER` time-basis preference; `LOCAL` is default;
- existing raw/floating METER History/Statistics semantics retained as compatibility path;
- verified Live acquisition anchors from the production default-read path;
- stable per-meter IANA timezone assignment after first verified Live read;
- explicit per-meter timezone display/provenance/editing in Meter Details;
- fixed-offset-like zone input rejected; manual changes use `USER_SELECTED` provenance;
- occurrence-safe archive DB identity using raw timestamp + `occurrence_key`;
- repeated raw wall-clock periods remain representable across DST fall-back;
- passive archive timing evidence persisted without changing traversal semantics;
- canonical UTC projection from retained evidence, fail-closed when evidence is insufficient;
- LOCAL History/Statistics query routing over resolved UTC intervals;
- occurrence-safe predecessor context for consumption deltas;
- LOCAL navigator/range DST handling without guessing nonexistent or repeated local times;
- hourly LOCAL Statistics expected buckets use real civil-day duration: 23/24/25 hours as applicable;
- missing zone/ambiguous boundary/inconsistent zone evidence fails closed instead of assuming 24;
- chart coverage and KPI known-total logic share one expected-bucket definition;
- raw meter/logger time remains visible as secondary evidence in LOCAL History;
- History/Statistics rerender after a LOCAL/METER setting change;
- explicit LOCAL warnings distinguish:
  - missing meter timezone;
  - unsafe/ambiguous/nonexistent LOCAL boundary;
  - insufficient archive timing evidence;
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
C. LOCAL History/Statistics smoke;
D. LOCAL ↔ METER switch and immediate rerender;
E. timezone edit-dialog invalid-input smoke (no successful production-zone mutation required);
F. final normal Live regression read.

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
