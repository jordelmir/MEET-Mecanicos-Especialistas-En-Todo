#!/usr/bin/env python3
"""Build and verify MEET's traceable 420-element G4ED engine atlas."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = (
    REPO_ROOT
    / "android/app/src/main/assets/knowledge/g4ed/g4ed_engine_atlas.json"
)
EXPECTED_SOURCE_SHA256 = (
    "99a2dc92a2acd5364d9f85e257b382b93998065647617fed4ddd11165785a89f"
)
VEHICLE_SCOPE = "Hyundai Accent/Verna 2005 · 1.6 DOHC · automático"
ENGINE_SCOPE = "Hyundai Alpha II G4ED probable; confirm engine stamp and VIN"

SYSTEM_IDS = {
    1: "engine_structure",
    2: "crank_pistons_rods",
    3: "cylinder_head_combustion",
    4: "dohc_valvetrain",
    5: "timing",
    6: "lubrication",
    7: "cooling",
    8: "air_intake",
    9: "crankcase_ventilation",
    10: "fuel_injection",
    11: "ignition",
    12: "engine_sensors",
    13: "exhaust_emissions",
    14: "alternator",
    15: "starter",
    16: "accessories",
    17: "automatic_transmission_coupling",
    18: "powertrain_mounts",
    19: "electronic_control",
    20: "gaskets_seals",
}

INTEGRATED_FEATURE_ORDINALS = {
    *range(2, 11),
    28,
    29,
    30,
    62,
    63,
    64,
    65,
    66,
    67,
    68,
    69,
    73,
    80,
    81,
    82,
    94,
    95,
    96,
    97,
    104,
    119,
    120,
    149,
    150,
    151,
    152,
    191,
    192,
    199,
    204,
    205,
    207,
    216,
    217,
    228,
    291,
    292,
    293,
    294,
    295,
    296,
    297,
    298,
    299,
    300,
    301,
    302,
    307,
    308,
    309,
    317,
    318,
    319,
    320,
    321,
    322,
    323,
    324,
    325,
    326,
    327,
    329,
    330,
    331,
    332,
    333,
    334,
    335,
    336,
    337,
    338,
    339,
    340,
    361,
    394,
    395,
    396,
    397,
}
REFERENCE_MARK_ORDINALS = {119, 120}
CONDITIONAL_ORDINALS = {106, 107, 108, 109}

PARENT_ORDINALS = {
    **{ordinal: 1 for ordinal in range(2, 15)},
    20: 19,
    25: 24,
    26: 24,
    28: 27,
    29: 27,
    30: 27,
    **{ordinal: 61 for ordinal in range(62, 70)},
    73: 61,
    74: 73,
    80: 78,
    81: 79,
    82: 78,
    94: 92,
    95: 93,
    96: 92,
    97: 93,
    104: 103,
    106: 78,
    107: 106,
    108: 107,
    109: 106,
    119: 111,
    120: 112,
    127: 126,
    128: 126,
    129: 126,
    130: 126,
    131: 126,
    132: 131,
    133: 131,
    134: 131,
    139: 138,
    149: 27,
    150: 61,
    151: 103,
    152: 19,
    **{ordinal: 153 for ordinal in range(154, 160)},
    163: 162,
    175: 174,
    176: 174,
    177: 174,
    191: 190,
    192: 190,
    193: 190,
    194: 190,
    197: 190,
    199: 198,
    204: 203,
    205: 203,
    207: 203,
    216: 19,
    217: 19,
    **{ordinal: 218 for ordinal in range(219, 232)},
    233: 232,
    234: 232,
    **{ordinal: 288 for ordinal in range(289, 310)},
    **{ordinal: 316 for ordinal in range(317, 341)},
    352: 351,
    353: 351,
    355: 354,
    361: 360,
    394: 381,
    395: 381,
    396: 381,
    397: 381,
}


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical_slug(value: str) -> str:
    normalized = unicodedata.normalize("NFD", value)
    ascii_value = "".join(char for char in normalized if not unicodedata.combining(char))
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-")
    return slug[:72] or "element"


def clean_markdown(value: str) -> str:
    value = value.replace("**", "").replace("__", "")
    value = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", value)
    return value.strip().rstrip(".").strip()


def element_kind(ordinal: int, name: str) -> str:
    normalized = canonical_slug(name)
    if ordinal in CONDITIONAL_ORDINALS or "segun-version" in normalized:
        return "CONDITIONAL_VARIANT"
    if ordinal in REFERENCE_MARK_ORDINALS:
        return "REFERENCE_MARK"
    if ordinal in INTEGRATED_FEATURE_ORDINALS:
        return "INTEGRATED_FEATURE"
    if re.search(
        r"\b(perno|pernos|tuerca|tuercas|tornilleria|arandela|arandelas|"
        r"seguro|seguros|clip|clips|abrazadera|abrazaderas|esparrago|"
        r"esparragos|tapón|tapon|tapones|pasador|pasadores|chaveta)\b",
        name.lower(),
    ):
        return "SERVICE_HARDWARE"
    if re.search(
        r"\b(junta|juntas|retén|reten|retenes|sello|sellos|o-ring|o-rings|"
        r"filtro|filtros|correa|correas|bujía|bujia|bujías|bujias)\b",
        name.lower(),
    ) and "carter de aceite" not in normalized:
        return "CONSUMABLE"
    if re.search(r"\b(completo|completa|módulo|modulo|conjunto)\b", name.lower()):
        return "ASSEMBLY"
    return "SELLABLE_COMPONENT"


def animation_mode(name: str, kind: str) -> str:
    normalized = canonical_slug(name)
    if any(token in normalized for token in ("galeria", "paso", "conducto", "manguera", "linea")):
        return "FLOW_TRACE"
    if any(token in normalized for token in ("piston", "valvula", "embolo", "taque")):
        return "RECIPROCATING_MOTION"
    if any(
        token in normalized
        for token in (
            "ciguenal",
            "arbol-de-levas",
            "polea",
            "pinon",
            "rotor",
            "ventilador",
            "bomba",
            "arrancador",
            "alternador",
        )
    ):
        return "ROTATIONAL_FUNCTION"
    if any(token in normalized for token in ("resorte", "tensor", "correa", "cadena")):
        return "TENSION_AND_TRAVEL"
    if kind == "INTEGRATED_FEATURE":
        return "REGION_PULSE"
    if kind in {"SERVICE_HARDWARE", "CONSUMABLE"}:
        return "REMOVE_INSTALL"
    return "EXPLODE_REASSEMBLE"


def comparison_checks(name: str, kind: str) -> list[str]:
    checks = ["PHOTO", "MOUNTING_POSITION"]
    normalized = canonical_slug(name)
    if any(token in normalized for token in ("sensor", "conector", "arnes", "ecu", "rele")):
        checks += ["CONNECTOR", "PIN_COUNT", "KEYING"]
    if any(token in normalized for token in ("correa", "cadena", "pinon", "polea", "bendix")):
        checks += ["TOOTH_COUNT", "DIAMETER"]
    if any(token in normalized for token in ("perno", "tuerca", "rosca", "tapón", "tapon")):
        checks += ["THREAD", "LENGTH", "DIAMETER"]
    if kind in {"CONSUMABLE", "SERVICE_HARDWARE"}:
        checks += ["MATERIAL", "DIMENSIONS"]
    return list(dict.fromkeys(checks))


def parse_source(source_text: str, source_sha256: str) -> dict[str, Any]:
    if source_sha256 != EXPECTED_SOURCE_SHA256:
        raise ValueError(
            f"Unexpected G4ED source SHA-256: {source_sha256}; "
            f"expected {EXPECTED_SOURCE_SHA256}"
        )
    start_marker = "# Piezas del motor Hyundai Accent/Verna 2005"
    end_marker = "## Componentes que no deben agregarse erróneamente"
    if start_marker not in source_text or end_marker not in source_text:
        raise ValueError("G4ED source boundaries not found")
    body = source_text[source_text.index(start_marker) : source_text.index(end_marker)]
    lines = body.splitlines()
    section_pattern = re.compile(r"^##\s+(\d+)\.\s+(.+?)\s*$")
    item_pattern = re.compile(r"^(\d+)\.\s+(.+?)\s*$")
    url_pattern = re.compile(r"https?://[^)\]\s\"\\]+")

    sections: list[dict[str, Any]] = []
    raw_elements: list[dict[str, Any]] = []
    current_section: dict[str, Any] | None = None
    subsection_title = ""

    for line in lines:
        stripped = line.strip()
        section_match = section_pattern.match(stripped)
        if section_match:
            section_number = int(section_match.group(1))
            current_section = {
                "number": section_number,
                "systemId": SYSTEM_IDS[section_number],
                "title": clean_markdown(section_match.group(2)),
                "knowledgeLines": [],
                "sourceReferences": [],
            }
            sections.append(current_section)
            subsection_title = ""
            continue
        if current_section is None:
            continue
        if stripped.startswith("### "):
            subsection_title = clean_markdown(stripped.removeprefix("### "))
            current_section["knowledgeLines"].append(subsection_title)
            continue
        item_match = item_pattern.match(stripped)
        if item_match:
            ordinal = int(item_match.group(1))
            if 1 <= ordinal <= 420:
                raw_elements.append(
                    {
                        "ordinal": ordinal,
                        "nameOriginal": clean_markdown(item_match.group(2)),
                        "sectionNumber": current_section["number"],
                        "subsectionTitle": subsection_title,
                    }
                )
                continue
        if not stripped or stripped.startswith("[![") or stripped.startswith("["):
            continue
        if stripped.startswith("#"):
            continue
        cleaned = clean_markdown(stripped)
        if cleaned:
            current_section["knowledgeLines"].append(cleaned)
            current_section["sourceReferences"].extend(url_pattern.findall(stripped))

    ordinals = [element["ordinal"] for element in raw_elements]
    if ordinals != list(range(1, 421)):
        raise ValueError(f"Expected contiguous ordinals 1..420, got {ordinals[:5]}...{ordinals[-5:]}")

    global_references = list(dict.fromkeys(url_pattern.findall(body)))
    section_payloads = []
    for section in sections:
        knowledge = "\n".join(dict.fromkeys(section["knowledgeLines"])).strip()
        section_payloads.append(
            {
                "sectionNumber": section["number"],
                "systemId": section["systemId"],
                "title": section["title"],
                "knowledge": knowledge or "Conocimiento literal aportado por el propietario.",
                "sourceReferences": list(dict.fromkeys(section["sourceReferences"]))
                or global_references[:3],
            }
        )
    if len(section_payloads) != 20:
        raise ValueError(f"Expected 20 knowledge sections, got {len(section_payloads)}")

    id_by_ordinal = {
        raw["ordinal"]: f"g4ed-{raw['ordinal']:03d}-{canonical_slug(raw['nameOriginal'])}"
        for raw in raw_elements
    }
    elements = []
    for raw in raw_elements:
        ordinal = raw["ordinal"]
        name = raw["nameOriginal"]
        kind = element_kind(ordinal, name)
        parent_ordinal = PARENT_ORDINALS.get(ordinal)
        conditional = kind == "CONDITIONAL_VARIANT"
        directly_sellable = kind not in {
            "INTEGRATED_FEATURE",
            "REFERENCE_MARK",
        }
        render_strategy = (
            "SEMANTIC_REGION"
            if kind in {"INTEGRATED_FEATURE", "REFERENCE_MARK"}
            else "ISOLATED_PART"
        )
        system_id = SYSTEM_IDS[raw["sectionNumber"]]
        elements.append(
            {
                "ordinal": ordinal,
                "canonicalId": id_by_ordinal[ordinal],
                "nameOriginal": name,
                "aliases": [],
                "systemId": system_id,
                "sectionNumber": raw["sectionNumber"],
                "subsectionTitle": raw["subsectionTitle"] or None,
                "elementKind": kind,
                "parentCanonicalId": id_by_ordinal.get(parent_ordinal),
                "applicability": {
                    "vehicleScope": VEHICLE_SCOPE,
                    "engineScope": ENGINE_SCOPE,
                    "installedState": (
                        "PENDING_PHYSICAL_CONFIRMATION"
                        if conditional
                        else "REFERENCE_SCOPE"
                    ),
                    "compatibilityCeiling": "REQUIRES_VERIFICATION",
                },
                "evidenceRequirements": [
                    "CONFIRM_ENGINE_CODE",
                    "CONFIRM_VIN_OR_OEM",
                    "COMPARE_PHOTO_CONNECTOR_MEASUREMENTS",
                    *(
                        ["INSPECT_CYLINDER_HEAD"]
                        if ordinal in CONDITIONAL_ORDINALS
                        else []
                    ),
                ],
                "commerce": {
                    "directlySellable": directly_sellable,
                    "redirectToParent": not directly_sellable,
                    "comparisonChecks": comparison_checks(name, kind),
                    "visualMatchIsExactCompatibility": False,
                },
                "visual": {
                    "packId": f"g4ed_{system_id}",
                    "nodeKey": f"g4ed_{ordinal:03d}",
                    "renderStrategy": render_strategy,
                    "authority": (
                        "SCHEMATIC_REGION"
                        if render_strategy == "SEMANTIC_REGION"
                        else "REFERENCE_RECONSTRUCTION"
                    ),
                    "cameraPreset": (
                        "MACRO_REGION_ORBIT"
                        if render_strategy == "SEMANTIC_REGION"
                        else "PRODUCT_ORBIT"
                    ),
                    "interactionModes": [
                        "ORBIT_360",
                        "ZOOM",
                        "ISOLATE",
                        "XRAY_CONTEXT",
                        "EXPLODE_REASSEMBLE",
                        "RESET_CAMERA",
                    ],
                    "animationMode": animation_mode(name, kind),
                    "dimensional": False,
                    "oemClaim": False,
                },
                "knowledgeBinding": {
                    "sourceSha256": source_sha256,
                    "sectionNumber": raw["sectionNumber"],
                    "sourceOrdinal": ordinal,
                    "provenance": "FUENTE APORTADA POR EL PROPIETARIO",
                },
            }
        )

    payload: dict[str, Any] = {
        "schemaVersion": 1,
        "atlasId": "meet.g4ed.engine.parts.420",
        "atlasVersion": "1.0.0",
        "displayName": "Atlas Hyundai Alpha II G4ED — 420 elementos",
        "vehicleLabel": VEHICLE_SCOPE,
        "engineLabel": ENGINE_SCOPE,
        "source": {
            "sha256": source_sha256,
            "lineCount": len(source_text.splitlines()),
            "ownership": "USER_PROVIDED",
            "referenceCount": len(global_references),
        },
        "geometryPolicy": {
            "oemClaim": False,
            "vehicleSpecificClaim": False,
            "dimensionalState": "ILLUSTRATIVE_PROPORTIONS_ONLY",
            "defaultAuthority": "REFERENCE_RECONSTRUCTION",
            "warning": (
                "Reconstrucción técnica de referencia. Confirmar VIN, código de "
                "motor, OEM, fotografía, conector y medidas antes de comprar."
            ),
        },
        "statistics": {
            "elementCount": len(elements),
            "sectionCount": len(section_payloads),
            "directlySellableCount": sum(
                1 for element in elements if element["commerce"]["directlySellable"]
            ),
            "semanticRegionCount": sum(
                1
                for element in elements
                if element["visual"]["renderStrategy"] == "SEMANTIC_REGION"
            ),
            "conditionalVariantCount": sum(
                1 for element in elements if element["elementKind"] == "CONDITIONAL_VARIANT"
            ),
        },
        "sections": section_payloads,
        "elements": elements,
    }
    payload["contentSha256"] = canonical_payload_hash(payload)
    validate_payload(payload)
    return payload


def canonical_payload_hash(payload: dict[str, Any]) -> str:
    canonical_payload = dict(payload)
    canonical_payload.pop("contentSha256", None)
    canonical = json.dumps(
        canonical_payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return sha256_bytes(canonical)


def validate_payload(payload: dict[str, Any]) -> None:
    elements = payload.get("elements", [])
    if [element.get("ordinal") for element in elements] != list(range(1, 421)):
        raise ValueError("Atlas must contain contiguous ordinals 1..420")
    ids = [element.get("canonicalId") for element in elements]
    if len(ids) != len(set(ids)):
        raise ValueError("Atlas contains duplicate canonical IDs")
    known_ids = set(ids)
    for element in elements:
        parent = element.get("parentCanonicalId")
        if parent is not None and parent not in known_ids:
            raise ValueError(f"Unknown parent {parent} for {element['canonicalId']}")
        if (
            element["elementKind"] in {"INTEGRATED_FEATURE", "REFERENCE_MARK"}
            and element["commerce"]["directlySellable"]
        ):
            raise ValueError(f"Integrated element marked sellable: {element['canonicalId']}")
        if element["visual"].get("oemClaim"):
            raise ValueError(f"Unverified OEM claim: {element['canonicalId']}")
    if payload.get("contentSha256") != canonical_payload_hash(payload):
        raise ValueError("Atlas content SHA-256 mismatch")
    if payload.get("source", {}).get("sha256") != EXPECTED_SOURCE_SHA256:
        raise ValueError("Atlas source SHA-256 mismatch")


def load_payload(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    validate_payload(payload)
    return payload


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    if args.source is not None:
        source_raw = args.source.read_bytes()
        source_text = source_raw.decode("utf-8")
        payload = parse_source(source_text, sha256_bytes(source_raw))
        rendered = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if args.verify and args.output.exists():
            current = args.output.read_text(encoding="utf-8")
            if current != rendered:
                raise SystemExit("Generated G4ED atlas differs from committed asset")
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding="utf-8")
            print(f"Wrote {len(payload['elements'])} elements to {args.output}")
    else:
        if not args.output.exists():
            raise SystemExit("Atlas output does not exist; provide --source to generate it")
        payload = load_payload(args.output)
        print(
            f"Verified {len(payload['elements'])} elements; "
            f"contentSha256={payload['contentSha256']}"
        )


if __name__ == "__main__":
    main()
