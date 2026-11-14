import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1] / "routing-graph-parts.py"


class RoutingGraphPartsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.payload = b"TomOS England routing graph test payload"
        self.part = self.root / "tiles.tar.part-000"
        self.part.write_bytes(self.payload)
        self.manifest = self.root / "england-routing.manifest.json"
        self.write_manifest()

    def tearDown(self):
        self.temp.cleanup()

    def write_manifest(self):
        self.manifest.write_text(json.dumps({
            "schemaVersion": 1,
            "archiveName": "tiles.tar",
            "partSizeLimitBytes": 1_900_000_000,
            "archiveSizeBytes": len(self.payload),
            "archiveSha256": hashlib.sha256(self.payload).hexdigest(),
            "parts": [{
                "index": 0,
                "file": self.part.name,
                "sizeBytes": len(self.payload),
                "sha256": hashlib.sha256(self.payload).hexdigest()
            }]
        }))

    def run_tool(self, *args):
        return subprocess.run(
            [sys.executable, str(TOOL), str(self.manifest), *map(str, args)],
            capture_output=True, text=True
        )

    def test_valid_manifest_passes(self):
        result = self.run_tool()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_corrupt_part_is_rejected(self):
        self.part.write_bytes(self.payload + b"corrupt")
        result = self.run_tool()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SHA-256 mismatch", result.stderr)

    def test_missing_part_is_rejected(self):
        self.part.unlink()
        result = self.run_tool()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing graph part", result.stderr)

    def test_reordered_or_gapped_parts_are_rejected(self):
        data = json.loads(self.manifest.read_text())
        data["parts"][0]["file"] = "tiles.tar.part-001"
        self.manifest.write_text(json.dumps(data))
        result = self.run_tool()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("contiguous numeric order", result.stderr)

    def test_assemble_recreates_exact_archive(self):
        output = self.root / "restored.tar"
        result = self.run_tool("--assemble", output)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output.read_bytes(), self.payload)

    def test_full_archive_checksum_is_checked(self):
        data = json.loads(self.manifest.read_text())
        data["archiveSha256"] = "0" * 64
        self.manifest.write_text(json.dumps(data))
        result = self.run_tool()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Reassembled archive SHA-256", result.stderr)


if __name__ == "__main__":
    unittest.main()
