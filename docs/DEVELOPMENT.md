# Development guide

## Project layout

The first implementation keeps the shared Kotlin Multiplatform application in composeApp:

- commonMain: shared Compose UI and platform-neutral application logic.
- androidMain: Android application entry point and Android-specific adapters.
- desktopMain: Linux/desktop JVM entry point.

The shared/ and native/ directories will be introduced as domain contracts and native routing integration grow into separate modules.

## Build targets

- Android: :composeApp:assembleDebug
- Linux desktop distribution: :composeApp:packageDistributionForCurrentOS
- Desktop development run: :composeApp:run

Use JDK 17 or newer and install Android SDK platform 36 for Android builds.

## Engineering sequence

1. Establish reproducible Gradle builds and CI.
2. Add platform-neutral navigation, location, route and map-package models.
3. Add local persistence and map package validation.
4. Integrate MapLibre rendering separately for Android and desktop.
5. Integrate Valhalla behind a shared routing interface.
6. Add GPS adapters, off-route detection and voice output.
7. Add optional traffic providers without making them a runtime dependency.

## Offline-first rules

- Route calculation must use locally available routing data.
- No network request may be required to continue an active offline route.
- Location history and favourites remain local by default.
- Map packages must be versioned, checksummed and safely replaceable.
- Network-dependent features must clearly indicate when unavailable.

## Safety

This prototype is not yet suitable for real-world turn-by-turn driving. Do not rely on it for navigation until routing, GPS accuracy, voice timing, map coverage and failure recovery have been implemented and tested.
