# Great Britain master OSM dataset

## Architecture

TomOS Maps keeps MapLibre as its renderer and Valhalla as its routing engine.
The Great Britain PBF is the canonical source dataset from which regional
packages are produced. It is not installed on end-user devices.

- Source: Geofabrik's Great Britain .osm.pbf extract (England, Scotland and
  Wales; excludes Ireland, Isle of Man and Channel Islands).
- Persistent storage: an S3-compatible object bucket owned/configured by the
  project operator. GitHub Actions cache is not used for the multi-gigabyte
  master dataset.
- Regional extraction: osmium extract clips the master to a checked-in or
  operator-supplied .poly boundary.
- Rendering: existing Planetiler/OpenMapTiles output remains MBTiles.
- Routing: existing Valhalla graph is generated from the same regional PBF.
- Experimental distribution: optional PMTiles archives can be generated from
  MBTiles. MBTiles remains the only supported regional runtime format until
  Android and Linux offline tests both pass.

## One-time storage setup

Create a private S3 bucket with encryption enabled and versioning enabled.
Allow the workflow identity to s3:GetObject, s3:PutObject and s3:ListBucket
only for the tomos-maps/osm/great-britain/* prefix. Add these GitHub Actions
repository secrets:

- OSM_MASTER_S3_BUCKET — bucket name, not an s3:// URL.
- AWS_REGION — bucket region.
- AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY — restricted credentials.
- AWS_SESSION_TOKEN — optional, for temporary credentials.

Prefer an OIDC-assumed role over long-lived access keys when the repository's
AWS trust policy is ready. Never put credentials in the repository or workflow
source. The workflow uses an ephemeral runner, but the PBF is restored from
and written back to persistent object storage on every run.

The first workflow run downloads and verifies the Great Britain PBF using
Geofabrik's MD5 sidecar, validates it with osmium fileinfo, computes SHA-256,
and uploads an immutable timestamped snapshot. It then publishes a small
current/master-metadata.json pointer only after the snapshot upload succeeds.
Later runs restore that immutable snapshot, verify SHA-256, and update a
candidate copy using the replication URL and sequence stored in the PBF
header. Only after the candidate is fully updated and validated is a new
immutable snapshot uploaded and the current metadata pointer promoted.

Retain old snapshots for rollback. Configure a bucket lifecycle policy that
does not expire snapshots still needed for release reproducibility. The
metadata pointer identifies the exact snapshot and checksum.

## Incremental updates

The scheduled workflow runs daily. pyosmium-up-to-date reads the replication
server URL and sequence ID embedded in the Geofabrik PBF and applies only
missing daily changes. Exit status 1 means the tool applied one bounded batch
and more diffs remain; the script loops until exit status 0. Any other exit
status aborts without replacing the previous current snapshot.

The updater uses a 1 GB default diff batch to keep memory use reasonable on a
standard runner. If a master has fallen too far behind, use a larger-memory
runner or set OSM_DIFF_MAX_MB deliberately. Geofabrik retains regional diffs
for a limited period; if the stored sequence has fallen outside the available
window, bootstrap from a new full PBF rather than attempting to apply an
incomplete chain.

A failed download, checksum, update, validation or object upload must never
promote a partial file. The previous snapshot remains available for rollback.

## Extract a region locally

After restoring the master into .osm-master/great-britain.osm.pbf, supply a
polygon boundary:

    tools/extract-region-from-master.sh regions/liverpool.poly .regional-build/liverpool/liverpool.osm.pbf

The extraction script uses Osmium's complete-ways strategy and validates the
result. The same regional PBF should feed both Planetiler and Valhalla so map
rendering and routing use consistent source coverage. A region polygon must
be reviewed for coverage and licence/source provenance; do not guess bounds.

The existing Liverpool build script remains available for its current
Merseyside-source workflow. The new extractor establishes the master-data
path; switch a regional build to it only after its boundary and package output
have been validated.

## PMTiles investigation

Create an additional archive without deleting or replacing MBTiles:

    tools/convert-mbtiles-to-pmtiles.sh .regional-build/liverpool/region.mbtiles .regional-build/liverpool/region.pmtiles

The converter runs the PMTiles CLI conversion and archive verification. Keep
both files in test builds. PMTiles is a promising single-file distribution
format, but the application currently targets MapLibre Native/Compose and
both Android and Linux must demonstrate local-file rendering with networking
disabled before the app is allowed to prefer PMTiles.

MapLibre Native Android documentation describes PMTiles support from version
11.7.0 through a pmtiles://file:// source, but explicitly notes that PMTiles
sources do not support offline pack downloads or caching. The TomOS app's
actual MapLibre Native version and the Linux renderer path must be checked
before enabling it. Until then, MBTiles remains the supported offline source.

## Licensing and attribution

The Great Britain PBF is derived from OpenStreetMap data, © OpenStreetMap
contributors, under the Open Database License (ODbL). Preserve attribution
and comply with ODbL share-alike requirements for adapted databases. Geofabrik
is the extract provider, not the owner of OSM data. Tile generation and
distribution must not use the public OSM standard tile server as a bulk tile
source. Keep separate licence records for fonts, OS postcode data, styles and
other inputs. See OpenStreetMap copyright and licence:
https://www.openstreetmap.org/copyright
Geofabrik Great Britain downloads:
https://download.geofabrik.de/europe/great-britain.html
Geofabrik extract update technical details:
https://download.geofabrik.de/technical.html

## Current implementation boundaries

Implemented in this branch:
- S3-backed persistent master snapshot restore and publish.
- Geofabrik checksum verification on initial download.
- PBF header-based incremental updates with bounded batches.
- Immutable timestamped snapshots and a single metadata pointer.
- Local polygon extraction utility.
- Optional MBTiles-to-PMTiles conversion and verification utility.
- Daily GitHub Actions workflow, pending owner storage credentials.

Not yet proven:
- The first real multi-gigabyte S3 bootstrap and subsequent scheduled update.
- A production Liverpool polygon extracted from the Great Britain master.
- Rebuilding the existing regional package directly from that extracted PBF.
- PMTiles rendering on Android and Linux with networking disabled.
- Production storage credentials, retention and recovery configuration.

Do not describe the Great Britain pipeline as operational until the workflow
has completed successfully with the project's own persistent bucket.
