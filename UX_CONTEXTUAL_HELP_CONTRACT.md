# W1 NFC Reader — Contextual Help UX Contract

Date: 2026-09-12
Status: accepted UX rule for v2.1 release-candidate polish and future app work

## Purpose

Contextual help exists to explain technically correct behavior that a normal user could reasonably misinterpret without domain knowledge. It must reduce uncertainty, not make the interface look instrumented everywhere.

The user should be able to understand a page without searching Settings, GitHub or a manual, but explanations should stay out of the primary flow unless they are actually needed.

## Placement and redundancy rule

The v2.1 physical UI review established a stricter placement rule:

- History help belongs on the **History page heading only**.
- Statistics help belongs on the **Statistics page heading only**.
- Do not attach an info affordance merely because the words `History` / `Historie` or `Statistics` / `Statistik` occur elsewhere.
- In particular, no duplicate info icon belongs on drawer/navigation entries, overview labels, settings rows or other repeated occurrences of the same word.
- A page-level explanation should absorb generic explanatory copy that would otherwise be repeated farther down the same page.
- Do not repeat the same explanation both behind the page info control and as a long inline paragraph.
- Keep inline text when it is **metric-specific, actionable, warning-level or otherwise not covered by the page help**.
- Do not hide warnings, failures or required actions behind an info control.

For v2.1 Statistics this means the generic explanation of filled bars, dashed edge intervals, KPI inclusion, zero-consumption markers, true gaps and coverage-vs-sync semantics lives in the single Statistics page info dialog. The chart remains visually self-explanatory and the visible coverage line remains concise. Metric-specific caveats for temperature, flow, battery and alarm evidence may remain inline because they are not generic page-help duplication.

## Interaction pattern

- Use a small Material info icon associated with the actual page heading or other deliberately chosen concept.
- Tapping opens a short context-specific dialog or bottom sheet.
- The first paragraph answers the immediate question in plain language.
- Optional secondary text may explain technical reason and consequences.
- Explanations are localized with the rest of the product UI.
- The control must be accessible through TalkBack/content descriptions and have a sufficiently large touch target.
- Do not add info icons to self-evident labels or repeated navigation text merely to create visual consistency.

## Canonical real timeline / raw meter-clock evidence

The normal v2.1 product UI has one canonical real timeline. Archive real time is reconstructed from verified Live/default anchors plus monotonic meter `ON_TIME`, then projected through the persisted per-meter IANA zone. Live real time is the actual acquisition epoch.

The raw meter/logger wall clock is preserved source evidence, but it is no longer an equal normal-product time mode. The former global `Local time / Meter time` selector is retired from normal Settings.

Where raw meter/logger time is shown as secondary evidence, explanations should make clear that:

- the meter's internal wall clock may drift relative to real time;
- the app does not simply apply today's wall-clock difference retrospectively;
- archive real time is reconstructed from elapsed `ON_TIME` plus a verified real-time anchor;
- `ON_TIME` alone is not a civil timestamp and cannot be used without a suitable anchor;
- unsafe/unavailable reconstruction fails closed rather than silently promoting raw meter time;
- raw meter/logger time remains available for diagnosis, export and backup.

## Global date/time presentation consistency

The v2.1 polish pass uses a repository-wide presentation rule rather than screen-specific punctuation fixes.

Rules:

- when one displayed instant contains both a date and a time, separate them consistently with `date · time`;
- interval labels use the same convention, e.g. `11.09.2026 · 18:00–19:00`;
- locale-specific ordering remains authoritative;
- central locale-aware presentation helpers/hardening are preferred so Overview, History, Statistics, Meter Details, sync/result UI and warnings do not drift apart;
- raw stored timestamps are never rewritten by this presentation rule;
- secondary evidence, acquisition timestamps and accessibility descriptions are included where applicable.

## Timezone presentation and selection

For ordinary local display, prefer a recognizable localized zone abbreviation such as `MEZ/MESZ` or `CET/CEST` instead of routinely showing `+01:00/+02:00`.

The per-meter IANA-zone editor follows a constrained-selection product rule:

- do not expose arbitrary free-text persistence in the normal UI;
- populate the searchable picker from the timezone IDs supported by the running Android runtime (`ZoneId.getAvailableZoneIds()`), with `UTC` available;
- preselect the meter's currently persisted zone;
- allow typing to filter/search the supported set;
- Save succeeds only for an exact member of that supported set and still passes through the canonical `MeterTimeModelStore` validation/persistence path;
- changing the assigned zone changes only the local-time interpretation and never rewrites raw meter/logger timestamps, `ON_TIME`, canonical occurrence identity or archive payloads.

Additional guardrails:

- the assigned IANA zone remains the actual timezone identity;
- numeric UTC offset remains diagnostic truth;
- in a repeated/ambiguous DST fold hour, include enough offset information to distinguish occurrences;
- never use a friendly abbreviation to erase real occurrence ambiguity;
- do not invent localized country/city aliases that could obscure the stored IANA ID; the picker may display the canonical IDs directly.

The former release smoke test that depended on deliberately typing an invalid free-text IANA ID is superseded by the stronger normal-product guarantee that unsupported arbitrary IDs cannot be persisted through the picker.

## Statistics / coverage semantics

A count such as `22 von 24` must not imply missing data when two real physical archive intervals merely overlap civil-window edges.

Current chart contract:

- fully-contained bucket = filled bar and included in selected-window KPIs;
- partially overlapping real bucket = dashed outline showing the complete measured value as context only, excluded from KPIs;
- genuine zero bucket = visible baseline marker;
- missing native bucket = no fabricated bar.

The Statistics page info explanation covers:

- why fixed physical archive intervals may not align to local calendar boundaries;
- why edge intervals are not split or prorated;
- why dashed intervals are shown but excluded from KPIs;
- the distinction between zero consumption and missing data;
- that coverage count describes the selected analytical view and does not by itself prove protocol synchronization completeness.

The normal page should therefore show the concise coverage count without repeating the same explanatory paragraph underneath the chart.

## Statistics x-axis ownership

Every visible consumption bar must map unambiguously to one visible period label.

Rules:

- one visible bar = one x-axis label;
- label is centered on the same slot as its bar;
- do not silently skip labels on dense bar charts if that makes ownership ambiguous;
- when labels no longer fit horizontally, rotate the complete set together and reserve sufficient chart height;
- labels describe the actual represented interval/period, including shifted resolved intervals where a compact civil label would be false;
- line charts may continue to thin labels where point ownership remains visually unambiguous.

## Deferred next-version chart-axis audit

Do not alter the current v2.1 release candidate solely for this deferred audit. In the next app version, perform a repository-wide visual/implementation audit of **every Statistics chart**, not only consumption.

The same x-axis ownership/orientation policy must be applied consistently to all relevant metrics, including at minimum consumption, battery, flow, water temperature, ambient temperature and alarm/event views where an x-axis is shown.

Specific acceptance points for that audit:

- verify that no metric has a leftover chart-specific x-axis implementation with different rotation/orientation behavior;
- on dense categorical/bar views, apply the agreed rotated/vertical label treatment consistently to the complete label set rather than mixing horizontal and vertical labels or skipping individual labels;
- preserve one visible bar = one centered period label;
- reserve enough chart height so rotated labels are not clipped;
- line-chart label thinning remains allowed only where point ownership stays visually unambiguous;
- check the real UI for battery and flow in particular, because inconsistent x-axis text orientation was observed there during the v2.1 physical review;
- prefer fixing this in shared chart-axis policy/rendering rather than one metric at a time, and add regression coverage so future metrics inherit the same behavior.

## History `All` — Live delta baseline semantics

In `History → All`, the Live card is part of a mixed chronological timeline. Its consumption delta uses the chronologically nearest trustworthy **earlier archive observation** from the same physical meter as its baseline.

Baseline selection:

1. Consider valid archive observations from Hour, Day and Month together.
2. Choose the archive observation with the latest canonical time that is still strictly earlier than the Live read.
3. Do not use a fixed family priority such as Hour > Day > Month; chronology is authoritative.
4. If no valid earlier archive observation exists, fall back to the immediately previous valid Live observation.
5. If neither exists, do not fabricate a delta.
6. Never calculate across meter replacement, incompatible meter identity, known conflict or unsafe/unresolved chronology.

The dedicated `Live` filter remains Live-to-Live. Archive cards retain same-family archive-series delta semantics.

## Guardrails

- Explanations describe implemented behavior; they do not paper over incorrect behavior.
- Primary labels must remain understandable without requiring the dialog for basic correctness.
- Do not duplicate the same generic help in page body and dialog.
- Do not put page-help icons into navigation/drawer/menu repetitions.
- Never fabricate data to make coverage look complete.
- Never present a real coverage gap as a time-resolution problem, or vice versa.
- Never calculate a Live delta across meter replacement or unsafe chronology.
- Never silently promote raw meter/logger wall-clock to canonical real time.
- Canonical UTC/archive identity, raw meter evidence and protocol-safety rules remain authoritative.

## v2.1 implementation status

Current candidate: `67aafb713a0b09d23058b31640ba94bc0353bb21`.

Implemented and CI-green:

- mixed History-All Live archive-baseline rule;
- one-label-per-bar Statistics axis ownership with automatic rotation;
- centered `date · time` presentation hardening;
- localized timezone abbreviations with DST-fold disambiguation;
- dashed partial-edge bars and visible zero-consumption markers;
- History/Statistics contextual help scoped to the actual page heading only;
- duplicate generic Statistics explanation removed from chart body; concise coverage line retained;
- searchable device-supported IANA timezone picker replacing arbitrary free-text persistence;
- six-locale UI/help copy and regression suite.

Physical spot-check of the timezone picker and final UI state is still required before the final protected Live regression and release preparation.
