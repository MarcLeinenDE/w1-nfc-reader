# Privacy and local data

W1 NFC Reader is designed to operate locally on the Android device.

## Network access

The application manifest does **not** request Android Internet permission. The product does not require an account, cloud service or Home Assistant connection.

## Data stored locally

Normal product storage can include:

- meter/M-Bus identifier;
- decoded live readings and their Android read timestamps;
- decoded Month archive periods and logger timestamps;
- history-sync metadata;
- meter lifecycle/replacement relationships;
- local UI preferences.

The app uses local Android storage/SQLite for these product features.

## Data not intentionally persisted by normal history

Normal product history does not intentionally persist:

- NFC tag UID;
- raw NFC command/response traffic;
- raw M-Bus frames;
- private development traces or field-capture sessions.

Protocol code necessarily processes transport/frame bytes in memory while reading a meter, but those raw values are not part of the normal persistent history model.

## Backup and export

The app provides explicit user-triggered data portability:

- `.qw1backup` is the machine-restorable backup format;
- backup integrity is checked before database mutation;
- restore is transactional and should roll back if validation/import fails;
- CSV is intended for human-readable export, not as the canonical full restore format.

The app does not silently upload either format.

## Sharing

Android sharing is user-triggered. Product share actions use the **last successful live read** rather than a failed later NFC attempt.

Before sharing files or diagnostics, remember that meter identifiers and consumption history can be personal data in context.

## Android platform backup

`android:allowBackup` is disabled for W1 NFC Reader. Use the app's explicit backup/export functions if you want a portable copy of local data.

## Deleting data

Data can be removed through the product's data-management/replacement flows or by clearing/uninstalling the application through Android. Keep a `.qw1backup` first if you may need to restore the history later.

## Bug reports

Do not post real meter IDs, NFC UIDs, private consumption history, unredacted backups or raw field captures in public GitHub issues. Prefer minimized synthetic fixtures and privacy-safe diagnostic summaries.
