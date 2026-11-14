#!/usr/bin/env python3
"""Build a UK postcode index from an authorised ONSPD CSV/ZIP.
Requires explicit redistribution approval and evidence; public access is not permission.
"""
from __future__ import annotations
import argparse, csv, hashlib, io, json, math, re, tempfile, zipfile
from pathlib import Path
POSTCODE_RE=re.compile(r"^(GIR0AA|[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2})$",re.I)
MAX_RECORDS=2_000_000
MAX_BYTES=256*1024*1024

def normalise(value):
    compact="".join(value.upper().split())
    if not POSTCODE_RE.fullmatch(compact): return None
    return compact if compact=="GIR0AA" else compact[:-3]+" "+compact[-3:]

def csv_stream(source):
    if source.suffix.casefold()==".zip":
        archive=zipfile.ZipFile(source)
        candidates=[n for n in archive.namelist() if n.casefold().endswith(".csv") and not n.endswith("/")]
        if not candidates:
            archive.close(); raise ValueError("ZIP contains no CSV files.")
        for name in sorted(candidates):
            text=io.TextIOWrapper(archive.open(name),encoding="utf-8-sig",newline="")
            reader=csv.DictReader(text)
            fields={f.strip().upper() for f in (reader.fieldnames or [])}
            if ({"PCDS","LAT","LONG"}.issubset(fields) or
                {"POSTCODE","LATITUDE","LONGITUDE"}.issubset(fields)):
                return archive,reader
            text.close()
        archive.close()
        raise ValueError("No CSV has ONSPD PCDS/LAT/LONG or postcode/latitude/longitude columns.")
    stream=source.open("r",encoding="utf-8-sig",newline="")
    return stream,csv.DictReader(stream)

def find_field(fields,aliases,required=True):
    lookup={f.strip().upper():f for f in fields}
    for alias in aliases:
        if alias in lookup: return lookup[alias]
    if required: raise ValueError("Missing required CSV column; expected "+", ".join(aliases))
    return None

def sha256(path):
    digest=hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b""): digest.update(chunk)
    return digest.hexdigest()

def build(source,output,evidence,authorised,licence,attribution):
    if not authorised: raise ValueError("Redistribution not authorised; complete legal review and explicit approval first.")
    if not evidence.is_file() or evidence.stat().st_size==0: raise ValueError("A non-empty licence-evidence file is required.")
    if not licence.strip() or not attribution.strip(): raise ValueError("Explicit licence and attribution text are required.")
    if not source.is_file() or source.stat().st_size==0: raise ValueError("Source CSV/ZIP is missing or empty.")
    holder,reader=csv_stream(source); seen=set(); count=ni_count=gb_count=0; temp_path=None
    output.parent.mkdir(parents=True,exist_ok=True)
    try:
        with tempfile.NamedTemporaryFile("w",encoding="utf-8",newline="",delete=False,dir=output.parent,prefix=output.name+".") as temp:
            temp_path=Path(temp.name); temp.write('{"formatVersion":1,"records":['); first=True
            fields=reader.fieldnames or []
            pc=find_field(fields,("PCDS","POSTCODE")); lat_col=find_field(fields,("LAT","LATITUDE"))
            lon_col=find_field(fields,("LONG","LON","LONGITUDE"))
            term=find_field(fields,("DOTERM","DATE_TERMINATED"),False)
            for row in reader:
                if term and (row.get(term) or "").strip(): continue
                postcode=normalise((row.get(pc) or "").strip())
                if not postcode or postcode in seen: continue
                try: lat,lon=float(row[lat_col]),float(row[lon_col])
                except (TypeError,ValueError): continue
                if not (math.isfinite(lat) and math.isfinite(lon) and -90<=lat<=90 and -180<=lon<=180) or (lat==0 and lon==0): continue
                seen.add(postcode)
                if postcode.startswith("BT"): ni_count+=1
                else: gb_count+=1
                record={"id":f"postcode-{count+1}","label":postcode,"latitude":lat,"longitude":lon,"postcode":postcode,"category":"postcode"}
                if not first: temp.write(",")
                temp.write(json.dumps(record,ensure_ascii=False,separators=(",",":"))); first=False; count+=1
                if count>MAX_RECORDS: raise ValueError("Index exceeds the app's two-million-record limit.")
            temp.write("]}\n")
        if count==0: raise ValueError("No valid current postcode records found.")
        if ni_count==0: raise ValueError("No Northern Ireland postcodes detected; refusing to label a GB-only index UK-wide.")
        if gb_count==0: raise ValueError("No Great Britain postcodes detected; refusing to label an NI-only index UK-wide.")
        size=temp_path.stat().st_size
        if size>MAX_BYTES: raise ValueError(f"Index is {size:,} bytes; exceeds app's 256 MiB limit.")
        temp_path.replace(output)
    except Exception:
        if temp_path and temp_path.exists(): temp_path.unlink()
        raise
    finally: holder.close()
    metadata={"schemaVersion":1,"id":"uk-postcode-search","layer":"search","coverage":{"country":"GB","name":"United Kingdom","greatBritainRecords":gb_count,"northernIrelandRecords":ni_count},
    "source":"ONS Postcode Directory (user-supplied authorised source)","sourceFile":source.name,"sourceSha256":sha256(source),
    "licence":licence,"attribution":attribution,
    "licenceEvidenceSha256":sha256(evidence),"redistributionAuthorised":True,"format":"tomos-search-json-v1","records":count,
    "asset":{"file":output.name,"sizeBytes":size,"sha256":sha256(output)},"appIntegration":"not-enabled"}
    output.with_suffix(output.suffix+".metadata.json").write_text(json.dumps(metadata,indent=2)+"\n",encoding="utf-8")
    return metadata

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",required=True,type=Path,help="Authorised ONSPD CSV or ZIP")
    parser.add_argument("--output",required=True,type=Path)
    parser.add_argument("--licence-evidence",required=True,type=Path)
    parser.add_argument("--licence",required=True,help="Exact licence/permission basis confirmed by legal review")
    parser.add_argument("--attribution",required=True,help="Required source attribution text")
    parser.add_argument("--redistribution-authorized",action="store_true",help="Attest that legal review permits redistribution of derived postcode coordinates")
    args=parser.parse_args()
    try: result=build(args.source,args.output,args.licence_evidence,args.redistribution_authorized,args.licence,args.attribution)
    except (OSError,ValueError,zipfile.BadZipFile) as error: parser.error(str(error))
    print(f"Built UK postcode index: {result['records']:,} records (GB {result['coverage']['greatBritainRecords']:,}, NI {result['coverage']['northernIrelandRecords']:,}).")
    print("No app integration or publication was performed.")
    return 0
if __name__=="__main__": raise SystemExit(main())
