#!/usr/bin/env python3
"""Bounded release checks. Never print credentials or raw artifact strings."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--aapt", type=Path, required=True)
parser.add_argument("--local-properties", type=Path, required=True)
args = parser.parse_args()
properties = {}
for line in args.local_properties.read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        name, value = line.split("=", 1)
        properties[name.strip()] = value.strip()
private_names = ("CAR2DB_API_KEY", "MINIMAX_API_KEY_DEBUG", "KEYSTORE_PASSWORD", "KEY_PASSWORD")
private_values = {name: properties[name].encode() for name in private_names if len(properties.get(name, "")) >= 8}
badging = subprocess.run([str(args.aapt), "dump", "badging", str(args.apk)], check=True, capture_output=True).stdout
findings = []
if b"application-debuggable" in badging:
    findings.append("DEBUGGABLE_MANIFEST")
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        if not (name.endswith((".dex", ".so")) or name.startswith(("assets/", "res/raw/"))):
            continue
        payload = archive.read(name)
        for key, value in private_values.items():
            if value in payload:
                findings.append("EMBEDDED_PRIVATE_VALUE:" + key)
        if re.search(rb"sb_secret_[A-Za-z0-9_-]{15,}", payload):
            findings.append("SUPABASE_SECRET_KEY_MARKER")
        if re.search(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", payload):
            findings.append("PRIVATE_PEM_REQUIRES_REVIEW")
report = {
    "artifact": args.apk.name,
    "sha256": hashlib.file_digest(args.apk.open("rb"), "sha256").hexdigest(),
    "findings": sorted(set(findings)),
    "scope": "Manifest and bounded secret scan of DEX, native libraries, assets and raw resources; not a complete security audit.",
}
print(json.dumps(report, indent=2))
raise SystemExit(1 if findings else 0)
