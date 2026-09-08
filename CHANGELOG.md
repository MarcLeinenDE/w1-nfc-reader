# Changelog

All notable public changes to W1 NFC Reader will be documented here.

The public changelog starts with the first public release. Earlier private development iterations and protocol-research builds are intentionally not reproduced as public release history.

## 2.0.0-dev — Unreleased

Development line for the next major public version.

### Breaking changes

- History synchronization is rebuilt around independent Hour, Day and Month family state instead of the 1.x whole-history state model.
- 1.x History completeness metadata is not trusted as evidence of a complete archive baseline and will not be promoted automatically to a v2 family-complete state.
- The v2 portable-data model may intentionally break `.qw1backup` compatibility with 1.x. Backward restore compatibility is not a release requirement for 2.0.0 and any unsupported upgrade path will be called out explicitly in the final release notes.
- Archive and CSV schemas may change to preserve family identity, raw meter-time evidence, ON_TIME/Type-F provenance and derived real-time information.

### Added / changed in the current development line

- Advanced the side-by-side development identity to `2.0.0-dev.4` (`versionCode 42`) for final History/release-hardening testing.
- Introduced independent per-meter/per-family History state for Hour, Day and Month, with baseline completeness separate from the latest synchronization attempt.
- Added validated initial full Hour, Day and Month family synchronization behind explicit Settings actions while keeping normal NFC contact Live-only.
- Added synchronous immediate persistence for every accepted archive observation so interruption does not roll back already validated records.
- Added conservative per-family incremental synchronization after a COMPLETE baseline. Incremental overlap requires at least two consecutive securely known observations and never relies on timestamp-only matching.
- Preserved a previously COMPLETE family baseline when a later incremental or Full Re-Sync attempt is partial or fails.
- Added default-application normalization to protected normal Live reads so a reconnect from an unknown/still-selected archive state cannot be silently persisted as Live data.
- Live consumption deltas now reference only a strictly earlier Live observation on the same physical meter. Archive deltas remain same-meter and same-granularity.
- Added combined `Sync All` orchestration for Hour -> reconnect -> Day -> reconnect -> Month. Each family independently selects initial-full or incremental mode from its own authoritative baseline state and still enters the complete per-family safety shell.
- `Sync All` stops before a following family when the preceding family leaves default application state unverified. A partial family with a verified final restore may allow the next independently protected family session to start while the combined result remains non-complete.
- Physically validated combined incremental `Sync All` across all three COMPLETE families, including secure known overlap, final restore verification and a normal Live read afterward.
- Added a separate advanced `Full Re-Sync` action after all Hour/Day/Month baselines are COMPLETE. It rereads each exposed family from the beginning using the same validated family safety shell, preserves existing records, confirms matching periods and retains differing observations as revision/conflict evidence instead of silently replacing them.
- Full Re-Sync remains separate from normal routine updates: normal Sync All continues to use incremental mode for COMPLETE families, while Full Re-Sync deliberately uses `FULL_RESYNC` for Hour, Day and Month.
- Added bounded History and Statistics browsing over Hour, Day, Month and Live observations with completed-period semantics for archive logger boundaries.
- Added custom History/Statistics start and end date/time ranges without rewriting stored raw logger timestamps.
- Custom-range History may show overlapping boundary periods for context, while Statistics consumes only fully contained archive periods and never estimates partial Hour/Day/Month consumption.
- Added automatic custom-range Statistics resolution with explicit Hour/Day/Month override and availability-aware fallback.
- Added Statistics for consumption, water temperature, flow/max-flow evidence, battery and historical alarm-period evidence without interpolating missing data or inventing incident times/durations.
- Added v2 backup/export state for independent family synchronization and CSV schema 3 with explicit completed-period and consumption-reference semantics.
- Consolidated History filters and Statistics selectors into a more compact Material product UI.
- Moved the Live share control beside the existing meter/read-time metadata while keeping it strictly bound to the last successful Live read.
- Added a two-step Back-to-exit guard on Overview while preserving drawer Back handling priority.
- Restored normal Android Back-stack navigation for History, Statistics, Settings and other secondary screens; Overview remains the single exit root.
- Replaced fragile text-glyph period arrows with explicit chevron icons and replaced the overflowing History Filter label with a compact filter icon.
- Preserved History/Statistics selection state, including exact custom date/time ranges, when the device rotates between portrait and landscape.
- Removed the temporary physical-validation debug card and its formatter/helper from History Sync.
- Refreshed Diagnostics, Settings and About wording for the validated Live/Hour/Day/Month v2 scope and current public repository/privacy state, and removed a redundant generic privacy card from Diagnostics.
- Added an optional developer-support entry in About that opens `https://www.paypal.me/ccaa/` through the external browser only. The app does not process payments, unlock features or add an Internet permission.
- Added a restrained seasonal Christmas support prompt for 1–26 December. It becomes eligible only after a successful normal Live read during that same Christmas window, appears only on a later normal launcher cold start, and is capped at one display per season regardless of dismissal or support action.
- Added English, German, French, Polish, Dutch and Lithuanian product strings for the new v2 History, Statistics, custom-range, synchronization, Full Re-Sync, hardening and optional-support states.

### Protocol/safety invariants retained

- No fixed archive record count is a completion rule.
- Selected traversal preserves the validated FCB1/FCB0 progression and does not blindly retry an ambiguous selected request.
- Successful full completion remains limited to supported terminal/ring evidence; `KNOWN_RECORD_REACHED` is additionally valid only for incremental mode.
- Every family synchronization starts from unknown application state, performs Reset Default + verified Live preflight, uses the family-specific raw-meter boundary guard, persists accepted records immediately, then restores and verifies default Live state before COMPLETE.
- Full Re-Sync reuses the same per-family safety shell and does not introduce a new meter command or weaker completion rule.
- Logical NFC-V reconnect does not itself prove default application state.
- No intentional persistent meter/radio/calibration/firmware writes are introduced.

### Remaining before stable 2.0.0

- Final upgrade/portable-data verification, broader text/redundancy and accessibility review, breaking-change guidance and release-candidate validation remain required.
- Produce a v2.0.0 release candidate through CI, then perform one limited final real-device smoke focused on normal Live plus a representative History update rather than rereading the entire archive.

See `docs/V2_BREAKING_CHANGES.md` for the current major-version compatibility policy and `docs/V2_HISTORY_SYNC_ARCHITECTURE.md` for the History integration contract.

## 1.0.0

First public release.

### Added

- Android NFC-V live readout for compatible Axioma Qalcosonic W1 meters.
- Explicit Month-history synchronization with meter verification before archive traversal.
- Local history and consumption statistics.
- Meter lifecycle/replacement handling that keeps cumulative readings from different meter IDs separate.
- `.qw1backup` backup and transactional restore with integrity validation.
- Human-readable CSV export.
- Android share-sheet support for the last successful live read.
- Material-style product UI with system/light/dark appearance.
- English, German, French, Polish, Dutch and Lithuanian translations.
- Privacy-focused diagnostics, source/license information and local-data controls.

### Protocol scope

- Normal NFC contact performs live/default read only.
- Month is the only production-released archive family.
- Monthly traversal alternates validated FCB1/FCB0 requests and stops from meter/protocol evidence rather than a hardcoded period count.
- The default application is restored and structurally verified after Monthly traversal.
- Day, Hour and Year/Billing archive acquisition are not exposed as production features.

### Privacy and safety

- No Internet permission.
- Android platform backup disabled.
- No intentional persistent meter/radio/calibration/firmware writes.
- Normal history does not persist NFC UIDs, raw NFC traffic, raw M-Bus frames or development capture traces.
