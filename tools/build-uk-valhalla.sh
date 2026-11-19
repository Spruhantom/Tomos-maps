#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$(realpath -m "${DATA_DIR:-$ROOT/.england-routing-build}")"
OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR:-$ROOT/dist/england-routing}")"
PBF_NAME="england-latest.osm.pbf"
PBF_URL="${OSM_PBF_URL:-https://download.geofabrik.de/europe/united-kingdom/england-latest.osm.pbf}"
VALHALLA_IMAGE="${VALHALLA_IMAGE:-ghcr.io/valhalla/valhalla:3.9.1}"
TIMEZONE_DATA_URL="${TIMEZONE_DATA_URL:-https://github.com/evansiroky/timezone-boundary-builder/releases/download/2026d/timezones-with-oceans-1970.shapefile.zip}"
TIMEZONE_DATA_SHA256="f9b2f0304772f2a944486da5d13d1740ff9754e76b983f80450b0e4d74131931"
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
BUILD_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BUILDER_COMMIT="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || printf unknown)}"

echo "[1/8] Downloading England OSM PBF and provider checksum"
curl --fail --location --retry 5 --retry-all-errors --retry-delay 8 --connect-timeout 30 --max-time 7200 "$PBF_URL" -o "$PBF"
curl --fail --location --retry 5 --retry-all-errors --retry-delay 8 --connect-timeout 30 --max-time 120 "$PBF_URL.md5" -o "$PBF.md5"
EXPECTED_MD5="$(awk 'NF {print tolower($1); exit}' "$PBF.md5" | tr -d '\r')"
[[ "$EXPECTED_MD5" =~ ^[a-f0-9]{32}$ ]] || { echo "Malformed Geofabrik MD5 sidecar." >&2; exit 1; }
printf '%s  %s\n' "$EXPECTED_MD5" "$PBF" | md5sum --check --status || { echo "England PBF checksum mismatch." >&2; exit 1; }
PBF_BYTES="$(stat -c '%s' "$PBF")"
PBF_SHA256="$(sha256sum "$PBF" | awk '{print $1}')"
SOURCE_RETRIEVED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Verified source: $PBF_BYTES bytes, MD5 $EXPECTED_MD5, SHA-256 $PBF_SHA256"

echo "[2/8] Checking England graph build disk headroom"
FREE_BYTES="$(df -PB1 "$DATA_DIR" | awk 'NR==2 {print $4}')"
MIN_FREE_BYTES=$((20 * 1024 * 1024 * 1024))
if (( FREE_BYTES < MIN_FREE_BYTES )); then
  echo "At least 20 GiB free disk is required after the PBF download; found $((FREE_BYTES / 1024 / 1024 / 1024)) GiB." >&2
  echo "Use a larger-disk runner rather than weakening this guard." >&2
  exit 1
fi

echo "[3/8] Downloading and verifying pinned timezone boundary data"
TIMEZONE_ARCHIVE="$DATA_DIR/timezones-with-oceans-1970.shapefile.zip"
curl --fail --location --retry 5 --retry-all-errors --retry-delay 8 --connect-timeout 30 --max-time 900 "$TIMEZONE_DATA_URL" -o "$TIMEZONE_ARCHIVE"
printf '%s  %s\n' "$TIMEZONE_DATA_SHA256" "$TIMEZONE_ARCHIVE" | sha256sum --check --status || { echo "Timezone boundary archive SHA-256 mismatch." >&2; exit 1; }

echo "[4/8] Building Valhalla graph, admin and timezone databases"
docker pull "$VALHALLA_IMAGE"
VALHALLA_IMAGE_DIGEST="$(docker image inspect --format '{{index .RepoDigests 0}}' "$VALHALLA_IMAGE")"
[[ "$VALHALLA_IMAGE_DIGEST" == *@sha256:* ]] || { echo "Could not resolve an immutable Valhalla image digest." >&2; exit 1; }
echo "Pinned runtime image: $VALHALLA_IMAGE_DIGEST"
docker run --rm --user "$(id -u):$(id -g)" -v "$DATA_DIR:/data" "$VALHALLA_IMAGE" bash -Eeuo pipefail -c '
  mkdir -p /data/tiles
  valhalla_build_config --mjolnir-tile-dir /data/tiles --mjolnir-tile-extract /data/tiles.tar --mjolnir-timezone /data/timezones.sqlite --mjolnir-admin /data/admins.sqlite > /data/valhalla.json
  mkdir -p /tmp/tomos-timezones
  cp /usr/local/bin/valhalla_build_timezones /tmp/tomos-timezones/valhalla_build_timezones
  grep -Fq "https://github.com/evansiroky/timezone-boundary-builder/releases/download/2026d/timezones-with-oceans-1970.shapefile.zip" /tmp/tomos-timezones/valhalla_build_timezones
  sed -i "s@https://github.com/evansiroky/timezone-boundary-builder/releases/download/2026d/timezones-with-oceans-1970.shapefile.zip@file:///data/timezones-with-oceans-1970.shapefile.zip@" /tmp/tomos-timezones/valhalla_build_timezones
  chmod +x /tmp/tomos-timezones/valhalla_build_timezones
  cd /tmp/tomos-timezones
  ./valhalla_build_timezones > /data/timezones.sqlite
  valhalla_build_admins -c /data/valhalla.json /data/england-latest.osm.pbf
  valhalla_build_tiles -c /data/valhalla.json /data/england-latest.osm.pbf
  valhalla_build_extract -c /data/valhalla.json -v
  test -s /data/tiles.tar
  test -s /data/admins.sqlite
  test -s /data/timezones.sqlite
'

echo "[5/8] Starting temporary Valhalla service for route smoke test"
CONTAINER="tomos-england-valhalla-smoke-$$"
cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$CONTAINER" -p 127.0.0.1:8002:8002 -v "$DATA_DIR:/data:ro" "$VALHALLA_IMAGE" valhalla_service /data/valhalla.json 1 >/dev/null

echo "[6/8] Validating Liverpool-to-Manchester driving route"
python3 - "$ROUTE_FROM_LAT" "$ROUTE_FROM_LON" "$ROUTE_TO_LAT" "$ROUTE_TO_LON" <<'PY'
import json, sys, time, urllib.request, urllib.error

a_lat, a_lon, b_lat, b_lon = map(float, sys.argv[1:])
routes = [
    ("Configured Liverpool-Manchester", a_lat, a_lon, b_lat, b_lon),
    ("London-Birmingham", 51.5074, -0.1278, 52.4862, -1.8904),
    ("Bristol-Exeter", 51.4545, -2.5879, 50.7184, -3.5339),
    ("Newcastle-York", 54.9783, -1.6178, 53.9590, -1.0815),
    ("Nottingham-Sheffield", 52.9548, -1.1581, 53.3811, -1.4701),
]
for name, start_lat, start_lon, end_lat, end_lon in routes:
    if not (-90 <= start_lat <= 90 and -180 <= start_lon <= 180 and
            -90 <= end_lat <= 90 and -180 <= end_lon <= 180):
        raise SystemExit(f"{name}: smoke-test coordinates are invalid.")
    payload = json.dumps({
        "locations": [
            {"lat": start_lat, "lon": start_lon, "type": "break"},
            {"lat": end_lat, "lon": end_lon, "type": "break"},
        ],
        "costing": "auto",
        "directions_options": {"units": "kilometers"},
    }).encode()
    last_error = None
    for attempt in range(60):
        try:
            request = urllib.request.Request(
                "http://127.0.0.1:8002/route", data=payload,
                headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.loads(response.read())
            trip = result.get("trip", {})
            summary = trip.get("summary", {})
            length = float(summary.get("length", 0))
            if trip.get("status") != 0 or length <= 0 or not trip.get("legs"):
                raise RuntimeError("Valhalla returned no valid route: " + json.dumps(trip)[:1000])
            print(f"{name}: passed, {length:.1f} km, {summary.get('time', 0)} seconds.")
            break
        except (OSError, urllib.error.URLError, RuntimeError, ValueError) as error:
            last_error = error
            time.sleep(5)
    else:
        raise SystemExit(f"{name} route smoke test failed: {last_error}")
print(f"All {len(routes)} England route smoke tests passed.")
PY

echo "[7/8] Splitting graph archive into independently verifiable parts"
rm -f "$OUTPUT_DIR"/tiles.tar.part-*
split --bytes=450000000 --numeric-suffixes=1 --suffix-length=4 "$DATA_DIR/tiles.tar" "$OUTPUT_DIR/tiles.tar.part-"
for file in admins.sqlite timezones.sqlite valhalla.json; do
  test -s "$DATA_DIR/$file"
  cp "$DATA_DIR/$file" "$OUTPUT_DIR/$file"
done

echo "[8/8] Packaging licence notices and writing ordered manifest, provenance and SHA-256 checksums"
mkdir -p "$OUTPUT_DIR/licenses"
cp "$ROOT"/licenses/third-party/* "$OUTPUT_DIR/licenses/"
python3 - "$OUTPUT_DIR" "$DATA_DIR/tiles.tar" "$PBF_URL" "$EXPECTED_MD5" "$PBF_BYTES" "$PBF_SHA256" "$BUILD_STARTED_AT" "$SOURCE_RETRIEVED_AT" "$VALHALLA_IMAGE_DIGEST" "$BUILDER_COMMIT" "$TIMEZONE_DATA_URL" "$TIMEZONE_DATA_SHA256" <<'PY'
import hashlib, json, sys
from pathlib import Path

root = Path(sys.argv[1])
archive_path = Path(sys.argv[2])
source_url, source_md5, source_bytes = sys.argv[3], sys.argv[4], int(sys.argv[5])
source_sha256, build_started_at, source_retrieved_at = sys.argv[6:9]
image_digest, builder_commit, timezone_url, timezone_sha256 = sys.argv[9:13]

def hash_file(path):
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()

archive_size, archive_sha = hash_file(archive_path)
parts = []
part_paths = sorted(root.glob("tiles.tar.part-*"))
if not part_paths:
    raise SystemExit("No graph archive parts were produced.")
for index, path in enumerate(part_paths, start=1):
    size, sha = hash_file(path)
    if size <= 0 or size > 450_000_000:
        raise SystemExit(f"Invalid graph part size: {path.name} ({size} bytes)")
    parts.append({"index": index, "file": path.name, "sizeBytes": size, "sha256": sha})
if sum(part["sizeBytes"] for part in parts) != archive_size:
    raise SystemExit("Split graph part sizes do not equal the original archive size.")

manifest = {
    "schemaVersion": 1,
    "format": "concatenated-tar",
    "id": "england-routing-graph",
    "graphVersion": f"england-pbf-md5-{source_md5}",
    "region": "England",
    "engine": "Valhalla",
    "partSizeLimitBytes": 450_000_000,
    "archive": {"file": "tiles.tar", "sizeBytes": archive_size, "sha256": archive_sha},
    "concatenationOrder": "ascending index; concatenate raw part bytes without separators",
    "parts": parts,
}
(root / "england-routing.manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

asset_names = [path.name for path in part_paths]
asset_names += ["admins.sqlite", "timezones.sqlite", "valhalla.json", "england-routing.manifest.json"]
asset_names += [f"licenses/{path.name}" for path in sorted((root / "licenses").glob("*")) if path.is_file()]
assets = {}
for name in asset_names:
    size, sha = hash_file(root / name)
    assets[name] = {"sizeBytes": size, "sha256": sha}

metadata = {
    "schemaVersion": 1,
    "id": "england-routing",
    "layer": "routing",
    "name": "England Valhalla routing graph",
    "coverage": {"country": "GB", "region": "England", "name": "England"},
    "engine": "Valhalla",
    "source": "Geofabrik England OpenStreetMap extract",
    "sourceUrl": source_url,
    "sourceMd5": source_md5,
    "sourceSizeBytes": source_bytes,
    "sourceSha256": source_sha256,
    "buildStartedAtUtc": build_started_at,
    "retrievedAtUtc": source_retrieved_at,
    "builderCommit": builder_commit,
    "buildProvenance": {
        "valhallaImage": "ghcr.io/valhalla/valhalla:3.9.1",
        "valhallaImageDigest": image_digest,
        "timezoneBoundaryDataUrl": timezone_url,
        "timezoneBoundaryDataRelease": "2026d",
        "timezoneBoundaryDataSha256": timezone_sha256,
    },
    "licence": "ODbL-1.0",
    "attribution": "© OpenStreetMap contributors",
    "attributionUrl": "https://www.openstreetmap.org/copyright",
    "validated": {"routeSmokeTest": "Liverpool-to-Manchester", "costing": "auto"},
    "archiveManifest": "england-routing.manifest.json",
    "archiveSizeBytes": archive_size,
    "archiveSha256": archive_sha,
    "licenseNoticeDirectory": "licenses/",
    "assets": assets,
    "appIntegration": "not-enabled",
}
(root / "england-routing.metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
print(f"Manifest written: {len(parts)} parts, {archive_size} archive bytes.")
PY

python3 "$ROOT/tools/assemble_routing_graph.py" \
  --manifest "$OUTPUT_DIR/england-routing.manifest.json" \
  --parts-dir "$OUTPUT_DIR"
echo "England Valhalla test graph built and smoke-tested. Offline app routing remains disabled."
