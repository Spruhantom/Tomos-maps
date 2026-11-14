> **Status update (14 November 2026):** Map-area and regional-package downloads have been removed from the TomOS Maps app UI. The regional download feature flag remains disabled. This document is retained as implementation/history reference only; do not treat the workflows described below as currently available.

# Custom offline map areas

The Offline maps manager supports an area centred on the current map view (or
coordinates entered by the user). The initial presets are Small (1 km radius),
City (3 km radius, selected by default) and Large (5 km radius). Each selection
creates a rectangular WGS84 bounding box and requests a MapLibre tile pyramid
from zoom 10 through zoom 16.

## Layer selection and estimates

- **Map background** is downloadable for a custom area. The estimator counts
  the Web Mercator tile pyramid at every requested zoom and displays a
  conservative 8–24 KiB per tile range. This is a planning estimate, not a
  provider quote: actual bytes depend on the local density, style resources,
  compression and cache responses.
- **Roads and routing** can currently be obtained only from a published,
  signed regional package. The app does not yet generate custom-area Valhalla
  graphs or estimate their size independently.
- **UK postcodes and places** can currently be obtained only from a published,
  signed regional package. Custom-area postcode/place index generation is not
  yet available.

The UI keeps these layer limitations explicit. Selecting routing or search
does not silently add them to a map-only tile download; use the Regional data
manager for the existing combined regional package.

## Provider limits and download management

The current OpenFreeMap preview integration enforces a maximum of 3,000 tile
requests or 50 MiB per offline pack, with at most four simultaneous resource
requests. The planner estimates the tile pyramid before enabling a download;
the resource pipeline remains the final authority and can stop a pack if
actual provider requests exceed its limits.

Custom map areas are stored in MapLibre's native offline database. Existing
pause, resume, progress, refresh and delete operations remain available. Online
map browsing remains the default when no installed offline style is selected.

The initial implementation uses a coordinate-centred rectangle rather than
a draggable map polygon. A later UI enhancement can add direct drag-to-select
bounds without changing the shared area-planning model.
## Per-layer package sizes

Published regional package manifests declare the size of every file. The app
now groups those verified manifest sizes into three independent totals:

- **Map:** MBTiles plus MapLibre glyph resources.
- **Roads & routing:** the Valhalla graph plus routing support databases.
- **Postcodes & search:** the local postcode/place search index.

These figures are exact manifest-declared uncompressed bytes for the published
regional package, and are shown separately from the archive download estimate.
They are not presented as a custom-city estimate: a custom area does not yet
have its own routing-graph or search-index build pipeline. The city-area screen
continues to estimate map tiles and explicitly directs users to the signed
regional package manager for roads/routing and search. This avoids inventing
size figures or implying that unavailable custom layers were downloaded.


## Custom-area data builders (initial implementation)

The repository now includes two independent build entry points for a selected
WGS84 rectangle:

- `tools/build-custom-area-routing.sh <source.osm.pbf> <output-directory> <west> <south> <east> <north> [buffer-degrees]`
  extracts the requested area with complete ways and a configurable surrounding
  buffer, then builds a standalone Valhalla graph, administrative database,
  timezone database and Valhalla configuration. It uses the pinned build
  environment used by the regional graph builder (Docker by default).
- `tools/build-custom-area-search.py` accepts licensed postcode and/or place
  CSV files and emits the same version-1 JSON record format consumed by
  `OfflineSearchIndex`. It clips records to the selected bounds, validates
  coordinates and IDs, rejects empty indexes and enforces the app's record and
  byte limits.

These are build-stage foundations: they produce independently sized routing
and search artifacts, but they are not yet connected to a signed per-layer
catalogue, resumable per-layer transfer/install API, or the custom-area UI.
The existing app continues to disable those download actions until that
verified installation path is complete. The map layer remains independently
downloadable through MapLibre's offline-region store.

## Combined storage and offline validation

The target UX is one combined selection with per-layer progress, aggregate
bytes received, and installed storage split into map, routing and search. That
requires the app's package transfer contract to represent a set of independently
signed layer artifacts and to install/remove each layer atomically. Until that
contract lands, the existing signed regional package remains the supported
all-in-one offline option.

CI now syntax-checks the custom routing builder and runs the custom search
index tests. These checks validate the builder contract; they do not replace
Android emulator airplane-mode tests or Linux runtime tests with networking
disabled. Those runtime checks remain required before the feature can be
called complete.


## Independent signed custom-area layer packages

The custom-area layer pipeline now has a separate package contract alongside the
legacy all-in-one regional archive:

- `OfflineLayerPackageDescriptor` identifies one area, one layer and one
  version. The signed catalogue includes separate manifest/archive URLs,
  SHA-256 digests, archive transfer bytes and installed storage bytes.
- `OfflineLayerPackageManifest` declares the exact files, sizes, hashes,
  coverage and attribution for only MAP, ROUTING or SEARCH.
- Android and Linux use independent resumable downloads, verify the signed
  catalogue, manifest digest, archive digest, archive length and every extracted
  file hash, then atomically activate the layer.
- Each area/layer has its own active pointer and storage directory. Removing
  routing does not remove map tiles or the search index.
- Installed records expose verified relative file paths plus manifest version,
  coverage, attribution and manifest SHA-256. The transfer API can query the
  installed search layer without network access.
- Combined progress distinguishes downloaded archive bytes from installed
  uncompressed storage bytes. Unselected layers do not inflate the total.

### Publisher workflow

Build one layer archive from its prepared input directory:

```sh
python3 tools/package-custom-area-layer.py \
  --input-dir build/liverpool/search \
  --output-dir dist/layers \
  --area-id liverpool-city --area-name "Liverpool City" \
  --layer SEARCH --version 1.0.0 \
  --west -3.2 --south 53.2 --east -2.6 --north 53.6
```

Repeat for MAP and ROUTING using their respective prepared data directories.
The output metadata file for each layer is then supplied to
`tools/build-signed-layer-catalogue.py` with a public HTTPS asset base URL.
Pass the publisher's Ed25519 PEM private key using `--private-key` to sign
the exact catalogue bytes. Only the emitted public key may be pinned in the
application; the private key must remain outside the repository.

The application layer-catalogue URLs are deliberately blank until the
publisher has actually built and released these layer archives and pinned
the corresponding public key. This prevents the UI from implying that
unpublished artifacts can already be downloaded.

The custom-area screen exposes per-layer download, pause, resume and remove
actions for matching signed catalogue entries, aggregate transfer progress /
storage and offline postcode/place search. The app now adapts verified
independent records into the existing runtime contract: a MAP MBTiles file is
resolved into the MapLibre offline style; a matching ROUTING `tiles.tar` and
its colocated `admins.sqlite`, `timezones.sqlite` and `valhalla.json` are passed
to the existing Android JNI or Linux local Valhalla runtime; SEARCH indexes
are queried locally. Layers are combined only when their verified coverage
matches. A map-only install can render without pretending routing is installed.
The provider-backed MapLibre tile-region store remains a separate option.
These code paths have automated resolver tests, but still require current-head
CI and real Android/Linux airplane-mode validation before being considered
production-ready.
