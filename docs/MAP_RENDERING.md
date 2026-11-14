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

The current online preview uses OpenFreeMap Liberty, a light road-map style chosen to give TomOS a familiar mainstream navigation-map appearance without using Google Maps content:

`https://tiles.openfreemap.org/styles/liberty`

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


## Optional Google Maps online provider (Android)

Android can optionally display Google Maps through the Maps Compose library. The provider is selected explicitly in the map screen; MapLibre remains the default and is the only renderer used for TomOS offline map packs. Linux continues to use MapLibre.

Configure a restricted Google Maps SDK for Android API key locally. Supply it as either a Gradle property or an environment variable named `TOMOS_GOOGLE_MAPS_API_KEY`. For example, add the following to the user's Gradle properties file (not this repository):

```properties
TOMOS_GOOGLE_MAPS_API_KEY=YOUR_RESTRICTED_ANDROID_MAPS_KEY
```

Alternatively, export the environment variable before running Gradle. Restrict the key in Google Cloud to the Android application ID and signing certificate fingerprints, and restrict API usage to Maps SDK for Android. Billing and the Maps SDK must be enabled in the Google Cloud project. Do not commit keys, `local.properties`, secrets files or production credentials.

The Google provider is online-only. Google map tiles, routes, place content and other Google Maps Content are not copied into TomOS packages, cached for offline use, or used to build offline search/routing data. The Google map is hidden when the user switches back to the TomOS renderer. Offline map downloads, Valhalla routing and local postcode search continue to use independent, appropriately licensed data sources.

Google Maps SDK attribution and controls are provided by the SDK. The optional Google map layer currently draws TomOS-calculated route geometry as a visual overlay; route calculation and voice guidance remain owned by TomOS. A valid API key and Google Play services are required to verify the live Android map at runtime.
