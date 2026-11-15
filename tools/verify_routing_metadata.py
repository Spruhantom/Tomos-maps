#!/usr/bin/env python3
"""Verify the size and SHA-256 inventory in routing package metadata."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath


def verify_assets(metadata_path: Path, package_root: Path) -> dict:
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    assets = metadata.get("assets")
    if not isinstance(assets, dict) or not assets:
        raise ValueError("Metadata must contain a non-empty assets object.")

    root = package_root.resolve()
    for relative_name, expected in sorted(assets.items()):
        if not isinstance(relative_name, str):
            raise ValueError("Asset paths must be strings.")
        relative = PurePosixPath(relative_name)
        if relative.is_absolute() or not relative.parts or any(p in ("", ".", "..") for p in relative.parts):
            raise ValueError(f"Unsafe asset path: {relative_name!r}")
        if not isinstance(expected, dict):
            raise ValueError(f"Invalid asset metadata for {relative_name}.")
        size_expected = expected.get("sizeBytes")
        hash_expected = expected.get("sha256")
        if not isinstance(size_expected, int) or size_expected <= 0:
            raise ValueError(f"Invalid declared size for {relative_name}.")
        if not isinstance(hash_expected, str) or len(hash_expected) != 64:
            raise ValueError(f"Invalid declared SHA-256 for {relative_name}.")

        path = root.joinpath(*relative.parts)
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError(f"Missing or unsafe asset: {relative_name}")
        digest = hashlib.sha256()
        size = 0
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
                size += len(chunk)
        if size != size_expected:
            raise ValueError(f"Size mismatch for {relative_name}: expected {size_expected}, got {size}.")
        if digest.hexdigest() != hash_expected:
            raise ValueError(f"SHA-256 mismatch for {relative_name}.")
    return {"assets": len(assets), "verified": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--package-root", type=Path, required=True)
    args = parser.parse_args()
    result = verify_assets(args.metadata, args.package_root)
    print(f"Verified {result['assets']} routing package assets (size and SHA-256).")


if __name__ == "__main__":
    main()
