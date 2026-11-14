#!/usr/bin/env python3
"""Create a checksum-manifested ZIP for exactly one offline map/routing/search layer."""
from __future__ import annotations
import argparse, hashlib, json, re, zipfile
from pathlib import Path

LAYERS = {"MAP", "ROUTING", "SEARCH"}
MAX_TOTAL = 20 * 1024 * 1024 * 1024

def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input-dir", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    p.add_argument("--area-id", required=True)
    p.add_argument("--area-name", required=True)
    p.add_argument("--layer", choices=sorted(LAYERS), required=True)
    p.add_argument("--version", required=True)
    for key in ("west", "south", "east", "north"): p.add_argument("--"+key, type=float, required=True)
    p.add_argument("--attribution", default="© OpenStreetMap contributors")
    a = p.parse_args()
    try:
        if not re.fullmatch(r"[a-z0-9][a-z0-9._-]*", a.area_id): raise ValueError("Invalid area ID.")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", a.version): raise ValueError("Invalid version.")
        if not a.area_name.strip(): raise ValueError("Area name is required.")
        if not (-180 <= a.west < a.east <= 180 and -90 <= a.south < a.north <= 90):
            raise ValueError("Invalid WGS84 coverage bounds.")
        source = a.input_dir.resolve(strict=True)
        if not source.is_dir(): raise ValueError("Input path must be a directory.")
        files = sorted(source.rglob("*"))
        entries = []
        for path in files:
            if path.is_symlink(): raise ValueError(f"Symbolic links are not permitted: {path}")
            if not path.is_file(): continue
            relative = path.relative_to(source).as_posix()
            if not relative or relative.startswith("/") or ".." in Path(relative).parts:
                raise ValueError(f"Unsafe package path: {relative}")
            size = path.stat().st_size
            if size <= 0: raise ValueError(f"Empty package file: {relative}")
            entries.append({"path": relative, "sizeBytes": size, "sha256": sha(path)})
        paths = [item["path"].lower() for item in entries]
        if a.layer == "MAP" and not any(name.endswith(".mbtiles") for name in paths):
            raise ValueError("MAP layer must include an MBTiles file.")
        if a.layer == "ROUTING" and not any(name.endswith("tiles.tar") for name in paths):
            raise ValueError("ROUTING layer must include a Valhalla tiles.tar graph.")
        if a.layer == "SEARCH" and len([name for name in paths if name.endswith(".json")]) != 1:
            raise ValueError("SEARCH layer must contain exactly one JSON search-index file.")
        total = sum(item["sizeBytes"] for item in entries)
        if not entries or total > MAX_TOTAL: raise ValueError("Layer must contain files totalling 1 byte to 20 GiB.")
        package_id = f"{a.area_id}-{a.layer.lower()}-{a.version.lower()}"
        manifest = {
            "formatVersion": 1, "id": package_id, "areaId": a.area_id,
            "layer": a.layer, "version": a.version, "totalSizeBytes": total,
            "coverage": {"west": a.west, "south": a.south, "east": a.east, "north": a.north},
            "attribution": a.attribution, "files": entries,
        }
        a.output_dir.mkdir(parents=True, exist_ok=True)
        manifest_path = a.output_dir / f"{package_id}.manifest.json"
        archive_path = a.output_dir / f"{package_id}.zip"
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
        manifest_path.write_bytes(manifest_bytes)
        with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
            for entry in entries:
                archive.write(source / entry["path"], entry["path"])
        metadata = {
            "id": package_id, "areaId": a.area_id, "areaName": a.area_name.strip(),
            "layer": a.layer, "version": a.version,
            "manifestFile": manifest_path.name, "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
            "archiveFile": archive_path.name, "archiveSha256": sha(archive_path),
            "archiveSizeBytes": archive_path.stat().st_size, "installedSizeBytes": total,
        }
        metadata_path = a.output_dir / f"{package_id}.metadata.json"
        metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        with zipfile.ZipFile(archive_path) as archive:
            if archive.testzip() is not None: raise ValueError("Created layer archive failed ZIP integrity check.")
        print(f"Built {a.layer} layer {package_id}: archive {metadata['archiveSizeBytes']:,} bytes, installed {total:,} bytes.")
        print(f"Manifest: {manifest_path}\nArchive: {archive_path}\nMetadata: {metadata_path}")
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        p.error(str(error))
    return 0

if __name__ == "__main__": raise SystemExit(main())
