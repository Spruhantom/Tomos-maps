#!/usr/bin/env python3
"""Build a publisher-signed catalogue for independently downloadable layers."""
from __future__ import annotations
import argparse, base64, hashlib, json, re, subprocess, tempfile
from pathlib import Path
from urllib.parse import urljoin, urlparse

def https_base(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Base URL must be HTTPS without credentials, query or fragment.")
    return value.rstrip("/") + "/"

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""): h.update(block)
    return h.hexdigest()

def sign(path: Path, key: Path, signature: Path) -> str:
    with tempfile.TemporaryDirectory(prefix="tomos-layer-sign-") as tmp:
        sig, pub = Path(tmp)/"sig.bin", Path(tmp)/"pub.der"
        subprocess.run(["openssl","pkeyutl","-sign","-rawin","-inkey",str(key),"-in",str(path),"-out",str(sig)],check=True)
        subprocess.run(["openssl","pkey","-in",str(key),"-pubout","-outform","DER","-out",str(pub)],check=True)
        der=pub.read_bytes(); prefix=bytes.fromhex("302a300506032b6570032100")
        if len(der)!=len(prefix)+32 or not der.startswith(prefix): raise ValueError("Publisher key must be Ed25519.")
        signature.parent.mkdir(parents=True,exist_ok=True)
        signature.write_text(base64.b64encode(sig.read_bytes()).decode("ascii")+"\n",encoding="ascii")
        return base64.b64encode(der).decode("ascii")

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--package-metadata",action="append",type=Path,required=True)
    p.add_argument("--base-url",required=True)
    p.add_argument("--output",type=Path,required=True)
    p.add_argument("--private-key",type=Path)
    p.add_argument("--signature-output",type=Path)
    a=p.parse_args()
    try:
        base=https_base(a.base_url); packages=[]
        for path in a.package_metadata:
            m=json.loads(path.read_text(encoding="utf-8"))
            required=("id","areaId","areaName","layer","version","manifestFile","manifestSha256","archiveFile","archiveSha256","archiveSizeBytes","installedSizeBytes")
            missing=[key for key in required if key not in m]
            if missing: raise ValueError(f"{path}: missing {', '.join(missing)}")
            if m["layer"] not in {"MAP","ROUTING","SEARCH"}: raise ValueError(f"{path}: invalid layer.")
            for key in ("manifestSha256","archiveSha256"):
                if not re.fullmatch(r"[a-fA-F0-9]{64}",str(m[key])): raise ValueError(f"{path}: invalid {key}.")
            if not re.fullmatch(r"[a-z0-9][a-z0-9._-]*",str(m["id"])) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]*",str(m["areaId"])):
                raise ValueError(f"{path}: invalid package/area ID.")
            for key in ("manifestFile","archiveFile"):
                if Path(str(m[key])).name != str(m[key]): raise ValueError(f"{path}: filenames must not contain paths.")
            manifest=path.parent/str(m["manifestFile"]); archive=path.parent/str(m["archiveFile"])
            if not manifest.is_file() or not archive.is_file(): raise ValueError(f"{path}: missing archive or manifest.")
            if digest(manifest)!=str(m["manifestSha256"]).lower() or digest(archive)!=str(m["archiveSha256"]).lower():
                raise ValueError(f"{path}: built file checksum mismatch.")
            if archive.stat().st_size!=int(m["archiveSizeBytes"]): raise ValueError(f"{path}: archive size mismatch.")
            packages.append({
                "id":m["id"],"areaId":m["areaId"],"areaName":m["areaName"],"layer":m["layer"],
                "version":m["version"],"manifestUrl":urljoin(base,str(m["manifestFile"])),
                "manifestSha256":str(m["manifestSha256"]).lower(),"archiveUrl":urljoin(base,str(m["archiveFile"])),
                "archiveSha256":str(m["archiveSha256"]).lower(),"archiveSizeBytes":int(m["archiveSizeBytes"]),
                "installedSizeBytes":int(m["installedSizeBytes"]),
            })
        if not packages or len(packages)>2000: raise ValueError("Catalogue must contain 1 to 2,000 layer packages.")
        if len({x["id"] for x in packages})!=len(packages): raise ValueError("Duplicate package IDs.")
        if len({(x["areaId"],x["layer"]) for x in packages})!=len(packages): raise ValueError("Only one version per area/layer is allowed.")
        payload=(json.dumps({"formatVersion":1,"packages":packages},ensure_ascii=False,separators=(",",":"))+"\n").encode()
        if len(payload)>4*1024*1024: raise ValueError("Catalogue exceeds 4 MiB.")
        a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_bytes(payload)
        if a.private_key:
            sig=a.signature_output or Path(str(a.output)+".sig")
            public=sign(a.output,a.private_key,sig)
            print(f"Signed catalogue: {a.output}\nSignature: {sig}\nPinned Ed25519 public key (base64): {public}")
        else: print(f"Unsigned catalogue prepared: {a.output}; provide --private-key to sign.")
    except (OSError,ValueError,KeyError,json.JSONDecodeError,subprocess.CalledProcessError) as error: p.error(str(error))
    return 0

if __name__=="__main__": raise SystemExit(main())
