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
4. create `docs/releases/<versionName>.md` with curated user-facing release notes;
5. ensure normal CI is green on the exact commit intended for the tag;
6. run the release-safety/public-source audit;
7. verify that breaking changes, migration requirements and material known limitations are visible before the signed candidate is created.

Known stable identities:

- `1.0.0`: versionCode 38, tag `v1.0.0` — immutable historical release;
- `2.0.0`: versionCode 43, tag `v2.0.0` — published and immutable.

The release workflow refuses a tag whose name does not match `versionName`, whose version still contains `-dev`, or whose matching curated release-notes file is missing.

## Curated release notes contract

See `docs/releases/README.md`.

User-facing upgrade information comes before technical PR listings. Generated PR notes are useful as a lower technical-details section, but they must never be the only release description when breaking changes or known limitations exist.

## Release workflow

A `vX.Y.Z` tag triggers `.github/workflows/release.yml`.

The workflow:

1. checks out the tagged source;
2. sets up JDK 17 / Android SDK / Gradle 8.9;
3. validates tag, stable version and curated release notes;
4. validates translations;
5. runs the unit/Robolectric regression suite;
6. restores the release keystore only inside the runner;
7. builds the release APK;
8. verifies the APK signature and expected certificate SHA-256;
9. generates the APK SHA-256 checksum;
10. creates a **draft GitHub Release** for the exact existing tag using the curated notes file;
11. uploads the signed APK and checksum;
12. removes the temporary runner keystore.

The workflow deliberately does **not** publish the release immediately. The draft is the physical release-candidate gate.

The workflow uses the GitHub CLI available on the hosted runner rather than a third-party release action. This keeps the release/tag association explicit and avoids the runtime-deprecation warning observed during the v2.0.0 release workflow.

## Physical release gate

Download the signed APK from the **draft** GitHub Release and test that exact artifact. Do not rebuild, resign or replace it for the smoke test.

The required smoke depth depends on what changed:

- UI/documentation/database-only changes can normally rely mostly on CI plus a focused product smoke;
- NFC transport, archive acquisition/traversal, terminal handling, restore/default behavior or other protocol/device semantics require appropriate real-device evidence.

For protocol-heavy releases, avoid repeating already validated expensive archive campaigns unless later code actually touched those paths.

General stable-release smoke should confirm at least:

1. install/update the exact signed candidate using the stable public application ID;
2. normal launch and one plausible protected Live read;
3. representative use of the release's main changed surface;
4. when History behavior changed, one representative History synchronization/update and verified return to normal Live/default state;
5. History/Statistics presentation remains plausible where applicable;
6. backup/restore or migration surfaces affected by the release remain usable;
7. APK SHA-256 and signer certificate match release-workflow evidence.

If any release-blocking issue is found, do not publish the draft. Fix the issue and produce a new controlled signed candidate/version. Once physical validation has started for a tag/artifact, do not silently replace that APK asset or move/recreate the tag.

## Publishing an accepted draft

Publish the **existing** draft; do not create a second release and do not rebuild the artifact.

When using `gh api`/the GitHub Releases API, explicitly send the intended tag name along with `draft=false`, then verify the result by tag.

Conceptually:

`PATCH /repos/<owner>/<repo>/releases/<release-id>`

with:

- `tag_name=vX.Y.Z`
- `draft=false`

Post-publication verification must confirm:

- expected release ID;
- `tag_name=vX.Y.Z`;
- `draft=false` and `prerelease=false` unless intentionally different;
- expected APK/checksum asset identities;
- downloaded public APK SHA-256 equals the physically accepted candidate;
- published checksum file contains the same APK SHA-256;
- signer evidence remains the one verified by the release workflow.

### v2.0.0 publication lesson

The first v2.0.0 draft-to-public API update omitted an explicit `tag_name` and GitHub temporarily associated the public release with an `untagged-*` placeholder. The real immutable `v2.0.0` Git tag never moved and both tags pointed to the same release commit. The existing release was repaired in place by explicitly setting `tag_name=v2.0.0`, the public APK was downloaded and byte-verified, and the temporary tag was removed.

Future publishing commands must therefore explicitly preserve the intended release tag and verify the release by tag before cleanup branches are deleted.

## Repository cleanup after publication

After a stable release is publicly verified:

1. update the current handoff/release bookkeeping with publication evidence;
2. delete obsolete release-candidate and maintenance branches once their work is merged and no open PR depends on them;
3. archive development checkpoints that are useful historically but stale as current documentation;
4. remove only dead code that is proven unreferenced and covered by CI;
5. do not cosmetically churn protected NFC/M-Bus/archive traversal code;
6. keep the steady-state branch set minimal: `main` plus only the currently active development/handoff branch(es);
7. keep release tags immutable.

Documentation-only or maintenance cleanup after a release tag may advance `main`; it must not move the already validated release tag or rebuild/replace the published release APK.

If the public application ID differs from a private/development build, use the explicit backup/restore feature rather than assuming Android will migrate `.dev` application data automatically.
