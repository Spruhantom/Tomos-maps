# Android debug testing without an emulator

The normal GitHub Actions Android job compiles both the debug application APK
and the instrumented-test APK, then runs local Robolectric tests. It does not
boot an Android emulator. Shared business logic remains covered by the
Kotlin Multiplatform desktop/JVM test task in the independent Linux job.

## Run instrumented tests on a physical Android phone

1. Enable Developer options and USB debugging on the phone.
2. Connect it by USB, accept the computer's RSA authorisation prompt, and
   confirm `adb devices -l` shows the device as `device`.
3. Build both APKs:

   ```bash
   gradle :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
   ```

4. Run the offline installer instrumentation test:

   ```bash
   bash tools/run-android-device-tests.sh
   ```

The script installs the debug APK and its test APK, then invokes the
instrumentation runner on the connected phone. To run a different test class,
set `DEVICE_TEST_CLASS` to its fully qualified class name.

For multiple connected phones, select one first with
`adb -s DEVICE_SERIAL` by exporting an adb wrapper or disconnecting the
additional devices. Keep test logs free of personal location history,
addresses, credentials and signing material.

## What this does not certify

Robolectric is useful for Android framework-facing unit tests but does not
replace hardware checks for GPS, background execution, actual storage pressure,
network handover or OEM power management. Those remain physical-device
acceptance tests. A cloud device farm can be added later without changing the
debug signing or publisher-key arrangement.
