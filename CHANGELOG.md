# Changelog

All notable public changes to W1 NFC Reader will be documented here.

The public changelog starts with the first public release. Earlier private development iterations and protocol-research builds are intentionally not reproduced as public release history.

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
