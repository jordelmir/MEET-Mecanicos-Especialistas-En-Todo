#!/usr/bin/env python3
"""Fail closed unless a differential OBD report proves every safety invariant."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

ZERO_INVARIANTS = (
    "inventedDtcCount",
    "falseCleanCount",
    "wrongEcuCount",
    "wrongStatusCount",
    "wrongSnapshotOwnerCount",
    "crossVehicleContamination",
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    payload = json.loads(args.report.read_text(encoding="utf-8"))
    if payload.get("corpusState") != "CERTIFIED":
        raise SystemExit("conformance denied: physical corpus is not CERTIFIED")
    if int(payload.get("certifiedCaseCount", 0)) < 1:
        raise SystemExit("conformance denied: no certified physical cases")
    violations = {key: payload.get(key) for key in ZERO_INVARIANTS if payload.get(key) != 0}
    if violations:
        raise SystemExit(f"conformance denied: {violations}")
    print("OBD differential conformance: PASSED")


if __name__ == "__main__":
    main()
