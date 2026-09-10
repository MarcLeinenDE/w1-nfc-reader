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

Current development head and replacement physical-validation candidate:
- `474067fbdf51ad3b892053bd5694a7ffcea765d8`
- final commit in the LOCAL hardening block: `test: bound LOCAL family regression to resolved coverage`
- functional LOCAL fix begins at `854b358bca24f43c360ecfde8897de0e0af5c5e3`
- subsequent hardening commits: `c1fe9214146ffde09d50dfb6c2851c50add7989c`, `98fdc258d78c08a887b901308c49cec58d35a688`, `474067fbdf51ad3b892053bd5694a7ffcea765d8`
- Android CI run `34512260812`: SUCCESS
- complete `testDebugUnitTest` suite: SUCCESS; 355 tests in this suite
- product i18n contract: 227 keys across 6 locales
- debug assemble/signature verification: SUCCESS
- artifact `w1-nfc-reader-debug`
- artifact id `10166329385`
- artifact ZIP digest `sha256:4bc1f9d4ca039612d1463168600b3e0c9572b9074b67d9055d57f716ac9b9067`
- APK SHA-256 `82fb77ce20d7bd6592aa694a7bc38371928c52952673b3f93e690a44176eaa8c`
- the downloaded artifact was independently re-hashed after CI; ZIP digest, `SHA256SUMS.txt` and actual APK hash all match.

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- currently still Draft; keep Draft until replacement-candidate physical validation succeeds.

Do not use the former physical candidate `e4f458d9ff47a088926772a13d9bf19946f4bc48` for acceptance. It is preserved as evidence for the Section-C failure described below.

## 3. Real-device validation progress — 2026-09-10

### Section A — installation/default state

Previously observed on the former candidate:
- debug app installed and usable;
- LOCAL mode in use;
- no installation/coexistence problem reported.

Not explicitly recorded:
- whether a completely fresh debug-app state showed Local time as default before any setting change.

This does not require another NFC contact.

### Section B — protected normal Live read + localized meter time

PASS on real device / real meter using the former candidate:
- normal protected Live NFC read succeeded;
- German locale formatting of Live meter time was correct;
- Meter Details showed `Europe/Berlin` and correct automatic zone provenance;
- Meter Details meter time was locale formatted;
- no unintended automatic History synchronization was reported.

Minor cosmetic note remains:
- `Am Handy ausgelesen` is correctly localized but lacks the centered separator dot used by other product timestamps.
- this is presentation-only and did not cause the Section-C failure.

### Section C — LOCAL History / Statistics

**FAIL on former candidate `e4f458d9ff47a088926772a13d9bf19946f4bc48`.**

Root cause was reproduced downstream of the protected NFC/parser/archive path:
- LOCAL Statistics could validate physical archive adjacency as if the projected interval had to be one civil calendar Hour/Day/Month in the selected IANA zone;
- a valid physical archive interval crossing DST could therefore be dropped;
- chart/statistics ownership also reused a predecessor time token, allowing a valid value to be labelled as the preceding period.

The private real-meter backup was structurally audited during diagnosis. Its Hour/Day/Month archive progression, ON_TIME progression and canonical UTC ordering were internally consistent. No private meter identifier, consumption value, backup payload or raw private evidence was added to this public repository. Public regression fixtures are synthetic/minimised representations of the structural timing cases only.

### Replacement fix / hardening

The current candidate `474067fbdf51ad3b892053bd5694a7ffcea765d8` fixes and guards the failure:
- LOCAL statistics retain the **current resolved canonical UTC interval** as period ownership instead of shifting to the predecessor token;
- temperature, flow, battery and alarm statistics retain the same resolved interval ownership;
- physical archive adjacency is checked on native elapsed UTC duration, not civil DST duration;
- native physical durations are exact: Hour = 1 h, Day = 24 h, Month = 28/29/30/31 d, Year = 365/366 d;
- a 23 h or 25 h **LOCAL civil navigation day** remains valid where the IANA zone requires it, but must never be mistaken for the duration of one physical W1 Day archive record;
- shifted Day/Month/Year physical intervals are displayed as explicit LOCAL ranges rather than silently receiving a false civil calendar label;
- repeated fall-back clock hours remain distinguishable by canonical UTC identity and displayed offset;
- missing native buckets are not compressed into one chart/statistics bucket;
- different assigned meter zones cannot be treated as one adjacent resolved series.

Exhaustive automated timezone coverage now includes:
- every zone returned by Java `ZoneId.getAvailableZoneIds()`;
- UTC identity round-trip for Hour, Day, all valid Month lengths and both Year lengths;
- normal, spring-DST and autumn-DST LOCAL navigator windows;
- ambiguous folds and nonexistent gaps fail closed unless an explicit valid offset resolves the ambiguity;
- end-to-end repository → UTC projection → LOCAL selection → resolved observation → statistics tests for Hour, Day and Month, including the repeated autumn hour and a spring-DST Day sequence.

This separation is also the required basis for later Home Assistant integration: canonical UTC interval identity is authoritative; IANA LOCAL time is a reversible presentation/query projection and must never rewrite archive ownership.

### Next physical action — selective retest C–F

Use only candidate `474067fbdf51ad3b892053bd5694a7ffcea765d8` / artifact `10166329385`.

A and B do not need to be repeated solely because of this downstream fix; no protected NFC/mailbox/parser/traversal code changed. Section F still provides the final normal Live regression read.

Retest in this order:
1. C — LOCAL History Live/Hour/Day/Month and bounded Statistics, including the previously wrong period assignment.
2. D — switch LOCAL ↔ METER and verify that data identity/value does not change, only the intended time basis/presentation/query semantics.
3. E — invalid/fixed-offset-like timezone input must remain rejected/fail closed as documented.
4. F — final normal protected Live NFC read; no unintended History sync or protocol regression.

Screenshots are useful evidence for C/D. Do not use experimental NFC options for this gate.

## 4. Current v2.1 implementation state

Feature-complete and CI-green for the planned time-model/query/UI slice. Important completed behavior:
- global `LOCAL` / `METER`, LOCAL default;
- verified Live acquisition anchors;
- stable per-meter IANA timezone + provenance;
- occurrence-safe archive identity (`raw timestamp + occurrence_key`);
- passive archive timing evidence and fail-closed UTC projection;
- LOCAL History/Statistics on resolved canonical UTC timeline;
- LOCAL Live selection/order on Android acquisition epoch;
- METER Live bounded selection/order/predecessor on `meter_time`;
- DST-safe LOCAL navigation with real 23/24/25-hour civil-day completeness where appropriate;
- native physical archive period duration kept separate from civil navigation duration;
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

The LOCAL fix/hardening block did not change NFC command, mailbox, parser or archive traversal behavior.

## 7. Release gate

Current stable release remains immutable `v2.0.0` / versionCode `43`.

Do not:
- bump to stable v2.1.0 yet;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag/release.

First pass the selective replacement-candidate physical retest C–F. After physical acceptance, prepare version metadata, changelog/release notes and the exact signed release candidate, then physically accept that exact candidate before publication.

## 8. Private Research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, captures, NFC traffic, backup payloads or consumption data.

## 9. Post-validation UI follow-ups

Do not alter the pinned candidate during C–F solely for these presentation items. After the functional gate, evaluate them together before preparing the v2.1 release candidate:

- replace raw displayed UTC offsets such as `+01:00` / `+02:00` in ordinary LOCAL period labels with locale-aware timezone abbreviations where the platform provides a trustworthy localized name (for example German `MEZ` / `MESZ`, English `CET` / `CEST` for `Europe/Berlin`);
- keep the canonical IANA zone and exact numeric UTC offset internally and in diagnostic/ambiguity handling; the friendly abbreviation is presentation only and must never become time identity;
- repeated fall-back hours must remain unambiguous even if localized abbreviations are equal or unavailable, so the UI may fall back to or additionally expose the numeric offset when necessary;
- retain the separate cosmetic note for the missing centered separator in `Am Handy ausgelesen`.
