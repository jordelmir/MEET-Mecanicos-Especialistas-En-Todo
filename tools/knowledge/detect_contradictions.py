#!/usr/bin/env python3
"""Detect potential applicability contradictions without resolving them silently."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
from typing import Any

from common import PIPELINE_VERSION, load_json, normalize_text, sha256_text, write_json_atomic


def detect_contradictions(
    normalized_documents: list[dict[str, Any]],
    default_vehicle_scope: str,
) -> dict[str, Any]:
    grouped: dict[tuple[str, str], dict[str, list[dict[str, Any]]]] = defaultdict(
        lambda: {"PRESENT": [], "ABSENT": []}
    )
    unscoped_claim_count = 0

    for document in normalized_documents:
        source_name = document["sourceDocument"]["sourceFileName"]
        for candidate in document.get("candidates", []):
            scopes = candidate.get("vehicleScopeMentions", [])
            scope_binding = candidate.get("vehicleScopeBinding")
            entity_polarities = candidate.get("entityApplicabilityPolarities", {})
            for entity in candidate.get("entityMentions", []):
                polarity = entity_polarities.get(
                    entity,
                    candidate.get("applicabilityPolarity", "UNKNOWN"),
                )
                if polarity not in {"PRESENT", "ABSENT"}:
                    continue
                if not scopes or (
                    scope_binding is not None and scope_binding != "EXPLICIT_TEXT"
                ):
                    unscoped_claim_count += 1
                    continue
                for scope in scopes:
                    grouped[(entity, normalize_text(scope))][polarity].append(
                        {
                            "candidateId": candidate["candidateId"],
                            "sourceDocument": source_name,
                            "sourceBlockId": candidate["sourceBlockId"],
                            "sectionPath": candidate.get("sectionPath", []),
                            "excerpt": candidate["originalText"][:600],
                        }
                    )

    conflicts: list[dict[str, Any]] = []
    for (entity, scope), polarities in sorted(grouped.items()):
        if not polarities["PRESENT"] or not polarities["ABSENT"]:
            continue
        claim_ids = sorted(
            item["candidateId"]
            for polarity in ("PRESENT", "ABSENT")
            for item in polarities[polarity]
        )
        conflict_seed = f"{entity}|{scope}|{'|'.join(claim_ids)}"
        conflicts.append(
            {
                "conflictId": f"conflict_{sha256_text(conflict_seed)[:16]}",
                "type": "PRESENCE_ABSENCE_CONFLICT",
                "entity": entity,
                "vehicleScope": scope,
                "presentCandidates": polarities["PRESENT"],
                "absentCandidates": polarities["ABSENT"],
                "status": "OPEN",
                "resolution": None,
                "autoResolutionAllowed": False,
                "requiredAction": "VERIFY_SCOPE_SOURCE_VARIANT_AND_MARKET",
            }
        )

    return {
        "schemaVersion": 1,
        "pipelineVersion": PIPELINE_VERSION,
        "defaultVehicleScope": default_vehicle_scope,
        "statistics": {
            "conflictCount": len(conflicts),
            "unscopedClaimCount": unscoped_claim_count,
        },
        "conflicts": conflicts,
        "policy": {
            "silentResolutionAllowed": False,
            "referenceVehicleWins": False,
            "defaultScopeAppliedToUnscopedClaims": False,
            "humanTechnicalReviewRequired": True,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("normalized", type=Path, nargs="+")
    parser.add_argument("--default-vehicle-scope", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    documents = [load_json(path) for path in args.normalized]
    result = detect_contradictions(documents, args.default_vehicle_scope)
    write_json_atomic(args.output, result)
    print(f"Detected {result['statistics']['conflictCount']} potential contradictions.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
