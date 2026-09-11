# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-11

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

Current functional development head / exact next physical candidate:
- `c7b909bb288284d0a527aed599b4be46975ddf52`
- Android CI run `34634776793`: SUCCESS
- unit/Robolectric suite: SUCCESS
- product i18n contract: 227 keys across 6 locales: SUCCESS
- debug assemble/signature verification: SUCCESS
- artifact `w1-nfc-reader-debug`
- artifact id `10276704745`
- artifact ZIP SHA-256 `5b62bed9d75e17627ef0231090e3179de7fc1844ae50e3886144923b6a3db9b7`
- APK SHA-256 `32ccfd4e2504aa6eb7edba3b2accea5a1f575fe1df33b569fea66b88cc71885d`
- artifact independently downloaded after CI; ZIP digest, `SHA256SUMS.txt`, and actual APK SHA-256 all match.

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- keep Draft until replacement-candidate physical validation succeeds.

Superseded physical candidates:
- `e4f458d9ff47a088926772a13d9bf19946f4bc48`: incorrect LOCAL period ownership/adjacency and shifted statistics labels;
- `474067fbdf51ad3b892053bd5694a7ffcea765d8`: false-positive LOCAL warning for an empty/out-of-range archive family;
- `14a8ae4100ed801f19627c6489afa9ad3757c1aa`: warning scope fixed, but final History Default/Live verification was not persisted as a fresh time anchor;
- `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`: post-History anchor persistence worked physically, but statistics exposed a valid native bucket being dropped when its two UTC boundaries were resolved across two different verified anchors.

## 3. Real-device validation progress — 2026-09-11

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

Four distinct findings have occurred during physical validation.

#### C1 — period ownership / DST projection defect

FAIL on `e4f458d9ff47a088926772a13d9bf19946f4bc48`:
- physical archive adjacency was treated like civil LOCAL calendar adjacency across DST;
- valid physical archive periods could be dropped;
- chart/statistics ownership could reuse the predecessor time token and shift a value to the previous displayed period.

Fixed from `854b358bca24f43c360ecfde8897de0e0af5c5e3` and hardened with exhaustive IANA-zone/DST tests.

#### C2 — false-positive LOCAL warning for empty/out-of-range family

Physical retest on `474067fbdf51ad3b892053bd5694a7ffcea765d8` showed a custom February LOCAL range where Hour had no data but still produced the red insufficient-time warning; `All` inherited the unrelated Hour warning while Day was correctly resolved.

Fixed through `14a8ae4100ed801f19627c6489afa9ad3757c1aa`. The warning is now reserved for genuinely relevant unresolved time. Empty/out-of-range granularities, zero fully-contained statistic buckets and ordinary known coverage gaps do not warn by themselves.

#### C3 — final History Live verification was not becoming a fresh time anchor

On `14a8ae4100ed801f19627c6489afa9ad3757c1aa`, current September Hour/All legitimately warned because newer archive boundaries had ON_TIME later than the only stored verified Live anchor. The actual omission was that every History family already performs a mandatory final Default Restore/Live verification, but that successful final Live evidence was not persisted as a v2.1 time anchor.

Fixed in `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`.

Contract:
- capture timing/readout from the already-existing final Default/Live verification;
- persist it through `LiveTimeAnchorPersistence` only when `finalRestoreVerified()` is true;
- no additional NFC command or additional default read;
- Hour/Day/Month single sync and each family inside Sync All / Full Re-Sync share the common path;
- partial archive traversal may still yield a valid anchor when the final Default/Live verification succeeds;
- failed/unverified final restore yields no anchor;
- anchor persistence remains additive/fail-soft.

Physical retest confirmed that the new post-History anchors are created and newer Hour data becomes LOCAL-resolvable.

#### C4 — valid native statistics bucket dropped at verified anchor handover

After a successful `Alle aktualisieren` on `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`, the user still observed:
- September monthly statistics: `9 von 30` although the freshly exported private backup already contained the next completed Day archive record;
- 10 September day statistics: `21 von 24` and visibly fewer bars.

A force-close/reopen did not change the counts, disproving the initial stale-UI-only hypothesis.

Private backup diagnosis, without publishing any meter ID, consumption values or payload:
- archive synchronization and persistence were correct;
- post-sync verified anchors were present;
- native Hour and Day ON_TIME progression was correct;
- one valid physical archive interval crossed from an older verified anchor to a newer post-sync verified anchor;
- because the two independently verified anchors have slightly different real-time origin calibration, the derived UTC duration of that one otherwise-valid native interval differed slightly from the nominal native duration;
- downstream `HistoryResolvedTimeToken.adjacent()` incorrectly revalidated native adjacency using the derived UTC duration and dropped the valid bucket.

This is a time-model layering bug, not missing archive data.

Fix at current head `c7b909bb288284d0a527aed599b4be46975ddf52`:
- native archive adjacency is now validated where authoritative native evidence still exists: `ArchiveUtcProjection`, using consecutive ON_TIME progression;
- Hour requires +3600 s, Day +86400 s, Month +28/29/30/31 native days, Year +365/366 native days;
- a missing native record becomes explicit `NATIVE_GAP` and is never compressed into one bucket;
- once native adjacency has been validated, downstream resolved-token analytics only requires occurrence-safe shared UTC boundary plus the same assigned IANA zone and does not reject a bucket merely because an anchor handover produces a small derived-duration offset;
- a `NATIVE_GAP` is a known coverage gap, not unresolved time, so it does not trigger the red LOCAL time-resolution warning in bounded or all-period views;
- genuinely unresolved time continues to fail closed.

Synthetic public regression tests cover Hour/Day/Month anchor handovers and a missing native Hour record. The private backup was used only for local diagnosis; no private evidence was committed.

Expected physical result from the already-stored data after installing the current candidate, with no additional NFC sync needed for this first check:
- September 2026 monthly statistics should become `10 von 30` completed Day buckets;
- 10 September 2026 day statistics should become `22 von 24` fully-contained Hour buckets;
- `22/24` rather than `24/24` is expected because the real LOCAL civil-day boundaries cut through two shifted physical Hour intervals; statistics intentionally counts only fully-contained buckets and does not fabricate fractional edge consumption;
- the screenshot can visually show fewer filled bars than the coverage count because genuine `0.000 m³` buckets currently render as zero-height bars. In the private backup for this selected day, the valid fully-contained set includes genuine zero-consumption buckets. This is a chart-visibility/UX issue, not missing data.

### Next physical action — selective C retest with `c7b909b...`

Install the exact candidate. No new NFC contact is needed for the first verification because the database already contains the archive rows and post-sync anchors needed to reproduce C4.

Check:
- Statistics → September 2026: expected `10 von 30`;
- Statistics → 10.09.2026: expected `22 von 24`;
- History → current September Hour / All: no warning solely from the now-valid anchor handover;
- earlier February no-Hour-data range: Hour remains normal empty-state without warning; Day remains resolved; All must not inherit an unrelated warning.

Then finish remaining Section C statistics checks and continue:
- D — LOCAL ↔ METER identity/value stability and actual query-basis change;
- E — invalid/fixed-offset-like timezone input fails closed;
- F — final normal protected Live NFC read with no unintended History sync/protocol regression.

## 4. Current v2.1 time-model rules

- canonical UTC interval identity is authoritative;
- IANA LOCAL is a reversible query/presentation projection and never rewrites archive ownership;
- raw meter/logger time remains preserved source evidence;
- native adjacency/completeness is decided from native evidence (including ON_TIME), not from LOCAL labels or independently calibrated UTC interval duration;
- native Hour = 1 elapsed hour; Day = 24 elapsed hours; Month = 28/29/30/31 elapsed days; Year = 365/366 elapsed days;
- independently verified Live anchors may have small calibration differences; a handover between valid anchors must not itself invalidate an otherwise-native-adjacent archive bucket;
- LOCAL civil days can legitimately be 23/24/25 hours at DST transitions;
- repeated fall-back hours remain occurrence-safe;
- missing native records remain explicit coverage gaps and are never compressed;
- verified Live/default observations are the only anchors; never extrapolate beyond a later archive ON_TIME using an older anchor;
- all Java `ZoneId.getAvailableZoneIds()` zones remain covered by UTC/LOCAL invariant tests;
- future Home Assistant integration should preserve canonical UTC interval boundaries and explicit coverage gaps as integration identity, deriving LOCAL labels/windows from the IANA zone.

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

The C4 fix changes only downstream time projection/coverage/read-model logic and regression tests. It adds no NFC command and does not alter mailbox exchange cadence, archive selectors, terminal semantics, overlap rules or traversal state machine.

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

After the functional gate, evaluate these together before preparing the v2.1 release candidate:
- ordinary LOCAL period labels should prefer trustworthy locale-aware timezone abbreviations where available, e.g. German `MEZ` / `MESZ` and English `CET` / `CEST`, while canonical IANA zone and exact numeric offset remain internal/diagnostic truth;
- Meter Details `Am Handy ausgelesen` lacks the centered `·` separator between date and time;
- Dashboard/Overview `Ausgelesen` has the same missing centered `·` separator;
- genuine zero-consumption statistic buckets currently count correctly in coverage but render as zero-height bars, which can make the chart look as if points are missing. Evaluate a non-misleading zero marker/baseline tick rather than fabricating a positive-height bar.
