import csv, json, subprocess, sys, tempfile, unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "build-custom-area-search.py"

class CustomAreaSearchTest(unittest.TestCase):
    def test_generates_postcode_and_place_records_inside_bounds(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            postcodes, places, output = root/"postcodes.csv", root/"places.csv", root/"index.json"
            with postcodes.open("w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=["postcode","latitude","longitude"]); w.writeheader()
                w.writerow({"postcode":"L1 1AA","latitude":"53.405","longitude":"-2.99"})
                w.writerow({"postcode":"M1 1AE","latitude":"53.48","longitude":"-2.24"})
            with places.open("w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=["id","name","category","latitude","longitude"]); w.writeheader()
                w.writerow({"id":"poi-1","name":"Liverpool Lime Street","category":"railway","latitude":"53.407","longitude":"-2.977"})
                w.writerow({"id":"poi-2","name":"Outside","category":"place","latitude":"53.48","longitude":"-2.24"})
            subprocess.run([sys.executable,str(SCRIPT),"--postcodes",str(postcodes),"--places",str(places),
                "--output",str(output),"--west","-3.2","--south","53.2","--east","-2.6","--north","53.6"],
                check=True,capture_output=True,text=True)
            data=json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(data["formatVersion"],1)
            self.assertEqual(len(data["records"]),2)
            self.assertEqual({r["label"] for r in data["records"]},{"L1 1AA","Liverpool Lime Street"})
            self.assertEqual(data["records"][1]["id"],"poi-1")

    def test_rejects_empty_index(self):
        with tempfile.TemporaryDirectory() as tmp:
            output=Path(tmp)/"index.json"
            result=subprocess.run([sys.executable,str(SCRIPT),"--output",str(output),
                "--west","-3.2","--south","53.2","--east","-2.6","--north","53.6"],
                capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)

if __name__ == "__main__": unittest.main()
