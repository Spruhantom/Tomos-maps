#!/usr/bin/env bash
set -Eeuo pipefail

# Reproducible Liverpool-first package build using licensed open regional data.
# Requires Docker, curl, md5sum, osmium-tool, Python 3, and pyproj.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="${DATA_DIR:-$ROOT/.regional-build/liverpool}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT/dist/liverpool}"
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:latest}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"
PBF_NAME="merseyside-latest.osm.pbf"
PRIMARY_URL="https://download.geofabrik.de/europe/united-kingdom/england/merseyside-latest.osm.pbf"
MIRROR_URL="https://download-ext2.geofabrik.de/europe/united-kingdom/england/merseyside-260801.osm.pbf"

for command in docker curl md5sum python3 osmium; do
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

verify_md5() {
  local file="$1" checksum_file="$2"
  local expected
  expected="$(awk 'NF { print $1; exit }' "$checksum_file")"
  [[ "$expected" =~ ^[[:xdigit:]]{32}$ ]] || return 1
  local actual
  actual="$(md5sum "$file" | awk '{print $1}')"
  [[ "$actual" == "$expected" ]]
}

echo "[1/6] Downloading and verifying Geofabrik Merseyside OpenStreetMap extract"
if [[ ! -s "$PBF_NAME" ]]; then
  downloaded=false
  for source_url in "$PRIMARY_URL" "$MIRROR_URL"; do
    temporary="$PBF_NAME.download"
    checksum_temporary="$PBF_NAME.md5.download"
    rm -f "$temporary" "$checksum_temporary"
    echo "Trying source: $source_url"
    if ! curl --fail --location --retry 2 --retry-delay 2 "$source_url" -o "$temporary"; then
      continue
    fi
    if ! curl --fail --location --retry 2 "${source_url}.md5" -o "$checksum_temporary"; then
      rm -f "$temporary"
      continue
    fi
    if verify_md5 "$temporary" "$checksum_temporary"; then
      mv "$temporary" "$PBF_NAME"
      mv "$checksum_temporary" "$PBF_NAME.md5"
      downloaded=true
      break
    fi
    echo "Checksum mismatch for $source_url; trying the next source." >&2
    rm -f "$temporary" "$checksum_temporary"
  done
  if [[ "$downloaded" != true ]]; then
    echo "Unable to download a checksum-verified Merseyside PBF from either Geofabrik endpoint." >&2
    exit 1
  fi
else
  echo "Using cached PBF: $DATA_DIR/$PBF_NAME"
fi

echo "[2/6] Reading exact regional coverage from the PBF header"
BOUNDS="$(osmium fileinfo -e -g header.boxes "$PBF_NAME" | tr -d '()')"
IFS=',' read -r DEFAULT_WEST DEFAULT_SOUTH DEFAULT_EAST DEFAULT_NORTH <<< "$BOUNDS"
[[ -n "$DEFAULT_WEST" && -n "$DEFAULT_SOUTH" && -n "$DEFAULT_EAST" && -n "$DEFAULT_NORTH" ]] || {
  echo "Unable to read WGS84 bounds from the Merseyside PBF header: $BOUNDS" >&2
  exit 1
}
WEST="${WEST:-$DEFAULT_WEST}"
SOUTH="${SOUTH:-$DEFAULT_SOUTH}"
EAST="${EAST:-$DEFAULT_EAST}"
NORTH="${NORTH:-$DEFAULT_NORTH}"
echo "Coverage: west=$WEST south=$SOUTH east=$EAST north=$NORTH"

echo "[3/6] Building MapLibre/OpenMapTiles vector MBTiles with Planetiler"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx4g}" \
  -v "$DATA_DIR:/data" \
  "$PLANETILER_IMAGE" \
  --download=cached \
  "--osm_path=/data/$PBF_NAME" \
  --area=merseyside \
  --minzoom=0 \
  --maxzoom=14 \
  --output=/data/region.mbtiles
test -s "$DATA_DIR/region.mbtiles"

echo "[4/6] Building and testing the local Valhalla graph from the same OSM extract"
docker run --rm \
  --entrypoint /bin/bash \
  -v "$ROOT:/workspace" \
  -v "$DATA_DIR:/data" \
  -w /workspace \
  "$VALHALLA_IMAGE" \
  -lc "bash tools/build-valhalla-region.sh '/data/$PBF_NAME' '/data/valhalla' && bash tools/test-local-valhalla-route.sh '/data/valhalla'"
test -s "$DATA_DIR/valhalla/tiles.tar"

echo "[5/6] Downloading OS Code-Point Open and preparing Liverpool postcode search"
python3 "$ROOT/tools/download-codepoint-open.py" \
  --output "$DATA_DIR/code-point-open-gb.zip"
python3 "$ROOT/tools/prepare-codepoint-open.py" \
  --source-zip "$DATA_DIR/code-point-open-gb.zip" \
  --output "$DATA_DIR/liverpool-postcodes.csv" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH"

echo "[6/6] Downloading local glyphs and creating the installable regional package"
bash "$ROOT/tools/download-offline-map-glyphs.sh" "$DATA_DIR/glyphs"
VERSION="${PACKAGE_VERSION:-$(date -u +%Y.%m.%d)}"
python3 "$ROOT/tools/package-regional-data.py" \
  --id liverpool-city-region \
  --name "Liverpool City Region" \
  --version "$VERSION" \
  --region-code UK-LIV \
  --map-data "$DATA_DIR/region.mbtiles" \
  --routing-data "$DATA_DIR/valhalla/tiles.tar" \
  --routing-admin-data "$DATA_DIR/valhalla/admins.sqlite" \
  --routing-timezone-data "$DATA_DIR/valhalla/timezones.sqlite" \
  --routing-config "$DATA_DIR/valhalla/valhalla.json" \
  --glyphs-dir "$DATA_DIR/glyphs" \
  --postcode-csv "$DATA_DIR/liverpool-postcodes.csv" \
  --output "$OUTPUT_DIR" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH" \
  --source-attribution "© OpenMapTiles; © OpenStreetMap contributors; Contains OS data © Crown copyright and database right; Noto Sans glyphs under the SIL Open Font License"

echo
echo "Liverpool package build completed:"
find "$OUTPUT_DIR" -maxdepth 1 -type f -printf '%f (%s bytes)\n' | sort
echo "The output is unsigned. Publish only after reviewing licences, bounds and attribution."
