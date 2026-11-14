#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$(realpath -m "${DATA_DIR:-$ROOT/.england-routing-build}")"
OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR:-$ROOT/dist/england-routing}")"
PBF_NAME="england-latest.osm.pbf"
PBF_URL="${OSM_PBF_URL:-https://download.geofabrik.de/europe/great-britain/england-latest.osm.pbf}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:latest}"
ROUTE_FROM_LAT="${ROUTE_FROM_LAT:-53.4084}"
ROUTE_FROM_LON="${ROUTE_FROM_LON:--2.9916}"
ROUTE_TO_LAT="${ROUTE_TO_LAT:-53.4808}"
ROUTE_TO_LON="${ROUTE_TO_LON:--2.2426}"
for command in curl md5sum sha256sum docker python3 split; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done
[[ "$PBF_URL" == https://* ]] || { echo "OSM_PBF_URL must use HTTPS." >&2; exit 2; }
mkdir -p "$DATA_DIR" "$OUTPUT_DIR"
PBF="$DATA_DIR/$PBF_NAME"
echo "[1/7] Downloading England OSM PBF and provider checksum"
curl --fail --location --retry 5 --retry-all-errors --retry-delay 8 --connect-timeout 30 --max-time 7200 "$PBF_URL" -o "$PBF"
curl --fail --location --retry 5 --retry-all-errors --retry-delay 8 --connect-timeout 30 --max-time 120 "$PBF_URL.md5" -o "$PBF.md5"
EXPECTED_MD5="$(awk 'NF {print tolower($1); exit}' "$PBF.md5" | tr -d '\r')"
[[ "$EXPECTED_MD5" =~ ^[a-f0-9]{32}$ ]] || { echo "Malformed Geofabrik MD5 sidecar." >&2; exit 1; }
printf '%s  %s\n' "$EXPECTED_MD5" "$PBF" | md5sum --check --status || { echo "England PBF checksum mismatch." >&2; exit 1; }
PBF_BYTES="$(stat -c '%s' "$PBF")"
echo "Verified source: $PBF_BYTES bytes, MD5 $EXPECTED_MD5"
echo "[2/7] Checking England graph build disk headroom"
FREE_BYTES="$(df -PB1 "$DATA_DIR" | awk 'NR==2 {print $4}')"
MIN_FREE_BYTES=$((20 * 1024 * 1024 * 1024))
if (( FREE_BYTES < MIN_FREE_BYTES )); then
  echo "At least 20 GiB free disk is required after the PBF download; found $((FREE_BYTES / 1024 / 1024 / 1024)) GiB." >&2
  echo "Use a larger-disk runner if this England test exceeds the available capacity. No incomplete graph will be published." >&2
  exit 1
fi
echo "[3/7] Building Valhalla graph, admin and timezone databases"
docker pull "$VALHALLA_IMAGE"
docker run --rm --user "$(id -u):$(id -g)" -v "$DATA_DIR:/data" "$VALHALLA_IMAGE" bash -Eeuo pipefail -c '
  mkdir -p /data/tiles
  valhalla_build_config --mjolnir-tile-dir /data/tiles --mjolnir-tile-extract /data/tiles.tar --mjolnir-timezone /data/timezones.sqlite --mjolnir-admin /data/admins.sqlite > /data/valhalla.json
  valhalla_build_timezones > /data/timezones.sqlite
  valhalla_build_admins -c /data/valhalla.json /data/england-latest.osm.pbf
  valhalla_build_tiles -c /data/valhalla.json /data/england-latest.osm.pbf
  valhalla_build_extract -c /data/valhalla.json -v
  test -s /data/tiles.tar
  test -s /data/admins.sqlite
  test -s /data/timezones.sqlite
'
echo "[4/7] Starting temporary Valhalla service for route smoke test"
CONTAINER="tomos-uk-valhalla-smoke-$$"
cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$CONTAINER" -p 127.0.0.1:8002:8002 -v "$DATA_DIR:/data:ro" "$VALHALLA_IMAGE" valhalla_service /data/valhalla.json 1 >/dev/null
echo "[5/7] Validating Liverpool-to-Manchester driving route"
python3 - "$ROUTE_FROM_LAT" "$ROUTE_FROM_LON" "$ROUTE_TO_LAT" "$ROUTE_TO_LON" <<'PY'
import json, sys, time, urllib.request, urllib.error
a_lat, a_lon, b_lat, b_lon = map(float, sys.argv[1:])
if not (-90 <= a_lat <= 90 and -180 <= a_lon <= 180 and -90 <= b_lat <= 90 and -180 <= b_lon <= 180):
    raise SystemExit("Smoke-test coordinates are invalid.")
payload = json.dumps({"locations":[{"lat":a_lat,"lon":a_lon,"type":"break"},{"lat":b_lat,"lon":b_lon,"type":"break"}],"costing":"auto","directions_options":{"units":"kilometers"}}).encode()
last_error = None
for attempt in range(60):
    try:
        request = urllib.request.Request("http://127.0.0.1:8002/route", data=payload, headers={"Content-Type":"application/json"})
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.loads(response.read())
        trip = result.get("trip", {})
        summary = trip.get("summary", {})
        length = float(summary.get("length", 0))
        if trip.get("status") != 0 or length <= 0 or not trip.get("legs"):
            raise RuntimeError("Valhalla returned no valid route: " + json.dumps(trip)[:1000])
        print(f"Route smoke test passed: {length:.1f} km, {summary.get('time',0)} seconds.")
        break
    except (OSError, urllib.error.URLError, RuntimeError, ValueError) as error:
        last_error = error
        time.sleep(5)
else:
    raise SystemExit(f"Valhalla route smoke test failed: {last_error}")
PY
echo "[6/7] Copying England graph and support assets"
# GitHub Releases has a per-asset size ceiling; split the graph tar into
# independently downloadable parts below 1.8 GB. Consumers can concatenate
# them in lexical order before extracting.
split -b 1800M -d -a 3 "$DATA_DIR/tiles.tar" "$OUTPUT_DIR/tiles.tar.part-"
for file in admins.sqlite timezones.sqlite valhalla.json; do
  test -s "$DATA_DIR/$file"
  cp "$DATA_DIR/$file" "$OUTPUT_DIR/$file"
done
compgen -G "$OUTPUT_DIR/tiles.tar.part-*" >/dev/null || { echo "Graph archive splitting failed." >&2; exit 1; }
echo "[7/7] Writing provenance and SHA-256 checksums"
python3 - "$OUTPUT_DIR" "$PBF_URL" "$EXPECTED_MD5" "$PBF_BYTES" <<'PY'
import hashlib, json, sys
from pathlib import Path
root = Path(sys.argv[1]); files = {}
names = sorted(p.name for p in root.glob("tiles.tar.part-*")) + ["admins.sqlite","timezones.sqlite","valhalla.json"]
for name in names:
    path=root/name; digest=hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b""): digest.update(chunk)
    files[name]={"sizeBytes":path.stat().st_size,"sha256":digest.hexdigest()}
metadata={"schemaVersion":1,"id":"england-routing","layer":"routing","name":"England Valhalla routing graph",
"coverage":{"country":"GB","region":"England","name":"England"},"engine":"Valhalla",
"source":"Geofabrik England OpenStreetMap extract","sourceUrl":sys.argv[2],
"sourceMd5":sys.argv[3],"sourceSizeBytes":int(sys.argv[4]),"licence":"ODbL-1.0",
"attribution":"© OpenStreetMap contributors","attributionUrl":"https://www.openstreetmap.org/copyright",
"validated":{"routeSmokeTest":"Liverpool-to-Manchester","costing":"auto"},"assets":files,"appIntegration":"not-enabled"}
(root/"england-routing.metadata.json").write_text(json.dumps(metadata,indent=2)+"\n")
PY
echo "England Valhalla test graph built and smoke-tested. Offline app routing remains disabled."
