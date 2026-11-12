# Development guide

## Project layout

- `composeApp/commonMain`: shared Compose UI, map screen and platform-neutral application logic.
- `composeApp/androidMain`: Android-specific runtime dependencies for the shared module.
- `composeApp/desktopMain`: Linux desktop entry point and native renderer dependencies.
- `composeApp/commonTest`: platform-neutral model and validation tests.
- `androidApp`: standalone Android application, manifest and activity.

The shared module uses Kotlin Multiplatform with Google's Android-KMP library plugin. The Android application is a separate module, which avoids combining the Android application plugin with the KMP plugin.

## Toolchain

- JDK 25 (required by the current MapLibre Native desktop runtime).
- Gradle 9.7.0.
- Kotlin 2.4.21.
- Compose Multiplatform 1.12.2.
- Android Gradle Plugin 9.3.1.
- Android SDK platform 37.

The selected versions are aligned with the Kotlin Multiplatform compatibility matrix. Keep the Kotlin, Gradle and Android Gradle Plugin versions in a tested compatibility range when upgrading.

## Build targets

- Android debug APK: `:androidApp:assembleDebug`
- Linux desktop distribution: `:composeApp:packageDistributionForCurrentOS`
- Linux desktop development run: `:composeApp:run`
- Shared model tests: `:composeApp:desktopTest`

## Implemented foundations

- Shared geographic coordinates, travel modes, route requests and maneuver models.
- Shared navigation session, location and voice-guidance interfaces.
- Shared map camera, marker, route overlay, style-source and renderer contracts.
- Offline package manifest version 1 with required map/routing assets, per-file checksums and size validation.
- Relative package-path validation to reject absolute paths and traversal.
- MapLibre Compose 0.20.0 integration for Android and Linux desktop.
- Interactive pan/zoom map, explicit zoom controls and foreground location indicator.
- Android location permissions are requested only after the user selects the location control.
- Linux uses MapLibre's Vulkan runtime and XDG location portal backend.

## Map rendering status

See [MAP_RENDERING.md](MAP_RENDERING.md) for the renderer, style source and current limitations.

The current style URI points to OpenFreeMap Liberty as a development preview. It needs an internet connection to fetch uncached resources. MapLibre's offline pack API is available, but the application has not yet connected it to the TomOS regional package manager. No claim of complete offline maps or offline routing should be made yet.

## Engineering sequence

1. Keep Android and Linux builds green after the MapLibre and AGP 9 migration.
2. Connect MapLibre offline packs to the regional package manager, with provider permissions and attribution verified.
3. Implement secure archive import, checksum verification and atomic map package installation.
4. Add a local vector/raster tile source for imported map files where supported.
5. Integrate Valhalla behind `OfflineRoutePlanner`, using locally installed routing graphs.
6. Add GPS lifecycle, off-route detection, automatic rerouting and voice output.
7. Add optional traffic providers without making them a runtime dependency.
8. Add Android Auto and cycling/walking/EV routing after core driving navigation is reliable.

## Offline-first rules

- Route calculation must use locally available routing data.
- No network request may be required to continue an active offline route.
- Location history and favourites remain local by default.
- Map packages must be versioned, checksummed and safely replaceable.
- Online styles, traffic and search must never silently become mandatory.
- Network-dependent features must clearly indicate when unavailable.

## Safety

This prototype is not yet suitable for real-world turn-by-turn driving. Do not rely on it for navigation until routing, GPS accuracy, voice timing, map coverage and failure recovery have been implemented and tested.
