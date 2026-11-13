import csv
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "package-regional-data.py"


class RegionalPackageBuilderTest(unittest.TestCase):
    def test_builds_manifest_archive_and_postcode_index(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            map_file = root / "map.mbtiles"
            map_file.write_bytes(b"test-map-dataset")
            graph_dir = root / "valhalla-tiles"
            graph_dir.mkdir()
            (graph_dir / "tile-000.tar").write_bytes(b"routing-graph-tile")
            postcode_csv = root / "postcodes.csv"
            with postcode_csv.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(stream, fieldnames=["postcode", "latitude", "longitude", "label"])
                writer.writeheader()
                writer.writerow({
                    "postcode": "L1 1AA",
                    "latitude": "53.405",
                    "longitude": "-2.99",
                    "label": "Liverpool city centre",
                })
                writer.writerow({
                    "postcode": "M1 1AE",
                    "latitude": "53.48",
                    "longitude": "-2.24",
                    "label": "Outside coverage",
                })
            output = root / "output"
            subprocess.run([
                sys.executable, str(SCRIPT),
                "--id", "liverpool-city-region",
                "--name", "Liverpool City Region",
                "--version", "2026.11.1",
                "--region-code", "UK-LIV",
                "--map-data", str(map_file),
                "--routing-data", str(graph_dir),
                "--postcode-csv", str(postcode_csv),
                "--output", str(output),
                "--west", "-3.2", "--south", "53.2",
                "--east", "-2.6", "--north", "53.6",
            ], check=True, capture_output=True, text=True)

            manifest_path = output / "liverpool-city-region-2026.11.1.manifest.json"
            archive_path = output / "liverpool-city-region-2026.11.1.zip"
            metadata_path = output / "liverpool-city-region-2026.11.1.metadata.json"
            manifest_bytes = manifest_path.read_bytes()
            manifest = json.loads(manifest_bytes)
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            self.assertEqual(manifest["formatVersion"], 1)
            self.assertEqual(manifest["coverage"]["west"], -3.2)
            self.assertEqual({item["role"] for item in manifest["files"]},
                             {"MAP_DATA", "ROUTING_GRAPH", "SEARCH_INDEX"})
            self.assertEqual(metadata["manifestSha256"], hashlib.sha256(manifest_bytes).hexdigest())
            self.assertEqual(metadata["archiveSha256"], hashlib.sha256(archive_path.read_bytes()).hexdigest())
            self.assertEqual(metadata["postcodeRecords"], 1)
            with zipfile.ZipFile(archive_path) as archive:
                self.assertEqual(set(archive.namelist()), {item["path"] for item in manifest["files"]})
                search = json.loads(archive.read("search/search-index.json"))
                self.assertEqual(search["records"][0]["postcode"], "L1 1AA")
                self.assertEqual(len(search["records"]), 1)
                self.assertIn("routing/tiles.tar", archive.namelist())


if __name__ == "__main__":
    unittest.main()
