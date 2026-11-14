import hashlib, json, subprocess, sys, tempfile, unittest, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGER = ROOT / "package-custom-area-layer.py"
CATALOGUE = ROOT / "build-signed-layer-catalogue.py"

class OfflineLayerPackageBuildTest(unittest.TestCase):
    def test_builds_independent_layer_archive_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); source=root/"source"; output=root/"dist"
            (source/"routing").mkdir(parents=True)
            (source/"routing"/"tiles.tar").write_bytes(b"valhalla-tiles")
            (source/"routing"/"admins.sqlite").write_bytes(b"admin-db")
            subprocess.run([sys.executable,str(PACKAGER),"--input-dir",str(source),"--output-dir",str(output),
                "--area-id","liverpool-city","--area-name","Liverpool City","--layer","ROUTING","--version","1.0.0",
                "--west","-3.2","--south","53.2","--east","-2.6","--north","53.6"],check=True,capture_output=True)
            metadata=json.loads(next(output.glob("*.metadata.json")).read_text())
            manifest_path=output/metadata["manifestFile"]
            archive_path=output/metadata["archiveFile"]
            manifest=json.loads(manifest_path.read_text())
            self.assertEqual(manifest["layer"],"ROUTING")
            self.assertEqual({x["path"] for x in manifest["files"]},{"routing/tiles.tar","routing/admins.sqlite"})
            self.assertEqual(metadata["installedSizeBytes"],sum(x["sizeBytes"] for x in manifest["files"]))
            self.assertEqual(metadata["archiveSha256"],hashlib.sha256(archive_path.read_bytes()).hexdigest())
            with zipfile.ZipFile(archive_path) as z:
                self.assertIsNone(z.testzip())
                self.assertEqual(set(z.namelist()),{"routing/tiles.tar","routing/admins.sqlite"})

    def test_catalogue_rejects_missing_metadata_or_invalid_base(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); metadata=root/"bad.json"; metadata.write_text("{}")
            result=subprocess.run([sys.executable,str(CATALOGUE),"--package-metadata",str(metadata),
                "--base-url","http://invalid.example","--output",str(root/"catalogue.json")],
                capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)

    def test_packager_rejects_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); source=root/"source"; source.mkdir()
            target=root/"target"; target.write_text("outside")
            (source/"escape").symlink_to(target)
            result=subprocess.run([sys.executable,str(PACKAGER),"--input-dir",str(source),"--output-dir",str(root/"out"),
                "--area-id","liverpool-city","--area-name","Liverpool","--layer","SEARCH","--version","1.0.0",
                "--west","-3.2","--south","53.2","--east","-2.6","--north","53.6"],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)

if __name__=="__main__": unittest.main()
