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

## Package distribution

Packages must be produced from appropriately licensed OpenStreetMap-derived
data and include required attribution. Do not fetch bulk map tiles from the
public OpenStreetMap standard tile service. Offline distribution must use a
provider or self-hosted pipeline whose terms explicitly permit downloads and
offline use.

The manifest is a shared data contract, not yet a finished archive format:
serialization, compression, signing, downloading, installation and package
updates remain future implementation work.
