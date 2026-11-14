# England regional data pipeline

TomOS Maps builds and distributes independent regional packages. The app does
not need a permanently hosted Great Britain master PBF, an AWS S3 bucket or a
Cloudflare R2 bucket.

## User download flow

1. On first setup, the app asks the user to select **England** and one region.
2. The region picker covers North West, North East, Yorkshire and the Humber,
   East Midlands, West Midlands, East of England, London, South East and
   South West.
3. When a matching package appears in the publisher-signed catalogue, the app
   automatically downloads it to private device storage.
4. Downloads resume after interruption and show live progress. The user can
   pause, resume or delete packages.
5. The app verifies the signed catalogue, manifest and archive SHA-256 values
   before atomically installing the map, postcode/place index and Valhalla
   routing data.
6. Once installed, the region's map, postcode search and routing data remain
   on the device for offline use.

A region with no published package remains selectable but cannot download
until the publisher adds it to the signed catalogue. At present the Liverpool
City Region package is the first North West package target; other England
regions need their own built and tested datasets.

## Building regional packages

Build each region from its own licensed Geofabrik regional PBF extract. This
avoids downloading and retaining the full Great Britain master dataset.

The existing Liverpool-first pipeline is:

```bash
sudo apt-get install docker.io osmium-tool
python3 -m pip install pyproj
bash tools/build-liverpool-region.sh
```

The build downloads the Merseyside PBF and its MD5 sidecar, verifies the
checksum, builds OpenMapTiles vector MBTiles with Planetiler, creates a
Valhalla graph from the same PBF, prepares OS Code-Point Open postcode search
data, downloads local Noto Sans glyphs and packages the assets together.

The output is written to `dist/liverpool` and includes a manifest, ZIP archive
and metadata JSON. The builder does not sign or publish the package. Review
coverage, source licences and attribution before release.

For additional regions, add a region-specific build configuration with:
- a Geofabrik regional PBF URL and checksum source;
- an exact region boundary and WGS84 coverage bounds;
- matching Planetiler and Valhalla outputs;
- postcode/place search data clipped to the same coverage;
- local glyph assets and licence/attribution metadata.

Do not label a package offline-ready until its map renderer, postcode search
index and local routing engine all pass tests with networking disabled.

## Public package distribution without object storage

The package ZIP and manifest can be attached to public GitHub Releases. The
signed catalogue and its detached signature can be served from the repository
or a GitHub Release. The app downloads only the selected region's package;
users do not download the entire England dataset.

The publisher must:
- create and keep an Ed25519 private signing key outside Git;
- configure it as the GitHub Actions secret
  `TOMOS_PUBLISHER_ED25519_PRIVATE_KEY`;
- run the `Publish England regional map packages` workflow;
- copy the public key printed in the workflow summary into
  `RegionalPackageBuildConfig.kt` and rebuild the app;
- include only packages whose manifest/archive checksums match the release
  assets.

The workflow currently builds and publishes Liverpool City Region as the
first North West package. Other England regions require region-specific build
configurations and datasets before they can be selected for download.

No S3/R2 secrets or persistent master-data bucket are needed. GitHub Releases
are a distribution location for finished packages, not a substitute for
building and validating the source datasets.

## Package contents

A regional package contains:
- MapLibre-compatible MBTiles and local glyph resources;
- Valhalla routing graph, admin/timezone databases and runtime configuration;
- UK postcode/place search index;
- manifest with exact file sizes, SHA-256 hashes, coverage and attribution.

Android stores packages in private app storage. Linux stores them under
`~/.local/share/tomos-maps/regional-packages`.

## Licensing

OpenStreetMap data is © OpenStreetMap contributors and licensed under the
Open Database License (ODbL). Preserve attribution and comply with ODbL
share-alike obligations. Geofabrik is the extract provider, not the owner of
OSM data. Check the separate licence terms for OS postcode data, fonts, styles
and any other package inputs.

Do not bulk-download offline map tiles from the public OpenStreetMap standard
tile server. Generate tiles from a suitable regional extract or use a provider
that explicitly permits offline use and redistribution.


## Source-provider failover and independent UK data

The Liverpool builder now tries sources in this order:

1. Geofabrik's current Merseyside extract.
2. Geofabrik's dated Merseyside snapshot.
3. OpenStreetMap France's North West England PBF, clipped to the committed Merseyside polygon.
4. An optional BBBike custom Merseyside PBF, when configured with a temporary PBF URL and its provider-published MD5 URL.
5. The last-known-good cached Merseyside PBF, but only when its saved MD5 and Osmium parser checks pass.

BBBike custom extracts are generated on demand; their download links are temporary
and are sent after the user submits an area request. The build deliberately does
not invent a static BBBike URL. To use one, request a PBF for the same Merseyside
boundary from [BBBike Extracts](https://extract.bbbike.org/), then run:

```bash
BBBIKE_PBF_URL='https://...the-temporary-PBF-link...' \\
BBBIKE_MD5_URL='https://...the-provider-checksum-link...' \\
bash tools/build-liverpool-region.sh
```

The builder requires both URLs, checks the provider MD5, validates the PBF with
Osmium and rejects bounds that do not match the Merseyside build envelope. The
URL values are intentionally supplied at run time rather than committed because
BBBike links expire. BBBike and OpenStreetMap France are separate extract
infrastructures, but both distribute OpenStreetMap data: they are not independent
underlying map databases.

### Ordnance Survey as a non-OSM fallback

The pipeline already obtains UK postcode points from OS Code-Point Open. OS
OpenData also offers OS Open Zoomstack, a Great Britain vector basemap suitable
for offline map display, under the Open Government Licence. It can provide an
independent cartographic fallback if OSM extract services are unavailable.
However, it is not a replacement for the current OSM-derived Valhalla graph or
full feature/search coverage. OS Open Roads is a generalised road dataset and
must not be treated as a turn-by-turn in-vehicle routing graph. Do not silently
switch the routing layer to OS data or publish an OSM-derived route graph from
an OS-only package.

A future OS-only package should be a separately identified MAP/SEARCH data
source, with its own conversion pipeline, dataset version, coverage metadata,
licence and attribution. Required OS attribution is:

`Contains OS data © Crown copyright and database right [year]`

If Code-Point Open is included, its additional Royal Mail and National Statistics
acknowledgements must also be preserved. See [OS OpenData](https://www.ordnancesurvey.co.uk/products/open-data),
[OS Open Zoomstack](https://www.ordnancesurvey.co.uk/products/os-open-zoomstack)
and [OS attribution guidance](https://www.ordnancesurvey.co.uk/products/product-support).
