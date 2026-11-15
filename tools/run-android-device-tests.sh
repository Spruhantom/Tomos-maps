#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_APK="${APP_APK:-$(find "$ROOT/androidApp/build/outputs/apk/debug" -maxdepth 1 -name '*.apk' -print -quit)}"
TEST_APK="${TEST_APK:-$(find "$ROOT/androidApp/build/outputs/apk/androidTest/debug" -maxdepth 1 -name '*.apk' -print -quit)}"
DEVICE_TEST_CLASS="${DEVICE_TEST_CLASS:-uk.co.tomosmaps.OfflineLayerInstallerDeviceTest}"

command -v adb >/dev/null 2>&1 || { echo "adb is required; install Android platform-tools." >&2; exit 2; }
[[ -s "$APP_APK" ]] || { echo "Debug APK not found. Run :androidApp:assembleDebug first." >&2; exit 2; }
[[ -s "$TEST_APK" ]] || { echo "Instrumented test APK not found. Run :androidApp:assembleDebugAndroidTest first." >&2; exit 2; }
adb get-state >/dev/null 2>&1 || {
  echo "No authorised Android device. Enable USB debugging, connect the phone and accept its RSA prompt." >&2
  adb devices -l
  exit 2
}

adb install -r "$APP_APK"
adb install -r "$TEST_APK"
adb shell am instrument -w -e class "$DEVICE_TEST_CLASS" uk.co.tomosmaps.test/androidx.test.runner.AndroidJUnitRunner
