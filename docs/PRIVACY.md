# Privacy and local data

W1 NFC Reader is designed to operate locally on the Android device.

## Network access

The application manifest does **not** request Android Internet permission. The product does not require an account, cloud service or Home Assistant connection.

Links intentionally opened from About, including the optional developer-support link, are handed to the device's external browser with Android `ACTION_VIEW`. W1 NFC Reader does not embed a WebView, process payments, use a payment SDK or receive payment status.

## Data stored locally

Normal product storage can include:

- meter/M-Bus identifier;
- decoded Live readings and their Android acquisition timestamps;
- decoded Hour, Day and Month archive periods and logger timestamps;
- history-sync state/metadata for each archive family;
- archive integrity/conflict metadata;
- meter lifecycle/replacement relationships;
- local UI preferences;
- for the optional Christmas support prompt, only the year of an eligible in-season successful Live read and the year in which that season's prompt has already been handled.

The app uses local Android storage/SQLite for these product features. The seasonal support state contains no payment result and no additional personal profile data.

## Data not intentionally persisted by normal history

Normal product history does not intentionally persist:

- NFC tag UID;
- raw NFC command/response traffic;
- raw M-Bus frames;
- private development traces or field-capture sessions.

Protocol code necessarily processes transport/frame bytes in memory while reading a meter, but those raw values are not part of the normal persistent history model.

## Backup and export

The app provides explicit user-triggered data portability:

- `.qw1backup` is the canonical machine-restorable backup format **within a supported backup schema**;
- W1 NFC Reader 2.0.0 writes and accepts backup schema 2; 1.x schema-1 `.qw1backup` files are not a v2 restore path;
- unsupported backup schemas and checksum failures are rejected before normal restore mutation begins;
- restore is transactional and should roll back if validation/import fails after mutation has started;
- a v2 backup includes the per-family History sync state needed to continue safely after restore;
- CSV is intended for human-readable export/interoperability, not as the canonical full restore format.

The app does not silently upload either format. Seasonal support-prompt state is intentionally not treated as meter/history backup data.

## Sharing and diagnostics

Android sharing is user-triggered. Product share actions use the **last successful Live read** rather than a failed later NFC attempt.

The normal in-app Diagnostics screen reads local product/build/synchronization status. It does not expose raw NFC traffic, raw M-Bus frames or private field traces. General privacy explanations live in About/privacy documentation rather than being duplicated as a separate Diagnostics card.

Before sharing backups, CSV exports or screenshots, remember that meter identifiers and consumption history can be personal data in context.

## Android platform backup

`android:allowBackup` is disabled for W1 NFC Reader. Use the app's explicit backup/export functions if you want a portable copy of local data.

## Deleting data

Data can be removed through the product's data-management/replacement flows or by clearing/uninstalling the application through Android. Keep a compatible v2 `.qw1backup` first if you may need to restore the v2 history later.

## Bug reports

Do not post real meter IDs, NFC UIDs, private consumption history, unredacted backups or raw field captures in public GitHub issues. Prefer minimized synthetic fixtures and privacy-safe diagnostic summaries.
