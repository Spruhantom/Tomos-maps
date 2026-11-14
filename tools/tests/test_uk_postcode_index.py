import csv,json,subprocess,sys,tempfile,unittest,zipfile
from pathlib import Path
SCRIPT=Path(__file__).resolve().parents[1]/"build-uk-postcode-index.py"
class UkPostcodeIndexTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name)
        self.csv=self.root/"onspd.csv"
        with self.csv.open("w",newline="",encoding="utf-8") as f:
            w=csv.DictWriter(f,fieldnames=["PCDS","LAT","LONG","DOTERM"]); w.writeheader()
            w.writerow({"PCDS":"L1 1AA","LAT":"53.405","LONG":"-2.99","DOTERM":""})
            w.writerow({"PCDS":"BT1 1AA","LAT":"54.60","LONG":"-5.93","DOTERM":""})
            w.writerow({"PCDS":"M1 1AE","LAT":"53.48","LONG":"-2.24","DOTERM":"202001"})
            w.writerow({"PCDS":"INVALID","LAT":"53.4","LONG":"-2.9","DOTERM":""})
        self.evidence=self.root/"licence-review.txt"; self.evidence.write_text("Test-only authorisation evidence.")
        self.output=self.root/"uk-search.json"
    def tearDown(self): self.temp.cleanup()
    def run_builder(self,*extra):
        return subprocess.run([sys.executable,str(SCRIPT),"--source",str(self.csv),"--output",str(self.output),
            "--licence-evidence",str(self.evidence),*extra],capture_output=True,text=True)
    def test_requires_explicit_redistribution_authorisation(self):
        result=self.run_builder(); self.assertNotEqual(result.returncode,0); self.assertIn("Redistribution not authorised",result.stderr)
    def test_builds_uk_index_with_both_gb_and_ni(self):
        result=self.run_builder("--redistribution-authorized"); self.assertEqual(result.returncode,0,result.stderr)
        data=json.loads(self.output.read_text()); meta=json.loads(Path(str(self.output)+".metadata.json").read_text())
        self.assertEqual(data["formatVersion"],1); self.assertEqual(len(data["records"]),2)
        self.assertEqual(meta["coverage"]["greatBritainRecords"],1); self.assertEqual(meta["coverage"]["northernIrelandRecords"],1)
        self.assertTrue(meta["redistributionAuthorised"])
    def test_refuses_gb_only_dataset(self):
        with self.csv.open("w",newline="",encoding="utf-8") as f:
            w=csv.DictWriter(f,fieldnames=["PCDS","LAT","LONG","DOTERM"]); w.writeheader()
            w.writerow({"PCDS":"L1 1AA","LAT":"53.405","LONG":"-2.99","DOTERM":""})
        result=self.run_builder("--redistribution-authorized")
        self.assertNotEqual(result.returncode,0); self.assertIn("No Northern Ireland postcodes",result.stderr)
    def test_reads_zip_source(self):
        archive=self.root/"onspd.zip"
        with zipfile.ZipFile(archive,"w") as z: z.write(self.csv,"Data/ONSPD.csv")
        result=subprocess.run([sys.executable,str(SCRIPT),"--source",str(archive),"--output",str(self.output),
            "--licence-evidence",str(self.evidence),"--redistribution-authorized"],capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
if __name__=="__main__": unittest.main()
