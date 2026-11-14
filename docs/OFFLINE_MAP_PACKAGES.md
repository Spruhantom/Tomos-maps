# Offline map package format

TomOS Maps uses versioned regional packages so Android and Linux can install
the same verified data bundle.

## Manifest contract

The shared model is
`composeApp/src/commonMain/kotlin/uk/co/tomosmaps/offline/MapPackageManifest.kt`.
Format version 1 contains a stable package ID, display name, package version,
region code, uncompressed aggregate size, optional WGS84 coverage bounds and a
list of files. Every file has a safe relative path, role, byte count and
SHA-256 digest. Roles include MapLibre MBTiles, local glyph resources, the Valhalla routing
graph, Valhalla admin/timezone support databases, full Valhalla runtime configuration and postcode/place search index. Map data and routing graph roles are mandatory; the search
index is included in the Liverpool package builder.

The decoder rejects unsupported versions, duplicate paths, invalid
identifiers, missing required assets, mismatched aggregate sizes and unsafe
paths. The installer additionally rejects undeclared/duplicate ZIP entries,
path traversal, files exceeding declared sizes and checksum mismatches.

## Signed catalogue and transfer

The app accepts regional descriptors only from an Ed25519-verified catalogue
using the app-pinned public key. Catalogue entries pin HTTPS manifest/archive
URLs and SHA-256 digests. The Android and Linux transfer clients stream into
persistent partial files, resume with HTTP Range, expose progress and support
pause/resume/delete. They verify the manifest digest, package identity/version
and archive digest before installation.

The owner must still configure the production public key and catalogue URLs in
`RegionalPackageBuildConfig.kt`. The private signing key must never be stored
in this repository. Until the public key and signed feed are configured, the
regional download screen remains present but catalogue refresh is disabled.

## Regional data builder

Run `python3 tools/package-regional-data.py --help` for the complete options.
It accepts licensed MapLibre-compatible MBTiles, a Valhalla tile archive or
tile directory, and an optional postcode CSV. It produces:

- A manifest JSON with per-file hashes and coverage bounds.
- A ZIP archive containing exactly the declared files.
- Metadata containing archive and manifest hashes for a later signed
  catalogue entry.

The tool is offline and deterministic with respect to its input assets. It
does not download datasets, choose coverage bounds, sign or publish catalogues.
See [REGIONAL_DATA_PIPELINE.md](REGIONAL_DATA_PIPELINE.md) for full details.

## MapLibre preview policy

The current OpenFreeMap preview downloader remains a separate feature with its
existing provider limits: no more than four simultaneous requests and no more
than 3,000 tiles or 50 MB per permitted offline pack. Do not use the public
OpenStreetMap standard tile service for bulk tile downloads. A complete
regional MBTiles dataset must come from a provider or self-hosted pipeline
whose licence permits offline use and redistribution.

## Runtime integration status

The regional manager lists installed packages and searches their postcode/place
indexes locally. Route planning prefers an exact postcode match from an
installed regional search index. When the origin and destination are both
inside an installed package's coverage, the app uses its loopback-only local
Valhalla runtime.

Linux launches a locally installed `valhalla_service`. Android expects a
matching ABI executable under the app's private `engines/<abi>/` directory;
the engine binaries still need to be built and packaged. Packaged MBTiles are
not yet connected to MapLibre's renderer, so the current map view is not yet
fully offline even when a regional package has been installed.
