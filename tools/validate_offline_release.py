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
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(evidence.get("reviewedAt", ""))):
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


def validate_package(manifest_path: Path, archive_path: Path, licence_evidence_path: Path) -> dict:
    raw = manifest_path.read_bytes()
    if not raw or len(raw) > MAX_MANIFEST_BYTES:
        raise ValueError("Manifest is empty or exceeds the 4 MiB limit.")
    manifest = json.loads(raw)
    if manifest.get("formatVersion") != 1:
        raise ValueError("Unsupported package manifest format.")
    if not str(manifest.get("id", "")).strip() or not str(manifest.get("packageVersion", manifest.get("version", ""))).strip():
        raise ValueError("Manifest requires package id and version.")
    attribution = manifest.get("attribution")
    if not isinstance(attribution, str) or not attribution.strip():
        raise ValueError("Package attribution is missing.")
    attribution_lower = attribution.casefold()
    evidence = json.loads(licence_evidence_path.read_text(encoding="utf-8"))
    source_names = " ".join(str(source.get("name", "")) for source in evidence.get("sources", [])).casefold()
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
    if "layer" in manifest:
        layer = manifest.get("layer")
        paths = [str(entry.get("path", "")).lower() for entry in files if isinstance(entry, dict)]
        if layer == "MAP" and not any(path.endswith(".mbtiles") for path in paths):
            raise ValueError("MAP layer is missing its MBTiles data.")
        if layer == "ROUTING":
            required = ("tiles.tar", "admins.sqlite", "timezones.sqlite", "valhalla.json")
            missing = [name for name in required if not any(path.endswith(name) for path in paths)]
            if missing:
                raise ValueError("ROUTING layer is missing: " + ", ".join(missing))
        if layer == "SEARCH" and len([path for path in paths if path.endswith(".json")]) != 1:
            raise ValueError("SEARCH layer must contain exactly one JSON search index.")
        if layer not in {"MAP", "ROUTING", "SEARCH"}:
            raise ValueError("Unknown independent layer type.")
    else:
        roles = {entry.get("role") for entry in files if isinstance(entry, dict)}
        missing_roles = REQUIRED_ROLES - roles
        if missing_roles:
            raise ValueError("Missing required data roles: " + ", ".join(sorted(missing_roles)))
    expected_names: set[str] = set()
    total = 0
    glyph_paths = [str(entry.get("path", "")).lower() for entry in files if isinstance(entry, dict)]
    contains_map = manifest.get("layer") == "MAP" or any(
        entry.get("role") == "MAP_DATA" for entry in files if isinstance(entry, dict)
    )
    contains_routing = manifest.get("layer") == "ROUTING" or any(
        entry.get("role") == "ROUTING_GRAPH" for entry in files if isinstance(entry, dict)
    )
    if (contains_map or contains_routing) and (
        "openstreetmap contributors" not in attribution_lower or
        "https://opendatacommons.org/licenses/odbl/1-0/" not in attribution_lower or
        "https://www.openstreetmap.org/copyright" not in attribution_lower
    ):
        raise ValueError("Map/routing attribution must include OSM credit and the ODbL 1.0 and OSM copyright URIs.")
    if any("glyph" in name for name in glyph_paths):
        if not any(name.endswith("/ofl.txt") or name == "ofl.txt" for name in glyph_paths):
            raise ValueError("Noto glyph assets must include the upstream OFL.txt licence notice.")

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
        search_records = None
        for info in infos:
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise ValueError(f"Symbolic links are forbidden: {info.filename}")
            data = archive.read(info)
            entry = next(item for item in files if item["path"] == info.filename)
            if len(data) != entry["sizeBytes"] or sha256_bytes(data) != entry["sha256"].lower():
                raise ValueError(f"Size or SHA-256 mismatch: {info.filename}")
            if info.filename.lower().endswith("search-index.json"):
                index = json.loads(data.decode("utf-8"))
                records = index.get("records") if isinstance(index, dict) else None
                if not isinstance(index, dict) or index.get("formatVersion") != 1 or not isinstance(records, list):
                    raise ValueError("Offline search index must use formatVersion 1 and contain a records array.")
                if not records:
                    raise ValueError("Offline postcode search index is empty.")
                if len(records) > 2_000_000:
                    raise ValueError("Offline search index exceeds two million records.")
                for number, record in enumerate(records, start=1):
                    if not isinstance(record, dict):
                        raise ValueError(f"Offline search record {number} must be an object.")
                    postcode = str(record.get("postcode", "")).strip().upper()
                    label = str(record.get("label", "")).strip()
                    latitude, longitude = record.get("latitude"), record.get("longitude")
                    if not postcode or not label:
                        raise ValueError(f"Offline search record {number} requires postcode and label.")
                    if not isinstance(latitude, (int, float)) or not isinstance(longitude, (int, float)):
                        raise ValueError(f"Offline search record {number} requires numeric coordinates.")
                    if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                        raise ValueError(f"Offline search record {number} has invalid WGS84 coordinates.")
                    if not (south <= latitude <= north and west <= longitude <= east):
                        raise ValueError(f"Offline search record {number} lies outside package coverage.")
                search_records = records
            if info.filename.lower().endswith("/ofl.txt") or info.filename.lower() == "ofl.txt":
                notice = data.decode("utf-8", errors="replace")
                if "SIL OPEN FONT LICENSE Version 1.1" not in notice or "Copyright 2022 The Noto Project Authors" not in notice:
                    raise ValueError("Noto glyph OFL notice is incomplete or does not identify the Noto Project Authors.")
        if search_records is not None:
            postcode_records = [
                record for record in search_records
                if isinstance(record, dict) and str(record.get("postcode", "")).strip()
            ]
            bt_records = [
                record for record in postcode_records
                if str(record.get("postcode", "")).replace(" ", "").upper().startswith("BT")
            ]
            if postcode_records and any(token in source_names for token in ("code-point", "code point", "onspd", "ordnance survey")):
                required = (
                    "contains os data", "contains royal mail data", "contains national statistics data",
                    "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
                )
                if any(token not in attribution_lower for token in required):
                    raise ValueError("OS/ONSPD postcode packages must include OS, Royal Mail and National Statistics attribution.")
            if bt_records and not any(token in source_names for token in ("land and property services", "lps")):
                raise ValueError("BT postcode records require separate Northern Ireland/LPS redistribution permission evidence.")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--licence-evidence", required=True, type=Path)
    args = parser.parse_args()
    try:
        validate_evidence(args.licence_evidence)
        manifest = validate_package(args.manifest, args.archive, args.licence_evidence)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"OFFLINE RELEASE BLOCKED: {error}", file=sys.stderr)
        return 1
    print(f"OFFLINE RELEASE PREFLIGHT PASSED: {manifest['id']} {manifest.get('packageVersion', manifest.get('version'))}")
    print("Human licence review and all archive member checks passed. Signing/publication still require separate approval.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
