# Curated Phone implementation plan

A beginner guide to building a personal Samsung control app with Kotlin and Convex

Prepared for Anton Verhasselt — 11 October 2026. Browser inventory checked on the same date.

We will build a native Android app that keeps Samsung’s normal interface. All apps are allowed by default; a guardian-approved blocklist prevents specific apps from being installed or used. The initial blocklist contains Facebook, Instagram, TikTok, YouTube, LinkedIn and the fixed browser package inventory in section 12. Google Chrome stable remains allowed. You will edit settings as a draft and request approval. A guardian receives the exact changes and a one-time password by email; entering that code authorizes only that request.

The chosen stack is Kotlin and Jetpack Compose on the phone, Room for local data, and Convex with TypeScript and Resend for the approval backend. This briefing explains local setup, project structure, implementation rules, testing and eventual installation. It is a build specification, not a finished application. Commands labelled as setup can be run now; device-management commands are for an eligible test device only.

Start by running an ordinary sample app. Then prove device-owner enrolment, Samsung Knox package blocking, updates for non-blocked apps, Chrome filtering and approved removal. These capabilities must pass on the actual Samsung configuration before we rely on them for daily use.

## Contents

- [1. Product requirements](#1-product-requirements)
- [2. What the technologies do](#2-what-the-technologies-do)
- [3. Feasibility work before committing](#3-feasibility-work-before-committing)
- [4. Computer and account requirements](#4-computer-and-account-requirements)
- [5. Install Android Studio and the SDK](#5-install-android-studio-and-the-sdk)
- [6. Create the repository and first Android app](#6-create-the-repository-and-first-android-app)
- [7. Run and debug on a phone or emulator](#7-run-and-debug-on-a-phone-or-emulator)
- [8. Set up Convex and Resend](#8-set-up-convex-and-resend)
- [9. Android code structure and local storage](#9-android-code-structure-and-local-storage)
- [10. Add the Android management shell](#10-add-the-android-management-shell)
- [11. Prove device policies on a test device](#11-prove-device-policies-on-a-test-device)
- [12. Add Samsung Knox and app restrictions](#12-add-samsung-knox-and-app-restrictions)
- [13. Build the Convex data model and endpoints](#13-build-the-convex-data-model-and-endpoints)
- [14. Device authentication and email approval security](#14-device-authentication-and-email-approval-security)
- [15. Exact batch approval and applying a policy](#15-exact-batch-approval-and-applying-a-policy)
- [16. Guardian changes onboarding and removal](#16-guardian-changes-onboarding-and-removal)
- [17. Chrome website filtering](#17-chrome-website-filtering)
- [18. Screens and daily behaviour](#18-screens-and-daily-behaviour)
- [19. Tests and acceptance criteria](#19-tests-and-acceptance-criteria)
- [20. Release signing and everyday installation](#20-release-signing-and-everyday-installation)
- [21. Build order and first session](#21-build-order-and-first-session)
- [22. Official references](#22-official-references)

## 1. Product requirements

The first version is for your own Samsung. It has a settings interface and retains One UI, notifications and normal app switching. We will distribute a signed APK privately. A public Play Store release, iOS version, custom operating system, custom launcher, screen-time schedules and remote monitoring are outside this first build.

| Capability | Required behaviour |
| --- | --- |
| Blocked apps | Allow all apps by default. Initially block installation and use of Facebook, Instagram, TikTok, YouTube, LinkedIn and all browser packages listed in section 12. Keep Google Chrome stable allowed. Suspend installed blocked apps without deleting their data. |
| Essential apps | Belfius Mobile, itsme, WhatsApp, Waze, Google Maps, Chrome and Android Auto must remain usable. Essential Android and Samsung components remain enabled. |
| Updates | Non-blocked apps, the controller and essential platform components must still update. |
| Websites | Chrome blocks social networks, video and streaming, gambling and adult-content categories, plus custom domains. Other apps are outside the website filter. |
| Approval | You submit a complete batch of changes. The email shows that batch and its OTP. |
| Primary guardian | Approves settings changes, guardian-address changes and removal. |
| Backup guardian | Can only approve replacement of an inaccessible primary email. It cannot approve app or website changes or removal directly. |
| Protection | Prevent ordinary uninstall, force-stop, data clearing and practical alternate-user or debugging bypasses while restrictions are active. |
| Offline operation | Keep the last approved restrictions. You can draft changes offline; submission and OTP verification need connectivity. |

Neither guardian inbox is accessible to you. The guardian decides whether to share the code. The app does not read their inbox or automatically retrieve their OTP. Installing an email app does not change this ownership rule. Installing a non-blocked app needs no guardian approval; adding or removing an app from the blocklist does.

All settings changes use batch approval, including making restrictions stricter. Updating the contents of already approved category feeds is a maintenance operation; changing categories, feed sources or custom domains requires approval. This distinction must be visible in the app.

## 2. What the technologies do

| Technology | Role in this project |
| --- | --- |
| Kotlin | Android application logic, policy enforcement, approval verification and background reconciliation. |
| Jetpack Compose | Native UI written as functions of state. ViewModel holds screen state; coroutines and Flow manage asynchronous work. |
| Android device policy | Android’s management APIs, used by our device policy controller after device-owner enrolment. |
| Samsung Knox SDK | Samsung-specific application installation controls. Requires suitable licence activation and permissions. |
| Room | An on-device SQLite database accessed through Kotlin entities and DAO interfaces. |
| Android Keystore | Protects keys used for device authentication or encrypting locally stored credentials. |
| Convex | Hosted database, TypeScript functions, HTTP endpoints and scheduled work. |
| Resend | Actual email delivery, integrated using the official Convex component. |
| Gradle | Builds Android code and downloads dependencies; the project includes its own Gradle wrapper. |

Room is local storage, not a cloud service. Convex stores authoritative approval records; Room stores the last approved policy, your draft and application progress. Native Android and Knox policies enforce restrictions. A Room record saying an app is blocked does not itself block that app. [^1]

We will use a single Android app module with packages for UI, data, management and approvals. This is enough structure for a small personal app. Keep dependencies pinned in Gradle’s version catalog and package-lock.json rather than upgrading versions automatically.

For the first version, I recommend HTTPS endpoints implemented as Convex HTTP actions and a Kotlin HTTP client such as OkHttp. This makes our per-device authentication explicit without adding a user-login service. Everything server-side still runs in Convex. Its official Kotlin client is also available if we later choose an OIDC login flow; it is not required for the HTTP approach. [^2][^3]

## 3. Feasibility work before committing

### Device ownership

An ordinary installed app cannot enforce this scope. A legacy device-admin toggle is also insufficient. The intended mode is device owner on a fully managed phone, covering the whole device. A work profile would only cover part of your personal phone.

Google now checks controllers installed during enterprise enrolment against an approved DPC allowlist. A custom app may be rejected. Android also documents ADB as a development enrolment method; success there does not establish that production QR enrolment will work. Record the supported route on your exact phone and do not assume eligibility for Google approval. [^4][^5]

### Samsung licensing and updates

Get a Knox development licence and prove that ApplicationPolicy installation controls work. Personal access to licence generation is not guaranteed; the portal may require partner assistance. A development key is for testing, so verify an appropriate licence for daily deployment before enrolling the real phone. [^6]

The installation rule allows all packages except those explicitly on the app blocklist. Knox blocks installation of those packages; existing blocked apps need separate suspension. Do not add a wildcard default block or use Android’s blanket DISALLOW_INSTALL_APPS, because either would conflict with allowing new non-blocked apps. Test ordinary installation and updates with the blocklist active. [^8][^33][^35]

### Chrome filtering and removal

Chrome’s built-in URLBlocklist is limited to 1,000 entries. That can handle a small domain list but is inadequate for many category feeds. Category blocking is therefore a separate prototype, not a promised completed feature. [^9]

The direct Android API clearDeviceOwnerApp is deprecated and intended for testing; it may leave policies behind. We must demonstrate a complete approved release of management on the Samsung. If the only supported removal route involves a reset, that must be explicitly accepted before daily deployment. [^7]

If one of these tests fails, preserve the desired scope and revise the implementation. An ordinary app using Accessibility should not be presented as equivalent to device-owner enforcement.

## 4. Computer and account requirements

The detailed commands below assume macOS, matching your existing Mac development setup. Check Apple menu > About This Mac for the processor, memory and macOS version. On a 2019 Intel MacBook Pro choose Intel downloads; on an M-series Mac choose Apple Silicon downloads.

Google currently lists macOS 12 as the minimum, 8 GB RAM for Studio and 16 GB for Studio plus the emulator. Intel support is being phased out. Keep at least 32 GB free as a practical starting budget for SDKs, build caches and a test emulator; more is useful. Prefer a physical Samsung for Knox testing. [^10]

- A data-capable USB cable and access to the Samsung’s Settings app.

- A spare Samsung or a willingness to prepare the main phone later. An emulator can test Android policies but does not provide Samsung Knox.

- Android Studio stable, Android SDK Platform Tools, SDK Build Tools and a stable SDK platform.

- Node.js and npm for Convex; Git for version control. Use a supported LTS Node release for a fresh setup. Your existing installation can remain if Convex supports it.

- Convex account and hosted development deployment; Resend account and a sending domain you control.

- Samsung Knox developer account and an appropriate development licence.

- Two real guardian email addresses, verified during onboarding. Use your own test addresses only in the development environment.

Android Studio includes its runtime and manages the Android build tools. Keep its generated Gradle configuration and JDK choices together. Recent Studio versions can use Gradle Daemon JVM criteria; follow that generated configuration rather than forcing an unrelated Java installation. [^11]

This project does not require Expo, React Native, Xcode, CocoaPods, a Docker installation, a separate database server or a VPS when using hosted Convex.

### Windows and Linux alternatives

The app and backend can also be developed on Windows or Linux. Install the official Android Studio package for that OS. SDK locations and shell syntax differ from the Mac commands. On Windows a Samsung USB driver may be needed; Linux may need USB device rules. Keep the Kotlin and TypeScript project layout unchanged. Use the official Studio installation and hardware-device guides for those platform steps. [^10][^12]

## 5. Install Android Studio and the SDK

1. Open https://developer.android.com/studio and download the current stable release for your Mac’s processor. Avoid Canary and preview builds for your first project.

2. Open the DMG, drag Android Studio into Applications and launch it. Choose not to import old settings if this is your first installation.

3. Complete the Setup Wizard and allow the SDK downloads to finish. Leave the normal installation locations in place. Signing into Google or enabling an AI coding assistant is optional for local app building.

4. Open Tools > SDK Manager, or use More Actions > SDK Manager from the welcome screen. Find it with Studio’s action search if the menu labels differ.

5. In SDK Platforms, install the stable platform needed by the generated project. API 36 corresponds to Android 16 and is useful for matching your current phone; use the template’s current stable compile and target SDK choices.

6. In SDK Tools, check Android SDK Platform-Tools, Android SDK Build-Tools and Android SDK Command-line Tools latest. Install Android Emulator only if you will use an emulator.

7. Note the Android SDK Location. On macOS it is usually ~/Library/Android/sdk. Click Apply and wait for installation to complete.

These instructions use Studio to install SDK components, then use the installed adb tool. This avoids maintaining a second Platform Tools installation through Homebrew.

### Make adb available in Terminal

Open Terminal. The following settings are for the usual SDK location; change it if SDK Manager showed a different path. Paste the export lines into ~/.zshrc for the standard macOS zsh shell, then reopen Terminal. If you use bash, place them in your bash startup configuration instead.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Check the tools. These commands inspect your setup and do not alter the phone:

```bash
adb version
node --version
npm --version
git --version
```

Expected result: each command prints a version. If adb is not found, compare ANDROID_HOME with SDK Manager’s path. If Node is missing, install the macOS LTS package from https://nodejs.org/en/download, reopen Terminal and check again. If Git is missing, macOS may offer Command Line Tools when you run git. [^13]

For terminal Gradle builds, use the same JDK required by the generated project. If the bundled Studio runtime is compatible with that configuration, you can run a build with a command-scoped JAVA_HOME:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew --version
```

Run that only from the Android project directory after it exists. If Studio’s generated daemon configuration selects a different JDK, use that selection instead. Do not hard-code an old JDK version from an unrelated tutorial.

## 6. Create the repository and first Android app

### Create the parent folder

Run these in Terminal. The folder name CuratedPhone is a working name and can be changed before release.

```bash
mkdir -p ~/Developer/curated-phone
cd ~/Developer/curated-phone
git init
mkdir backend
```

In Android Studio, click New Project > Phone and Tablet > Empty Activity. Choose the Compose template, rather than Empty Views Activity. Use the following initial values:

| Field | Value |
| --- | --- |
| Name | CuratedPhone |
| Package and namespace | `be.anton.curatedphone` |
| Save location | ~/Developer/curated-phone/android |
| Language | Kotlin |
| Minimum SDK | API 30 Android 11, as a project choice for the management APIs we plan to use |
| Build scripts | Kotlin DSL |
| Compile and target SDK | Keep the current stable choices generated by Studio |

Let Gradle Sync finish. Studio generates app/build.gradle.kts, a version catalog, the Gradle wrapper, AndroidManifest.xml, MainActivity.kt and the initial theme. Open android/ in Studio as an Android project; edit backend/ in VS Code or another editor.

Change the sample greeting to 'CuratedPhone prototype'. Run it before adding Room, Convex or Knox. The first success condition is a screen opening on an emulator or Samsung without any device-management privileges.

### Files and generated content

Commit source files, version catalogs, Gradle wrapper files, package-lock.json and database migrations. Exclude local.properties, build output, .gradle caches, backend/node_modules, .env files, signing keystores and credentials. The generated project already includes some exclusions; add backend-specific ones in the repository root.

```gitignore
# Root .gitignore additions
.env
.env.*
!.env.example
backend/node_modules/
**/local.properties
**/build/
**/.gradle/
*.jks
*.keystore
keystore.properties
.DS_Store
```

Backend _generated files follow Convex’s generated defaults; never edit them by hand. Start with one root repository. Only initialise remote hosting when you choose where to store it.

## 7. Run and debug on a phone or emulator

### Run on your Samsung without management

1. Open Settings > About phone > Software information. Tap Build number seven times and enter your phone unlock code if asked.

2. Return to Settings > Developer options and enable USB debugging. This is for development; it will be restricted only after the final setup is proven.

3. Connect the phone using a data cable, unlock it and accept the computer’s USB debugging authorisation prompt.

4. Run adb devices -l. The phone should appear with status device. If it says unauthorized, accept the prompt. If it is absent, try a different cable or USB port.

5. Select the Samsung in Android Studio’s device selector and press Run. The app will be built, installed and opened. [^12]

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

Record the model, Android version, One UI version, security patch and Knox version in docs/device-test.md. The assumed target is Galaxy A55 SM-A556B, but the recorded values are authoritative.

Use Logcat in Studio to see crashes. Filter by the app package. Use the debugger for Kotlin logic and Compose Preview for screens. Never log codes, bearer tokens, signing keys or email bodies containing OTPs.

### Use an emulator

Open Tools > Device Manager > Create Device. Choose a ordinary phone profile and a stable Android image. Use x86_64 on Intel and arm64 on Apple Silicon. For management tests, create a separate fresh emulator with no Google accounts. Knox code must be disabled or mocked there.

On an older Intel Mac with limited RAM, start with the real phone for ordinary UI testing and create an emulator only when needed. Use the fresh emulator for device-owner experiments. A successful emulator test cannot validate Samsung licensing, banking compatibility or Android Auto.

A debug app is convenient to edit and disposable. It is not the final locked controller. Stop using the main phone for management experiments until you have a tested backup and recovery plan.

## 8. Set up Convex and Resend

### Initialise the backend

From the repository root:

```bash
cd backend
npm init -y
npm pkg set type=module
npm install convex @convex-dev/resend
npm install --save-dev typescript convex-test vitest
npx convex dev
```

Choose the existing Convex account or sign in, create a project called curated-phone, and select a hosted cloud development deployment when offered. Confirm in the dashboard that it is a cloud deployment. CLI choices can vary; if it starts a local backend, reconfigure it before using a physical phone. Hosted development avoids making your laptop reachable from the phone. [^14][^15]

Leave npx convex dev running while editing. It watches backend files, deploys changes and generates types. Ctrl+C stops the watcher, not a cloud deployment. The CLI creates the Convex folder and local deployment configuration.

Create backend/convex/http.ts with the health endpoint below. It exposes no device data. Confirm the deployment URL in the Convex dashboard: HTTP routes use convex.site, while the database/client URL uses convex.cloud. All device-data endpoints added later must require authentication.

```typescript
import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";

const http = httpRouter();
http.route({
  path: "/health",
  method: "GET",
  handler: httpAction(async () =>
    Response.json({ status: "ok", service: "curated-phone" })
  ),
});
export default http;
```

With the watcher running and deployment successful, visit https://YOUR-DEPLOYMENT.convex.site/health on the phone. Replace YOUR-DEPLOYMENT with the exact deployment name. Expect the JSON status ok. This verifies phone-to-backend connectivity without a local network tunnel.

### Add the email component

Create backend/convex/convex.config.ts:

```typescript
import { defineApp } from "convex/server";
import resend from "@convex-dev/resend/convex.config.js";

const app = defineApp();
app.use(resend);
export default app;
```

In Resend, add a sending domain or subdomain you control, such as approvals.your-domain.be. Add the exact DNS records shown by Resend and wait until verification succeeds. Create an API key restricted to sending where supported. Use a From address on that verified domain. [^16]

Store values in the Convex development deployment. These commands prompt for values without putting them in shell history:

```bash
npx convex env set RESEND_API_KEY
npx convex env set EMAIL_FROM
npx convex env set OTP_HMAC_SECRET
npx convex env set DEVICE_TOKEN_HMAC_SECRET
npx convex env set APPROVAL_SIGNING_PRIVATE_KEY
npx convex env list --names-only
```

Generate random HMAC secrets and the approval signing key in a setup script using cryptographic randomness. Store the private key only in Convex and a protected recovery backup. The phone receives the public verification key. Never copy these secrets into the APK or Git.

Create a backend/scripts/generate-secrets.mjs setup script using Node crypto to generate secrets and keys. An example starting point is randomBytes(32).toString('base64') for HMAC secrets and generateKeyPairSync('ec', { namedCurve: 'prime256v1' }) for the signing pair. Export the private key as PKCS8 PEM and the public key as SPKI PEM; choose an explicit DER signature encoding for compatibility with Android’s SHA256withECDSA verification. The setup script must not run during normal request handling or replace existing production keys.

The Resend component defaults to test mode. Initially use delivered@resend.dev for transport checks; when ready to send real test emails, explicitly configure testMode: false. Add a verified webhook endpoint using the component’s signature verification to track delivery and bounces. Provider acceptance is not proof that the guardian saw the message. [^17][^34]

## 9. Android code structure and local storage

Create the following packages under android/app/src/main/java/be/anton/curatedphone. Kotlin files can live under a java folder; this is normal in Android projects.

| Package | Main files and responsibility |
| --- | --- |
| ui | StatusScreen, AppsScreen, WebsitesScreen, GuardiansScreen, ReviewScreen, OtpScreen, HistoryScreen and RemovalScreen. |
| data | AppDatabase, PolicyEntity, DraftEntity, RequestEntity, ApplyJournalEntity and their DAO interfaces. |
| network | BackendClient, API request and response models, device authentication and HTTPS handling. |
| approval | ApprovalVerifier and PolicyValidator; validate signed payloads and protect against replay. |
| admin | PhoneAdminReceiver, provisioning activities and PolicyManager. |
| knox | KnoxPolicyAdapter and licence activation status; isolate Samsung-specific calls. |
| filter | ChromePolicyAdapter and CategoryFeedProvider; the final filtering engine is selected after the prototype. |
| work | PolicyReconcileWorker and feed refresh work after the device is unlocked. |

Use ViewModel per screen or feature, a repository that coordinates Room and backend access, and coroutines for work off the UI thread. Start with manual dependency construction in the Application class; introduce Hilt only if the project grows enough to benefit.

### Add dependencies gradually

Use Studio’s generated Compose dependencies and versions. Add stable Room runtime and Room Kotlin coroutine support, the Room compiler through KSP, WorkManager, Kotlin serialization and the chosen HTTP client. Use the current compatible stable versions from official documentation and store them in gradle/libs.versions.toml. Sync and run after each dependency group. [^1][^19]

Room entities store: the active approved policy and signature; its schema and version; your editable draft; request ID and summary; an application journal; and non-secret history. Save a draft after edits so it survives restarting the phone. Validate a signed policy before accepting it as active.

A minimal first Room entity can hold the last signed policy bytes. Keep its verification in ApprovalVerifier; database persistence is not authorization. This example illustrates the storage model rather than the complete schema:

```kotlin
@Entity(tableName = "active_policy")
data class ActivePolicyEntity(
    @PrimaryKey val slot: Int = 1,
    val version: Long,
    val signedPayload: String,
    val signature: String,
    val keyId: String
)

@Dao
interface PolicyDao {
    @Query("SELECT * FROM active_policy WHERE slot = 1")
    fun observe(): Flow<ActivePolicyEntity?>

    @Upsert
    suspend fun save(policy: ActivePolicyEntity)
}
```

Import Entity, PrimaryKey, Dao, Query and Upsert from androidx.room, and Flow from kotlinx.coroutines.flow. Add a RoomDatabase subclass listing your entities and DAO accessors. Export Room schemas into version control and write migrations when changing stored fields. Do not use destructive migrations in the release controller because losing policy and replay history changes its protection behaviour.

Keep device credentials encrypted in private storage using a key protected by Android Keystore. Room does not encrypt its database by default and is not a secure vault for plaintext OTPs or server secrets. Configure backup exclusions so restoring old data cannot silently replace current policy state or duplicate device credentials. [^20]

Persisted native policies should remain active without the UI running. On launch, reboot after unlock or app update, reconcile actual policy state against the last validated approved policy. WorkManager assists this reconciliation; it is not a continuously running enforcement engine.

## 10. Add the Android management shell

This shell registers the controller component. It does not grant device ownership, activate Knox or apply app blocking by itself. Keep the existing application and MainActivity entries generated by Studio.

Create admin/PhoneAdminReceiver.kt:

```kotlin
package be.anton.curatedphone.admin

import android.app.admin.DeviceAdminReceiver

class PhoneAdminReceiver : DeviceAdminReceiver()
```

Create android/app/src/main/res/xml/device_admin.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android=
    "http://schemas.android.com/apk/res/android">
    <uses-policies />
</device-admin>
```

In AndroidManifest.xml, add INTERNET directly under manifest. Add the receiver below inside application:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<!-- Place this receiver inside the application element. -->
<receiver
    android:name=".admin.PhoneAdminReceiver"
    android:exported="true"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name=
            "android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

BIND_DEVICE_ADMIN belongs on the receiver; it is not a runtime permission prompt. Add package visibility declarations for the app inventory feature. Start with launcher-intent queries and explicitly needed packages; if complete inventory is required, assess QUERY_ALL_PACKAGES and its distribution requirements. [^21]

Provisioning for Android 12 and later also needs activities handling ACTION_GET_PROVISIONING_MODE and ACTION_ADMIN_POLICY_COMPLIANCE, with the correct return results and manifest declarations. Implement these from the official provisioning contract and test on a clean device. Do not copy an old QR tutorial that only starts ACTION_PROVISION_MANAGED_DEVICE. [^22]

The status screen should display isDeviceOwnerApp(packageName), Knox activation status and the actual policy state. A green enabled switch is insufficient evidence. Policy calls must check return values, failed-package lists and available callbacks.

## 11. Prove device policies on a test device

Use a fresh emulator first. It must have no existing owner or conflicting account setup. Build the debug APK from android/ and install it. When several devices are connected, use adb -s followed by the serial from adb devices for every command.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner \
  be.anton.curatedphone/.admin.PhoneAdminReceiver
adb shell dumpsys device_policy
```

This is a development experiment on an eligible test device. It is not a way to promote an ordinary app on a fully configured personal phone. If the APK was produced by Studio’s Run flow and is marked testOnly, adb installation may require -t. Inspect eligibility errors rather than changing the phone blindly. [^5]

Test the controller’s own uninstall block and user-control protection. Then attempt uninstall, force-stop and clearing storage through Settings. In the native adapter, evaluate setUninstallBlocked for the controller and setUserControlDisabledPackages for its package. The latter requires API 30 or later. [^7]

For existing apps on the blocklist, use setPackagesSuspended where appropriate and inspect returned failures. Do not suspend apps merely because they are absent from a curated list. Keep a protected platform baseline for the launcher, dialer, permission controller, Play services, package-management dependencies and our controller; these are safety constraints, not an app allowlist. Test YouTube as a preinstalled app on the Samsung. [^7]

Consider restrictions on debugging, adding users, safe boot and normal Settings factory reset once daily operation is proven. The normal reset restriction does not prevent every hardware recovery wipe. Avoid broad USB data disabling because wired Android Auto may depend on it.

Do not activate the debugging restriction while depending on ADB to fix the prototype. First prove the email-approved removal route and signed APK updates. Use only debug-build escape tools on test devices; release builds must not contain a local bypass button.

Test DPC is a useful open-source reference for policy calls and provisioning. Keep our app’s interface and authorization rules separate from its demonstration UI. [^23]

## 12. Add Samsung Knox and app restrictions

1. Sign in to the Samsung Knox Developer portal. Download the SDK from SDK Tools > SDK Downloads.

2. Copy the current knoxsdk.jar into android/app/libs and add it to the app module as directed by Samsung’s current SDK packaging guidance.

3. Declare the licence and application-management permissions required by the selected APIs. Declaring KNOX_APP_MGMT alone does not grant its signature-level privileges.

4. Obtain a development key from License keys > My license keys, associate the correct APK where required, and activate it in the app. Wait for the activation callback before enabling Knox policy actions. [^6][^24]

KnoxPolicyAdapter should check Samsung support and licence state before every relevant operation. On a non-Samsung emulator it reports unsupported and allows UI testing with a fake adapter. It must never display success for an unsupported native call.

The installation prototype adds only the blocked package IDs using ApplicationPolicy.addAppPackageNameToBlackList. Removing an app from the list uses removeAppPackageNameFromBlackList after guardian approval. Do not use addAppPackageNameToWhiteList(packageName, true), which creates a wildcard default block. Persist the intended blocklist and reconcile additions and removals idempotently. Inspect existing rules so an old prototype wildcard or whitelist exception does not change the result. [^8][^35]

### Initial app blocklist

<!-- DEFAULT-BLOCKLIST-START -->

The five social/video apps below and every browser package in the following tables form one flat app blocklist. The table sections are documentation only; the app does not classify apps into categories.

| Initially blocked app | Expected package ID |
| --- | --- |
| Facebook | `com.facebook.katana` |
| Instagram | `com.instagram.android` |
| TikTok | `com.zhiliaoapp.musically` |
| YouTube | `com.google.android.youtube` |
| LinkedIn | `com.linkedin.android` |

Verify these expected package IDs on the actual Samsung before applying them. Match the listed packages precisely; a display name alone is insufficient. Lite versions, regional variants, repackaged apps and third-party clients with different IDs remain allowed unless explicitly added in a guardian-approved request. Blocking an app does not automatically add its website to the domain blocklist; website categories and custom domains remain separate rules.

### Default browser package blocklist

This inventory contains **195 distinct browser package IDs** identified during research on 11 October 2026. All are intended default blocked entries, including beta/nightly and older package variants. **Google Chrome stable (`com.android.chrome`) is deliberately excluded.**

The inventory is finite: it cannot guarantee coverage of every new, renamed, repackaged or self-built browser. Everything outside the exact app blocklist remains allowed. Adding an unknown browser is a normal guardian-approved app-list change. There is no app-category engine, automatic browser detection or remotely expanding browser feed.

A package appearing in a source establishes its identity, not that it is currently installable in Belgium or compatible with Android 16. Before initial enrolment, confirm installed-package identities and native enforcement on the Samsung. Absent packages can retain their installation-blocklist entries. If an API cannot apply an entry, report that failure instead of calling the entire policy applied.

#### Publisher and project package entries

The sources below are publisher Play listings, official project documentation or package repositories. Some are legacy or restricted testing channels; a listing is not a promise of current availability.

| Browser / variant | Package ID | Identity source |
| --- | --- | --- |
| Adblock Browser | `org.adblockplus.browser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.adblockplus.browser) |
| AdBlock Browser (ReachMore) | `app.reachmore.adblockbrowser` | [Source](https://play.google.com/store/apps/details?hl=fr&id=app.reachmore.adblockbrowser) |
| Adblock Browser Beta | `org.adblockplus.browser.beta` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.adblockplus.browser.beta) |
| Aloha | `com.aloha.browser` | [Source](https://play.google.com/store/apps/details?id=com.aloha.browser) |
| Aloha Beta | `com.alohamobile.browser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.alohamobile.browser) |
| Avast Secure Browser | `com.avast.android.secure.browser` | [Source](https://play.google.com/store/apps/details?id=com.avast.android.secure.browser) |
| AVG Secure Browser | `com.avg.android.secure.browser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.avg.android.secure.browser) |
| Berry Browser | `jp.ejimax.berrybrowser` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=jp.ejimax.berrybrowser) |
| Brave | `com.brave.browser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.brave.browser) |
| Brave Beta | `com.brave.browser_beta` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.brave.browser_beta) |
| Brave Nightly | `com.brave.browser_nightly` | [Source](https://play.google.com/store/apps/details?hl=en-US&id=com.brave.browser_nightly) |
| Ceno | `ie.equalit.ceno` | [Source](https://f-droid.org/en/packages/ie.equalit.ceno/) |
| Chrome Beta | `com.chrome.beta` | [Source](https://play.google.com/store/apps/details?id=com.chrome.beta) |
| Chrome Canary (Unstable) | `com.chrome.canary` | [Source](https://play.google.com/store/apps/details?id=com.chrome.canary) |
| Chrome Dev | `com.chrome.dev` | [Source](https://play.google.com/store/apps/details?id=com.chrome.dev) |
| ConsoleFlow | `space.karrarnazim.ConsoleFlow` | [Source](https://f-droid.org/en/packages/space.karrarnazim.ConsoleFlow/) |
| Dolphin | `mobi.mgeek.TunnyBrowser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=mobi.mgeek.TunnyBrowser) |
| DuckDuckGo | `com.duckduckgo.mobile.android` | [Source](https://play.google.com/store/apps/details/DuckDuckGo_Private_Browser?hl=en&id=com.duckduckgo.mobile.android) |
| Ecosia | `com.ecosia.android` | [Source](https://play.google.com/store/apps/details/Ecosia_Search_to_plant_trees?hl=nl&id=com.ecosia.android) |
| EinkBro | `info.plateaukao.einkbro` | [Source](https://f-droid.org/en/packages/info.plateaukao.einkbro/) |
| FAB / Free Adblocker Browser | `com.hsv.freeadblockerbrowser` | [Source](https://play.google.com/store/apps/details/FAB_Adblocker_Browser_Adblock?hl=en_GB&id=com.hsv.freeadblockerbrowser) |
| Fennec F-Droid | `org.mozilla.fennec_fdroid` | [Source](https://f-droid.org/en/packages/org.mozilla.fennec_fdroid/) |
| Firefox | `org.mozilla.firefox` | [Source](https://play.google.com/store/apps/details/?hl=en&id=org.mozilla.firefox) |
| Firefox Beta | `org.mozilla.firefox_beta` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.mozilla.firefox_beta) |
| Firefox Focus | `org.mozilla.focus` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.mozilla.focus) |
| Firefox Focus Beta | `org.mozilla.focus.beta` | [Source](https://firefox-source-docs.mozilla.org/mobile/android/focus-android/Release-tracks.html) |
| Firefox Focus Nightly | `org.mozilla.focus.nightly` | [Source](https://firefox-source-docs.mozilla.org/mobile/android/focus-android/Release-tracks.html) |
| Firefox Klar | `org.mozilla.klar` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.mozilla.klar) |
| Firefox Nightly | `org.mozilla.fenix` | [Source](https://play.google.com/store/apps/details?hl=nl&id=org.mozilla.fenix) |
| FOSS Browser | `de.baumann.browser` | [Source](https://f-droid.org/en/packages/de.baumann.browser/) |
| FREE Browser | `org.woheller69.browser` | [Source](https://f-droid.org/en/packages/org.woheller69.browser/) |
| Fulguris F-Droid | `net.slions.fulguris.full.fdroid` | [Source](https://f-droid.org/en/packages/net.slions.fulguris.full.fdroid/) |
| Fulguris Web Browser | `net.slions.fulguris.full.playstore` | [Source](https://play.google.com/store/apps/details?hl=en_GB&id=net.slions.fulguris.full.playstore) |
| Herond | `com.herond.android.browser` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=com.herond.android.browser) |
| HeyTap Internet Browser | `com.heytap.browser` | [Source](https://play.google.com/store/apps/details?hl=en&id=com.heytap.browser) |
| IdiomReplaceX Browser | `de.idiomreplacex.browser_app` | [Source](https://f-droid.org/en/packages/de.idiomreplacex.browser_app/) |
| Incognito Browser - Go Private | `com.androidbull.incognito.browser` | [Source](https://play.google.com/store/apps/details?id=com.androidbull.incognito.browser) |
| IronFox | `org.ironfoxoss.ironfox` | [Source](https://ironfoxoss.org/download/) |
| IronFox Nightly | `org.ironfoxoss.ironfox.nightly` | [Source](https://ironfoxoss.org/download/) |
| jQuarks | `com.oF2pks.jquarks` | [Source](https://f-droid.org/en/packages/com.oF2pks.jquarks/) |
| Lemur | `com.lemurbrowser.exts` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.lemurbrowser.exts) |
| Lemur Beta | `com.lemurbrowser.exts.beta` | [Source](https://play.google.com/store/apps/details?id=com.lemurbrowser.exts.beta) |
| LibreLynx Lite | `com.librelynx.lite` | [Source](https://f-droid.org/en/packages/com.librelynx.lite/) |
| Lightning | `acr.browser.lightning` | [Source](https://f-droid.org/en/packages/acr.browser.lightning/) |
| Mi Browser | `com.mi.globalbrowser` | [Source](https://play.google.com/store/apps/details/Mi_Browser?hl=nl&id=com.mi.globalbrowser) |
| Microsoft Edge | `com.microsoft.emmx` | [Source](https://play.google.com/store/apps/details/Microsoft_Edge_Web_Browser?hl=nl&id=com.microsoft.emmx&listing=shopping) |
| Microsoft Edge Beta | `com.microsoft.emmx.beta` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.microsoft.emmx.beta) |
| Microsoft Edge Canary | `com.microsoft.emmx.canary` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.microsoft.emmx.canary) |
| Microsoft Edge Dev | `com.microsoft.emmx.dev` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=com.microsoft.emmx.dev) |
| Midori | `org.midorinext.android` | [Source](https://play.google.com/store/apps/details?hl=en_IE&id=org.midorinext.android) |
| Mint Browser | `com.mi.globalbrowser.mini` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.mi.globalbrowser.mini) |
| Mises Browser | `site.mises.browser_stable` | [Source](https://play.google.com/store/apps/details?id=site.mises.browser_stable) |
| Mises Browser (older package) | `site.mises.browser` | [Source](https://play.google.com/store/apps/details?hl=en-US&id=site.mises.browser) |
| monocles browser | `de.monocles.browser` | [Source](https://f-droid.org/en/packages/de.monocles.browser/) |
| Mull (legacy) | `us.spotco.fennec_dos` | [Source](https://monitor.f-droid.org/anti-feature/Tracking) |
| Naver Whale Browser | `com.naver.whale` | [Source](https://play.google.com/store/apps/details?id=com.naver.whale) |
| OH Browser - One Handed | `com.oh.bro` | [Source](https://play.google.com/store/apps/details?id=com.oh.bro) |
| OH Private Browser - Minimal | `com.oh.brop` | [Source](https://play.google.com/store/apps/details?hl=en_GB&id=com.oh.brop) |
| Opera | `com.opera.browser` | [Source](https://play.google.com/store/apps/details?hl=en&id=com.opera.browser) |
| Opera Beta | `com.opera.browser.beta` | [Source](https://play.google.com/store/apps/details/Opera_browser_beta?hl=en_GB&id=com.opera.browser.beta) |
| Opera GX: Gaming Browser | `com.opera.gx` | [Source](https://play.google.com/store/apps/details?id=com.opera.gx) |
| Opera Mini | `com.opera.mini.native` | [Source](https://play.google.com/store/apps/details/?hl=nl&id=com.opera.mini.native) |
| Opera Mini Beta | `com.opera.mini.native.beta` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.opera.mini.native.beta) |
| Phoenix | `com.transsion.phoenix` | [Source](https://play.google.com/store/apps/details?hl=fr&id=com.transsion.phoenix) |
| Privacy Browser Standard | `com.stoutner.privacybrowser.standard` | [Source](https://f-droid.org/en/packages/com.stoutner.privacybrowser.standard/) |
| Private Internet Browser | `com.lechneralexander.privatebrowser` | [Source](https://play.google.com/store/apps/details?id=com.lechneralexander.privatebrowser) |
| Puffin Cloud Browser | `com.cloudmosa.puffinCloudBrowser` | [Source](https://play.google.com/store/apps/details?id=com.cloudmosa.puffinCloudBrowser) |
| Puffin TV Browser | `com.cloudmosa.puffinTV` | [Source](https://play.google.com/store/apps/details?id=com.cloudmosa.puffinTV) |
| Puffin Web Browser | `com.cloudmosa.puffinFree` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.cloudmosa.puffinFree) |
| Quetta | `net.quetta.browser` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=net.quetta.browser) |
| Qwant | `com.qwant.liberty` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.qwant.liberty) |
| Samsung Internet / Samsung Browser | `com.sec.android.app.sbrowser` | [Source](https://play.google.com/store/apps/details?id=com.sec.android.app.sbrowser) |
| Samsung Internet / Samsung Browser Beta | `com.sec.android.app.sbrowser.beta` | [Source](https://play.google.com/store/apps/details?id=com.sec.android.app.sbrowser.beta) |
| Seabolt Fast & Private Browser | `com.amizo.seabolt` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=com.amizo.seabolt) |
| SmartCookieWeb | `com.cookiegames.smartcookie` | [Source](https://f-droid.org/en/packages/com.cookiegames.smartcookie/) |
| Solipsism Browser | `com.krystelligence.solipsism` | [Source](https://f-droid.org/en/packages/com.krystelligence.solipsism/) |
| Soul Browser | `com.mycompany.app.soulbrowser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.mycompany.app.soulbrowser) |
| Stargon Browser | `net.onecook.browser` | [Source](https://play.google.com/store/apps/details?hl=en&id=net.onecook.browser) |
| Startpage | `com.startpage.app` | [Source](https://play.google.com/store/apps/details?id=com.startpage.app) |
| Styx / Sec Web Browser | `com.xz.styx` | [Source](https://play.google.com/store/apps/details?hl=en_NZ&id=com.xz.styx) |
| Tor Browser | `org.torproject.torbrowser` | [Source](https://play.google.com/store/apps/details?id=org.torproject.torbrowser) |
| Tor Browser (Alpha) | `org.torproject.torbrowser_alpha` | [Source](https://play.google.com/store/apps/details?id=org.torproject.torbrowser_alpha) |
| UC Browser | `com.UCMobile.intl` | [Source](https://play.google.com/store/apps/details?id=com.UCMobile.intl&listing=uc_drive) |
| UC Mini | `com.uc.browser.en` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=com.uc.browser.en) |
| UC Turbo | `com.ucturbo` | [Source](https://play.google.com/store/apps/details?hl=en&id=com.ucturbo) |
| Via | `mark.via.gp` | [Source](https://play.google.com/store/apps/details?hl=nl&id=mark.via.gp) |
| Vivaldi | `com.vivaldi.browser` | [Source](https://play.google.com/store/apps/details?hl=en&id=com.vivaldi.browser) |
| Vivaldi Snapshot | `com.vivaldi.browser.snapshot` | [Source](https://play.google.com/store/apps/details?hl=en-US&id=com.vivaldi.browser.snapshot) |
| Waterfox | `net.waterfox.android.release` | [Source](https://play.google.com/store/apps/details?hl=en_GB&id=net.waterfox.android.release) |
| WebLibre | `eu.weblibre.gecko` | [Source](https://f-droid.org/en/packages/eu.weblibre.gecko/) |
| X Browser | `com.xbrowser.play` | [Source](https://play.google.com/store/apps/details?id=com.xbrowser.play) |
| X Browser - Video Downloader | `com.smarttools.xbrowser.videodownloader` | [Source](https://play.google.com/store/apps/details?id=com.smarttools.xbrowser.videodownloader) |
| x Browser Pro | `org.superfast.xbrowser` | [Source](https://play.google.com/store/apps/details?hl=en_US&id=org.superfast.xbrowser) |
| xBrowser - Video Downloader HD | `com.xbrowser.video.browserdownloader.downloader` | [Source](https://play.google.com/store/apps/details?id=com.xbrowser.video.browserdownloader.downloader) |
| XO Browser Pro | `com.microgrezm.xbrowser` | [Source](https://play.google.com/store/apps/details?id=com.microgrezm.xbrowser) |
| Yandex Alpha | `com.yandex.browser.alpha` | [Source](https://play.google.com/store/apps/details?hl=en&id=com.yandex.browser.alpha) |
| Yandex Beta | `com.yandex.browser.beta` | [Source](https://play.google.com/store/apps/details?id=com.yandex.browser.beta) |
| Yandex Browser | `com.yandex.browser` | [Source](https://play.google.com/store/apps/details?hl=nl&id=com.yandex.browser) |
| Yandex Browser Lite | `com.yandex.browser.lite` | [Source](https://play.google.com/store/apps/details?id=com.yandex.browser.lite) |

#### Additional compatibility-catalog entries

These additional identifiers occur in AdGuard’s Android browser compatibility catalog. Short family labels identify the namespace rather than guarantee the current marketing name. Keep them in the initial blocklist as legacy/alternate coverage, with package-level validation during the enforcement prototype. Add-ons, licence keys, browser engines, launchers, general news clients and the allowed stable Chrome package have been excluded.

| Family | Package ID | Identity source |
| --- | --- | --- |
| 360 | `com.qihoo.contents` | [Catalog][browser-catalog] |
| Amigo | `ru.mail.amigo` | [Catalog][browser-catalog] |
| AOSP | `com.android.browser` | [Catalog][browser-catalog] |
| APUS | `com.apusapps.browser` | [Catalog][browser-catalog] |
| APUS | `com.apusapps.browser.turbo` | [Catalog][browser-catalog] |
| Armorfly | `com.cmcm.armorfly` | [Catalog][browser-catalog] |
| ASUS | `com.asus.browser` | [Catalog][browser-catalog] |
| Atlas | `nextapp.atlas` | [Catalog][browser-catalog] |
| Baidu | `com.baidu.browser.inter` | [Catalog][browser-catalog] |
| Baidu | `com.baidu.browserhd.inter` | [Catalog][browser-catalog] |
| Boat | `com.boatbrowser.free` | [Catalog][browser-catalog] |
| Boat | `com.boatbrowser.tablet` | [Catalog][browser-catalog] |
| Boat | `com.boatgo.browser` | [Catalog][browser-catalog] |
| Brave | `com.brave.browser_default` | [Catalog][browser-catalog] |
| Brave | `com.brave.browser_dev` | [Catalog][browser-catalog] |
| Bromite | `org.bromite.bromite` | [Catalog][browser-catalog] |
| Browser | `com.apn.mobile.browser` | [Catalog][browser-catalog] |
| Browser | `com.appdream.cloud` | [Catalog][browser-catalog] |
| Browser | `com.browser.Speed` | [Catalog][browser-catalog] |
| Browser | `com.droid.browser` | [Catalog][browser-catalog] |
| Browser | `com.explore.web.browser` | [Catalog][browser-catalog] |
| Browser | `com.swe.atego.browser` | [Catalog][browser-catalog] |
| Browser | `explore.web.browser` | [Catalog][browser-catalog] |
| Browser | `internetexplorer.browser.webexplorer` | [Catalog][browser-catalog] |
| Browser | `me.android.browser` | [Catalog][browser-catalog] |
| Browser | `net.fast.web.browser` | [Catalog][browser-catalog] |
| Browser | `org.easyweb.browser` | [Catalog][browser-catalog] |
| Chromium | `org.chromium.chrome` | [Catalog][browser-catalog] |
| CM | `com.ksmobile.cb` | [Catalog][browser-catalog] |
| Cobra | `com.callsoft.cobrabrowser` | [Catalog][browser-catalog] |
| Cromite | `org.cromite.cromite` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.browser` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.browser.android.elex` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.browser.express.web` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.browser.id` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.browser.zero` | [Catalog][browser-catalog] |
| Dolphin | `com.dolphin.web.browser.android` | [Catalog][browser-catalog] |
| Fennec | `org.mozilla.fennec` | [Catalog][browser-catalog] |
| Fennec | `org.mozilla.fennec_aurora` | [Catalog][browser-catalog] |
| Firefox | `cn.mozilla.firefox` | [Catalog][browser-catalog] |
| Firefox | `org.mozilla.fenix.nightly` | [Catalog][browser-catalog] |
| FirePhoenix | `com.vionika.firephoenix` | [Catalog][browser-catalog] |
| FlashFox | `mobi.browser.flashfox` | [Catalog][browser-catalog] |
| FlashFox | `mobi.browser.flfoxpro` | [Catalog][browser-catalog] |
| Gello | `org.cyanogenmod.gello.browser` | [Catalog][browser-catalog] |
| Ghostery | `com.ghostery.android.ghostery` | [Catalog][browser-catalog] |
| GO | `com.jiubang.browser` | [Catalog][browser-catalog] |
| Habit | `jp.ddo.pigsty.HabitBrowser` | [Catalog][browser-catalog] |
| Habit | `jp.ddo.pigsty.Habit_Browser` | [Catalog][browser-catalog] |
| HTC | `com.htc.sense.browser` | [Catalog][browser-catalog] |
| Huawei | `com.huawei.browser` | [Catalog][browser-catalog] |
| IceCat | `org.gnu.icecat` | [Catalog][browser-catalog] |
| Iceraven | `io.github.forkmaintainers.iceraven` | [Catalog][browser-catalog] |
| InBrowser | `nu.tommie.inbrowser` | [Catalog][browser-catalog] |
| Iron | `org.iron.srware` | [Catalog][browser-catalog] |
| Javelin | `com.nubelacorp.javelin` | [Catalog][browser-catalog] |
| Jbak | `com.jbak.superbrowser` | [Catalog][browser-catalog] |
| Kiwi | `com.kiwibrowser.browser` | [Catalog][browser-catalog] |
| LinkBubble | `com.linkbubble.playstore` | [Catalog][browser-catalog] |
| Maxthon | `com.mx.browser` | [Catalog][browser-catalog] |
| Maxthon | `com.mx.browser.tablet` | [Catalog][browser-catalog] |
| Mercury | `com.ilegendsoft.mercury` | [Catalog][browser-catalog] |
| Mozilla | `org.mozilla.reference.browser` | [Catalog][browser-catalog] |
| Mulch | `us.spotco.mulch` | [Catalog][browser-catalog] |
| Naked | `com.fevdev.nakedbrowser` | [Catalog][browser-catalog] |
| Naked | `com.fevdev.nakedbrowserpro` | [Catalog][browser-catalog] |
| Ninesky | `com.ninesky.browser` | [Catalog][browser-catalog] |
| NoChromo | `com.metalasfook.nochromo` | [Catalog][browser-catalog] |
| Opera | `com.opera.browser.classic` | [Catalog][browser-catalog] |
| Opera | `com.opera.browser.yandex` | [Catalog][browser-catalog] |
| Opera | `com.opera.browses` | [Catalog][browser-catalog] |
| Opera | `com.opera.mini.android` | [Catalog][browser-catalog] |
| Opera | `com.opera.mini.android.yandex` | [Catalog][browser-catalog] |
| Opera | `com.opera.touch` | [Catalog][browser-catalog] |
| Privacy | `com.stoutner.privacybrowser.free` | [Catalog][browser-catalog] |
| Puffin | `com.cloudmosa.puffin` | [Catalog][browser-catalog] |
| Pyrope | `com.ninnix96.pyrope.browser` | [Catalog][browser-catalog] |
| QQ | `com.tencent.ibibo.mtt` | [Catalog][browser-catalog] |
| QQ | `com.tencent.mtt` | [Catalog][browser-catalog] |
| Quark | `com.quark.browser` | [Catalog][browser-catalog] |
| Rocket | `com.rocket.browser` | [Catalog][browser-catalog] |
| Silk | `com.amazon.cloud9` | [Catalog][browser-catalog] |
| Sleipnir | `jp.co.fenrir.android.sleipnir` | [Catalog][browser-catalog] |
| Sleipnir | `jp.co.fenrir.android.sleipnir_black` | [Catalog][browser-catalog] |
| Sleipnir | `jp.co.fenrir.android.sleipnir_test` | [Catalog][browser-catalog] |
| Thorium | `org.chromium.thorium` | [Catalog][browser-catalog] |
| Tint | `org.tint` | [Catalog][browser-catalog] |
| UC | `com.UCMobile.intl.x86` | [Catalog][browser-catalog] |
| UC | `com.uc.browser.hd` | [Catalog][browser-catalog] |
| Via | `mark.via` | [Catalog][browser-catalog] |
| Vivaldi | `com.vivaldi.browser.sopranos` | [Catalog][browser-catalog] |
| WJBrowser | `com.feedback.browser.wjbrowser` | [Catalog][browser-catalog] |
| XBrowser | `com.mmbox.xbrowser.pro` | [Catalog][browser-catalog] |
| YuBrowser | `com.mokee.yubrowser` | [Catalog][browser-catalog] |
| Yuzu | `jp.ejimax.yuzubrowser` | [Catalog][browser-catalog] |
| Yuzu | `jp.hazuki.yuzubrowser` | [Catalog][browser-catalog] |

<!-- DEFAULT-BLOCKLIST-END -->

[browser-catalog]: https://github.com/AdguardTeam/CompatibilityIssues/blob/master/android/browsers.txt

#### Turn the fixed list into an Android asset

Copy this Markdown file into the repository as `docs/implementation-plan.md`. Create `scripts/export-default-blocklist.mjs` with the following code. It extracts only the marked initial-blocklist tables and produces a single flat JSON asset; it does not perform runtime browser detection or download an app-category list.

```javascript
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";

const plan = readFileSync("docs/implementation-plan.md", "utf8");
const start = "<!-- " + "DEFAULT-BLOCKLIST-START -->";
const end = "<!-- " + "DEFAULT-BLOCKLIST-END -->";
if (plan.split(start).length !== 2 || plan.split(end).length !== 2) {
  throw new Error("Expected exactly one marked default blocklist");
}
const section = plan.split(start)[1].split(end)[0];
const ids = section.split("\n").flatMap((line) => {
  if (!line.startsWith("| ")) return [];
  const match = line.match(/\|\s*`([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)`\s*\|/);
  return match ? [match[1]] : [];
});
const blockedAppPackageIds = [...new Set(ids)].sort();
if (!blockedAppPackageIds.length || blockedAppPackageIds.includes("com.android.chrome")) {
  throw new Error("Invalid seed: empty list or stable Chrome blocked");
}
const dir = "android/app/src/main/assets";
mkdirSync(dir, { recursive: true });
writeFileSync(`${dir}/default-blocked-apps.json`, JSON.stringify({
  seedVersion: 1,
  appPolicyMode: "blocklist",
  blockedAppPackageIds,
}, null, 2) + "\n");
console.log(`Exported ${blockedAppPackageIds.length} blocked package IDs`);
```

Run from the repository root after creating the Android project:

```bash
mkdir -p docs scripts
node scripts/export-default-blocklist.mjs
```

With the current inventory, the generated asset contains **200 blocked package IDs**: five social/video apps plus the browser entries. Kotlin reads this bundled asset only to prefill the initial draft; Convex validates the exact submitted policy and the primary guardian approves it. The approved snapshot is then applied through Knox and Android device policy.

Once onboarding is locked, a later APK or seed-file update must never overwrite the active approved list. A seed revision can propose a visible draft diff, but any changed package entries still need primary-guardian OTP approval. Website category-feed maintenance remains a separate mechanism.

#### Browser acceptance checks

- Stable Chrome launches, updates and follows the approved domain rules.
- Installed Samsung Internet is suspended; attempting to reinstall its blocked package is refused.
- A blocked alternative and a blocked beta/nightly variant cannot install through Play, Galaxy Store or APK sideloading.
- A previously unknown non-blocked app installs without OTP, preserving default-allow behaviour.
- A guardian-approved browser unblock permits that exact package and leaves other entries active.
- Links from Belfius, itsme, WhatsApp and other essential apps still reach an appropriate allowed browser or their normal app flow.
- Android System WebView, Google Play services, the launcher and other required platform components remain enabled; the browser list must not be applied to similarly named system components.
- A newly discovered browser requires a visible package-specific request; no category or automatic rule is introduced.

### Compatibility test apps

| App | Expected package ID |
| --- | --- |
| Belfius Mobile | `be.belfius.directmobile.android` |
| itsme | `be.bmid.itsme` |
| WhatsApp | `com.whatsapp` |
| Waze | `com.waze` |
| Google Maps | `com.google.android.apps.maps` |
| Google Chrome | `com.android.chrome` |
| Android Auto | `com.google.android.projection.gearhead` |
| Our controller | `be.anton.curatedphone` |

Confirm all package IDs against the installed phone inventory. Package IDs, not display names, identify app rules. The compatibility apps above are test cases, not an allowlist: they and every other non-blocked app remain permitted. Reject attempts to block protected controller or essential platform packages. Keep those protections separate from the editable blocklist.

Test blocked installations through Play, Galaxy Store and APK sideloading, plus installation of a previously unknown non-blocked app without OTP. Suspend installed blocked apps separately; uninstalling them must not remove their installation-blocklist entries. Verify reboot, update and reinstall behaviour. Test ordinary non-blocked app and controller updates. A store download may finish before Knox rejects installation; do not confuse download availability with installation success. [^36]

Keeping stock firmware and Google services is our compatibility approach. Banking and itsme still require real functional tests; management should not be presented as a guarantee of their support. Test activation and authentication without recording bank credentials or approving real transfers as test data. [^25][^26]

## 13. Build the Convex data model and endpoints

Convex is the only backend service we write. Use HTTP actions for the phone API, internal actions for cryptography and integrations, internal mutations for atomic database changes, and internal queries for reads. HTTP actions live in convex/http.ts and are exposed on the deployment’s convex.site URL. Internal-only policy mutation functions prevent clients bypassing approval. [^3]

| Collection | Important fields |
| --- | --- |
| devices | deviceId, tokenDigest, platform metadata, status, approvedPolicyVersion, appliedPolicyVersion, lastSeenAt. |
| guardians | deviceId, role primary or recovery, email, verifiedAt, guardianVersion. |
| policies | deviceId, version, schemaVersion, appPolicyMode blocklist, blockedAppPackageIds, categories, feed IDs, custom domains, content digest. |
| changeRequests | deviceId, requestId, purpose, base versions, immutable proposed snapshot, server-generated diff, digest, status. |
| otpChallenges | requestId, generation, recipient role, code HMAC, expiresAt, attempts, consumedAt, email job ID. |
| approvals | requestId, approvalId, signed payload, keyId, target version, issuance and application status. |
| auditEvents | deviceId, requestId, event type, timestamp and redacted result; never plaintext codes. |
| categoryFeeds | source, category, version, licence metadata, checksum, fetch status and last successful update. |

Add indexes on deviceId, requestId and relevant status fields. Store full immutable snapshots for the small personal policy; server-generated diffs are for review. The backend must recompute differences rather than trust an app-supplied description.

Use appPolicyMode: "blocklist" and blockedAppPackageIds: string[] in the backend and signed policy snapshot. Sort and deduplicate IDs before hashing; compute email changes as "Block app" and "Unblock app". Kotlin may expose blockedAppPackageIds as Set<String>. Do not silently reinterpret an old allowlist field as a blocklist: use a new schema version and reject unsupported payloads. The default allow behaviour applies only after a valid initial policy exists.

| Endpoint | Purpose |
| --- | --- |
| POST /device/pair | Consume a short-lived one-time pairing code and register the phone. |
| GET /device/state | Return that device’s approved policy and request status. |
| POST /requests | Create an immutable batch and issue a guardian challenge. |
| POST /requests/verify | Verify the OTP and return or recover the signed approval. |
| POST /requests/resend | Issue a new challenge generation under rate limits. |
| POST /requests/cancel | Cancel a pending request; cancellation cannot undo already approved changes. |
| POST /device/applied | Record the native application result and applied policy version. |
| POST /resend-webhook | Receive provider events with verified webhook signatures. |

Purpose is settings, replace_primary, replace_backup or remove_management. Backup authorization is accepted only for a standalone replace_primary request. A batch with that replacement plus an app change must be rejected on the recovery route.

## 14. Device authentication and email approval security

### Pair the phone once

For this one-device MVP, use a random per-device bearer credential over HTTPS. A setup-only internal function creates a short-lived pairing code. The phone exchanges it once; the server issues a 256-bit random device token and stores only its HMAC digest. The phone encrypts the token using an Android Keystore key. The device ID and backend URL are identifiers, not secrets.

Every device endpoint validates the credential, device status and ownership of the referenced request. It must not accept arbitrary device IDs. The credential lets the phone submit and inspect requests; it never permits guardian-email edits or policy changes without approval. Re-enrolment requires a deliberate new pairing process.

A separate signing key in Convex signs approvals. Keep its public verification key in the release app and identify it by keyId. Using a device token does not replace signed approvals. Key rotation requires a planned update and trusted transition.

### Proposed OTP defaults

Use an eight-digit code, a ten-minute lifetime, five failed attempts per challenge, a sixty-second resend interval and at most five sends per hour per device and recipient. These are proposed initial values, not Android requirements. Apply a rolling verification-attempt budget as well, so resending does not reset brute-force limits.

Generate codes with a cryptographically secure random generator in a Convex action, such as Node crypto.randomInt. Store an HMAC over request ID, generation, recipient role and code with a server-only secret. Plain hashing of a short numeric code is inadequate protection against offline guessing.

Keep Node crypto work in a use node action file. Queries and mutations run in Convex’s default runtime; the action calls internal mutations for state changes. Validate expiry using backend time, not the phone clock. [^27]

An atomic verification mutation checks the challenge generation, expiry, attempts, request state and base versions, then records failure or consumes the challenge. Return an invalid-code result after recording a failed attempt; throwing an exception would roll back that attempt increment. Concurrent verification cannot create two approvals. [^28]

Handle resend races: each new code has a new generation, old generations become invalid, and delayed older emails remain harmless. Do not return the OTP from a phone endpoint, include it in notification text, or log it. The delivery provider will see the email contents, and its stored email records need a cleanup policy.

## 15. Exact batch approval and applying a policy

### Draft and submission

The phone edits a DraftPolicy. Editing does not change native restrictions. Review shows additions and removals of apps, category changes, domain additions and removals, and any guardian changes. The backend validates the proposed snapshot and creates a request against the current policy and guardian versions.

Send the primary guardian a complete server-generated diff, request ID, phone label, request purpose, expiry and OTP. For example: 'Block Instagram; unblock LinkedIn; block example.org; turn off video and streaming blocking.' Include app package IDs as secondary detail so similarly named apps cannot be substituted. Put the full diff in the email body without truncation; reject oversized requests before submission.

Submitting a revised draft creates a new request and invalidates the previous pending request. Editing offline only changes the local draft; it cannot cancel a server request until connectivity returns. Keep one pending batch per device initially. If another approval changes a base policy or guardian version, reject the stale request and ask the user to review again.

### Signed payload

After successful verification, the backend records authorization and generates a signed payload with deviceId, requestId, approvalId, purpose, request digest, base policy and guardian versions, target versions, schemaVersion, intended settings, issuance and expiry. Sign defined UTF-8 bytes and transmit the same bytes for verification. Use a standard signature algorithm such as ECDSA P-256 with SHA-256 and document its encoding; test Node-to-Android signature interoperability.

Kotlin verifies the pinned public key, signature, device and purpose, exact request digest, versions, supported schema and replay history. Never apply a locally edited draft just because a Boolean isApproved is true. Use the signed snapshot.

### Application and retries

Backend authorization and phone enforcement are separate states. The server cannot atomically apply Android APIs. Save the verified approval and an application journal locally, apply the policy in a tested order, read back the actual state and acknowledge success. Display 'Approved, applying' until native checks pass.

Use a retryable application process keyed by approvalId. A crash after changing one policy should resume the same operation, not require a new OTP or open general settings. Reconcile partially applied rules to the authorized target; do not silently revert to less restrictive settings.

Keep approvedPolicyVersion and appliedPolicyVersion distinct on the server. Return the same recorded approval when verification responses are lost. Prevent a new conflicting request while the previous policy is still applying. After completion, record the consumed approval locally and reject replay or older signed snapshots.

Offline operation uses the last verified approved snapshot and persisted OS policies. Missing local data must not be interpreted as a deliberate empty blocklist or a reason to clear native restrictions. A valid guardian-approved empty app blocklist permits all apps under this policy; missing or corrupt policy data is a different state.

## 16. Guardian changes onboarding and removal

### First setup

During the single initial setup, prefill the app blocklist with Facebook, Instagram, TikTok, YouTube, LinkedIn and the fixed browser package inventory from section 12. Also prefill the agreed website categories and any custom domains. Register both guardian addresses and prove each inbox is reachable by verification email. Send the complete starting policy to the primary guardian for approval. Lock the bootstrap setup route after the first approved policy and successful enrolment; reinstalling or clearing data must not reopen a free configuration path.

### Normal email changes

Replacing the primary address requires approval sent to the current primary inbox and verification of the proposed new inbox before activation. The new-inbox verification is proof of reachability; it does not authorize the change. Replacing the backup address likewise requires current-primary approval and verification of the proposed backup.

Bind both addresses to the exact submitted request. Once changed, increment guardianVersion and invalidate outstanding challenges tied to previous addresses. Notify relevant old and new addresses about the completed change without granting additional approval powers.

### Lost primary email

A separate recovery request asks the pre-registered backup to approve only the replacement of the primary address. The recovery email shows the old and proposed new primary addresses. Verify the proposed new inbox, consume the backup challenge and invalidate pending primary approvals. Notify the old primary even if it is expected to be inaccessible.

The system cannot prove that an inbox was actually lost. Its enforceable rule is that only the pre-registered backup can approve the narrowly defined recovery purpose. If both inboxes are lost, there is no app-level third recovery method in the agreed scope. Hardware reset remains an ownership-level possibility.

### Approved removal

Removal is a standalone request stating that management and filtering will end. It requires the primary guardian, not the backup. A signed remove_management approval cannot authorize unrelated changes.

Before releasing ownership, stop filtering, unsuspend apps we suspended, restore managed Chrome settings, remove Knox installation restrictions, remove user restrictions we set, and remove controller protections. Keep a journal so interrupted cleanup can resume.

Only after verified cleanup should the controller attempt the tested ownership-release mechanism. The deprecated clearDeviceOwnerApp API is not a guaranteed production removal solution. Do not automatically invoke wipeData as a fallback: that erases the phone and requires a separate explicit user decision. [^7]

Mark the device retired in Convex after release. Ordinary uninstall can then proceed if ownership release worked. Daily deployment is blocked until this path, including a failure partway through cleanup, has been exercised on the Samsung.

## 17. Chrome website filtering

The requested behaviour is domain blocking in Chrome, including category lists and custom domains. Domain rules cover a named host and its intended subdomains. They do not classify individual videos or social features embedded inside an otherwise permitted website.

Start by proving one blocked domain and one allowed domain through managed Chrome policy. Check chrome://policy and actual page navigation, including Incognito and Chrome Custom Tabs. Under the default-allow app policy, another browser can be installed unless its package is explicitly blocked. Chrome-only filtering does not cover that browser or another app’s embedded webview. The browser packages listed in section 12 are explicitly included in the initial app blocklist. There is no browser category rule or automatic discovery; any unlisted browser remains allowed until its exact package is added through guardian approval. [^9]

Chrome URLBlocklist has a 1,000-entry limit. Do not silently truncate feeds. The implementation must visibly report effective rule count and failed application. Large category support requires another tested mechanism.

### Candidate for large feeds

Evaluate a Chrome-only local VPN using Android VpnService, with com.android.chrome as its allowed application. Android supports per-app routing while other apps keep ordinary networking. This is a candidate, not a complete implementation: the app must forward permitted traffic as well as block domains. Creating a VPN interface alone is not a working filter. [^29]

Test secure DNS, Android Private DNS, DNS caching, IPv6, QUIC, reconnects, VPN termination, reboot and Wi-Fi-to-mobile transitions. DNS-only filtering must control encrypted DNS or it can be bypassed. A VPN occupies the device’s VPN slot and may conflict with another VPN.

Do not enable blanket VPN lockdown without checking its effect on every non-Chrome app. Standard lockdown combined with per-app routing can block other apps’ networking; this conflicts with the required banking, navigation and messaging behaviour. Choose a tested combination of policy and routing, or revise the design explicitly. [^30]

Convex can distribute feed metadata and approved configuration, but it should not proxy browser traffic. The network filter belongs on the phone or a purpose-built filtering service. No TLS interception or installation of a root certificate is part of this scope.

### Feed requirements

Select a maintained source covering the four approved categories, with acceptable redistribution terms, download size and update frequency. Store source URL, category, version, checksum, fetch time and licence. Use last-known-good data when an update fails; never replace a failed download with an empty blocklist.

Define CategoryFeedProvider now and select the actual source during the prototype. The initial policy approves sources and categories; changing them needs OTP approval. Feeds can contain mistakes and omissions. Any future exception for a wrongly blocked domain must also be approved.

## 18. Screens and daily behaviour

| Screen | Required content |
| --- | --- |
| Status | Device ownership and Knox state; active and applied versions; filter state; last successful sync; pending approval or failure. |
| Blocked apps | Search installed apps and add or remove package IDs in the draft blocklist. Show blocked apps, permitted apps and protected platform packages. Support a verified package ID for an app not currently installed. |
| Websites | Four category toggles, feed source and update status, custom domains, effective rule count and draft changes. |
| Guardians | Primary and backup address and role, draft address changes, and the separate recovery path. |
| Review | Exact batch diff, recipients, request purpose and Submit request. |
| OTP | Request summary, code field, expiry, retry and resend status; submission does not unlock general settings. |
| History | Submitted, cancelled, authorized, applied and failed operations, without storing plaintext codes. |
| Removal | Plain explanation that restrictions will end and how management will be released; standalone request. |

Keep the app simple and functional. You use the normal Samsung home screen and launch apps as usual. Opening the controller shows current status. Only the draft is editable freely; active controls change through signed approval.

If an app is blocked, let Android or Knox show its managed restriction state. A Chrome policy block uses Chrome’s own blocked-page behaviour. If we adopt network filtering, browser errors may be less descriptive; show filter diagnostics inside our app rather than promise a custom Chrome page.

If email delivery fails, keep the request pending or failed and explain that approval has not occurred. If OTP verification succeeds but a native rule fails, report 'Approved but not fully applied' with the actual failure. Never treat approval as proof that enforcement succeeded.

Basic phone calls, notifications, Bluetooth, location permissions and ordinary device settings should remain usable except for controls needed to enforce the agreed restrictions. Avoid aggressive kiosk or lock-task mode because it can disrupt app switching and Android Auto.

## 19. Tests and acceptance criteria

Write tests for authorization rules and failure handling, then run device tests for actual enforcement. UI previews and tests of simple getters are not substitutes for those checks. Use JUnit on Android and Vitest with convex-test for backend transaction rules.

| Test | Passing result |
| --- | --- |
| Batch approval | Email diff matches the immutable submitted snapshot; editing requires a new request. |
| OTP validity | Wrong, expired, used or previous-generation codes cannot authorize. Failed attempts persist. |
| Concurrent verification | Two simultaneous valid submissions produce one authorization. |
| Recovery role | Backup cannot approve app changes, category changes, backup replacement or removal. |
| Device isolation | Unauthenticated calls and a credential for another device cannot inspect or change this device. |
| Signature validation | Changed payload, wrong device, old version, unknown key and unsupported schema are rejected. |
| Retry after crash | An interrupted apply resumes the same authorized operation without general unlock. |
| Install enforcement | Listed packages fail installation through Play, Galaxy Store and sideloading; a new non-blocked app installs without OTP. |
| Existing apps | Installed listed apps cannot launch, including preinstalled YouTube if present; unlisted apps and essential phone functions remain usable. |
| Updates | Non-blocked app, controller and required platform updates work; updating a blocked app cannot make it usable. |
| Controller protection | Settings cannot uninstall, force-stop or clear its data; debug and alternate-user bypasses are covered. |
| Chrome filtering | Chosen mechanism survives reboot and network switches; categories are not truncated. |
| Other apps online | Belfius, itsme, WhatsApp, Waze, Maps and Android Auto remain connected. |
| Approved removal | Complete cleanup and ownership release succeed, including recovery after interruption. |
| Blocklist changes | Adding or removing a package needs exact-batch OTP approval. Uninstalling a blocked app keeps its rule; permitted installs need no approval. |

For Belfius and itsme, test registration if needed, login and their normal app-to-app authentication flows. For WhatsApp, test notification delivery and a call. For navigation, test GPS and audio. For Android Auto, test the connection type you actually use, including wired USB if applicable.

Test on the actual Samsung with the intended release APK and licence. Save the model, OS and Knox version, APK version, backend deployment, test date and outcome. Repeat the affected tests after OS updates or changes to the policy adapter or filter engine.

Acceptance requires actual readback and behaviour, not just an API returning true. Document unsupported system packages and how each essential exception affects the original restriction goal.

## 20. Release signing and everyday installation

### Prepare a release APK

1. In Studio choose Build > Generate Signed App Bundle or APK. Select APK for private installation; an AAB is primarily a Play distribution format.

2. Create a signing keystore outside the repository. Choose a strong password, a stable alias and a suitable long validity. Back up the file and credentials privately.

3. Build a signed release with debugging disabled and no testOnly flag or development escape UI. Inspect the merged manifest and release configuration.

4. Keep the same applicationId and signing key for future upgrades. A debug-signed device-owner install cannot simply be upgraded to a release signed with a different key. Prepare the real-phone controller using its final stable signing identity. [^31]

Use separate Convex development and production deployments, device credentials, OTP secrets and approval signing keys. Add backend URL and public signing-key ID through build configuration; these are public values. Confirm that the release app points to production.

### Deploy Convex

```bash
# Run from backend after production configuration is ready
npx convex env set RESEND_API_KEY --prod
npx convex env set EMAIL_FROM --prod
npx convex env set OTP_HMAC_SECRET --prod
npx convex env set DEVICE_TOKEN_HMAC_SECRET --prod
npx convex env set APPROVAL_SIGNING_PRIVATE_KEY --prod
npx convex deploy
```

Configure the production webhook secret separately if using delivery events. Verify the production functions, device authentication, primary and recovery workflows and native signature verification before activating management. Development email or signing settings must not leak into production. [^18][^32]

### Prepare the real phone

First back up photos, contacts, WhatsApp and other data, and verify how Belfius and itsme can be reactivated. Confirm access to your Google and Samsung accounts, the SIM or eSIM and recovery credentials. A management setup may require a factory reset and affects personal-device backup behaviour.

Use only the enrolment method already proven for this exact configuration. If QR provisioning is approved and available, its configuration identifies the admin component, signed APK download URL, APK checksum and bootstrap data, and our provisioning activities handle Android’s required callbacks. If Google blocks it, do not remove Google services or weaken the phone’s firmware as a workaround.

Finish guardian verification, pairing and initial approval; restore essential apps; apply the baseline; test updates and removal; then enable final debugging and bypass restrictions. A hardware recovery wipe remains possible, and ownership of your signing keys and backend ultimately lets you change the project. The target is strong everyday friction, not irreversible control.

## 21. Build order and first session

| Milestone | Concrete output |
| --- | --- |
| 1 Local tools | Studio, SDK, adb, Node and Git work. |
| 2 First Android run | A sample Compose screen runs on the phone without management. |
| 3 Technical proof | Eligible enrolment, package blocklist rules, non-blocked app updates, Chrome filtering and removal are demonstrated. |
| 4 Backend transport | Hosted Convex /health works; Resend delivers a test email. |
| 5 Device identity | Pairing and authenticated device endpoints work; internal functions cannot be called as public bypasses. |
| 6 Approval workflow | Immutable batches, OTP rules, signatures and guardian recovery pass tests. |
| 7 Local policy application | Room, verified snapshots and crash-safe native application are integrated. |
| 8 Full UI | Status, apps, websites, guardians, review, OTP, history and removal screens work. |
| 9 Release validation | Signed APK and production backend pass the acceptance matrix. |
| 10 Daily setup | Backed-up Samsung is enrolled through the proven route and guardians approve the initial policy. |

Your first session’s starting point is milestones 1 and 2. Install Studio, create the root repository and the Empty Activity project, connect the Samsung with USB debugging, run the greeting and commit the result. There is no need to reset the phone to do this.

Then start hosted Convex development and send a transport-test email. At the same time, obtain Knox access and prepare a clean test device. Do not spend time polishing every screen before the technical proof is complete.

Before everyday use, confirm the category-feed source, browser-only enforcement method, release enrolment route, supported removal route and licence lifecycle. We have specified their required behaviour; these implementation choices depend on the prototype results.

### Common setup problems

- Gradle Sync fails: read the first concrete error in Build output, check internet access and keep the template’s plugin and JDK versions compatible.

- adb shows unauthorized: unlock the phone and accept the authorisation prompt. No device usually means a cable, port or USB-mode issue.

- Device-owner command fails: check that the test device is eligible and has no accounts or existing management. Installing the APK alone never grants ownership.

- Room compiler errors: check the KSP version and Room dependency compatibility, and ensure the compiler dependency uses ksp rather than implementation.

- Convex code generation fails: keep use node actions separate from mutation files and fix TypeScript validation errors before continuing.

- No guardian email: check sending-domain verification, testMode, From address, delivery events and spam; do not accept missing delivery as approval.

- An approval is rejected on Android: compare development versus production key IDs, exact signed bytes, device ID, schema and base versions.

## 22. Official references

The links below support the platform capabilities and setup steps. Numeric OTP defaults, file organisation, endpoint names and application workflow are proposed project decisions. Dependency versions and portal options should be checked when implementing.

[^1]: [Android Room local storage](https://developer.android.com/training/data-storage/room)

[^2]: [Convex Android Kotlin client](https://docs.convex.dev/client/android/overview)

[^3]: [Convex HTTP actions](https://docs.convex.dev/functions/http-actions)

[^4]: [Google approved DPC enrolment allowlist](https://support.google.com/work/android/answer/16694822?hl=en)

[^5]: [Android dedicated device overview and development enrolment](https://developer.android.com/work/dpc/dedicated-devices)

[^6]: [Samsung Knox licence access and generation](https://docs.samsungknox.com/dev/knox-sdk/get-started/get-a-license/)

[^7]: [Android DevicePolicyManager API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)

[^8]: [Samsung Knox ApplicationPolicy API](https://docs.samsungknox.com/devref/knox-sdk/reference/com/samsung/android/knox/application/ApplicationPolicy.html)

[^9]: [Chrome website blocking and the URL policy limit](https://support.google.com/chrome/a/answer/7532419?hl=en)

[^10]: [Android Studio installation and system requirements](https://developer.android.com/studio/install)

[^11]: [Android build JDK selection](https://developer.android.com/build/jdks)

[^12]: [Run on a physical Android device](https://developer.android.com/studio/run/device)

[^13]: [Node.js official downloads](https://nodejs.org/en/download)

[^14]: [Convex Node.js quickstart](https://docs.convex.dev/quickstart/nodejs)

[^15]: [Convex development workflow](https://docs.convex.dev/understanding/workflow)

[^16]: [Resend Node.js sending and domain prerequisites](https://resend.com/docs/send-with-nodejs)

[^17]: [Official Convex Resend component](https://www.convex.dev/components/resend)

[^18]: [Convex environment-variable CLI](https://docs.convex.dev/cli/reference/env)

[^19]: [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)

[^20]: [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

[^21]: [Android package visibility declarations](https://developer.android.com/training/package-visibility/declaring)

[^22]: [AOSP provisioning contract](https://source.android.com/docs/devices/admin/provision)

[^23]: [Google Test DPC reference source](https://github.com/googlesamples/android-testdpc)

[^24]: [Samsung Knox SDK installation](https://docs.samsungknox.com/dev/knox-sdk/get-started/install-the-sdk/)

[^25]: [Belfius policy on modified operating systems](https://www.belfius.be/webapps/nl/selfcare/belfius/apps/veiligheid/Geen-verbinding-Belfius-Mobile-mogelijk-met-rooted-jailbroken-toestellen)

[^26]: [itsme compatible-device requirements](https://support.itsme-id.com/hc/en-us/articles/360041508153-Compatible-devices-and-functionality)

[^27]: [Convex execution runtimes](https://docs.convex.dev/functions/runtimes)

[^28]: [Convex functions and transactions](https://docs.convex.dev/functions/overview)

[^29]: [Android VPN and per-app routing](https://developer.android.com/develop/connectivity/vpn)

[^30]: [Android Enterprise VPN lockdown behaviour](https://support.google.com/work/android/answer/9213914?hl=en)

[^31]: [Android app signing](https://developer.android.com/studio/publish/app-signing)

[^32]: [Convex production deployment](https://docs.convex.dev/production/overview)

[^33]: [Android user restrictions including DISALLOW_INSTALL_APPS](https://developer.android.com/reference/android/os/UserManager)

[^34]: [Convex Resend setup test mode and retention](https://github.com/get-convex/resend/blob/main/README.md)

[^35]: [Samsung Knox installation blocklist implementation](https://docs.samsungknox.com/dev/knox-sdk/features/mdm-providers/app-management/app-installation/)

[^36]: [Samsung Knox download and installation blocking distinction](https://docs.samsungknox.com/dev/knox-sdk/faq/installation/)
