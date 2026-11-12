# Development guide

## Project layout

The current implementation keeps the shared Kotlin Multiplatform application in `composeApp`:

- `commonMain`: shared Compose UI and platform-neutral application logic.
- `androidMain`: Android application entry point and Android-specific dependencies.
- `desktopMain`: Linux/desktop JVM entry point.
- `commonTest`: platform-neutral model and validation tests.

The Android entry point is currently hosted in the KMP module using the legacy Android Gradle integration. Extract it into a dedicated `androidApp` module and migrate the shared module to Google's Android-KMP library plugin before moving to AGP 9 or newer.

## Build targets

- Android: `:composeApp:assembleDebug`
- Linux desktop distribution: `:composeApp:packageDistributionForCurrentOS`
- Desktop development run: `:composeApp:run`
- Shared model tests: `:composeApp:desktopTest`

Use JDK 17 and install Android SDK platform 36 for the current build configuration.

## Implemented foundations

- Shared geographic coordinates, travel modes, route requests and maneuver models.
- Shared navigation session, location and voice-guidance interfaces.
- Shared map camera, marker, route overlay, style-source and renderer contracts.
- Offline package manifest version 1 with required map/routing assets, per-file checksums and size validation.
- Relative package-path validation to reject absolute paths and traversal.
- Android debug and Linux desktop CI jobs.

The package manifest is a data contract only. Archive parsing, download, extraction, checksum verification against actual bytes, atomic installation and package updates are not yet implemented.

## Engineering sequence

1. Keep Android and Linux builds green and add regression tests for shared contracts.
2. Implement persistent local storage for map packages, favourites and navigation history.
3. Integrate MapLibre on Android and Linux behind the shared `MapRenderer` contract.
4. Implement secure archive import, checksum verification and atomic map package installation.
5. Integrate Valhalla behind `OfflineRoutePlanner`, using locally installed routing graphs.
6. Add platform GPS adapters, off-route detection, automatic rerouting and voice output.
7. Add optional traffic providers without making them a runtime dependency.
8. Add Android Auto and cycling/walking/EV routing after core driving navigation is reliable.

## Map renderer dependency note

MapLibre Compose is the intended shared rendering path because it supports Android and desktop JVM and provides offline map support. Its newer desktop runtime requires Java 25 and platform-specific native runtime artifacts, so it must be integrated alongside a deliberate Gradle/JDK/toolchain upgrade rather than added as an unpinned dependency to the current JDK 17 build.

## Offline-first rules

- Route calculation must use locally available routing data.
- No network request may be required to continue an active offline route.
- Location history and favourites remain local by default.
- Map packages must be versioned, checksummed and safely replaceable.
- Online styles, traffic and search must never silently become mandatory.
- Network-dependent features must clearly indicate when unavailable.

## Safety

This prototype is not yet suitable for real-world turn-by-turn driving. Do not rely on it for navigation until routing, GPS accuracy, voice timing, map coverage and failure recovery have been implemented and tested.
