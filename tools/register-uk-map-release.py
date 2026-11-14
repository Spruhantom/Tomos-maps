#!/usr/bin/env python3
"""Register a built UK PMTiles release in the public data catalogue."""
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

def bump_patch(version: str) -> str:
    parts = version.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise ValueError(f"catalogueVersion must be semantic X.Y.Z, got {version!r}")
    major, minor, patch = map(int, parts)
    return f"{major}.{minor}.{patch + 1}"

def register(catalogue_path: Path, asset_path: Path, version: str, tag: str, repository: str) -> dict:
    data = json.loads(catalogue_path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1 or data.get("coverage", {}).get("country") != "GB":
        raise ValueError("Expected schemaVersion 1 and GB catalogue coverage.")
    packages = data.setdefault("packages", [])
    package_id = f"uk-map-{version}"
    if any(item.get("id") == package_id for item in packages):
        raise ValueError(f"Package {package_id} is already registered.")
    if not asset_path.is_file() or asset_path.stat().st_size <= 0:
        raise ValueError("Map archive is missing or empty.")
    size = asset_path.stat().st_size
    if size >= 2_000_000_000:
        raise ValueError("Map archive exceeds the conservative GitHub Release upload limit.")
    hasher = hashlib.sha256()
    with asset_path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    digest = hasher.hexdigest()
    url = f"https://github.com/{repository}/releases/download/{tag}/{asset_path.name}"
    package = {
        "id": package_id, "name": "United Kingdom vector map", "layer": "map",
        "version": version, "format": "pmtiles",
        "coverage": {"country": "GB", "name": "United Kingdom"},
        "asset": {"url": url, "sha256": digest, "sizeBytes": size},
        "source": "Geofabrik United Kingdom OpenStreetMap extract",
        "licence": "ODbL-1.0",
        "attribution": "© OpenStreetMap contributors",
        "attributionUrl": "https://www.openstreetmap.org/copyright"
    }
    packages.append(package)
    data["catalogueVersion"] = bump_patch(data.get("catalogueVersion", "0.1.0"))
    data["coverage"]["status"] = "map-layer-published-routing-and-search-pending"
    catalogue_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return package

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalogue", type=Path, default=Path("data/catalogue/uk.json"))
    parser.add_argument("--asset", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", required=True)
    args = parser.parse_args()
    package = register(args.catalogue, args.asset, args.version, args.tag, args.repository)
    print(f"Registered {package['id']} ({package['asset']['sizeBytes']} bytes, SHA-256 {package['asset']['sha256']}).")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
