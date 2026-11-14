#!/usr/bin/env python3
"""Build a bounded, standalone TomOS postcode/place search index.

Input CSVs must be from sources whose licences permit offline redistribution.
Postcodes require postcode, latitude and longitude columns. Place CSVs require
name, latitude and longitude; optional category and id columns are supported.
"""
from __future__ import annotations
import argparse, csv, json, math, re
from pathlib import Path

POSTCODE = re.compile(r"^(GIR 0AA|[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2})$", re.I)
MAX_RECORDS = 2_000_000
MAX_BYTES = 256 * 1024 * 1024

def inside(lat: float, lon: float, bounds: tuple[float, float, float, float]) -> bool:
    west, south, east, north = bounds
    return south <= lat <= north and west <= lon <= east

def coordinate(row: dict[str, str], source: Path, line: int) -> tuple[float, float]:
    try:
        lat, lon = float(row["latitude"]), float(row["longitude"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"{source}:{line}: invalid latitude/longitude") from exc
    if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError(f"{source}:{line}: coordinates outside WGS84 range")
    return lat, lon

def load_postcodes(path: Path | None, bounds: tuple[float, float, float, float]) -> list[dict]:
    if path is None: return []
    result = []
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or not {"postcode", "latitude", "longitude"}.issubset(reader.fieldnames):
            raise ValueError(f"{path}: requires postcode, latitude and longitude columns")
        for line, row in enumerate(reader, 2):
            raw = (row.get("postcode") or "").strip().upper()
            compact = "".join(raw.split())
            if not POSTCODE.fullmatch(raw) and not re.fullmatch(r"GIR0AA|[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}", compact):
                continue
            lat, lon = coordinate(row, path, line)
            if inside(lat, lon, bounds):
                result.append({"id": f"postcode-{len(result)+1}", "label": raw, "latitude": lat,
                               "longitude": lon, "postcode": raw, "category": "postcode"})
    return result

def load_places(path: Path | None, bounds: tuple[float, float, float, float], offset: int) -> list[dict]:
    if path is None: return []
    result = []
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or not {"name", "latitude", "longitude"}.issubset(reader.fieldnames):
            raise ValueError(f"{path}: requires name, latitude and longitude columns")
        for line, row in enumerate(reader, 2):
            name = (row.get("name") or "").strip()
            if not name: continue
            lat, lon = coordinate(row, path, line)
            if not inside(lat, lon, bounds): continue
            stable_id = (row.get("id") or "").strip()
            result.append({"id": stable_id or f"place-{offset+len(result)+1}", "label": name,
                           "latitude": lat, "longitude": lon,
                           "category": (row.get("category") or "place").strip() or "place"})
    return result

def build(postcodes: Path | None, places: Path | None, output: Path,
          bounds: tuple[float, float, float, float]) -> int:
    if postcodes is None and places is None:
        raise ValueError("Supply at least one of --postcodes or --places.")
    records = load_postcodes(postcodes, bounds)
    records.extend(load_places(places, bounds, len(records)))
    if not records: raise ValueError("No postcode or place records fall inside the selected area.")
    if len(records) > MAX_RECORDS: raise ValueError("Search index exceeds two million records.")
    ids = [r["id"] for r in records]
    if len(ids) != len(set(ids)): raise ValueError("Search index contains duplicate record IDs.")
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps({"formatVersion": 1, "records": records}, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    if len(payload) > MAX_BYTES: raise ValueError("Search index exceeds the app's 256 MiB limit.")
    output.write_bytes(payload)
    return len(records)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--postcodes", type=Path)
    parser.add_argument("--places", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--west", type=float, required=True)
    parser.add_argument("--south", type=float, required=True)
    parser.add_argument("--east", type=float, required=True)
    parser.add_argument("--north", type=float, required=True)
    args = parser.parse_args()
    bounds = (args.west, args.south, args.east, args.north)
    if not all(map(math.isfinite, bounds)) or not (-180 <= args.west < args.east <= 180 and -90 <= args.south < args.north <= 90):
        parser.error("Coverage must be a valid non-wrapping WGS84 rectangle.")
    try: count = build(args.postcodes, args.places, args.output, bounds)
    except (OSError, ValueError) as error: parser.error(str(error))
    print(f"Wrote {count:,} postcode/place records to {args.output} ({args.output.stat().st_size:,} bytes).")
    print("Input-source licensing and attribution must be preserved by the publisher.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
