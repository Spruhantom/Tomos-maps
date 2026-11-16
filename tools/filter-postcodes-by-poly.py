#!/usr/bin/env python3
"""Keep postcode points inside a committed Osmium .poly clipping boundary."""
from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path


def read_poly(path: Path) -> list[tuple[bool, list[tuple[float, float]]]]:
    lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    rings: list[tuple[bool, list[tuple[float, float]]]] = []
    index = 1  # first line is the polygon name
    while index < len(lines) and lines[index] != "END":
        ring_id = lines[index]
        index += 1
        points: list[tuple[float, float]] = []
        while index < len(lines) and lines[index] != "END":
            fields = lines[index].split()
            if len(fields) < 2:
                raise ValueError(f"Invalid coordinate in {path}: {lines[index]}")
            longitude, latitude = float(fields[0]), float(fields[1])
            if not (math.isfinite(longitude) and math.isfinite(latitude)):
                raise ValueError(f"Non-finite coordinate in {path}")
            points.append((longitude, latitude))
            index += 1
        if index >= len(lines) or len(points) < 4:
            raise ValueError(f"Invalid polygon ring {ring_id!r} in {path}")
        index += 1
        rings.append((ring_id.startswith("!"), points))
    if not rings:
        raise ValueError(f"No polygon rings in {path}")
    return rings


def point_in_ring(longitude: float, latitude: float, points: list[tuple[float, float]]) -> bool:
    inside = False
    previous = points[-1]
    for current in points:
        x1, y1 = previous
        x2, y2 = current
        if (y1 > latitude) != (y2 > latitude):
            crossing = (x2 - x1) * (latitude - y1) / (y2 - y1) + x1
            if longitude < crossing:
                inside = not inside
        previous = current
    return inside


def contains(longitude: float, latitude: float, rings: list[tuple[bool, list[tuple[float, float]]]]) -> bool:
    in_outer = any(point_in_ring(longitude, latitude, points) for hole, points in rings if not hole)
    in_hole = any(point_in_ring(longitude, latitude, points) for hole, points in rings if hole)
    return in_outer and not in_hole


def filter_csv(source: Path, destination: Path, polygon: Path) -> tuple[int, int]:
    rings = read_poly(polygon)
    destination.parent.mkdir(parents=True, exist_ok=True)
    kept = rejected = 0
    with source.open("r", encoding="utf-8-sig", newline="") as input_stream, destination.open(
        "w", encoding="utf-8", newline=""
    ) as output_stream:
        reader = csv.DictReader(input_stream)
        required = {"postcode", "latitude", "longitude"}
        if not reader.fieldnames or not required.issubset(reader.fieldnames):
            raise ValueError("Postcode CSV requires postcode, latitude and longitude columns.")
        writer = csv.DictWriter(output_stream, fieldnames=reader.fieldnames)
        writer.writeheader()
        for row in reader:
            try:
                latitude = float(row["latitude"])
                longitude = float(row["longitude"])
            except (TypeError, ValueError):
                rejected += 1
                continue
            if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                rejected += 1
                continue
            if contains(longitude, latitude, rings):
                writer.writerow(row)
                kept += 1
            else:
                rejected += 1
    if kept == 0:
        destination.unlink(missing_ok=True)
        raise ValueError("No postcode records fall inside the regional clipping polygon.")
    return kept, rejected


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--polygon", required=True, type=Path)
    args = parser.parse_args()
    if not args.input.is_file() or not args.polygon.is_file():
        parser.error("Input postcode CSV and polygon must both exist.")
    try:
        kept, rejected = filter_csv(args.input, args.output, args.polygon)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(f"Filtered regional postcodes: kept {kept:,}; outside/invalid {rejected:,}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
