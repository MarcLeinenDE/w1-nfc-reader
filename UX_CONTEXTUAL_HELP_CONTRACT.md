# W1 NFC Reader — Contextual Help UX Contract

Date: 2026-09-11
Status: accepted UX rule for the v2.1 release-candidate polish pass and future app work

## Purpose

Whenever the app shows technically correct information that a normal user could reasonably misinterpret without domain knowledge, provide an explanation directly at the point of use through a small, accessible information control (Material info icon / `i`).

The user must not need to search Settings, a manual, GitHub or a general Help page to understand the meaning of the value currently on screen.

This is a global product rule, not a Statistics-only exception.

## Interaction pattern

- Place a small info icon next to the label, status, coverage text or concept that needs explanation.
- Tapping it opens a short context-specific dialog or bottom sheet.
- The first paragraph answers the immediate question in plain language.
- Optional secondary text may explain the technical reason and consequences.
- Explanations must be localized with the rest of the product UI; no hard-coded German-only help text.
- The control must be accessible through TalkBack/content descriptions and have a sufficiently large touch target.
- Do not show an info icon for self-evident labels merely to create visual noise.
- Do not hide warnings, failures or required user actions behind an info icon. The icon explains a state; it never replaces a required warning/action.

## Statistics / coverage example

Current ambiguous wording such as `22 von 24 Datenpunkten verfügbar` can make a user believe that two records are missing even when all stored evidence is present and two physical Hour intervals merely intersect the LOCAL civil-day edges.

Prefer a semantically precise visible summary such as:

- `22 von 24 Stunden vollständig enthalten  ⓘ`
- `10 von 30 Tagen vollständig enthalten  ⓘ`

The corresponding explanation should state, in localized plain language:

- the meter stores consumption in fixed physical archive intervals;
- those intervals do not necessarily begin/end at LOCAL calendar boundaries;
- only intervals fully contained in the selected Statistics window are used for consumption differences;
- edge intervals that only partially overlap are not split or prorated because the app must not invent fractional consumption;
- therefore a count below the civil-window total does not automatically mean that archive records are missing.

Where the data model can distinguish causes, the visible UI should distinguish them too. For example:

- `22 von 24 vollständig enthalten · 2 Randintervalle`
- versus `21 von 24 · 1 Datenlücke`

Do not collapse these semantically different cases into the same generic `available` wording.

## Scope across the app

During the release-candidate UI/polish pass, review all user-facing screens for concepts that are technically correct but may not be immediately obvious. Candidate areas include, but are not limited to:

- Statistics coverage / fully-contained buckets / edge intervals;
- LOCAL versus METER time basis;
- timezone provenance and automatic IANA-zone assignment;
- DST-related repeated or shifted local times where additional explanation is useful;
- History versus Live semantics;
- History sync coverage versus protocol completeness;
- `KNOWN_RECORD_REACHED`, partial/incremental sync outcomes or other sync-state wording when surfaced to ordinary users;
- difference between an actual data gap and a time-resolution problem;
- zero-consumption chart points that are valid but visually have zero height;
- warnings/status indicators whose meaning or consequence is not self-evident;
- any advanced/diagnostic field retained in the normal UI that requires domain knowledge.

The review is not limited to Hour views. Day, Month, All, Overview, History, Statistics, Meter Details, sync/result UI and Settings should all be checked.

## Guardrails

- Explanations must describe the implemented behavior, not paper over incorrect behavior.
- A contextual explanation does not turn a confusing or wrong primary label into an acceptable label; primary wording should still be improved where possible.
- Never fabricate data to make coverage look complete.
- Never present a known coverage gap as a LOCAL time-resolution failure, or vice versa.
- Canonical UTC/archive identity, raw meter evidence and existing protocol-safety rules remain authoritative.

## Release integration

Do not modify the currently pinned functional C-test APK solely for this presentation pattern while Section C–F validation is still in progress.

After the functional gate passes, implement the contextual-help pass together with the already-recorded presentation items (localized timezone abbreviations, missing centered date/time separators and visible zero-consumption markers), then run UI/i18n/accessibility regression checks before preparing the exact v2.1 release candidate.
