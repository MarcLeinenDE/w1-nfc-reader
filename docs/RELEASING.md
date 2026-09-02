# Releasing W1 NFC Reader

Stable public releases are created from version tags and must use one long-term signing identity.

## Release identity

Before the first public stable release, create a dedicated signing key used only for W1 NFC Reader.

Recommended key properties:

- RSA 3072 bits or stronger;
- validity of at least 25 years (for example 10,000 days);
- unique alias such as `w1-nfc-reader-release`;
- strong, unique keystore/key passwords.

Example interactive command:

```bash
keytool -genkeypair \
  -keystore w1-nfc-reader-release.jks \
  -alias w1-nfc-reader-release \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

Do not commit the keystore or passwords. Keep at least two secure offline backups of the keystore and store recovery information separately from the file itself.

## GitHub Actions secrets

The public release workflow expects:

- `W1_RELEASE_KEYSTORE_BASE64`
- `W1_RELEASE_KEYSTORE_PASSWORD`
- `W1_RELEASE_KEY_ALIAS`
- `W1_RELEASE_KEY_PASSWORD`
- `W1_RELEASE_CERT_SHA256`

`W1_RELEASE_KEYSTORE_BASE64` is the base64 representation of the binary keystore stored only in GitHub Actions secrets. The original keystore remains the authoritative offline backup.

The expected certificate SHA-256 fingerprint is public information and may also be documented in release notes once the first stable key is created. The workflow uses the configured expected value as a release gate so an unexpected signer cannot silently create an official release candidate.

## Version preparation

Before tagging a stable version:

1. ensure `app/build.gradle.kts` contains the intended `versionCode`;
2. set `versionName` to the exact stable version without `-dev`;
3. update `CHANGELOG.md`;
4. ensure CI is green on the commit to be tagged;
5. run the release-safety/public-source audit.

For the first public release:

- versionName: `1.0.0`
- versionCode: `38`
- tag: `v1.0.0`

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

## Physical release gate

Download the signed APK from the draft GitHub Release and test that exact artifact before publishing the draft.

Minimum smoke test:

- clean install using the public application ID;
- live NFC read;
- explicit Month history synchronization;
- confirmed default-application restore;
- history/statistics presentation;
- `.qw1backup` export and restore;
- CSV export;
- share action;
- language switching;
- light/dark/system appearance.

Also verify the APK SHA-256 and signer certificate against the values produced/expected by the release workflow.

If any release-blocking issue is found, do not publish the draft. Fix the issue, create a new version/tag as appropriate and produce a new signed draft candidate.

Only after the exact draft APK passes the physical and product smoke checks should the existing GitHub Release draft be changed to **Published**. Publishing the draft must not rebuild or replace the tested APK.

If the public application ID differs from an old private development build, use the explicit backup/restore feature rather than assuming Android will migrate private-development app data automatically.
