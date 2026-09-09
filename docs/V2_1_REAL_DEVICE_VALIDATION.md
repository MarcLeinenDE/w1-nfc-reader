# W1 NFC Reader v2.1 — limited real-device validation

Status: required physical gate before PR #22 can leave draft/release hardening can advance.

## Goal

Confirm on a real compatible Qalcosonic W1 that the v2.1 time-model, global LOCAL/METER query routing and presentation sit safely downstream of the already validated NFC acquisition path. This validation is intentionally narrow: deterministic DST/23/25-hour behavior, database migration, locale formatting contracts and backup semantics are covered by automated tests and do not need artificial physical reproduction.

## Pinned physical candidate

Use exactly the CI-green debug APK below. Debug builds use the `.dev` application-id suffix and can coexist with the stable public app.

- branch: `dev/v2.1.0-real-time-timeline`
- functional commit: `e4f458d9ff47a088926772a13d9bf19946f4bc48`
- CI run: `34320334896` — success
- unit/Robolectric tests: 346 — success
- artifact: `w1-nfc-reader-debug`
- artifact id: `10091656517`
- artifact ZIP digest: `sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775`
- APK SHA-256: `44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a`

The artifact was downloaded after CI and the APK hash was independently recalculated from the downloaded ZIP; it matched `SHA256SUMS.txt` exactly.

Do not substitute the earlier `2191147a…` artifact: that checkpoint predates the completed Live LOCAL/METER presentation/query contract and is superseded.

## Safety rules

- Do not add experimental NFC commands for this validation.
- Normal NFC contact must remain the protected fast Live/default read.
- History synchronization must still start only after explicit user action.
- Do not alter meter/radio/calibration/firmware state.
- If NFC behavior differs from the previously validated v2.0 path, stop and treat it as a regression rather than adapting the protocol during the same test.
- Do not commit private meter IDs, consumption values, captures or raw NFC traffic to the public repository.

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

1. From Overview in LOCAL mode, perform one normal NFC read exactly as in normal use.
2. Keep the phone on the meter until the read completes.
3. Confirm the normal Live result appears and no archive synchronization starts automatically.
4. Inspect the Live time information on Overview.
5. Open Meter details and inspect both phone/read time and meter internal time.

Expected:
- Live read succeeds through the established default-read path;
- no unexpected extra NFC interaction is visible;
- the physical meter is identified as usual;
- LOCAL Overview uses the real/Android acquisition time as primary Live time and shows raw meter time as secondary technical evidence when available;
- raw meter time is locale-formatted for the selected app language instead of exposing storage syntax such as `yyyy-MM-dd HH:mm`;
- with German UI, a value such as `2026-09-09 14:05` is presented in German date order (for example `09.09.2026 · 14:05`), not as the raw ISO-like storage string;
- Meter details uses the same locale-aware formatting for the meter internal time;
- a per-meter IANA timezone is shown after the verified Live read;
- for a device configured for Germany this will normally be `Europe/Berlin`;
- provenance indicates automatic assignment from the device at the first verified Live read unless the debug-app state already contained an explicit user assignment.

### C. LOCAL History / Statistics

Use already synchronized archive data if the debug app contains it. If the debug installation has no archive baseline, perform only the normal explicit History synchronization needed to establish/refresh the test data, following the existing validated sync UX.

1. Open History in LOCAL mode.
2. Inspect Live and available Hour/Day/Month rows.
3. Confirm Live rows use real acquisition time as primary and meter time as secondary when available.
4. Confirm archive rows use resolved local/civil time as primary and raw meter/logger time as secondary evidence.
5. Open Statistics for a bounded period with known data.

Expected:
- Live ordering follows actual acquisition epoch in LOCAL mode;
- History does not silently reorder archive rows by raw logger text when resolved UTC evidence is available;
- raw meter time remains visible, not overwritten;
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
