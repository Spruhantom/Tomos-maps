import json
import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "build-signed-catalogue.py"


class SignedCatalogueBuilderTest(unittest.TestCase):
    def test_prepares_catalogue_from_package_metadata_without_private_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata = root / "liverpool.metadata.json"
            manifest_file = root / "liverpool.manifest.json"
            archive_file = root / "liverpool.zip"
            manifest_file.write_bytes(b"manifest")
            archive_file.write_bytes(b"archive-bytes")
            metadata.write_text(json.dumps({
                "id": "liverpool-city-region",
                "name": "Liverpool City Region",
                "version": "2026.11.1",
                "manifestFile": manifest_file.name,
                "archiveFile": archive_file.name,
                "manifestSha256": hashlib.sha256(manifest_file.read_bytes()).hexdigest(),
                "archiveSha256": hashlib.sha256(archive_file.read_bytes()).hexdigest(),
                "estimatedSizeBytes": archive_file.stat().st_size,
            }), encoding="utf-8")
            output = root / "catalogue.json"
            result = subprocess.run([
                sys.executable, str(SCRIPT),
                "--package-metadata", str(metadata),
                "--base-url", "https://maps.example.test/regions/",
                "--output", str(output),
            ], check=True, capture_output=True, text=True)
            catalogue = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(catalogue["formatVersion"], 1)
            self.assertEqual(catalogue["packages"][0]["manifestUrl"],
                             "https://maps.example.test/regions/liverpool.manifest.json")
            self.assertEqual(
                catalogue["packages"][0]["archiveSha256"],
                hashlib.sha256(archive_file.read_bytes()).hexdigest(),
            )
            self.assertIn("No signature was created", result.stdout)
            self.assertFalse(Path(str(output) + ".sig").exists())

    def test_rejects_non_https_package_host(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata = root / "package.json"
            metadata.write_text("{}", encoding="utf-8")
            result = subprocess.run([
                sys.executable, str(SCRIPT),
                "--package-metadata", str(metadata),
                "--base-url", "http://maps.example.test/",
                "--output", str(root / "catalogue.json"),
            ], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn("must be HTTPS", result.stderr)


if __name__ == "__main__":
    unittest.main()
