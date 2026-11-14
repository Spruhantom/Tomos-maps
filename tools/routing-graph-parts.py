#!/usr/bin/env python3
"""Validate and reassemble a chunked TomOS Valhalla routing graph archive."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
from pathlib import Path

MAX_PART_BYTES = 1_900_000_000
PART_NAME = re.compile(r"^tiles\\.tar\\.part-(\\d{3,})$")


def digest_file(path: Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()


def load_manifest(path: Path) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Cannot read manifest {path}: {exc}") from exc
    if data.get("schemaVersion") != 1 or data.get("archiveName") != "tiles.tar":
        raise ValueError("Unsupported manifest schema or archive name.")
    if data.get("partSizeLimitBytes") != MAX_PART_BYTES:
        raise ValueError("Manifest part-size limit is not the supported 1,900,000,000 bytes.")
    if not isinstance(data.get("parts"), list) or not data["parts"]:
        raise ValueError("Manifest contains no graph parts.")
    if not isinstance(data.get("archiveSizeBytes"), int) or data["archiveSizeBytes"] <= 0:
        raise ValueError("Invalid full archive size.")
    if not re.fullmatch(r"[a-f0-9]{64}", str(data.get("archiveSha256", ""))):
        raise ValueError("Invalid full archive SHA-256.")
    return data


def validate(manifest_path: Path) -> tuple[dict, list[Path]]:
    manifest = load_manifest(manifest_path)
    root = manifest_path.parent
    parts = manifest["parts"]
    expected_names = [f"tiles.tar.part-{i:03d}" for i in range(len(parts))]
    names = [item.get("file") if isinstance(item, dict) else None for item in parts]
    if names != expected_names:
        raise ValueError("Part list is missing, duplicated, or not in contiguous numeric order.")
    combined = hashlib.sha256()
    total = 0
    paths = []
    for index, item in enumerate(parts):
        path = root / item["file"]
        if path.parent != root or not path.is_file():
            raise ValueError(f"Missing graph part: {item['file']}")
        size, sha = digest_file(path)
        if size <= 0 or size > MAX_PART_BYTES:
            raise ValueError(f"Part {item['file']} has invalid size {size}.")
        if item.get("index") != index or item.get("sizeBytes") != size:
            raise ValueError(f"Part {item['file']} index or size does not match manifest.")
        if item.get("sha256") != sha:
            raise ValueError(f"SHA-256 mismatch for {item['file']}.")
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                combined.update(block)
        total += size
        paths.append(path)
    if total != manifest["archiveSizeBytes"]:
        raise ValueError(f"Archive size mismatch: expected {manifest['archiveSizeBytes']}, got {total}.")
    if combined.hexdigest() != manifest["archiveSha256"]:
        raise ValueError("Reassembled archive SHA-256 does not match manifest.")
    return manifest, paths


def assemble(manifest_path: Path, output: Path) -> None:
    _, parts = validate(manifest_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{output.name}.", dir=output.parent)
    try:
        with os.fdopen(fd, "wb") as destination:
            for part in parts:
                with part.open("rb") as source:
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        destination.write(block)
            destination.flush()
            os.fsync(destination.fileno())
        os.replace(temporary, output)
    except Exception:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise
    print(f"Reassembled and verified {output} ({output.stat().st_size} bytes).")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Path to england-routing.manifest.json")
    parser.add_argument("--assemble", type=Path, metavar="OUTPUT", help="Validate then reassemble the archive")
    args = parser.parse_args()
    try:
        manifest, parts = validate(args.manifest)
        if args.assemble:
            assemble(args.manifest, args.assemble)
        else:
            print(f"Validated {len(parts)} parts; archive is {manifest['archiveSizeBytes']} bytes; SHA-256 {manifest['archiveSha256']}.")
    except (OSError, ValueError) as exc:
        print(f"routing graph validation failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
