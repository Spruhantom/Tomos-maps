#!/usr/bin/env bash
set -Eeuo pipefail

# Reproducible Liverpool-first package build using licensed open regional data.
# Requires Docker, curl, md5sum, osmium-tool, Python 3, and pyproj.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="${DATA_DIR:-$ROOT/.regional-build/liverpool}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT/dist/liverpool}"
DATA_DIR="$(realpath -m "$DATA_DIR")"
OUTPUT_DIR="$(realpath -m "$OUTPUT_DIR")"
if [[ -n "${SOURCE_PBF:-}" ]]; then SOURCE_PBF="$(realpath "$SOURCE_PBF")"; fi
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:latest}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"
PBF_NAME="merseyside-latest.osm.pbf"
PRIMARY_URL="${GEOFABRIK_PRIMARY_URL:-https://download.geofabrik.de/europe/united-kingdom/england/merseyside-latest.osm.pbf}"
SNAPSHOT_URL="${GEOFABRIK_SNAPSHOT_URL:-https://download.geofabrik.de/europe/united-kingdom/england/merseyside-261031.osm.pbf}"

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

echo "[1/6] Preparing the regional OpenStreetMap extract"
EXTRACT_PROVIDER="Geofabrik"
POLYGON_FILE="$ROOT/data/boundaries/merseyside.poly"
NORTH_WEST_PBF="north_west-latest.osm.pbf"
NORTH_WEST_URL="${OSM_FR_NORTH_WEST_URL:-https://download.openstreetmap.fr/extracts/europe/united_kingdom/england/north_west-latest.osm.pbf}"
NORTH_WEST_MD5_URL="${OSM_FR_NORTH_WEST_MD5_URL:-https://download.openstreetmap.fr/extracts/europe/united_kingdom/england/north_west.osm.pbf.md5}"

download_with_retry() {
  local url="$1" destination="$2"
  curl --fail --location --silent --show-error \
    --connect-timeout 20 --max-time 1800 \
    --retry 6 --retry-all-errors --retry-delay 5 --retry-max-time 300 \
    "$url" -o "$destination"
}

verify_merseyside_bounds() {
  local file="$1" bounds
  bounds="$(osmium fileinfo -e -g header.boxes "$file" | tr -d '()')"
  python3 - "$bounds" <<'PY'
import sys
values = [float(value) for value in sys.argv[1].split(",") if value.strip()]
if len(values) != 4:
    raise SystemExit(f"Expected four PBF bounds, got: {sys.argv[1]!r}")
west, south, east, north = values
# The committed official Merseyside polygon envelope is approximately:
# west=-3.342781, south=53.285397, east=-2.575686, north=53.705277.
expected = (-3.342781, 53.285397, -2.575686, 53.705277)
if not (abs(west-expected[0]) < 0.01 and abs(south-expected[1]) < 0.01
        and abs(east-expected[2]) < 0.01 and abs(north-expected[3]) < 0.01):
    raise SystemExit(f"Extract bounds do not match the Merseyside polygon: {values}")
PY
}

if [[ -n "${SOURCE_PBF:-}" ]]; then
  [[ -s "$SOURCE_PBF" ]] || { echo "SOURCE_PBF does not exist: $SOURCE_PBF" >&2; exit 1; }
  cp --reflink=auto "$SOURCE_PBF" "$PBF_NAME"
  osmium fileinfo -e "$PBF_NAME" >/dev/null
  EXTRACT_PROVIDER="caller-supplied local PBF"
  echo "Using the region extracted locally from the Great Britain master: $SOURCE_PBF"
else
  CACHE_VALID=false
  if [[ -s "$PBF_NAME" && -s "$PBF_NAME.md5" ]] && verify_md5 "$PBF_NAME" "$PBF_NAME.md5" && osmium fileinfo -e "$PBF_NAME" >/dev/null 2>&1; then
    CACHE_VALID=true
    echo "A checksum-verified cached Merseyside extract is available; attempting fresh sources first."
  else
    rm -f "$PBF_NAME" "$PBF_NAME.md5"
  fi

  downloaded=false
  # First try the direct Geofabrik Merseyside extracts and dated snapshot.
  for source_url in "$PRIMARY_URL" "$SNAPSHOT_URL"; do
    temporary="$PBF_NAME.download"
    checksum_temporary="$PBF_NAME.md5.download"
    rm -f "$temporary" "$checksum_temporary"
    echo "Trying Geofabrik Merseyside source: $source_url"
    if ! download_with_retry "$source_url" "$temporary"; then
      echo "PBF download failed for $source_url; moving to the next source." >&2
      rm -f "$temporary"
      continue
    fi
    if ! download_with_retry "${source_url}.md5" "$checksum_temporary"; then
      echo "Checksum download failed for $source_url; refusing an unverified extract." >&2
      rm -f "$temporary" "$checksum_temporary"
      continue
    fi
    if verify_md5 "$temporary" "$checksum_temporary" && osmium fileinfo -e "$temporary" >/dev/null 2>&1; then
      mv "$temporary" "$PBF_NAME"
      mv "$checksum_temporary" "$PBF_NAME.md5"
      downloaded=true
      EXTRACT_PROVIDER="Geofabrik"
      echo "Downloaded, checksum-verified and parsed the Merseyside extract from $source_url."
      break
    fi
    echo "Checksum or PBF validation failed for $source_url; trying the next source." >&2
    rm -f "$temporary" "$checksum_temporary"
  done

  # Independent fallback: download the North West England PBF from OpenStreetMap
  # France, verify its provider checksum, then clip it to the committed
  # Geofabrik Merseyside polygon. Never package the wider North West extract.
  if [[ "$downloaded" != true ]]; then
    nw_temporary="$NORTH_WEST_PBF.download"
    nw_checksum="$NORTH_WEST_PBF.md5.download"
    clipped_temporary="$PBF_NAME.download"
    clipped_checksum="$PBF_NAME.md5.download"
    rm -f "$nw_temporary" "$nw_checksum" "$clipped_temporary" "$clipped_checksum"
    echo "Trying independent OpenStreetMap France North West England source: $NORTH_WEST_URL"
    if download_with_retry "$NORTH_WEST_URL" "$nw_temporary" &&
       download_with_retry "$NORTH_WEST_MD5_URL" "$nw_checksum" &&
       verify_md5 "$nw_temporary" "$nw_checksum" &&
       osmium fileinfo -e "$nw_temporary" >/dev/null 2>&1; then
      echo "North West PBF passed provider MD5 and Osmium validation; clipping to Merseyside."
      osmium extract --polygon "$POLYGON_FILE" --strategy=smart --set-bounds \
        --overwrite --output "$clipped_temporary" "$nw_temporary"
      if osmium fileinfo -e "$clipped_temporary" >/dev/null 2>&1 &&
         verify_merseyside_bounds "$clipped_temporary"; then
        md5sum "$clipped_temporary" > "$clipped_checksum"
        mv "$clipped_temporary" "$PBF_NAME"
        mv "$clipped_checksum" "$PBF_NAME.md5"
        downloaded=true
        EXTRACT_PROVIDER="OpenStreetMap France"
        echo "Created a validated Merseyside-only extract from the OpenStreetMap France North West source."
      else
        echo "Clipped PBF failed parser or Merseyside-bounds validation." >&2
        rm -f "$clipped_temporary" "$clipped_checksum"
      fi
    else
      echo "OpenStreetMap France North West source failed download, checksum or PBF validation." >&2
    fi
    rm -f "$nw_temporary" "$nw_checksum"
  fi

  # Optional BBBike custom PBF extract. BBBike creates custom areas on demand
  # and sends a temporary download link; therefore the build never guesses a
  # permanent URL. Supply both URL and provider checksum URL from the extract
  # request. This keeps the fallback reproducible and checksum-authenticated.
  if [[ "$downloaded" != true && -n "${BBBIKE_PBF_URL:-}" ]]; then
    bbbike_temporary="$PBF_NAME.bbbike.download"
    bbbike_checksum="$PBF_NAME.bbbike.md5.download"
    rm -f "$bbbike_temporary" "$bbbike_checksum"
    if [[ -z "${BBBIKE_MD5_URL:-}" && -z "${BBBIKE_EXPECTED_MD5:-}" ]]; then
      echo "BBBike PBF URL is set but no provider checksum URL or expected MD5 was supplied; refusing an unverified extract." >&2
    else
      echo "Trying configured BBBike custom Merseyside PBF extract."
      if download_with_retry "$BBBIKE_PBF_URL" "$bbbike_temporary"; then
        if [[ -n "${BBBIKE_MD5_URL:-}" ]]; then
          download_with_retry "$BBBIKE_MD5_URL" "$bbbike_checksum" || true
        elif [[ "${BBBIKE_EXPECTED_MD5:-}" =~ ^[[:xdigit:]]{32}$ ]]; then
          printf '%s  %s\n' "$BBBIKE_EXPECTED_MD5" "$bbbike_temporary" > "$bbbike_checksum"
        fi
      fi
      if [[ -s "$bbbike_temporary" && -s "$bbbike_checksum" ]] &&
         verify_md5 "$bbbike_temporary" "$bbbike_checksum" &&
         osmium fileinfo -e "$bbbike_temporary" >/dev/null 2>&1; then
        if verify_merseyside_bounds "$bbbike_temporary"; then
          mv "$bbbike_temporary" "$PBF_NAME"
          mv "$bbbike_checksum" "$PBF_NAME.md5"
          downloaded=true
          EXTRACT_PROVIDER="BBBike"
          echo "Downloaded and validated the BBBike Merseyside extract."
        else
          echo "BBBike PBF bounds do not match the Merseyside build area; refusing it." >&2
        fi
      else
        echo "BBBike source failed download, provider MD5 or PBF validation." >&2
      fi
    fi
    rm -f "$bbbike_temporary" "$bbbike_checksum"
  fi

  if [[ "$downloaded" != true ]]; then
    if [[ "$CACHE_VALID" == true ]]; then
      EXTRACT_PROVIDER="verified local cache"
      echo "All live sources are unavailable; using the previously cached, checksum-verified Merseyside extract." >&2
    else
      echo "Unable to obtain a verified Merseyside PBF from Geofabrik, OpenStreetMap France or configured BBBike, and no verified cache is available." >&2
      exit 1
    fi
  fi
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
  --source-attribution "© OpenMapTiles; © OpenStreetMap contributors (ODbL 1.0): https://www.openstreetmap.org/copyright; OSM extract provider: $EXTRACT_PROVIDER; Merseyside clipping polygon: Geofabrik; Contains OS data © Crown copyright and database right; Noto Sans glyphs under the SIL Open Font License"

echo
echo "Liverpool package build completed:"
find "$OUTPUT_DIR" -maxdepth 1 -type f -printf '%f (%s bytes)\n' | sort
echo "The output is unsigned. Publish only after reviewing licences, bounds and attribution."
