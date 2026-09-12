# W1 NFC Reader v2.1 — limited real-device validation

Status: required physical gate before PR #22 can leave draft/release hardening can advance.

## Goal

Confirm on a real compatible Qalcosonic W1 that the v2.1 time-model, global LOCAL/METER query routing and presentation sit safely downstream of the already validated NFC acquisition path. This validation is intentionally narrow: deterministic DST/23/25-hour behavior, database migration, locale formatting contracts and backup semantics are covered by automated tests and do not need artificial physical reproduction.

## Pinned physical candidate

Use exactly the CI-green debug APK below. Debug builds use the `.dev` application-id suffix and can coexist with the stable public app.

Current replacement candidate after the two Section-C findings:

- branch: `dev/v2.1.0-real-time-timeline`
- functional commit: `14a8ae4100ed801f19627c6489afa9ad3757c1aa`
- CI run: `34519245337` — success
- complete unit/Robolectric suite: success; includes 9 dedicated LOCAL resolution-warning matrix tests on top of the previously green 355-test suite
- product i18n contract: 227 keys / 6 locales — success
- artifact: `w1-nfc-reader-debug`
- artifact id: `10169014402`
- artifact ZIP digest: `sha256:eadf6ba16d1d5386dd613ba54b645e0c980cfc01cfa817116fd4ed348eed5a51`
- APK SHA-256: `c08bcff4152e075a5e59f2b8d90409f0786f2a5a559e047e594743136677ddc3`

The artifact was independently downloaded after CI. The downloaded ZIP digest matches GitHub's artifact digest, and the actual APK hash matches `SHA256SUMS.txt` exactly.

Superseded physical candidates:

- `e4f458d9ff47a088926772a13d9bf19946f4bc48`: Section C exposed incorrect LOCAL period ownership/adjacency around projected archive periods;
- `474067fbdf51ad3b892053bd5694a7ffcea765d8`: corrected the period-ownership defect, but a real-device custom-range check exposed a false-positive LOCAL unresolved-time warning when an archive family had no data in the requested time range and only began much later.

Do not use either superseded candidate for acceptance.

## Safety rules

- Do not add experimental NFC commands for this validation.
- Normal NFC contact must remain the protected fast Live/default read.
- History synchronization must still start only after explicit user action.
- Do not alter meter/radio/calibration/firmware state.
- If NFC behavior differs from the previously validated v2.0 path, stop and treat it as a regression rather than adapting the protocol during the same test.
- Do not commit private meter IDs, consumption values, captures or raw NFC traffic to the public repository.

## LOCAL resolution-warning contract

The red LOCAL time-resolution warning is a fail-closed signal, not a generic no-data message.

It may be shown only when the selected LOCAL window is genuinely unsafe to interpret, or when unresolved archive timing can actually affect the selected window. In particular:

- an empty Hour/Day/Month family must not show the warning;
- a family whose stored coverage begins later than the requested window must not show the warning solely because its oldest stored native bucket lacks a predecessor boundary;
- the `All` view must not inherit a warning from an unrelated empty/out-of-range granularity;
- Statistics with no fully contained bucket must not show a time-resolution warning merely because it returns zero points;
- a normal coverage gap is not itself a time-resolution problem;
- a genuinely missing per-meter zone remains fail-closed when archive selection cannot be interpreted safely;
- an ambiguous fall-back boundary or nonexistent spring-forward boundary remains fail-closed;
- genuinely unresolved archive timing that can overlap the requested window remains fail-closed.

For warning relevance only, the natural unknown start of the oldest stored native record is conservatively bounded by the maximum physical size of that one record (Hour 1 h, Day 24 h, Month 31 d, Year 366 d). This bound is **not** used to synthesize, store or render a canonical UTC interval. Interior unresolved runs and unbounded unsafe suffixes remain conservative and continue to warn when their unknown region can affect the request.

Automated warning coverage includes empty families, Hour/Day/Month retention edges far outside the selected range, a selected range that actually touches the unresolved oldest bucket, `All` with valid Day data plus later Hour coverage, bounded Statistics before Hour coverage, relevant missing zone, relevant missing usable time anchor, DST fold and DST gap. Existing lower-level coverage tests continue to cover exact/partial/gap/unresolved interval states.

## Physical validation sequence

### A. Installation and default state

1. Install the pinned debug APK alongside the stable app.
2. Launch the debug app normally.
3. Open Settings and confirm the global time display defaults to **Local time** for a fresh debug-app state.
4. Return without changing protocol-related settings.

Expected:
- app launches normally;
- stable app remains installed independently;
- LOCAL is the default presentation/query basis.

### B. Protected normal Live read + localized meter time

Already passed on the real meter before the downstream Section-C-only fixes. It does not need to be repeated solely because of these downstream changes; Section F remains the final protected NFC regression check.

Confirmed evidence:
- Live read succeeds through the established default-read path;
- no archive synchronization starts automatically;
- German locale formatting of Live meter time is correct;
- Meter details shows the assigned `Europe/Berlin` zone and expected automatic provenance.

### C. LOCAL History / Statistics

Use already synchronized archive data if the debug app contains it. If the debug installation has no archive baseline, perform only the normal explicit History synchronization needed to establish/refresh the test data, following the existing validated sync UX.

1. Open History in LOCAL mode.
2. Inspect Live and available Hour/Day/Month rows.
3. Confirm Live rows use real acquisition time as primary and meter time as secondary when available.
4. Confirm archive rows use resolved local/civil time as primary and raw meter/logger time as secondary evidence.
5. Check a bounded/custom range both where the selected granularity has data and where one granularity has no data.
6. In `All`, confirm an unavailable granularity does not create a false LOCAL resolution warning.
7. Open Statistics for bounded periods with known data and also inspect a range with no fully contained bucket.

Expected:
- Live ordering follows actual acquisition epoch in LOCAL mode;
- History does not silently reorder archive rows by raw logger text when resolved UTC evidence is available;
- raw meter time remains visible, not overwritten;
- Hour/Day/Month values remain owned by their canonical physical UTC intervals and are not shifted to predecessor buckets;
- no false red LOCAL warning appears merely because a granularity is empty or begins outside the selected range;
- a genuine unsafe LOCAL boundary/time-resolution state still warns and omits unsafe derived rows;
- Statistics shows plausible coverage and values;
- no `0` total is invented merely because a LOCAL projection is unavailable.

### D. Global LOCAL ↔ METER switch — Overview, Live History and archive History

1. While remembering one visible Live read and one archive row/range, open Settings.
2. Switch to **Meter time**.
3. Return first to Overview.
4. Confirm the latest Live result now shows meter time as primary and real/local acquisition time as secondary.
5. Open History and select Live. Confirm Live rows use localized raw meter time as primary and real/local acquisition time as secondary.
6. Use a bounded Live period where the meter clock differs visibly from Android time. Confirm inclusion follows meter time, not Android acquisition time.
7. Inspect the same archive evidence: primary time follows raw meter/logger wall-clock semantics; resolved local time is secondary when trustworthy.
8. Return to Statistics and confirm the METER basis remains active.
9. Switch back to **Local time** and revisit Overview/History.

Expected:
- switching presentation basis does not change stored measurements or raw timestamps;
- METER uses raw meter time for Live History selection/order and for Live/archive primary presentation;
- METER does not silently fall back to Android time when a bounded Live row has no usable meter time;
- trustworthy local/real time remains secondary evidence in METER mode;
- LOCAL returns to real acquisition time for Live and resolved civil time for archives;
- the same underlying records remain identifiable;
- no History synchronization is triggered by changing the setting.

### E. Zone-management UI smoke

1. Open Meter details.
2. Tap the timezone row and confirm the edit dialog opens with the current IANA zone.
3. Enter an obviously invalid value and attempt to save.
4. Confirm the dialog remains open and shows an error.
5. Cancel the dialog.

Expected:
- invalid IANA input is rejected;
- no raw archive/live data changes;
- the existing valid timezone remains unchanged.

A successful manual timezone replacement does not need to be forced on the production meter merely to satisfy this physical gate; store/update/provenance behavior is covered by automated tests.

### F. Final normal Live regression read

After navigating History, Statistics, Settings and Meter details, return to Overview in LOCAL mode and perform another normal Live read.

Expected:
- normal protected Live read still succeeds;
- the newly displayed real and meter timestamps are plausible and locale-formatted;
- no stale selected archive state leaks into the Live result;
- no protocol regression is observed.

## Presentation-only follow-ups already recorded

These do not change the warning/time identity model and may be handled together after the functional C–F gate:

- ordinary LOCAL labels should prefer trustworthy locale-aware timezone abbreviations (for example `MEZ`/`MESZ` in German or `CET`/`CEST` in English) rather than showing only `+01:00`/`+02:00`; exact IANA zone and numeric offset remain internal/diagnostic truth;
- the centered `·` separator is missing between date/time in `Am Handy ausgelesen` in Meter details;
- the same centered separator is missing in the Overview `Ausgelesen` line.

## Evidence to record

For the accepted candidate, record at minimum:

- APK source commit;
- GitHub Actions run id;
- artifact id/digest and APK SHA-256;
- Android device/model and Android version (non-sensitive only);
- meter family/model, without publishing a private serial/meter ID;
- PASS/FAIL for sections A–F;
- any user-visible discrepancy with a short description.

Screenshots are optional. Do not publish screenshots containing private meter IDs or personal consumption data without redaction.

## Acceptance rule

The physical gate passes only if A–F are all accepted and no protected NFC/archive behavior regresses. A UI wording/layout issue may be fixed in a follow-up candidate and retested selectively; a protocol/read regression reopens the safety gate and blocks release.
