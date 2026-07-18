#!/usr/bin/env python3
"""Build a deterministic review queue from normalized candidates and conflicts."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
from typing import Any

from common import PIPELINE_VERSION, load_json, write_json_atomic


def candidate_priority(candidate: dict[str, Any]) -> str:
    if candidate.get("securityFindings"):
        return "P0_SECURITY"
    if candidate.get("riskFlags"):
        return "P1_SAFETY"
    if candidate.get("measurements"):
        return "P1_MEASUREMENT"
    if candidate.get("applicabilityPolarity") in {"PRESENT", "ABSENT", "NOT_DOCUMENTED"}:
        return "P2_APPLICABILITY"
    if candidate.get("dtcs"):
        return "P2_DIAGNOSTIC"
    return "P3_NORMALIZATION"


def required_roles(candidate: dict[str, Any]) -> list[str]:
    roles = {"technical_reviewer"}
    if candidate.get("riskFlags"):
        roles.add("safety_reviewer")
    if candidate.get("measurements"):
        roles.add("source_verifier")
    if candidate.get("vehicleScopeMentions") or candidate.get("entityMentions"):
        roles.add("vehicle_applicability_reviewer")
    if candidate.get("securityFindings"):
        roles.add("security_reviewer")
    return sorted(roles)


def build_review_queue(
    normalized_documents: list[dict[str, Any]],
    contradiction_report: dict[str, Any],
) -> dict[str, Any]:
    items: list[dict[str, Any]] = []

    for document in normalized_documents:
        source_name = document["sourceDocument"]["sourceFileName"]
        source_hash = document["sourceDocument"]["sourceSha256"]
        for candidate in document.get("candidates", []):
            priority = candidate_priority(candidate)
            items.append(
                {
                    "reviewItemId": candidate["candidateId"],
                    "itemType": "KNOWLEDGE_CANDIDATE",
                    "priority": priority,
                    "status": candidate["reviewStatus"],
                    "sourceDocument": source_name,
                    "sourceDocumentSha256": source_hash,
                    "sourceBlockId": candidate["sourceBlockId"],
                    "sourceTextHash": candidate["sourceTextHash"],
                    "sectionPath": candidate.get("sectionPath", []),
                    "excerpt": candidate["originalText"][:800],
                    "candidateKinds": candidate["candidateKinds"],
                    "domains": candidate["domains"],
                    "riskFlags": candidate["riskFlags"],
                    "dtcs": candidate["dtcs"],
                    "entityMentions": candidate["entityMentions"],
                    "vehicleScopeMentions": candidate["vehicleScopeMentions"],
                    "measurementCount": len(candidate["measurements"]),
                    "requiredRoles": required_roles(candidate),
                    "publishable": False,
                    "requiredAction": (
                        "REJECT_AND_SECURITY_REVIEW"
                        if priority == "P0_SECURITY"
                        else "VERIFY_SOURCE_SCOPE_APPLICABILITY_AND_SAFETY"
                    ),
                }
            )

    for conflict in contradiction_report.get("conflicts", []):
        items.append(
            {
                "reviewItemId": conflict["conflictId"],
                "itemType": "KNOWLEDGE_CONFLICT",
                "priority": "P1_CONFLICT",
                "status": "OPEN",
                "entity": conflict["entity"],
                "vehicleScope": conflict["vehicleScope"],
                "presentCandidates": [
                    candidate["candidateId"] for candidate in conflict["presentCandidates"]
                ],
                "absentCandidates": [
                    candidate["candidateId"] for candidate in conflict["absentCandidates"]
                ],
                "requiredRoles": ["technical_reviewer", "vehicle_applicability_reviewer"],
                "publishable": False,
                "requiredAction": conflict["requiredAction"],
            }
        )

    priority_order = {
        "P0_SECURITY": 0,
        "P1_SAFETY": 1,
        "P1_CONFLICT": 2,
        "P1_MEASUREMENT": 3,
        "P2_APPLICABILITY": 4,
        "P2_DIAGNOSTIC": 5,
        "P3_NORMALIZATION": 6,
    }
    items.sort(key=lambda item: (priority_order[item["priority"]], item["reviewItemId"]))
    priority_counts = Counter(item["priority"] for item in items)
    status_counts = Counter(item["status"] for item in items)

    return {
        "schemaVersion": 1,
        "pipelineVersion": PIPELINE_VERSION,
        "statistics": {
            "reviewItemCount": len(items),
            "publishableCount": 0,
            "priorityCounts": dict(sorted(priority_counts.items())),
            "statusCounts": dict(sorted(status_counts.items())),
        },
        "items": items,
        "publicationPolicy": {
            "autoPublishAllowed": False,
            "aiCanApprove": False,
            "criticalAuthorCanSelfApprove": False,
            "signedPackRequiredAfterApproval": True,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("normalized", type=Path, nargs="+")
    parser.add_argument("--contradictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    queue = build_review_queue(
        [load_json(path) for path in args.normalized],
        load_json(args.contradictions),
    )
    write_json_atomic(args.output, queue)
    print(f"Built review queue with {queue['statistics']['reviewItemCount']} items.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
