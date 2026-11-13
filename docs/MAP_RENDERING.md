# Map rendering integration

## Renderer

TomOS Maps uses MapLibre Compose 0.20.0 from the shared `composeApp` module. The same Compose map screen is compiled for Android and Linux desktop.

- Android loads the MapLibre Vulkan runtime.
- Linux x64 loads the MapLibre Vulkan runtime and XDG location portal backend.
- The desktop window supplies MapLibre with its Compose GPU presentation host.
- MapLibre's default overlay is retained so provider attribution and standard map controls remain visible.

The initial camera is UK-wide. Pointer/touch gestures provide pan and pinch zoom.

## Location and camera tracking

The shared screen creates a foreground location state and draws a location indicator layer. Permission is requested through the user-initiated location/navigation flow. While the navigation preview is active, the camera follows the current fix and bearing.

Android uses foreground location permissions. Linux uses the desktop XDG location portal, commonly backed by GeoClue. Background location is not requested. Navigation depends on platform permission and location-provider availability.

## Calculated route overlay

A successful Valhalla route response is adapted into shared route geometry. The app serialises that geometry as a GeoJSON LineString and supplies it to a MapLibre GeoJSON source and line layer. Coordinates are emitted in GeoJSON order: longitude, latitude. The overlay is cleared when the navigation preview/session ends.

This is a rendered route line, not a guarantee that the route follows the road network accurately on every map style. Physical-device validation of route placement and GPS alignment remains outstanding.

## Development style

The current development style is OpenFreeMap Dark:

`https://tiles.openfreemap.org/styles/dark`

This is a network-backed style with offline-pack support. TomOS sends a unique app-specific User-Agent. A shared suspendable MapResourceProvider routes OpenFreeMap requests through a four-slot semaphore; offline-pack loads additionally consume a 3,000-tile / 50 MB quota. This keeps the provider's concurrency and per-download limits enforced across Android and Linux.

## Offline map integration

TomOS has a shared `OfflineRegionStore` contract and Android/Linux implementations backed by MapLibre native offline storage. The adapters can list packs restored from persistent storage, create tile-pyramid packs, resume or pause downloads and delete packs. Pack metadata records region and provider identity. The in-app **Offline maps** manager lists installed packs, refreshes status, pauses/resumes downloads and removes packs with confirmation.

The common request validates geographic bounds, zoom levels and HTTPS style URLs. It requires explicit `providerAllowsOfflineDownloads = true` confirmation. The OpenFreeMap provider is enabled through a request pipeline that applies an app-specific User-Agent, at most four concurrent requests and a 3,000-tile / 50 MB quota to each active offline pack. The manager offers compact city-centre presets for Liverpool, Manchester, Central London, Birmingham, Leeds, Glasgow and Edinburgh at zoom levels 10–16.

MapLibre tile packs and TomOS versioned packages are different layers. A tile pack does not contain a Valhalla routing graph, local search index or voice assets. Keep those resources separately versioned and checksummed.

Do not use the public OpenStreetMap standard tile server for bulk/offline downloads. Map-data licensing and tile-provider terms are separate from the MapLibre software licence.

## Limitations

- OpenFreeMap is the current development provider; its public service has no SLA and may change its terms or availability.
- Offline tile-pack downloads are enabled for small city-centre presets and are limited by the enforced four-request, 3,000-tile and 50 MB caps.
- Android and Linux desktop now have a verified ZIP archive installer with manifest/file checksum validation, safe staging and atomic package activation. Streaming downloads, signed catalogue verification and runtime wiring of installed packages remain outstanding.
- No local routing graph is loaded by the map renderer.
- Live route geometry is drawn after successful Valhalla calculation, but physical route-line/GPS alignment testing remains outstanding.
- Location is foreground-only and depends on platform permission/provider availability.
- Background navigation is not implemented; foreground spoken guidance is handled separately by the navigation module.
