# Device acceptance record

## Completed development experiment

Date: 11 October 2026. Device: `emulator-5554`, Google API 36 x86_64 Android 16 image, Pixel 6 profile. No personal accounts or existing owner were present before this project's enrolment. The APK installed and opened, and `dpm set-device-owner be.anton.curatedphone/.admin.PhoneAdminReceiver` returned success. `dumpsys device_policy` confirmed the owner component.

This emulator has no Samsung Knox implementation. It is not evidence that installation blocking, banking compatibility, production DPC eligibility, full category coverage, or complete Samsung removal works. No physical phone was enrolled, reset, wiped, or modified.

## Actual Samsung record — pending

| Field | Value |
| --- | --- |
| Model / SKU | Pending; briefing assumes Galaxy A55 SM-A556B |
| Android / SDK | Pending |
| One UI / security patch | Pending |
| Knox version / SDK / licence | Pending |
| APK version / SHA-256 / signing certificate | Pending final tested release |
| Backend deployment / key ID | Pending final test selection |
| Date / tester | Pending |
| Proven enrolment route | Pending |
| Proven complete removal route | Pending |

## Required behaviour checks

Record Pass/Fail, exact behaviour, native readback, and logs with OTPs/credentials redacted. Do not treat an API returning true as sufficient evidence.

| Test | Passing evidence | Samsung result |
| --- | --- | --- |
| Ordinary installation | Native controller opens while One UI, switching, notifications, calls, Bluetooth/location/settings remain usable | Pending |
| Production ownership | Supported whole-device enrolment accepts this signed DPC; recorded route/eligibility | Pending |
| Initial guardian setup | Both separate inboxes verified; full initial diff approved by primary; initial route locked afterward | Pending |
| Exact batch | Email and submitted immutable snapshot agree; draft edits need a new request | Pending |
| Wrong/expired/used/old OTP | No approval; failure attempts persist; resend generation invalidates delayed emails | Pending real delivery; backend automated checks pass |
| Concurrent verification | Exactly one approval ID/version; lost response recovers the same envelope | Backend automated checks pass; phone integration pending |
| Recovery role | Backup can only replace primary; new-inbox verification required; no app/category/backup/removal approval | Backend automated checks pass; real email pending |
| Device isolation/signature | Wrong credential/device/key/digest/schema/version/payload rejected | Automated checks pass; final release key integration pending |
| Interrupted native apply | Interrupt each stage; journal resumes the exact authorized target without a general unlock | Pending |
| Listed installations | Social app, Samsung Internet, another browser, and beta/nightly package cannot install via Play, Galaxy Store, or sideload | Pending |
| Listed existing apps | Installed blocked packages, including preinstalled YouTube, cannot launch; data preserved | Pending |
| Default-allow installs | A previously unknown nonblocked app installs without guardian approval | Pending |
| Nonblocked updates | Essential app/store/platform/controller updates succeed; blocked update cannot regain usability | Pending |
| Exact unblocking | Guardian-approved removal of one package restores only that package; other rules remain | Pending |
| Rule persistence | Uninstall/reinstall/reboot/network changes retain the approved app rules | Pending |
| Controller protection | Settings cannot uninstall, force-stop, or clear controller data; final validated debug/user/safe-boot controls work | Pending |
| Essential components | Stable Chrome, launcher/dialer, permissions, WebView, Google Play/services, Samsung components remain usable | Pending |
| Chrome managed rules | `chrome://policy` and real navigation prove named host/subdomain blocking, allowed pages, Incognito, and Custom Tabs | Pending |
| Complete categories | Tested sources cover all four categories; large lists are fully enforced by a validated mechanism without truncation | Pending |
| Network filter robustness | Secure DNS, Private DNS, caching, IPv6, QUIC, reconnects, VPN termination/conflicts, reboot, Wi-Fi/mobile | Pending |
| Feed failure | Failed/empty/bad source downloads retain last usable data; metadata/checksum/license/source shown; source changes require approval | Parser/cache implemented; device test pending |
| Belfius/itsme | Registration when needed, login, normal app-to-app auth; no credential recording or real transfer testing | Pending |
| WhatsApp | Notification delivery and a call | Pending |
| Waze/Maps | GPS/navigation and audio | Pending |
| Android Auto | Actual wired/wireless connection and required USB behaviour | Pending |
| Approved removal | Primary-only standalone approval; complete cleanup readback and ownership release; interrupted cleanup resumes; device retired | Pending |
| Signed upgrade | Same package/signing identity upgrades the enrolled controller without opening unrestricted setup | Pending |

Repeat affected checks after Samsung OS/Knox updates and policy/filter changes. Hardware recovery wipes remain possible. Daily deployment cannot pass while the physical gates are pending.
