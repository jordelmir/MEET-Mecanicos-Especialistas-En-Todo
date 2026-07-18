#!/usr/bin/env python3
"""Build the complete literal MEET parts corpus from the owner's DOCX extractions."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from common import normalize_text, sha256_text


SCHEMA_VERSION = 1
CORPUS_ID = "meet_owner_proprietary_parts_corpus"
CORPUS_VERSION = "1.0.0"
VEHICLE_LABEL = "Hyundai Accent/Verna 2005 · caja automática · motor 1600 cc"
EXPECTED_SOURCES = {
    "Document (16).docx": {
        "documentId": "document_16",
        "sha256": "09f2926a22542a4e7be24e50f2a4f4c42674f32958e8e541683fbb0cf76352d7",
        "blockCount": 44_106,
    },
    "Document (17).docx": {
        "documentId": "document_17",
        "sha256": "baf4add3f22202fc7d66f7b7f4aee549d90780f1891da6fa66ffbc2db1820824",
        "blockCount": 30_542,
    },
}


@dataclass(frozen=True)
class SystemDefinition:
    id: str
    title: str
    color: str


SYSTEMS = {
    item.id: item
    for item in (
        SystemDefinition("structure", "Núcleo y estructura", "#38BDF8"),
        SystemDefinition("engine", "Motor de combustión", "#F59E0B"),
        SystemDefinition("intake", "Admisión de aire", "#22D3EE"),
        SystemDefinition("forced_induction", "Sobrealimentación", "#F97316"),
        SystemDefinition("transmission", "Transmisión y tren motriz", "#10B981"),
        SystemDefinition("suspension", "Suspensión", "#A3E635"),
        SystemDefinition("steering", "Dirección", "#8B5CF6"),
        SystemDefinition("brakes", "Frenos", "#FB7185"),
        SystemDefinition("wheels", "Ruedas y neumáticos", "#EAB308"),
        SystemDefinition("electrical", "Sistema eléctrico", "#60A5FA"),
        SystemDefinition("control_modules", "ECUs, módulos y controladores", "#C084FC"),
        SystemDefinition("sensors", "Sensores", "#2DD4BF"),
        SystemDefinition("actuators", "Actuadores", "#F472B6"),
        SystemDefinition("lighting", "Iluminación", "#FDE047"),
        SystemDefinition("hvac", "HVAC y climatización", "#38BDF8"),
        SystemDefinition("passive_safety", "Seguridad pasiva", "#EF4444"),
        SystemDefinition("adas", "ADAS y asistencia", "#06B6D4"),
        SystemDefinition("body", "Carrocería exterior", "#94A3B8"),
        SystemDefinition("wipers", "Limpiaparabrisas y lavado", "#34D399"),
        SystemDefinition("interior", "Interior", "#F59E0B"),
        SystemDefinition("infotainment", "Infotainment y comunicación", "#818CF8"),
        SystemDefinition("access", "Cierre, acceso e inmovilizador", "#EC4899"),
        SystemDefinition("hybrid_ev", "Híbridos y eléctricos", "#FACC15"),
        SystemDefinition("fluids", "Fluidos, consumibles y desgaste", "#0EA5E9"),
        SystemDefinition("hardware", "Fasteners, sellos y hardware", "#D1D5DB"),
        SystemDefinition("overview", "Índice funcional y reglas", "#22C55E"),
    )
}


DOC16_SYSTEM_ANCHORS = (
    (2, "structure"),
    (896, "engine"),
    (7127, "intake"),
    (11043, "forced_induction"),
    (12537, "transmission"),
    (18934, "suspension"),
    (21957, "steering"),
    (24073, "brakes"),
    (27867, "wheels"),
    (29237, "electrical"),
    (35434, "control_modules"),
    (41557, "sensors"),
    (43429, "actuators"),
    (43472, "lighting"),
    (43514, "hvac"),
    (43560, "passive_safety"),
    (43588, "adas"),
    (43614, "body"),
    (43694, "wipers"),
    (43711, "interior"),
    (43779, "infotainment"),
    (43811, "access"),
    (43840, "hybrid_ev"),
    (43898, "fluids"),
    (43941, "hardware"),
    (43981, "overview"),
)

DOC17_SYSTEM_ANCHORS = (
    (1, "sensors"),
    (1781, "transmission"),
    (2981, "suspension"),
    (4280, "sensors"),
    (7017, "actuators"),
    (8743, "lighting"),
    (11570, "hvac"),
    (15348, "passive_safety"),
    (16921, "adas"),
    (18564, "body"),
    (25004, "wipers"),
    (26690, "interior"),
    (30215, "infotainment"),
    (30247, "access"),
    (30276, "hybrid_ev"),
    (30334, "fluids"),
    (30377, "hardware"),
    (30417, "overview"),
)

DOC17_SECTION_ANCHORS = {
    1: "Sensores principales",
    30: "Sensores principales del motor",
    1781: "Sensores principales de la transmisión",
    2981: "Sensores principales del chasis",
    4280: "Sensores principales de carrocería e interior",
    7017: "10. Actuadores principales",
    8743: "11. Iluminación",
    10434: "12. Iluminación interior",
    11570: "12. HVAC / climatización",
    15348: "Seguridad pasiva",
    16921: "14. ADAS y asistencia",
    18564: "15. Carrocería exterior",
    25004: "16. Limpiaparabrisas y lavado",
    26690: "17. Interior",
    30215: "18. Infotainment, comunicación y confort",
    30247: "19. Cierre, acceso e inmovilizador",
    30276: "20. Híbridos y eléctricos",
    30334: "21. Fluidos, consumibles y piezas de desgaste",
    30377: "22. Fasteners, sellos y hardware crítico",
    30417: "23. Piezas por sistema funcional resumido",
    30483: "24. Piezas que una IA automotriz mediocre suele olvidar",
    30533: "25. Veredicto técnico",
}

DETAIL_MARKERS = {
    "aplicabilidad",
    "clasificacion",
    "descripcion",
    "diagnostico",
    "funcion",
    "herramientas",
    "procedimiento",
    "que es",
    "sintomas",
    "ubicacion",
    "validacion",
}
META_TITLES = {
    "advertencia",
    "aplicabilidad",
    "clasificacion",
    "diagnostico",
    "error fatal",
    "funcion",
    "herramientas",
    "introduccion",
    "procedimiento",
    "reglas duras",
    "respuesta correcta",
    "sintomas",
    "tabla de aplicabilidad",
    "test clave",
    "validacion",
    "veredicto tecnico",
}
ACTION_PREFIXES = (
    "ajustar ", "aplicar ", "asegurar ", "buscar ", "cambiar ", "comprobar ",
    "conectar ", "confirmar ", "desactivar ", "desconectar ", "determinar ",
    "evitar ", "inspeccionar ", "instalar ", "limpiar ", "medir ", "montar ",
    "diagnostico ", "ejemplo ", "ejemplos", "herramientas ", "no ", "nota ",
    "procedimiento ", "probar ", "registrar ", "reparar ", "retirar ", "revisar ",
    "si hay ", "sintomas ", "sustituir ", "validar ", "verificar ",
)
COMPONENT_TERMS = (
    "abrazadera", "actuador", "airbag", "alternador", "amortiguador", "arnes",
    "asiento", "bateria", "biela", "bieleta", "bloque", "bomba", "brazo",
    "buje", "cable", "caja", "caliper", "camara", "carcasa", "catalizador",
    "ciguenal", "cinturon", "cojinete", "compresor", "conector", "controlador",
    "convertidor", "correa", "cremallera", "cubo", "culata", "deposito", "disco",
    "ducto", "eje", "electrovalvula", "embrague", "espejo", "filtro", "freno",
    "fusible", "guardapolvo", "inyector", "junta", "lampara", "linea", "manguera",
    "manija", "mariposa", "modulo", "motor", "multiple", "panel", "pastilla",
    "piston", "polea", "puerta", "radiador", "rele", "resorte", "rodamiento",
    "rotula", "sensor", "sello", "semieje", "solenoide", "soporte", "switch",
    "tapa", "tensor", "terminal", "termostato", "tornillo", "transmision", "turbo",
    "unidad", "valvula", "varillaje", "ventilador", "vidrio", "volante",
)
REAL_CASE_PATTERN = re.compile(
    r"(?i)(ejemplo real|referencia cuando no aplica|referencia real|caso real|usa ejemplo real)"
)
NUMBERED_TITLE_PATTERN = re.compile(r"^\s*\d{1,3}[.)]\s+(.+?)\s*$")
TREE_PREFIX_PATTERN = re.compile(r"^\s*[•·▪◦├└│─]+\s*")


def canonical_json(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def slug(value: str, limit: int = 72) -> str:
    decomposed = unicodedata.normalize("NFKD", value)
    ascii_value = "".join(char for char in decomposed if not unicodedata.combining(char))
    compact = re.sub(r"[^a-zA-Z0-9]+", "-", ascii_value).strip("-").lower()
    return compact[:limit].rstrip("-") or "registro"


def system_for_order(order: int, anchors: tuple[tuple[int, str], ...]) -> str:
    current = anchors[0][1]
    for start, system_id in anchors:
        if order < start:
            break
        current = system_id
    return current


def is_top_level_heading(block: dict[str, Any], document_id: str) -> bool:
    order = block["order"]
    if document_id == "document_16":
        return order in {anchor for anchor, _ in DOC16_SYSTEM_ANCHORS}
    return order in DOC17_SECTION_ANCHORS


def cleaned_title(text: str) -> str:
    tree_cleaned = TREE_PREFIX_PATTERN.sub("", text).strip()
    match = NUMBERED_TITLE_PATTERN.match(tree_cleaned)
    return (match.group(1) if match else tree_cleaned).strip()


def looks_like_component_name(text: str) -> bool:
    title = cleaned_title(text)
    normalized = normalize_text(title)
    if not title or len(title) > 150 or len(title.split()) > 18:
        return False
    if normalized in META_TITLES or any(normalized.startswith(prefix) for prefix in ACTION_PREFIXES):
        return False
    if title.endswith((".", ":", ";", "?", "!")):
        return False
    return any(term in normalized for term in COMPONENT_TERMS)


def classify_blocks(blocks: list[dict[str, Any]], document_id: str) -> list[str]:
    roles: list[str] = []
    for index, block in enumerate(blocks):
        text = block.get("text", "")
        normalized = normalize_text(text)
        next_normalized = normalize_text(blocks[index + 1].get("text", "")) if index + 1 < len(blocks) else ""
        style = block.get("styleId", "")
        if block.get("kind") == "table":
            role = "TABLE"
        elif style in {"Heading1", "Heading2"} or is_top_level_heading(block, document_id):
            role = "SECTION_TITLE"
        elif REAL_CASE_PATTERN.search(text):
            role = "REAL_CASE"
        elif TREE_PREFIX_PATTERN.match(text) and looks_like_component_name(text):
            role = "COMPONENT"
        elif NUMBERED_TITLE_PATTERN.match(text) and looks_like_component_name(text):
            role = "COMPONENT"
        elif style == "ListParagraph" and next_normalized in DETAIL_MARKERS and looks_like_component_name(text):
            role = "COMPONENT"
        elif next_normalized in DETAIL_MARKERS and looks_like_component_name(text):
            role = "COMPONENT"
        elif looks_like_component_name(text) and len(text) <= 96 and text.lstrip()[:1].isupper():
            role = "COMPONENT"
        else:
            role = "SOURCE_DETAIL"
        roles.append(role)

    # A detailed H1 such as "7. Cuerpo de aceleración" is itself a component.
    for index, block in enumerate(blocks):
        if block.get("styleId") == "Heading1" and not is_top_level_heading(block, document_id):
            if NUMBERED_TITLE_PATTERN.match(block.get("text", "")) and looks_like_component_name(block.get("text", "")):
                roles[index] = "COMPONENT"
    return roles


def logical_section_title(block: dict[str, Any], document_id: str, current_doc17_title: str) -> str:
    if document_id == "document_17":
        return current_doc17_title
    path = list(block.get("sectionPath") or [])
    if not path:
        return "Introducción"
    first = path[0]
    detailed_h1 = NUMBERED_TITLE_PATTERN.match(first) and first not in {
        "0. Núcleo del vehículo", "1. Motor de combustión interna", "2. Transmisión y tren motriz",
        "3. Suspensión", "4. Dirección", "5. Frenos", "6. Ruedas y neumáticos",
        "7. Sistema eléctrico principal", "8. ECUs, módulos y controladores", "9. Sensores principales",
        "10. Actuadores principales", "11. Iluminación", "12. HVAC / climatización",
        "13. Seguridad pasiva", "14. ADAS y asistencia", "15. Carrocería exterior",
        "16. Limpiaparabrisas y lavado", "17. Interior", "18. Infotainment, comunicación y confort",
        "19. Cierre, acceso e inmovilizador", "20. Híbridos y eléctricos",
        "21. Fluidos, consumibles y piezas de desgaste", "22. Fasteners, sellos y hardware crítico",
        "23. Piezas por sistema funcional resumido", "24. Piezas que una IA automotriz mediocre suele olvidar",
        "25. Veredicto técnico",
    }
    if detailed_h1:
        return first
    return " · ".join(path[:2])


def visual_seed(entity_id: str) -> int:
    return int(hashlib.sha256(entity_id.encode("utf-8")).hexdigest()[:8], 16)


def entity_from_block(
    block: dict[str, Any], document: dict[str, Any], document_id: str, section_id: str,
    shard_path: str, system_id: str, role: str,
) -> dict[str, Any]:
    entity_id = f"{document_id}-o{block['order']:06d}-{slug(cleaned_title(block['text']), 44)}"
    return {
        "id": entity_id,
        "nameOriginal": block["text"],
        "recordRole": role,
        "systemId": system_id,
        "sectionId": section_id,
        "shardPath": shard_path,
        "sourceDocumentId": document_id,
        "sourceFileName": document["sourceFileName"],
        "sourceDocumentSha256": document["sourceSha256"],
        "sourceBlockId": block["blockId"],
        "sourceTextHash": block["textHash"],
        "sourceOrder": block["order"],
        "vehicleScope": "Caso real conservado literalmente desde la fuente" if role == "REAL_CASE" else VEHICLE_LABEL,
        "threeDimensionalBinding": {
            "sceneId": system_id,
            "nodeId": entity_id,
            "visualAuthority": "PROCEDURAL_SCHEMATIC",
            "isDimensionalModel": False,
            "seed": visual_seed(entity_id),
        },
    }


def load_and_validate_extractions(paths: Iterable[Path]) -> list[dict[str, Any]]:
    documents: list[dict[str, Any]] = []
    for path in paths:
        with path.open("r", encoding="utf-8") as handle:
            extraction = json.load(handle)
        document = extraction.get("document") or {}
        expected = EXPECTED_SOURCES.get(document.get("sourceFileName"))
        if expected is None:
            raise ValueError(f"Unexpected source document: {document.get('sourceFileName')}")
        if document.get("sourceSha256") != expected["sha256"]:
            raise ValueError(f"Source SHA-256 mismatch for {document.get('sourceFileName')}")
        blocks = extraction.get("blocks")
        if not isinstance(blocks, list) or len(blocks) != expected["blockCount"]:
            raise ValueError(f"Block count mismatch for {document.get('sourceFileName')}")
        for block in blocks:
            if sha256_text(block.get("text", "")) != block.get("textHash"):
                raise ValueError(f"Text hash mismatch in {document.get('sourceFileName')} order {block.get('order')}")
        extraction["documentId"] = expected["documentId"]
        documents.append(extraction)
    if {item["documentId"] for item in documents} != {"document_16", "document_17"}:
        raise ValueError("Both proprietary documents are required")
    return sorted(documents, key=lambda item: item["documentId"])


def build_catalog(documents: list[dict[str, Any]], max_blocks_per_shard: int = 360) -> tuple[dict[str, Any], dict[str, Any], dict[str, dict[str, Any]]]:
    sections: list[dict[str, Any]] = []
    entities: list[dict[str, Any]] = []
    shards: dict[str, dict[str, Any]] = {}
    total_role_counts: dict[str, int] = {}

    for extraction in documents:
        document = extraction["document"]
        document_id = extraction["documentId"]
        blocks = extraction["blocks"]
        roles = classify_blocks(blocks, document_id)
        anchors = DOC16_SYSTEM_ANCHORS if document_id == "document_16" else DOC17_SYSTEM_ANCHORS
        current_doc17_title = "Sensores principales"
        grouped: list[tuple[str, str, list[tuple[dict[str, Any], str]]]] = []

        for block, role in zip(blocks, roles, strict=True):
            if document_id == "document_17" and block["order"] in DOC17_SECTION_ANCHORS:
                current_doc17_title = DOC17_SECTION_ANCHORS[block["order"]]
            system_id = system_for_order(block["order"], anchors)
            title = logical_section_title(block, document_id, current_doc17_title)
            reached_safe_split = (
                bool(grouped)
                and len(grouped[-1][2]) >= max_blocks_per_shard
                and role in {"COMPONENT", "REAL_CASE", "SECTION_TITLE"}
            )
            if not grouped or grouped[-1][0] != system_id or grouped[-1][1] != title or reached_safe_split:
                grouped.append((system_id, title, []))
            grouped[-1][2].append((block, role))

        for system_id, title, section_blocks in grouped:
            source_order_start = section_blocks[0][0]["order"]
            section_id = f"{document_id}-{system_id}-o{source_order_start:06d}-{slug(title, 42)}"
            relative_path = f"knowledge/proprietary/sections/{section_id}.json"
            section_entities: list[dict[str, Any]] = []
            current_entity_id: str | None = None
            literal_blocks: list[dict[str, Any]] = []

            for block, role in section_blocks:
                total_role_counts[role] = total_role_counts.get(role, 0) + 1
                entity_id: str | None = None
                if role in {"COMPONENT", "REAL_CASE"}:
                    entity = entity_from_block(
                        block, document, document_id, section_id, relative_path, system_id, role
                    )
                    entities.append(entity)
                    section_entities.append(entity)
                    entity_id = entity["id"]
                    if role == "COMPONENT":
                        current_entity_id = entity_id
                literal = {
                    "blockId": block["blockId"],
                    "kind": block["kind"],
                    "order": block["order"],
                    "recordRole": role,
                    "sectionPath": block.get("sectionPath", []),
                    "styleId": block.get("styleId", ""),
                    "text": block["text"],
                    "textHash": block["textHash"],
                    "entityId": entity_id,
                    "parentEntityId": current_entity_id if entity_id is None else None,
                }
                if "rows" in block:
                    literal["rows"] = block["rows"]
                literal_blocks.append(literal)

            shard_payload = {
                "schemaVersion": SCHEMA_VERSION,
                "corpusId": CORPUS_ID,
                "sectionId": section_id,
                "systemId": system_id,
                "titleOriginal": title,
                "vehicleLabel": VEHICLE_LABEL,
                "sourceDocumentId": document_id,
                "sourceFileName": document["sourceFileName"],
                "sourceDocumentSha256": document["sourceSha256"],
                "blocks": literal_blocks,
            }
            shard_sha = sha256_text(canonical_json(shard_payload))
            shard_payload["contentSha256"] = shard_sha
            shards[relative_path] = shard_payload
            sections.append({
                "id": section_id,
                "systemId": system_id,
                "titleOriginal": title,
                "sourceDocumentId": document_id,
                "sourceFileName": document["sourceFileName"],
                "sourceDocumentSha256": document["sourceSha256"],
                "sourceOrderStart": literal_blocks[0]["order"],
                "sourceOrderEnd": literal_blocks[-1]["order"],
                "blockCount": len(literal_blocks),
                "entityCount": sum(1 for item in section_entities if item["recordRole"] == "COMPONENT"),
                "realCaseCount": sum(1 for item in section_entities if item["recordRole"] == "REAL_CASE"),
                "shardPath": relative_path,
                "contentSha256": shard_sha,
            })

    source_documents = [
        {
            "id": extraction["documentId"],
            "sourceFileName": extraction["document"]["sourceFileName"],
            "sourceSha256": extraction["document"]["sourceSha256"],
            "blockCount": len(extraction["blocks"]),
            "ownership": "USER_PROPRIETARY_MANUALLY_CURATED",
        }
        for extraction in documents
    ]
    systems = []
    for system in SYSTEMS.values():
        system_sections = [section for section in sections if section["systemId"] == system.id]
        if not system_sections:
            continue
        systems.append({
            "id": system.id,
            "title": system.title,
            "color": system.color,
            "sectionCount": len(system_sections),
            "blockCount": sum(item["blockCount"] for item in system_sections),
            "entityCount": sum(item["entityCount"] for item in system_sections),
            "realCaseCount": sum(item["realCaseCount"] for item in system_sections),
        })

    entity_index = {
        "schemaVersion": SCHEMA_VERSION,
        "corpusId": CORPUS_ID,
        "corpusVersion": CORPUS_VERSION,
        "vehicleLabel": VEHICLE_LABEL,
        "entities": entities,
    }
    entity_index["contentSha256"] = sha256_text(canonical_json(entity_index))
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "corpusId": CORPUS_ID,
        "corpusVersion": CORPUS_VERSION,
        "title": "MEET · Base propietaria completa de piezas y conocimiento automotriz",
        "vehicleLabel": VEHICLE_LABEL,
        "provenanceLabel": "Fuente propietaria del usuario · contenido conservado literalmente",
        "visualAuthority": "PROCEDURAL_SCHEMATIC",
        "sourceDocuments": source_documents,
        "systems": systems,
        "sections": sections,
        "entityIndexPath": "knowledge/proprietary/entity_index.json",
        "statistics": {
            "blockCount": sum(item["blockCount"] for item in source_documents),
            "entityCount": sum(1 for item in entities if item["recordRole"] == "COMPONENT"),
            "realCaseCount": sum(1 for item in entities if item["recordRole"] == "REAL_CASE"),
            "sectionCount": len(sections),
            "shardCount": len(shards),
            "roleCounts": dict(sorted(total_role_counts.items())),
        },
    }
    manifest["contentSha256"] = sha256_text(canonical_json(manifest))
    return manifest, entity_index, shards


def validate_catalog(manifest: dict[str, Any], entity_index: dict[str, Any], shards: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    expected_total = sum(item["blockCount"] for item in EXPECTED_SOURCES.values())
    if manifest["statistics"]["blockCount"] != expected_total:
        errors.append("manifest block count mismatch")
    if manifest["vehicleLabel"] != VEHICLE_LABEL or entity_index["vehicleLabel"] != VEHICLE_LABEL:
        errors.append("vehicle label mismatch")
    if len(shards) != manifest["statistics"]["shardCount"]:
        errors.append("shard count mismatch")

    seen_blocks: set[tuple[str, str]] = set()
    shard_paths = set(shards)
    section_ids = {section["id"] for section in manifest["sections"]}
    for path, shard in shards.items():
        expected_sha = shard["contentSha256"]
        unhashed = {key: value for key, value in shard.items() if key != "contentSha256"}
        if sha256_text(canonical_json(unhashed)) != expected_sha:
            errors.append(f"shard hash mismatch: {path}")
        document_id = shard["sourceDocumentId"]
        for block in shard["blocks"]:
            key = (document_id, block["blockId"])
            if key in seen_blocks:
                errors.append(f"duplicate source block: {document_id}/{block['blockId']}")
            seen_blocks.add(key)
            if sha256_text(block["text"]) != block["textHash"]:
                errors.append(f"source text changed: {document_id}/{block['blockId']}")
    if len(seen_blocks) != expected_total:
        errors.append(f"literal coverage mismatch: {len(seen_blocks)} != {expected_total}")

    entity_ids: set[str] = set()
    for entity in entity_index["entities"]:
        if entity["id"] in entity_ids:
            errors.append(f"duplicate entity id: {entity['id']}")
        entity_ids.add(entity["id"])
        if entity["shardPath"] not in shard_paths:
            errors.append(f"missing shard for entity: {entity['id']}")
        if entity["sectionId"] not in section_ids:
            errors.append(f"missing section for entity: {entity['id']}")
        if entity["threeDimensionalBinding"]["nodeId"] != entity["id"]:
            errors.append(f"broken 3D binding: {entity['id']}")
        if entity["threeDimensionalBinding"]["sceneId"] not in SYSTEMS:
            errors.append(f"unknown 3D scene: {entity['id']}")
        if (entity["sourceDocumentId"], entity["sourceBlockId"]) not in seen_blocks:
            errors.append(f"entity references missing block: {entity['id']}")
    expected_entities = manifest["statistics"]["entityCount"] + manifest["statistics"]["realCaseCount"]
    if len(entity_ids) != expected_entities:
        errors.append("entity count mismatch")
    return errors


def write_catalog(root: Path, manifest: dict[str, Any], entity_index: dict[str, Any], shards: dict[str, dict[str, Any]]) -> None:
    target = root / "knowledge" / "proprietary"
    if target.exists():
        shutil.rmtree(target)
    write_json_atomic(target / "manifest.json", manifest)
    write_json_atomic(target / "entity_index.json", entity_index)
    prefix = "knowledge/proprietary/"
    for relative_path, payload in shards.items():
        if not relative_path.startswith(prefix):
            raise ValueError(f"Unsafe shard path: {relative_path}")
        write_json_atomic(root / relative_path, payload)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", action="append", type=Path, required=True, help="Extracted JSON; pass twice")
    parser.add_argument("--android-assets-root", type=Path, required=True)
    parser.add_argument("--web-public-root", type=Path)
    parser.add_argument("--max-blocks-per-shard", type=int, default=360)
    parser.add_argument("--report", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    documents = load_and_validate_extractions(args.input)
    manifest, entity_index, shards = build_catalog(documents, max_blocks_per_shard=args.max_blocks_per_shard)
    errors = validate_catalog(manifest, entity_index, shards)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    write_catalog(args.android_assets_root, manifest, entity_index, shards)
    if args.web_public_root:
        write_catalog(args.web_public_root, manifest, entity_index, shards)
    if args.report:
        write_json_atomic(args.report, {
            "status": "PASS",
            "contentSha256": manifest["contentSha256"],
            "statistics": manifest["statistics"],
            "sourceDocuments": manifest["sourceDocuments"],
        })
    print(canonical_json({"status": "PASS", **manifest["statistics"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
