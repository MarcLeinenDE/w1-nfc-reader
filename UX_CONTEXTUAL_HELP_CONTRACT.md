# W1 NFC Reader — Contextual Help / Presentation UX Contract

Date: 2026-09-12
Status: accepted UX rule for v2.1 release-candidate polish and future app work

## Purpose

Contextual help exists to explain technically correct behavior that a normal user could reasonably misinterpret without domain knowledge. It must reduce uncertainty without instrumenting every repeated label in the interface.

## Placement and redundancy

- History help belongs on the **History page heading only**.
- Statistics help belongs on the **Statistics page heading only**.
- Do not attach an info affordance merely because `History` / `Historie` or `Statistics` / `Statistik` occurs elsewhere.
- No duplicate page-help icon belongs on drawer/navigation entries, overview labels, settings rows or other repeated occurrences.
- Generic page-level explanation should absorb generic explanatory copy that would otherwise be repeated lower on the same page.
- Keep inline text when it is metric-specific, actionable, warning-level or otherwise not covered by page help.
- Never hide warnings, failures or required actions behind an info control.

For v2.1 Statistics, the generic explanation of filled bars, dashed edge intervals, KPI inclusion, zero markers, true gaps and coverage-vs-sync semantics belongs in the single Statistics page help dialog. Metric-specific caveats for temperature, flow, battery and alarm evidence may stay inline.

## Interaction pattern

- Use a small accessible Material info affordance associated with the deliberately chosen page heading/concept.
- Tapping opens a short context-specific dialog or bottom sheet.
- First paragraph answers the immediate question in plain language.
- Optional secondary text explains technical reason/consequence.
- Copy is localized with the rest of the UI.
- Do not add info icons to self-evident or repeated navigation text merely for visual consistency.

## Canonical real timeline / raw evidence

Normal v2.1 has one canonical real timeline.

- Live real time = actual acquisition epoch.
- Archive real time = single newest fully verified Live/default anchor per physical meter + monotonic `ON_TIME`.
- `ON_TIME` defines native elapsed-time geometry; the active anchor only places that complete geometry on UTC.
- Raw meter/logger wall clock remains preserved source evidence, not a peer primary product timeline.
- Unsafe/unavailable reconstruction fails closed rather than silently promoting raw meter time.

Where raw meter/logger time is shown, explanations should make clear that the meter clock may drift and that raw time remains available for diagnosis/export/backup.

## Global date/time presentation

- When one displayed instant contains date and time, separate consistently with `date · time`.
- Interval labels follow the same convention, e.g. `11.09.2026 · 18:00–19:00`.
- Locale-specific ordering remains authoritative.
- Prefer centralized locale-aware presentation helpers across Overview, History, Statistics, Meter Details, sync/result UI and warnings.
- Raw stored timestamps are never rewritten by presentation formatting.

## Timezone presentation and selection

For ordinary local display, prefer recognizable localized zone abbreviations such as `MEZ/MESZ` or `CET/CEST`, with enough numeric-offset information retained where a DST fold is genuinely ambiguous.

Per-meter zone selection:
- populate the searchable picker from runtime-supported `ZoneId.getAvailableZoneIds()` plus `UTC`;
- preselect the persisted zone;
- typing filters the supported set;
- Save succeeds only for an exact supported ID through canonical validation/persistence;
- arbitrary unsupported free text cannot persist;
- changing the assigned zone changes only local-time interpretation and never rewrites raw meter/logger timestamps, `ON_TIME`, occurrence identity or archive payloads.

## Statistics / coverage semantics

- fully-contained bucket = filled bar and included in selected-window KPIs;
- partially overlapping real bucket = dashed outline showing the complete measured value as context only, excluded from KPIs;
- genuine zero bucket = visible baseline marker;
- missing native bucket = no fabricated bar;
- concise coverage count describes the selected analytical view and does not by itself prove protocol synchronization completeness.

Do not repeat the generic explanation under every chart when it already exists in page help.

## Statistics x-axis — implemented shared v2.1 policy

The adaptive x-axis behavior is now implemented centrally in `V2MetricChartView`; it is no longer deferred to a later app version.

### Bar charts

Current bar metrics are consumption and flow. The complete visible label set follows one whole-set policy:

1. render all labels horizontally if the complete set fits cleanly;
2. otherwise rotate the complete set together and reserve enough chart height;
3. if even rotated text would collide because each slot is too narrow, hide the complete x-axis label set;
4. never keep an arbitrary mixture by skipping isolated bar labels;
5. hiding labels changes presentation only — no bucket/value is dropped, merged or aggregated.

### Line charts

Current line metrics are temperature and battery.

- label thinning is allowed only when measured spacing keeps the remaining labels non-overlapping and point ownership understandable;
- choose the smallest fitting thinning step from actual text metrics and plot width;
- if even the sparsest useful label set collides, hide x-axis labels entirely rather than produce an unreadable text block;
- line data points and gaps remain unchanged.

The current alarm metric is a textual event timeline and therefore has no `V2MetricChartView` x-axis to apply this policy to.

### Fit calculation / future metrics

- decisions use actual available plot width, visible item count, rendered text/font metrics and minimum spacing, not only hard-coded point counts;
- rotated labels reserve extra height; hidden labels reclaim unnecessary label space;
- future Statistics chart metrics must reuse the shared renderer/policy rather than implement their own x-axis behavior;
- a future ambient-temperature or alarm/event chart, if added, must inherit the same contract.

`V2MetricChartAxisPolicyTest` protects horizontal -> rotated -> hidden bar behavior and measured thinning -> hidden line behavior.

## History `All` — Live delta baseline

In mixed `History -> All`, a Live card uses the chronologically nearest trustworthy **earlier archive cumulative observation** on the same physical meter as baseline.

1. Consider valid Hour, Day and Month archive observations together.
2. Choose the latest canonical observation that is strictly earlier than the Live read.
3. Chronology is authoritative; do not use a fixed family priority.
4. If no archive predecessor exists, fall back to immediately previous valid Live.
5. If neither exists, do not fabricate a delta.
6. Never calculate across meter replacement, incompatible identity, known conflict or unsafe chronology.

Dedicated Live remains Live-to-Live; archive cards retain same-family archive-series semantics.

## Future Home Assistant source identity presentation rule

`source_record_id` is a source identity, not a display timestamp. The current primitive is `w1:v1:<sha256>` derived from immutable meter-native evidence. Reconstructed UTC/local time must not participate in the ID because a newer verified anchor may legitimately improve the absolute time placement of an unchanged physical occurrence.

Android does not maintain an authoritative already-sent state for HA. A future integration may resend all eligible local records; HA owns idempotence on `source_record_id`.

## Guardrails

- Help describes implemented behavior; it does not paper over incorrect behavior.
- Do not duplicate generic help in page body and dialog.
- Do not put page-help icons into navigation/drawer/menu repetitions.
- Never fabricate data to make coverage look complete.
- Never present a real native coverage gap as a time-resolution issue or vice versa.
- Never calculate Live deltas across replacement/unsafe chronology.
- Never silently promote raw meter/logger wall clock to canonical real time.
- Hiding x-axis text never changes the underlying data series.
- Canonical UTC, native occurrence identity, raw evidence and protocol safety remain authoritative.

## v2.1 implementation status

Current exact candidate: `b38006f9dab270a574d7e08cb9e3785a231a2ef8`.

CI `34715215357`: **SUCCESS**.

Implemented and CI-green:
- single-active-anchor archive projection;
- mixed History-All Live archive-baseline rule;
- adaptive shared Statistics x-axis policy for consumption/flow/temperature/battery;
- centered locale-aware `date · time` presentation;
- localized timezone abbreviations with DST-fold disambiguation;
- dashed partial-edge bars and visible zero markers;
- History/Statistics help scoped only to actual page headings;
- duplicate generic Statistics explanation removed from chart body;
- searchable supported-IANA timezone picker;
- deterministic `SourceRecordId` primitive for future HA idempotence;
- six-locale UI/help copy and regression suite.

Physical acceptance still required: recheck the previously stretched Hour, check normal and very dense Statistics ranges for label behavior, complete any remaining timezone/help spot-check, then perform final protected normal Live/default NFC regression F before exact signed RC preparation.
