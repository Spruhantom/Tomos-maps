import csv
import tempfile
import unittest
import zipfile
from pathlib import Path

import importlib.util

SCRIPT = Path(__file__).resolve().parents[1] / "prepare-codepoint-open.py"
SPEC = importlib.util.spec_from_file_location("prepare_codepoint_open", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PrepareCodePointOpenTest(unittest.TestCase):
    def test_transforms_and_filters_liverpool_postcodes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "code-point-open.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr(
                    "Data/CSV/L.csv",
                    "L1 1AA,10,335000,390000,00,00,00\n"
                    "M1 1AE,10,384000,398000,00,00,00\n"
                )
            output = root / "liverpool.csv"
            count = MODULE.prepare(
                source, output,
                west=-3.4, south=53.2, east=-2.5, north=53.7,
            )
            self.assertEqual(count, 1)
            with output.open(encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(rows[0]["postcode"], "L1 1AA")
            self.assertEqual(rows[0]["label"], "L1 1AA")
            self.assertTrue(-3.4 <= float(rows[0]["longitude"]) <= -2.5)
            self.assertTrue(53.2 <= float(rows[0]["latitude"]) <= 53.7)


if __name__ == "__main__":
    unittest.main()
