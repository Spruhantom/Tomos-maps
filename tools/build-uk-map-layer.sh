#!/usr/bin/env bash
set -Eeuo pipefail
# Build a UK-wide MapLibre vector map archive. Does not enable app downloads.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$(realpath -m "${DATA_DIR:-$ROOT/.uk-map-build}")"
OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR:-$ROOT/dist/uk-map}")"
PBF_NAME="united-kingdom-latest.osm.pbf"
PBF_URL="${UK_PBF_URL:-https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf}"
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:latest}"
MIN_ZOOM="${MIN_ZOOM:-0}"
MAX_ZOOM="${MAX_ZOOM:-12}"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx6g}"

for command in curl md5sum sha256sum docker python3 pmtiles; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done
[[ "$PBF_URL" == https://* ]] || { echo "UK_PBF_URL must use HTTPS." >&2; exit 2; }
[[ "$MIN_ZOOM" =~ ^[0-9]+$ && "$MAX_ZOOM" =~ ^[0-9]+$ ]] || { echo "Zoom levels must be integers." >&2; exit 2; }
(( MIN_ZOOM <= MAX_ZOOM && MAX_ZOOM <= 14 )) || { echo "Require 0 <= MIN_ZOOM <= MAX_ZOOM <= 14." >&2; exit 2; }

mkdir -p "$DATA_DIR" "$OUTPUT_DIR"
PBF="$DATA_DIR/$PBF_NAME"
CHECKSUM="$PBF.md5"
download() {
  curl --fail --location --silent --show-error --connect-timeout 30 --max-time 7200 \
    --retry 5 --retry-all-errors --retry-delay 8 "$1" -o "$2"
}

echo "[1/5] Downloading Geofabrik UK PBF and checksum"
download "$PBF_URL" "$PBF"
download "${PBF_URL}.md5" "$CHECKSUM"
EXPECTED_MD5="$(awk 'NF { print tolower($1); exit }' "$CHECKSUM" | tr -d '\r')"
[[ "$EXPECTED_MD5" =~ ^[a-f0-9]{32}$ ]] || { echo "Malformed Geofabrik MD5 sidecar." >&2; exit 1; }
printf '%s  %s\n' "$EXPECTED_MD5" "$PBF" | md5sum --check --status || { echo "PBF MD5 verification failed." >&2; exit 1; }
test -s "$PBF"
PBF_BYTES="$(stat -c '%s' "$PBF")"
echo "Verified source PBF: $PBF_BYTES bytes (MD5 $EXPECTED_MD5)"

echo "[2/5] Checking build capacity"
FREE_BYTES="$(df -PB1 "$DATA_DIR" | awk 'NR==2 {print $4}')"
MIN_FREE_BYTES=$((24 * 1024 * 1024 * 1024))
if (( FREE_BYTES < MIN_FREE_BYTES )); then
  echo "At least 24 GiB free disk is required after the PBF download; found $((FREE_BYTES / 1024 / 1024 / 1024)) GiB." >&2
  exit 1
fi

echo "[3/5] Generating MapLibre vector MBTiles with Planetiler"
docker run --rm --user "$(id -u):$(id -g)" -e JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS" \
  -v "$DATA_DIR:/data" "$PLANETILER_IMAGE" --download=cached \
  "--osm_path=/data/$PBF_NAME" --area=united-kingdom \
  "--minzoom=$MIN_ZOOM" "--maxzoom=$MAX_ZOOM" --output=/data/united-kingdom.mbtiles
test -s "$DATA_DIR/united-kingdom.mbtiles"

echo "[4/5] Converting MBTiles to PMTiles and verifying"
pmtiles convert "$DATA_DIR/united-kingdom.mbtiles" "$OUTPUT_DIR/united-kingdom.pmtiles"
pmtiles verify "$OUTPUT_DIR/united-kingdom.pmtiles"
test -s "$OUTPUT_DIR/united-kingdom.pmtiles"

echo "[5/5] Writing provenance metadata"
OUTPUT_BYTES="$(stat -c '%s' "$OUTPUT_DIR/united-kingdom.pmtiles")"
OUTPUT_SHA256="$(sha256sum "$OUTPUT_DIR/united-kingdom.pmtiles" | awk '{print $1}')"
BUILD_TIME="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
python3 - "$OUTPUT_DIR/united-kingdom.metadata.json" "$PBF_URL" "$EXPECTED_MD5" "$PBF_BYTES" "$OUTPUT_BYTES" "$OUTPUT_SHA256" "$BUILD_TIME" "$MIN_ZOOM" "$MAX_ZOOM" <<'PY'
import json, sys
from pathlib import Path
path, source_url, source_md5, source_bytes, output_bytes, output_sha, built, minzoom, maxzoom = sys.argv[1:]
metadata = {
    "schemaVersion": 1, "id": "uk-map", "layer": "map",
    "name": "United Kingdom vector map",
    "coverage": {"country": "GB", "name": "United Kingdom"},
    "format": "pmtiles", "tileSchema": "OpenMapTiles",
    "minZoom": int(minzoom), "maxZoom": int(maxzoom),
    "source": "Geofabrik United Kingdom OpenStreetMap extract",
    "sourceUrl": source_url, "sourceMd5": source_md5,
    "sourceSizeBytes": int(source_bytes), "licence": "ODbL-1.0",
    "attribution": "© OpenStreetMap contributors",
    "attributionUrl": "https://www.openstreetmap.org/copyright",
    "builtAt": built,
    "asset": {"file": "united-kingdom.pmtiles", "sizeBytes": int(output_bytes), "sha256": output_sha},
    "appIntegration": "not-enabled"
}
Path(path).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
echo "UK map layer built and verified:"
ls -lh "$OUTPUT_DIR/united-kingdom.pmtiles" "$OUTPUT_DIR/united-kingdom.metadata.json"
echo "No app download or offline mode has been enabled."
