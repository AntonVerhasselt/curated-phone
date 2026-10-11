#!/usr/bin/env bash
set -euo pipefail
# Local installations only; no system configuration or shell startup files are changed.
task_tools_dir="${XDG_DATA_HOME:-$HOME/.local/share}/curated-phone-tools"
task_sdk_dir="${ANDROID_HOME:-$HOME/Android/sdk}"
mkdir -p "$task_tools_dir" "$task_sdk_dir/cmdline-tools"
if ! command -v mise >/dev/null; then
  echo 'Install mise or provide JDK 21 with JAVA_HOME before running this script.' >&2
  exit 1
fi
mise install java@temurin-21
export JAVA_HOME="$(mise where java@temurin-21)"
if [[ ! -x "$task_sdk_dir/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$task_tools_dir/cmdline.zip"
  unzip -q "$task_tools_dir/cmdline.zip" -d "$task_sdk_dir/cmdline-tools"
  mv "$task_sdk_dir/cmdline-tools/cmdline-tools" "$task_sdk_dir/cmdline-tools/latest"
fi
# Licence acceptance is explicit in the Android SDK installer; review when prompted.
"$task_sdk_dir/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$task_sdk_dir" --licenses
"$task_sdk_dir/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$task_sdk_dir" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' 'emulator' 'system-images;android-36;google_apis;x86_64'
printf 'sdk.dir=%s\n' "$task_sdk_dir" > android/local.properties
echo "Tools ready. SDK: $task_sdk_dir"
echo "JDK: $JAVA_HOME"
echo 'Run npm ci from backend/, then open android/ in Android Studio or run android/gradlew.'
