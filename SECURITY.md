# Security reporting and operating assumptions

Report security issues privately through the repository's GitHub security advisory feature when available. Do not post bearer credentials, OTPs, guardian addresses, licence keys, keystore passwords, or private signing material in public issues.

This project protects exact guardian-approved settings on a device-owner-enrolled Samsung. It does not claim irreversible control against its owner, who retains source code/backend/signing-key ownership and hardware-reset options. A development APK, emulator, work profile, or ordinary installation does not establish the daily-use enforcement scope.

Backend writes are internal, phone calls are device authenticated, and guardian authorization is separate from phone authentication. Approval envelopes are signed and version-bound. Do not add a public mutation, test OTP endpoint, generic unlock Boolean, release bypass button, plaintext secret log, or automatic wipe fallback.

Signing keys live outside Git and must be backed up privately. Keep production/dev credentials, deployment URLs, guardian emails, and key pins separate. Changes to the HMAC/signing secrets require an explicit migration rather than replacing enrolled-device trust silently.

Review [docs/BLOCKERS.md](docs/BLOCKERS.md) before daily enrolment. The published prototype keeps physical acceptance gates explicit.
