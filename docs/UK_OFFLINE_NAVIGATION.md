# UK offline navigation programme

## Regional package model

One versioned regional package contains:

- MapLibre-compatible MBTiles map data.
- A Valhalla routing tile archive.
- A versioned postcode/place search index.
- Geographic coverage bounds and source attribution.
- A strict manifest with per-file byte counts and SHA-256 digests.

The shared manifest and search-index contracts are in
`composeApp/src/commonMain/kotlin/uk/co/tomosmaps/offline/`.

## Download and installation

The Android and Linux clients now include a regional download manager. The
pipeline verifies a publisher-signed Ed25519 catalogue, downloads HTTPS
manifests and archives with persistent partial files and HTTP Range resume,
reports progress, supports pause/resume/delete, checks manifest and archive
hashes, and passes only verified packages to the strict ZIP installer.

The installer rejects undeclared or duplicate files, unsafe paths and size/hash
mismatches. It stages privately and activates only after successful validation.
The previous active version is preserved when installation fails.

The publisher key and catalogue URLs are deliberately blank in
`RegionalPackageBuildConfig.kt`. The repository contains no private signing
key. The owner can add the Ed25519 public key and hosted catalogue URLs later;
until then, the UI keeps catalogue refresh disabled.

## Liverpool and North West data production

`tools/package-regional-data.py` packages already-prepared, licensed assets
into the exact manifest and ZIP format accepted by the app. It can create the
local postcode index from a CSV and filters records to the supplied WGS84
coverage. It emits manifest/archive checksums and metadata for a later signed
catalogue entry.

The tool does not fetch or invent source data. A release still needs a licensed
MapLibre-compatible MBTiles dataset, Valhalla graph tiles and postcode source
data for Liverpool City Region. See
[REGIONAL_DATA_PIPELINE.md](REGIONAL_DATA_PIPELINE.md) for the build command,
input format, Android engine location and publication steps.

## Offline postcode search

The regional manager can search installed indexes by full postcode, postcode
prefix, place name or category without network access. Route planning checks
for an exact full-postcode match in installed regional data before using the
online Postcodes.io lookup. A package advertises its WGS84 coverage, and a
regional route is selected only when both the current GPS fix and destination
are inside those bounds.

## Offline routing runtime

The shared offline routing client only accepts loopback Valhalla endpoints.
Linux starts a local `valhalla_service` process from the installed graph and
stops it when navigation ends. Set `TOMOS_VALHALLA_SERVICE` if the executable
is not on PATH.

Android looks for an ABI-matched `valhalla_service` at
`files/regional-packages/engines/<abi>/valhalla_service`. The binary is not
included in this repository yet; the release build must supply and test it for
each supported ABI. If it is missing or fails to start, the app reports that
offline routing is unavailable rather than silently using a remote router for
a journey covered by the installed region.

## Remaining release gates

- Produce and licence the real Liverpool map, routing and postcode datasets.
- Add the public signing key and catalogue URLs, then publish the signed feed.
- Bundle and validate Android Valhalla binaries for supported ABIs.
- Connect packaged MBTiles to MapLibre's offline renderer. The current map
  view still uses its configured style/provider; packaging MBTiles alone does
  not make the map render offline.
- Run interruption/restart, checksum failure, rollback, deletion and low-space
  tests on devices.
- Verify postcode search, map rendering and turn-by-turn route guidance with
  networking disabled on Android and Linux.

A successful compile or installed package is not proof of offline navigation.
Do not advertise a region as fully offline-ready until its map, search and
routing paths have all been exercised without network access.

## Engineering verification

The latest fix for the regional transfer result type passed both Android debug
build and Linux desktop/shared tests in GitHub Actions run
[43526611038](https://github.com/Spruhantom/Tomos-maps/actions/runs/43526611038).
Further CI validation is running for the additional manager, package builder
and local runtime integration.
