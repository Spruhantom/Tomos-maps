# TomOS Maps

Open-source, offline-first navigation for Android and Linux, developed in one repository with a shared Kotlin Multiplatform core.

## Project goals

- Offline maps, local search and on-device route calculation.
- Turn-by-turn navigation with spoken instructions.
- Automatic rerouting without a network connection.
- Optional live traffic and community incident reports when online.
- Shared application logic and map-package format across Android and Linux.
- No mandatory account, subscription or remote service for core navigation.

## Technology

- Kotlin Multiplatform and Compose Multiplatform for shared application logic and UI.
- MapLibre Compose for native Android and Linux desktop map rendering.
- Valhalla is planned as the offline routing engine behind a shared interface.
- Versioned regional packages and local storage are planned for offline map and routing data.

## Repository layout

- `composeApp/` — shared Compose UI, common models, Android library target, desktop target and tests.
- `androidApp/` — standalone Android application entry point.
- `docs/` — architecture, development and offline package documentation.
- `THIRD_PARTY_NOTICES.md` — third-party licence notices.

## Current status

The application now has an interactive MapLibre map on the shared UI, with pan/zoom gestures, zoom controls and an opt-in current-location indicator on Android and Linux. The Android entry point has been separated into `androidApp` and the shared module migrated to the Android-KMP library plugin.

The current map style is an online OpenFreeMap development preview. Native MapLibre offline-region storage is now bridged on Android and Linux: the shared API validates region bounds and provider permission, while the platform adapters create, list, resume, pause and delete persistent tile packs. The in-app Offline maps manager lists installed packs, refreshes progress, pauses/resumes downloads and removes regions. The current preview provider is deliberately blocked from new offline downloads until its terms are verified. A starter UK region catalogue now lists Greater London, South East England, the Midlands, North West England and Central Scotland, and the manager displays the provider policy. Custom-area selection, download-size estimates and a provider with verified offline rights are still outstanding. Offline routing graphs, local search, GPS background tracking, voice guidance and Valhalla integration are not yet complete. This is not yet a production navigation application.

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

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) and [docs/MAP_RENDERING.md](docs/MAP_RENDERING.md).

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Any map-data distribution must preserve required attribution and comply with ODbL. Do not bulk-download offline areas from the public OpenStreetMap standard tile service; use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
