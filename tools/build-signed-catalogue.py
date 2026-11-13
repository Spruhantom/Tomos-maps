#!/usr/bin/env python3
"""Assemble and optionally Ed25519-sign a TomOS regional package catalogue.

Private keys are supplied by the publisher at release time and are never
created, copied or committed by this tool. OpenSSL 3 is required for signing.
"""
from __future__ import annotations

import argparse
import base64
import json
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import urljoin, urlparse

CATALOGUE_FORMAT_VERSION = 1


def require_https_base(url: str) -> str:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        raise ValueError("Package base URL must be HTTPS and must not contain credentials.")
    if parsed.query or parsed.fragment:
        raise ValueError("Package base URL must not contain a query or fragment.")
    return url.rstrip("/") + "/"


def descriptor_from_metadata(metadata_path: Path, base_url: str) -> dict[str, object]:
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    required = (
        "id", "name", "version", "manifestFile", "archiveFile",
        "manifestSha256", "archiveSha256", "estimatedSizeBytes",
    )
    missing = [key for key in required if key not in metadata]
    if missing:
        raise ValueError(f"{metadata_path}: missing metadata fields: {', '.join(missing)}")
    for key in ("manifestSha256", "archiveSha256"):
        value = metadata[key]
        if not isinstance(value, str) or len(value) != 64 or any(c not in "0123456789abcdefABCDEF" for c in value):
            raise ValueError(f"{metadata_path}: invalid {key}")
    size = int(metadata["estimatedSizeBytes"])
    if size <= 0 or size > 20 * 1024 * 1024 * 1024:
        raise ValueError(f"{metadata_path}: invalid estimated archive size")
    manifest_file = Path(str(metadata["manifestFile"])).name
    archive_file = Path(str(metadata["archiveFile"])).name
    return {
        "id": str(metadata["id"]),
        "name": str(metadata["name"]),
        "version": str(metadata["version"]),
        "manifestUrl": urljoin(base_url, manifest_file),
        "manifestSha256": metadata["manifestSha256"].lower(),
        "estimatedSizeBytes": size,
        "archiveUrl": urljoin(base_url, archive_file),
        "archiveSha256": metadata["archiveSha256"].lower(),
    }


def sign_catalogue(catalogue_path: Path, private_key: Path, signature_path: Path) -> str:
    with tempfile.TemporaryDirectory(prefix="tomos-catalogue-sign-") as temporary:
        signature_bin = Path(temporary) / "catalogue.sig"
        public_der = Path(temporary) / "public.der"
        subprocess.run([
            "openssl", "pkeyutl", "-sign", "-rawin",
            "-inkey", str(private_key),
            "-in", str(catalogue_path),
            "-out", str(signature_bin),
        ], check=True)
        subprocess.run([
            "openssl", "pkey", "-in", str(private_key),
            "-pubout", "-outform", "DER", "-out", str(public_der),
        ], check=True)
        der = public_der.read_bytes()
        ed25519_spki_prefix = bytes.fromhex("302a300506032b6570032100")
        if not der.startswith(ed25519_spki_prefix) or len(der) != len(ed25519_spki_prefix) + 32:
            raise ValueError("The supplied PEM key is not an Ed25519 private key.")
        public_key_base64 = base64.b64encode(der[-32:]).decode("ascii")
        signature_path.write_text(base64.b64encode(signature_bin.read_bytes()).decode("ascii") + "\n", encoding="ascii")
        return public_key_base64


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-metadata", action="append", type=Path, required=True,
                        help="Metadata JSON from package-regional-data.py; repeat for each region")
    parser.add_argument("--base-url", required=True,
                        help="HTTPS directory URL where the manifest and ZIP files will be hosted")
    parser.add_argument("--output", required=True, type=Path, help="Output catalogue JSON path")
    parser.add_argument("--private-key", type=Path,
                        help="Publisher Ed25519 private key PEM; omitted to prepare an unsigned catalogue")
    parser.add_argument("--signature-output", type=Path,
                        help="Signature output path (default: <catalogue>.sig)")
    args = parser.parse_args()

    try:
        base_url = require_https_base(args.base_url)
        packages = [descriptor_from_metadata(path, base_url) for path in args.package_metadata]
        if not packages or len(packages) > 500:
            raise ValueError("Catalogue must contain between 1 and 500 packages.")
        ids = [package["id"] for package in packages]
        if len(set(ids)) != len(ids):
            raise ValueError("Duplicate package IDs are not allowed.")
        catalogue = {"formatVersion": CATALOGUE_FORMAT_VERSION, "packages": packages}
        payload = (json.dumps(catalogue, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        if len(payload) > 4 * 1024 * 1024:
            raise ValueError("Catalogue exceeds the 4 MiB app limit.")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(payload)

        if args.private_key:
            signature_path = args.signature_output or Path(str(args.output) + ".sig")
            public_key = sign_catalogue(args.output, args.private_key, signature_path)
            print(f"Signed catalogue: {args.output}")
            print(f"Signature:        {signature_path}")
            print(f"Pinned public key (base64): {public_key}")
            print("Copy only the public key into RegionalPackageBuildConfig.kt. Keep the private key offline.")
        else:
            print(f"Unsigned catalogue prepared: {args.output}")
            print("No signature was created. Supply --private-key when the publisher key is ready.")
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
