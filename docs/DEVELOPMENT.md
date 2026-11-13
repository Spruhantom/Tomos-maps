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

The GitHub Actions workflow on `main` runs shared tests, the Linux desktop distribution build and the Android debug build. It also publishes the Android testing APK as a prerelease when the workflow completes successfully. Check the actual workflow result before describing a commit as build-verified.

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
- Foreground voice guidance announces Valhalla manoeuvres at staged distances and can be muted from the navigation panel.
- Android speech uses the system TextToSpeech engine with the UK-English locale where available; Linux desktop uses eSpeak NG or eSpeak.
- Common tests cover postcode validation/lookup and turn-announcement de-duplication.
- MapLibre Compose 0.20.0 integration for Android and Linux desktop.
- Foreground location indicator and map camera tracking.
- MapLibre native offline-region storage adapters and an in-app pack manager; provider policy gates new downloads.
- Versioned offline package manifest with required map/routing assets, checksums and path validation.

## Map and offline status

See [MAP_RENDERING.md](MAP_RENDERING.md) and [OFFLINE_MAP_PACKAGES.md](OFFLINE_MAP_PACKAGES.md).

The current OpenFreeMap Dark style is an online development preview. Its provider terms allow restricted offline downloads (3,000 tiles or 50 MB per download, at most four parallel requests and an app-specific User-Agent). TomOS sends the User-Agent, but the current MapLibre Compose runtime does not expose the required four-request cap, so new downloads remain blocked. A downloaded tile pack does not include a routing graph or local search index.

## Live-navigation status and verification

See [NAVIGATION.md](NAVIGATION.md). The code and CI currently provide the route-request, route-display and GPS progress foundations. CI verifies compilation and automated shared tests only. It does not verify GPS accuracy, route alignment, ETA quality, signal-loss recovery or safe turn timing on a physical device.

Before any real-world use, test on a physical Android device in a safe, controlled setting. Do not operate or inspect the phone while driving; use a passenger or stop safely. Confirm permissions, outdoor GPS lock, route-line alignment, measured-speed ETA, stale-fix warning, recovery after a fresh fix and arrival behaviour. Linux should be smoke-tested separately for map rendering and location-provider behaviour.

## Offline-first and safety rules

- No offline download is enabled unless the provider's size/tile limits, concurrency cap and User-Agent requirements are enforced.
- Do not use the public OpenStreetMap standard tile server for bulk/offline downloads.
- Map tiles, routing graphs, search indexes and voice assets are distinct package assets.
- Never imply map tiles alone provide offline route calculation.
- Postcode lookup and Valhalla routing are network-dependent and must clearly report service errors; neither is offline routing.
- Foreground speech must pause on stale GPS fixes; do not represent it as background navigation.
- This prototype is not yet suitable as a sole source of real-world driving directions.
