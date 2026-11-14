# TomOS Maps

TomOS Maps is an online-first navigation application for Android and Linux, built with Kotlin Multiplatform and Compose Multiplatform.

## Project goals

- UK-first online mapping and navigation using OpenStreetMap data.
- Shared navigation logic across Android and Linux.
- Online route calculation and UK postcode destination lookup without a mandatory account or subscription.
- Clear provider attribution and respect for map-data and tile-provider licences.

## Technology

- Kotlin Multiplatform and Compose Multiplatform.
- MapLibre Compose 0.20.0 for Android and Linux desktop map rendering.
- Ferrostar Core 0.59.0 owns Android live navigation state, step progress, route deviation handling, online/offline rerouting and spoken turn instructions.
- Valhalla supplies online routes through the configured HTTPS endpoint and can also route against previously installed regional graphs; Android hands both route types to Ferrostar. Linux retains its shared progress controller and native Valhalla integration.
- GraphHopper remains an optional alternative routing backend, not a replacement for the installed Valhalla offline graph.
- Photon is an optional geocoding/search component; the existing UK postcode index and local search remain first-class.
- PMTiles/Protomaps is an optional regional map distribution format under evaluation; the existing MBTiles runtime remains supported.
- Ktor HTTP client for configured routing and search endpoints.
- Map and regional-package downloads are currently disabled; offline download work remains out of scope.

See [docs/FERROSTAR_MIGRATION.md](docs/FERROSTAR_MIGRATION.md) for the platform integration and [docs/NAVIGATION.md](docs/NAVIGATION.md) for current behaviour and test status.

## Repository layout

- `composeApp/` — shared Compose UI, navigation, map and offline abstractions, platform implementations and tests.
- `androidApp/` — Android application entry point and manifest.
- `docs/` — architecture, development, navigation, rendering and offline-package documentation.
- `THIRD_PARTY_NOTICES.md` — third-party licence notices.

## Current status

**Stabilisation / scoped feature freeze is active.** The approved work covers online OpenStreetMap rendering, UK postcode destinations, foreground turn-by-turn guidance, Android Ferrostar integration and validation of live navigation. Map and regional-package downloads are out of scope. See [docs/FEATURE_FREEZE.md](docs/FEATURE_FREEZE.md). The Ferrostar integration is a navigation-foundation change; it must not expand into unrelated UI redesign or provider replacement.

The shared app renders an interactive MapLibre map on Android and Linux. The user can request foreground location, follow the current position and preview navigation. The navigation panel accepts a Valhalla HTTPS endpoint and a UK postcode. Postcodes.io resolves the postcode to coordinates before the app requests a car route from the current GPS fix. On Android, the route is handed to Ferrostar for live step progress, route deviation handling, automatic rerouting and spoken instructions; the MapLibre route line follows accepted route replacements. Android speech uses Ferrostar's TextToSpeech observer with UK-English locale selection. Linux retains the shared progress controller and eSpeak NG/eSpeak guidance. Speech is foreground-only and is muted when the GPS fix becomes stale. Routing exceptions and service failures are surfaced in the navigation panel.

The routing endpoint is user-configured; TomOS does not operate or endorse a public routing service. A successful CI build does not establish real-world navigation reliability. The full journey still requires physical Android testing, including GPS accuracy, route-line placement, ETA behaviour, stale-fix recovery and arrival detection.

TomOS Maps uses MapLibre Compose on Android and Linux with the OpenFreeMap Liberty style, which renders OpenStreetMap-based road data online. MapLibre is the only map renderer and Google Maps is not integrated. Map and regional-package download controls have been removed; the app uses the online map and configured online routing services. Previously installed regional data is not automatically deleted, but the app no longer offers a download workflow. TomOS Maps remains a prototype and is not yet a production navigation application.

## Latest testing release

TomOS Maps 0.2.0 testing build 538 was published from `main` after PR #4 was merged. The Android debug APK and Linux x86_64 AppImage are available from the [GitHub release](https://github.com/Spruhantom/Tomos-maps/releases/tag/testing-0.2.0-build-538). This is a prerelease for testing, not a signed production Android release. The release workflow runs after the Android, Linux desktop/AppImage and regional package test jobs pass.

- [Android debug APK](https://github.com/Spruhantom/Tomos-maps/releases/download/testing-0.2.0-build-538/androidApp-debug.apk)
- [Linux x86_64 AppImage](https://github.com/Spruhantom/Tomos-maps/releases/download/testing-0.2.0-build-538/Tomos-Maps-Linux-x86_64.AppImage)

## Build

Requirements:
- JDK 25.
- Gradle 9.7.0.
- Android SDK platform 37 for Android builds.
- A Vulkan-capable Linux graphics driver for the current Linux desktop renderer.

Build Android:

```bash
gradle :androidApp:assembleDebug
```

Build Linux desktop distribution:

```bash
gradle :composeApp:createDistributable :composeApp:packageDistributionForCurrentOS
```

The CI workflow packages the Linux distribution as an x86_64 AppImage using `appimagetool`. The AppImage is currently produced and tested on GitHub Actions; use the published testing release for the ready-built binary.

Run shared model tests:

```bash
gradle :composeApp:desktopTest
```

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/MAP_RENDERING.md](docs/MAP_RENDERING.md), [docs/NAVIGATION.md](docs/NAVIGATION.md), [docs/OFFLINE_MAP_PACKAGES.md](docs/OFFLINE_MAP_PACKAGES.md), [docs/CUSTOM_OFFLINE_AREAS.md](docs/CUSTOM_OFFLINE_AREAS.md), [docs/UK_OFFLINE_NAVIGATION.md](docs/UK_OFFLINE_NAVIGATION.md), [docs/REGIONAL_DATA_PIPELINE.md](docs/REGIONAL_DATA_PIPELINE.md) and [docs/FERROSTAR_MIGRATION.md](docs/FERROSTAR_MIGRATION.md).

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Preserve required attribution and comply with ODbL when using or distributing OSM-derived data. Do not bulk-download offline areas from the public OpenStreetMap standard tile service. Use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
