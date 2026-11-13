# Offline map package format

TomOS Maps uses versioned regional packages so the same downloaded region can be
installed by the Android and Linux clients.

## Current shared contract

The shared Kotlin model is in
`composeApp/src/commonMain/kotlin/uk/co/tomosmaps/offline/MapPackageManifest.kt`.

Manifest format version 1 contains:

- A stable package ID, display name, package version and region code.
- The declared total archive size.
- A list of files, each with a role, relative path, byte size and SHA-256 digest.
- Required `MAP_DATA` and `ROUTING_GRAPH` assets.
- Optional `SEARCH_INDEX` and `VOICE_GUIDANCE` assets.

The shared model rejects duplicate paths, invalid identifiers, unsupported
manifest versions, mismatched aggregate sizes, missing required assets and
unsafe paths. Paths are slash-separated relative paths; absolute paths,
traversal segments, backslashes and drive-style paths are rejected.

## Installer requirements

The platform installer is not implemented yet. When it is added, it must:

1. Parse the manifest with strict size and nesting limits.
2. Reject archive entries not declared in the manifest.
3. Resolve every entry beneath a private staging directory and verify the
   canonical destination remains inside that directory.
4. Enforce per-file and total uncompressed size limits before extraction.
5. Verify each file's exact byte count and SHA-256 digest.
6. Require both map data and routing graph assets before installation succeeds.
7. Keep the previous installed package intact until the new package has passed
   all checks, then atomically switch the active package.
8. Never extract symlinks, device files, absolute paths or parent traversal.
9. Support cancellation and remove incomplete staging data on failure.
10. Keep map rendering and route calculation fully offline once package data is
    installed.

A SHA-256 digest protects against accidental corruption; it does not prove who
published a package. Package authenticity/signatures and a trusted distribution
index will be designed separately before public map downloads are enabled.

## Current provider and enforced limits

The current map style is OpenFreeMap Dark. OpenFreeMap's terms permit a single offline download of up to 3,000 tiles or 50 MB, with no more than four parallel requests and a unique application-identifying User-Agent. See [OpenFreeMap terms](https://openfreemap.org/tos/).

TomOS enforces these conditions in `OpenFreeMapResourcePipeline`:

- All resources for `https://tiles.openfreemap.org/` are supplied through MapLibre Compose's suspendable `MapResourceProvider` hook.
- A single shared coroutine semaphore allows at most four OpenFreeMap HTTP requests at once, including map display and offline-pack resource loads.
- Every HTTP request uses the TomOS Maps User-Agent.
- Offline-pack loads are identified by MapLibre's request usage. `OpenFreeMapOfflineQuota` allows only one active pack, counts tile requests before they are sent, reserves known response sizes before reading, and rejects further resources when the 3,000-tile or 50 MB limit would be exceeded.
- The manager exposes small city-centre presets (Liverpool, Manchester, Central London, Birmingham, Leeds, Glasgow and Edinburgh) at zoom levels 10–16. It does not offer country- or county-scale bulk downloads.
- Only the approved OpenFreeMap style host can be used by this downloader. The platform store rejects other providers and non-OpenFreeMap style URLs.

If a provider request fails or a quota is reached, the pack reports a download error and the user can inspect or remove the incomplete pack. Only one region should be downloaded at a time. The map pack contains the style, tiles and dependent map resources; it does not include a Valhalla routing graph, postcode search data or voice assets.


## Package distribution

Packages must be produced from appropriately licensed OpenStreetMap-derived
data and include required attribution. Do not fetch bulk map tiles from the
public OpenStreetMap standard tile service. Offline distribution must use a
provider or self-hosted pipeline whose terms explicitly permit downloads and
offline use.

The manifest is a shared data contract, not yet a finished archive format:
serialization, compression, signing, downloading, installation and package
updates remain future implementation work.
