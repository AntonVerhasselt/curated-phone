# Architecture and approval contract

One Android app module uses manual dependency construction in `CuratedApplication`. A feature ViewModel coordinates the eight Compose screens. Room stores drafts, requests, the fully applied signed snapshot, replay versions, an application journal, and non-secret history. Credentials use AES-GCM with an Android Keystore key in private, backup-excluded storage.

## Trust boundaries

The phone edits drafts freely. Draft persistence cannot authorize Android or Knox calls. The Convex HTTP layer accepts a 256-bit random device credential over HTTPS; only its HMAC digest is stored server-side. Pairing codes contain 128 bits of randomness, expire in ten minutes, and are consumed in a transaction. Only an authenticated operator invokes the internal pairing-creation action.

Every phone operation resolves device identity from the token digest. A supplied request ID must belong to that device. Public Convex query/mutation bypasses are absent: storage queries and mutations are internal. Signing/HMAC/random generation lives in Node actions; atomic state transitions live in the normal Convex transaction runtime.

The eight-digit OTP's HMAC binds request ID, generation, channel, recipient role, and code. Challenge expiry is checked against backend time. Wrong checks return an error result after committing attempt increments; they do not throw and roll back. Each generation allows five attempts, resends wait sixty seconds, each device/recipient has five sends/hour, and each device has twenty verification attempts/hour across generations. Older email delivery results cannot overwrite newer generation status.

## Request lifecycle

1. Normalize/sort/deduplicate a complete snapshot. Reject protected packages, unsupported schemas, invalid domains, missing category feeds, and oversized requests.
2. Bind it to current policy and guardian versions. Recompute the complete diff on the server. A new request cancels the previous pending batch; approved/unapplied operations block conflicting submissions.
3. Issue primary authorization, plus proposed-inbox verification when required. Initial setup also verifies the backup inbox. Recovery is accepted only for standalone primary replacement with unchanged policy and backup.
4. Atomically consume the challenge and, when all required proofs exist, allocate one approval ID and target versions. Concurrent checks cannot authorize twice.
5. Sign and store one immutable envelope. If a network response or signing action is interrupted, `/device/state` or `/requests/verify` recovers the recorded operation and generates the missing envelope from its stored authorization.
6. The phone verifies the pinned key, DER signature, exact request/digest/purpose/snapshot, device ID, supported schema, base/target versions, and validity window. Signature verification uses the exact transmitted UTF-8 bytes; no JSON reserialization is used for cryptographic verification.
7. Persist the verified envelope and journal before any native call. Apply controller protection, exact Knox rules, suspension changes, Chrome rules, final validated bypass restrictions, and native readback. A crash resumes the same approval ID. An already accepted journal can resume beyond the envelope's first-acceptance expiry; it does not grant new authorization.
8. Commit the applied local policy and consumed version together in a Room transaction. Acknowledge success to Convex only after readback. Retry a lost acknowledgement. Applied version remains separate from approved version. Retired devices may repeat their exact completed removal acknowledgement; they cannot submit new changes.

## Signed format

`{ payload: string, signature: base64 DER, keyId: string }`.

The UTF-8 `payload` is a JSON object with `schemaVersion`, `deviceId`, `requestId`, `approvalId`, `purpose`, `requestDigest`, base policy/guardian versions, target policy/guardian versions, `snapshot`, `issuedAt`, and `expiresAt`. The snapshot has policy and guardian addresses. Policy schema 1 always uses `appPolicyMode: "blocklist"`; old allowlist schemas are rejected.

Server signing is ECDSA P-256 / SHA-256. Public keys are SPKI PEM; private keys are PKCS8 PEM. A Node-generated signature fixture is verified by Kotlin unit tests. Only the fixture public key/signature are committed; its ephemeral private key is discarded. Key rotation requires a trusted APK update and planned transition.

## Native enforcement

`PolicyManager` owns Android device policy calls. It protects the controller plus an essential baseline and the actual launcher/default dialer. Knox integration is isolated and loaded only when the proprietary SDK exists on supported Samsung hardware. It rejects wildcard/default-deny rules, conflicting administrators, and whitelist exceptions, then checks exact own-admin installation-list readback.

Installed blocked apps are suspended without deleting data. Only packages tracked as managed by this controller are unsuspended during unblocking/removal. A union is saved before mutation to survive interrupted suspension. Absent blocklist entries remain installation restrictions. Nonblocked app installs/updates are not blanket-disabled.

Chrome uses managed `URLBlocklist`/`URLAllowlist` and persists the original application restrictions for removal. Feed downloads are bound to guardian-approved HTTPS source metadata; content maintenance cannot change the source or category. Downloads are bounded at 5 MB, parsed strictly, hashed, cached atomically, and failures retain existing nonempty data. The combined domain set must fit the native 1,000-rule limit. Native DPM readback is evidence that restrictions were stored; actual Chrome navigation remains a device acceptance test.

Final debugging/user/safe-boot restrictions and ownership release are guarded by build-time validation assertions. These are set only after real release tests, not through an unapproved runtime switch. No Accessibility enforcement, generic VPN lockdown, kiosk mode, certificate interception, automatic wipe, plaintext code logging, or secret-bearing notification is implemented.

## HTTP API

| Endpoint | Authentication | Behaviour |
| --- | --- | --- |
| `GET /health` | Public | Service/schema status only |
| `POST /device/pair` | One-time code | Issues device token once |
| `GET /device/state` | Device bearer | Own policy, pending request, safe challenge status, envelope, history |
| `POST /requests` | Device bearer | Immutable snapshot and exact diff |
| `POST /requests/verify` | Device bearer + OTP | Atomic challenge verification and recoverable signed envelope |
| `POST /requests/resend` | Device bearer | New generation under rolling limits |
| `POST /requests/cancel` | Device bearer | Pending requests only |
| `POST /device/applied` | Device bearer | Matching signed operation outcome, separate applied version |
| `POST /device/feed-status` | Device bearer | Cache checksum/time bound to an already approved feed source; cannot change its configuration |
| `POST /resend-webhook` | Verified provider signature | Delivery/bounce state |

Guardian-change notifications contain no OTP and grant no new powers. The official Resend component retains queued message bodies; hourly cleanup removes finalized bodies after one day and abandoned jobs after seven days. This does not control retention in a recipient inbox or the external provider account. Challenge HMAC records are removed after seven days beyond expiry; immutable approval/audit records remain for the personal device's history.
