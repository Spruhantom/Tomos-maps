import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_routing_metadata import verify_assets


class RoutingMetadataTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.package = self.root / "package"
        self.package.mkdir()
        self.asset = self.package / "sample.bin"
        self.asset.write_bytes(b"routing-review")
        self.metadata = self.root / "metadata.json"
        self.write_metadata("sample.bin", self.asset.read_bytes())

    def tearDown(self):
        self.temp.cleanup()

    def write_metadata(self, name, content):
        self.metadata.write_text(json.dumps({"assets": {
            name: {"sizeBytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
        }}), encoding="utf-8")

    def test_verifies_asset_inventory(self):
        self.assertEqual(verify_assets(self.metadata, self.package), {"assets": 1, "verified": True})

    def test_detects_corrupt_asset(self):
        self.asset.write_bytes(b"tampered-data")
        with self.assertRaisesRegex(ValueError, "Size mismatch|SHA-256 mismatch"):
            verify_assets(self.metadata, self.package)

    def test_detects_missing_asset(self):
        self.asset.unlink()
        with self.assertRaisesRegex(ValueError, "Missing or unsafe"):
            verify_assets(self.metadata, self.package)

    def test_rejects_path_traversal(self):
        self.write_metadata("../outside.bin", b"x")
        with self.assertRaisesRegex(ValueError, "Unsafe asset path"):
            verify_assets(self.metadata, self.package)


if __name__ == "__main__":
    unittest.main()
