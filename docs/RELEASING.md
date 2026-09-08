# Releasing W1 NFC Reader

Stable public releases are created from version tags and must use one long-term signing identity.

## Release identity

Use the dedicated long-term W1 NFC Reader release key for every stable public APK. Do not commit the keystore or passwords. Keep secure offline backups of the keystore and store recovery information separately from the file itself.

Recommended key properties for any future replacement identity are RSA 3072 bits or stronger and long-term validity. Replacing the established public signing identity is itself a breaking distribution event and must never happen casually.

## GitHub Actions secrets

The public release workflow expects:

- `W1_RELEASE_KEYSTORE_BASE64`
- `W1_RELEASE_KEYSTORE_PASSWORD`
- `W1_RELEASE_KEY_ALIAS`
- `W1_RELEASE_KEY_PASSWORD`
- `W1_RELEASE_CERT_SHA256`

`W1_RELEASE_KEYSTORE_BASE64` is the base64 representation of the binary keystore stored only in GitHub Actions secrets. The original keystore remains the authoritative offline backup.

The expected certificate SHA-256 fingerprint is public information. The workflow uses the configured expected value as a release gate so an unexpected signer cannot silently create an official release candidate.

## Version preparation

Before tagging a stable version:

1. ensure `app/build.gradle.kts` contains the intended monotonic `versionCode`;
2. set `versionName` to the exact stable version without `-dev`;
3. update `CHANGELOG.md` and release/upgrade documentation;
4. ensure normal CI is green on the exact commit intended for the tag;
5. run the release-safety/public-source audit;
6. verify that breaking changes are visible before the signed candidate is created.

Known stable identities:

- `1.0.0`: versionCode 38, tag `v1.0.0` — immutable historical release;
- `2.0.0`: versionCode 43, tag `v2.0.0`.

The release workflow refuses a tag whose name does not match `versionName` or whose version still contains `-dev`.

## Release workflow

A `vX.Y.Z` tag triggers `.github/workflows/release.yml`.

The workflow:

1. checks out the tagged source;
2. sets up JDK 17 / Android SDK / Gradle 8.9;
3. validates translations;
4. runs the unit/Robolectric regression suite;
5. restores the release keystore only inside the runner;
6. builds the release APK;
7. verifies the APK signature and expected certificate SHA-256;
8. generates the APK SHA-256 checksum;
9. creates a **draft GitHub Release** containing the signed APK and checksum;
10. removes the temporary runner keystore.

The workflow deliberately does **not** publish the release immediately. The draft is the physical release-candidate gate.

## v2.0.0 physical release gate

Download the signed APK from the **draft** GitHub Release and test that exact artifact. Do not rebuild, resign or replace it for the smoke test.

The v2 protocol-heavy Hour/Day/Month baseline and incremental paths were already physically validated during development. The final release gate therefore stays deliberately limited and must not consume another unnecessary full archive campaign.

Minimum v2.0.0 smoke:

1. install/update the exact signed candidate using the public application ID and confirm normal launch;
2. if needed to avoid a full archive reread, restore a known-good **v2 schema-2 `.qw1backup`** containing COMPLETE Hour/Day/Month state;
3. perform one normal Overview NFC contact and confirm a plausible protected Live read;
4. run one representative routine History update — preferably `Update all history` from an established COMPLETE baseline so the incremental path is exercised rather than rereading the entire archive;
5. confirm the update finishes with verified default restore and that a subsequent normal Overview contact again produces Live/default data;
6. briefly open History and Statistics and confirm the new data/state is presented plausibly;
7. confirm the v2 backup/restore surface remains usable on the signed candidate. A large backup matrix is not required if the same schema-2 backup was successfully restored for this smoke;
8. verify APK SHA-256 and signer certificate against the release workflow evidence.

Do **not** use a 1.x `.qw1backup` for the v2 smoke; 2.0.0 intentionally rejects backup schema 1. See `docs/V2_BREAKING_CHANGES.md`.

Previously completed dev.3/dev.4 UI checks — navigation, Back behavior, chevrons/filter layout, rotation/state retention, About support link/external browser and Full Re-Sync presentation — do not need to be repeated unless the signed candidate shows an unexpected regression.

If any release-blocking issue is found, do not publish the draft. Fix the issue and produce a new controlled signed candidate. Once physical validation has started for a tag/artifact, do not silently replace that APK asset or move/recreate the tag.

## Final repository cleanup gate

Before publishing a stable release:

1. close obsolete/superseded pull requests and remove obsolete temporary/import/audit branches where safe; keep only branches that are intentionally active;
2. verify that there are no open pull requests that depend on a branch planned for deletion;
3. review `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, `docs/`, licensing notices and issue templates against the actual release behavior;
4. verify that private handoff/current-state files, real-device evidence, raw captures, backups, meter data, signing material and private research are absent from the public tree;
5. verify that unreleased archive-family research is not exposed as a production feature;
6. confirm the release tag still points to the exact physically tested source commit;
7. confirm the draft release still contains the exact physically tested APK and published checksum;
8. run normal CI after documentation/repository-cleanup commits on `main` when the release branch is integrated there.

Documentation-only cleanup after the release tag may advance another branch; it must not move the already validated release tag or rebuild/replace the tested release APK.

Only after the exact draft APK passes the physical/product checks and the repository cleanup gate is complete should the existing GitHub Release draft be changed to **Published**. Publishing the draft must not rebuild or replace the tested APK.

If the public application ID differs from a private/development build, use the explicit v2 backup/restore feature rather than assuming Android will migrate `.dev` application data automatically.
