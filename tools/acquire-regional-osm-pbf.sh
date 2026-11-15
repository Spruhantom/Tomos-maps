#!/usr/bin/env bash
set -Eeuo pipefail

# Acquire a regional OSM PBF safely for any TomOS Maps region.
# A final cache entry is written only after checksum, Osmium and coverage checks.
#
# Required:
#   --output FILE --url HTTPS_URL
#   --checksum-url HTTPS_URL OR --sha256 HEX OR --md5 HEX
# Optional:
#   --polygon FILE       clip the source extract before publishing the output
#   --bounds W,S,E,N     require the final PBF to match this WGS84 envelope
#   --cache-dir DIR      cache directory (defaults to output directory)
#   --source-pbf FILE    use a local PBF instead of downloading
#
# Example:
#   bash tools/acquire-regional-osm-pbf.sh --url "$PBF_URL" \
#     --checksum-url "$PBF_URL.md5" --polygon data/boundaries/greater-london.poly \
#     --bounds "-0.51,51.28,0.34,51.70" --output .regional-build/london/region.osm.pbf

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT=""
URL=""
CHECKSUM_URL=""
EXPECTED_SHA256=""
EXPECTED_MD5=""
POLYGON=""
BOUNDS=""
CACHE_DIR=""
SOURCE_PBF=""

usage() {
  sed -n '1,24p' "$0" >&2
  exit 2
}

while (($#)); do
  case "$1" in
    --output) OUTPUT="${2:?}"; shift 2 ;;
    --url) URL="${2:?}"; shift 2 ;;
    --checksum-url) CHECKSUM_URL="${2:?}"; shift 2 ;;
    --sha256) EXPECTED_SHA256="${2:?}"; shift 2 ;;
    --md5) EXPECTED_MD5="${2:?}"; shift 2 ;;
    --polygon) POLYGON="${2:?}"; shift 2 ;;
    --bounds) BOUNDS="${2:?}"; shift 2 ;;
    --cache-dir) CACHE_DIR="${2:?}"; shift 2 ;;
    --source-pbf) SOURCE_PBF="${2:?}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

[[ -n "$OUTPUT" ]] || { echo "--output is required" >&2; exit 2; }
if [[ -z "$SOURCE_PBF" ]]; then
  [[ "$URL" == https://* ]] || { echo "A trusted HTTPS --url is required" >&2; exit 2; }
  checksum_count=0
  [[ -n "$CHECKSUM_URL" ]] && ((checksum_count+=1))
  [[ -n "$EXPECTED_SHA256" ]] && ((checksum_count+=1))
  [[ -n "$EXPECTED_MD5" ]] && ((checksum_count+=1))
  [[ "$checksum_count" -eq 1 ]] || {
    echo "Supply exactly one of --checksum-url, --sha256 or --md5" >&2
    exit 2
  }
fi
[[ -z "$POLYGON" || -s "$POLYGON" ]] || { echo "Polygon file is missing: $POLYGON" >&2; exit 2; }

for command in curl osmium sha256sum md5sum python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done

OUTPUT="$(realpath -m "$OUTPUT")"
CACHE_DIR="${CACHE_DIR:-$(dirname "$OUTPUT")}"
mkdir -p "$CACHE_DIR" "$(dirname "$OUTPUT")"
CACHE_DIR="$(realpath -m "$CACHE_DIR")"
CACHE_FILE="$CACHE_DIR/$(basename "$OUTPUT")"
LOCK_FILE="$CACHE_FILE.lock"
exec 9>"$LOCK_FILE"
flock -x 9

verify_bounds() {
  local file="$1"
  [[ -z "$BOUNDS" ]] && return 0
  local actual
  actual="$(osmium fileinfo -e -g header.boxes "$file" | tr -d '()')"
  python3 - "$actual" "$BOUNDS" <<'PY'
import sys
actual = [float(x) for x in sys.argv[1].split(",") if x.strip()]
expected = [float(x) for x in sys.argv[2].split(",")]
if len(actual) != 4 or len(expected) != 4:
    raise SystemExit("Coverage bounds must contain exactly west,south,east,north")
if not (actual[0] <= expected[0] and actual[1] <= expected[1]
        and actual[2] >= expected[2] and actual[3] >= expected[3]):
    raise SystemExit(f"Source coverage {actual} does not fully cover requested bounds {expected}")
PY
}

verify_digest() {
  local file="$1" checksum_file="$2"
  if [[ -n "$EXPECTED_SHA256" ]]; then
    [[ "$EXPECTED_SHA256" =~ ^[[:xdigit:]]{64}$ ]] || return 1
    [[ "$(sha256sum "$file" | awk '{print tolower($1)}')" == "${EXPECTED_SHA256,,}" ]]
  elif [[ -n "$EXPECTED_MD5" ]]; then
    [[ "$EXPECTED_MD5" =~ ^[[:xdigit:]]{32}$ ]] || return 1
    [[ "$(md5sum "$file" | awk '{print tolower($1)}')" == "${EXPECTED_MD5,,}" ]]
  else
    [[ -s "$checksum_file" ]] || return 1
    local expected actual
    expected="$(awk 'NF {gsub(/[^[:xdigit:]].*/, "", $1); print tolower($1); exit}' "$checksum_file")"
    [[ "$expected" =~ ^([[:xdigit:]]{32}|[[:xdigit:]]{64})$ ]] || return 1
    if [[ "${#expected}" -eq 32 ]]; then actual="$(md5sum "$file" | awk '{print tolower($1)}')"
    else actual="$(sha256sum "$file" | awk '{print tolower($1)}')"; fi
    [[ "$actual" == "$expected" ]]
  fi
}

validate_pbf() {
  local file="$1"
  [[ -s "$file" ]] || return 1
  osmium fileinfo -e "$file" >/dev/null 2>&1
  verify_bounds "$file"
}

# A previously verified cache is reusable offline. Require its sidecar digest
# as well as a valid PBF structure and the requested coverage envelope.
validate_cache() {
  [[ -s "$CACHE_FILE" && -s "$CACHE_FILE.sha256" ]] || return 1
  local expected actual
  expected="$(awk 'NF {print tolower($1); exit}' "$CACHE_FILE.sha256")"
  [[ "$expected" =~ ^[[:xdigit:]]{64}$ ]] || return 1
  actual="$(sha256sum "$CACHE_FILE" | awk '{print tolower($1)}')"
  [[ "$actual" == "$expected" ]] && validate_pbf "$CACHE_FILE"
}
if validate_cache; then
  cp --reflink=auto "$CACHE_FILE" "$OUTPUT.tmp"
  mv "$OUTPUT.tmp" "$OUTPUT"
  echo "Using verified regional PBF cache: $CACHE_FILE"
  exit 0
fi

work="$(mktemp -d "$CACHE_DIR/.regional-pbf.XXXXXX")"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT
download="$work/source.osm.pbf"
checksum="$work/source.checksum"
candidate="$work/candidate.osm.pbf"

if [[ -n "$SOURCE_PBF" ]]; then
  [[ -s "$SOURCE_PBF" ]] || { echo "Local source PBF is missing: $SOURCE_PBF" >&2; exit 1; }
  cp --reflink=auto "$SOURCE_PBF" "$download"
else
  echo "Downloading regional OSM PBF with resumable retries: $URL"
  curl --fail --location --silent --show-error \
    --connect-timeout 20 --max-time 3600 --retry 8 --retry-all-errors \
    --retry-delay 5 --retry-max-time 600 --continue-at - \
    "$URL" -o "$download"
  if [[ -n "$CHECKSUM_URL" ]]; then
    [[ "$CHECKSUM_URL" == https://* ]] || { echo "Checksum URL must use HTTPS" >&2; exit 2; }
    curl --fail --location --silent --show-error \
      --connect-timeout 20 --max-time 120 --retry 5 --retry-all-errors \
      "$CHECKSUM_URL" -o "$checksum"
  fi
  verify_digest "$download" "$checksum" || {
    echo "Regional PBF checksum verification failed; refusing the download." >&2
    exit 1
  }
fi

osmium fileinfo -e "$download" >/dev/null
if [[ -n "$POLYGON" ]]; then
  osmium extract --polygon "$POLYGON" --strategy=smart --set-bounds \
    --overwrite --output "$candidate" "$download"
else
  cp --reflink=auto "$download" "$candidate"
fi
validate_pbf "$candidate" || {
  echo "Final regional PBF failed structural or requested-coverage validation." >&2
  exit 1
}

# Atomic cache and output replacement: incomplete files are never exposed.
cp --reflink=auto "$candidate" "$CACHE_FILE.tmp"
mv "$CACHE_FILE.tmp" "$CACHE_FILE"
cp --reflink=auto "$candidate" "$OUTPUT.tmp"
mv "$OUTPUT.tmp" "$OUTPUT"
sha256sum "$CACHE_FILE" > "$CACHE_FILE.sha256"
echo "Verified regional PBF ready: $OUTPUT"
echo "SHA-256: $(sha256sum "$OUTPUT" | awk '{print $1}')"
