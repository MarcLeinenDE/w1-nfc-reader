# W1 NFC Reader — Contextual Help UX Contract

Date: 2026-09-12
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

## Canonical real timeline / raw meter-clock evidence

The normal v2.1 product UI has one canonical real timeline. Archive real time is reconstructed from verified Live/default anchors plus monotonic meter `ON_TIME`, then projected through the persisted per-meter IANA zone. Live real time is the actual acquisition epoch.

The raw meter/logger wall clock is preserved source evidence, but it is no longer an equal normal-product time mode. The former global `Local time / Meter time` selector is retired from normal Settings.

Where raw meter/logger time is shown as secondary evidence, the contextual explanation should make clear that:

- the meter's internal wall clock may drift relative to real time;
- the app does not simply apply today's wall-clock difference retrospectively;
- archive real time is reconstructed from elapsed `ON_TIME` plus a verified real-time anchor;
- `ON_TIME` alone is not a civil timestamp and cannot be used without a suitable anchor;
- when real-time reconstruction is unsafe/unavailable, the app fails closed rather than silently promoting raw meter time to canonical time;
- raw meter/logger time remains available for diagnosis, export and backup.

Suggested plain-language explanation in localized form:

> The displayed real time is reconstructed from the meter's elapsed operating time and verified readout times. The meter's own clock may drift and is therefore shown only as additional raw evidence.

## Global date/time presentation consistency

The v2.1 polish pass must perform a repository-wide audit of every user-facing date/time presentation path, not only the currently observed Overview and Meter Details examples.

Rules:

- whenever one displayed instant contains both a date and a time, separate them consistently with the centered dot pattern `date · time`;
- interval labels use the same convention, for example `11.09.2026 · 18:00–19:00`;
- do not manually concatenate date/time independently in individual screens when a shared presentation helper can express the same semantics;
- prefer central locale-aware presentation helpers so Overview, History, Statistics, Meter Details, sync/result UI, warnings/status cards and other screens cannot drift into different punctuation/formatting rules;
- preserve raw stored timestamps unchanged; this is a presentation-only contract;
- locale-specific date and time ordering remains authoritative. The centered dot separates the date component from the time component; it does not force German ordering onto other locales;
- audit labels, secondary evidence, warning timestamps, latest-sync timestamps, acquisition timestamps, dialog summaries and accessibility/content-description strings as well as the obvious main cards;
- add regression coverage for representative shared formatters and high-value UI contracts instead of relying only on screenshot inspection.

Known examples that motivated the global audit include Overview `Lokale Zeit` / former `Ausgelesen`, Meter Details `Am Handy ausgelesen`, History secondary `Lokale Zeit`, and `Letzte Archivsynchronisierung`, but the work item is explicitly broader than these examples.

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

## History `All` — Live delta baseline semantics

In `History → All`, the Live card is part of a mixed chronological timeline. Its consumption delta should therefore use the chronologically nearest trustworthy previous observation from that same physical meter as its baseline.

Baseline selection:

1. Prefer the latest valid archive observation before the Live read, regardless of whether it is Hour, Day or Month.
2. If no valid archive observation exists before the Live read, fall back to the immediately previous valid Live observation.
3. If neither exists, do not fabricate a delta.
4. Never calculate across meter replacement, incompatible physical meter identity, known conflict or an unsafe/unresolved chronology.
5. If more than one archive resolution ends at the same effective boundary, prefer the finest available archive resolution (`Hour` before `Day` before `Month`) for deterministic presentation; the cumulative meter reading should remain consistent.

Examples:

- latest historical observation is an Hour boundary at 19:00 → Live delta means `since 19:00`;
- no Hour/Day data exists but the latest historical observation is a Month boundary → Live delta means `since month-end ...`;
- there are no historical archive observations at all, but an earlier Live read exists → Live delta means `since previous Live read`;
- first-ever Live read with no predecessor → no consumption delta is shown.

This behavior applies only to the mixed `All` timeline. The dedicated `Live` filter may continue to compare Live reads with the previous Live read.

The visible label must make the baseline explicit.

## Scope across the app

During the release-candidate UI/polish pass, review all user-facing screens for concepts that are technically correct but may not be immediately obvious. Candidate areas include, but are not limited to:

- canonical real-time reconstruction versus raw meter-clock evidence;
- Statistics coverage / fully-contained buckets / edge intervals;
- timezone provenance and automatic IANA-zone assignment;
- DST-related repeated or shifted local times where additional explanation is useful;
- History versus Live semantics;
- mixed `All`-timeline Live-delta baseline selection;
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
- Never present a known coverage gap as a time-resolution failure, or vice versa.
- Never calculate a Live delta across meter replacement or an unsafe chronology.
- Never silently promote raw meter/logger wall-clock to canonical real time.
- Canonical UTC/archive identity, raw meter evidence and existing protocol-safety rules remain authoritative.

## Release integration

After the functional gate passes, implement the contextual-help pass together with the recorded presentation items: locale-aware timezone abbreviations, global centered date/time separators, visible zero-consumption markers, precise coverage wording, and the `All`-timeline Live baseline rule. Then run UI/i18n/accessibility regression checks before preparing the exact v2.1 release candidate.
