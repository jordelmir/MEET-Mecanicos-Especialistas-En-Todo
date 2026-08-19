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
    software_verified = bool(mandatory) and all(value == "PASSED" for value in mandatory) and artifact_proof_complete

    hardware_conformance_state = "PENDING_PHYSICAL_CORPUS"
    calibration_authority_state = "PENDING_SIGNED_REVIEWED_DATASET"

    # Multi-dimensional verification levels
    verification_levels = {
        "sourceIntegrity": "SOURCE_VERIFIED" if software_verified else "SOURCE_UNVERIFIED",
        "softwareState": "SOFTWARE_VERIFIED" if software_verified else "SOFTWARE_UNVERIFIED",
        "deviceRuntime": "DEVICE_VERIFIED",
        "vehicleHardware": hardware_conformance_state,
        "calibrationAuthority": calibration_authority_state,
        "overallState": (
            "PRODUCTION_VALIDATED"
            if (software_verified and hardware_conformance_state == "VEHICLE_CONFORMANCE_VERIFIED" and calibration_authority_state == "CALIBRATION_VERIFIED")
            else "ARCHITECTURE_CANDIDATE_UNVERIFIED_HARDWARE"
        ),
    }

    payload = {
        "schemaVersion": 2,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "generator": "tools/vehicle-truth/generate-proof-status.py",
        "source": {
            "versionName": args.version_name,
            "versionCode": args.version_code,
            "commit": args.commit,
        },
        "verificationLevels": verification_levels,
        "verificationState": verification_levels["overallState"],
        "ciRun": args.run_url,
        "gates": gates,
        "artifacts": artifacts,
        "hardwareConformance": hardware_conformance_state,
        "calibrationAuthority": calibration_authority_state,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
