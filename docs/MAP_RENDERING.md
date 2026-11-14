# Map rendering integration

## Renderer

TomOS Maps uses MapLibre Compose 0.20.0 on Android and Linux desktop. MapLibre is the only map renderer in the application; the optional Google Maps provider has been removed.

- Android loads the MapLibre Vulkan runtime.
- Linux x64 loads the MapLibre Vulkan runtime and XDG location portal backend.
- The desktop window supplies MapLibre with its Compose GPU presentation host.
- MapLibre's default overlay is retained so provider attribution and standard map controls remain visible.

The initial camera is UK-wide. Pointer/touch gestures provide pan and pinch zoom.

## Online map style

The default map style is OpenFreeMap Liberty:

`https://tiles.openfreemap.org/styles/liberty`

It is a network-backed road-map style based on OpenStreetMap data. OpenStreetMap attribution is supplied through the map style. TomOS uses the online style on both Android and Linux.

Do not use the public OpenStreetMap standard tile server for bulk/offline downloads. Map-data licensing and tile-provider terms are separate from the MapLibre software licence.

## Location and camera tracking

The shared screen creates a foreground location state and draws a location indicator layer. Permission is requested through the user-initiated location/navigation flow. While the navigation preview is active, the camera follows the current fix and bearing.

Android uses foreground location permissions. Linux uses the desktop XDG location portal, commonly backed by GeoClue. Background location is not requested. Navigation depends on platform permission and location-provider availability.

## Calculated route overlay

A successful Valhalla route response is adapted into shared route geometry. The app serialises that geometry as a GeoJSON LineString and supplies it to a MapLibre GeoJSON source and line layer. Coordinates are emitted in GeoJSON order: longitude, latitude. The overlay is cleared when the navigation preview/session ends.

This is a rendered route line, not a guarantee that the route follows the road network accurately on every map style. Physical-device validation of route placement and GPS alignment remains outstanding.

## Download status

Map-area downloads, offline tile-pack creation and regional package download controls have been removed from the app UI. Regional catalogue URLs remain unset and the regional-download feature flag remains disabled. Previously installed packages are not automatically deleted, but new map downloads cannot be started from the application.

## Limitations

- OpenFreeMap is the current development provider; its public service has no SLA and may change its terms or availability.
- Online map rendering requires an internet connection.
- Route calculation uses the configured Valhalla HTTPS endpoint unless a previously installed local routing package is available.
- Live route geometry is drawn after successful route calculation, but physical route-line/GPS alignment testing remains outstanding.
- Location is foreground-only and depends on platform permission/provider availability.
- Background navigation is not implemented; foreground spoken guidance is handled separately by the navigation module.
