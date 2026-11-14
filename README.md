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
- Map downloads and regional package downloads are removed from the user-facing application.

See [docs/FERROSTAR_MIGRATION.md](docs/FERROSTAR_MIGRATION.md) for the platform migration plan and integration boundaries.

## Repository layout

- `composeApp/` — shared Compose UI, navigation, map and offline abstractions, platform implementations and tests.
- `androidApp/` — Android application entry point and manifest.
- `docs/` — architecture, development, navigation, rendering and offline-package documentation.
- `THIRD_PARTY_NOTICES.md` — third-party licence notices.

## Current status

**Stabilisation / scoped feature freeze is active.** The approved work covers bounded offline map packs, UK postcode destinations, foreground voice guidance and validation of live navigation. See [docs/FEATURE_FREEZE.md](docs/FEATURE_FREEZE.md). The Ferrostar migration is a specifically requested navigation-foundation change; it must not expand into unrelated UI redesign or provider replacement.

The shared app renders an interactive MapLibre map on Android and Linux. The user can request foreground location, follow the current position and preview navigation. The navigation panel accepts a Valhalla HTTPS endpoint and a UK postcode. Postcodes.io resolves the postcode to coordinates before the app requests a car route from the current GPS fix. The returned route geometry is drawn on the map and live GPS progress selects the next manoeuvre. Spoken turn prompts are issued at staged approach distances; Android uses the device text-to-speech engine with a UK-English voice, while Linux desktop uses eSpeak NG or eSpeak when installed. Speech is foreground-only and pauses when the GPS fix becomes stale. GPS speed is passed to the progress tracker when available; ETA is only shown when a valid positive speed estimate is available. GPS fixes older than ten seconds pause guidance and produce a stale-signal warning. Routing exceptions and service failures are surfaced in the navigation panel.

The routing endpoint is user-configured; TomOS does not operate or endorse a public routing service. A successful CI build does not establish real-world navigation reliability. The full journey still requires physical Android testing, including GPS accuracy, route-line placement, ETA behaviour, stale-fix recovery and arrival detection.

TomOS Maps uses MapLibre Compose on Android and Linux with the OpenFreeMap Liberty style, which renders OpenStreetMap-based road data online. MapLibre is the only map renderer and Google Maps is not integrated. Map and regional-package download controls have been removed; the app uses the online map and configured online routing services. Previously installed regional data is not automatically deleted, but the app no longer offers a download workflow. TomOS Maps remains a prototype and is not yet a production navigation application.

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

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), [docs/MAP_RENDERING.md](docs/MAP_RENDERING.md), [docs/NAVIGATION.md](docs/NAVIGATION.md), [docs/OFFLINE_MAP_PACKAGES.md](docs/OFFLINE_MAP_PACKAGES.md), [docs/CUSTOM_OFFLINE_AREAS.md](docs/CUSTOM_OFFLINE_AREAS.md), [docs/UK_OFFLINE_NAVIGATION.md](docs/UK_OFFLINE_NAVIGATION.md), [docs/REGIONAL_DATA_PIPELINE.md](docs/REGIONAL_DATA_PIPELINE.md) and [docs/FERROSTAR_MIGRATION.md](docs/FERROSTAR_MIGRATION.md).

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Preserve required attribution and comply with ODbL when using or distributing OSM-derived data. Do not bulk-download offline areas from the public OpenStreetMap standard tile service. Use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
