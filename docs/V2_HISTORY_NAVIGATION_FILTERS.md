# W1 NFC Reader 2.0 — History and Statistics navigation product decision

Status: product/UX decision for the `2.0.0-dev` development line. This document records agreed behavior only; it does not authorize protocol changes and does not itself implement the UI.

## Goal

Large local History datasets must remain useful without forcing users to scroll through thousands of rows. Navigation and filtering should follow how users think about meter history: by data granularity, calendar period, alarms/events and meter lifecycle — not by page number.

History and Statistics use the same calendar/navigation language so users do not have to learn two different interaction models.

The History screen must retain the virtualized list introduced for large-dataset performance and should increasingly query only the selected subset from SQLite rather than materializing the complete archive and filtering everything in memory.

## Primary History controls

The primary navigation model has two independent axes:

1. **Granularity**
   - All
   - Live
   - Hour
   - Day
   - Month

2. **Time period**
   - Live / Hour -> day navigation
   - Day -> month navigation
   - Month -> year navigation
   - All -> month as default period
   - centered period label is directly selectable for jumping to a specific day/month/year
   - previous/next controls navigate adjacent periods
   - `All periods` resets the time restriction

Page numbers are not a primary navigation concept.

## Localized History date/time presentation

Stored timestamps remain canonical and are not rewritten for presentation. The UI must format them according to the currently selected app locale.

Time provenance must remain explicit:

- Live observations use the Android acquisition time as the primary History timestamp; any meter-reported time remains a separate meter-time field.
- Archive Hour/Day/Month observations use the logger/archive timestamp reported by the meter as their primary semantic time.
- Archive timestamps must not be silently shifted using the phone clock, timezone or daylight-saving assumptions. Presentation localization changes the display only, not the stored value or semantic meaning.

The visible timestamp should be granularity-aware instead of showing raw `yyyy-MM-dd HH:mm` everywhere. Examples for German presentation:

- Hour: `06.09.2026 · 18:00 · Stunde`
- Day: `06.09.2026 · Tag`
- Month: `September 2026 · Monat`

Other locales use their normal date/time conventions.

The `since previous` label must also be granularity-aware:

- Hour: show the predecessor time; include the date when crossing a day boundary.
- Day: show the localized predecessor date.
- Month: show the localized predecessor month/year.
- Live: show a localized date/time.

Do not truncate every predecessor to an ISO date.

## Alarm quick filter

History must provide a user-facing **Only alarms** quick filter.

This is an orthogonal filter dimension, not another granularity. It therefore combines with the main controls, for example:

- Hour + a selected day + Only alarms
- Day + a selected month + Only alarms
- Month + a selected year + Only alarms
- All + a selected month + Only alarms

The first implementation should show observations for which the persisted/decoded product data contains relevant alarm evidence. Exact field semantics must come from the existing decoded product model; this decision does not authorize inventing new protocol meanings.

## Alarm states versus alarm events

A plain `Only alarms` filter and an alarm-event view are deliberately different concepts.

A persistent alarm can otherwise generate many repeated Hour/Day observations with the same active state. Product analytics should therefore derive meaningful transitions only where stored evidence safely supports them, such as:

- alarm became active;
- alarm cleared;
- alarm state changed.

Such transitions should be presented as user-relevant events rather than pretending every repeated alarm-bearing archive row is a separate incident.

Alarm transition derivation must compare observations within the appropriate meter/semantic series and must not cross a meter replacement.

## Meter lifecycle filtering

History navigation must remain compatible with meter replacement.

An extended filter should allow users to distinguish at least:

- current meter;
- previous meter(s);
- all meters/history.

The normal UI does not need to expose raw meter IDs prominently when a user-friendly lifecycle label is sufficient. Cumulative consumption must never be subtracted across different meter IDs.

## Statistics navigator

Statistics uses the same calendar-period navigation concept as History.

The selected period determines the natural detail resolution:

- selected **day** -> Hour observations / hourly statistics;
- selected **month** -> Day observations / daily statistics;
- selected **year** -> Month observations / monthly statistics.

The centered period label is directly selectable, with previous/next controls for adjacent periods.

Period selection and metric selection are independent dimensions. Changing from Consumption to Temperature, for example, must keep the selected day/month/year unless the selected metric is unavailable for that resolution.

The first implementation should choose the natural resolution automatically. Manual resolution overrides may be evaluated later if they add clear value without making the UI confusing.

## Statistics surface and core metrics

The Statistics screen should stay focused:

- compact summary/KPI cards for the selected period;
- one main chart area;
- a clear metric selector that changes the chart content;
- visible data coverage / unavailable-state information where relevant.

The v2 core metric surface is:

1. **Consumption**
2. **Water temperature**
3. **Flow**
4. **Battery**
5. **Alarms** where safely derivable from persisted product data

A secondary `More` surface may expose ambient/external temperature, reverse volume/flow and technical measurements.

Only measurements actually available and reliably decoded for the selected archive family may be presented. Missing values remain unavailable rather than being inferred.

## Chart selection principles

Each metric uses a chart type that matches the semantics of the stored data. The UI must not use a visually attractive chart when it would imply information the meter did not actually measure.

General rules:

- one primary chart at a time;
- no dual-axis chart in the first v2 slice;
- no interpolation across meter replacement;
- missing/invalid observations create gaps rather than fabricated values;
- selected day/month/year determines the normal bucket resolution;
- KPI cards summarize the same filtered dataset shown by the chart;
- coverage must be visible when the selected period is incomplete.

## Consumption statistics and chart

Consumption is the primary Statistics use case.

Use a **bar chart** for bucket consumption because each bar represents a discrete consumption amount for one Hour/Day/Month bucket:

- selected day -> consumption per Hour;
- selected month -> consumption per Day;
- selected year -> consumption per Month.

Useful summary values:

- total consumption for the selected period;
- average consumption per displayed bucket;
- highest-consumption bucket and its time/period;
- lowest/zero-consumption bucket where meaningful;
- data coverage.

Consumption deltas retain established same-meter, same-granularity semantics and never cross a meter replacement.

A later `Typical day` view may aggregate sufficient Hour data by hour-of-day across a selected multi-day period. It must be labeled clearly as an aggregate profile, not a specific historic day.

## Water-temperature statistics and chart

Water temperature is a normal v2 Statistics metric, not merely an advanced technical value.

Use a **line chart with observed points** for the water-temperature trend over the selected period. It is particularly useful for understanding seasonal/source-water changes and can provide useful context for households that heat domestic hot water with a heat pump.

The meter alone does **not** provide enough information to calculate heat-pump COP, electrical energy use or heating-system efficiency. The app must not derive or claim those values from water temperature alone.

Useful summary values where supported:

- first/earliest valid temperature in the selected period;
- latest valid temperature;
- minimum temperature;
- maximum temperature;
- temperature span in kelvin/degrees;
- timestamps of stored extrema where the archive explicitly provides them;
- data coverage.

The chart uses only actual valid persisted temperature observations. It does not interpolate missing values.

## Ambient/external-temperature semantics

`externalTemperature` is available in the normalized data model but must not automatically be labeled as outdoor weather temperature.

Until its exact product semantics are explicitly validated, use a conservative label such as **meter ambient temperature** / locale equivalent and keep it in the secondary `More` surface.

Where validated and available, it may use a line chart and the same period navigator.

## Flow statistics and chart

Flow is a normal v2 Statistics metric where the selected archive family reliably provides it.

A single instantaneous flow observation at a bucket boundary can be unrepresentative, often including zero when no water was flowing at that exact moment. Therefore the primary v2 flow visualization should prefer the archive's **maximum flow per bucket** where available.

Use a **bar chart of peak flow per bucket** for the natural period resolution:

- selected day -> maximum flow per Hour;
- selected month -> maximum flow per Day;
- selected year -> maximum flow per Month.

Useful summary values:

- highest observed/recorded maximum flow in the selected period;
- its timestamp/period;
- lowest valid recorded peak where meaningful;
- latest/current flow observation when it adds context;
- data coverage.

A raw/instantaneous flow **line chart** may be exposed later only when the sampling semantics make such a trend meaningful.

Persistent low-flow/leak-style anomaly detection is a later Insights feature. It requires an explicit validated analytical rule and must not be created from an arbitrary threshold.

## Battery statistics and chart

Provide a dedicated Battery metric where persisted battery observations are available.

Use a **line chart with observed points**. Battery percentages are discrete reported observations; the chart must not imply greater precision than the meter provides.

Rules:

- use the selected calendar period and matching stored resolution;
- keep observations separated by meter identity across replacements;
- do not interpolate through a meter replacement;
- do not claim a precise remaining lifetime unless a separately validated model exists.

Useful summary values:

- battery at start of selected period;
- battery at end/latest observation;
- change in percentage points;
- observed time span;
- data coverage.

A battery lifetime/remaining-years prediction is explicitly not part of the initial Statistics decision.

## Alarm statistics and visualization

Alarms are a core metric only where persisted alarm semantics are reliable enough to support the selected view.

Do **not** use a conventional numeric line chart for alarm state.

Preferred visualization:

- a time-aligned event/state timeline for meaningful alarm transitions;
- alternatively discrete event markers grouped by time bucket when a timeline component is not yet available.

Useful summaries where safely derivable:

- alarm categories observed;
- first/last alarm transition in the selected period;
- number of derived incidents/transitions;
- duration only when active/clear transition evidence safely supports it.

Repeated archive rows containing the same active alarm must not automatically be counted as separate incidents.

If only repeated alarm-state observations are available and incident derivation is unsafe, show the observed states without inventing an incident count.

## Data-quality rules for Statistics

Statistics needs a shared validity layer before values enter charts or KPI aggregation.

Rules:

- known device/decoder sentinel or placeholder values are treated as unavailable, not as measurements;
- malformed numeric values are excluded;
- missing values remain gaps;
- validity rules should be based on validated decoder/device semantics, not arbitrary clipping merely to make charts look plausible;
- invalid values must not influence minimum, maximum, average, trend lines or chart scaling;
- the UI should distinguish `no valid measurement` from a genuine numeric zero where semantics allow that distinction.

This rule applies to temperature, flow, battery and future additional measurements.

## Reverse-flow / reverse-volume statistics

Where reliably decoded, reverse-flow/reverse-volume data belongs in the secondary `More` surface.

If non-trivial reverse-flow evidence is detected, the product may promote it more visibly because it can be user-relevant. Any promoted message must still reflect validated semantics rather than a generic alarm assumption.

Where the value represents a cumulative quantity, period deltas must use same-meter, same-granularity predecessor logic before visualization.

## Positive/tariff volume and technical values

Positive volume and tariff volume may be exposed in `More` where their semantics are useful and reliably decoded.

ON_TIME and operating time remain advanced/technical statistics or plausibility/context values rather than primary consumer-facing metrics.

## Period comparison

Useful later comparisons include:

- selected month vs previous month;
- selected year vs previous year;
- selected range vs preceding comparable range;
- weekday vs weekend where enough data exists.

Comparisons must use comparable data coverage and must not silently compare incomplete against complete periods.

## Data coverage

Statistics should make observed coverage visible, for example conceptually:

- 24/24 Hour buckets available for a day;
- 30/30 Day buckets available for a month;
- 12/12 Month buckets available for a year.

Coverage information reflects actually stored valid observations. It must not promote an archive family to protocol-level COMPLETE merely because an expected-looking number of rows exists.

Metric-specific coverage may differ: a period can have complete consumption coverage while only some buckets contain a valid temperature or battery value.

## Additional History filter ideas retained for later evaluation

### Only changes

Potentially useful for dense Hour history: show only observations where something relevant changed compared with the previous observation from the same semantic series.

Candidate changes include alarm state or other safely decoded product state. The exact definition must be explicit before implementation so normal cumulative consumption progression does not accidentally classify every row as a meaningful state change.

### Alarm type

If the existing product model exposes multiple alarm categories reliably, a later advanced filter may allow selection by alarm type. This belongs in an expanded filter surface rather than the primary navigation row.

### Data-quality / technical filters

Advanced/technical filtering may later expose:

- recorded conflicts/revisions;
- repeated identical confirmations;
- incomplete/partial synchronization evidence;
- selected source/granularity details.

These are useful for diagnostics but should not overload the normal History experience.

### Consumption-oriented filters

Possible later analysis filters include:

- consumption greater than a user-selected threshold;
- zero/no consumption;
- unusually high consumption.

Especially `unusually high` requires an explicit analytical definition and fits Statistics/Insights better than basic History navigation.

## UX priority

The normal History screen stays simple:

- granularity chips first;
- contextual calendar-period navigator beneath them;
- localized/granularity-aware timestamp presentation;
- `Only alarms` immediately reachable;
- `All periods` reset;
- advanced filters behind a secondary surface.

The normal Statistics screen stays focused:

- contextual calendar-period navigator;
- automatic natural resolution for day/month/year;
- compact summary cards;
- one main chart;
- metric selector: Consumption / Temperature / Flow / Battery / Alarms / More;
- metric-specific unavailable/coverage indication.

History and Statistics prefer useful filter combinations over mutually exclusive modes.

## Implementation direction

When implementation starts, prefer:

1. preserve the virtualized `ListView` behavior that fixed large-history ANRs;
2. implement a reusable calendar-period navigation model shared by History and Statistics;
3. add localized, granularity-aware History timestamp and predecessor formatting;
4. add contextual History period navigation;
5. add the orthogonal `Only alarms` quick filter;
6. move History archive loading toward SQLite range/family/alarm queries for the selected view instead of loading all archive rows;
7. extend Statistics to use the same period model and matching Hour/Day/Month resolution;
8. implement a shared measurement-validity layer before chart/KPI aggregation;
9. implement core metric charts in this order: Consumption bars, Temperature line, Flow peak bars, Battery line, then Alarm timeline/events where safely derivable;
10. preserve correct same-meter, same-granularity delta semantics from the mixed-granularity History fix;
11. add targeted tests for filter combinations, locale formatting, period boundaries, meter replacement, sentinel/missing-value handling, incomplete coverage and statistic aggregation;
12. evaluate typical-day profiles, period comparisons, ambient temperature and other advanced metrics after the shared navigator foundation is stable.

## Development batching principle

The v2 development line should avoid unnecessary APK/version churn.

Prefer grouping directly related work into coherent development slices when the changes share the same data/UI foundation and can be regression-tested together. History navigation, Statistics navigation, SQLite range-query support, localized time presentation, metric validity rules and related cleanup should therefore be implemented back-to-back and validated in one Dev checkpoint where practical.

Do not create a new physical meter test requirement for every UI or database-only change. Reuse synthetic/unit/Robolectric regression coverage and the portable Dev backup dataset whenever protocol behavior is unchanged.

Physical meter validation remains mandatory when a change affects NFC transport, archive acquisition/traversal, terminal handling, restore/default application behavior, Incremental/Resume or other protocol/device semantics.

## Out of scope for this decision

This product decision does not change:

- normal NFC contact = Live-only;
- archive acquisition or traversal behavior;
- family completeness semantics;
- immediate persistence;
- Incremental/Resume behavior;
- `QalcosonicReader.java` or `MbusParser.java`;
- protocol field meanings.

True Incremental/Resume remains a separate later product block after the History/Statistics navigator foundation is stable.
