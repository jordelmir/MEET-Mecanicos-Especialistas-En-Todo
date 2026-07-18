#!/usr/bin/env python3
"""Normalize DOCX extraction into conservative, review-only automotive candidates."""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path
from typing import Any

from common import (
    PIPELINE_VERSION,
    find_dtcs,
    find_measurements,
    find_prompt_injection,
    load_json,
    normalize_text,
    sha256_text,
    unique_sorted,
    write_json_atomic,
)


DOMAIN_KEYWORDS = {
    "ENGINE": ("motor", "engine", "ciguenal", "camshaft", "lubricacion"),
    "FUEL": ("combustible", "fuel", "bomba", "inyector", "presion de riel"),
    "IGNITION": ("encendido", "ignition", "bujia", "bobina"),
    "COOLING": ("refrigerante", "coolant", "radiador", "termostato"),
    "TRANSMISSION": ("transmision", "caja automatica", "gearbox", "tcm"),
    "SUSPENSION": ("suspension", "tijereta", "brazo de control", "rotula", "bieleta"),
    "STEERING": ("direccion", "steering", "mangueta", "terminal"),
    "BRAKES": ("freno", "brake", "abs", "caliper"),
    "ELECTRICAL": ("electrico", "electric", "fusible", "rele", "arnes", "conector"),
    "SENSORS_ACTUATORS": ("sensor", "actuador", "switch", "solenoide"),
    "SRS": ("srs", "airbag", "pretensor", "ocuppant", "ocupante"),
    "SEATS_RESTRAINTS": ("asiento", "seat", "isofix", "latch", "top tether", "riel"),
    "INTERIOR": ("tapiceria", "alfombra", "cielo", "moldura", "pilar"),
    "INFOTAINMENT_TELEMATICS": (
        "infotainment",
        "radio android",
        "bluetooth",
        "wifi",
        "gps",
        "dashcam",
        "telematica",
        "amplificador",
    ),
    "HV_EV": ("alta tension", "high voltage", "hibrido", "hybrid", "vehiculo electrico"),
    "HVAC": ("hvac", "aire acondicionado", "a/c", "compresor"),
    "ADAS": ("adas", "radar", "lidar", "lane", "camara frontal"),
}

RISK_KEYWORDS = {
    "SRS_CRITICAL": ("airbag", "pretensor", "srs", "conector amarillo"),
    "HIGH_VOLTAGE_CRITICAL": ("alta tension", "high voltage", "hv battery", "bateria hibrida"),
    "FUEL_FIRE": ("combustible", "gasolina", "fuel", "chispa"),
    "BRAKE_SAFETY": ("freno", "brake", "liquido de frenos"),
    "HOT_PRESSURIZED_COOLANT": ("refrigerante", "coolant", "presurizado", "caliente"),
    "STRUCTURAL_RESTRAINT": ("soldar", "riel", "isofix", "top tether", "anclaje"),
    "REMOTE_WRITE": ("can/uds", "can bus", "escritura can", "comando bidireccional"),
}

ENTITY_TERMS = {
    "SENSOR_MAF": ("maf", "sensor de flujo de aire", "mass air flow"),
    "SENSOR_MAP": ("map", "sensor de presion absoluta", "manifold absolute pressure"),
    "SENSOR_IAT": ("iat", "sensor de temperatura de aire"),
    "APP_DUAL": ("app dual", "pedal electronico dual", "accelerator pedal position"),
    "O2_WIDEBAND_AFR": ("wideband", "afr", "banda ancha"),
    "FUEL_RAIL_PRESSURE_SENSOR": ("sensor de presion de riel", "fuel rail pressure sensor"),
    "DPF": ("dpf", "filtro de particulas diesel"),
    "NOX_SENSOR": ("nox", "sensor nox"),
    "BOOST_VGT": ("boost", "vgt", "turbo de geometria variable"),
    "OCS": ("ocs", "occupant classification", "clasificacion de ocupante"),
    "EPB": ("epb", "freno de estacionamiento electrico"),
    "EPS": ("eps", "direccion electrica"),
    "ADAS": ("adas", "lane keep", "radar frontal"),
    "FUEL_PUMP": ("bomba de combustible", "fuel pump"),
    "LOWER_CONTROL_ARM": ("tijereta", "brazo inferior", "lower control arm"),
}

ALIAS_CANONICAL_IDS = {
    "tijereta": "lower_control_arm",
    "tijera": "lower_control_arm",
    "trapecio": "lower_control_arm",
    "brazo inferior": "lower_control_arm",
    "rotula": "ball_joint",
    "bieleta": "sway_bar_link",
    "mangueta": "steering_knuckle",
    "munon": "steering_knuckle",
}

ABSENCE_PATTERNS = (
    re.compile(r"\bno\s+(?:tiene|usa|utiliza|equipa|lleva|incorpora|dispone de)\b"),
    re.compile(r"\bausente\b"),
    re.compile(r"\bno\s+existe\b"),
    re.compile(r"\bnot\s+(?:present|equipped|used)\b"),
)
NOT_DOCUMENTED_PATTERNS = (
    re.compile(r"\bno\s+(?:esta\s+)?documentad[oa]\b"),
    re.compile(r"\bno\s+(?:se\s+)?(?:muestra|confirma|documenta)\b"),
    re.compile(r"\bnot\s+documented\b"),
)
PRESENCE_PATTERNS = (
    re.compile(r"\b(?:tiene|usa|utiliza|equipa|lleva|incorpora|dispone de)\b"),
    re.compile(r"\bpresente\b"),
    re.compile(r"\bse\s+documenta\b"),
    re.compile(r"\b(?:is|uses)\s+(?:present|equipped|used)\b"),
)
REFERENCE_PATTERNS = (
    re.compile(r"\breferencia\b"),
    re.compile(r"\bejemplo\b"),
    re.compile(r"\breference vehicle\b"),
    re.compile(r"\bsolo como comparacion\b"),
)
YEAR_PATTERN = re.compile(r"\b(?:19|20)\d{2}(?:\s*[-/]\s*(?:19|20)?\d{2})?\b")


def keyword_matches(normalized: str, mapping: dict[str, tuple[str, ...]]) -> list[str]:
    matches = []
    for category, terms in mapping.items():
        def term_matches(term: str) -> bool:
            normalized_term = normalize_text(term)
            if len(normalized_term) <= 4 and normalized_term.isalnum():
                return re.search(rf"(?<![a-z0-9]){re.escape(normalized_term)}(?![a-z0-9])", normalized) is not None
            return normalized_term in normalized

        if any(term_matches(term) for term in terms):
            matches.append(category)
    return matches


def entity_mentions(normalized: str) -> list[str]:
    return keyword_matches(normalized, ENTITY_TERMS)


def alias_matches(normalized: str) -> list[dict[str, str]]:
    return [
        {"alias": alias, "canonicalId": canonical_id}
        for alias, canonical_id in ALIAS_CANONICAL_IDS.items()
        if normalize_text(alias) in normalized
    ]


def applicability_polarity(normalized: str, entities: list[str]) -> str:
    if not entities:
        return "UNKNOWN"
    if any(pattern.search(normalized) for pattern in ABSENCE_PATTERNS):
        return "ABSENT"
    if any(pattern.search(normalized) for pattern in NOT_DOCUMENTED_PATTERNS):
        return "NOT_DOCUMENTED"
    if any(pattern.search(normalized) for pattern in PRESENCE_PATTERNS):
        return "PRESENT"
    return "UNKNOWN"


def _bounded_term_pattern(term: str) -> str:
    normalized_term = normalize_text(term)
    return rf"(?<![a-z0-9]){re.escape(normalized_term)}(?![a-z0-9])"


def entity_applicability_polarities(
    normalized: str,
    entities: list[str],
) -> dict[str, str]:
    """Resolve presence per entity so nearby text about another system cannot flip it."""
    clauses = [part.strip() for part in re.split(r"[.!?;\n]+", normalized) if part.strip()]
    result: dict[str, str] = {}
    verbs = r"(?:tiene|usa|utiliza|equipa|lleva|incorpora|dispone(?:\s+de)?|presenta)"

    for entity in entities:
        term_patterns = [_bounded_term_pattern(term) for term in ENTITY_TERMS[entity]]
        signals: set[str] = set()
        for clause in clauses:
            matching_terms = [pattern for pattern in term_patterns if re.search(pattern, clause)]
            if not matching_terms:
                continue

            for term_pattern in matching_terms:
                for term_match in re.finditer(term_pattern, clause):
                    preceding = clause[max(0, term_match.start() - 80) : term_match.start()]
                    verb_relations = list(
                        re.finditer(
                            rf"(?P<negative>\bno\s+)?(?:se\s+)?{verbs}\b",
                            preceding,
                        )
                    )
                    if verb_relations:
                        relation = verb_relations[-1]
                        signals.add(
                            "ABSENT" if relation.group("negative") else "PRESENT"
                        )
                if (
                    re.search(rf"\bno\s+(?:se\s+)?{verbs}\b.{{0,80}}{term_pattern}", clause)
                    or re.search(rf"\bno\s+{term_pattern}", clause)
                    or re.search(rf"{term_pattern}.{{0,40}}\bausente\b", clause)
                ):
                    signals.add("ABSENT")
                if (
                    re.search(rf"\bno\s+(?:esta\s+)?documentad[oa]\b.{{0,80}}{term_pattern}", clause)
                    or re.search(rf"\bno\s+(?:se\s+)?(?:muestra|confirma|documenta)\b.{{0,80}}{term_pattern}", clause)
                    or re.search(rf"{term_pattern}.{{0,40}}\bno\s+(?:esta\s+)?documentad[oa]\b", clause)
                ):
                    signals.add("NOT_DOCUMENTED")
                if (
                    re.search(rf"{term_pattern}.{{0,40}}\bpresente\b", clause)
                    or re.search(rf"\bse\s+documenta\b.{{0,80}}{term_pattern}", clause)
                ):
                    signals.add("PRESENT")

            clause_entities = entity_mentions(clause)
            if not signals and clause_entities == [entity]:
                fallback = applicability_polarity(clause, [entity])
                if fallback != "UNKNOWN":
                    signals.add(fallback)

        if len(signals) == 1:
            result[entity] = next(iter(signals))
        else:
            result[entity] = "UNKNOWN"
    return result


def vehicle_scope_mentions(text: str) -> list[str]:
    normalized = normalize_text(text)
    mentions: list[str] = []
    known_scopes = (
        "hyundai accent",
        "hyundai verna",
        "accent verna",
        "accent lc",
        "accent 2005",
        "verna 2005",
    )
    mentions.extend(scope for scope in known_scopes if scope in normalized)
    mentions.extend(match.group(0) for match in YEAR_PATTERN.finditer(text))
    return unique_sorted(mentions)


def normalize_candidate(
    block: dict[str, Any],
    text: str,
    row_index: int | None,
) -> dict[str, Any] | None:
    normalized = normalize_text(text)
    if not normalized:
        return None

    dtcs = find_dtcs(text)
    measurements = find_measurements(text)
    domains = keyword_matches(normalized, DOMAIN_KEYWORDS)
    risks = keyword_matches(normalized, RISK_KEYWORDS)
    entities = entity_mentions(normalized)
    aliases = alias_matches(normalized)
    vehicle_scopes = vehicle_scope_mentions(text)
    vehicle_scope_binding = "UNSCOPED"
    if vehicle_scopes:
        vehicle_scope_binding = (
            "AMBIGUOUS_TABLE_CONTEXT" if row_index is not None else "EXPLICIT_TEXT"
        )
    prompt_findings = find_prompt_injection(text)
    is_reference = any(pattern.search(normalized) for pattern in REFERENCE_PATTERNS)
    entity_polarities = entity_applicability_polarities(normalized, entities)
    resolved_polarities = {
        value for value in entity_polarities.values() if value != "UNKNOWN"
    }
    polarity = next(iter(resolved_polarities)) if len(resolved_polarities) == 1 else "UNKNOWN"

    has_review_signal = any(
        (dtcs, measurements, domains, risks, entities, aliases, vehicle_scopes, prompt_findings)
    )
    if not has_review_signal:
        return None

    row_suffix = f"_row_{row_index:04d}" if row_index is not None else ""
    candidate_id = f"candidate_{block['order']:06d}{row_suffix}_{sha256_text(text)[:12]}"
    for index, measurement in enumerate(measurements, start=1):
        measurement["candidateId"] = f"{candidate_id}_measurement_{index:03d}"

    candidate_kinds = []
    if measurements:
        candidate_kinds.append("MEASUREMENT_SPEC_CANDIDATE")
    if risks:
        candidate_kinds.append("SAFETY_POLICY_CANDIDATE")
    if entities or vehicle_scopes or polarity != "UNKNOWN":
        candidate_kinds.append("APPLICABILITY_CLAIM_CANDIDATE")
    if dtcs:
        candidate_kinds.append("DTC_KNOWLEDGE_CANDIDATE")
    if aliases:
        candidate_kinds.append("ALIAS_CANDIDATE")
    if not candidate_kinds:
        candidate_kinds.append("TECHNICAL_CLAIM_CANDIDATE")

    default_applicability = {
        "ABSENT": "ABSENT_DOCUMENTED",
        "NOT_DOCUMENTED": "UNKNOWN_INSUFFICIENT_EVIDENCE",
        "PRESENT": "VERIFY_PHYSICALLY",
    }.get(polarity, "UNKNOWN_INSUFFICIENT_EVIDENCE")
    if is_reference:
        default_applicability = "REFERENCE_VEHICLE_ONLY"
    review_status = "REJECTED_SECURITY" if prompt_findings else "PENDING_TECHNICAL_REVIEW"
    return {
        "candidateId": candidate_id,
        "sourceBlockId": block["blockId"],
        "sourceRowIndex": row_index,
        "sourceOrder": block["order"],
        "sourceTextHash": sha256_text(text),
        "sectionPath": block.get("sectionPath", []),
        "sourceKind": "table_row" if row_index is not None else block["kind"],
        "originalText": text,
        "candidateKinds": candidate_kinds,
        "domains": domains,
        "riskFlags": risks,
        "dtcs": dtcs,
        "entityMentions": entities,
        "entityApplicabilityPolarities": entity_polarities,
        "aliasMatches": aliases,
        "vehicleScopeMentions": vehicle_scopes,
        "vehicleScopeBinding": vehicle_scope_binding,
        "applicabilityPolarity": polarity,
        "defaultApplicability": default_applicability,
        "confidence": "UNVERIFIED",
        "sourceAuthority": "UNKNOWN",
        "measurements": measurements,
        "securityFindings": prompt_findings,
        "reviewStatus": review_status,
        "autoPublishAllowed": False,
        "requiresHumanReview": True,
    }


def normalize_extraction(extraction: dict[str, Any]) -> dict[str, Any]:
    candidates: list[dict[str, Any]] = []
    domain_counts: Counter[str] = Counter()
    risk_counts: Counter[str] = Counter()

    for block in extraction.get("blocks", []):
        if block.get("kind") == "table" and block.get("rows"):
            units = [
                (" | ".join(str(cell) for cell in row).strip(), index)
                for index, row in enumerate(block["rows"], start=1)
            ]
        else:
            units = [(block.get("text", ""), None)]

        for text, row_index in units:
            candidate = normalize_candidate(block, text, row_index)
            if candidate is None:
                continue
            candidates.append(candidate)
            domain_counts.update(candidate["domains"])
            risk_counts.update(candidate["riskFlags"])

    return {
        "schemaVersion": 1,
        "pipelineVersion": PIPELINE_VERSION,
        "sourceDocument": extraction["document"],
        "statistics": {
            "sourceBlockCount": extraction.get("statistics", {}).get("blockCount", 0),
            "candidateCount": len(candidates),
            "securityRejectedCount": sum(
                candidate["reviewStatus"] == "REJECTED_SECURITY" for candidate in candidates
            ),
            "measurementCandidateCount": sum(len(candidate["measurements"]) for candidate in candidates),
            "domainCounts": dict(sorted(domain_counts.items())),
            "riskCounts": dict(sorted(risk_counts.items())),
        },
        "candidates": candidates,
        "normalizationPolicy": {
            "allCandidatesDefaultToUnverified": True,
            "referenceExamplesAreNeverTransferred": True,
            "numericValuesRequireSourceReview": True,
            "autoPublishAllowed": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("extraction", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    normalized = normalize_extraction(load_json(args.extraction))
    write_json_atomic(args.output, normalized)
    print(
        f"Normalized {normalized['statistics']['candidateCount']} review candidates from "
        f"{normalized['sourceDocument']['sourceFileName']}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
