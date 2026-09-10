# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-10

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
11. `docs/research/PUBLIC_QW1_EVIDENCE.md`
12. `docs/RELEASING.md`
13. `CHANGELOG.md`
14. `AGENTS.md`

Then inspect the actual development code at the exact development head. The handoff branch is coordination-only; never develop directly on it.

## 2. Repository / branch state

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable `main` baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current development head:
- `404dc90c1f974fb145d80cce23508825d8de16b9`
- commit: `docs: add public support for v2.1 timing evidence`
- Android CI run `34362370166`: SUCCESS
- documentation-only on top of the functional candidate.

Exact functional / physical-validation candidate:
- `e4f458d9ff47a088926772a13d9bf19946f4bc48`
- Android CI run `34320334896`: SUCCESS
- 346 tests passed
- product i18n contract: 227 keys across 6 locales
- artifact `w1-nfc-reader-debug`
- artifact id `10091656517`
- artifact digest `sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775`
- APK SHA-256 `44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a`

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- keep Draft until real-device validation succeeds.

The older candidate `2191147a96db59b2857af7526fa07826bffc8967` is obsolete and must not be used.

## 3. Real-device validation progress — 2026-09-10

The user currently has physical access to the meter and started the A–F validation from `docs/V2_1_REAL_DEVICE_VALIDATION.md` using the pinned candidate above.

### Section A — installation/default state

Observed:
- debug candidate is installed and usable;
- LOCAL mode is being used successfully;
- no installation/coexistence problem was reported.

Not explicitly recorded in chat:
- whether a fresh debug-app state showed **Local time as the default** before any setting change.

This can be verified without another NFC contact if needed.

### Section B — protected normal Live read + localized meter time

Confirmed on the real meter:
- normal protected Live NFC read succeeds;
- German locale formatting of the Live meter time is correct;
- Meter Details shows a per-meter timezone;
- the shown timezone is `Europe/Berlin`;
- provenance correctly indicates automatic assignment from the first verified Live read / device context;
- Meter Details also shows the meter time in German locale format;
- no unintended automatic History synchronization was reported after the normal Live read.

Minor UI discrepancy found:
- in Meter Details, the **`Am Handy ausgelesen`** timestamp is correctly formatted but lacks the centered separator dot between date and time that other product timestamps use.
- treat this as a small presentation consistency issue, not a time-model/NFC failure;
- do not change the pinned candidate in the middle of the A–F test just for this cosmetic issue. Record it for the post-validation fix/retest decision.

### Next physical step — Section C

Continue in **LOCAL** mode and inspect History / Statistics. Screenshots from the phone are explicitly welcome in the new project chat.

Check in order:
1. History → Live: real acquisition time primary, meter time secondary when available.
2. History → Hour / Day / Month: resolved local/civil time primary, raw meter/logger time secondary.
3. Ordering must look chronologically plausible; no false missing-timezone warning should appear.
4. If the debug app has no archive data, start only the normal explicit History synchronization needed to establish the data. Do not use experimental NFC options.
5. Statistics: use a bounded range with known data; coverage/values should be plausible and no artificial zero total should be invented because LOCAL projection is unavailable.

After Section C, continue with D (LOCAL ↔ METER), E (invalid timezone input smoke) and F (final normal Live regression read).

## 4. Current v2.1 implementation state

Feature-complete and CI-green for the planned time-model/query/UI slice. Important completed behavior:
- global `LOCAL` / `METER`, LOCAL default;
- verified Live acquisition anchors;
- stable per-meter IANA timezone + provenance;
- occurrence-safe archive identity (`raw timestamp + occurrence_key`);
- passive archive timing evidence and fail-closed UTC projection;
- LOCAL History/Statistics on resolved UTC timeline;
- LOCAL Live selection/order on Android acquisition epoch;
- METER Live bounded selection/order/predecessor on `meter_time`;
- DST-safe local navigation and 23/24/25-hour Statistics completeness;
- raw meter time secondary in LOCAL;
- trustworthy resolved local time secondary in METER;
- locale-aware Live meter-time display in Overview / History / Meter Details;
- explicit LOCAL warnings for missing zone / unsafe boundary / insufficient timing evidence;
- schema-3 backup/restore, schema-2 compatibility;
- six-locale product translation contract.

## 5. Public QW1 supporting evidence — documentation only

`docs/research/PUBLIC_QW1_EVIDENCE.md` records public Axioma/FCC/ST supporting evidence. Guardrails remain:
- current Axioma manual says integrated NFC is intended for data reading only;
- ON_TIME-related manufacturer data is supporting evidence, not proof of our UTC reconstruction;
- nominal `1480 / 1130 / 36` capacities are informational only, never hardcoded sync depth;
- semantic terminal detection and secure `KNOWN_RECORD_REACHED` overlap remain authoritative;
- historical ST25DV04K evidence does not create a chip/revision dependency.

## 6. Safety boundary — do not regress

Remain protected:
- normal NFC contact = fast Live/default read only;
- History synchronization starts only after explicit user action;
- Hour/Day/Month families remain independent;
- accepted archive observations persist immediately;
- secure known overlap only; timestamp-only overlap is forbidden;
- semantic terminal conditions, not hardcoded record counts;
- final Default Restore/Live verification required for COMPLETE;
- no cumulative consumption across physical meter replacement;
- no intentional persistent meter/radio/calibration/firmware writes;
- `QalcosonicReader.java`, `MbusParser.java` and validated NFC/mailbox/archive traversal paths remain protected;
- raw meter/logger time remains source evidence and is never overwritten by derived UTC/local presentation.

If the physical test exposes a protocol/read regression, stop and reopen the safety gate rather than modifying NFC commands during the same test.

## 7. Release gate

Current stable release remains immutable `v2.0.0` / versionCode `43`.

Do not:
- bump to stable v2.1.0 yet;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag/release.

First complete physical sections A–F. After physical acceptance, prepare version metadata, changelog/release notes and the exact signed release candidate, then physically accept that exact candidate before publication.

## 8. Private Research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, captures, NFC traffic or consumption data.