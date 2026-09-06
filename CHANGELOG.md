# Changelog

All notable public changes to W1 NFC Reader will be documented here.

The public changelog starts with the first public release. Earlier private development iterations and protocol-research builds are intentionally not reproduced as public release history.

## 2.0.0-dev — Unreleased

Development line for the next major public version.

### Breaking changes

- History synchronization is being rebuilt around independent Hour, Day and Month family state instead of the 1.x whole-history state model.
- 1.x History completeness metadata is not trusted as evidence of a complete archive baseline and will not be promoted automatically to a v2 family-complete state.
- The v2 portable-data model may intentionally break `.qw1backup` compatibility with 1.x. Backward restore compatibility is not a release requirement for 2.0.0 and any unsupported upgrade path will be called out explicitly in the final release notes.
- Archive and CSV schemas may change to preserve family identity, raw meter-time evidence, ON_TIME/Type-F provenance and derived real-time information.

### In progress

- Started the `2.0.0-dev` development identity (`versionCode 39`).
- Introduced the v2 product-domain contract for independent per-meter/per-family synchronization state.
- Baseline completeness is modeled separately from the latest synchronization attempt so a later partial update cannot erase an already completed baseline.
- Added family policies for the validated Hour, Day, Month and optional Year selectors, native backward progression and raw-meter boundary calculation.
- Added a family-neutral archive traversal state machine with exact FCB1/FCB0 alternation and no fixed record-count completion cap.
- Successful completion is restricted to semantic archive end conditions (`PROTOCOL_TERMINAL`, confirmed `RING_WRAP_DETECTED`, or later incremental `KNOWN_RECORD_REACHED`) plus verified final restore.
- `HEAD_ADVANCED_DURING_TRAVERSAL` is explicitly separated from confirmed ring wrap; ring completion requires return to the exact session head plus one continuation proof.
- Incremental traversal requires a conservative multi-record securely-known overlap; one timestamp match alone is not a successful stop.
- Added a read-only Type-F/ON_TIME evidence extractor beside the protected `MbusParser`, preserving raw Type-F bytes plus SU/IV flags without changing established value decoding.
- Documented the target v2 History architecture and Settings-based Hour/Day/Month/All synchronization model.

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
