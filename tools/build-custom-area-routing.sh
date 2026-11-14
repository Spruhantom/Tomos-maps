#!/usr/bin/env bash
set -Eeuo pipefail

# Build an independent Valhalla routing layer for a selected WGS84 rectangle.
# A buffer is added around the requested area so routes can reach roads just
# outside the visible boundary without clipping graph connectivity.
usage() {
  echo "Usage: $0 <source.osm.pbf> <output-directory> <west> <south> <east> <north> [buffer-degrees]" >&2
}
if [[ $# -lt 6 || $# -gt 7 ]]; then usage; exit 2; fi
SOURCE="$(realpath "$1")"
OUTPUT="$(mkdir -p "$2" && realpath "$2")"
WEST="$3"; SOUTH="$4"; EAST="$5"; NORTH="$6"; BUFFER="${7:-0.02}"
python3 - "$WEST" "$SOUTH" "$EAST" "$NORTH" "$BUFFER" <<'PY'
import math, sys
west, south, east, north, buffer = map(float, sys.argv[1:])
if not all(map(math.isfinite, (west, south, east, north, buffer))):
    raise SystemExit("Bounds and buffer must be finite numbers.")
if not (-180 <= west < east <= 180 and -85 <= south < north <= 85):
    raise SystemExit("Bounds must be a valid non-wrapping WGS84 rectangle.")
if not (0 <= buffer <= 1):
    raise SystemExit("Buffer must be between 0 and 1 degree.")
PY
[[ -s "$SOURCE" ]] || { echo "Source PBF is missing or empty: $SOURCE" >&2; exit 2; }
for command in osmium docker; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done
read -r CLIP_WEST CLIP_SOUTH CLIP_EAST CLIP_NORTH < <(python3 - "$WEST" "$SOUTH" "$EAST" "$NORTH" "$BUFFER" <<'PY'
import sys
w,s,e,n,b=map(float,sys.argv[1:])
print(max(-180,w-b), max(-85,s-b), min(180,e+b), min(85,n+b))
PY
)
mkdir -p "$OUTPUT/source" "$OUTPUT/valhalla"
CLIPPED="$OUTPUT/source/custom-area.osm.pbf"
echo "[1/3] Extracting selected area plus routing buffer"
osmium extract --bbox "$CLIP_WEST,$CLIP_SOUTH,$CLIP_EAST,$CLIP_NORTH" \
  --strategy=complete_ways --overwrite -o "$CLIPPED" "$SOURCE"
osmium fileinfo -e "$CLIPPED" >/dev/null
echo "[2/3] Building independent Valhalla graph and support databases"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${VALHALLA_IN_DOCKER:-1}" == "1" ]]; then
  IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"
  docker run --rm --entrypoint /bin/bash -v "$SCRIPT_DIR/..:/workspace" \
    -v "$OUTPUT:/output" -w /workspace "$IMAGE" \
    -lc "bash tools/build-valhalla-region.sh '/output/source/custom-area.osm.pbf' '/output/valhalla'"
else
  bash "$SCRIPT_DIR/build-valhalla-region.sh" "$CLIPPED" "$OUTPUT/valhalla"
fi
for file in tiles.tar admins.sqlite timezones.sqlite valhalla.json; do
  [[ -s "$OUTPUT/valhalla/$file" ]] || { echo "Missing routing output: $file" >&2; exit 1; }
done
echo "[3/3] Routing layer is ready"
du -h "$OUTPUT/valhalla/"{tiles.tar,admins.sqlite,timezones.sqlite,valhalla.json}
echo "Coverage bounds: $WEST,$SOUTH,$EAST,$NORTH (buffer $BUFFER degrees)"
echo "This is an unsigned build artifact; package and sign it through the trusted publisher pipeline."
