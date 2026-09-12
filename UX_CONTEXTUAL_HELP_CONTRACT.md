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

The v2.1 polish pass uses a repository-wide presentation rule rather than screen-specific punctuation fixes.

Rules:

- whenever one displayed instant contains both a date and a time, separate them consistently with the centered dot pattern `date · time`;
- interval labels use the same convention, for example `11.09.2026 · 18:00–19:00`;
- do not force German date ordering onto other locales;
- central locale-aware presentation helpers/hardening are preferred so Overview, History, Statistics, Meter Details, sync/result UI, warnings/status cards and other screens cannot drift into different punctuation rules;
- preserve raw stored timestamps unchanged; this is a presentation-only contract;
- include secondary evidence, warning timestamps, acquisition timestamps, dialog summaries and accessibility/content-description strings in the audit where applicable;
- retain regression coverage for representative formatters/high-value UI paths.

## Timezone label presentation

For ordinary local display, prefer a recognizable localized zone abbreviation such as `MEZ/MESZ` or `CET/CEST` instead of routinely showing `+01:00/+02:00`.

Guardrails:

- the assigned IANA zone remains the actual timezone identity;
- numeric UTC offset remains available as diagnostic truth;
- in a repeated/ambiguous DST fold hour, include enough offset information to distinguish the two occurrences even if the localized abbreviation is also shown;
- never use a friendly abbreviation to erase real occurrence ambiguity.

## Statistics / coverage semantics

Wording such as `22 von 24 Datenpunkten verfügbar` can make a user believe two records are missing even when all stored evidence is present and two physical Hour intervals merely intersect the LOCAL civil-day edges.

Prefer semantically precise visible wording such as:

- `22 von 24 Stunden vollständig enthalten  ⓘ`
- `10 von 30 Tagen vollständig enthalten  ⓘ`

The explanation should state:

- the meter stores consumption in fixed physical archive intervals;
- those intervals do not necessarily begin/end at LOCAL calendar boundaries;
- fully-contained intervals are included in selected-window consumption KPIs;
- edge intervals that only partially overlap are not split or prorated because the app must not invent fractional consumption;
- a count below the civil-window total does not automatically mean archive records are missing;
- genuine zero consumption is valid data and should be visually distinguishable from a missing interval;
- known native gaps remain real gaps.

Current chart contract:

- fully-contained bucket = filled bar and included in KPIs;
- partially overlapping real bucket = dashed outline showing the complete measured value as context only, excluded from KPIs;
- genuine zero bucket = visible baseline marker;
- missing native bucket = no fabricated bar.

## Statistics x-axis ownership

Every visible consumption bar must map unambiguously to one visible period label.

Rules:

- one visible bar = one x-axis label;
- label is centered on the same slot as its bar;
- do not silently skip labels on dense Hour charts if that makes bar ownership ambiguous;
- when labels no longer fit horizontally, rotate the complete set of bar labels together and reserve sufficient chart height;
- labels must describe the actual represented interval/period, including shifted resolved intervals where a compact civil label would be false;
- line charts may continue to thin labels where point ownership remains visually unambiguous; this one-label-per-bar contract is specifically for bar charts.

## History `All` — Live delta baseline semantics

In `History → All`, the Live card is part of a mixed chronological timeline. Its consumption delta uses the chronologically nearest trustworthy **earlier archive observation** from the same physical meter as its baseline.

Baseline selection:

1. Consider valid archive observations from Hour, Day and Month together.
2. Choose the archive observation with the latest canonical time that is still strictly earlier than the Live read.
3. Do **not** use a fixed family priority such as Hour > Day > Month. Chronology is authoritative.
4. If no valid earlier archive observation exists, fall back to the immediately previous valid Live observation.
5. If neither exists, do not fabricate a delta.
6. Never calculate across meter replacement, incompatible meter identity, known conflict or unsafe/unresolved chronology.
7. If two trustworthy archive observations represent exactly the same effective boundary, the implementation may choose a deterministic representative only if doing so does not change the cumulative meter total or imply a false chronology; no family priority should be presented as product semantics.

Examples:

- latest historical observation is an Hour boundary at 19:00 → Live delta means `since 19:00`;
- no recent Hour exists and the nearest earlier observation is a Day close → Live delta uses that Day close;
- only a Month close exists before the Live read → Live delta uses that Month close;
- no earlier archive observation exists but an earlier Live read exists → Live delta means `since previous Live read`;
- first-ever Live read with no predecessor → no consumption delta is shown.

This behavior applies only to the mixed `All` timeline. The dedicated `Live` filter continues to compare Live reads with the previous Live read. Archive cards retain their same-family archive-series delta semantics.

The visible label should make the chosen baseline time clear enough that the user does not need to infer it.

## Scope across the app

Review all user-facing screens for concepts that are technically correct but may not be immediately obvious. Candidate areas include:

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

- Explanations must describe implemented behavior, not paper over incorrect behavior.
- A contextual explanation does not turn a confusing or wrong primary label into an acceptable label; primary wording should still be improved where possible.
- Never fabricate data to make coverage look complete.
- Never present a known coverage gap as a time-resolution failure, or vice versa.
- Never calculate a Live delta across meter replacement or an unsafe chronology.
- Never silently promote raw meter/logger wall-clock to canonical real time.
- Canonical UTC/archive identity, raw meter evidence and existing protocol-safety rules remain authoritative.

## v2.1 implementation status

Implemented in CI-green candidate `e6967885315283ed7943bb77b589b78d855fc6c1`:

- mixed History-All Live archive-baseline rule;
- one-label-per-bar Statistics axis ownership with automatic rotation;
- centered `date · time` presentation hardening;
- localized timezone abbreviations with DST-fold disambiguation;
- contextual info dialogs for History, Statistics and Meter Details time model;
- visible zero-consumption marker and dashed partial-edge Statistics presentation;
- six-locale help copy and regression coverage.

Physical visual confirmation is still required before release preparation. The help contract remains a continuing product rule beyond v2.1.
