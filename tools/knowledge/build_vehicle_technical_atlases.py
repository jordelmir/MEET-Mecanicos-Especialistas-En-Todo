#!/usr/bin/env python3
"""Build traceable MEET atlases for transmission, electrical and body systems."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT_ROOT = (
    REPO_ROOT / "android/app/src/main/assets/knowledge/vehicle_technical_atlases"
)
VEHICLE_LABEL = "Hyundai Accent/Verna 2005 · 1.6 DOHC · automático"


@dataclass(frozen=True)
class AtlasSpec:
    domain_id: str
    atlas_id: str
    display_name: str
    expected_sha256: str
    expected_count: int
    start_heading: str
    end_heading: str
    source_path: Path
    vehicle_label: str = VEHICLE_LABEL
    heading_style: str = "roman"
    reset_source_ordinals: bool = False

    @property
    def output_path(self) -> Path:
        return OUTPUT_ROOT / f"{self.domain_id}_atlas.json"


SPECS = {
    "transmission_hydraulics": AtlasSpec(
        domain_id="transmission_hydraulics",
        atlas_id="meet.accent2005.transmission-hydraulics.parts.838",
        display_name="Transmisión e hidráulicos — 838 elementos",
        expected_sha256="77973385cceafee8cb5c35f01463264df816501d81ed060e390cbb36cd226b2d",
        expected_count=838,
        start_heading="# I. Transmisión automática completa",
        end_heading="# XIV. Componentes que no corresponden a esta versión",
        source_path=Path(
            "/Users/jordelmirsdevhome/.codex/attachments/"
            "e09bd369-69e3-4264-bff4-648d7d652a2f/pasted-text.txt"
        ),
    ),
    "electrical": AtlasSpec(
        domain_id="electrical",
        atlas_id="meet.accent2005.electrical.parts.1529",
        display_name="Sistema eléctrico — 1.529 elementos",
        expected_sha256="b511b2085fc96a1c2d2cd23066ca63ab553af5791529084dc1a28579c36c6efb",
        expected_count=1529,
        start_heading="# I. Fuente de energía y alimentación principal",
        end_heading="# XXXV. Componentes que no deben inventarse",
        source_path=Path(
            "/Users/jordelmirsdevhome/.codex/attachments/"
            "b58b1084-b84b-434e-86b0-295c43686983/pasted-text.txt"
        ),
    ),
    "body": AtlasSpec(
        domain_id="body",
        atlas_id="meet.accent2005.sedan-body.parts.1665",
        display_name="Carrocería sedán interna y externa — 1.665 elementos",
        expected_sha256="719fbb72f6994d1e37a6072395a23ad84caa82b2bedc4b65b1a06586ed568e5f",
        expected_count=1665,
        start_heading="# I. Monocasco y estructura frontal",
        end_heading="# Componentes que no deben agregarse sin evidencia",
        source_path=Path(
            "/Users/jordelmirsdevhome/.codex/attachments/"
            "8878236d-1ec1-4d33-865a-319b3f224ced/pasted-text.txt"
        ),
        vehicle_label=(
            "Hyundai Accent/Verna 2005 · 1.6 DOHC · automático · sedán 4 puertas"
        ),
    ),
    "remaining_systems": AtlasSpec(
        domain_id="remaining_systems",
        atlas_id="meet.accent2005.remaining-systems.parts.1953",
        display_name="Sistemas mecánicos y periféricos — 1.953 elementos",
        expected_sha256="e9d82c61d08bfda44867666ecf5a7b4ba0d3bf67ced2fc32ba0271eeee3d9364",
        expected_count=1953,
        start_heading="# 1. Suspensión delantera completa",
        end_heading="# Estado final",
        source_path=Path(
            "/Users/jordelmirsdevhome/.codex/attachments/"
            "bbab9275-746f-43f6-81fd-b7107fc5aa04/pasted-text.txt"
        ),
        heading_style="arabic",
        reset_source_ordinals=True,
    ),
}


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def slug(value: str) -> str:
    normalized = unicodedata.normalize("NFD", value)
    ascii_value = "".join(
        char for char in normalized if not unicodedata.combining(char)
    )
    return re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-")[:72]


def clean(value: str) -> str:
    value = value.replace("**", "").replace("__", "")
    value = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", value)
    return value.strip().rstrip(".").strip()


def is_conditional(name: str) -> bool:
    normalized = slug(name)
    return any(
        token in normalized
        for token in (
            "cuando-equipa",
            "si-equipa",
            "si-la-version",
            "segun-version",
            "segun-mercado",
            "opcional",
            "solamente-si",
            "unicamente-",
            "segun-diseno",
            "segun-fabricante",
            "cuando-se-utiliza",
            "cuando-lo-incorpora",
        )
    )


def is_integrated_feature(name: str) -> bool:
    normalized = slug(name)
    exact_prefixes = (
        "superficie-",
        "camara-",
        "puerto-",
        "entrada-de-",
        "salida-de-",
        "salida-hacia-",
        "derivacion-",
        "alimentacion-",
        "senal-",
        "linea-de-diagnostico",
        "tierra-logica",
        "tierra-de-potencia",
        "marca-min",
        "marca-max",
        "numero-vin-estampado",
    )
    integrated_tokens = (
        "placas-positivas-internas",
        "placas-negativas-internas",
        "separadores-internos",
        "electrolito",
        "alabes-del-",
        "bobinados-del-",
        "nucleo-laminado",
        "circuito-hidraulico-interno",
        "pasos-internos",
        "conductos-internos",
        "barra-interna-de-cobre",
        "contactos-internos",
        "pista-conductora",
        "puntos-de-soldadura",
    )
    return normalized.startswith(exact_prefixes) or any(
        token in normalized for token in integrated_tokens
    )


def element_kind(name: str) -> str:
    normalized = slug(name)
    if is_conditional(name):
        return "CONDITIONAL_VARIANT"
    if is_integrated_feature(name):
        return "INTEGRATED_FEATURE"
    if any(
        normalized.startswith(prefix)
        for prefix in ("marca-", "numero-o-placa-de-identificacion")
    ):
        return "REFERENCE_MARK"
    if re.search(
        r"\b(perno|pernos|tuerca|tuercas|tornillo|tornillos|arandela|"
        r"arandelas|clip|clips|grapa|grapas|pasador|pasadores|circlip|"
        r"e-clip|remache|remaches|sujetador|sujetadores|espiga|espigas)\b",
        name.lower(),
    ):
        return "SERVICE_HARDWARE"
    if re.search(
        r"\b(junta|juntas|retén|reten|retenes|sello|sellos|o-ring|"
        r"fusible|fusibles|bombillo|bombillos|filtro|fluido|atf|"
        r"electrolito|adhesivo|sellador|cera|undercoating)\b",
        name.lower(),
    ):
        return "CONSUMABLE"
    if re.search(r"\b(completo|completa|conjunto|módulo|modulo)\b", name.lower()):
        return "ASSEMBLY"
    return "SELLABLE_COMPONENT"


def animation_mode(name: str, kind: str, domain_id: str) -> str:
    normalized = slug(name)
    if any(
        token in normalized
        for token in (
            "fluido",
            "linea",
            "tuberia",
            "manguera",
            "conducto",
            "circuito",
            "alimentacion",
            "cable",
            "arnes",
        )
    ):
        return "FLOW_TRACE" if domain_id != "electrical" else "CURRENT_TRACE"
    if any(
        token in normalized
        for token in (
            "engranaje",
            "planetario",
            "rotor",
            "motor",
            "bomba",
            "polea",
            "rodamiento",
            "eje",
            "ventilador",
        )
    ):
        return "ROTATIONAL_FUNCTION"
    if any(
        token in normalized
        for token in (
            "piston",
            "valvula",
            "solenoide",
            "actuador",
            "pedal",
            "amortiguador",
            "bisagra",
            "cerradura",
        )
    ):
        return "RECIPROCATING_MOTION"
    if kind in {"INTEGRATED_FEATURE", "REFERENCE_MARK"}:
        return "REGION_PULSE"
    if kind in {"SERVICE_HARDWARE", "CONSUMABLE"}:
        return "REMOVE_INSTALL"
    return "EXPLODE_REASSEMBLE"


def comparison_checks(name: str, kind: str, domain_id: str) -> list[str]:
    checks = ["PHOTO", "MOUNTING_POSITION", "DIMENSIONS"]
    normalized = slug(name)
    if domain_id == "electrical" or any(
        token in normalized for token in ("sensor", "conector", "modulo", "rele")
    ):
        checks += ["CONNECTOR", "PIN_COUNT", "KEYING", "VOLTAGE_DROP_UNDER_LOAD"]
    if domain_id == "transmission_hydraulics":
        checks += ["TRANSMISSION_CODE", "PORT_LAYOUT", "SPLINE_OR_TOOTH_COUNT"]
    if domain_id == "body":
        checks += ["BODY_STYLE", "SIDE_OR_POSITION", "PANEL_GAPS", "WELD_OR_CLIP_PATTERN"]
    if kind == "SERVICE_HARDWARE":
        checks += ["THREAD", "LENGTH", "DIAMETER"]
    return list(dict.fromkeys(checks))


def side_for(name: str) -> str:
    normalized = slug(name)
    has_left = any(token in normalized for token in ("izquierd", "-lh", "lado-izq"))
    has_right = any(token in normalized for token in ("derech", "-rh", "lado-der"))
    if has_left and has_right:
        return "LEFT_AND_RIGHT"
    if has_left:
        return "LEFT"
    if has_right:
        return "RIGHT"
    return "NOT_SIDE_SPECIFIC"


def body_style_for(name: str) -> str:
    normalized = slug(name)
    if "hatchback" in normalized:
        return "HATCHBACK_ONLY"
    if "sedan" in normalized:
        return "SEDAN_ONLY"
    return "ALL_REFERENCED_BODY_STYLES"


def equipment_conditions(name: str, conditional: bool) -> list[str]:
    normalized = slug(name)
    conditions = []
    for token, condition in (
        ("abs", "ABS_EQUIPPED"),
        ("ebd", "EBD_EQUIPPED"),
        ("srs", "SRS_EQUIPPED"),
        ("airbag", "SRS_EQUIPPED"),
        ("aire-acondicionado", "A_C_EQUIPPED"),
        ("cruise", "CRUISE_CONTROL_EQUIPPED"),
        ("techo-corredizo", "SUNROOF_EQUIPPED"),
        ("ventanas-electricas", "POWER_WINDOWS_EQUIPPED"),
    ):
        if token in normalized:
            conditions.append(condition)
    if conditional and not conditions:
        conditions.append("CONFIRM_INSTALLED_EQUIPMENT")
    return list(dict.fromkeys(conditions))


def parse_atlas(spec: AtlasSpec, source_raw: bytes) -> dict[str, Any]:
    source_sha = sha256_bytes(source_raw)
    if source_sha != spec.expected_sha256:
        raise ValueError(
            f"{spec.domain_id}: source SHA mismatch {source_sha} != "
            f"{spec.expected_sha256}"
        )
    text = source_raw.decode("utf-8")
    if spec.start_heading not in text or spec.end_heading not in text:
        raise ValueError(f"{spec.domain_id}: source boundaries missing")
    body = text[text.index(spec.start_heading) : text.index(spec.end_heading)]
    lines = body.splitlines()
    h1_pattern = (
        re.compile(r"^#\s+(\d+)\.\s+(.+?)\s*$")
        if spec.heading_style == "arabic"
        else re.compile(r"^#\s+([IVXLCDM]+)\.\s+(.+?)\s*$")
    )
    h2_pattern = (
        re.compile(r"^##\s+(\d+\.\d+)\s+(.+?)\s*$")
        if spec.reset_source_ordinals
        else re.compile(r"^##\s+(\d+)\.\s+(.+?)\s*$")
    )
    item_pattern = re.compile(r"^(\d+)\.\s+(.+?)\s*$")
    url_pattern = re.compile(r"https?://[^)\]\s\"\\]+")

    sections: list[dict[str, Any]] = []
    raw_elements: list[dict[str, Any]] = []
    current_section: dict[str, Any] | None = None
    subsection = ""

    for line in lines:
        stripped = line.strip()
        h1_match = h1_pattern.match(stripped)
        if h1_match:
            number = len(sections) + 1
            title = clean(h1_match.group(2))
            current_section = {
                "sectionNumber": number,
                "systemId": f"{spec.domain_id}_{number:02d}_{slug(title)[:42]}",
                "title": title,
                "knowledgeLines": [],
                "sourceReferences": [],
            }
            sections.append(current_section)
            subsection = ""
            continue
        if current_section is None:
            continue
        h2_match = h2_pattern.match(stripped)
        if h2_match:
            subsection = clean(h2_match.group(2))
            current_section["knowledgeLines"].append(subsection)
            continue
        item_match = item_pattern.match(stripped)
        if item_match:
            source_local_ordinal = int(item_match.group(1))
            ordinal = len(raw_elements) + 1 if spec.reset_source_ordinals else source_local_ordinal
            if 1 <= ordinal <= spec.expected_count:
                raw_elements.append(
                    {
                        "ordinal": ordinal,
                        "sourceLocalOrdinal": source_local_ordinal,
                        "nameOriginal": clean(item_match.group(2)),
                        "sectionNumber": current_section["sectionNumber"],
                        "systemId": current_section["systemId"],
                        "subsectionTitle": subsection,
                    }
                )
            continue
        if not stripped or stripped.startswith(("[![", "[", "---", "|", "* ")):
            continue
        cleaned = clean(stripped)
        if cleaned and not cleaned.startswith("#"):
            current_section["knowledgeLines"].append(cleaned)
            current_section["sourceReferences"].extend(url_pattern.findall(stripped))

    ordinals = [element["ordinal"] for element in raw_elements]
    expected = list(range(1, spec.expected_count + 1))
    if ordinals != expected:
        raise ValueError(
            f"{spec.domain_id}: expected ordinals 1..{spec.expected_count}; "
            f"got {ordinals[:5]}...{ordinals[-5:]}"
        )

    id_by_ordinal = {
        item["ordinal"]: (
            f"{spec.domain_id}-{item['ordinal']:04d}-{slug(item['nameOriginal'])}"
        )
        for item in raw_elements
    }
    last_sellable_by_subsection: dict[tuple[int, str], str] = {}
    last_sellable_by_section: dict[int, str] = {}
    elements = []
    for item in raw_elements:
        ordinal = item["ordinal"]
        name = item["nameOriginal"]
        kind = element_kind(name)
        integrated = kind in {"INTEGRATED_FEATURE", "REFERENCE_MARK"}
        body_style_condition = body_style_for(name)
        subsection_key = (item["sectionNumber"], item["subsectionTitle"])
        parent_id = None
        if integrated:
            parent_id = (
                last_sellable_by_subsection.get(subsection_key)
                or last_sellable_by_section.get(item["sectionNumber"])
            )
        directly_sellable = not integrated
        conditional = (
            kind == "CONDITIONAL_VARIANT"
            or body_style_condition == "HATCHBACK_ONLY"
            or (
                spec.domain_id == "remaining_systems"
                and item["sectionNumber"] in {21, 25}
            )
        )
        if conditional:
            kind = "CONDITIONAL_VARIANT"
        canonical_id = id_by_ordinal[ordinal]
        if directly_sellable:
            last_sellable_by_subsection[subsection_key] = canonical_id
            last_sellable_by_section[item["sectionNumber"]] = canonical_id
        render_strategy = "SEMANTIC_REGION" if integrated else "ISOLATED_PART"
        elements.append(
            {
                "ordinal": ordinal,
                "canonicalId": canonical_id,
                "nameOriginal": name,
                "aliases": [],
                "systemId": item["systemId"],
                "sectionNumber": item["sectionNumber"],
                "subsectionTitle": item["subsectionTitle"] or None,
                "elementKind": kind,
                "parentCanonicalId": parent_id,
                "applicability": {
                    "vehicleScope": (
                        "Hyundai Accent/Verna 2005 · 1.6 DOHC · automático · "
                        "hatchback únicamente; no aplicable al sedán seleccionado"
                        if body_style_condition == "HATCHBACK_ONLY"
                        else spec.vehicle_label
                    ),
                    "engineScope": (
                        "Referencia de configuración; confirmar VIN, código de "
                        "transmisión, mercado, carrocería y equipamiento físico"
                    ),
                    "installedState": (
                        "PENDING_PHYSICAL_CONFIRMATION"
                        if conditional
                        else "REFERENCE_SCOPE"
                    ),
                    "compatibilityCeiling": "REQUIRES_VERIFICATION",
                    "side": side_for(name),
                    "bodyStyleCondition": body_style_condition,
                    "equipmentConditions": equipment_conditions(name, conditional),
                },
                "evidenceRequirements": [
                    "CONFIRM_VIN_OR_OEM",
                    "COMPARE_PHOTO_CONNECTOR_MEASUREMENTS",
                    "CONFIRM_INSTALLED_EQUIPMENT",
                ],
                "commerce": {
                    "directlySellable": directly_sellable,
                    "redirectToParent": integrated,
                    "comparisonChecks": comparison_checks(name, kind, spec.domain_id),
                    "visualMatchIsExactCompatibility": False,
                },
                "visual": {
                    "packId": f"{spec.domain_id}_{item['sectionNumber']:02d}",
                    "nodeKey": f"{spec.domain_id}_{ordinal:04d}",
                    "renderStrategy": render_strategy,
                    "authority": (
                        "SCHEMATIC_REGION"
                        if integrated
                        else "REFERENCE_RECONSTRUCTION"
                    ),
                    "cameraPreset": (
                        "MACRO_REGION_ORBIT" if integrated else "PRODUCT_ORBIT"
                    ),
                    "interactionModes": [
                        "ORBIT_360",
                        "ZOOM",
                        "ISOLATE",
                        "XRAY_CONTEXT",
                        "EXPLODE_REASSEMBLE",
                        "RESET_CAMERA",
                    ],
                    "animationMode": animation_mode(name, kind, spec.domain_id),
                    "dimensional": False,
                    "oemClaim": False,
                },
                "knowledgeBinding": {
                    "sourceSha256": source_sha,
                    "sectionNumber": item["sectionNumber"],
                    "sourceOrdinal": ordinal,
                    "provenance": "FUENTE APORTADA POR EL PROPIETARIO",
                    **(
                        {"sourceLocalOrdinal": item["sourceLocalOrdinal"]}
                        if spec.reset_source_ordinals
                        else {}
                    ),
                },
                "normalization": {
                    "identityKey": (
                        f"{spec.domain_id}:{item['sectionNumber']:02d}:"
                        f"{slug(item['subsectionTitle'] or 'general')}:"
                        f"{slug(name)}:{ordinal:04d}"
                    ),
                    "oemResolutionState": "PENDING_VIN_EPC",
                    "oemNumber": None,
                    "quantity": None,
                    "supersededBy": None,
                    "fastenerRelationshipState": "PENDING_EPC_RELATIONSHIP",
                },
            }
        )

    sections_payload = []
    global_refs = list(dict.fromkeys(url_pattern.findall(body)))
    for section in sections:
        sections_payload.append(
            {
                "sectionNumber": section["sectionNumber"],
                "systemId": section["systemId"],
                "title": section["title"],
                "knowledge": "\n".join(
                    dict.fromkeys(section["knowledgeLines"])
                ).strip()
                or "Conocimiento literal aportado por el propietario.",
                "sourceReferences": list(
                    dict.fromkeys(section["sourceReferences"])
                )
                or global_refs[:3],
            }
        )

    payload: dict[str, Any] = {
        "schemaVersion": 1,
        "atlasId": spec.atlas_id,
        "atlasVersion": "1.0.0",
        "domainId": spec.domain_id,
        "displayName": spec.display_name,
        "vehicleLabel": spec.vehicle_label,
        "engineLabel": (
            "Perfil de referencia; aplicabilidad física pendiente de confirmación"
        ),
        "source": {
            "sha256": source_sha,
            "lineCount": len(text.splitlines()),
            "ownership": "USER_PROVIDED",
            "referenceCount": len(global_refs),
        },
        "geometryPolicy": {
            "oemClaim": False,
            "vehicleSpecificClaim": False,
            "dimensionalState": "ILLUSTRATIVE_PROPORTIONS_ONLY",
            "defaultAuthority": "REFERENCE_RECONSTRUCTION",
            "warning": (
                "Reconstrucción técnica de referencia. Confirmar VIN, OEM, "
                "código aplicable, equipamiento, foto, conector y medidas."
            ),
        },
        "statistics": {
            "elementCount": len(elements),
            "sectionCount": len(sections_payload),
            "directlySellableCount": sum(
                element["commerce"]["directlySellable"] for element in elements
            ),
            "semanticRegionCount": sum(
                element["visual"]["renderStrategy"] == "SEMANTIC_REGION"
                for element in elements
            ),
            "conditionalVariantCount": sum(
                element["elementKind"] == "CONDITIONAL_VARIANT"
                for element in elements
            ),
        },
        "sections": sections_payload,
        "elements": elements,
    }
    payload["contentSha256"] = canonical_hash(payload)
    validate_payload(payload, spec)
    return payload


def canonical_hash(payload: dict[str, Any]) -> str:
    copy = dict(payload)
    copy.pop("contentSha256", None)
    raw = json.dumps(
        copy, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()
    return sha256_bytes(raw)


def validate_payload(payload: dict[str, Any], spec: AtlasSpec) -> None:
    elements = payload["elements"]
    if [element["ordinal"] for element in elements] != list(
        range(1, spec.expected_count + 1)
    ):
        raise ValueError(f"{spec.domain_id}: non-contiguous ordinals")
    ids = [element["canonicalId"] for element in elements]
    if len(ids) != len(set(ids)):
        raise ValueError(f"{spec.domain_id}: duplicate canonical IDs")
    known = set(ids)
    for element in elements:
        parent = element["parentCanonicalId"]
        if parent is not None and parent not in known:
            raise ValueError(f"{spec.domain_id}: orphan parent {parent}")
        if element["visual"]["oemClaim"] or element["visual"]["dimensional"]:
            raise ValueError(f"{spec.domain_id}: unsupported geometry claim")
        if element["commerce"]["visualMatchIsExactCompatibility"]:
            raise ValueError(f"{spec.domain_id}: visual match promoted to exact")
        if (
            element["visual"]["renderStrategy"] == "SEMANTIC_REGION"
            and (
                element["commerce"]["directlySellable"]
                or not element["commerce"]["redirectToParent"]
                or parent is None
            )
        ):
            raise ValueError(f"{spec.domain_id}: invalid semantic commerce")
    if payload["contentSha256"] != canonical_hash(payload):
        raise ValueError(f"{spec.domain_id}: content hash mismatch")


def render(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--atlas",
        choices=[*SPECS, "all"],
        default="all",
    )
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    selected = SPECS.values() if args.atlas == "all" else [SPECS[args.atlas]]
    for spec in selected:
        payload = parse_atlas(spec, spec.source_path.read_bytes())
        rendered = render(payload)
        if args.verify:
            if not spec.output_path.exists():
                raise SystemExit(f"Missing committed atlas: {spec.output_path}")
            if spec.output_path.read_text() != rendered:
                raise SystemExit(f"Generated atlas differs: {spec.domain_id}")
            print(
                f"Verified {spec.domain_id}: {len(payload['elements'])} elements; "
                f"contentSha256={payload['contentSha256']}"
            )
        else:
            spec.output_path.parent.mkdir(parents=True, exist_ok=True)
            spec.output_path.write_text(rendered)
            print(
                f"Wrote {spec.domain_id}: {len(payload['elements'])} elements -> "
                f"{spec.output_path}"
            )


if __name__ == "__main__":
    main()
