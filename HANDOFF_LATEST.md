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
- `4a6fe131f212874a3091c7fef78ea85818cfc3c9`
- commit: `docs: pin LOCAL warning-scope retest candidate`
- documentation-only on top of the exact functional candidate below.

Exact functional / physical-validation candidate:
- `14a8ae4100ed801f19627c6489afa9ad3757c1aa`
- code fix root for the warning-scope issue: `39908c926ff32b56545d2849bbb5745f5c49ccd1`
- Android CI run `34519245337`: SUCCESS
- complete previously green 355-test suite plus 9 dedicated LOCAL resolution-warning matrix tests: SUCCESS
- product i18n contract: 227 keys across 6 locales: SUCCESS
- debug assemble/signature verification: SUCCESS
- artifact `w1-nfc-reader-debug`
- artifact id `10169014402`
- artifact ZIP digest `sha256:eadf6ba16d1d5386dd613ba54b645e0c980cfc01cfa817116fd4ed348eed5a51`
- APK SHA-256 `c08bcff4152e075a5e59f2b8d90409f0786f2a5a559e047e594743136677ddc3`
- downloaded artifact independently verified: ZIP digest, `SHA256SUMS.txt` and actual APK hash all match.

Open Draft PR:
- `#22 — WIP: add v2.1 real-time timeline foundation`
- base `main`
- head `dev/v2.1.0-real-time-timeline`
- keep Draft until replacement-candidate physical validation succeeds.

Superseded physical candidates:
- `e4f458d9ff47a088926772a13d9bf19946f4bc48`: Section C exposed incorrect LOCAL period ownership/adjacency and shifted statistics labels;
- `474067fbdf51ad3b892053bd5694a7ffcea765d8`: fixed the first defect but real-device Section C exposed a false-positive LOCAL time-resolution warning for an archive family with no data in the requested earlier range.

## 3. Real-device validation progress — 2026-09-10

### Section A — installation/default state

Previously observed:
- debug app installed and usable;
- LOCAL mode in use;
- no installation/coexistence problem reported.

Not explicitly recorded:
- whether a completely fresh debug-app state showed Local time as default before any setting change.

No NFC contact is needed for that check. A does not need repetition solely because of the downstream C fixes.

### Section B — protected normal Live read + localized meter time

PASS on the real meter before the downstream Section-C-only fixes:
- normal protected Live NFC read succeeded;
- German locale formatting of Live meter time was correct;
- Meter Details showed `Europe/Berlin` and correct automatic zone provenance;
- Meter Details meter time was locale formatted;
- no unintended automatic History synchronization was reported.

B does not need repetition solely because of the downstream C fixes. Section F remains the final protected normal Live regression read.

### Section C — LOCAL History / Statistics

Two separate real-device findings have occurred.

#### C1 — period ownership / DST projection defect

FAIL on `e4f458d9ff47a088926772a13d9bf19946f4bc48`:
- LOCAL Statistics could validate physical archive adjacency as if projected intervals had to be civil Hour/Day/Month intervals;
- valid physical archive periods crossing DST could be dropped;
- chart/statistics ownership could reuse the predecessor time token and shift a value to the preceding displayed period.

The private real-meter backup was structurally audited. Hour/Day/Month progression, ON_TIME progression and canonical UTC ordering were internally consistent. No private meter identifier, consumption value, backup payload or raw capture was published.

The correction begins at `854b358bca24f43c360ecfde8897de0e0af5c5e3` and was hardened through `474067fbdf51ad3b892053bd5694a7ffcea765d8` with exhaustive IANA-zone/DST tests.

#### C2 — false-positive LOCAL resolution warning

During physical retest on `474067fbdf51ad3b892053bd5694a7ffcea765d8`, a custom February LOCAL range showed:
- Hour availability = 0 and the normal empty-state;
- Day data resolved and displayed correctly;
- nevertheless Hour showed the red “insufficient time information” warning, and the `All` view inherited that warning.

The cause was `ArchiveWindowCoverage`: the natural unknown start of the oldest stored native bucket was conservatively modelled as extending to negative infinity when its predecessor was not retained. An Hour family that actually starts months after the request could therefore poison any earlier query and, through `All`, unrelated resolved families.

Current fix / contract:
- empty Hour/Day/Month family => no LOCAL resolution warning;
- archive family whose retained coverage begins later than the requested window => no warning solely because the oldest retained native record lacks a predecessor;
- `All` must not inherit a warning from an unrelated empty/out-of-range granularity;
- Statistics with zero fully-contained buckets => not automatically a time-resolution warning;
- ordinary coverage/data gaps => not automatically a time-resolution warning;
- a genuinely missing meter zone, ambiguous DST fold boundary, nonexistent DST gap boundary, or genuinely unresolved archive timing that can affect the request remains fail-closed and warns.

For warning relevance only, a natural oldest-record open prefix is conservatively bounded by the maximum physical duration of one native record: Hour 1 h, Day 24 h, Month 31 d, Year 366 d. This bound is never used to synthesize, persist or display a canonical UTC interval. Interior unresolved runs/open unsafe suffixes remain conservative.

Dedicated warning matrix tests cover:
1. completely empty archive family;
2. oldest Hour/Day/Month record far outside the selected range;
3. selected range actually touching that unresolved oldest record;
4. `All` with resolved Day data plus later Hour coverage;
5. Statistics before Hour coverage;
6. relevant missing zone;
7. relevant archive data without a usable verified time anchor;
8. ambiguous fall-back LOCAL window;
9. nonexistent spring-forward LOCAL window.

All are green in CI `34519245337` together with the existing suite.

### Next physical action — selective retest C–F

Use exact candidate `14a8ae4100ed801f19627c6489afa9ad3757c1aa` / artifact `10169014402`.

First reproduce the exact false-warning range on the new candidate:
- Hour: no red resolution warning when no Hour data exists; normal empty-state only;
- Day: resolved rows, no false warning;
- All: resolved available families, no warning inherited from empty/out-of-range Hour;
- Statistics: inspect both known-data ranges and a range with no fully-contained bucket.

Then continue:
- D — LOCAL ↔ METER identity/value stability and actual query-basis change;
- E — invalid/fixed-offset-like timezone input fails closed;
- F — final normal protected Live NFC read with no unintended History sync/protocol regression.

## 4. Current v2.1 time-model rules

- canonical UTC interval identity is authoritative;
- IANA LOCAL is a reversible query/presentation projection and never rewrites archive ownership;
- native W1 physical archive durations remain distinct from civil navigation duration;
- native Hour = 1 elapsed hour;
- native Day = 24 elapsed hours;
- native Month = 28/29/30/31 elapsed days;
- native Year = 365/366 elapsed days;
- LOCAL civil days can legitimately be 23/24/25 hours at DST transitions;
- repeated fall-back hours remain occurrence-safe;
- all Java `ZoneId.getAvailableZoneIds()` zones are covered by UTC/LOCAL invariant tests;
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
- `QalcosonicReader.java`, `MbusParser.java` and validated NFC/mailbox/archive traversal paths remain protected;
- raw meter/logger time remains source evidence and is never overwritten by derived UTC/local presentation.

The `474067...` → `14a8ae4...` functional delta changes only `ArchiveWindowCoverage.java` plus the new warning-matrix test file. No NFC, mailbox, parser or archive traversal code changed.

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
- canonical IANA zone and exact numeric UTC offset remain internal/diagnostic truth; ambiguous repeated hours may still need numeric offset fallback/additional disambiguation;
- Meter Details `Am Handy ausgelesen` lacks the centered `·` separator between date and time;
- Dashboard/Overview `Ausgelesen` has the same missing centered `·` separator.
