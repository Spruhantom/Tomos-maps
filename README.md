# TomOS Maps

TomOS Maps is an offline-first navigation application for Android and Linux, built with Kotlin Multiplatform and Compose Multiplatform.

## Project goals

- UK-first mapping and navigation, with offline operation as the long-term target.
- Shared navigation logic across Android and Linux.
- Local map, routing and search data without a mandatory account or subscription.
- Clear provider attribution and respect for map-data and tile-provider licences.

## Technology

- Kotlin Multiplatform and Compose Multiplatform.
- MapLibre Compose 0.20.0 for Android and Linux desktop map rendering.
- Valhalla request/response integration for route calculation.
- Ktor HTTP client for configured routing endpoints.
- Shared GPS route-progress and live-navigation controller.
- MapLibre native offline-region storage adapters on Android and Linux.

## Repository layout

- `composeApp/` — shared Compose UI, navigation, map and offline abstractions, platform implementations and tests.
- `androidApp/` — Android application entry point and manifest.
- `docs/` — architecture, development, navigation, rendering and offline-package documentation.
- `THIRD_PARTY_NOTICES.md` — third-party licence notices.

## Current status

**Stabilisation / scoped feature freeze is active.** The approved work covers bounded offline map packs, UK postcode destinations, foreground voice guidance and validation of live navigation. Unrelated features, integrations and redesigns remain frozen. See [docs/FEATURE_FREEZE.md](docs/FEATURE_FREEZE.md).

The shared app renders an interactive MapLibre map on Android and Linux. The user can request foreground location, follow the current position and preview navigation. The navigation panel accepts a Valhalla HTTPS endpoint and a UK postcode. Postcodes.io resolves the postcode to coordinates before the app requests a car route from the current GPS fix. The returned route geometry is drawn on the map and live GPS progress selects the next manoeuvre. Spoken turn prompts are issued at staged approach distances; Android uses the device text-to-speech engine with a UK-English voice, while Linux desktop uses eSpeak NG or eSpeak when installed. Speech is foreground-only and pauses when the GPS fix becomes stale. GPS speed is passed to the progress tracker when available; ETA is only shown when a valid positive speed estimate is available. GPS fixes older than ten seconds pause guidance and produce a stale-signal warning. Routing exceptions and service failures are surfaced in the navigation panel.

The routing endpoint is user-configured; TomOS does not operate or endorse a public routing service. A successful CI build does not establish real-world navigation reliability. The full journey still requires physical Android testing, including GPS accuracy, route-line placement, ETA behaviour, stale-fix recovery and arrival detection.

The OpenFreeMap Dark style remains the current online preview source. Offline maps now support coordinate-centred Small/City/Large area selection, a City preset by default, zoom-level tile-count estimates, map-layer download and existing pause/resume/delete management. The current custom-area downloader caches the basemap only; roads/routing and postcode/place search still require the signed regional package pipeline. First-time setup now asks the user to choose an English region; when a matching verified package is present in the signed catalogue, TomOS starts downloading it automatically. Regional downloads support resumable transfers, integrity-checked installation, local postcode/place search and local Valhalla runtime startup when a package covers the journey. Packages can be distributed as public GitHub Release assets, avoiding a separately operated S3/R2 master-data bucket. The publisher public key and signed catalogue URLs still need release-owner configuration, and real regional package publication, Android Valhalla engine binaries and MapLibre MBTiles renderer integration remain release blockers. Installed regional packages do not yet make the basemap fully offline. TomOS Maps remains a prototype and is not yet a production navigation application.

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
gradle :composeApp:packageDistributionForCurrentOS
```

Run shared model tests:

```bash
gradle :composeApp:desktopTest
```

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/MAP_RENDERING.md](docs/MAP_RENDERING.md), [docs/NAVIGATION.md](docs/NAVIGATION.md) and [docs/OFFLINE_MAP_PACKAGES.md](docs/OFFLINE_MAP_PACKAGES.md), [docs/CUSTOM_OFFLINE_AREAS.md](docs/CUSTOM_OFFLINE_AREAS.md), [docs/UK_OFFLINE_NAVIGATION.md](docs/UK_OFFLINE_NAVIGATION.md) and [docs/REGIONAL_DATA_PIPELINE.md](docs/REGIONAL_DATA_PIPELINE.md).

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Preserve required attribution and comply with ODbL when using or distributing OSM-derived data. Do not bulk-download offline areas from the public OpenStreetMap standard tile service. Use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
