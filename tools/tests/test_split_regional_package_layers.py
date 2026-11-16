import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPLITTER = ROOT / "split-regional-package-layers.py"


class SplitRegionalPackageLayersTest(unittest.TestCase):
    def create_source(self, root: Path):
        assets = {
            "map/region.mbtiles": (b"map-bytes", "MAP_DATA"),
            "map/glyphs/Noto Sans Regular/0-255.pbf": (b"glyph-bytes", "MAP_GLYPHS"),
            "routing/tiles.tar": (b"routing-bytes", "ROUTING_GRAPH"),
            "routing/admins.sqlite": (b"admins", "ROUTING_SUPPORT"),
            "routing/timezones.sqlite": (b"timezones", "ROUTING_SUPPORT"),
            "routing/valhalla.json": (b"{}", "ROUTING_SUPPORT"),
            "search/search-index.json": (b'{"formatVersion":1,"records":[]}', "SEARCH_INDEX"),
        }
        files = [
            {"path": name, "role": role, "sizeBytes": len(data),
             "sha256": hashlib.sha256(data).hexdigest()}
            for name, (data, role) in assets.items()
        ]
        manifest = {
            "formatVersion": 1, "id": "liverpool-city-region",
            "displayName": "Liverpool City Region", "packageVersion": "2026.11.15",
            "regionCode": "UK-LIV", "totalSizeBytes": sum(len(v[0]) for v in assets.values()),
            "attribution": "© OpenStreetMap contributors",
            "coverage": {"west": -3.35, "south": 53.28, "east": -2.57, "north": 53.71},
            "files": files,
        }
        manifest_path = root / "region.manifest.json"
        manifest_path.write_text(json.dumps(manifest))
        archive_path = root / "region.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            for name, (data, _) in assets.items():
                archive.writestr(name, data)
        return manifest_path, archive_path

    def test_splits_verified_package_into_all_three_layers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, archive = self.create_source(root)
            output = root / "layers"
            subprocess.run([
                sys.executable, str(SPLITTER), "--manifest", str(manifest),
                "--archive", str(archive), "--output-dir", str(output),
            ], check=True, capture_output=True, text=True)
            metadata = [json.loads(path.read_text()) for path in output.glob("*.metadata.json")]
            self.assertEqual({item["layer"] for item in metadata}, {"MAP", "ROUTING", "SEARCH"})
            by_layer = {item["layer"]: item for item in metadata}
            with zipfile.ZipFile(output / by_layer["MAP"]["archiveFile"]) as package:
                self.assertIn("map/region.mbtiles", package.namelist())
                self.assertIn("map/glyphs/Noto Sans Regular/0-255.pbf", package.namelist())
            with zipfile.ZipFile(output / by_layer["ROUTING"]["archiveFile"]) as package:
                self.assertIn("routing/tiles.tar", package.namelist())
                self.assertIn("routing/admins.sqlite", package.namelist())
            with zipfile.ZipFile(output / by_layer["SEARCH"]["archiveFile"]) as package:
                self.assertEqual(package.namelist(), ["search/search-index.json"])

    def test_refuses_tampered_source_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, archive = self.create_source(root)
            with zipfile.ZipFile(archive, "w") as tampered:
                tampered.writestr("map/region.mbtiles", b"tampered")
            result = subprocess.run([
                sys.executable, str(SPLITTER), "--manifest", str(manifest),
                "--archive", str(archive), "--output-dir", str(root / "layers"),
            ], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Archive members do not exactly match", result.stderr)

    def test_refuses_missing_layer(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, archive = self.create_source(root)
            data = json.loads(manifest.read_text())
            data["files"] = [entry for entry in data["files"] if entry["role"] != "SEARCH_INDEX"]
            data["totalSizeBytes"] = sum(entry["sizeBytes"] for entry in data["files"])
            manifest.write_text(json.dumps(data))
            with zipfile.ZipFile(archive, "w") as rebuilt:
                for name in ("map/region.mbtiles", "map/glyphs/Noto Sans Regular/0-255.pbf",
                             "routing/tiles.tar", "routing/admins.sqlite",
                             "routing/timezones.sqlite", "routing/valhalla.json"):
                    rebuilt.writestr(name, {
                        "map/region.mbtiles": b"map-bytes",
                        "map/glyphs/Noto Sans Regular/0-255.pbf": b"glyph-bytes",
                        "routing/tiles.tar": b"routing-bytes",
                        "routing/admins.sqlite": b"admins",
                        "routing/timezones.sqlite": b"timezones",
                        "routing/valhalla.json": b"{}",
                    }[name])
            result = subprocess.run([
                sys.executable, str(SPLITTER), "--manifest", str(manifest),
                "--archive", str(archive), "--output-dir", str(root / "layers"),
            ], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("SEARCH layer", result.stderr)


if __name__ == "__main__":
    unittest.main()
