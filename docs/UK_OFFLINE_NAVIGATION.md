# UK offline navigation programme

## One region package, five capabilities

The offline-navigation package is the single source of truth for a region. A
complete package contains:

- A MapLibre-compatible map dataset and style resources.
- A Valhalla routing graph/tile set for the same geographic coverage.
- A versioned postcode/place search index.
- Attribution, provider licence metadata, coverage bounds and package version.
- SHA-256 checksums for every file and a signed manifest for publisher identity.

The manifest contract is in `offline/MapPackageManifest.kt`. The search-index
contract is in `offline/OfflineSearchIndex.kt`. Do not activate partial packages:
map, routing and search coverage must be declared and validated together.

## Initial UK coverage

The first deliverable is Liverpool City Region, followed by a larger North West
England package. The city package should be a smaller download and validation
target before the wider region. Packages must advertise their actual coverage
bounds; postcode lookup must never imply coverage outside an installed package.

## Download lifecycle

The transfer layer exposes queued, downloading, paused, verifying, installing,
complete and failed states, with byte-level progress. The Android and Linux desktop archive installer is now implemented. It stages ZIP contents privately, rejects undeclared/duplicate entries and unsafe paths, enforces manifest sizes, verifies file SHA-256 checksums, and activates only after successful validation. It uses an atomic directory move and atomic active-version pointer replacement. A trusted signed catalogue and streaming resumable downloader are still required before packages can be distributed. Platform transfer implementations must:

1. Stream into private staging storage, never directly over the active package.
2. Persist byte offsets and use HTTP Range for resumable transfers.
3. Enforce declared and configured maximum download/extraction sizes.
4. Verify the manifest digest, manifest signature, file lengths and SHA-256
   checksums before extraction/activation.
5. Reject undeclared archive entries, path traversal, symlinks and special files.
6. Atomically switch the active package only after every asset validates.
7. Preserve the previous package if a download or install fails.
8. Pause by closing the active transfer cleanly and resume from the verified
   partial offset; delete must remove staging and installed assets safely.

## Offline map rendering

MapLibre's native offline tile-pyramid support remains suitable for bounded
region downloads when the style and all referenced resources are served by a
provider whose licence explicitly permits offline use. A complete North West
package must not be fetched from the public OpenStreetMap standard tile server.
The existing OpenFreeMap Liberty preview is not approved as a bulk regional
package source. Production regional downloads require a self-hosted or
commercially licensed tile pipeline with documented redistribution rights.

## Offline routing

The shared `OfflineValhallaRoutingClient` sends route requests only to a
loopback Valhalla endpoint. The endpoint must be started locally and configured
against the installed package's routing graph. It must not silently fall back
to a remote routing API. Android needs a bundled/managed native Valhalla runtime;
Linux needs a packaged Valhalla executable/service. Both need lifecycle,
storage-path, ABI, resource and real-device validation before offline routing
can be called complete.

## Offline postcode and place search

The package's search index is loaded locally by `OfflineSearchIndex`. It
supports full postcode, postcode-prefix, place-name and category matching
without network access. Search results include WGS84 coordinates for map
positioning and routing. A postcode result is only offered when its record is
inside the installed region's coverage. Online Postcodes.io remains a fallback
only when connectivity is available and the user has no local match.

## Release gates

- Build and test shared code on CI.
- Verify signed catalogue authenticity and checksum failure paths.
- Exercise Android and Linux archive installation, rollback and deletion tests.
- Test pause/resume after app restart and interrupted network.
- Verify map rendering with network disabled.
- Verify local Valhalla routes with network disabled on Android and Linux.
- Verify postcode-prefix search with network disabled.
- Test upgrade, rollback, deletion and low-storage behaviour.
- Publish package sizes, coverage bounds, source attribution and licence.

A package schema or a loopback client is not proof that the platform runtime or
regional datasets exist. Do not advertise offline routing as available until
the required local Valhalla engine and region graph are installed and tested.

## Engineering verification

The shared test suite, Android debug APK build, and Linux desktop distribution build passed in GitHub Actions run [43486358756](https://github.com/Spruhantom/Tomos-maps/actions/runs/43486358756) on 13 November 2026. This verifies compilation and automated tests for the current offline-navigation foundations; it does not replace device testing with networking disabled or prove that regional package downloads and local Valhalla runtimes are implemented.
