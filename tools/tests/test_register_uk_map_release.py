import hashlib, json, sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from register_uk_map_release import register

class RegisterUkMapReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.catalogue = self.root / "uk.json"
        self.asset = self.root / "united-kingdom.pmtiles"
        self.catalogue.write_text(json.dumps({
            "schemaVersion": 1, "catalogueVersion": "0.1.0",
            "coverage": {"country": "GB", "status": "catalogue-ready-data-pending"},
            "packages": []
        }), encoding="utf-8")
        self.asset.write_bytes(b"verified test archive")

    def tearDown(self):
        self.temp.cleanup()

    def test_registers_checksum_size_and_release_url(self):
        package = register(self.catalogue, self.asset, "2026.11.14.1",
                           "uk-map-data-2026.11.14.1", "Spruhantom/Tomos-maps")
        data = json.loads(self.catalogue.read_text(encoding="utf-8"))
        self.assertEqual(package["layer"], "map")
        self.assertEqual(package["asset"]["sha256"], hashlib.sha256(self.asset.read_bytes()).hexdigest())
        self.assertEqual(package["asset"]["sizeBytes"], self.asset.stat().st_size)
        self.assertIn("/releases/download/", package["asset"]["url"])
        self.assertEqual(data["catalogueVersion"], "0.1.1")
        self.assertEqual(data["coverage"]["country"], "GB")

    def test_rejects_duplicate_version(self):
        register(self.catalogue, self.asset, "2026.11.14.1",
                 "uk-map-data-2026.11.14.1", "Spruhantom/Tomos-maps")
        with self.assertRaises(ValueError):
            register(self.catalogue, self.asset, "2026.11.14.1",
                     "uk-map-data-2026.11.14.1", "Spruhantom/Tomos-maps")

    def test_rejects_invalid_catalogue_version(self):
        data = json.loads(self.catalogue.read_text(encoding="utf-8"))
        data["catalogueVersion"] = "dev"
        self.catalogue.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(ValueError):
            register(self.catalogue, self.asset, "2026.11.14.1",
                     "uk-map-data-2026.11.14.1", "Spruhantom/Tomos-maps")

if __name__ == "__main__":
    unittest.main()
