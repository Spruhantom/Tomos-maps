#!/usr/bin/env bash
set -Eeuo pipefail

# Build the Liverpool offline package using the persistent Great Britain
# master as the only OSM PBF source. The small official .poly boundary is
# downloaded separately; no regional PBF is fetched.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MASTER="${OSM_MASTER_PBF:-$ROOT/.osm-master/great-britain.osm.pbf}"
DATA_DIR="${DATA_DIR:-$ROOT/.regional-build/liverpool-from-gb}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT/dist/liverpool}"
POLYGON_URL="${MERSEYSIDE_POLYGON_URL:-https://download.geofabrik.de/europe/united-kingdom/england/merseyside.poly}"
POLYGON="$DATA_DIR/merseyside.poly"
REGION_PBF="$DATA_DIR/merseyside-from-gb.osm.pbf"

command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 2; }
[[ -s "$MASTER" ]] || {
  echo "Great Britain master not found: $MASTER" >&2
  echo "Run tools/sync-great-britain-master.sh first." >&2
  exit 1
}
mkdir -p "$DATA_DIR"
if [[ ! -s "$POLYGON" ]]; then
  curl --fail --location --show-error --retry 5 --retry-all-errors \
    --connect-timeout 20 --max-time 180 "$POLYGON_URL" -o "$POLYGON"
fi
grep -q '^END$' "$POLYGON" || {
  echo "Downloaded Merseyside polygon is invalid: $POLYGON" >&2
  exit 1
}

OSM_MASTER_PBF="$MASTER" bash "$ROOT/tools/extract-region-from-master.sh" \
  "$POLYGON" "$REGION_PBF"

SOURCE_PBF="$REGION_PBF" \
DATA_DIR="$DATA_DIR/package-build" \
OUTPUT_DIR="$OUTPUT_DIR" \
bash "$ROOT/tools/build-liverpool-region.sh"

echo "Liverpool package generated from the Great Britain master: $OUTPUT_DIR"
