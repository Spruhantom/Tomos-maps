import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from assemble_routing_graph import verify_or_assemble


class RoutingArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.parts = self.root / "parts"
        self.parts.mkdir()
        self.payloads = [b"first-part-", b"second-part"]
        entries = []
        for index, payload in enumerate(self.payloads, start=1):
            name = f"tiles.tar.part-{index - 1:04d}"
            (self.parts / name).write_bytes(payload)
            entries.append({
                "index": index,
                "file": name,
                "sizeBytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            })
        archive = b"".join(self.payloads)
        self.manifest = self.root / "manifest.json"
        self.manifest.write_text(json.dumps({
            "schemaVersion": 1,
            "format": "concatenated-tar",
            "partSizeLimitBytes": 1_900_000_000,
            "archive": {
                "file": "tiles.tar",
                "sizeBytes": len(archive),
                "sha256": hashlib.sha256(archive).hexdigest(),
            },
            "parts": entries,
        }), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_verify_and_assemble(self):
        output = self.root / "tiles.tar"
        result = verify_or_assemble(self.manifest, self.parts, output)
        self.assertEqual(result["parts"], 2)
        self.assertEqual(output.read_bytes(), b"".join(self.payloads))

    def test_detects_corrupt_part(self):
        (self.parts / "tiles.tar.part-0000").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "Size mismatch|SHA-256 mismatch"):
            verify_or_assemble(self.manifest, self.parts)

    def test_detects_missing_part(self):
        (self.parts / "tiles.tar.part-0001").unlink()
        with self.assertRaisesRegex(ValueError, "Missing graph part"):
            verify_or_assemble(self.manifest, self.parts)

    def test_rejects_out_of_order_manifest(self):
        data = json.loads(self.manifest.read_text(encoding="utf-8"))
        data["parts"][1]["index"] = 3
        self.manifest.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "contiguous and ordered"):
            verify_or_assemble(self.manifest, self.parts)


if __name__ == "__main__":
    unittest.main()
