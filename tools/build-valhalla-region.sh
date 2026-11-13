#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <licensed-region.osm.pbf> <output-directory>" >&2
  echo "Builds Valhalla graph tiles and a tiles.tar archive; it does not download source data." >&2
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

PBF="$(realpath "$1")"
OUTPUT="$(mkdir -p "$2" && realpath "$2")"
if [[ ! -s "$PBF" ]]; then
  echo "OSM PBF input is missing or empty: $PBF" >&2
  exit 2
fi

for command in valhalla_build_config valhalla_build_timezones valhalla_build_admins valhalla_build_tiles tar find sort; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required Valhalla/data command is not installed: $command" >&2
    exit 2
  fi
done

TILE_DIR="$OUTPUT/tiles"
TILE_EXTRACT="$OUTPUT/tiles.tar"
TIMEZONES="$OUTPUT/timezones.sqlite"
ADMINS="$OUTPUT/admins.sqlite"
CONFIG="$OUTPUT/valhalla.json"
mkdir -p "$TILE_DIR"

echo "[1/5] Generating Valhalla configuration"
valhalla_build_config \
  --mjolnir-tile-dir "$TILE_DIR" \
  --mjolnir-tile-extract "$TILE_EXTRACT" \
  --mjolnir-timezone "$TIMEZONES" \
  --mjolnir-admin "$ADMINS" > "$CONFIG"

echo "[2/5] Building timezone database"
valhalla_build_timezones > "$TIMEZONES"

echo "[3/5] Building administrative database from supplied extract"
valhalla_build_admins -c "$CONFIG" "$PBF"

echo "[4/5] Building routing graph tiles"
valhalla_build_tiles -c "$CONFIG" "$PBF"

echo "[5/5] Creating Valhalla tile archive"
(
  cd "$OUTPUT"
  find tiles -type f -print0 | sort -z | tar --null -T - -cf "$TILE_EXTRACT" --no-recursion
)

if [[ ! -s "$TILE_EXTRACT" ]]; then
  echo "Valhalla tile archive was not created." >&2
  exit 1
fi

echo "Valhalla graph ready: $TILE_EXTRACT"
echo "Use this file as --routing-data when running tools/package-regional-data.py."
echo "Keep valhalla.json and the source PBF for reproducible rebuilds."
