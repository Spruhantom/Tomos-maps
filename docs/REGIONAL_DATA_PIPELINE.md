# Liverpool and North West regional data pipeline

This document describes the implemented package flow and the inputs still
needed to publish real regional data. The application never downloads bulk
tiles from the public OpenStreetMap tile server.

## What is implemented

- A regional download manager is available from **Regional data** in the map UI.
- The app stores regional downloads in Android private app storage or
  `~/.local/share/tomos-maps/regional-packages` on Linux.
- The downloader accepts package descriptors only after verifying the
  publisher-signed Ed25519 catalogue.
- HTTPS downloads stream to persistent partial files, support HTTP Range
  resume, expose progress and pause/resume/delete controls, and verify the
  manifest and archive SHA-256 values before installation.
- The installer validates every declared file and atomically activates the
  package only after extraction and checksums succeed.
- The package manager lists installed regions and can query their local UK
  postcode/place search indexes.
- Navigation checks an installed package's coverage before routing. It uses
  that package's local Valhalla runtime when the origin and destination are
  both covered; it does not silently send that covered journey to a remote
  router if the local engine fails.
- Package manifests now carry WGS84 coverage bounds so the app can avoid
  claiming a region supports journeys outside its data.

## Build a regional package from licensed source files

The packaging tool is standard-library Python and does not need network access.
It takes already-prepared assets:

1. A MapLibre-compatible MBTiles dataset with a redistribution licence.
2. A Valhalla `tiles.tar` archive, or a directory containing the Valhalla graph
   tiles built from the same geographic extract.
3. An optional postcode CSV. Default columns are `postcode`, `latitude`,
   `longitude`, and optional `label` and `category`. Records outside the
   declared coverage are discarded.

Example (replace paths and bounds with the exact licensed data coverage):

```bash
python3 tools/package-regional-data.py \
  --id liverpool-city-region \
  --name "Liverpool City Region" \
  --version 2026.11.1 \
  --region-code UK-LIV \
  --map-data /data/liverpool/region.mbtiles \
  --routing-data /data/liverpool/valhalla/tiles.tar \
  --postcode-csv /data/liverpool/postcodes.csv \
  --output ./dist/liverpool \
  --west <west-longitude> --south <south-latitude> \
  --east <east-longitude> --north <north-latitude>
```

The tool creates three files:

- `<id>-<version>.manifest.json`: versioned manifest, file sizes and checksums.
- `<id>-<version>.zip`: ZIP containing exactly the manifest-declared assets.
- `<id>-<version>.metadata.json`: archive/manifest checksums, coverage and
  package metadata for the publisher catalogue.

The manifest's `totalSizeBytes` is the sum of the uncompressed declared asset
sizes, as required by the installer. The package archive SHA-256 and manifest
SHA-256 are emitted for the signed catalogue entry.

The builder deliberately does not download source datasets, invent bounds,
sign catalogues, or publish files. Use an appropriately licensed extract and
retain the licence/attribution details with the release. OSM-derived data is
subject to ODbL attribution and share-alike obligations; verify the licence of
the MBTiles style/data provider and postcode source independently.

## Local routing runtime

### Linux desktop

Install a compatible `valhalla_service` executable and make it available on
`PATH`, or set `TOMOS_VALHALLA_SERVICE` to its absolute path. The desktop
runtime creates a loopback-only service configuration for the installed
region's routing tile directory, waits for the service to accept connections,
and terminates it when navigation ends. Logs are kept under the regional
package storage directory in `valhalla-runtime`.

### Android

The Android launcher selects the device's first supported ABI and copies the
matching executable from this app asset location on first launch:

```
androidApp/src/main/assets/engines/<android-abi>/valhalla_service
```

It is copied to the private runtime location
`files/regional-packages/engines/<android-abi>/valhalla_service` and marked
executable. The engine binary is intentionally not checked into this
repository. A release must build/package a Valhalla binary for each supported
Android ABI, verify its licence and dependencies, and exercise startup/routing
on real devices. If the binary is absent or fails to start, offline routing
reports a clear error.

## Still required before a real Liverpool offline release

- Supply the licensed Liverpool MBTiles, Valhalla graph and postcode dataset.
- Run the packaging tool with exact coverage bounds and review attribution.
- Publish manifest and ZIP over HTTPS, then add their descriptor to a
  publisher-signed catalogue. The app's Ed25519 public key and catalogue URLs
  are intentionally blank until the owner configures them in
  `RegionalPackageBuildConfig.kt`. Keep the private signing key out of Git.
- Bundle and test Android Valhalla binaries for the supported ABIs.
- Connect packaged MBTiles to MapLibre's offline renderer. The current
  MapLibre preview still uses its configured style/provider; packaging a
  `.mbtiles` file alone does not make the basemap render offline.
- Test download interruption/restart, failed checksum, upgrade rollback,
  deletion, postcode lookup and routing with networking disabled on Android
  and Linux.

Do not call the region offline-ready until the map renderer, postcode index and
local routing engine all work from the installed package with networking off.
