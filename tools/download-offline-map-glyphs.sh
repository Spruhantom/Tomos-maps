#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT="${1:-.regional-build/liverpool/glyphs}"
FONTSTACK="Noto Sans Regular"
BASE_URL="https://fonts.openmaptiles.org/Noto%20Sans%20Regular"
DESTINATION="$OUTPUT/$FONTSTACK"
mkdir -p "$DESTINATION"

for range in 0-255 256-511; do
  file="$DESTINATION/$range.pbf"
  curl --fail --location --retry 4 --retry-delay 2 \
    "$BASE_URL/$range.pbf" -o "$file"
  if [[ ! -s "$file" ]]; then
    echo "Missing offline glyph range: $range" >&2
    exit 1
  fi
done

echo "Offline Noto Sans Regular glyphs ready: $DESTINATION"
echo "Source: OpenMapTiles open font glyphs; font licence is OFL/Apache as noted by the upstream font project."
