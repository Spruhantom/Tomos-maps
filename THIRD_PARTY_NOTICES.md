# Third-party notices

TomOS Maps includes or integrates the following software. Map data and hosted tile services have separate terms from the application code.

- Kotlin and Kotlin Multiplatform — Apache License 2.0.
- Compose Multiplatform — Apache License 2.0.
- MapLibre Compose — BSD 3-Clause License.
- MapLibre Native / Native FFI runtime components — retain the licence and notices shipped with the exact runtime artifacts.
- Ferrostar Android navigation core 0.59.0 — BSD 3-Clause License.
- OkHttp — Apache License 2.0.
- AndroidX Activity Compose — Apache License 2.0.

Planned components, not yet integrated:
- Valhalla — MIT License (the Android JNI runtime is integrated; regional routing data has separate source licensing).
- OpenStreetMap data — Open Database License (ODbL), with attribution and database-share-alike obligations.

The current development map style is hosted by OpenFreeMap. Its tile/style service terms and offline-download permissions must be checked separately before production use or bulk downloads.

Before shipping binaries or map packages, generate and review a dependency licence report for the exact resolved versions, include all required transitive notices, and preserve upstream licence texts. Do not assume map data, tile services or routing datasets use the same licence as the software.
