# Map rendering integration

## Renderer

TomOS Maps uses MapLibre Compose 0.20.0 from the shared `composeApp` module. The same Compose map screen is compiled for Android and Linux desktop.

- Android loads the MapLibre Vulkan runtime.
- Linux x64 loads the MapLibre Vulkan runtime and the XDG location portal backend.
- The desktop window supplies MapLibre with its Compose GPU presentation host.
- MapLibre's default overlay is retained so provider attribution and standard map controls remain visible.

The map opens on a UK-wide camera position. Pointer/touch gestures provide pan and pinch zoom; the on-screen controls provide zoom in/out.

## Location

The shared screen creates a foreground location state and draws a location indicator layer. Permission is requested only after the user taps **My location**. When a location fix is available, the control centres the camera on it.

Android declares coarse and fine foreground location permissions. The Linux provider uses the desktop XDG location portal, commonly backed by GeoClue. Background location is not requested.

## Development style

The current development style is OpenFreeMap Liberty:

`https://tiles.openfreemap.org/styles/liberty`

This is a network-backed preview, not a bundled offline map. It is intentionally identified in the UI as an online preview. Before production use, verify the selected provider's terms, rate limits, offline-download permissions and attribution requirements.

## Offline map integration

MapLibre Compose has a native offline storage API. It stores the style, tile resources and supporting assets for a style URL and geographic region, and reuses downloaded resources when the device is offline.

TomOS now has a shared `OfflineRegionStore` contract and Android/Linux implementations backed by `DefaultMapRuntime.instance.offlineStorage`. The adapter can list packs restored from MapLibre's persistent offline database, create a tile-pyramid pack, resume or pause it, and delete it. Region identity and provider identity are stored in the pack metadata so packs can be rediscovered after an app restart. The API exposes resource counts and error messages from MapLibre's progress state. The in-app **Offline maps** manager lists installed packs, refreshes their status, pauses or resumes active downloads, and removes packs with a confirmation step.\n\nThe common request validates geographic bounds, zoom levels and HTTPS style URLs. It also requires an explicit `providerAllowsOfflineDownloads = true` confirmation; this intentionally blocks the current OpenFreeMap development preview because its offline-download rights have not been verified. The adapter and manager do not yet provide a region picker or a production provider catalogue.\n\nThe remaining integration work must connect the native tile packs to TomOS's versioned `MapPackageManifest` without treating a map tile pack as a routing graph:

1. Select a map provider whose terms explicitly permit the intended offline downloads and distribution.
2. Add an offline-region catalogue with region bounds, zoom range, provider/style identity and estimated size.
3. Create, pause, resume, inspect and delete MapLibre offline packs.
4. Persist the pack ID and provider metadata in the TomOS local package database.
5. Keep routing graph files, search indexes and voice assets as separate package assets with their own hashes.
6. Verify map-pack availability after process restart and expose clear offline-ready status.
7. Ensure the active route remains usable without network access; map tiles alone do not provide route calculation.

Do not use the public OpenStreetMap standard tile server for bulk/offline downloads. Map data and tile-provider terms are separate from the MapLibre software licence.

## Limitations

- No production map provider has been selected.
- No offline map packs are currently installed in the project environment; the manager can inspect and manage packs created on a device.
- The custom TomOS archive importer and atomic package installer are not implemented.
- No local routing graph is loaded by the map renderer.
- Valhalla, route overlays from calculated routes and voice guidance are not connected.
- The current location feature is foreground-only and depends on platform permission/provider availability.
