# W1 NFC Reader — HANDOFF_LATEST

Date: 2026-09-12

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
15. `UX_CONTEXTUAL_HELP_CONTRACT.md` on this handoff branch for the accepted post-functional-gate contextual-help UX rule.

Then inspect the actual development code at the exact development head. The handoff branch is coordination-only; never develop directly on it.

## 2. Repository / branch state

Public repository: `MarcLeinenDE/w1-nfc-reader`

Stable `main` baseline:
- `841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8`

Active development branch:
- `dev/v2.1.0-real-time-timeline`

Current functional development head / exact next physical candidate:
- `cef78ff97ef89004dfdfa47d171b8b4c26db1298`
- Android CI run `34680335357`: SUCCESS
- unit/Robolectric suite: SUCCESS
- product i18n contract: 227 keys across 6 locales: SUCCESS
- debug assemble/signature verification: SUCCESS
- artifact `w1-nfc-reader-debug`
- artifact id `10293467328`
- artifact ZIP SHA-256 `797500b00b4d7554d4b06f01ebfb55219d266dcf455177de13fad842fca43899`
- APK SHA-256 `bb6bf2d17061452cf089692cfb1848578615dc52f5a76099686302389552ab55`
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
- `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e`: post-History anchor persistence worked physically, but statistics exposed a valid native bucket being dropped when its two UTC boundaries were resolved across two different verified anchors;
- `c7b909bb288284d0a527aed599b4be46975ddf52`: C4 statistics fix passed physically (`10/30` month, `22/24` day) but `Alle Zeiträume` still showed a false LOCAL warning because the natural oldest retained archive boundary has no stored predecessor.

## 3. Real-device validation progress — 2026-09-12

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

Five distinct findings have occurred during physical validation.

#### C1 — period ownership / DST projection defect

FAIL on `e4f458d9ff47a088926772a13d9bf19946f4bc48`:
- physical archive adjacency was treated like civil LOCAL calendar adjacency across DST;
- valid archive periods could be dropped;
- chart/statistics ownership could reuse the predecessor time token and shift a value to the previous displayed period.

Fixed from `854b358bca24f43c360ecfde8897de0e0af5c5e3` and hardened with exhaustive IANA-zone/DST tests.

#### C2 — false-positive LOCAL warning for empty/out-of-range family

Physical retest on `474067fbdf51ad3b892053bd5694a7ffcea765d8` showed a custom February LOCAL range where Hour had no data but still produced the red insufficient-time warning; `All` inherited the unrelated Hour warning while Day was correctly resolved.

Fixed through `14a8ae4100ed801f19627c6489afa9ad3757c1aa`. The warning is reserved for genuinely relevant unresolved time. Empty/out-of-range granularities, zero fully-contained statistic buckets and ordinary known coverage gaps do not warn by themselves.

#### C3 — final History Live verification was not becoming a fresh time anchor

On `14a8ae4100ed801f19627c6489afa9ad3757c1aa`, current September Hour/All legitimately warned because newer archive boundaries had ON_TIME later than the only stored verified Live anchor.

Fixed in `229d0a4f3abf595c36ad24a5f9a78cb50dd5009e` by persisting the already-required final verified Default/Live read through `LiveTimeAnchorPersistence`, with zero additional NFC commands/reads. Physical retest confirmed that new post-History anchors are created and newer Hour data becomes LOCAL-resolvable.

#### C4 — valid native statistics bucket dropped at verified anchor handover

On `229d0a4...`, September statistics showed `9/30` and 10 September showed `21/24` despite the relevant archive rows being present. Private backup diagnosis showed one otherwise-native-valid interval crossed from an older verified anchor to a newer verified anchor; small anchor-origin calibration differences changed the derived UTC duration slightly and downstream exact-duration adjacency incorrectly dropped the bucket.

Fixed by `c7b909bb288284d0a527aed599b4be46975ddf52`:
- native adjacency is validated from consecutive ON_TIME evidence in `ArchiveUtcProjection`;
- missing native records become explicit `NATIVE_GAP` and are never compressed;
- downstream resolved-token analytics uses the shared occurrence-safe UTC boundary and assigned zone instead of reclassifying native adjacency from projected duration;
- known `NATIVE_GAP` is coverage, not unresolved time.

Physical retest on 2026-09-12: PASS for the two exact reproductions:
- September 2026 Statistics now shows `10 von 30` completed Day buckets;
- 10 September 2026 Statistics now shows `22 von 24` fully-contained Hour buckets.

`22/24` is expected because two shifted physical Hour intervals cross the LOCAL civil-day edges; the app deliberately does not fabricate fractional edge consumption. Genuine zero-consumption buckets are valid points but currently render with zero visual height; this remains a presentation follow-up.

#### C5 — false all-period warning from natural oldest retained boundary

On `c7b909bb288284d0a527aed599b4be46975ddf52`, `Statistik → Alle Zeiträume` still displayed the red LOCAL insufficient-time warning even though bounded month/day cases were correct.

Root cause:
- an all-period query necessarily includes the oldest retained archive boundary;
- that oldest boundary naturally cannot form a full period because its older predecessor is not stored, therefore it has `START_BOUNDARY_UNAVAILABLE` with `startBoundary == null`;
- `queryAllPeriods()` treated every unresolved period except `NATIVE_GAP` as a time-resolution warning, so this normal retention edge always warned.

Fix at current head `cef78ff97ef89004dfdfa47d171b8b4c26db1298`:
- the natural oldest retained `START_BOUNDARY_UNAVAILABLE` with `startBoundary == null` does not contribute an all-period LOCAL warning;
- an interior `START_BOUNDARY_UNAVAILABLE` whose previous boundary exists but is unresolved still warns fail-closed;
- `NATIVE_GAP` remains a known coverage gap, not time ambiguity;
- genuinely unresolved end boundaries/non-monotonic time still warn.

Regression coverage explicitly distinguishes natural oldest retention geometry from an interior unresolved start. CI is green.

### Next physical action — selective C5 retest with `cef78ff...`

Install the exact candidate. No new NFC contact or History sync is needed for the first check because this is downstream warning classification over existing stored data.

Check:
- Statistics → `Alle Zeiträume`: red LOCAL warning should disappear if the natural oldest retained boundary was the only unresolved condition;
- History → `Alle Zeiträume` / All where available: same rule;
- September Statistics must remain `10/30`;
- 10 September Statistics must remain `22/24`;
- earlier February no-Hour-data range must remain normal empty-state without warning.

If those pass, finish remaining Section C checks and continue D, E, F.

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
- the natural open prefix at the oldest retained archive boundary is expected retention geometry and not, by itself, a LOCAL time-resolution failure;
- an unresolved boundary inside the retained series remains a fail-closed warning condition;
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

The C5 fix changes only downstream all-period warning classification plus regression tests. It adds no NFC command and does not alter archive projection evidence, mailbox exchange cadence, archive selectors, terminal semantics, overlap rules or traversal state machine.

## 6. Release gate

Current stable release remains immutable `v2.0.0` / versionCode `43`.

Do not:
- bump to stable v2.1.0 yet;
- mark PR #22 ready;
- merge PR #22;
- create/move a v2.1.0 tag/release.

First pass selective replacement-candidate C–F. After physical acceptance, implement the recorded presentation/contextual-help polish, prepare version metadata/changelog/release notes and the exact signed release candidate, then physically accept that exact candidate before publication.

## 7. Private Research authority

Only if protocol/time-evidence behavior itself must be revisited:
- private repo `MarcLeinenDE/engineering-lab`
- path `android/qalcosonic-nfc-reader/`
- branch `research/w1-nfc-archive-analysis`
- frozen head `65358d55ee52a6911ab4fa8d1a66bc0d0dda0fd0`

Never publish private meter IDs, captures, NFC traffic, backup payloads or consumption data.

## 8. Accepted post-functional-gate UI / UX follow-ups

After C–F, implement these together before preparing the v2.1 release candidate:
- contextual info-button pattern per `UX_CONTEXTUAL_HELP_CONTRACT.md` across the app wherever technically correct information is not self-explanatory;
- statistics coverage wording should distinguish fully-contained buckets, edge intervals and real data gaps where the model can distinguish them;
- ordinary LOCAL period labels should prefer trustworthy locale-aware timezone abbreviations where available, e.g. German `MEZ` / `MESZ` and English `CET` / `CEST`, while canonical IANA zone and exact numeric offset remain internal/diagnostic truth;
- Meter Details `Am Handy ausgelesen` lacks the centered `·` separator between date and time;
- Dashboard/Overview `Ausgelesen` has the same missing centered `·` separator;
- genuine zero-consumption statistic buckets currently count correctly in coverage but render as zero-height bars; provide a non-misleading visible zero marker/baseline indication rather than fabricating a positive value.
