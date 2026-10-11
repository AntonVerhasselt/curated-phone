# Release signing and deployment

The app's stable release signing identity was created outside Git at `~/.local/share/curated-phone/signing`. The directory is private, the keystore/password/config files have restricted permissions, and passwords were not printed. Back it up privately. Losing this key can prevent updating an already enrolled controller. Do not upload any file from this directory to GitHub.

For a new machine without an identity, run `JAVA_HOME=... node scripts/create-signing-key.mjs` from the repository root. The script refuses to replace an existing identity. The default alias is `curated-phone`; it creates a 3072-bit RSA key with long validity.

Build with explicit production values:

```bash
cd ~/Projects/curated-phone
export JAVA_HOME="$(mise where java@temurin-21)"
export CURATED_BACKEND_URL=https://necessary-cormorant-361.eu-west-1.convex.site
export CURATED_APPROVAL_KEY_ID=production-p256-1
export CURATED_APPROVAL_PUBLIC_KEY_FILE="$PWD/.secrets/production/approval-public-key.pem"
node scripts/build-release.mjs
```

The script reads protected signing configuration, passes it to Gradle's environment without printing the credentials, and builds `android/app/build/outputs/apk/release/app-release.apk`. Without signing environment values, CI deliberately builds an unsigned release artifact; it cannot replace a release-signed installation.

The published prototype has `debuggable=false` and `testOnly=false`, uses the production HTTP URL/public key, and retains both Samsung validation flags at false. It is suitable for ordinary UI/setup inspection. It cannot claim successful complete enforcement or daily-use acceptance until the hardware gates pass. Knox binaries and credentials are not bundled yet.

Before a daily release:

1. Add the licensed Knox SDK and prove activation lifecycle, exact install rules, suspension, nonblocked installs/updates, and release controller updates.
2. Prove a full supported ownership release on the exact Samsung with cleanup and interrupted recovery. The current evaluated API is deprecated; implement a supported mechanism if needed. Only then set `CURATED_REMOVAL_VALIDATED=true`.
3. Validate final bypass restrictions after update/removal proof. Only then set `CURATED_BYPASS_PROTECTION_VALIDATED=true`.
4. Select feeds and prove complete category enforcement. Managed Chrome's 1,000-entry ceiling is enforced, so a larger list requires a validated alternative mechanism before daily release.
5. Configure real production Resend delivery/webhook and guardian pairing. Prove approval/signature validation against the production key in the final signed APK.
6. Inspect APK signature and manifest with `apksigner verify --print-certs` and `apkanalyzer manifest print`. Confirm identity, public backend/key selection, release flags, permissions, and no development escape UI.
7. Save the full [device acceptance record](device-test.md), phone backup, account/SIM recovery information, and banking/itsme reactivation plan.

Provision only through a route already proven for that configuration. Ordinary installation preserves One UI but does not grant ownership. A debug-signed owner cannot be upgraded directly with this release key. Never weaken firmware or remove Google services to work around DPC eligibility.

Removal is a standalone primary-approved signed operation. Cleanup restores this controller's tracked native state before the validated release mechanism runs. A hardware recovery wipe remains possible; the app never invokes a wipe as an automatic fallback.
