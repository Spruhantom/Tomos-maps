# TomOS Maps

Open-source, offline-first navigation for Android and Linux, developed in one repository with a shared Kotlin Multiplatform core.

## Project goals

- Offline maps, local search and on-device route calculation.
- Turn-by-turn navigation with spoken instructions.
- Automatic rerouting without a network connection.
- Optional live traffic and community incident reports when online.
- Shared application logic and map-package format across Android and Linux.
- No mandatory account, subscription or remote service for core navigation.

## Planned technology

- Kotlin Multiplatform and Compose Multiplatform for shared application logic and UI.
- Android application target and Linux desktop target.
- MapLibre for map rendering.
- Valhalla as the routing engine, integrated behind a platform-neutral interface.
- SQLite-compatible local storage and versioned regional map packages.

## Repository layout

- `composeApp/` — shared Compose UI, common models, tests and current Android/Linux entry points.
- `docs/` — architecture, development and offline package format documentation.
- `THIRD_PARTY_NOTICES.md` — third-party licence notices.

The `shared/` and `native/` directories are planned for future extraction as domain logic and native routing integration grow.

## Current status

The foundation is in place and the Android debug APK plus Linux desktop distribution have both passed CI on commit `2caa9db`. Shared models now cover coordinates, route requests, navigation sessions, map camera state, markers, route overlays and offline package metadata.

This is not yet a production navigation application. Real map rendering, GPS integration, routing-engine bindings, secure archive import, local search and voice guidance remain to be implemented and tested.

## Build

Requirements:
- JDK 17.
- Android SDK platform 36 for Android builds.
- Gradle 8.14 (installed locally; a Gradle wrapper is planned).

Build Android:

```bash
gradle :composeApp:assembleDebug
```

Build Linux desktop distribution:

```bash
gradle :composeApp:packageDistributionForCurrentOS
```

Run shared model tests:

```bash
gradle :composeApp:desktopTest
```

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the current development workflow and compatibility notes.

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Any map-data distribution must preserve required attribution and comply with ODbL. Do not bulk-download offline areas from the public OpenStreetMap standard tile service; use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Contributions

Contributions are welcome. Please open an issue before introducing a new map provider, routing engine, bundled dataset or licence dependency.
