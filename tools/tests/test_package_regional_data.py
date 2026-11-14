import csv
import hashlib
import json
import subprocess
import sqlite3
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
            connection = sqlite3.connect(map_file)
            connection.execute("CREATE TABLE metadata (name TEXT, value TEXT)")
            connection.execute("CREATE TABLE images (tile_id TEXT PRIMARY KEY, tile_data BLOB)")
            connection.execute("CREATE TABLE map (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_id TEXT)")
            connection.execute(
                "CREATE VIEW tiles AS SELECT map.zoom_level, map.tile_column, map.tile_row, images.tile_data "
                "FROM map JOIN images ON map.tile_id = images.tile_id"
            )
            connection.execute("INSERT INTO metadata VALUES ('format', 'pbf')")
            connection.execute(
                "INSERT INTO metadata VALUES ('json', ?)",
                (json.dumps({"vector_layers": [
                    {"id": "transportation"}, {"id": "water"}, {"id": "building"}
                ]}),),
            )
            connection.execute("INSERT INTO images VALUES ('tile-0', ?)", (b"vector-tile",))
            connection.execute("INSERT INTO map VALUES (0, 0, 0, 'tile-0')")
            connection.commit()
            connection.close()
            graph_dir = root / "valhalla-tiles"
            graph_dir.mkdir()
            (graph_dir / "tile-000.tar").write_bytes(b"routing-graph-tile")
            admins_file = root / "admins.sqlite"
            timezone_file = root / "timezones.sqlite"
            admins_file.write_bytes(b"admin-support-data")
            timezone_file.write_bytes(b"timezone-support-data")
            routing_config = root / "valhalla.json"
            routing_config.write_text('{"mjolnir":{"tile_dir":"/tmp","tile_extract":"/tmp/tiles.tar"}}', encoding="utf-8")
            glyph_dir = root / "glyphs" / "Noto Sans Regular"
            glyph_dir.mkdir(parents=True)
            (glyph_dir / "0-255.pbf").write_bytes(b"latin-glyphs")
            (glyph_dir / "256-511.pbf").write_bytes(b"latin-extended-glyphs")
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
                "--routing-admin-data", str(admins_file),
                "--routing-timezone-data", str(timezone_file),
                "--routing-config", str(routing_config),
                "--glyphs-dir", str(glyph_dir.parent),
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
            self.assertEqual(manifest["attribution"], "© OpenStreetMap contributors")
            self.assertEqual({item["role"] for item in manifest["files"]},
                             {"MAP_DATA", "MAP_GLYPHS", "ROUTING_GRAPH", "ROUTING_SUPPORT", "SEARCH_INDEX"})
            self.assertEqual(metadata["manifestSha256"], hashlib.sha256(manifest_bytes).hexdigest())
            self.assertEqual(metadata["archiveSha256"], hashlib.sha256(archive_path.read_bytes()).hexdigest())
            self.assertEqual(metadata["postcodeRecords"], 1)
            with zipfile.ZipFile(archive_path) as archive:
                self.assertEqual(set(archive.namelist()), {item["path"] for item in manifest["files"]})
                search = json.loads(archive.read("search/search-index.json"))
                self.assertEqual(search["records"][0]["postcode"], "L1 1AA")
                self.assertEqual(len(search["records"]), 1)
                self.assertIn("routing/tiles.tar", archive.namelist())
                self.assertIn("routing/admins.sqlite", archive.namelist())
                self.assertIn("routing/timezones.sqlite", archive.namelist())
                self.assertIn("routing/valhalla.json", archive.namelist())
                self.assertIn("map/glyphs/Noto Sans Regular/0-255.pbf", archive.namelist())


if __name__ == "__main__":
    unittest.main()
