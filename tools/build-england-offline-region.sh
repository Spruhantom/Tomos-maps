#!/usr/bin/env bash
set -Eeuo pipefail

# Build an unsigned regional candidate for Greater London or South East England.
# No catalogue is signed, uploaded or enabled by this script.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGION_ID="${REGION_ID:-}"
REGION_NAME="${REGION_NAME:-}"
REGION_CODE="${REGION_CODE:-}"
POLYGON_FILE="${POLYGON_FILE:-}"
DATA_DIR="$(realpath -m "${DATA_DIR:-$ROOT/.regional-build/$REGION_ID}")"
OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR:-$ROOT/dist/$REGION_ID}")"
PBF_URL="${PBF_URL:-https://download.geofabrik.de/europe/united-kingdom/england-latest.osm.pbf}"
PBF_CHECKSUM_URL="${PBF_CHECKSUM_URL:-$PBF_URL.md5}"
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:latest}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"
VERSION="${PACKAGE_VERSION:-$(date -u +%Y.%m.%d)}"

[[ "$REGION_ID" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo "Set REGION_ID to a safe lowercase area id." >&2; exit 2; }
[[ -n "$REGION_NAME" && -n "$REGION_CODE" ]] || { echo "REGION_NAME and REGION_CODE are required." >&2; exit 2; }
[[ -s "$POLYGON_FILE" ]] || { echo "POLYGON_FILE is missing: $POLYGON_FILE" >&2; exit 2; }
[[ "$PBF_URL" == https://* && "$PBF_CHECKSUM_URL" == https://* ]] || { echo "PBF source and checksum must use HTTPS." >&2; exit 2; }

for command in docker osmium python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done
python3 -c 'import pyproj' >/dev/null 2>&1 || { echo "Install pyproj: python3 -m pip install pyproj" >&2; exit 2; }

mkdir -p "$DATA_DIR" "$OUTPUT_DIR"
PBF="$DATA_DIR/$REGION_ID.osm.pbf"

echo "[1/7] Acquire, checksum-verify and clip the England OSM extract"
bash "$ROOT/tools/acquire-regional-osm-pbf.sh" \
  --url "$PBF_URL" --checksum-url "$PBF_CHECKSUM_URL" \
  --polygon "$POLYGON_FILE" --output "$PBF" --cache-dir "$DATA_DIR/pbf-cache"
osmium fileinfo -e "$PBF" >/dev/null

echo "[2/7] Read the actual clipped-region coverage bounds"
BOUNDS="$(osmium fileinfo -e -g header.boxes "$PBF" | tr -d '()')"
IFS=',' read -r WEST SOUTH EAST NORTH <<< "$BOUNDS"
[[ -n "$WEST" && -n "$SOUTH" && -n "$EAST" && -n "$NORTH" ]] || {
  echo "Could not read region bounds from PBF: $BOUNDS" >&2; exit 1;
}
echo "Coverage: west=$WEST south=$SOUTH east=$EAST north=$NORTH"

echo "[3/7] Build MapLibre vector MBTiles from the clipped extract"
docker run --rm --user "$(id -u):$(id -g)" \
  -e JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx6g}" \
  -v "$DATA_DIR:/data" "$PLANETILER_IMAGE" \
  --download=cached "--osm_path=/data/$REGION_ID.osm.pbf" \
  "--area=$REGION_ID" --minzoom=0 --maxzoom=14 --output=/data/region.mbtiles
test -s "$DATA_DIR/region.mbtiles"

echo "[4/7] Build and smoke-test the Valhalla routing graph from the same PBF"
docker run --rm --entrypoint /bin/bash \
  -v "$ROOT:/workspace" -v "$DATA_DIR:/data" -w /workspace \
  "$VALHALLA_IMAGE" \
  -lc "bash tools/build-valhalla-region.sh '/data/$REGION_ID.osm.pbf' '/data/valhalla' && bash tools/test-local-valhalla-route.sh '/data/valhalla'"
test -s "$DATA_DIR/valhalla/tiles.tar"

echo "[5/7] Build the regional postcode search data"
python3 "$ROOT/tools/download-codepoint-open.py" --output "$DATA_DIR/code-point-open-gb.zip"
python3 "$ROOT/tools/prepare-codepoint-open.py" \
  --source-zip "$DATA_DIR/code-point-open-gb.zip" \
  --output "$DATA_DIR/postcodes.csv" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH"
test -s "$DATA_DIR/postcodes.csv"

echo "[6/7] Download glyph assets and package the three source layers"
bash "$ROOT/tools/download-offline-map-glyphs.sh" "$DATA_DIR/glyphs"
python3 "$ROOT/tools/package-regional-data.py" \
  --id "$REGION_ID" --name "$REGION_NAME" --version "$VERSION" --region-code "$REGION_CODE" \
  --map-data "$DATA_DIR/region.mbtiles" \
  --routing-data "$DATA_DIR/valhalla/tiles.tar" \
  --routing-admin-data "$DATA_DIR/valhalla/admins.sqlite" \
  --routing-timezone-data "$DATA_DIR/valhalla/timezones.sqlite" \
  --routing-config "$DATA_DIR/valhalla/valhalla.json" \
  --glyphs-dir "$DATA_DIR/glyphs" --postcode-csv "$DATA_DIR/postcodes.csv" \
  --output "$OUTPUT_DIR" \
  --west "$WEST" --south "$SOUTH" --east "$EAST" --north "$NORTH" \
  --source-attribution "© OpenStreetMap contributors; ODbL 1.0: https://opendatacommons.org/licenses/odbl/1-0/; OSM attribution: https://www.openstreetmap.org/copyright; Geofabrik England extract clipped to $REGION_NAME; Contains OS data © Crown copyright; Contains Royal Mail data © Royal Mail copyright; Contains National Statistics data © Crown copyright; OS Open Government Licence v3.0: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/; Noto Sans © The Noto Project Authors, SIL Open Font License 1.1"

echo "[7/7] Split into independently downloadable MAP, ROUTING and SEARCH candidate packages"
MANIFEST="$(find "$OUTPUT_DIR" -maxdepth 1 -name "$REGION_ID-*.manifest.json" -print -quit)"
ARCHIVE="$(find "$OUTPUT_DIR" -maxdepth 1 -name "$REGION_ID-*.zip" -print -quit)"
[[ -s "$MANIFEST" && -s "$ARCHIVE" ]] || { echo "Combined regional package missing." >&2; exit 1; }
python3 "$ROOT/tools/split-regional-package-layers.py" \
  --manifest "$MANIFEST" --archive "$ARCHIVE" --output-dir "$OUTPUT_DIR/layers"
python3 - "$OUTPUT_DIR/layers" <<'PY'
import json, pathlib, sys, zipfile
root=pathlib.Path(sys.argv[1]); expected={"MAP","ROUTING","SEARCH"}; found=set()
for metadata_path in root.glob("*.metadata.json"):
    metadata=json.loads(metadata_path.read_text())
    layer=metadata["layer"]
    if layer not in expected: raise SystemExit(f"Unexpected layer {layer}")
    manifest=root/metadata["manifestFile"]; archive=root/metadata["archiveFile"]
    if not manifest.is_file() or not archive.is_file(): raise SystemExit(f"Missing {layer} assets")
    with zipfile.ZipFile(archive) as z:
        bad=z.testzip()
        if bad: raise SystemExit(f"{layer} archive corrupt at {bad}")
        names=set(z.namelist())
        if layer=="MAP" and not any(n.endswith(".mbtiles") for n in names): raise SystemExit("MAP layer has no MBTiles")
        if layer=="ROUTING" and not all(any(n.endswith(s) for n in names) for s in ("tiles.tar","admins.sqlite","timezones.sqlite","valhalla.json")): raise SystemExit("ROUTING layer incomplete")
        if layer=="SEARCH":
            indexes=[n for n in names if n.endswith("search-index.json")]
            if len(indexes)!=1: raise SystemExit("SEARCH layer must contain one index")
            index=json.loads(z.read(indexes[0]))
            if not index.get("records"): raise SystemExit("SEARCH index is empty")
    found.add(layer)
if found!=expected: raise SystemExit(f"Expected all layers, found {sorted(found)}")
print("All three regional candidate layers passed ZIP and schema checks.")
PY

echo "Unsigned candidate packages ready in $OUTPUT_DIR/layers"
echo "No licence approval, catalogue signing, upload, release or app feature flag was changed."
