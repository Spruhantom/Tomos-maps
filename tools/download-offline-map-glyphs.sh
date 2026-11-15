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

LICENSE_FILE="$OUTPUT/OFL.txt"
curl --fail --location --retry 4 --retry-delay 2 \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/OFL.txt" -o "$LICENSE_FILE"
grep -Fq "SIL OPEN FONT LICENSE Version 1.1" "$LICENSE_FILE"
grep -Fq "Copyright 2022 The Noto Project Authors" "$LICENSE_FILE"

echo "Offline Noto Sans Regular glyphs ready: $DESTINATION"
echo "Noto Sans OFL 1.1 licence and copyright notice included: $LICENSE_FILE"
echo "Glyph source: OpenMapTiles fonts; original Noto Sans font licence: SIL OFL 1.1."
