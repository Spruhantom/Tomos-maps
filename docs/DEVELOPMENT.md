# Development guide

## Project layout

- `composeApp/src/commonMain`: shared Compose UI, navigation models/controllers, map screen and platform-neutral application logic.
- `composeApp/src/androidMain`: Android-specific runtime and location integration.
- `composeApp/src/desktopMain`: Linux desktop entry point and native renderer/location integration.
- `composeApp/src/commonTest`: platform-neutral model, route-adapter, routing-client and navigation-controller tests.
- `androidApp`: standalone Android application, manifest and activity.

The shared module uses Kotlin Multiplatform with Google's Android-KMP library plugin. The Android application is a separate module.

## Toolchain

- JDK 25.
- Gradle 9.7.0.
- Kotlin 2.4.21.
- Compose Multiplatform 1.12.2.
- Android Gradle Plugin 9.3.1.
- Android SDK platform 37.
- MapLibre Compose 0.20.0.

Keep Kotlin, Gradle, Android Gradle Plugin and MapLibre dependencies in a tested compatibility range when upgrading.

## Build and test targets

- Android debug APK: `gradle :androidApp:assembleDebug`
- Linux desktop distribution: `gradle :composeApp:packageDistributionForCurrentOS`
- Linux desktop development run: `gradle :composeApp:run`
- Shared tests: `gradle :composeApp:desktopTest`

The GitHub Actions workflow on `main` runs shared tests, the Linux desktop distribution and AppImage build, the Android debug build, and regional package builder tests. When all release dependencies pass, it publishes a GitHub testing prerelease containing both the Android debug APK and Linux x86_64 AppImage. Check the actual workflow result and release assets before describing a commit as published.

## Implemented foundations

- Shared geographic coordinates, travel modes, route requests and manoeuvre models.
- Valhalla request builder, response adapter and Ktor-based routing client.
- HTTPS validation for configured remote routing endpoints; plain HTTP is accepted only for localhost development.
- Shared route geometry, progress tracking, live-navigation session lifecycle and arrival threshold.
- Measured GPS speed is supplied to route progress when the platform measurement includes it.
- Live navigation screen accepts a routing endpoint and UK postcode, resolves the postcode through Postcodes.io, requests a route from the current GPS fix, and displays route progress and the next manoeuvre.
- Calculated route geometry is represented as GeoJSON and drawn as a MapLibre line layer.
- GPS measurements older than ten seconds pause progress updates and are surfaced as a stale-fix warning.
- Routing failures, invalid/unknown UK postcodes and thrown exceptions are shown in the navigation UI rather than silently swallowed.
- Android voice guidance is driven by Ferrostar's AndroidTtsObserver, with UK-English locale selection where available; Linux desktop uses eSpeak NG or eSpeak.
- Common tests cover postcode validation/lookup and turn-announcement de-duplication.
- MapLibre Compose 0.20.0 integration for Android and Linux desktop.
- Foreground location indicator and map camera tracking.

## Map and offline status

MapLibre renders the online OpenStreetMap-based map using the configured OpenFreeMap style. Map downloads and regional-package downloads are currently disabled in the user-facing application. Do not re-enable download controls or imply that previously installed regional data is downloadable or managed through the current UI. See [MAP_RENDERING.md](MAP_RENDERING.md) and [FEATURE_FREEZE.md](FEATURE_FREEZE.md).

## Live-navigation status and verification

See [NAVIGATION.md](NAVIGATION.md). Android now hands online Valhalla routes to Ferrostar for live step progress, route-deviation handling, automatic rerouting and spoken prompts. Linux retains the shared TomOS progress controller and eSpeak guidance. CI verifies compilation and automated shared tests only. It does not verify GPS accuracy, route alignment, ETA quality, signal-loss recovery or safe turn timing on a physical device.

Before any real-world use, test on a physical Android device in a safe, controlled setting. Do not operate or inspect the phone while driving; use a passenger or stop safely. Confirm permissions, outdoor GPS lock, route-line alignment, measured-speed ETA, stale-fix warning, recovery after a fresh fix and arrival behaviour. Linux should be smoke-tested separately for map rendering and location-provider behaviour.

## Offline-first and safety rules

- Map and regional-package downloads are disabled and remain out of scope. Do not reintroduce download functionality without a separately approved design and implementation plan.
- Do not use the public OpenStreetMap standard tile server for bulk/offline downloads.
- Previously installed regional routing graphs may still be used by the existing local-routing path, but the current app does not offer a download workflow.
- Postcode lookup and Valhalla routing are network-dependent and must clearly report service errors; neither is offline routing.
- Foreground speech must pause on stale GPS fixes; do not represent it as background navigation.
- This prototype is not yet suitable as a sole source of real-world driving directions.
