#!/usr/bin/env python3
"""Generate MEET proof status from CI step outcomes and emitted artifacts.

This file intentionally has no path that accepts a human-authored PASS claim.
GitHub provides each step outcome; artifact hashes are calculated from bytes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str | None:
    if not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_outcome(value: str) -> str:
    normalized = value.strip().lower()
    return {
        "success": "PASSED",
        "failure": "FAILED",
        "cancelled": "CANCELLED",
        "skipped": "SKIPPED",
    }.get(normalized, "NOT_REPORTED")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--gate", action="append", default=[])
    parser.add_argument("--artifact", action="append", default=[])
    args = parser.parse_args()

    gates: dict[str, str] = {}
    for item in args.gate:
        name, separator, outcome = item.partition("=")
        if not separator or not name.strip():
            raise SystemExit(f"invalid --gate: {item}")
        gates[name.strip()] = normalize_outcome(outcome)

    artifacts: dict[str, dict[str, object]] = {}
    for item in args.artifact:
        name, separator, raw_path = item.partition("=")
        if not separator or not name.strip():
            raise SystemExit(f"invalid --artifact: {item}")
        path = Path(raw_path)
        artifacts[name.strip()] = {
            "path": raw_path,
            "present": path.is_file(),
            "sizeBytes": path.stat().st_size if path.is_file() else None,
            "sha256": sha256(path),
        }

    mandatory = [value for value in gates.values()]
    artifact_proof_complete = bool(artifacts) and all(item["present"] and item["sha256"] for item in artifacts.values())
    proof_state = (
        "VERIFIED"
        if mandatory and all(value == "PASSED" for value in mandatory) and artifact_proof_complete
        else "NOT_VERIFIED"
    )
    payload = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "generator": "tools/vehicle-truth/generate-proof-status.py",
        "source": {
            "versionName": args.version_name,
            "versionCode": args.version_code,
            "commit": args.commit,
        },
        "verificationState": proof_state,
        "ciRun": args.run_url,
        "gates": gates,
        "artifacts": artifacts,
        "hardwareConformance": "PENDING_PHYSICAL_CORPUS",
        "calibrationAuthority": "PENDING_SIGNED_REVIEWED_DATASET",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
