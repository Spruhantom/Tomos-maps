#!/usr/bin/env python3
"""Validate the public TomOS Maps data catalogue without downloading assets."""
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

CATALOGUE = Path(sys.argv[1] if len(sys.argv) > 1 else "data/catalogue/uk.json")


def fail(message: str) -> None:
    raise SystemExit(f"catalogue validation failed: {message}")


def main() -> None:
    try:
        data = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(str(exc))
    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if data.get("coverage", {}).get("country") != "GB":
        fail("catalogue coverage must be GB")
    if data.get("hosting", {}).get("catalogue") != "github-pages":
        fail("catalogue hosting must be github-pages")
    attribution = data.get("attribution", {})
    if not attribution.get("required") or "OpenStreetMap" not in attribution.get("text", ""):
        fail("OpenStreetMap attribution must be retained")
    packages = data.get("packages")
    if not isinstance(packages, list):
        fail("packages must be an array")
    seen = set()
    for package in packages:
        package_id = package.get("id")
        if not isinstance(package_id, str) or not package_id or package_id in seen:
            fail("each package needs a unique non-empty id")
        seen.add(package_id)
        if package.get("layer") not in {"map", "routing", "search"}:
            fail(f"{package_id}: unsupported layer")
        if package.get("coverage", {}).get("country") != "GB":
            fail(f"{package_id}: package coverage must declare country GB")
        asset = package.get("asset", {})
        url = asset.get("url", "")
        if urlparse(url).scheme != "https" or "github.com/" not in url:
            fail(f"{package_id}: asset URL must be an HTTPS GitHub release URL")
        if not re.fullmatch(r"[a-fA-F0-9]{64}", asset.get("sha256", "")):
            fail(f"{package_id}: asset.sha256 must be 64 hexadecimal characters")
        if not isinstance(asset.get("sizeBytes"), int) or asset["sizeBytes"] <= 0:
            fail(f"{package_id}: asset.sizeBytes must be a positive integer")
        if not package.get("licence") or not package.get("source"):
            fail(f"{package_id}: source and licence are required")
    print(f"Validated {CATALOGUE}: {len(packages)} package(s)")


if __name__ == "__main__":
    main()
