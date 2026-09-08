# Contributing to W1 NFC Reader

Contributions, corrections and reproducible compatibility observations are welcome.

## Before opening a pull request

- Keep the app's read-focused safety model intact.
- Do not add persistent meter, radio, calibration, metering-parameter or firmware writes without prior design discussion and strong evidence.
- Treat `QalcosonicReader.java`, `MbusParser.java` and the NFC/mailbox/archive transport path as protected, real-device-validated protocol baseline. Changes there should be small, focused, evidence-backed and covered by targeted regression tests.
- The physically validated v2 archive families are **Hour, Day and Month**. Keep them independent. Do not expose Year/Billing acquisition as a normal product feature without separate physical validation and an explicit product decision.
- Preserve the product rule that a normal NFC contact performs a live read only; history synchronization must remain an explicit user action.
- After a COMPLETE family baseline, normal synchronization should use the conservative incremental path rather than replaying the full archive unnecessarily.
- Never hardcode a historical period count observed on one meter. Traversal must stop from protocol/meter evidence.
- Do not blindly repeat an ambiguous selected request.
- Preserve immediate persistence of accepted archive records and the mandatory final default restore/Live verification.
- Live consumption compares to the previous Live read on the same physical meter only; archive deltas remain same-meter and same-granularity.
- Do not commit or post real NFC UIDs, real meter identifiers, private consumption values, unredacted raw captures, backups or secrets.
- Preserve third-party attribution and license notices.
- Use Android string resources for user-facing text.
- Keep the supported language sets coherent: English fallback, German, French, Polish, Dutch and Lithuanian.

## Tests

Before submitting a code change, run:

```bash
python3 scripts/check_product_i18n.py
./gradlew testDebugUnitTest assembleDebug
```

Changes to history persistence, backup/restore, meter replacement, timestamp provenance, navigation or protocol traversal should include regression coverage for the affected behavior.

## Protocol and real-device changes

A protocol change is not considered validated only because a unit test passes. When a change affects NFC transport, M-Bus parsing or archive traversal, document:

1. what protocol behavior is changing;
2. the evidence supporting the change;
3. which regression tests protect the old and new behavior;
4. whether a physical meter validation is required before release.

Do not use a public issue to upload sensitive raw captures. Prefer privacy-safe diagnostics and synthetic/minimized test fixtures.

Pure UI/database/analytics changes should not invent a new physical-meter gate when they do not alter meter communication.

## Scope of useful contributions

Useful contributions include:

- bug fixes;
- accessibility and UI improvements;
- translations;
- privacy-safe regression tests;
- documentation;
- evidence-backed decoding improvements;
- compatibility reports from independently tested Qalcosonic W1 meters and Android devices.

For significant behavior, storage-format or protocol changes, open an issue or discussion first so the design and safety boundary can be agreed before implementation work begins.

## Maintainer availability

This is a personal spare-time project. The maintainer has a young child and limited free time, so there is no guaranteed response or review time for issues and pull requests. Please do not interpret a delayed response as rejection. Well-documented contributions that are easy to reproduce and review are especially helpful.

## Licensing

By contributing project-authored source code, you agree that your contribution may be distributed under the project's **GPL-3.0-or-later** software license. Project-authored documentation is distributed under **CC-BY-SA-4.0** unless stated otherwise.

Do not contribute code copied from third-party projects unless its provenance and license compatibility are explicitly documented and accepted first.
