#!/usr/bin/env python3
"""Convert OS Code-Point Open GB CSVs into a Liverpool offline postcode index input."""
from __future__ import annotations

import argparse
import csv
import io
import zipfile
from pathlib import Path

from pyproj import Transformer

TRANSFORMER = Transformer.from_crs("EPSG:27700", "EPSG:4326", always_xy=True)


def prepare(source_zip: Path, destination: Path, west: float, south: float, east: float, north: float) -> int:
    destination.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with zipfile.ZipFile(source_zip) as archive, destination.open(
        "w", encoding="utf-8", newline=""
    ) as output:
        writer = csv.DictWriter(
            output,
            fieldnames=["postcode", "latitude", "longitude", "label", "category"],
        )
        writer.writeheader()
        data_files = sorted(
            name for name in archive.namelist()
            if name.casefold().endswith(".csv")
            and ("/data/csv/" in name.casefold() or name.casefold().startswith("data/csv/"))
        )
        if not data_files:
            raise ValueError("The OS Code-Point Open ZIP contains no Data/CSV postcode files.")

        for name in data_files:
            with archive.open(name) as binary:
                text = io.TextIOWrapper(binary, encoding="utf-8-sig", newline="")
                for row in csv.reader(text):
                    if len(row) < 3:
                        continue
                    postcode = row[0].strip().upper()
                    try:
                        easting = float(row[1])
                        northing = float(row[2])
                    except ValueError:
                        continue
                    longitude, latitude = TRANSFORMER.transform(easting, northing)
                    if not (west <= longitude <= east and south <= latitude <= north):
                        continue
                    writer.writerow({
                        "postcode": postcode,
                        "latitude": f"{latitude:.7f}",
                        "longitude": f"{longitude:.7f}",
                        "label": postcode,
                        "category": "postcode",
                    })
                    count += 1
    if count == 0:
        raise ValueError("No postcode points fell inside the requested regional bounds.")
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-zip", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--west", type=float, required=True)
    parser.add_argument("--south", type=float, required=True)
    parser.add_argument("--east", type=float, required=True)
    parser.add_argument("--north", type=float, required=True)
    args = parser.parse_args()
    if not args.source_zip.is_file():
        parser.error("--source-zip does not exist")
    if not (args.west < args.east and args.south < args.north):
        parser.error("Coverage bounds must be west < east and south < north")
    count = prepare(args.source_zip, args.output, args.west, args.south, args.east, args.north)
    print(f"Prepared {count:,} live GB postcode points inside the Liverpool regional bounds.")
    print("Coordinates transformed from British National Grid EPSG:27700 to WGS84 EPSG:4326.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
