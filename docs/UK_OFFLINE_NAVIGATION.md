# UK offline navigation programme

## Current status: regional package downloads paused

The Liverpool/North West regional map-package download and publishing work is
paused. The previous data-source approach is not being treated as production
ready. The app's regional package download controls are disabled, the signed
catalogue endpoints are unset, and the Liverpool city-centre download preset
has been removed. The Liverpool build/integration and England publishing
workflows are disabled so they cannot consume CI time or publish incomplete
regional data.

Existing installed regional packages remain readable/searchable and the
shared offline map, routing, postcode and package infrastructure is retained.
This is a pause, not a deletion of the reusable implementation. Re-enable
regional downloads only after a reliable, licensed data source and package
production process have been agreed and tested.

## Regional package model

One versioned regional package contains:

- MapLibre-compatible MBTiles map data.
- A Valhalla routing tile archive plus its admin and timezone support databases.
- A versioned postcode/place search index.
- Geographic coverage bounds and source attribution.
- A strict manifest with per-file byte counts and SHA-256 digests.

The shared manifest and search-index contracts are in
`composeApp/src/commonMain/kotlin/uk/co/tomosmaps/offline/`.

## Download and installation architecture retained

The Android and Linux clients retain the regional package transfer and
installation implementation: signed-catalogue verification, resumable HTTPS
transfers, progress reporting, pause/resume/delete, integrity checks and safe
package activation. New regional downloads are currently disabled by the
`REGIONAL_DOWNLOADS_ENABLED` switch in `RegionalPackageBuildConfig.kt`.

The publisher key and catalogue URLs are unset. No private signing key is
stored in the repository.

## Future data-source requirements

Before re-enabling downloads, select a source whose licence permits the
intended offline use and redistribution, define a repeatable bounded-area
extract process, verify provider checksums and geographic coverage, and
produce a real map/routing/search package. Validate the full package on Android
and Linux with networking disabled before publishing it.

## Offline postcode search and routing

The existing regional manager can search installed indexes by full postcode,
postcode prefix, place name or category without network access. Route planning
can use installed regional data where coverage and runtime support are present.
The Android and Linux offline routing implementations remain in the codebase.

## Engineering verification

A successful compile or installed package is not proof of offline navigation.
Do not advertise a region as fully offline-ready until its map, search and
routing paths have all been exercised without network access.
