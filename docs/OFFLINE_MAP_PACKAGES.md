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

The Android and Linux desktop clients now share a ZIP archive installer. It verifies the expected manifest SHA-256, rejects undeclared or duplicate archive entries, constrains extraction to a private staging directory, enforces declared file and total sizes, verifies every file SHA-256, and only activates a version after successful extraction using an atomic directory/pointer move. The installer preserves the previously active version if validation fails. The expected manifest digest must come from a trusted, signed catalogue; a bare SHA-256 is not publisher authentication. The remaining platform transfer and publisher-signature work must:

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

## Current provider and download gate

The development style is OpenFreeMap Dark. OpenFreeMap's current terms permit a single offline download of up to 3,000 tiles or 50 MB, with no more than four parallel requests and a unique application-identifying User-Agent. See [OpenFreeMap terms](https://openfreemap.org/tos/).

TomOS configures an application-specific User-Agent for requests to the OpenFreeMap host. However, this project currently uses MapLibre Compose 0.20.0, whose public `MapRuntimeOptions` API does not expose a supported setting to reduce native request concurrency. MapLibre Native's documented default maximum is 20 concurrent requests. Therefore, new offline downloads remain disabled until TomOS can enforce the provider's four-request limit. This is an implementation restriction, not a claim that OpenFreeMap prohibits offline downloads.

The existing manager can still list, pause, resume and delete previously installed packs. The broad UK presets are coverage ideas, not approved downloads: a future downloader must provide small, user-selected areas and enforce both the tile/byte ceiling and request concurrency before starting a pack.

## UK regional navigation package

The coordinated offline-navigation scope is documented in [UK_OFFLINE_NAVIGATION.md](UK_OFFLINE_NAVIGATION.md). The initial region is Liverpool City Region, followed by North West England. A complete package must include map data, Valhalla routing graph data and a postcode/place search index. The shared package transfer contract exposes progress, pause, resume and deletion states. The verified archive installer is implemented on Android and Linux desktop, but a streaming HTTP downloader with persistent Range-resume state is still outstanding.

## Package distribution

Packages must be produced from appropriately licensed OpenStreetMap-derived
data and include required attribution. Do not fetch bulk map tiles from the
public OpenStreetMap standard tile service. Offline distribution must use a
provider or self-hosted pipeline whose terms explicitly permit downloads and
offline use.

The manifest is a shared data contract, not yet a finished archive format:
serialization, compression, signing, downloading, installation and package
updates remain future implementation work.
