# Curated release notes

Every future stable release must carry a curated Markdown file in this directory before its tag is created.

File naming:

`docs/releases/<versionName>.md`

Example:

`docs/releases/2.1.0.md`

The file is consumed directly by `.github/workflows/release.yml` when a matching `vX.Y.Z` tag is pushed.

## Required content

Release notes must be written for users first and include, where applicable:

- Highlights;
- important upgrade information;
- breaking changes;
- known limitations that materially affect interpretation or migration;
- installation/update compatibility;
- privacy/safety notes when behavior changes;
- download verification information if it is known before publication;
- a technical PR/changelog section after the user-facing material.

The release workflow deliberately refuses to create a draft when the curated notes file is missing or lacks a recognizable user-facing section.

## Draft-first rule

The workflow creates a **draft** release only. The signed APK in that draft is the physical release candidate.

After physical validation begins:

- do not rebuild or silently replace the APK;
- do not move/recreate the tag;
- if a blocker is found, produce a new controlled candidate/version rather than mutating validated evidence.

## Publishing rule

When publishing an existing draft through the GitHub API/CLI, explicitly keep the intended tag name in the update request and verify the release by tag afterwards.

For example, conceptually:

`PATCH release-id: tag_name=vX.Y.Z, draft=false`

This avoids a repeat of the v2.0.0 publication incident where a draft temporarily became associated with GitHub's `untagged-*` placeholder even though the real immutable tag remained correct.

Always verify after publication:

- release ID;
- `tag_name`;
- `draft=false`;
- asset IDs/names;
- downloaded APK SHA-256;
- published checksum file;
- signer evidence from the release workflow.
