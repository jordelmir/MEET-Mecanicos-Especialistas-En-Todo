#!/usr/bin/env python3
"""Validate a MEET knowledge pack against JSON Schema and semantic safety rules."""

from __future__ import annotations

import argparse
import math
import re
from collections import defaultdict
from pathlib import Path
from typing import Any

from common import find_prompt_injection, load_json, write_json_atomic


def resolve_ref(root_schema: dict[str, Any], reference: str) -> dict[str, Any]:
    if not reference.startswith("#/"):
        raise ValueError(f"Only local schema references are supported: {reference}")
    value: Any = root_schema
    for segment in reference[2:].split("/"):
        value = value[segment.replace("~1", "/").replace("~0", "~")]
    return value


def type_matches(value: Any, expected: str) -> bool:
    if expected == "object":
        return isinstance(value, dict)
    if expected == "array":
        return isinstance(value, list)
    if expected == "string":
        return isinstance(value, str)
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "null":
        return value is None
    raise ValueError(f"Unsupported schema type: {expected}")


def validate_schema_value(
    value: Any,
    schema: dict[str, Any],
    root_schema: dict[str, Any],
    path: str = "$",
) -> list[str]:
    if "$ref" in schema:
        return validate_schema_value(value, resolve_ref(root_schema, schema["$ref"]), root_schema, path)

    errors: list[str] = []
    expected_types = schema.get("type")
    if expected_types is not None:
        if isinstance(expected_types, str):
            expected_types = [expected_types]
        if not any(type_matches(value, expected) for expected in expected_types):
            return [f"{path}: expected type {expected_types}, got {type(value).__name__}"]

    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: value {value!r} is not in enum")

    if isinstance(value, str):
        if len(value) < schema.get("minLength", 0):
            errors.append(f"{path}: string is shorter than minLength")
        pattern = schema.get("pattern")
        if pattern and not re.fullmatch(pattern, value):
            errors.append(f"{path}: value {value!r} does not match {pattern}")

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            errors.append(f"{path}: value is below minimum {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            errors.append(f"{path}: value is above maximum {schema['maximum']}")

    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            errors.append(f"{path}: array has fewer than {schema['minItems']} items")
        if schema.get("uniqueItems"):
            serialized = [repr(item) for item in value]
            if len(serialized) != len(set(serialized)):
                errors.append(f"{path}: array items are not unique")
        item_schema = schema.get("items")
        if item_schema:
            for index, item in enumerate(value):
                errors.extend(validate_schema_value(item, item_schema, root_schema, f"{path}[{index}]"))

    if isinstance(value, dict):
        required = schema.get("required", [])
        for key in required:
            if key not in value:
                errors.append(f"{path}: missing required property {key}")
        properties = schema.get("properties", {})
        for key, child in value.items():
            if key in properties:
                errors.extend(
                    validate_schema_value(child, properties[key], root_schema, f"{path}.{key}")
                )
            elif schema.get("additionalProperties") is False:
                errors.append(f"{path}: unexpected property {key}")
    return errors


def duplicate_ids(records: list[dict[str, Any]], field: str) -> list[str]:
    seen: set[str] = set()
    duplicates: list[str] = []
    for record in records:
        value = record.get(field, "")
        if value in seen:
            duplicates.append(value)
        seen.add(value)
    return sorted(set(duplicates))


def all_strings(value: Any, path: str = "$") -> list[tuple[str, str]]:
    strings: list[tuple[str, str]] = []
    if isinstance(value, str):
        strings.append((path, value))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            strings.extend(all_strings(item, f"{path}[{index}]"))
    elif isinstance(value, dict):
        for key, item in value.items():
            strings.extend(all_strings(item, f"{path}.{key}"))
    return strings


def semantic_validation(pack: dict[str, Any]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    source_policy = pack.get("sourcePolicy", {})
    if source_policy.get("tier") == "H_REJECTED_UNKNOWN_LICENSE":
        errors.append("sourcePolicy.tier H_REJECTED_UNKNOWN_LICENSE is forbidden")
    if source_policy.get("redistributionAllowed") is False and source_policy.get("tier") != "G_EXTERNAL_LINK_ONLY":
        errors.append("non-redistributable content must use G_EXTERNAL_LINK_ONLY")

    for collection, id_field in (
        ("nodes", "id"),
        ("edges", "id"),
        ("vehicleProfiles", "profileId"),
        ("sourceCitations", "sourceId"),
        ("technicalClaims", "claimId"),
        ("measurementSpecifications", "measurementId"),
        ("knowledgeConflicts", "conflictId"),
    ):
        duplicates = duplicate_ids(pack.get(collection, []), id_field)
        if duplicates:
            errors.append(f"{collection} contains duplicate ids: {', '.join(duplicates)}")

    node_ids = {node.get("id") for node in pack.get("nodes", [])}
    for edge in pack.get("edges", []):
        if edge.get("from") not in node_ids or edge.get("to") not in node_ids:
            errors.append(f"edge {edge.get('id')} references a missing node")
        if edge.get("from") == edge.get("to"):
            errors.append(f"edge {edge.get('id')} is a self-loop")

    for profile in pack.get("profiles", []):
        ranked = profile.get("rankedCauses", [])
        if duplicate_ids(ranked, "id"):
            errors.append(f"profile {profile.get('code')} has duplicate ranked causes")
        probability_sum = sum(item.get("probability", 0) for item in ranked)
        if probability_sum > 1.000001:
            errors.append(f"profile {profile.get('code')} probabilities exceed 1.0")

    sources = {source.get("sourceId"): source for source in pack.get("sourceCitations", [])}
    for profile in pack.get("vehicleProfiles", []):
        if profile.get("yearStart", 0) > profile.get("yearEnd", 0):
            errors.append(f"vehicle profile {profile.get('profileId')} has an invalid year range")
        unknown_sources = set(profile.get("sourceCitationIds", [])) - set(sources)
        if unknown_sources:
            errors.append(
                f"vehicle profile {profile.get('profileId')} references unknown sources: "
                f"{', '.join(sorted(unknown_sources))}"
            )
        if profile.get("confidence") == "VERIFIED" and not profile.get("sourceCitationIds"):
            errors.append(f"VERIFIED vehicle profile {profile.get('profileId')} lacks sources")
    claims = {claim.get("claimId"): claim for claim in pack.get("technicalClaims", [])}
    for claim in claims.values():
        if claim.get("subjectId") not in node_ids:
            errors.append(f"claim {claim.get('claimId')} references a missing subject node")
        source_id = claim.get("sourceCitationId")
        if source_id and source_id not in sources:
            errors.append(f"claim {claim.get('claimId')} references an unknown source citation")
        if claim.get("confidence") == "VERIFIED":
            source = sources.get(source_id)
            if source is None:
                errors.append(f"VERIFIED claim {claim.get('claimId')} has no valid source citation")
            elif source.get("sourceAuthority") == "UNKNOWN":
                errors.append(f"VERIFIED claim {claim.get('claimId')} uses UNKNOWN source authority")

    for spec in pack.get("measurementSpecifications", []):
        values = [spec.get("minimumValue"), spec.get("nominalValue"), spec.get("maximumValue")]
        if all(value is None for value in values):
            errors.append(f"measurement {spec.get('measurementId')} has no numeric value")
        if spec.get("minimumValue") is not None and spec.get("maximumValue") is not None:
            if spec["minimumValue"] > spec["maximumValue"]:
                errors.append(f"measurement {spec.get('measurementId')} has an invalid range")
        source_claim_id = spec.get("sourceClaimId")
        if source_claim_id and source_claim_id not in claims:
            errors.append(
                f"measurement {spec.get('measurementId')} references an unknown source claim"
            )
        if spec.get("verificationStatus") == "VERIFIED":
            for field in ("unitCode", "measurementCondition", "requiredInstrument", "tolerance"):
                if not str(spec.get(field, "")).strip():
                    errors.append(f"VERIFIED measurement {spec.get('measurementId')} lacks {field}")
            source_claim = claims.get(source_claim_id)
            if source_claim is None or source_claim.get("confidence") != "VERIFIED":
                errors.append(
                    f"VERIFIED measurement {spec.get('measurementId')} lacks a VERIFIED source claim"
                )

    grouped_claims: dict[tuple[str, str, str], list[dict[str, Any]]] = defaultdict(list)
    for claim in claims.values():
        grouped_claims[(claim.get("subjectId", ""), claim.get("predicate", ""), claim.get("vehicleScopeId", ""))].append(claim)
    declared_conflicts = {
        frozenset(conflict.get("claimIds", [])) for conflict in pack.get("knowledgeConflicts", [])
    }
    for conflict in pack.get("knowledgeConflicts", []):
        claim_ids = conflict.get("claimIds", [])
        if not claim_ids or any(claim_id not in claims for claim_id in claim_ids):
            errors.append(
                f"conflict {conflict.get('conflictId')} references an unknown claim"
            )
    present_statuses = {
        "PRESENT_DOCUMENTED",
        "PRESENT_CONDITIONAL",
        "PRESENT_USER_VERIFIED",
        "AFTERMARKET_INSTALLED",
    }
    absent_statuses = {"ABSENT_DOCUMENTED", "NOT_APPLICABLE_ARCHITECTURE"}
    for scoped_claims in grouped_claims.values():
        has_present = any(claim.get("applicability") in present_statuses for claim in scoped_claims)
        has_absent = any(claim.get("applicability") in absent_statuses for claim in scoped_claims)
        claim_ids = frozenset(claim.get("claimId") for claim in scoped_claims)
        if has_present and has_absent and claim_ids not in declared_conflicts:
            errors.append(f"undeclared presence/absence conflict: {', '.join(sorted(claim_ids))}")

    for path, text in all_strings(pack):
        matches = find_prompt_injection(text)
        if matches:
            errors.append(f"prompt-injection candidate at {path}")

    return errors, warnings


def publication_status(pack: dict[str, Any]) -> str:
    if pack.get("sourcePolicy", {}).get("tier") == "G_EXTERNAL_LINK_ONLY":
        return "EXTERNAL_ONLY"
    requires_review = any(
        (
            pack.get("sourcePolicy", {}).get("tier") == "F_AI_GENERATED_PENDING_REVIEW",
            any(node.get("validationStatus", "VALIDATED") != "VALIDATED" for node in pack.get("nodes", [])),
            bool(pack.get("profiles"))
            and not any(
                node.get("validationStatus", "VALIDATED") == "VALIDATED"
                for node in pack.get("nodes", [])
            ),
            any(profile.get("confidence") != "VERIFIED" for profile in pack.get("vehicleProfiles", [])),
            any(
                not source.get("reviewedBy") or not source.get("reviewedAt")
                for source in pack.get("sourceCitations", [])
            ),
            any(claim.get("confidence") != "VERIFIED" for claim in pack.get("technicalClaims", [])),
            any(
                spec.get("verificationStatus") != "VERIFIED"
                for spec in pack.get("measurementSpecifications", [])
            ),
            any(
                conflict.get("status") not in {"RESOLVED", "REJECTED"}
                for conflict in pack.get("knowledgeConflicts", [])
            ),
        )
    )
    return "REVIEW_REQUIRED" if requires_review else "ACTIVE"


def validate_pack(pack: dict[str, Any], schema: dict[str, Any]) -> dict[str, Any]:
    schema_errors = validate_schema_value(pack, schema, schema)
    semantic_errors, warnings = semantic_validation(pack)
    errors = sorted(set(schema_errors + semantic_errors))
    return {
        "valid": not errors,
        "packId": pack.get("packId", "unknown"),
        "errorCount": len(errors),
        "warningCount": len(warnings),
        "publicationStatus": publication_status(pack),
        "errors": errors,
        "warnings": sorted(set(warnings)),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    parser.add_argument(
        "--schema",
        type=Path,
        default=Path(__file__).with_name("schema") / "knowledge-pack.schema.json",
    )
    parser.add_argument("--result", type=Path)
    args = parser.parse_args()

    result = validate_pack(load_json(args.pack), load_json(args.schema))
    if args.result:
        write_json_atomic(args.result, result)
    if result["valid"]:
        print(
            f"VALID {result['packId']} ({result['warningCount']} warnings, "
            f"{result['publicationStatus']})"
        )
        return 0
    print(f"INVALID {result['packId']} ({result['errorCount']} errors)")
    for error in result["errors"]:
        print(f"- {error}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
