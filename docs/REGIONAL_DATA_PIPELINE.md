# Liverpool and North West regional data pipeline

This document describes the implemented package flow and the inputs still
needed to publish real regional data. The application never downloads bulk
tiles from the public OpenStreetMap tile server.

## What is implemented

- A regional download manager is available from **Regional data** in the map UI.
- The regional package includes local glyph resources, so the offline style renders place, road and water labels without contacting a font host. The app stores regional downloads in Android private app storage or
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

## Build the Valhalla graph from a regional OSM extract

With the Valhalla command-line tools installed and a licensed regional PBF
already downloaded, build the routing graph without contacting a tile server:

```bash
tools/build-valhalla-region.sh \
  /data/licensed/liverpool-city-region.osm.pbf \
  ./build/liverpool/valhalla
```

The script runs `valhalla_build_config`, timezone/admin database generation
and `valhalla_build_tiles`, then creates `tiles.tar` for the regional package.
It deliberately does not download the OSM extract. See the
[Valhalla build guide](https://valhalla.github.io/valhalla/building/) for
installation and source-data details.

A MapLibre-compatible MBTiles file must still be produced separately from
licensed source data (for example, using a configured Planetiler pipeline or
an offline-permitted map provider). Pass that MBTiles file and the generated
`tiles.tar` to the package builder below.

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
  --routing-admin-data /data/liverpool/valhalla/admins.sqlite \
  --routing-timezone-data /data/liverpool/valhalla/timezones.sqlite \
  --routing-config /data/liverpool/valhalla/valhalla.json \
  --glyphs-dir /data/liverpool/glyphs \
  --postcode-csv /data/liverpool/postcodes.csv \
  --output ./dist/liverpool \
  --west <west-longitude> --south <south-latitude> \
  --east <east-longitude> --north <north-latitude>
```

The tool creates three files:

- `<id>-<version>.manifest.json`: versioned manifest, file sizes and checksums for map tiles, local glyphs, routing graph, Valhalla admin/timezone databases and its full runtime configuration and search index.
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

## Assemble and sign the publisher catalogue

After uploading the generated manifest and ZIP files to an HTTPS directory,
assemble the catalogue from one or more package metadata files:

```bash
python3 tools/build-signed-catalogue.py \
  --package-metadata ./dist/liverpool/liverpool-city-region-2026.11.1.metadata.json \
  --base-url https://downloads.example.org/tomos-maps/regions/ \
  --output ./dist/catalogue.json
```

Without a private key this writes an unsigned catalogue for review. When the
publisher is ready, supply an Ed25519 PEM private key held outside the
repository:

```bash
python3 tools/build-signed-catalogue.py \
  --package-metadata ./dist/liverpool/liverpool-city-region-2026.11.1.metadata.json \
  --base-url https://downloads.example.org/tomos-maps/regions/ \
  --output ./dist/catalogue.json \
  --private-key /secure/path/tomos-publisher-ed25519.pem
```

The tool verifies that the local manifest and archive match their metadata,
then writes a base64 Ed25519 signature and prints the X.509 DER-encoded
Ed25519 public key in base64 for pinning in `RegionalPackageBuildConfig.kt`. It never generates or stores
a private key. Publish the catalogue and signature at the HTTPS URLs configured
in the app.

## Local routing runtime

The regional build command is `tools/build-liverpool-region.sh`. It downloads
the Geofabrik Merseyside PBF, verifies its MD5 sidecar, builds OpenMapTiles
vector MBTiles with Planetiler, creates a Valhalla graph from the same PBF,
downloads the Great Britain OS Code-Point Open CSV through the public OS
Downloads API, converts British National Grid coordinates to WGS84, filters
postcode points to the regional bounds, and packages MBTiles, glyph PBFs,
postcode search data, the Valhalla tile archive, admin/timezone databases and
the full Valhalla configuration. It also starts the newly built local Valhalla
service and verifies that a Liverpool route returns geometry and turn-by-turn
manoeuvres before packaging.

Run the full package build with Docker, `osmium-tool` and Python `pyproj` installed:

```bash
sudo apt-get install osmium-tool
python3 -m pip install pyproj
bash tools/build-liverpool-region.sh
```

The build writes the unsigned package, manifest and metadata under
`dist/liverpool`. The `Liverpool offline navigation end-to-end` GitHub
Actions workflow uploads the package as a short-lived artifact and runs the
Android native route test with airplane mode enabled. The signed catalogue is
still a separate owner-controlled publication step.

### Linux desktop

Install a compatible `valhalla_service` executable and make it available on
`PATH`, or set `TOMOS_VALHALLA_SERVICE` to its absolute path. The desktop
runtime reads the full packaged `routing/valhalla.json`, rewrites tile,
admin and timezone paths to the installed package, binds the service to
loopback only, waits for it to accept connections, and terminates it when
navigation ends. Logs are kept under the regional package storage directory
in `valhalla-runtime`.

### Android

Android uses the upstream Valhalla Mobile JNI library
(`io.github.rallista:valhalla-mobile:0.6.4`) rather than launching a shell
service. Its Maven AAR bundles native Valhalla shared libraries for supported
Android ABIs. TomOS reads the packaged `routing/valhalla.json`, rewrites
tile, admin and timezone paths to the installed package, creates a native
engine from `routing/tiles.tar`, calls Valhalla in-process, and closes the
native actor when navigation ends. The runtime does not require INTERNET permission for
routing and never contacts a remote routing endpoint. The library's published
source and native build instructions are documented at
https://github.com/Rallista/valhalla-mobile.

The Android instrumentation workflow installs the generated Liverpool package,
disables Wi-Fi/mobile data, verifies the local MBTiles database, builds the
MapLibre `mbtiles:` style, and requests a route plus manoeuvres from the native
Valhalla engine.

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
