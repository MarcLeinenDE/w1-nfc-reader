# W1 NFC Reader

W1 NFC Reader is an independent Android app for reading compatible **Axioma Qalcosonic W1** water meters locally over **NFC-V / ISO 15693**.

The app is designed around a conservative, read-focused protocol model: a normal NFC contact performs a fast live read, while historical meter data is retrieved only after an explicit user action.

> This project is independent and is not affiliated with, endorsed by, or supported by Axioma Metering, STMicroelectronics, Google, or the Android project. Product and company names are used descriptively only.

## Features

- live meter readout over Android NFC-V;
- explicit **Monthly history synchronization**;
- local history and consumption statistics;
- meter-replacement handling without subtracting cumulative values across different meter IDs;
- local `.qw1backup` backup/restore with integrity validation;
- human-readable CSV export;
- Android share-sheet integration for the last successful live read;
- light, dark and system appearance;
- English, German, French, Polish, Dutch and Lithuanian UI resources;
- RTL-ready Android layout handling;
- no account, cloud service or network connection required.

## Supported protocol scope

The first public release intentionally supports a narrow, physically validated scope.

### Live read

A normal NFC contact performs the validated live/default read only. A failed later contact does not erase the last successful live state.

### Monthly history

Historical data is synchronized only after the user explicitly selects **Synchronize history** and then presents the intended meter again.

The production path currently supports the **Month** archive family only. It alternates the validated FCB1/FCB0 request sequence and stops on the meter's actual terminal condition rather than hardcoding a previously observed number of periods.

Day, Hour and Year/Billing archive acquisition is **not part of the public production feature set** until separately validated and released.

## Safety model

The app is read-focused. It does not intentionally write persistent meter configuration, radio configuration, calibration data, metering parameters or firmware.

The protocol implementation uses the volatile ST25 mailbox transport needed to exchange M-Bus read/application-selection messages with the meter. The validated Month flow restores the default application after history traversal and verifies the restored default response structurally.

Protocol changes should be evidence-backed and regression-tested. See [docs/PROTOCOL_SAFETY.md](docs/PROTOCOL_SAFETY.md).

## Privacy

W1 NFC Reader works locally on the Android device and the app manifest does **not** request Internet permission.

The app stores decoded meter/history data locally. It does not intentionally persist NFC UIDs, raw NFC traffic, raw M-Bus frames or development capture traces as part of normal product history.

Data leaves the app only through explicit user actions such as backup, CSV export or Android sharing. Android platform backup is disabled for the application.

See [docs/PRIVACY.md](docs/PRIVACY.md).

## Compatibility

The protocol path has been physically validated against a Qalcosonic W1 meter using Android NFC-V hardware. NFC antenna placement, NFC-V behavior and meter firmware can vary between devices, so compatibility with every phone/meter revision is not guaranteed.

Useful compatibility reports are welcome, provided they do not publish private meter identifiers or consumption data.

## Installation

Public binaries are intended to be distributed through this repository's **GitHub Releases** page.

Each stable release should contain:

- the signed APK;
- a SHA-256 checksum;
- source code matching the release tag.

For security, install release APKs only from the official repository or verify the published checksum and signing certificate identity.

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
