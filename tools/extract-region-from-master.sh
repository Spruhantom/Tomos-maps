#!/usr/bin/env bash
set -Eeuo pipefail

# Extract a region from the verified Great Britain master without another
# Geofabrik download. Use a .poly boundary so adjacent regional packages can
# share clean, documented boundaries.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MASTER="${OSM_MASTER_PBF:-$ROOT/.osm-master/great-britain.osm.pbf}"
POLYGON="${1:?Usage: extract-region-from-master.sh <region.poly> <output.osm.pbf>}"
OUTPUT="${2:?Usage: extract-region-from-master.sh <region.poly> <output.osm.pbf>}"

command -v osmium >/dev/null 2>&1 || { echo "osmium-tool is required" >&2; exit 2; }
[[ -s "$MASTER" ]] || { echo "Great Britain master not found: $MASTER" >&2; exit 1; }
[[ -s "$POLYGON" ]] || { echo "Region polygon not found: $POLYGON" >&2; exit 1; }
mkdir -p "$(dirname "$OUTPUT")"
TEMP="$OUTPUT.tmp.osm.pbf"
trap 'rm -f "$TEMP"' EXIT

osmium extract --polygon "$POLYGON" --strategy=complete_ways \
  --output "$TEMP" --overwrite "$MASTER"
osmium fileinfo -e "$TEMP" >/dev/null
mv -f "$TEMP" "$OUTPUT"
trap - EXIT
echo "Created regional extract: $OUTPUT"
