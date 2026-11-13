#!/usr/bin/env bash
set -Eeuo pipefail

# Reproducible Liverpool-first package build using licensed open regional data.
# Requires Docker, curl, md5sum, Python 3, and pyproj.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="${DATA_DIR:-$ROOT/.regional-build/liverpool}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT/dist/liverpool}"
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:latest}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"

# Merseyside is the available Geofabrik extract covering Liverpool, Sefton,
# Knowsley, St Helens and Wirral. All three assets are filtered to this area.
WEST="${WEST:--3.32}"
SOUTH="${SOUTH:-53.25}"
EAST="${EAST:--2.43}"
NORTH="${NORTH:-53.67}"
PBF_NAME="merseyside-latest.osm.pbf"
PBF_URL="https://download.geofabrik.de/europe/united-kingdom/england/$PBF_NAME"

for command in docker curl md5sum python3; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Missing required command: $command" >&2
    exit 2
  }
done
python3 -c 'import pyproj' >/dev/null 2>&1 || {
  echo "Install pyproj first: python3 -m pip install pyproj" >&2
  exit 2
}

mkdir -p "$DATA_DIR" "$OUTPUT_DIR"
cd "$DATA_DIR"

echo "[1/5] Downloading Geofabrik Merseyside OpenStreetMap extract"
if [[ ! -s "$PBF_NAME" ]]; then
  curl --fail --location --retry 4 --retry-delay 3 "$PBF_URL" -o "$PBF_NAME"
fi
curl --fail --location --retry 4 "$PBF_URL.md5" -o "$PBF_NAME.md5"
md5sum --check "$PBF_NAME.md5"

echo "[2/5] Building MapLibre/OpenMapTiles vector MBTiles with Planetiler"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx4g}" \
  -v "$DATA_DIR:/data" \
  "$PLANETILER_IMAGE" \
  "--osm_path=/data/$PBF_NAME" \
  --area=merseyside \
  --minzoom=0 \
  --maxzoom=14 \
  --output=/data/region.mbtiles
test -s "$DATA_DIR/region.mbtiles"

echo "[3/5] Building the local Valhalla graph from the same OSM extract"
docker run --rm \
  --entrypoint /bin/bash \
  -v "$ROOT:/workspace" \
  -w /workspace \
  "$VALHALLA_IMAGE" \
  -lc "tools/build-valhalla-region.sh '/data/$PBF_NAME' '/data/valhalla' && tools/test-local-valhalla-route.sh '/data/valhalla'"

test -s "$DATA_DIR/valhalla/tiles.tar"

echo "[4/5] Downloading OS Code-Point Open and preparing Liverpool postcode search"
python3 "$ROOT/tools/download-codepoint-open.py" \
  --output "$DATA_DIR/code-point-open-gb.zip"
python3 "$ROOT/tools/prepare-codepoint-open.py" \
  --source-zip "$DATA_DIR/code-point-open-gb.zip" \
  --output "$DATA_DIR/liverpool-postcodes.csv" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH"

echo "[5/5] Creating the installable signed-catalogue-ready regional package"
VERSION="${PACKAGE_VERSION:-$(date -u +%Y.%m.%d)}"
python3 "$ROOT/tools/package-regional-data.py" \
  --id liverpool-city-region \
  --name "Liverpool City Region" \
  --version "$VERSION" \
  --region-code UK-LIV \
  --map-data "$DATA_DIR/region.mbtiles" \
  --routing-data "$DATA_DIR/valhalla/tiles.tar" \
  --postcode-csv "$DATA_DIR/liverpool-postcodes.csv" \
  --output "$OUTPUT_DIR" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH" \
  --source-attribution "© OpenMapTiles; © OpenStreetMap contributors; Contains OS data © Crown copyright and database right"

echo
echo "Liverpool package build completed:"
find "$OUTPUT_DIR" -maxdepth 1 -type f -printf '%f (%s bytes)\n' | sort
echo "The output is unsigned. Publish only after reviewing licences, bounds and attribution."
