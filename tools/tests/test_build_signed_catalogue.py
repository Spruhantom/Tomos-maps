import json
import hashlib
import base64
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

    def test_signs_catalogue_with_supplied_ed25519_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata = root / "region.metadata.json"
            manifest = root / "region.manifest.json"
            archive = root / "region.zip"
            manifest.write_bytes(b"test-manifest")
            archive.write_bytes(b"test-archive")
            metadata.write_text(json.dumps({
                "id": "region-test",
                "name": "Test Region",
                "version": "1.0.0",
                "manifestFile": manifest.name,
                "archiveFile": archive.name,
                "manifestSha256": hashlib.sha256(manifest.read_bytes()).hexdigest(),
                "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                "estimatedSizeBytes": archive.stat().st_size,
            }), encoding="utf-8")
            private_key = root / "publisher.pem"
            subprocess.run([
                "openssl", "genpkey", "-algorithm", "Ed25519",
                "-out", str(private_key),
            ], check=True, capture_output=True)
            output = root / "catalogue.json"
            result = subprocess.run([
                sys.executable, str(SCRIPT),
                "--package-metadata", str(metadata),
                "--base-url", "https://maps.example.test/regions/",
                "--output", str(output),
                "--private-key", str(private_key),
            ], check=True, capture_output=True, text=True)
            public_line = next(line for line in result.stdout.splitlines()
                               if line.startswith("Pinned public key (base64): "))
            public_key = base64.b64decode(public_line.split(": ", 1)[1])
            signature = base64.b64decode(Path(str(output) + ".sig").read_text().strip())
            public_der = root / "publisher-public.der"
            signature_bin = root / "catalogue.sig"
            self.assertTrue(public_key.startswith(bytes.fromhex("302a300506032b6570032100")))
            public_der.write_bytes(public_key)
            signature_bin.write_bytes(signature)
            subprocess.run([
                "openssl", "pkeyutl", "-verify", "-rawin", "-pubin",
                "-inkey", str(public_der), "-keyform", "DER",
                "-in", str(output), "-sigfile", str(signature_bin),
            ], check=True, capture_output=True)

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
