#!/usr/bin/env python3
"""Fail-closed structural validation for the generated CycloneDX release SBOM."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

SHA256 = re.compile(r"^[a-f0-9]{64}$")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("sbom", type=Path)
    args = parser.parse_args()
    payload = json.loads(args.sbom.read_text(encoding="utf-8"))
    if payload.get("bomFormat") != "CycloneDX" or payload.get("specVersion") != "1.5":
        raise SystemExit("invalid CycloneDX 1.5 identity")
    application = payload.get("metadata", {}).get("component", {})
    if not application.get("version") or not application.get("bom-ref"):
        raise SystemExit("SBOM application version/bom-ref missing")
    components = payload.get("components")
    dependencies = payload.get("dependencies")
    if not isinstance(components, list) or not components:
        raise SystemExit("SBOM contains no resolved dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        raise SystemExit("SBOM dependency graph missing")
    refs = {item.get("bom-ref") for item in components}
    for component in components:
        hashes = component.get("hashes", [])
        if component.get("scope") != "required" or not component.get("licenses"):
            raise SystemExit(f"scope/license missing for {component.get('bom-ref')}")
        if not any(item.get("alg") == "SHA-256" and SHA256.fullmatch(item.get("content", "")) for item in hashes):
            raise SystemExit(f"SHA-256 missing for {component.get('bom-ref')}")
    app_edges = next((item for item in dependencies if item.get("ref") == application.get("bom-ref")), None)
    if app_edges is None or set(app_edges.get("dependsOn", [])) != refs:
        raise SystemExit("application dependency relationship is incomplete")
    print("CycloneDX SBOM structure: PASSED")


if __name__ == "__main__":
    main()
