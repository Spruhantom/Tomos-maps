import hashlib
import json
import tempfile
import unittest
import zipfile
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_offline_release import validate_evidence, validate_package


class OfflineReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.assets = {
            "map/region.mbtiles": (b"map", "MAP_DATA"),
            "routing/tiles.tar": (b"route", "ROUTING_GRAPH"),
            "routing/admins.sqlite": (b"admin", "ROUTING_SUPPORT"),
            "search/search-index.json": (b"search", "SEARCH_INDEX"),
        }
        files = [
            {"path": name, "role": role, "sizeBytes": len(data),
             "sha256": hashlib.sha256(data).hexdigest()}
            for name, (data, role) in self.assets.items()
        ]
        self.manifest = {
            "formatVersion": 1, "id": "test-area", "packageVersion": "1.0.0",
            "totalSizeBytes": sum(len(data) for data, _ in self.assets.values()),
            "attribution": "© OpenStreetMap contributors",
            "coverage": {"west": -3.0, "south": 53.0, "east": -2.0, "north": 54.0},
            "files": files,
        }
        self.manifest_path = self.root / "manifest.json"
        self.manifest_path.write_text(json.dumps(self.manifest))
        self.archive_path = self.root / "package.zip"
        with zipfile.ZipFile(self.archive_path, "w") as archive:
            for name, (data, _) in self.assets.items():
                archive.writestr(name, data)
        self.evidence_path = self.root / "licence-evidence.json"
        self.evidence_path.write_text(json.dumps({
            "redistributionPermitted": True, "reviewedBy": "Reviewer",
            "reviewedAt": "2026-11-15",
            "sources": [{"name": "Test source", "licence": "Test licence", "evidence": "approval record"}],
            "requiredAttribution": "© contributors",
        }))

    def tearDown(self):
        self.temp.cleanup()

    def test_accepts_complete_package_and_review_record(self):
        validate_evidence(self.evidence_path)
        self.assertEqual(validate_package(self.manifest_path, self.archive_path)["id"], "test-area")

    def test_rejects_missing_redistribution_permission(self):
        data = json.loads(self.evidence_path.read_text())
        data["redistributionPermitted"] = False
        self.evidence_path.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "does not explicitly permit"):
            validate_evidence(self.evidence_path)

    def test_rejects_archive_checksum_mismatch(self):
        with zipfile.ZipFile(self.archive_path, "w") as archive:
            archive.writestr("map/region.mbtiles", b"tampered")
            for name, (data, _) in self.assets.items():
                if name != "map/region.mbtiles":
                    archive.writestr(name, data)
        with self.assertRaisesRegex(ValueError, "Size or SHA-256 mismatch"):
            validate_package(self.manifest_path, self.archive_path)

    def test_accepts_independent_routing_layer(self):
        assets = {
            "routing/tiles.tar": b"tiles",
            "routing/admins.sqlite": b"admins",
            "routing/timezones.sqlite": b"timezones",
            "routing/valhalla.json": b"{}",
        }
        files = [
            {"path": name, "sizeBytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in assets.items()
        ]
        manifest = {
            "formatVersion": 1, "id": "liverpool-routing-v1", "areaId": "liverpool",
            "layer": "ROUTING", "version": "1.0.0",
            "totalSizeBytes": sum(len(data) for data in assets.values()),
            "attribution": "© OpenStreetMap contributors",
            "coverage": {"west": -3.0, "south": 53.0, "east": -2.0, "north": 54.0},
            "files": files,
        }
        self.manifest_path.write_text(json.dumps(manifest))
        with zipfile.ZipFile(self.archive_path, "w") as archive:
            for name, data in assets.items():
                archive.writestr(name, data)
        self.assertEqual(validate_package(self.manifest_path, self.archive_path)["layer"], "ROUTING")

    def test_rejects_missing_required_layer(self):
        self.manifest["files"] = [f for f in self.manifest["files"] if f["role"] != "SEARCH_INDEX"]
        self.manifest_path.write_text(json.dumps(self.manifest))
        with self.assertRaisesRegex(ValueError, "Missing required data roles"):
            validate_package(self.manifest_path, self.archive_path)


if __name__ == "__main__":
    unittest.main()
