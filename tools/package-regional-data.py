#!/usr/bin/env python3
"""Build a deterministic TomOS regional package from licensed local data files.

The tool does not download map data or publish/sign catalogues. Supply a
MapLibre-compatible MBTiles file, a Valhalla tiles.tar (or tiles directory), and
an optional postcode CSV from sources whose licences permit redistribution.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import shutil
import tarfile
import tempfile
import zipfile
from pathlib import Path
from typing import Any

POSTCODE_RE = re.compile(
    r"^(GIR 0AA|[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2})$",
    re.IGNORECASE,
)
MAX_SEARCH_RECORDS = 2_000_000


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalized_postcode(value: str) -> str | None:
    compact = "".join(value.upper().split())
    if compact == "GIR0AA":
        return compact
    if not re.fullmatch(r"[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}", compact):
        return None
    return compact[:-3] + " " + compact[-3:]


def build_search_index(csv_path: Path, destination: Path, args: argparse.Namespace) -> int:
    records: list[dict[str, Any]] = []
    if csv_path:
        with csv_path.open("r", encoding="utf-8-sig", newline="") as stream:
            reader = csv.DictReader(stream)
            required = {args.postcode_column, args.latitude_column, args.longitude_column}
            if not reader.fieldnames or not required.issubset(reader.fieldnames):
                raise ValueError(
                    "Postcode CSV must contain columns: "
                    + ", ".join(sorted(required))
                )
            for row_number, row in enumerate(reader, start=2):
                postcode = normalized_postcode((row.get(args.postcode_column) or "").strip())
                if not postcode:
                    continue
                try:
                    latitude = float(row[args.latitude_column])
                    longitude = float(row[args.longitude_column])
                except (TypeError, ValueError):
                    continue
                if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                    continue
                if not (args.south <= latitude <= args.north and args.west <= longitude <= args.east):
                    continue
                label = (row.get(args.label_column) or "").strip() if args.label_column else ""
                if not label:
                    label = postcode
                category = (row.get(args.category_column) or "").strip() if args.category_column else ""
                records.append({
                    "id": f"postcode-{len(records) + 1}",
                    "label": label,
                    "latitude": latitude,
                    "longitude": longitude,
                    "postcode": postcode,
                    **({"category": category} if category else {}),
                })
                if len(records) > MAX_SEARCH_RECORDS:
                    raise ValueError("Postcode CSV exceeds the two-million-record package limit.")
    destination.write_text(
        json.dumps({"formatVersion": 1, "records": records}, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    return len(records)


def add_asset(files: list[dict[str, Any]], root: Path, relative: str, role: str) -> None:
    path = root / relative
    size = path.stat().st_size
    if size <= 0:
        raise ValueError(f"Package asset is empty: {relative}")
    files.append({
        "path": relative,
        "role": role,
        "sizeBytes": size,
        "sha256": sha256_file(path),
    })


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--id", required=True, help="Stable package id, e.g. liverpool-city-region")
    parser.add_argument("--name", required=True, help="Display name")
    parser.add_argument("--version", required=True, help="Package version, e.g. 2026.11.1")
    parser.add_argument("--region-code", required=True, help="Short uppercase region code, e.g. UK-LIV")
    parser.add_argument("--map-data", required=True, type=Path, help="Licensed MapLibre-compatible .mbtiles file")
    parser.add_argument("--routing-data", required=True, type=Path, help="Valhalla tiles.tar file or tile directory")
    parser.add_argument("--postcode-csv", type=Path, help="Licensed postcode CSV; omit to package an empty search index")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--west", required=True, type=float)
    parser.add_argument("--south", required=True, type=float)
    parser.add_argument("--east", required=True, type=float)
    parser.add_argument("--north", required=True, type=float)
    parser.add_argument("--postcode-column", default="postcode")
    parser.add_argument("--latitude-column", default="latitude")
    parser.add_argument("--longitude-column", default="longitude")
    parser.add_argument("--label-column", default="label")
    parser.add_argument("--category-column", default="category")
    parser.add_argument("--source-attribution", default="© OpenStreetMap contributors")
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]*", args.id):
        parser.error("--id must be lowercase and contain only letters, digits, dot, underscore or hyphen")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", args.version):
        parser.error("--version contains unsupported characters")
    if not re.fullmatch(r"[A-Z0-9_-]{2,16}", args.region_code):
        parser.error("--region-code must be 2–16 uppercase letters, digits, _ or -")
    if not (args.west < args.east and args.south < args.north):
        parser.error("Coverage bounds must have west < east and south < north")
    if not (-180 <= args.west <= 180 and -180 <= args.east <= 180 and
            -90 <= args.south <= 90 and -90 <= args.north <= 90):
        parser.error("Coverage bounds must be valid WGS84 coordinates")
    if not args.map_data.is_file() or args.map_data.stat().st_size == 0:
        parser.error("--map-data must be a non-empty MBTiles file")
    if args.postcode_csv and not args.postcode_csv.is_file():
        parser.error("--postcode-csv does not exist")
    if not args.routing_data.exists():
        parser.error("--routing-data does not exist")

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tomos-region-") as temp:
        root = Path(temp)
        map_dir = root / "map"
        route_dir = root / "routing"
        search_dir = root / "search"
        map_dir.mkdir()
        route_dir.mkdir()
        search_dir.mkdir()

        shutil.copyfile(args.map_data, map_dir / "region.mbtiles")
        if args.routing_data.is_dir():
            with tarfile.open(route_dir / "tiles.tar", "w") as archive:
                for item in sorted(args.routing_data.rglob("*")):
                    if item.is_file() and not item.is_symlink():
                        archive.add(item, arcname=item.relative_to(args.routing_data), recursive=False)
        else:
            shutil.copyfile(args.routing_data, route_dir / "tiles.tar")
        if (route_dir / "tiles.tar").stat().st_size == 0:
            raise ValueError("Valhalla tile archive is empty.")

        search_file = search_dir / "search-index.json"
        record_count = build_search_index(args.postcode_csv, search_file, args)

        assets: list[dict[str, Any]] = []
        add_asset(assets, root, "map/region.mbtiles", "MAP_DATA")
        add_asset(assets, root, "routing/tiles.tar", "ROUTING_GRAPH")
        add_asset(assets, root, "search/search-index.json", "SEARCH_INDEX")
        total_size = sum(asset["sizeBytes"] for asset in assets)

        manifest = {
            "formatVersion": 1,
            "id": args.id,
            "displayName": args.name,
            "packageVersion": args.version,
            "regionCode": args.region_code,
            "totalSizeBytes": total_size,
            "coverage": {
                "west": args.west,
                "south": args.south,
                "east": args.east,
                "north": args.north,
            },
            "files": assets,
        }
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        manifest_path = output / f"{args.id}-{args.version}.manifest.json"
        manifest_path.write_bytes(manifest_bytes)
        archive_path = output / f"{args.id}-{args.version}.zip"
        with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for asset in assets:
                archive.write(root / asset["path"], arcname=asset["path"])

        metadata = {
            "id": args.id,
            "name": args.name,
            "version": args.version,
            "estimatedSizeBytes": archive_path.stat().st_size,
            "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
            "archiveSha256": sha256_file(archive_path),
            "coverage": manifest["coverage"],
            "postcodeRecords": record_count,
            "sourceAttribution": args.source_attribution,
            "manifestFile": manifest_path.name,
            "archiveFile": archive_path.name,
            "signing": "NOT SIGNED: add this entry to the publisher-signed catalogue after hosting both files over HTTPS",
        }
        metadata_path = output / f"{args.id}-{args.version}.metadata.json"
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")

    print(f"Package archive: {archive_path}")
    print(f"Manifest:        {manifest_path}")
    print(f"Metadata:        {metadata_path}")
    print(f"Archive SHA256:  {metadata['archiveSha256']}")
    print(f"Postcode rows:   {record_count}")
    print("Catalogue signing and HTTPS publication are intentionally not performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
