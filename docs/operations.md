# Backend operations

## Secrets and environments

Use [.env.example](../.env.example) as a field-by-field reference. Environment copies, credentials, Knox binaries/licence keys, and keystores are ignored. Public backend URLs and verification keys can ship in an APK; private signing/HMAC/email credentials cannot.

On this machine separate random secrets were already created and configured. For a new project, from `backend/`:

```bash
node scripts/generate-secrets.mjs ../.secrets/development
node scripts/configure-secrets.mjs ../.secrets/development/convex-secrets.json
node scripts/generate-secrets.mjs ../.secrets/production
node scripts/configure-secrets.mjs ../.secrets/production/convex-secrets.json --prod
```

Generation refuses to replace existing keys. Do not rerun with new values against an enrolled device: changing the device HMAC secret invalidates its credential, and changing the signing key breaks the APK's pin. Keep recovery copies privately. A deliberate rotation needs a migration and trusted release update.

Development:

```bash
npx convex env set RESEND_API_KEY
npx convex env set EMAIL_FROM
npx convex env set RESEND_WEBHOOK_SECRET
npx convex env set RESEND_TEST_MODE true
npx convex env list --names-only
npx convex dev --once
```

Production uses the same commands with `--prod`. `npx convex deploy` publishes functions, schema, indexes, Resend components, and cleanup cron jobs to the default production deployment. Confirm its dashboard/URL before agreeing to the CLI deploy prompt.

## Email delivery

Verify your own sending domain in Resend and use a matching From address. Start with Resend test recipients and test mode. Configure the signed webhook at the environment's `/resend-webhook` with all `email.*` events. Confirm delivery/bounce states, not just provider queue acceptance. The phone reads safe status and expiry metadata only; its API never receives the code or message body.

Real guardian addresses need `RESEND_TEST_MODE=false`. Keep real/test devices and guardian addresses separate between deployments. Never log email bodies, OTP fields, HTTP bearer headers, or private signing data. A sending-domain or credential failure leaves requests unapproved and records failed delivery. The user can resend subject to cooldown and hourly limits.

Hourly cron jobs remove finalized Resend component records after one day, abandoned jobs after seven days, expired pairing codes after one day, and expired challenge HMACs after seven days. Configure the provider account's own retention separately. Notifications of guardian changes go to the relevant old/new inboxes and contain no approval code.

## Pairing and onboarding

The authenticated operator CLI invokes `cryptoActions:createPairing` with a phone label and two different guardian emails. Enter its one-time code on the phone within ten minutes. The initial submitted snapshot must retain the configured guardian addresses. Both inbox proofs and primary authorization are needed before the first signed approval.

Once authorized, bootstrap guardian editing is closed server-side. Only standalone approved replacement flows can change addresses. Reinstallation or data loss must not be interpreted as an approved empty blocklist. Missing/corrupt approved storage preserves native restrictions and requires deliberate recovery, not a free bootstrap.

## Maintenance and troubleshooting

- `GET /health` is public and exposes no device data. Test both deployment URLs independently.
- `npm test` runs authorization/transaction tests; `npm run typecheck` checks server and tests.
- `npm run smoke` tests hosted development HTTPS transport using short-lived synthetic fixtures. It does not read an OTP or simulate guardian authorization. It deletes only its own unapproved test device afterward.
- UI Status separates device ownership, Knox readback availability, server-approved version, locally applied version, server applied acknowledgement, rule count, sync, and application failures.
- An authorized/unapplied batch prevents conflicting requests. Retry it instead of approving a different local draft. A lost verify response is recovered through state sync and the stored signed envelope.
- An incorrect/expired code never changes native rules. Old generations stay invalid even if an older email arrives late.
- New category-feed content is maintenance. Feed URLs, licences, categories, and custom domains remain snapshot changes requiring approval. The app accepts strict domain-per-line HTTPS feeds and refuses empty or oversized downloads.
- Guardian recovery only replaces the primary. It is not an alternative settings/removal authority. If both inboxes are lost, no third app-level recovery path exists.

The backend account and source/signing-key ownership ultimately allow its owner to alter the system. The scope is strong everyday friction with explicit guardian approval, not irreversible control.
