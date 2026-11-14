# Automated UK map-data generation

## Scope and safety boundary

The data pipeline is separate from the TomOS Maps application. It creates and validates release assets; it does not add download controls, enable offline map rendering, enable offline routing or change the app's online-only behaviour.

The map, routing and postcode/search datasets are independently versioned. Do not mark a layer available until its own validation and licence checks pass.

## First stage: UK vector map

The UK map builder is tools/build-uk-map-layer.sh. It can be run on a suitably provisioned Linux host with Docker, PMTiles CLI, curl and checksum utilities. The app remains online-only.

- Source: Geofabrik's current United Kingdom OSM PBF extract, covering Great Britain and Northern Ireland (not the Isle of Man or Channel Islands).
- Source integrity: downloads Geofabrik's published MD5 sidecar and rejects a mismatch.
- Renderer data: Planetiler OpenMapTiles profile creates MapLibre-compatible MBTiles, converted to PMTiles.
- Default zoom range: 0–12; the workflow permits a deliberate increase to zoom 14.
- Output validation: PMTiles verification, non-empty file checks, metadata, SHA-256 and byte size.
- Distribution: a 30-day Actions artifact by default. Publishing a versioned prerelease and registering its map asset in the Pages catalogue is a separate explicit option.

The UK PBF is approximately 2.1 GB and Planetiler needs substantial temporary disk space. The builder checks at least 24 GiB free after the source download and stops rather than publishing partial output. The PMTiles file is a data distribution artifact only; it is not enabled in the app runtime.

## Second stage: England Valhalla routing pilot

Run Actions → Build England Valhalla routing test → Run workflow on main.

This is deliberately an England-only pilot, not a UK-wide graph build. It uses Geofabrik's England extract and tests a Liverpool-to-Manchester driving route. Northern Ireland, Scotland and Wales are not included in this routing graph.

- Downloads the current Geofabrik England PBF and verifies its provider MD5 sidecar.
- Builds Valhalla graph tiles, administrative-boundary data and timezone data.
- Requires at least 20 GiB free disk after downloading the England PBF. If the hosted runner cannot meet this, move to a larger runner rather than weakening the guard.
- Starts a temporary Valhalla service and requests a Liverpool-to-Manchester driving route. The job fails unless Valhalla returns a successful trip with a non-zero route length.
- Writes source provenance and SHA-256/size metadata for every output.
- Splits the graph archive into 1.8 GB parts so each file remains below the configured GitHub Release per-asset ceiling. Reassemble parts in lexical order before extracting: cat tiles.tar.part-* > tiles.tar.
- Uploads a 30-day review artifact by default. Publishing to a prerelease is optional and does not register the graph as an app-download package.

A successful England pilot will validate the build pipeline and a route within England. It will not establish that a complete UK graph has been generated. Scotland, Wales and Northern Ireland would need to be added in a later full-coverage build. Offline app routing remains disabled.

## Third stage: UK postcode search

The tools/build-uk-postcode-index.py builder accepts an ONS Postcode Directory CSV or ZIP supplied by the operator. It streams the source, keeps current postcode rows, normalises postcode formatting, validates coordinates, rejects duplicates, and writes the app's formatVersion: 1 search index.

The builder has mandatory safety gates:
- --redistribution-authorized must be supplied after legal review.
- --licence-evidence must point to a non-empty local record of the applicable permission/terms.
- Both Great Britain and Northern Ireland records must be present. A GB-only index is rejected as not UK-wide.
- The generated index must remain within the app's two-million-record and 256 MiB limits.
- A metadata sidecar records source/evidence hashes, coverage counts and output SHA-256.

Example, only after permission to redistribute derived postcode coordinates has been confirmed:

    python3 tools/build-uk-postcode-index.py --source /path/to/authorised-onspd.zip --output dist/uk-search/search-index.json --licence-evidence /path/to/redistribution-permission.txt --licence 'confirmed ONSPD terms and written permission' --attribution 'ONS, Ordnance Survey, Royal Mail, Gridlink and LPS as required by the supplied terms' --redistribution-authorized

No ONSPD data is automatically downloaded or published by CI. The ONSPD dataset metadata identifies Royal Mail, Gridlink, LPS (Northern Ireland), Ordnance Survey and ONS intellectual property. Its public availability does not itself establish permission to redistribute a derived offline index. Keep licence evidence private; do not commit agreements or source data to this repository.

## Data sources and attribution

- Geofabrik UK extracts: https://download.geofabrik.de/europe/united-kingdom.html
- OpenStreetMap copyright and ODbL: https://www.openstreetmap.org/copyright
- Valhalla Mjölnir guide: https://valhalla.github.io/valhalla/contributing/architecture/mjolnir/
- Valhalla getting started: https://valhalla.github.io/valhalla/mjolnir/getting_started_guide/
- ONS Postcode Directory (August 2026): https://www.data.gov.uk/dataset/70592de5-8004-434c-a122-5b2b080708c4/ons-postcode-directory-august-2026-for-the-united-kingdom-hosted-table

Every OSM-derived release must retain OpenStreetMap attribution and comply with ODbL. No map, routing or postcode package is enabled in the app by this pipeline.