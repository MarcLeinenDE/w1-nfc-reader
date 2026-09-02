# Security policy

## Supported versions

Security fixes are normally made against the latest public release and current `main` branch.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability that would expose private user data, release-signing material or a reproducible security weakness before a fix is available.

Use GitHub's private security-advisory / private vulnerability-reporting mechanism for the public repository when available. If private reporting is not available, open a minimal public issue asking for a private contact channel without publishing exploit details or sensitive data.

## Signing and release integrity

Official release APKs are expected to be signed with the long-term W1 NFC Reader release certificate. Release automation verifies the expected certificate identity and publishes a SHA-256 checksum alongside the APK.

Never commit keystores, signing passwords, private keys, raw secrets, backups or unredacted meter captures.

## Meter safety

Security reports that involve changing meter configuration, radio state, calibration, firmware or other persistent device state should not include destructive proof-of-concept instructions in a public issue. The application itself intentionally remains read-focused.
