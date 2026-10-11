# Recorded software validation

Date: 11 October 2026. Results below apply to this repository's development implementation and local Android emulator. They do not substitute for the Samsung acceptance matrix.

| Check | Result |
| --- | --- |
| Fixed brief inventory export/integrity | Passed: exactly 200 unique blocked packages, five required social/video packages, stable Chrome excluded |
| Backend Vitest / convex-test | Passed: 29 tests covering normalized policy, protected packages/schema/domains, complete diffs, primary/recovery roles, device isolation, single-use pairing, concurrent OTP authorization, failed-attempt persistence, expiry/generation/resend limits, old email races, stale/cancelled batches, application separation, guardian reachability proofs, and removal acknowledgement recovery |
| Backend TypeScript | Passed with strict type checking |
| Hosted development HTTPS integration | Passed: public health, missing/wrong credentials rejected, one-time pairing, own-device state, immutable submission, both required challenge channels, failed-code persistence, cancellation, fixture cleanup |
| Hosted production health | Passed: service/schema response on separate EU deployment; device calls require authentication |
| Android debug/release builds | Passed; release and debug manifests differ in debugging/testOnly configuration |
| Android JUnit signature/security | Passed: 10 tests, including a real Node-generated P-256 DER signature verified by Kotlin, payload/device/key/digest/purpose/version/snapshot/expiry rejection, unsupported schema, protected package/domain validation |
| Android instrumentation | Passed: 5 tests on Android 16 emulator covering exact seed, real Keystore credential round trip, Room persistence/journal completion, honest non-Samsung Knox status, and Compose navigation without management permissions |
| Android lint | Passed with no errors. Remaining warnings are pinned-dependency update notices and optional KTX style suggestions. No baseline suppresses security/API errors. |
| First native Android run | Passed: controller opens on the emulator without requiring management |
| Signed release ordinary install | Passed on a second fresh Android 16 emulator with no owner: ordinary install and native startup, no testOnly/debugging flag or management requirement |
| Fresh-emulator device owner | Passed: owner component set and confirmed by `dumpsys device_policy` |
| Signed prototype | Built using the stable private signing identity, production URL/public verification key, and false physical-validation gates. Signature/manifest inspection recorded below. |

Commands:

```bash
node scripts/export-default-blocklist.mjs
node scripts/check-briefing.mjs
cd backend
npm test
npm run typecheck
npx convex dev --once
npm run smoke
cd ../android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

The hosted smoke test creates only synthetic unapproved fixtures, does not read or retrieve any OTP, and cleans up its own device data. Email sends cannot succeed without the missing Resend credentials; no approval occurs as a result of delivery failure.

No actual Samsung, real guardian inbox, Knox licence, banking account, Android Auto connection, large-category forwarding engine, production phone enrolment, or complete approved Samsung removal was tested. These are recorded as open in [BLOCKERS.md](BLOCKERS.md), not as automated-test successes.

## Published prototype identity

- APK: `curated-phone-v0.1.0.apk`
- APK SHA-256: `2579cc3424e26d4a1c5ac0cd5f29cfeb8706bcc9dded5e712926f4eb19638f6b`
- Signing certificate SHA-256: `2adfeb65ac60a10156f43da748cefddfe5aa93916e0bd83d554e3a225123b851`
- Package/version: `be.anton.curatedphone` / `0.1.0` (code 1)
- `debuggable=false`, `testOnly=false`, cleartext disabled
- Production endpoint and `production-p256-1` public key pin verified in DEX
- Both physical validation flags remain false
