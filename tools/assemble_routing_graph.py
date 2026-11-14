#!/usr/bin/env python3
"""Verify and optionally assemble a manifest-indexed Valhalla graph archive."""
from __future__ import annotations
import argparse, hashlib, json, os, tempfile
from pathlib import Path

def verify_or_assemble(manifest_path: Path, parts_dir: Path, output: Path | None = None):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1 or manifest.get("format") != "concatenated-tar":
        raise ValueError("Unsupported routing manifest schema or archive format.")
    archive, parts = manifest.get("archive"), manifest.get("parts")
    if not isinstance(archive, dict) or not isinstance(parts, list) or not parts:
        raise ValueError("Manifest must contain archive metadata and a non-empty parts list.")
    if archive.get("file") != "tiles.tar":
        raise ValueError("Unexpected archive filename.")
    limit = manifest.get("partSizeLimitBytes")
    if limit != 1_900_000_000:
        raise ValueError("Manifest part-size limit must be exactly 1,900,000,000 bytes.")
    total_size, archive_hash = 0, hashlib.sha256()
    temporary_path = temporary_stream = None
    try:
        if output is not None:
            output.parent.mkdir(parents=True, exist_ok=True)
            fd, name = tempfile.mkstemp(prefix=f".{output.name}.", suffix=".tmp", dir=output.parent)
            temporary_path, temporary_stream = Path(name), os.fdopen(fd, "wb")
        for expected_index, part in enumerate(parts, start=1):
            if not isinstance(part, dict) or part.get("index") != expected_index:
                raise ValueError("Part indexes must be contiguous and ordered from 1.")
            filename = part.get("file")
            if not isinstance(filename, str) or Path(filename).name != filename or not filename.startswith("tiles.tar.part-"):
                raise ValueError(f"Unsafe or invalid part filename: {filename!r}")
            expected_size, expected_hash = part.get("sizeBytes"), part.get("sha256")
            if not isinstance(expected_size, int) or expected_size <= 0 or expected_size > limit:
                raise ValueError(f"Invalid declared size for {filename}.")
            if not isinstance(expected_hash, str) or len(expected_hash) != 64:
                raise ValueError(f"Invalid SHA-256 for {filename}.")
            path = parts_dir / filename
            if not path.is_file():
                raise ValueError(f"Missing graph part: {filename}")
            part_hash, part_size = hashlib.sha256(), 0
            with path.open("rb") as source:
                while True:
                    chunk = source.read(1024 * 1024)
                    if not chunk: break
                    part_hash.update(chunk); archive_hash.update(chunk); part_size += len(chunk)
                    if temporary_stream is not None: temporary_stream.write(chunk)
            if part_size != expected_size:
                raise ValueError(f"Size mismatch for {filename}: expected {expected_size}, got {part_size}.")
            if part_hash.hexdigest() != expected_hash:
                raise ValueError(f"SHA-256 mismatch for {filename}.")
            total_size += part_size
        if total_size != archive.get("sizeBytes"):
            raise ValueError(f"Archive size mismatch: expected {archive.get('sizeBytes')}, got {total_size}.")
        if archive_hash.hexdigest() != archive.get("sha256"):
            raise ValueError("Reassembled archive SHA-256 does not match the manifest.")
        if temporary_stream is not None:
            temporary_stream.flush(); os.fsync(temporary_stream.fileno()); temporary_stream.close()
            temporary_stream = None
            os.replace(temporary_path, output); temporary_path = None
        return {"parts": len(parts), "sizeBytes": total_size, "sha256": archive_hash.hexdigest()}
    finally:
        if temporary_stream is not None: temporary_stream.close()
        if temporary_path is not None: temporary_path.unlink(missing_ok=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--parts-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, help="Assemble to this path; omit to verify only")
    args = parser.parse_args()
    result = verify_or_assemble(args.manifest, args.parts_dir, args.output)
    action = f"Assembled {args.output}" if args.output else "Verified parts without writing an archive"
    print(f"{action}: {result['parts']} parts, {result['sizeBytes']} bytes, SHA-256 {result['sha256']}")

if __name__ == "__main__":
    main()
