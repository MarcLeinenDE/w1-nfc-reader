# W1 NFC Reader 2.0 — archive period semantics

Status: canonical product/presentation rule for the v2 development line.

This document defines how already-decoded Hour, Day and Month archive records are assigned to calendar periods in History and Statistics. It does not change NFC transport, archive traversal, persistence keys or decoder field meanings.

## Core rule

For product presentation, the W1 archive logger timestamp is treated as the **end boundary of the completed archive period**.

The stored logger timestamp remains unchanged and continues to be the canonical persistence identity. Only calendar selection, aggregation and user-facing presentation apply the period-boundary interpretation.

Examples:

- Hour boundary `2026-09-06 18:00` -> completed Hour period `17:00–18:00`.
- Day boundary `2025-09-06 00:00` -> completed Day period `2025-09-05`.
- Month boundary `2025-10-01 00:00` -> completed Month period `September 2025`.

A UI must not label the last example as `October 2025` merely because the stored logger boundary is 1 October.

## Calendar range queries

For a selected archive calendar window `[start, end)`, archive rows whose periods lie in that window are selected by their end boundaries:

`logger_timestamp > start AND logger_timestamp <= end`

The exact `start` boundary may additionally be loaded as context for the first archive consumption delta. It is not itself a displayed period in the selected window.

Live reads are different. Their primary timestamp is Android acquisition time and remains selected with normal device-time `[start, end)` semantics.

For a freely selected custom date/time range, the app may evaluate only archive buckets that lie completely inside the requested range. It must not interpolate a fractional Hour/Day/Month bucket merely to make the selected boundaries appear exact. If the available granularity cannot represent one or both edges exactly, the UI must report that limitation and the available coverage.

## Consumption and cumulative registers

Total volume and other cumulative registers are boundary readings. A period amount is derived from adjacent observations of the same physical meter and the same archive granularity.

For example:

- reading at `2025-09-01 00:00` = period start boundary;
- reading at `2025-10-01 00:00` = period end boundary;
- their same-meter Month delta = consumption for **September 2025**.

Never subtract across meter replacement, archive granularity or a missing intermediate bucket when presenting a single Hour/Day/Month amount.

Positive volume, reverse volume and tariff volume must follow the same rule if a future UI presents them as period amounts rather than raw register readings.

## Live consumption reference

A Live reading is a point-in-time observation, not a completed archive period. Its consumption delta is therefore a separate Live-only series.

For a Live observation, compare the cumulative total only with the **newest strictly earlier Live observation from the same physical meter**.

Hour, Day and Month archive rows must never become hidden reference context for a Live card, even when an archive observation is chronologically newer than the previous Live read. The semantic meaning of a Live card must also remain identical whether the user is looking at `All` History or the `Live` filter.

If no previous Live observation exists for the same physical meter, the app does not invent a Live consumption delta and does not silently fall back to archive data.

Example:

- Live at `06.09.2026 19:23` = `205.050 m³`;
- Hour archive boundary at `07.09.2026 20:00` may contain another cumulative total;
- Live at `07.09.2026 21:15` = `205.391 m³`;
- the later Live card still compares to the previous **Live** observation and may display `0.341 m³ since 06.09.2026 19:23`.

Live predecessor selection must never cross a meter replacement, must reject negative/reset-like cumulative deltas, and must not use an observation at the exact same timeline timestamp as a strictly earlier reference.

This Live-specific rule does **not** relax the archive-period rule: Hour/Day/Month consumption remains same-meter + same-granularity + adjacent-period only.

## Historical alarm flags

Historical alarm/error flags attached to an archive record are period evidence.

If a Month record ending at `2025-10-01 00:00` contains an alarm flag, the product may state that the alarm/status was **registered within September 2025**.

The record alone does not establish:

- the exact time within that period when the condition occurred;
- how long it lasted;
- whether repeated alarm-bearing archive periods are one continuous incident or multiple incidents;
- an exact activation or clear timestamp.

Therefore History and Statistics must present alarm-bearing archive periods without inventing exact incidents or transition times. Finer archive families may narrow the known period, but they still do not create an exact event timestamp unless such a timestamp is explicitly provided by validated meter data.

## Maximum/minimum values and explicit extremum timestamps

Some archive measurements include both an extremum value and a dedicated extremum timestamp, such as maximum/minimum flow and maximum/minimum temperature.

The archive record itself belongs to the completed Hour/Day/Month period, but when an explicit validated extremum timestamp is available it is authoritative for the displayed time of that extremum. Do not replace it with the archive boundary or the period start.

## Temperature, battery and instantaneous observations

Values such as recorded water temperature or battery percentage are observations associated with the completed archive record. They must not be relabeled as period averages unless the protocol explicitly says they are averages.

Charts may place these observations in the corresponding completed archive period, but explanatory text must avoid claiming unsupported averaging, interpolation or lifetime prediction.

Known sentinel/unavailable values remain excluded from chart aggregation.

## Timezone handling

Archive logger timestamps remain meter-local/floating timestamps unless separately validated otherwise. The phone timezone or daylight-saving rules must not silently shift stored archive boundaries.

Localization affects only presentation format.

## Backup and persistence

`.qw1backup` remains a lossless machine-readable representation of stored data and must preserve the original logger timestamps. Restoring a backup must not rewrite archive timestamps to period starts.

Period semantics are a presentation/query/analytics rule above persistence.

## Scope guard

This rule does not authorize changes to:

- normal NFC contact behavior;
- archive selectors or traversal;
- terminal detection;
- immediate persistence;
- family COMPLETE semantics;
- `QalcosonicReader.java`;
- `MbusParser.java`.
