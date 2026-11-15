#!/usr/bin/env python3
"""Split one verified TomOS regional ZIP into MAP, ROUTING and SEARCH packages.

The source archive and manifest are verified before any asset is extracted.
Each output layer is built with package-custom-area-layer.py so the result can
be passed directly to build-signed-layer-catalogue.py.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import stat
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

ROLE_TO_LAYER = {
    "MAP_DATA": "MAP",
    "MAP_GLYPHS": "MAP",
    "ROUTING_GRAPH": "ROUTING",
    "ROUTING_SUPPORT": "ROUTING",
    "SEARCH_INDEX": "SEARCH",
}
MAX_ARCHIVE = 20 * 1024 * 1024 * 1024


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_member(name: str) -> bool:
    path = PurePosixPath(name)
    return not path.is_absolute() and bool(path.parts) and all(
        part not in ("", ".", "..") for part in path.parts
    )


def verify_source(manifest_path: Path, archive_path: Path) -> dict:
    raw = manifest_path.read_bytes()
    if not raw or len(raw) > 4 * 1024 * 1024:
        raise ValueError("Regional manifest is empty or too large.")
    manifest = json.loads(raw)
    if manifest.get("formatVersion") != 1:
        raise ValueError("Unsupported regional manifest version.")
    coverage = manifest.get("coverage")
    if not isinstance(coverage, dict):
        raise ValueError("Regional manifest has no coverage bounds.")
    west, south, east, north = (coverage.get(k) for k in ("west", "south", "east", "north"))
    if not all(isinstance(v, (int, float)) for v in (west, south, east, north)):
        raise ValueError("Regional coverage bounds are invalid.")
    if not (-180 <= west < east <= 180 and -90 <= south < north <= 90):
        raise ValueError("Regional coverage bounds are outside WGS84.")
    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("Regional manifest has no files.")
    if sum(int(entry.get("sizeBytes", 0)) for entry in files) != manifest.get("totalSizeBytes"):
        raise ValueError("Regional manifest total size does not match its assets.")
    if not archive_path.is_file() or archive_path.stat().st_size <= 0 or archive_path.stat().st_size > MAX_ARCHIVE:
        raise ValueError("Regional archive is missing, empty or too large.")
    expected = {}
    for entry in files:
        name = entry.get("path")
        if not isinstance(name, str) or not safe_member(name) or name in expected:
            raise ValueError(f"Unsafe or duplicate manifest asset path: {name!r}")
        size = entry.get("sizeBytes")
        sha = entry.get("sha256")
        if not isinstance(size, int) or size <= 0 or not isinstance(sha, str) or len(sha) != 64:
            raise ValueError(f"Invalid size or SHA-256 for {name}.")
        expected[name] = entry
    with zipfile.ZipFile(archive_path) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        if len(names) != len(set(names)) or set(names) != set(expected):
            raise ValueError("Archive members do not exactly match the regional manifest.")
        for info in infos:
            if not safe_member(info.filename):
                raise ValueError(f"Unsafe ZIP member: {info.filename}")
            if stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError(f"Symbolic links are forbidden: {info.filename}")
            entry = expected[info.filename]
            if info.file_size != entry["sizeBytes"]:
                raise ValueError(f"ZIP member size mismatch: {info.filename}")
            data = archive.read(info)
            if digest(data) != entry["sha256"].lower():
                raise ValueError(f"ZIP member SHA-256 mismatch: {info.filename}")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--packager", type=Path, default=Path(__file__).with_name("package-custom-area-layer.py"))
    parser.add_argument("--version", help="Override the source package version")
    args = parser.parse_args()
    try:
        manifest = verify_source(args.manifest, args.archive)
        grouped: dict[str, list[str]] = {layer: [] for layer in ("MAP", "ROUTING", "SEARCH")}
        for item in manifest["files"]:
            role = item.get("role")
            if role not in ROLE_TO_LAYER:
                raise ValueError(f"Unsupported regional asset role: {role}")
            grouped[ROLE_TO_LAYER[role]].append(item["path"])
        for layer, paths in grouped.items():
            if not paths:
                raise ValueError(f"Regional package is missing its {layer} layer.")
        if not any(path.lower().endswith(".mbtiles") for path in grouped["MAP"]):
            raise ValueError("MAP layer has no MBTiles archive.")
        if not any(path.lower().endswith("tiles.tar") for path in grouped["ROUTING"]):
            raise ValueError("ROUTING layer has no Valhalla tiles.tar.")
        if len([p for p in grouped["SEARCH"] if p.lower().endswith(".json")]) != 1:
            raise ValueError("SEARCH layer must contain exactly one JSON index.")
        args.output_dir.mkdir(parents=True, exist_ok=True)
        version = args.version or manifest["packageVersion"]
        bounds = manifest["coverage"]
        with tempfile.TemporaryDirectory(prefix="tomos-layer-split-") as tmp:
            root = Path(tmp)
            with zipfile.ZipFile(args.archive) as archive:
                for layer, paths in grouped.items():
                    source = root / layer.lower()
                    source.mkdir()
                    for name in paths:
                        destination = source / name
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(archive.read(name))
                    command = [
                        sys.executable, str(args.packager),
                        "--input-dir", str(source),
                        "--output-dir", str(args.output_dir),
                        "--area-id", manifest["id"],
                        "--area-name", manifest["displayName"],
                        "--layer", layer,
                        "--version", version,
                        "--west", str(bounds["west"]),
                        "--south", str(bounds["south"]),
                        "--east", str(bounds["east"]),
                        "--north", str(bounds["north"]),
                        "--attribution", manifest["attribution"],
                    ]
                    subprocess.run(command, check=True)
        print("All three independent layer packages were created from the verified regional archive.")
        print("Run the offline release preflight and review licence evidence before catalogue signing.")
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, zipfile.BadZipFile,
            subprocess.CalledProcessError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
