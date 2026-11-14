# Automated UK map-data generation

## Scope and safety boundary

The data pipeline is separate from the TomOS Maps application. It creates and validates release assets; it does not add download controls, enable offline map rendering, enable offline routing or change the app's online-only behaviour.

The first automated layer is the UK-wide vector map. Routing and postcode/search are separate build stages so that each dataset can be validated, versioned and licensed independently.

## First stage: UK vector map

Run **Actions → Build UK map data layer → Run workflow** on main.

- Source: Geofabrik's current United Kingdom OSM PBF extract, covering Great Britain and Northern Ireland (not the Isle of Man or Channel Islands).
- Source integrity: the builder downloads Geofabrik's published MD5 sidecar and rejects a mismatch or malformed checksum.
- Renderer data: Planetiler OpenMapTiles profile creates MapLibre-compatible MBTiles, converted to a PMTiles archive.
- Default zoom range: 0–12. The workflow permits a deliberate increase to zoom 14.
- Output validation: PMTiles verification, non-empty file checks, metadata validation, SHA-256 and byte size.
- Distribution: a 30-day Actions artifact by default. Set publish_release only after reviewing the output; the workflow then creates a versioned prerelease and registers the immutable asset URL, checksum and size in data/catalogue/uk.json.

The UK PBF is approximately 2.1 GB and Planetiler needs substantial temporary disk space. The workflow checks for at least 24 GiB free after cleanup and stops rather than publishing a partial archive. If the hosted runner does not have enough space, use a larger runner or split the build into Geofabrik sub-regions; do not remove the guard.

The PMTiles archive is a distribution artifact. PMTiles is not enabled as an app runtime format by this pipeline. App downloads remain disabled.

## Second stage: routing data

Routing will be generated separately from the same dated Geofabrik PBF snapshot used for the map release, using the project's Valhalla builder. The routing release must include graph tiles, admin/timezone databases and configuration, plus a route smoke test. UK-wide graph generation has higher memory and disk requirements than a map build; it must run on a suitably sized runner or as independently versioned regional graphs. Do not advertise routing as available until route tests pass.

## Third stage: postcode and address search

The existing OS Code-Point Open downloader provides Great Britain postcode points. It does not by itself establish complete UK coverage.

The ONS Postcode Directory covers the UK, including Northern Ireland, but its published metadata identifies Royal Mail, Gridlink, LPS, Ordnance Survey and ONS intellectual property. Northern Ireland data also has additional LPS end-user licence requirements. The pipeline must not redistribute a UK-wide derived index until those terms and attribution have been confirmed for the intended distribution. Until then, publish no UK-wide postcode package and do not label GB-only data as UK-wide.

A future search build must produce the app's documented bounded JSON index format, validate postcode syntax and coordinates, retain source attribution, and publish only under confirmed redistribution terms.

## Sources and attribution

- Geofabrik United Kingdom extracts: https://download.geofabrik.de/europe/united-kingdom.html
- OpenStreetMap copyright and ODbL: https://www.openstreetmap.org/copyright
- Planetiler: https://github.com/onthegomap/planetiler
- PMTiles: https://github.com/protomaps/go-pmtiles
- ONS Postcode Directory: https://www.data.gov.uk/dataset/70592de5-8004-434c-a122-5b2b080708c4/ons-postcode-directory-august-2026-for-the-united-kingdom-hosted-table

Every OSM-derived map release must retain OpenStreetMap attribution and comply with ODbL. A source download being public does not remove its redistribution obligations.
