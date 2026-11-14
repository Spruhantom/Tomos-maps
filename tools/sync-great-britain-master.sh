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

for command in aws curl osmium pyosmium-up-to-date sha256sum md5sum python3; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Missing required command: $command" >&2
    exit 2
  }
done

mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
MASTER="$WORK_DIR/$MASTER_NAME"
CANDIDATE="$WORK_DIR/great-britain.candidate.osm.pbf"
REMOTE_SHA="$WORK_DIR/$MASTER_NAME.remote.sha256"

echo "[1/6] Restore the persistent master snapshot through its atomic metadata pointer"
METADATA="$WORK_DIR/master-metadata.json"
if [[ ! -s "$MASTER" ]]; then
  if aws s3 cp "s3://$BUCKET/$PREFIX/current/master-metadata.json" "$METADATA" --only-show-errors; then
    SNAPSHOT_URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["snapshot"])' "$METADATA")"
    EXPECTED_SHA="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["sha256"])' "$METADATA")"
    aws s3 cp "$SNAPSHOT_URL" "$MASTER" --only-show-errors
    ACTUAL_SHA="$(sha256sum "$MASTER" | awk '{print $1}')"
    [[ "$EXPECTED_SHA" == "$ACTUAL_SHA" ]] || {
      echo "Restored master failed SHA-256 verification; refusing to use it." >&2
      rm -f "$MASTER"
      exit 1
    }
  else
    echo "No current master pointer found; a first-time source download is required."
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
  echo "[2/6] Apply incremental Geofabrik replication diffs to a candidate copy"
  cp --reflink=auto "$MASTER" "$CANDIDATE"
  # Geofabrik PBFs include their replication URL and sequence. Exit 1 means
  # the configured batch-size cap was reached and more diffs remain; continue
  # until pyosmium reports exit 0 (fully current).
  attempts=0
  while true; do
    set +e
    pyosmium-up-to-date --size "${OSM_DIFF_MAX_MB:-1024}" "$CANDIDATE"
    update_status=$?
    set -e
    if [[ "$update_status" -eq 0 ]]; then
      break
    elif [[ "$update_status" -eq 1 ]]; then
      attempts=$((attempts + 1))
      [[ "$attempts" -lt 25 ]] || {
        echo "Reached the incremental-update batch safety limit; retry on the next run." >&2
        exit 1
      }
    else
      echo "Incremental update failed (exit $update_status); original master remains untouched." >&2
      exit "$update_status"
    fi
  done
fi

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
SNAPSHOT_KEY="$PREFIX/snapshots/$TIMESTAMP/$MASTER_NAME"
SNAPSHOT_URL="s3://$BUCKET/$SNAPSHOT_KEY"
aws s3 cp "$CANDIDATE" "$SNAPSHOT_URL" --only-show-errors

echo "[5/6] Atomically publish the current metadata pointer"
cat > "$METADATA" <<EOF
{
  "dataset": "great-britain",
  "source": "$REGION_URL",
  "provider": "Geofabrik",
  "format": "osm.pbf",
  "sha256": "$sha",
  "sizeBytes": $size,
  "updatedAt": "$TIMESTAMP",
  "snapshot": "$SNAPSHOT_URL",
  "updateMethod": "pyosmium-up-to-date using the replication state embedded in the PBF"
}
EOF
# The pointer is a single small object uploaded only after the immutable PBF
# snapshot is complete and verified. Readers therefore never see a half pair.
aws s3 cp "$METADATA" "s3://$BUCKET/$PREFIX/current/master-metadata.json" --only-show-errors
mv -f "$CANDIDATE" "$MASTER"
cp "$METADATA" "$WORK_DIR/master-metadata.json"
rm -f "$CANDIDATE.md5"

echo "[6/6] Verify the local master and report its source metadata"
osmium fileinfo -e "$MASTER" >/dev/null
echo "Great Britain master is current: $size bytes, SHA-256 $sha"
