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
- Android application target.
- Linux desktop target using Compose Desktop.
- MapLibre for map rendering.
- Valhalla as the routing engine, integrated behind a platform-neutral interface.
- SQLite-compatible local storage and versioned regional map packages.

## Repository layout

- composeApp/ — shared Compose UI and Android/Linux entry points.
- shared/ — navigation, offline package, search, traffic and storage contracts.
- native/ — native routing-engine integration.
- docs/ — architecture, development and data/licensing documentation.

## Current status

This repository is at its foundation stage. The initial commit establishes the project structure and platform entry points. It is not yet a production navigation application: map rendering, GPS integration, routing-engine bindings, offline map import and voice guidance will be implemented and tested in subsequent milestones.

## Build

Requirements:
- JDK 17 or newer.
- Android SDK for Android builds.
- Gradle wrapper (to be added with the first reproducible build setup).

The initial project uses Gradle Kotlin DSL. See docs/DEVELOPMENT.md for the intended development workflow.

## Data and licensing

OpenStreetMap data is licensed under the Open Database License (ODbL). Any map-data distribution must preserve required attribution and comply with ODbL. Do not bulk-download offline areas from the public OpenStreetMap standard tile service; use a suitable provider or project-operated tile infrastructure with explicit offline-download permission.

Third-party components retain their own licences. See THIRD_PARTY_NOTICES.md.

## Contributions

Contributions are welcome. Please open an issue before introducing a new map provider, routing engine, bundled dataset or licence dependency.
