# AGENTS.md

This file gives coding/AI agents the repository-specific rules that matter most before changing W1 NFC Reader.

## Read first

Before making code or behavior changes, read:

1. `README.md`
2. `CONTRIBUTING.md`
3. `docs/PROTOCOL_SAFETY.md`
4. `docs/PRIVACY.md`
5. the relevant v2 design documents under `docs/`
6. `docs/RELEASING.md` when touching versioning, signing, tags or release automation

Repository code, tests, CI and documented real-device constraints are authoritative. Do not infer new protocol behavior from old comments, examples or a single observed meter capture.

## Product behavior that must remain true

- A normal NFC contact performs the fast Live/default read only.
- History synchronization starts only after an explicit user action and meter re-presentation/verification.
- The physically validated v2 product archive families are **Hour, Day and Month**. They remain independent synchronization families.
- Year/Billing is not exposed as a normal v2 history synchronization control unless separately validated and intentionally released.
- After a family has an authoritative COMPLETE baseline, normal updates use the conservative incremental path and stop only on secure known overlap or another valid semantic terminal condition.
- Never hardcode the number of retained historical periods. Traversal must stop from meter/protocol evidence.
- Every accepted archive record is persisted immediately.
- Ambiguous selected requests must never be blindly repeated.
- A family attempt is not complete without the required final default-application restore and verified Live/default read.
- A failed later NFC read must not erase the last successful Live state.
- Sharing uses the last successful Live read only.
- Live consumption deltas compare only to the previous Live observation on the same physical meter.
- Archive consumption deltas stay on the same meter and same archive granularity.
- Cumulative readings from different meter IDs must never be subtracted across a meter replacement.

## Protected protocol baseline

Treat the real-device-validated NFC/M-Bus path conservatively, especially:

- `QalcosonicReader.java`
- `MbusParser.java`
- NFC/mailbox/archive transport and traversal code

Changes that affect NFC transport, M-Bus parsing, archive traversal, terminal handling, selected-request sequencing or default-application restore must be small, evidence-backed and regression-tested. Unit tests alone do not prove physical meter behavior; such changes may require a new real-device validation gate before release.

Do not add intentional persistent writes to meter configuration, radio configuration, calibration values, metering parameters or firmware.

## Data and privacy rules

Never commit or publish real user/meter evidence such as:

- NFC UIDs;
- real meter identifiers;
- private consumption history;
- `.qw1backup` files;
- exported user CSV files;
- unredacted raw NFC/M-Bus captures;
- private field/debug traces;
- keystores, private keys, passwords, tokens or signing-secret material.

Use synthetic/minimized fixtures for public regression tests whenever possible.

Preserve these data semantics:

- archive identity = `meter_id + archive_family + logger_timestamp`;
- same identity + same content = idempotent confirmation;
- same identity + different content = retain conflict/revision evidence, never silently overwrite;
- archive logger timestamps must not be silently rewritten through timezone/DST conversion;
- `.qw1backup` is the canonical machine-restorable format; CSV is a human-readable export.

## UI and translations

- Put user-facing text in Android string resources.
- Keep all currently supported product locales coherent: English fallback, German, French, Polish, Dutch and Lithuanian.
- Preserve formatting placeholders and run the translation consistency check after string changes.
- Overview is the single Android task root. Normal secondary screens must remain on the Android Back stack; only Overview uses the double-back-to-exit guard.
- History/Statistics filters should stay compact and should not consume disproportionate screen space.

## Build and test baseline

The project currently uses:

- JDK 17
- Android SDK 35
- Gradle 8.9

Before submitting a normal code change, run:

```bash
python3 scripts/check_product_i18n.py
./gradlew testDebugUnitTest assembleDebug
```

Add targeted regression coverage for changes to history persistence, backup/restore, meter replacement, timestamp provenance, navigation, parsing or protocol traversal.

## Release safety

- Do not move, recreate or replace an existing stable release tag/artifact merely for documentation or repository-cleanup changes.
- Do not replace a physically tested release APK with a newly built APK under the same release identity.
- Stable release publication must follow `docs/RELEASING.md` and its physical release gate.
- Release signing material must remain outside the repository.

## Scope discipline

Prefer focused changes over broad refactors, especially around protocol code. Preserve third-party attribution and licensing notices. If a requested change conflicts with these rules, stop and surface the conflict rather than silently broadening product or protocol scope.
