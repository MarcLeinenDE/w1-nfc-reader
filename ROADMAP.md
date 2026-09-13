# W1 NFC Reader — Roadmap

Status: product direction as of 2026-09-13. This roadmap records intended scope and release boundaries; it is not a promise of dates.

## v2.1.0 — Canonical real-time timeline and release hardening

Goal: finish the current feature release without reopening the validated protocol path.

Planned/implemented scope:

- one canonical real-time product timeline;
- single newest fully verified Live/default anchor per physical meter;
- archive reconstruction through monotonic `ON_TIME` while preserving raw meter/logger time as evidence;
- per-meter IANA timezone model and constrained searchable timezone picker;
- History/Statistics coverage semantics and shared adaptive chart x-axis behavior;
- deterministic `SourceRecordId` primitive for future integrations;
- UI/i18n/accessibility polish and release documentation.

Release gate:

- final protected normal Live/default NFC regression read;
- version metadata, changelog and release notes;
- exact signed v2.1.0 RC build;
- physical acceptance of that exact RC before merge/tag/release.

No Home Assistant networking is part of v2.1.

## v2.2.0 — Code cleanup and maintenance baseline

Goal: remove transitional implementation debt after v2.1 is safely released, without adding major product features.

Primary cleanup scope:

- remove the retired `AppTimeBasis.METER` runtime path and obsolete peer-time-mode branches;
- remove obsolete time-basis resources and tests that exist only for the retired runtime mode;
- retain only the minimum compatibility needed to normalize old development backups/state into the canonical timeline;
- audit duplicate/legacy helpers and dead code introduced during the v2.0/v2.1 transition;
- review old tests and keep regression coverage that still protects real bugs, protocol safety, backup compatibility, DST behavior and archive traversal;
- align documentation with the post-cleanup architecture;
- run the complete automated suite and a short real-device smoke test;
- perform a final repository hygiene pass for stale files/branches/TODOs.

Target state after v2.2:

- current feature set considered feature-complete for the local NFC product;
- repository enters maintenance mode unless a real bug, Android/platform change or deliberately planned new feature requires work;
- protocol/NFC/archive safety invariants remain unchanged.

## v3.0.0 — Home Assistant integration

Goal: add an optional, local-network Home Assistant path while keeping the meter protocol itself local and unchanged.

### Architecture

Preferred direction:

`Qalcosonic W1 -> NFC -> Android app -> local connector protocol -> Home Assistant custom integration -> sensors / long-term statistics`

The Home Assistant side owns the connector and central persistence/deduplication. The Android app remains the NFC acquisition client.

### Pairing by QR code

The preferred setup flow is local and cloud-free:

1. User installs/configures the W1 NFC Reader Home Assistant custom integration.
2. Home Assistant creates a short-lived one-time pairing session and displays a QR code.
3. In W1 NFC Reader, the user chooses Home Assistant pairing and scans that QR code.
4. The QR payload identifies the local connector endpoint and carries only short-lived pairing material.
5. The app redeems the one-time pairing material over the local network.
6. Home Assistant issues a dedicated, restricted app/device credential for subsequent transfers.

The QR code must not contain Wi-Fi credentials and should not require a Home Assistant administrator token or normal long-lived user token to be stored in the Android app.

The phone must be able to reach the Home Assistant connector over the same LAN/routable local network. Automatic discovery may be added later as convenience, but QR pairing should not depend on mDNS or broadcast discovery and should therefore also work in networks where discovery is unreliable.

### Transfer and deduplication contract

Android does **not** keep an authoritative "already sent" ledger.

On a transfer the app may resend every locally available eligible record. Home Assistant performs idempotence using the deterministic `source_record_id` already defined in v2.1:

- unknown `source_record_id` -> insert;
- known ID with identical payload -> no-op/confirmation;
- known ID with improved derived metadata, for example a better reconstructed UTC placement -> update the existing record;
- never create a duplicate merely because the same physical archive record came from another phone or a fresh installation.

`source_record_id` remains derived from reproducible meter-native identity evidence and must stay independent of phone/install identity and reconstructed UTC.

This makes restore-free phone replacement safe: a new phone can be paired again, reread the retained meter archive and resend everything; Home Assistant recognizes already-known physical occurrences.

### Home Assistant data model

Expected split:

- current/latest meter state as normal Home Assistant entities/sensors where appropriate;
- historical Hour/Day/Month archive data mapped to Home Assistant long-term statistics rather than manufacturing thousands of permanent entities;
- raw meter/logger time, `ON_TIME`, archive family and occurrence identity may be sent as diagnostic/source metadata where useful;
- canonical UTC remains the integration-facing time axis for statistics and automations.

Exact Home Assistant API/storage details must be validated against the then-current HA integration interfaces before implementation.

### Multiple phones

Multiple independently paired phones should be supported. Each may resend the same meter archive. Home Assistant still deduplicates centrally using `source_record_id`.

### Security / privacy boundary

- local-first; no cloud service required;
- explicit user pairing only;
- short-lived one-time QR pairing secret;
- least-privilege persistent credential after pairing;
- no Wi-Fi password in the QR payload;
- no meter writes or new NFC commands are implied by Home Assistant integration;
- private meter identifiers/consumption data are transferred only to the user-configured Home Assistant endpoint.

### Android platform change

v3.0 is the intentional network-capability boundary. The Android app will require network access (including the Android `INTERNET` permission) only when this integration is implemented. v2.x remains a local NFC application without a Home Assistant network transport.

## Beyond v3.0

No additional major feature line is currently committed. After v2.2 the app is intended to remain stable/maintained; v3.0 is the deliberately separated future integration project. Further work should be driven by concrete user needs, compatibility changes, security maintenance or evidence-backed protocol improvements rather than feature growth for its own sake.
