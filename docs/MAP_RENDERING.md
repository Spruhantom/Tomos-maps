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

The current development style is OpenFreeMap Liberty:

`https://tiles.openfreemap.org/styles/liberty`

This is a network-backed preview, not a bundled offline map. Before production use, verify the provider's terms, rate limits, offline-download permissions and attribution requirements.

## Offline map integration

TomOS has a shared `OfflineRegionStore` contract and Android/Linux implementations backed by MapLibre native offline storage. The adapters can list packs restored from persistent storage, create tile-pyramid packs, resume or pause downloads and delete packs. Pack metadata records region and provider identity. The in-app **Offline maps** manager lists installed packs, refreshes status, pauses/resumes downloads and removes packs with confirmation.

The common request validates geographic bounds, zoom levels and HTTPS style URLs. It requires explicit `providerAllowsOfflineDownloads = true` confirmation. The current OpenFreeMap development preview remains blocked because offline-download rights have not been verified. The starter UK catalogue contains Greater London, South East England, the Midlands, North West England and Central Scotland. These are geographic presets only; they do not provide tile-count or size estimates and are not downloadable while the provider is unapproved.

MapLibre tile packs and TomOS versioned packages are different layers. A tile pack does not contain a Valhalla routing graph, local search index or voice assets. Keep those resources separately versioned and checksummed.

Do not use the public OpenStreetMap standard tile server for bulk/offline downloads. Map-data licensing and tile-provider terms are separate from the MapLibre software licence.

## Limitations

- No production map provider has been selected.
- Offline pack storage and management are implemented, but new downloads are policy-blocked for the current preview provider.
- The custom TomOS archive importer and atomic package installer are not implemented.
- No local routing graph is loaded by the map renderer.
- Live route geometry is drawn after successful Valhalla calculation, but physical route-line/GPS alignment testing remains outstanding.
- Location is foreground-only and depends on platform permission/provider availability.
- Background navigation and voice guidance are not implemented.
