# Setup on this machine

Repository: `/home/antonverhasselt/Projects/curated-phone`.

| Tool | Installed location / selection |
| --- | --- |
| Node/npm | Existing mise Node 26 installation; backend dependencies are pinned with a committed npm lockfile. CI uses Node 24 LTS. |
| GitHub | Existing authenticated `gh` CLI for AntonVerhasselt; SSH Git transport. |
| Java | mise Temurin 21, `mise where java@temurin-21`; Java/Kotlin bytecode targets 17. |
| Android Studio | `~/.local/share/curated-phone-tools/android-studio/bin/studio` (or `studio.sh` depending on packaging). Downloaded official Linux archive. Uses its bundled runtime to launch. |
| Android SDK | `~/Android/sdk`, command-line tools, API 36 platform, build tools 36.0.0, platform-tools, emulator, Google APIs x86_64 API 36 image. |
| Gradle | Committed wrapper 8.13; AGP 8.13.2, Kotlin/Compose compiler 2.2.21, KSP 2.2.21-2.0.4. Compatible pinned stable versions, deliberately not automatic latest upgrades. |
| Emulator | `curated_phone_api36`, Pixel 6 profile, Android 16, x86_64. AVD is at `~/.config/.android/avd` on this XDG Linux setup. |

Android Studio's bundled launch runtime is independent of the app's Gradle runtime. In Studio's Gradle settings select the installed Temurin 21 path for this project. Terminal builds use command-scoped `JAVA_HOME`. No shell startup files or Omarchy desktop settings were changed.

```bash
export JAVA_HOME="$(mise where java@temurin-21)"
export ANDROID_HOME="$HOME/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export ANDROID_AVD_HOME="$HOME/.config/.android/avd"
"$HOME/.local/share/curated-phone-tools/android-studio/bin/studio" "$HOME/Projects/curated-phone/android"
```

## Hosted Convex

Team: `anton-verhasselt`. Project: `curated-phone`.

| Environment | Region | HTTP API | Dashboard |
| --- | --- | --- | --- |
| Development | Europe/Ireland | `https://tidy-jaguar-244.eu-west-1.convex.site` | [Development](https://dashboard.convex.dev/t/anton-verhasselt/curated-phone/tidy-jaguar-244) |
| Production | Europe/Ireland | `https://necessary-cormorant-361.eu-west-1.convex.site` | [Production](https://dashboard.convex.dev/t/anton-verhasselt/curated-phone/necessary-cormorant-361) |

The `.convex.cloud` counterparts are database/client URLs. The Android client uses the `.convex.site` HTTP action URL. The CLI's automatically maintained `backend/.env.local` selects development. `npx convex deploy` selects the default production deployment; `npx convex env set NAME --prod` configures it.

Independent HMAC and P-256 signing secrets were generated and configured for the two deployments. Recovery copies are in ignored, mode-0700 `.secrets/development` and `.secrets/production` directories with mode-0600 secret files. Only public verification keys belong in APKs. Back up these recovery copies privately before changing machines. Resend credentials have not been supplied.

The CLI initially auto-provisioned an empty US production deployment. Its identity and absence of deployed code were checked, and only that newly created empty deployment was removed before creating the intended EU production deployment. No existing user project or data was deleted.

## Fresh emulator

```bash
export JAVA_HOME="$(mise where java@temurin-21)"
export ANDROID_AVD_HOME="$HOME/.config/.android/avd"
echo no | "$HOME/Android/sdk/cmdline-tools/latest/bin/avdmanager" create avd \
  --name curated_phone_api36 --package 'system-images;android-36;google_apis;x86_64' --device pixel_6
"$HOME/Android/sdk/emulator/emulator" -avd curated_phone_api36 -gpu swiftshader
```

The fresh emulator was enrolled successfully with `adb shell dpm set-device-owner`. This validates the shell and development contract, not Samsung/Google production eligibility. Do not run enrolment commands blindly against a personal phone.
