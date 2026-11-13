#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <valhalla-build-directory>" >&2
  exit 2
fi

VALHALLA_DIR="$(realpath "$1")"
CONFIG="$VALHALLA_DIR/valhalla.json"
TILES="$VALHALLA_DIR/tiles.tar"
LOG="$VALHALLA_DIR/offline-route-test.log"
[[ -s "$CONFIG" && -s "$TILES" ]] || {
  echo "Valhalla config or graph archive is missing." >&2
  exit 2
}
command -v valhalla_service >/dev/null 2>&1 || {
  echo "valhalla_service is not installed." >&2
  exit 2
}

valhalla_service "$CONFIG" 1 >"$LOG" 2>&1 &
SERVICE_PID=$!
cleanup() {
  kill "$SERVICE_PID" >/dev/null 2>&1 || true
  wait "$SERVICE_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

python3 - <<'PY'
import json
import time
import urllib.error
import urllib.request

endpoint = "http://127.0.0.1:8002/route"
payload = {
    "locations": [
        {"lat": 53.4073, "lon": -2.9775, "type": "break"},
        {"lat": 53.4016, "lon": -2.9915, "type": "break"},
    ],
    "costing": "auto",
    "units": "kilometers",
    "shape_format": "polyline6",
    "directions_options": {"units": "kilometers"},
}
body = json.dumps(payload).encode("utf-8")
last_error = None
for attempt in range(60):
    try:
        request = urllib.request.Request(
            endpoint, data=body, headers={"Content-Type": "application/json"}, method="POST"
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.loads(response.read().decode("utf-8"))
        trip = result.get("trip") or {}
        if trip.get("status") != 0:
            raise RuntimeError(
                f"Valhalla returned status {trip.get('status')}: {trip.get('status_message')}"
            )
        legs = trip.get("legs") or []
        if not legs or not legs[0].get("shape") or not legs[0].get("maneuvers"):
            raise RuntimeError("Offline route response has no geometry or turn-by-turn maneuvers.")
        summary = trip.get("summary") or {}
        if not isinstance(summary.get("length"), (int, float)) or summary["length"] <= 0:
            raise RuntimeError("Offline route response has an invalid route length.")
        print(
            "Offline Valhalla route passed: "
            f"{summary['length']:.2f} km, {summary.get('time', 0):.0f} seconds, "
            f"{len(legs[0]['maneuvers'])} maneuvers."
        )
        break
    except (OSError, urllib.error.URLError, TimeoutError) as error:
        last_error = error
        time.sleep(1)
else:
    raise SystemExit(f"Local Valhalla did not become ready: {last_error}")
PY
