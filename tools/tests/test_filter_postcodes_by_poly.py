import csv
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "filter-postcodes-by-poly.py"
BOUNDARIES = ROOT / "data" / "boundaries"


class PostcodePolygonFilterTests(unittest.TestCase):
    def test_keeps_storrington_and_drops_outside_south_east_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "postcodes.csv"
            output = root / "filtered.csv"
            with source.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=["postcode", "latitude", "longitude", "label", "category"])
                writer.writeheader()
                writer.writerow({"postcode": "RH20 4AA", "latitude": "50.93", "longitude": "-0.454", "label": "Storrington", "category": "postcode"})
                writer.writerow({"postcode": "L1 1AA", "latitude": "53.4", "longitude": "-2.99", "label": "Liverpool", "category": "postcode"})
            subprocess.run([
                sys.executable, str(SCRIPT), "--input", str(source), "--output", str(output),
                "--polygon", str(BOUNDARIES / "south-east-england.poly"),
            ], check=True, capture_output=True, text=True)
            with output.open("r", encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual([row["postcode"] for row in rows], ["RH20 4AA"])

    def test_keeps_london_and_drops_nearby_outside_point(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, output = root / "in.csv", root / "out.csv"
            with source.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=["postcode", "latitude", "longitude"])
                writer.writeheader()
                writer.writerow({"postcode": "SW1A 1AA", "latitude": "51.5072", "longitude": "-0.1276"})
                writer.writerow({"postcode": "RH20 4AA", "latitude": "50.93", "longitude": "-0.454"})
            subprocess.run([
                sys.executable, str(SCRIPT), "--input", str(source), "--output", str(output),
                "--polygon", str(BOUNDARIES / "greater-london.poly"),
            ], check=True, capture_output=True, text=True)
            with output.open("r", encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual([row["postcode"] for row in rows], ["SW1A 1AA"])


if __name__ == "__main__":
    unittest.main()
