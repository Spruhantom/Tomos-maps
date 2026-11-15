#!/usr/bin/env bash
set -Eeuo pipefail

# End-to-end Liverpool build: regional data -> independent layers -> licence
# and checksum preflight -> signed catalogue. Hosting remains a separate step.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_ROOT="$(realpath -m "${BUILD_ROOT:-$ROOT/.liverpool-offline-release}")"
COMBINED_DIR="$BUILD_ROOT/combined"
LAYER_ROOT="$BUILD_ROOT/layers"
OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR:-$ROOT/dist/liverpool-offline-layers}")"
LICENCE_EVIDENCE="${OFFLINE_LICENCE_EVIDENCE:-}"
PRIVATE_KEY="${PUBLISHER_PRIVATE_KEY:-}"
BASE_URL="${LAYER_PACKAGE_BASE_URL:-}"
VERSION="${PACKAGE_VERSION:-$(date -u +%Y.%m.%d)}"
[[ "$VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]*$ ]] || {
  echo "PACKAGE_VERSION contains unsupported characters." >&2
  exit 2
}
LAYER_DIR="$LAYER_ROOT/$VERSION"

[[ -n "$LICENCE_EVIDENCE" && -s "$LICENCE_EVIDENCE" ]] || {
  echo "Set OFFLINE_LICENCE_EVIDENCE to a reviewed redistribution evidence JSON file." >&2
  exit 2
}
[[ -n "$PRIVATE_KEY" && -s "$PRIVATE_KEY" ]] || {
  echo "Set PUBLISHER_PRIVATE_KEY to the publisher's Ed25519 PEM key (never commit it)." >&2
  exit 2
}
[[ "$BASE_URL" == https://* ]] || {
  echo "Set LAYER_PACKAGE_BASE_URL to the HTTPS directory where assets will be hosted." >&2
  exit 2
}
python3 - "$LICENCE_EVIDENCE" <<'PY'
import json, sys
from pathlib import Path
data=json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if data.get("redistributionPermitted") is not True:
    raise SystemExit("Licence evidence does not explicitly authorise redistribution.")
if not data.get("reviewedBy") or not data.get("requiredAttribution") or not data.get("sources"):
    raise SystemExit("Licence evidence is missing reviewer, sources or required attribution.")
PY

PINNED_PUBLIC_KEY="$(python3 - "$ROOT/composeApp/src/commonMain/kotlin/uk/co/tomosmaps/offline/RegionalPackageBuildConfig.kt" <<'PY'
import re, sys
from pathlib import Path
source=Path(sys.argv[1]).read_text(encoding="utf-8")
match=re.search(r'PINNED_ED25519_PUBLIC_KEY_BASE64 = "([^"]+)"', source)
if not match:
    raise SystemExit("The app's pinned Ed25519 public key is missing.")
print(match.group(1))
PY
)"
[[ -n "$PINNED_PUBLIC_KEY" ]] || { echo "The app's pinned public key is not configured." >&2; exit 2; }

mkdir -p "$COMBINED_DIR" "$LAYER_ROOT" "$OUTPUT_DIR"
rm -rf -- "$LAYER_DIR"
mkdir -p "$LAYER_DIR"

echo "[1/4] Build the complete Liverpool regional dataset"
DATA_DIR="$BUILD_ROOT/source" OUTPUT_DIR="$COMBINED_DIR" PACKAGE_VERSION="$VERSION" \
  bash "$ROOT/tools/build-liverpool-region.sh"

MANIFEST="$COMBINED_DIR/liverpool-city-region-$VERSION.manifest.json"
ARCHIVE="$COMBINED_DIR/liverpool-city-region-$VERSION.zip"
test -s "$MANIFEST" && test -s "$ARCHIVE"

echo "[2/4] Split the verified regional package into map, routing and search downloads"
python3 "$ROOT/tools/split-regional-package-layers.py" \
  --manifest "$MANIFEST" --archive "$ARCHIVE" --output-dir "$LAYER_DIR"

echo "[3/4] Verify each independent package and its redistribution evidence"
METADATA=()
for layer in MAP ROUTING SEARCH; do
  file="$(find "$LAYER_DIR" -maxdepth 1 -type f -name "*-$layer-*.metadata.json" -print -quit)"
  # Package filenames use lowercase layer names.
  if [[ -z "$file" ]]; then
    file="$(find "$LAYER_DIR" -maxdepth 1 -type f -name "*-${layer,,}-*.metadata.json" -print -quit)"
  fi
  [[ -n "$file" && -s "$file" ]] || { echo "Missing $layer layer metadata." >&2; exit 1; }
  metadata="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["manifestFile"])' "$file")"
  archive_name="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archiveFile"])' "$file")"
  python3 "$ROOT/tools/validate_offline_release.py" \
    --manifest "$LAYER_DIR/$metadata" --archive "$LAYER_DIR/$archive_name" \
    --licence-evidence "$LICENCE_EVIDENCE"
  METADATA+=(--package-metadata "$file")
done

echo "[4/4] Create and Ed25519-sign the independent layer catalogue"
python3 "$ROOT/tools/build-signed-layer-catalogue.py" \
  "${METADATA[@]}" \
  --base-url "$BASE_URL" \
  --output "$OUTPUT_DIR/offline-layers.json" \
  --signature-output "$OUTPUT_DIR/offline-layers.json.sig" \
  --licence-evidence "$LICENCE_EVIDENCE" \
  --private-key "$PRIVATE_KEY" \
  --expected-public-key "$PINNED_PUBLIC_KEY"

echo "Signed catalogue and independently verified package assets are in:"
echo "  $LAYER_DIR"
echo "  $OUTPUT_DIR/offline-layers.json"
echo "  $OUTPUT_DIR/offline-layers.json.sig"
echo "No upload or app feature-flag change was performed. Upload the immutable assets to $BASE_URL,"
echo "then configure the matching pinned public key and catalogue URLs in the app."
