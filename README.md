# W1 NFC Reader

W1 NFC Reader is an independent Android app for reading compatible **Axioma Qalcosonic W1** water meters locally over **NFC-V / ISO 15693**.

The app is designed around a conservative, read-focused protocol model: a normal NFC contact performs a fast Live read, while historical meter data is retrieved only after an explicit user action.

> This project is independent and is not affiliated with, endorsed by, or supported by Axioma Metering, STMicroelectronics, Google, or the Android project. Product and company names are used descriptively only.

## Features

- Live meter readout over Android NFC-V;
- explicit **Hour, Day and Month history synchronization** in the v2 development line;
- full baseline acquisition followed by conservative per-family incremental updates;
- local History with granularity filters, calendar navigation and custom date/time ranges;
- local multi-metric Statistics for consumption and available meter/archive measurements;
- transparent coverage/precision reporting when a requested range cannot be represented exactly by the available archive granularity;
- meter-replacement handling without subtracting cumulative values across different meter IDs;
- local `.qw1backup` backup/restore with integrity validation;
- human-readable CSV export;
- Android share-sheet integration for the last successful Live read;
- light, dark and system appearance;
- English, German, French, Polish, Dutch and Lithuanian UI resources;
- RTL-ready Android layout handling;
- no account, cloud service or network connection required.

## Supported protocol scope

The current v2 development line keeps Live acquisition and History synchronization deliberately separate.

### Live read

A normal NFC contact performs the validated Live/default read only. The app explicitly restores the meter's default application before accepting a normal Live observation, and a failed later contact does not erase the last successful Live state.

For History analytics, a Live consumption delta compares only with the previous strictly earlier Live observation from the same physical meter. Hidden Hour/Day/Month observations are not used as Live predecessors.

### Hour, Day and Month history

Historical data is synchronized only after the user explicitly selects History synchronization and then presents the intended meter again.

The physically validated v2 product families are **Hour**, **Day** and **Month**. Each family is synchronized independently and keeps its own baseline state.

An initial family baseline traverses until the meter/protocol state machine reaches a valid semantic terminal condition; it never relies on a fixed record count. Every accepted archive record is persisted immediately. The app then restores the default application and verifies a Live/default response before the attempt can be considered complete.

After a family has an authoritative COMPLETE baseline, normal updates use a conservative **incremental** path. Incremental traversal may stop at securely known overlap; it does not blindly repeat an ambiguous selected request.

**Update all history** chooses the correct mode independently for Hour, Day and Month: incomplete families use the full baseline path, while COMPLETE families use incremental synchronization.

Year/Billing is **not exposed as a normal v2 History synchronization control** unless separately validated and intentionally released.

## Safety model

The app is read-focused. It does not intentionally write persistent meter configuration, radio configuration, calibration data, metering parameters or firmware.

The protocol implementation uses the volatile ST25 mailbox transport needed to exchange M-Bus read/application-selection messages with the meter. Every production History family runs inside the same conservative safety shell: verified default preflight, family selection/traversal, immediate accepted-record persistence, final default restore and verified Live/default read.

Protocol changes should be evidence-backed and regression-tested. See [docs/PROTOCOL_SAFETY.md](docs/PROTOCOL_SAFETY.md).

## History ranges and statistics

History and Statistics support both quick calendar navigation and freely selected **date/time ranges**.

A custom range can start and end at arbitrary times, for example `03 Sep 15:00` to `04 Sep 10:00`. The app does not invent precision: if the selected boundaries cannot be represented exactly by the available Hour/Day/Month data, it reports the available resolution/coverage and evaluates only defensible complete intervals rather than interpolating unknown consumption.

## Privacy

W1 NFC Reader works locally on the Android device and the app manifest does **not** request Internet permission.

The app stores decoded meter/history data locally. It does not intentionally persist NFC UIDs, raw NFC traffic, raw M-Bus frames or development capture traces as part of normal product history.

Data leaves the app only through explicit user actions such as backup, CSV export or Android sharing. Android platform backup is disabled for the application.

See [docs/PRIVACY.md](docs/PRIVACY.md).

## Compatibility

The protocol path has been physically validated against a Qalcosonic W1 meter using Android NFC-V hardware. NFC antenna placement, NFC-V behavior and meter firmware can vary between devices, so compatibility with every phone/meter revision is not guaranteed.

Useful compatibility reports are welcome, provided they do not publish private meter identifiers or consumption data.

## Installation

W1 NFC Reader is distributed as a signed Android APK through this repository's **GitHub Releases** page. It is not distributed through Google Play, so installation is currently done manually ("sideloading").

### Requirements

- Android 7.0 (API 24) or later;
- an Android device with NFC support;
- NFC-V / ISO 15693 support for communication with compatible Qalcosonic W1 meters.

### Installing the APK

1. Open this repository's **Releases** page on the Android device.
2. Download the APK from the latest stable release, for example `w1-nfc-reader-1.0.0.apk`.
3. Open the downloaded APK.
4. Android may ask you to allow the browser or file manager you used to **install unknown apps**. Enable this permission for that app.
5. Confirm the installation.
6. After installation, you may disable the "install unknown apps" permission again.

The exact wording and location of this Android setting can differ between Android versions and device manufacturers.

Each stable release contains:

- the signed APK;
- a SHA-256 checksum;
- source code matching the release tag.

For security, install W1 NFC Reader only from the official GitHub repository. Technically experienced users can additionally verify the downloaded APK against the published SHA-256 checksum and signing-certificate identity.

Android or Google Play Protect may display an additional warning because the APK was downloaded outside Google Play. Such a warning can occur for sideloaded applications and does not by itself mean that the APK has been modified. Always verify that the file came from this repository before installing it.

## Building

Requirements:

- JDK 17;
- Android SDK 35;
- Gradle 8.9 / Android Gradle Plugin 8.7.3.

After cloning the standalone repository:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The translation consistency check can be run with:

```bash
python3 scripts/check_product_i18n.py
```

The app currently targets Android SDK 35 and supports Android API 24 and later.

## Project layout

```text
app/                    Android application
scripts/                validation/build helper scripts
docs/                   public product, privacy and safety documentation
.github/workflows/      public CI and release automation
```

Historical protocol probes, raw field captures, private handoff files and internal real-device research records are intentionally not part of the public product repository.

## Contributing

Bug fixes, accessibility improvements, translations, privacy-safe tests, documentation and evidence-backed compatibility/decoding improvements are welcome.

Protocol changes require extra care because the app talks directly to a metering device. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## Licensing

Project-authored software is distributed under **GNU GPL-3.0-or-later**. Project-authored documentation is distributed under **CC-BY-SA-4.0** unless a file states otherwise.

Third-party components and research sources retain their own licenses and attribution. See:

- [LICENSE](LICENSE)
- [LICENSES.md](LICENSES.md)
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

The NFC mailbox sequence and parts of the Qalcosonic M-Bus parsing/field mapping were informed by the open-source `dbmaxpayne/esphome_qalcosonicnfc` project by Mark Hermann, licensed LGPL-2.1-or-later. Additional Android/archive research was cross-checked against `MrGoodbody/AxiomaQalcosonicW1NFCReaderAndroid` (AGPL-3.0); no source code from that AGPL project is intentionally included in this repository.

## Project background and maintainer availability

This project started as a private spare-time project out of personal need, curiosity and interest. I wanted a practical way to read a Qalcosonic W1 directly with an Android phone and to understand the protocol well enough to turn the research into a reliable, useful app. I decided to publish the resulting work so that other meter owners and developers can benefit from it instead of having to repeat the same research from scratch.

Issues, corrections and pull requests are welcome. This is not a commercial project and there is no support or response-time commitment. I have a young child and limited spare time, so reviews and replies may sometimes take a while. Community contributions are nevertheless very welcome.

Copyright © 2026 Marc Leinen and contributors.
