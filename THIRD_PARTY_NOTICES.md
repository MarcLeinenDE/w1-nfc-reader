# Third-party notices

W1 NFC Reader is an independent project. Third-party names are used descriptively and do not imply affiliation or endorsement.

## `dbmaxpayne/esphome_qalcosonicnfc`

Protocol behavior and field mapping used by W1 NFC Reader were materially informed by:

- Project: `dbmaxpayne/esphome_qalcosonicnfc`
- Upstream author/copyright notice: Copyright (c) 2025 Mark Hermann
- Upstream license: GNU Lesser General Public License v2.1 or later (`LGPL-2.1-or-later`)
- Reviewed upstream revision: `bed6773b803a7ebf71613585aad8a73376d38b8e`
- Repository: https://github.com/dbmaxpayne/esphome_qalcosonicnfc

Relevant areas include ST25 dynamic-mailbox behavior, Qalcosonic M-Bus read sequencing, M-Bus frame handling and Qalcosonic W1 VIF/VIFE field mappings.

The Android NFC transport is implemented for Android `NfcV`, but the parser/field-mapping work should be treated conservatively as influenced by and, where applicable, adapted/translated from that upstream implementation rather than as wholly provenance-independent code. W1 NFC Reader is distributed under GPL-3.0-or-later, using the GPL conversion option provided by LGPL-2.1 section 3 for any protectable adapted/translated upstream implementation, with upstream attribution and LGPL provenance preserved.

`QalcosonicReader.java` and `MbusParser.java` carry explicit provenance/modification headers. A copy of the LGPL v2.1 license is bundled in the Android app for offline viewing.

## `MrGoodbody/AxiomaQalcosonicW1NFCReaderAndroid`

Additional Android and archive-selection research was cross-checked against:

- Project: `MrGoodbody/AxiomaQalcosonicW1NFCReaderAndroid`
- Upstream attribution: GitHub user `MrGoodbody`
- Upstream license: GNU Affero General Public License v3.0 (`AGPL-3.0`)
- Reviewed repository revision: `72811389a43dc42eda337714a081129dc87d7241`
- Repository: https://github.com/MrGoodbody/AxiomaQalcosonicW1NFCReaderAndroid

This project is used as an external research/reference source for protocol facts and independent comparison. **No source code from this AGPL project is intentionally included in W1 NFC Reader.**

If code from that project is ever proposed for inclusion, it must not be merged on attribution alone. The exact reused scope and resulting AGPL obligations must be reviewed and documented before integration.

## Android runtime dependencies

The Android application directly depends on:

- Google Material Components for Android (`com.google.android.material:material`)
- AndroidX AppCompat (`androidx.appcompat:appcompat`)
- AndroidX Activity (`androidx.activity:activity`)
- AndroidX DrawerLayout (`androidx.drawerlayout:drawerlayout`)

These projects are distributed under the Apache License 2.0. Their transitive dependencies retain their respective upstream licenses.

The canonical Apache-2.0 license text is included in the public source tree at:

`THIRD_PARTY_LICENSES/Apache-2.0.txt`

and is bundled into the APK as:

`app/src/main/res/raw/apache_2_0.txt`

Development/test dependencies such as JUnit and Robolectric are not part of the shipped APK and retain their own upstream licenses.

## Attribution policy

Material third-party sources should remain traceable. When third-party code is copied or adapted, record:

- source project/repository;
- upstream copyright holder or author where stated;
- upstream license;
- exact files/functions or substantial portions reused;
- whether the use is research-only, independent implementation, translation/adaptation or direct reuse;
- any corresponding source, notice or reciprocal-license obligations.

Do not remove a correct attribution merely because code has later been refactored.

## Trademarks

Axioma, Qalcosonic, STMicroelectronics, ST25, Google and Android are trademarks or product names of their respective owners. Their use in this project is solely descriptive.
