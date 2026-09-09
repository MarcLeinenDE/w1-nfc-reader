# W1 NFC Reader v2.1 — limited real-device validation

Status: required physical gate before PR #22 can leave draft/release hardening can advance.

## Goal

Confirm on a real compatible Qalcosonic W1 that the v2.1 time-model, query routing and UI sit safely downstream of the already validated NFC acquisition path. This validation is intentionally narrow: deterministic DST/23/25-hour behavior, database migration and backup semantics are covered by automated tests and do not need artificial physical reproduction.

## Candidate

Use the latest CI-green debug APK from the v2.1 development branch. Debug builds use the `.dev` application-id suffix and can coexist with the stable public app.

Current code checkpoint before documentation-only follow-up:

- branch: `dev/v2.1.0-real-time-timeline`
- commit: `2191147a96db59b2857af7526fa07826bffc8967`
- CI: `34316796172` — success
- artifact: `w1-nfc-reader-debug`
- artifact id: `10090392521`
- artifact digest: `sha256:2f9f873bd426f3d38e9eb67939e52208d96eaa2d99bac8170d61a032d3192441`

If a later documentation-only commit creates a new APK, prefer the latest fully green artifact and record its exact commit/run/digest in the evidence.

## Safety rules

- Do not add experimental NFC commands for this validation.
- Normal NFC contact must remain the protected fast Live/default read.
- History synchronization must still start only after explicit user action.
- Do not alter meter/radio/calibration/firmware state.
- If NFC behavior differs from the previously validated v2.0 path, stop and treat it as a regression rather than adapting the protocol during the same test.
- Do not commit private meter IDs, consumption values, captures or raw NFC traffic to the public repository.

## Physical validation sequence

### A. Installation and default state

1. Install the CI-green debug APK alongside the stable app.
2. Launch the debug app normally.
3. Open Settings and confirm the global time display defaults to **Local time** for a fresh debug-app state.
4. Return without changing protocol-related settings.

Expected:
- app launches normally;
- stable app remains installed independently;
- LOCAL is the default presentation/query basis.

### B. Protected normal Live read

1. From Overview, perform one normal NFC read exactly as in normal use.
2. Keep the phone on the meter until the read completes.
3. Confirm the normal Live result appears and no archive synchronization starts automatically.
4. Open Meter details.

Expected:
- Live read succeeds through the established default-read path;
- no unexpected extra NFC interaction is visible;
- the physical meter is identified as usual;
- a per-meter IANA timezone is shown after the verified Live read;
- for a device configured for Germany this will normally be `Europe/Berlin`;
- provenance indicates automatic assignment from the device at the first verified Live read unless the debug-app state already contained an explicit user assignment.

### C. LOCAL History / Statistics

Use already synchronized archive data if the debug app contains it. If the debug installation has no archive baseline, perform only the normal explicit History synchronization needed to establish/refresh the test data, following the existing validated sync UX.

1. Open History in LOCAL mode.
2. Inspect Hour/Day/Month rows that are available.
3. Confirm the primary displayed time is the resolved local/civil time.
4. Confirm raw meter/logger time is still visible as secondary evidence on archive rows.
5. Open Statistics for a bounded period with known data.

Expected:
- History does not silently reorder by raw logger text when resolved UTC evidence is available;
- raw meter time remains visible, not overwritten;
- Statistics shows plausible coverage and values;
- no `0` total is invented merely because a LOCAL projection is unavailable.

### D. Global LOCAL ↔ METER switch

1. While remembering one visible History row/range, open Settings.
2. Switch to **Meter time**.
3. Return to History/Statistics.
4. Confirm the screen rerenders without needing an app restart.
5. Compare the same archive evidence: primary time should now follow raw meter/logger wall-clock semantics.
6. Switch back to **Local time** and return again.

Expected:
- switching presentation basis does not change stored measurements;
- METER reproduces the raw/floating time behavior;
- LOCAL returns to resolved civil time;
- the same underlying archive records remain identifiable;
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

After navigating History, Statistics, Settings and Meter details, return to Overview and perform another normal Live read.

Expected:
- normal protected Live read still succeeds;
- no stale selected archive state leaks into the Live result;
- no protocol regression is observed.

## Evidence to record

For the accepted candidate, record at minimum:

- APK source commit;
- GitHub Actions run id;
- artifact id and digest;
- Android device/model and Android version (non-sensitive only);
- meter family/model, without publishing a private serial/meter ID;
- PASS/FAIL for sections A–F;
- any user-visible discrepancy with a short description.

Screenshots are optional. Do not publish screenshots containing private meter IDs or personal consumption data without redaction.

## Acceptance rule

The physical gate passes only if A–F are all accepted and no protected NFC/archive behavior regresses. A UI wording/layout issue may be fixed in a follow-up candidate and retested selectively; a protocol/read regression reopens the safety gate and blocks release.
