#!/usr/bin/env bash
set -Eeuo pipefail

REPO="Spruhantom/Tomos-maps"
ARTIFACT_NAME="tomos-maps-linux-x86_64"
API="https://api.github.com/repos/${REPO}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

for command in curl python3 unzip find; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

ARCH="$(uname -m)"
if [[ "$ARCH" != "x86_64" ]]; then
  echo "The current Linux CI package supports x86_64 only (detected: $ARCH)." >&2
  exit 1
fi

echo "Finding the latest successful TomOS Maps Linux build..."
RUNS_JSON="$TMP_DIR/runs.json"
curl -fsSL "$API/actions/runs?branch=main&status=completed&per_page=30" -o "$RUNS_JSON"
RUN_ID="$(python3 - "$RUNS_JSON" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
runs = [r for r in data.get("workflow_runs", [])
        if r.get("name") == "Build and test"
        and r.get("conclusion") == "success"
        and r.get("head_branch") == "main"]
print(runs[0]["id"] if runs else "")
PY
)"
if [[ -z "$RUN_ID" ]]; then
  echo "No successful main-branch build is available yet." >&2
  exit 1
fi

ARTIFACTS_JSON="$TMP_DIR/artifacts.json"
curl -fsSL "$API/actions/runs/$RUN_ID/artifacts" -o "$ARTIFACTS_JSON"
ARTIFACT_ID="$(python3 - "$ARTIFACTS_JSON" "$ARTIFACT_NAME" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
matches = [a for a in data.get("artifacts", [])
           if a.get("name") == sys.argv[2] and not a.get("expired")]
print(matches[0]["id"] if matches else "")
PY
)"
if [[ -z "$ARTIFACT_ID" ]]; then
  echo "The latest successful build has no Linux app image artifact yet. Re-run CI after the installer packaging change." >&2
  exit 1
fi

echo "Downloading TomOS Maps..."
curl -fsSL -H "Accept: application/vnd.github+json" \
  "$API/actions/artifacts/$ARTIFACT_ID/zip" -o "$TMP_DIR/app.zip"
unzip -q "$TMP_DIR/app.zip" -d "$TMP_DIR/unpacked"

LAUNCHER="$(find "$TMP_DIR/unpacked" -type f -path '*/bin/*' -perm -u+x -print -quit)"
if [[ -z "$LAUNCHER" ]]; then
  echo "Could not locate the executable in the downloaded Linux app image." >&2
  exit 1
fi
APP_IMAGE_DIR="$(dirname "$(dirname "$LAUNCHER")")"

INSTALL_ROOT="${XDG_DATA_HOME:-$HOME/.local/share}/tomos-maps"
BIN_DIR="${HOME}/.local/bin"
mkdir -p "$INSTALL_ROOT" "$BIN_DIR"
rm -rf "$INSTALL_ROOT/current"
mkdir -p "$INSTALL_ROOT/current"
cp -a "$APP_IMAGE_DIR/." "$INSTALL_ROOT/current/"
INSTALLED_LAUNCHER="$INSTALL_ROOT/current/bin/$(basename "$LAUNCHER")"
chmod +x "$INSTALLED_LAUNCHER"
ln -sfn "$INSTALLED_LAUNCHER" "$BIN_DIR/tomos-maps"

DESKTOP_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$DESKTOP_DIR"
cat > "$DESKTOP_DIR/tomos-maps.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=TomOS Maps
Comment=Offline-first navigation for Linux
Exec=$INSTALLED_LAUNCHER
Terminal=false
Categories=Utility;Maps;
EOF

echo "TomOS Maps installed."
echo "Launch from your app menu or run: $BIN_DIR/tomos-maps"
if [[ ":${PATH}:" != *":$BIN_DIR:"* ]]; then
  echo "Add $BIN_DIR to PATH to run 'tomos-maps' directly."
fi
