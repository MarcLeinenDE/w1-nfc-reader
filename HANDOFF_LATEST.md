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

Current functional development head / next physical candidate:
- `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`
- commit: `fix: persist verified final History reads as time anchors`
- Android CI run `34523964417`: SUCCESS
- product i18n contract: 227 keys across 6 locales: SUCCESS
- unit/Robolectric suite: SUCCESS
- debug assemble: SUCCESS
- APK signature verification: SUCCESS
- artifact `w1-nfc-reader-debug`
- artifact id `10170855829`
- artifact ZIP SHA-256 `a56b188388ff3d214c6bbb3f51cfac6d9b8f51aa792a892ec86c54e62d11e4f5`
- APK SHA-256 `44b12bb68c985d9ca5009ef562d93dd02aeedcc163d0e5a3c8492eb59c6707ca`
- artifact independently downloaded after CI; ZIP digest, `SHA256SUMS.txt`, and actual APK SHA-256 match.

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- keep Draft until replacement-candidate physical validation succeeds.

Superseded physical candidates:
- `e4f458d9ff47a088926772a13d9bf19946f4bc48`: Section C exposed incorrect LOCAL period ownership/adjacency and shifted statistics labels;
- `474067fbdf51ad3b892053bd5694a7ffcea765d8`: fixed C1 but exposed a false-positive LOCAL time-resolution warning for an archive family with no data in an earlier requested range;
- `14a8ae4100ed801f19627c6489afa9ad3757c1aa`: fixed the warning-scope defect, but real-device/current-backup comparison exposed that History final Default/Live verification was not persisted as a fresh time anchor, leaving newer archive rows legitimately unresolved in LOCAL.

## 3. Real-device validation progress — 2026-09-10

### Section A — installation/default state

Previously observed:
- debug app installed and usable;
- LOCAL mode in use;
- no installation/coexistence problem reported.

Not explicitly recorded:
- whether a completely fresh debug-app state showed Local time as default before any setting change.

No NFC contact is needed for that check. A does not need repetition solely because of downstream C fixes.

### Section B — protected normal Live read + localized meter time

PASS on the real meter before the downstream Section-C-only fixes:
- normal protected Live NFC read succeeded;
- German locale formatting of Live meter time was correct;
- Meter Details showed `Europe/Berlin` and correct automatic zone provenance;
- Meter Details meter time was locale formatted;
- no unintended automatic History synchronization was reported.

B does not need repetition solely because of downstream C fixes. Section F remains the final protected normal Live regression read.

### Section C — LOCAL History / Statistics

Three distinct findings occurred during physical validation.

#### C1 — period ownership / DST projection defect

FAIL on `e4f458d9ff47a088926772a13d9bf19946f4bc48`:
- physical archive adjacency was treated like civil LOCAL calendar adjacency across DST;
- valid physical archive periods could be dropped;
- chart/statistics ownership could reuse the predecessor time token and shift a value to the previous displayed period.

The private real-meter backup was structurally audited. Hour/Day/Month progression, ON_TIME progression and canonical UTC ordering were internally consistent. No private meter identifier, consumption value, backup payload or raw capture was published.

The correction begins at `854b358bca24f43c360ecfde8897de0e0af5c5e3` and was hardened with exhaustive IANA-zone/DST tests.

#### C2 — false-positive LOCAL resolution warning for empty/out-of-range family

Physical retest on `474067fbdf51ad3b892053bd5694a7ffcea765d8` showed a custom February LOCAL range where Hour had no data but still produced the red insufficient-time warning; `All` inherited the unrelated Hour warning while Day was correctly resolved.

Fixed through `14a8ae4100ed801f19627c6489afa9ad3757c1aa`. Current warning contract:
- empty Hour/Day/Month family => no LOCAL resolution warning;
- retained family coverage that begins later than the selected earlier range => no warning solely because the oldest retained record lacks a predecessor;
- `All` must not inherit a warning from an unrelated empty/out-of-range granularity;
- Statistics with zero fully-contained buckets => not automatically a time-resolution warning;
- ordinary known data/coverage gaps => not automatically a time-resolution warning;
- genuinely missing zone, ambiguous/nonexistent LOCAL boundary, or genuinely unresolved timing affecting the query remains fail-closed and warns.

For warning relevance only, the natural oldest-record open prefix is bounded by one maximum native record duration: Hour 1 h, Day 24 h, Month 31 d, Year 366 d. This bound never synthesizes or persists a canonical UTC interval.

#### C3 — History final Live verification was not becoming a fresh time anchor

Physical screenshots on `14a8ae4100ed801f19627c6489afa9ad3757c1aa` showed a red warning for the current September Hour/All view. Comparison with the private backup established that this warning was *legitimate*, not another warning-scope false positive:
- the archive contained newer Hour records than the only stored verified Live time anchor;
- `ArchiveUtcProjection` intentionally refuses to project an archive boundary using an anchor whose ON_TIME is older than that archive boundary;
- therefore the newest Hour rows correctly became `NO_SUITABLE_ANCHOR` and were omitted from LOCAL while remaining available in METER.

The actual design omission was downstream of the protected History safety shell: every family attempt already performs a mandatory final Default Restore/Live verification, but that successful final Live evidence was used only for safety completion and was not persisted as a v2.1 time anchor.

Fix in `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`:
- the existing final Default/Live read captures its real acquisition window (`readBeforeEpochMs` / `readAfterEpochMs`) and readout;
- only when `transport.finalRestoreVerified()` is true is that exact already-performed Live evidence converted through the existing `LiveTimeAnchorPersistence` path and persisted as a new verified anchor;
- no additional NFC command, no additional default read, and no new protocol behavior were added;
- single Hour, Day and Month attempts all use the common `performFamilyAttempt` path and therefore gain this behavior;
- `Sync All` / Full Re-Sync use the same path per family, so a valid verified anchor can be added after each successfully restored family;
- a partial archive traversal may still add a valid time anchor when its final Default/Live verification succeeds, because anchor validity is independent of archive completeness;
- failed/unverified final restore creates no anchor;
- anchor persistence remains additive/fail-soft and must not turn an otherwise valid History safety result into a protocol failure.

New regression coverage proves synthetically for Hour/Day/Month that an archive boundary newer than the old Live anchor remains unresolved before the post-sync anchor and resolves after adding the later verified anchor. Source-route tests also guard that no extra NFC/default read was introduced.

### Next physical action — selective C retest with `229d0a4...`

Installing the APK alone cannot retroactively create the missing post-sync anchor. After installing the exact candidate, perform **one normal explicit History sync** on the real meter (Hour is the most direct reproduction case). The already-required final Default/Live verification should then persist a fresh anchor automatically.

After that sync:
- current September `Stunde`: previously missing newest Hour rows should resolve in LOCAL up to the new verified anchor and the red warning should disappear unless there is some genuinely unresolved relevant evidence;
- current September `Alle`: must likewise not warn solely because of the formerly stale anchor;
- recheck the earlier February custom range: Hour remains normal no-data state without warning; Day remains resolved; All must not inherit a warning from empty/out-of-range Hour;
- bounded Statistics must remain value/coverage-consistent and must not invent zero consumption.

Then continue:
- D — LOCAL ↔ METER identity/value stability and actual query-basis change;
- E — invalid/fixed-offset-like timezone input fails closed;
- F — final normal protected Live NFC read with no unintended History sync/protocol regression.

## 4. Current v2.1 time-model rules

- canonical UTC interval identity is authoritative;
- IANA LOCAL is a reversible query/presentation projection and never rewrites archive ownership;
- raw meter/logger time remains preserved source evidence;
- native W1 physical archive durations remain distinct from civil navigation duration;
- native Hour = 1 elapsed hour;
- native Day = 24 elapsed hours;
- native Month = 28/29/30/31 elapsed days;
- native Year = 365/366 elapsed days;
- LOCAL civil days can legitimately be 23/24/25 hours at DST transitions;
- repeated fall-back hours remain occurrence-safe;
- all Java `ZoneId.getAvailableZoneIds()` zones are covered by UTC/LOCAL invariant tests;
- verified Live/default observations are the only anchors; never extrapolate beyond a later archive ON_TIME using an older anchor;
- future Home Assistant integration should preserve canonical UTC boundaries as identity and derive LOCAL labels/windows through the IANA zone.

## 5. Safety boundary — do not regress

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
- `QalcosonicReader.java`, `MbusParser.java` and validated archive traversal/state-machine behavior remain protected;
- raw meter/logger time is never overwritten by UTC/LOCAL presentation.

`229d0a4...` changes only:
- `HistorySyncActivity.java` downstream orchestration after the existing safety-shell result;
- `MonthlyArchiveNfcWire.java` to retain timing/readout metadata from the existing default verification;
- a new regression-test class.

It does **not** add an NFC command or alter `QalcosonicReader.java`, `MbusParser.java`, archive selectors, terminal semantics, mailbox exchange cadence, overlap rules or traversal state machine.

## 6. Release gate

Current stable release remains immutable `v2.0.0` / versionCode `43`.

Do not:
- bump to stable v2.1.0 yet;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag/release.

First pass selective replacement-candidate C–F. After physical acceptance, prepare version metadata, changelog/release notes and the exact signed release candidate, then physically accept that exact candidate before publication.

## 7. Private Research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, captures, NFC traffic, backup payloads or consumption data.

## 8. Post-validation UI follow-ups

Do not alter the exact functional candidate merely for these presentation items during the current C retest. After the functional gate, evaluate them together before preparing the v2.1 release candidate:

- ordinary LOCAL period labels should prefer trustworthy locale-aware timezone abbreviations where available, for example German `MEZ` / `MESZ` and English `CET` / `CEST`, instead of showing only `+01:00` / `+02:00`;
- canonical IANA zone and exact numeric UTC offset remain internal/diagnostic truth; ambiguous repeated hours may still need numeric-offset fallback/additional disambiguation;
- Meter Details `Am Handy ausgelesen` lacks the centered `·` separator between date and time;
- Dashboard/Overview `Ausgelesen` has the same missing centered `·` separator.
