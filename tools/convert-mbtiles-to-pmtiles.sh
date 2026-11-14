#!/usr/bin/env bash
set -Eeuo pipefail

# Experimental distribution artifact only. MBTiles remains the runtime format
# until Android AND Linux offline renderer tests pass.
INPUT="${1:?Usage: convert-mbtiles-to-pmtiles.sh <input.mbtiles> [output.pmtiles]}"
OUTPUT="${2:-${INPUT%.mbtiles}.pmtiles}"
command -v pmtiles >/dev/null 2>&1 || {
  echo "Install the Protomaps PMTiles CLI before running this converter." >&2
  exit 2
}
[[ -s "$INPUT" ]] || { echo "Input MBTiles file not found: $INPUT" >&2; exit 1; }
TEMP="$OUTPUT.tmp"
trap 'rm -f "$TEMP"' EXIT
rm -f "$TEMP"
pmtiles convert "$INPUT" "$TEMP"
pmtiles verify "$TEMP"
test -s "$TEMP"
mv -f "$TEMP" "$OUTPUT"
trap - EXIT
echo "Verified experimental PMTiles archive: $OUTPUT"
echo "The original MBTiles file has not been changed or removed."
