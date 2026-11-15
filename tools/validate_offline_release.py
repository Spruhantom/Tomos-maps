#!/usr/bin/env python3
"""Fail-closed validation for a TomOS offline region before catalogue signing.

The tool verifies package bytes and requires a human-reviewed redistribution
record. It never signs, uploads, or publishes assets.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

REQUIRED_ROLES = {"MAP_DATA", "ROUTING_GRAPH", "ROUTING_SUPPORT", "SEARCH_INDEX"}
MAX_MANIFEST_BYTES = 4 * 1024 * 1024
MAX_ARCHIVE_BYTES = 20 * 1024 * 1024 * 1024


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_evidence(path: Path) -> None:
    evidence = json.loads(path.read_text(encoding="utf-8"))
    if evidence.get("redistributionPermitted") is not True:
        raise ValueError("Licence evidence does not explicitly permit redistribution.")
    if not str(evidence.get("reviewedBy", "")).strip():
        raise ValueError("Licence evidence must identify the human reviewer.")
    if not re.fullmatch(r"\\d{4}-\\d{2}-\\d{2}", str(evidence.get("reviewedAt", ""))):
        raise ValueError("Licence evidence reviewedAt must be YYYY-MM-DD.")
    sources = evidence.get("sources")
    if not isinstance(sources, list) or not sources:
        raise ValueError("Licence evidence must list every data source.")
    for source in sources:
        if not isinstance(source, dict) or not all(
            str(source.get(field, "")).strip() for field in ("name", "licence", "evidence")
        ):
            raise ValueError("Each source needs name, licence and evidence fields.")
    attribution = evidence.get("requiredAttribution")
    if not isinstance(attribution, str) or not attribution.strip():
        raise ValueError("Licence evidence must include the required attribution text.")


def validate_package(manifest_path: Path, archive_path: Path) -> dict:
    raw = manifest_path.read_bytes()
    if not raw or len(raw) > MAX_MANIFEST_BYTES:
        raise ValueError("Manifest is empty or exceeds the 4 MiB limit.")
    manifest = json.loads(raw)
    if manifest.get("formatVersion") != 1:
        raise ValueError("Unsupported package manifest format.")
    if not str(manifest.get("id", "")).strip() or not str(manifest.get("packageVersion", "")).strip():
        raise ValueError("Manifest requires package id and version.")
    attribution = manifest.get("attribution")
    if not isinstance(attribution, str) or not attribution.strip():
        raise ValueError("Package attribution is missing.")
    coverage = manifest.get("coverage")
    if not isinstance(coverage, dict):
        raise ValueError("Package coverage is missing.")
    west, south, east, north = (coverage.get(k) for k in ("west", "south", "east", "north"))
    if not all(isinstance(v, (int, float)) for v in (west, south, east, north)):
        raise ValueError("Coverage coordinates must be numeric.")
    if not (-180 <= west < east <= 180 and -90 <= south < north <= 90):
        raise ValueError("Coverage bounds are invalid.")
    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("Manifest has no assets.")
    roles = {entry.get("role") for entry in files if isinstance(entry, dict)}
    missing_roles = REQUIRED_ROLES - roles
    if missing_roles:
        raise ValueError("Missing required data roles: " + ", ".join(sorted(missing_roles)))
    expected_names: set[str] = set()
    total = 0
    for entry in files:
        if not isinstance(entry, dict):
            raise ValueError("Manifest asset entry must be an object.")
        name = entry.get("path")
        safe = PurePosixPath(str(name))
        if safe.is_absolute() or not safe.parts or any(p in ("", ".", "..") for p in safe.parts):
            raise ValueError(f"Unsafe asset path: {name!r}")
        if str(name) in expected_names:
            raise ValueError(f"Duplicate asset path: {name}")
        expected_names.add(str(name))
        size = entry.get("sizeBytes")
        digest = entry.get("sha256")
        if not isinstance(size, int) or size <= 0 or not re.fullmatch(r"[a-fA-F0-9]{64}", str(digest)):
            raise ValueError(f"Invalid size or SHA-256 for {name}")
        total += size
    if total != manifest.get("totalSizeBytes") or total > MAX_ARCHIVE_BYTES:
        raise ValueError("Manifest total size does not match its assets or exceeds 20 GiB.")
    if not archive_path.is_file() or archive_path.stat().st_size <= 0:
        raise ValueError("Package archive is missing or empty.")
    with zipfile.ZipFile(archive_path) as archive:
        infos = archive.infolist()
        names = [i.filename for i in infos]
        if len(names) != len(set(names)):
            raise ValueError("Archive contains duplicate paths.")
        if set(names) != expected_names:
            raise ValueError("Archive members do not exactly match the manifest.")
        for info in infos:
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise ValueError(f"Symbolic links are forbidden: {info.filename}")
            data = archive.read(info)
            entry = next(item for item in files if item["path"] == info.filename)
            if len(data) != entry["sizeBytes"] or sha256_bytes(data) != entry["sha256"].lower():
                raise ValueError(f"Size or SHA-256 mismatch: {info.filename}")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--licence-evidence", required=True, type=Path)
    args = parser.parse_args()
    try:
        validate_evidence(args.licence_evidence)
        manifest = validate_package(args.manifest, args.archive)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"OFFLINE RELEASE BLOCKED: {error}", file=sys.stderr)
        return 1
    print(f"OFFLINE RELEASE PREFLIGHT PASSED: {manifest['id']} {manifest['packageVersion']}")
    print("Human licence review and all archive member checks passed. Signing/publication still require separate approval.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
