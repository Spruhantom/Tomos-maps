#!/usr/bin/env bash
set -Eeuo pipefail

# Persistent Great Britain OSM source dataset.
# Storage is an S3-compatible bucket; local work happens on a disk with
# enough free space for the ~2.2 GB PBF, a candidate copy and update diffs.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${OSM_MASTER_WORK_DIR:-$ROOT/.osm-master}"
BUCKET="${OSM_MASTER_S3_BUCKET:?Set OSM_MASTER_S3_BUCKET to a persistent S3 bucket}"
PREFIX="${OSM_MASTER_S3_PREFIX:-tomos-maps/osm/great-britain}"
REGION_URL="${GREAT_BRITAIN_PBF_URL:-https://download.geofabrik.de/europe/great-britain-latest.osm.pbf}"
MASTER_NAME="great-britain.osm.pbf"
CURRENT_KEY="$PREFIX/current/$MASTER_NAME"
CURRENT_SHA_KEY="$PREFIX/current/$MASTER_NAME.sha256"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

for command in aws curl osmium pyosmium-up-to-date sha256sum; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Missing required command: $command" >&2
    exit 2
  }
done

mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
MASTER="$WORK_DIR/$MASTER_NAME"
CANDIDATE="$WORK_DIR/$MASTER_NAME.candidate"
REMOTE_SHA="$WORK_DIR/$MASTER_NAME.remote.sha256"

echo "[1/6] Restore the persistent master snapshot from object storage"
if [[ ! -s "$MASTER" ]]; then
  aws s3 cp "s3://$BUCKET/$CURRENT_KEY" "$MASTER" || true
  if [[ -s "$MASTER" ]]; then
    aws s3 cp "s3://$BUCKET/$CURRENT_SHA_KEY" "$REMOTE_SHA"
    expected="$(awk 'NF {print $1; exit}' "$REMOTE_SHA")"
    actual="$(sha256sum "$MASTER" | awk '{print $1}')"
    [[ "$expected" == "$actual" ]] || {
      echo "Restored master failed SHA-256 verification; refusing to use it." >&2
      rm -f "$MASTER"
      exit 1
    }
  fi
fi

if [[ ! -s "$MASTER" ]]; then
  echo "[2/6] No persistent master exists; download the Great Britain PBF once"
  rm -f "$CANDIDATE" "$CANDIDATE.md5"
  curl --fail --location --show-error --retry 6 --retry-all-errors \
    --connect-timeout 30 --max-time 7200 "$REGION_URL" -o "$CANDIDATE"
  curl --fail --location --show-error --retry 6 --retry-all-errors \
    --connect-timeout 30 --max-time 300 "${REGION_URL}.md5" -o "$CANDIDATE.md5"
  expected="$(awk 'NF {print $1; exit}' "$CANDIDATE.md5")"
  actual="$(md5sum "$CANDIDATE" | awk '{print $1}')"
  [[ "$expected" == "$actual" ]] || {
    echo "Geofabrik MD5 verification failed." >&2
    exit 1
  }
else
  echo "[2/6] Update the restored master using its embedded Geofabrik replication state"
  cp --reflink=auto "$MASTER" "$CANDIDATE"
  # Geofabrik publishes daily extract diffs and the PBF records the matching
  # replication URL/sequence. pyosmium applies only missing diffs.
  set +e
  pyosmium-up-to-date --size "${OSM_DIFF_MAX_MB:-10240}" "$CANDIDATE"
  update_status=$?
  set -e
  if [[ "$update_status" -ne 0 ]]; then
    echo "Incremental update did not complete cleanly (exit $update_status); original master remains untouched." >&2
    exit "$update_status"
  fi
fi

echo "[3/6] Validate candidate PBF and compute a strong content checksum"
test -s "$CANDIDATE"
osmium fileinfo -e "$CANDIDATE" >/dev/null
sha256sum "$CANDIDATE" > "$CANDIDATE.sha256"
sha="$(awk '{print $1}' "$CANDIDATE.sha256")"
size="$(stat -c '%s' "$CANDIDATE")"

echo "[4/6] Retain an immutable versioned snapshot"
aws s3 cp "$CANDIDATE" "s3://$BUCKET/$PREFIX/snapshots/$TIMESTAMP/$MASTER_NAME" \
  --only-show-errors
aws s3 cp "$CANDIDATE.sha256" "s3://$BUCKET/$PREFIX/snapshots/$TIMESTAMP/$MASTER_NAME.sha256" \
  --only-show-errors

echo "[5/6] Atomically promote the verified candidate as current"
# S3 objects are immutable snapshots plus a small current pointer pair. Upload
# the checksum first; consumers verify both values before accepting the PBF.
aws s3 cp "$CANDIDATE.sha256" "s3://$BUCKET/$CURRENT_SHA_KEY" --only-show-errors
aws s3 cp "$CANDIDATE" "s3://$BUCKET/$CURRENT_KEY" --only-show-errors
mv -f "$CANDIDATE" "$MASTER"
mv -f "$CANDIDATE.sha256" "$MASTER.sha256"
rm -f "$CANDIDATE.md5"

echo "[6/6] Write machine-readable source metadata"
cat > "$WORK_DIR/master-metadata.json" <<EOF
{
  "dataset": "great-britain",
  "source": "$REGION_URL",
  "provider": "Geofabrik",
  "format": "osm.pbf",
  "sha256": "$sha",
  "sizeBytes": $size,
  "updatedAt": "$TIMESTAMP",
  "storage": "s3://$BUCKET/$CURRENT_KEY",
  "snapshot": "s3://$BUCKET/$PREFIX/snapshots/$TIMESTAMP/$MASTER_NAME",
  "updateMethod": "pyosmium-up-to-date using the replication state embedded in the PBF"
}
EOF
aws s3 cp "$WORK_DIR/master-metadata.json" "s3://$BUCKET/$PREFIX/current/master-metadata.json" --only-show-errors
echo "Great Britain master is current: $size bytes, SHA-256 $sha"
