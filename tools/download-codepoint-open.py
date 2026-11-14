#!/usr/bin/env python3
"""Download the current OS Code-Point Open GB CSV release through the public OS Downloads API."""
from __future__ import annotations

import argparse
import json
import shutil
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.os.uk/downloads/v1"


def request_json(url: str) -> object:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "TomOS-Maps-regional-data-builder/1.0", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path, help="Destination ZIP file")
    args = parser.parse_args()

    products = request_json(API + "/products")
    if not isinstance(products, list):
        raise RuntimeError("OS Downloads API returned an unexpected product list.")
    product = next(
        (
            item for item in products
            if "code-point open" in str(item.get("name", "")).casefold()
            or "codepointopen" in str(item.get("id", "")).casefold()
        ),
        None,
    )
    if product is None:
        raise RuntimeError("OS Code-Point Open was not found in the current OS OpenData catalogue.")

    product_id = urllib.parse.quote(str(product["id"]), safe="")
    query = urllib.parse.urlencode({"area": "GB", "format": "CSV"})
    downloads = request_json(f"{API}/products/{product_id}/downloads?{query}")
    if isinstance(downloads, dict):
        downloads = downloads.get("downloads", [])
    if not isinstance(downloads, list):
        raise RuntimeError("OS Downloads API returned an unexpected Code-Point Open download list.")
    candidate = next(
        (
            item for item in downloads
            if str(item.get("area", "")).upper() == "GB"
            and "csv" in str(item.get("format", "")).casefold()
            and str(item.get("url", "")).startswith("https://")
        ),
        None,
    )
    if candidate is None:
        raise RuntimeError("No GB CSV download was returned for OS Code-Point Open.")

    url = str(candidate["url"])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "TomOS-Maps-regional-data-builder/1.0"},
    )
    with urllib.request.urlopen(request, timeout=120) as response, args.output.open("wb") as output:
        shutil.copyfileobj(response, output, length=1024 * 1024)
    if args.output.stat().st_size < 1024:
        raise RuntimeError("The OS Code-Point Open download is unexpectedly small.")
    print(f"Downloaded {candidate.get('fileName', args.output.name)} to {args.output}")
    print("Source: Ordnance Survey Code-Point Open (Great Britain), OS OpenData")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
