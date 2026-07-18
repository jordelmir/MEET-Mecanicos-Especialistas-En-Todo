#!/usr/bin/env python3
"""Build the conservative MEET front-end parts pilot from DOCX extractions."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from common import normalize_text, sha256_text, write_json_atomic


PACK_ID = "pilot_hyundai_accent_verna_2005_front_end"
PACK_VERSION = "1.0.0"


@dataclass(frozen=True)
class PartSeed:
    id: str
    name_es: str
    name_en: str
    aliases: tuple[str, ...]
    category: str
    system: str
    subsystem: str
    assembly: str
    position: str
    source_terms: tuple[str, ...]


def seed(
    part_id: str,
    name_es: str,
    name_en: str,
    aliases: Iterable[str],
    category: str,
    system: str,
    subsystem: str,
    assembly: str,
    position: str,
    source_terms: Iterable[str],
) -> PartSeed:
    return PartSeed(
        id=part_id,
        name_es=name_es,
        name_en=name_en,
        aliases=tuple(aliases),
        category=category,
        system=system,
        subsystem=subsystem,
        assembly=assembly,
        position=position,
        source_terms=tuple(source_terms),
    )


PARTS: tuple[PartSeed, ...] = (
    seed("front_subframe", "Bastidor auxiliar delantero", "Front subframe", ("cuna de motor", "subframe delantero", "crossmember delantero"), "Estructura", "Suspension / Chasis", "Subchasis", "Tren delantero", "CENTER", ("bastidor auxiliar", "subchasis", "cuna del motor")),
    seed("subframe_bolts", "Pernos del bastidor auxiliar", "Subframe bolts", ("tornillos de cuna", "subframe bolts"), "Fijacion", "Suspension / Chasis", "Fijaciones", "Subchasis", "CENTER", ("pernos del bastidor auxiliar", "tornillos del subchasis", "bastidor auxiliar delantero")),
    seed("engine_mount_front", "Soporte de motor delantero", "Front engine mount", ("soporte frontal de motor", "front mount"), "Soportes", "Motor", "Monturas", "Vano motor", "FRONT", ("soporte de motor", "montura de motor")),
    seed("front_left_wheel_bearing", "Rodamiento de rueda delantero izquierdo", "Front left wheel bearing", ("balinera delantera izquierda", "wheel bearing left"), "Rodamientos", "Suspension", "Conjunto de rueda", "Mangueta", "LEFT", ("rodamiento de rueda", "cojinete de rueda")),
    seed("front_right_wheel_bearing", "Rodamiento de rueda delantero derecho", "Front right wheel bearing", ("balinera delantera derecha", "wheel bearing right"), "Rodamientos", "Suspension", "Conjunto de rueda", "Mangueta", "RIGHT", ("rodamiento de rueda", "cojinete de rueda")),
    seed("front_left_lower_control_arm", "Brazo de control inferior delantero izquierdo", "Front left lower control arm", ("tijereta izquierda", "tijera izquierda", "trapecio izquierdo", "brazo inferior izquierdo", "control arm left"), "Direccion / Suspension", "Suspension", "Brazos", "Subchasis", "LEFT", ("brazo de control inferior", "brazo inferior", "tijereta")),
    seed("front_right_lower_control_arm", "Brazo de control inferior delantero derecho", "Front right lower control arm", ("tijereta derecha", "tijera derecha", "trapecio derecho", "brazo inferior derecho", "control arm right"), "Direccion / Suspension", "Suspension", "Brazos", "Subchasis", "RIGHT", ("brazo de control inferior", "brazo inferior", "tijereta")),
    seed("front_left_arm_front_bushing", "Buje delantero del brazo izquierdo", "Front left control arm front bushing", ("bushing pequeno izquierdo", "silentblock delantero izquierdo"), "Bujes", "Suspension", "Bujes de brazo", "Brazo de control", "LEFT", ("buje del brazo", "bujes del brazo", "silentblock")),
    seed("front_left_arm_rear_bushing", "Buje trasero del brazo izquierdo", "Front left control arm rear bushing", ("bushing grande izquierdo", "casquillo trasero izquierdo"), "Bujes", "Suspension", "Bujes de brazo", "Brazo de control", "LEFT", ("buje del brazo", "bujes del brazo", "silentblock")),
    seed("front_right_arm_front_bushing", "Buje delantero del brazo derecho", "Front right control arm front bushing", ("bushing pequeno derecho", "silentblock delantero derecho"), "Bujes", "Suspension", "Bujes de brazo", "Brazo de control", "RIGHT", ("buje del brazo", "bujes del brazo", "silentblock")),
    seed("front_right_arm_rear_bushing", "Buje trasero del brazo derecho", "Front right control arm rear bushing", ("bushing grande derecho", "casquillo trasero derecho"), "Bujes", "Suspension", "Bujes de brazo", "Brazo de control", "RIGHT", ("buje del brazo", "bujes del brazo", "silentblock")),
    seed("front_left_ball_joint", "Rotula inferior delantera izquierda", "Front left lower ball joint", ("rotula izquierda", "ball joint left"), "Direccion / Suspension", "Suspension", "Rotulas", "Brazo de control", "LEFT", ("rotula inferior", "rotula de suspension", "rotula")),
    seed("front_right_ball_joint", "Rotula inferior delantera derecha", "Front right lower ball joint", ("rotula derecha", "ball joint right"), "Direccion / Suspension", "Suspension", "Rotulas", "Brazo de control", "RIGHT", ("rotula inferior", "rotula de suspension", "rotula")),
    seed("front_left_strut", "Amortiguador delantero izquierdo", "Front left strut", ("strut izquierdo", "amortiguador izquierdo"), "Suspension", "Suspension", "Amortiguadores", "Torre de suspension", "LEFT", ("amortiguador delantero", "amortiguador")),
    seed("front_right_strut", "Amortiguador delantero derecho", "Front right strut", ("strut derecho", "amortiguador derecho"), "Suspension", "Suspension", "Amortiguadores", "Torre de suspension", "RIGHT", ("amortiguador delantero", "amortiguador")),
    seed("front_left_spring", "Resorte helicoidal delantero izquierdo", "Front left coil spring", ("espiral izquierdo", "spring left"), "Suspension", "Suspension", "Resortes", "Amortiguador", "LEFT", ("resorte helicoidal", "muelle helicoidal", "resorte de suspension")),
    seed("front_right_spring", "Resorte helicoidal delantero derecho", "Front right coil spring", ("espiral derecho", "spring right"), "Suspension", "Suspension", "Resortes", "Amortiguador", "RIGHT", ("resorte helicoidal", "muelle helicoidal", "resorte de suspension")),
    seed("front_left_strut_mount", "Copela superior delantera izquierda", "Front left strut mount", ("base de amortiguador izquierda", "strut mount left"), "Suspension", "Suspension", "Copelas", "Carroceria", "LEFT", ("copela", "soporte superior del amortiguador", "base del amortiguador")),
    seed("front_right_strut_mount", "Copela superior delantera derecha", "Front right strut mount", ("base de amortiguador derecha", "strut mount right"), "Suspension", "Suspension", "Copelas", "Carroceria", "RIGHT", ("copela", "soporte superior del amortiguador", "base del amortiguador")),
    seed("front_left_strut_bearing", "Rodamiento de copela delantero izquierdo", "Front left strut bearing", ("balinera de copela izquierda", "strut bearing left"), "Rodamientos", "Suspension", "Copelas", "Copela superior", "LEFT", ("rodamiento de copela", "cojinete del amortiguador", "rodamiento superior")),
    seed("front_right_strut_bearing", "Rodamiento de copela delantero derecho", "Front right strut bearing", ("balinera de copela derecha", "strut bearing right"), "Rodamientos", "Suspension", "Copelas", "Copela superior", "RIGHT", ("rodamiento de copela", "cojinete del amortiguador", "rodamiento superior")),
    seed("front_left_bump_stop", "Tope de amortiguador izquierdo", "Front left bump stop", ("tope de goma izquierdo", "bump stop left"), "Topes", "Suspension", "Amortiguadores", "Vastago de amortiguador", "LEFT", ("tope de amortiguador", "tope de suspension", "bump stop")),
    seed("front_right_bump_stop", "Tope de amortiguador derecho", "Front right bump stop", ("tope de goma derecho", "bump stop right"), "Topes", "Suspension", "Amortiguadores", "Vastago de amortiguador", "RIGHT", ("tope de amortiguador", "tope de suspension", "bump stop")),
    seed("front_left_dust_boot", "Guardapolvo de amortiguador izquierdo", "Front left strut dust boot", ("bota protectora izquierda", "dust boot left"), "Guardapolvos", "Suspension", "Amortiguadores", "Vastago de amortiguador", "LEFT", ("guardapolvo del amortiguador", "guardapolvo", "dust boot")),
    seed("front_right_dust_boot", "Guardapolvo de amortiguador derecho", "Front right strut dust boot", ("bota protectora derecha", "dust boot right"), "Guardapolvos", "Suspension", "Amortiguadores", "Vastago de amortiguador", "RIGHT", ("guardapolvo del amortiguador", "guardapolvo", "dust boot")),
    seed("stabilizer_bar", "Barra estabilizadora delantera", "Front stabilizer bar", ("barra estabilizadora", "sway bar"), "Direccion / Suspension", "Suspension", "Barras", "Subchasis", "CENTER", ("barra estabilizadora", "sway bar")),
    seed("left_stabilizer_link", "Bieleta estabilizadora izquierda", "Left stabilizer link", ("link kit izquierdo", "bieleta izquierda", "stabilizer link left"), "Direccion / Suspension", "Suspension", "Bieletas estabilizadoras", "Mangueta", "LEFT", ("bieleta estabilizadora", "bieleta de la barra", "bieleta")),
    seed("right_stabilizer_link", "Bieleta estabilizadora derecha", "Right stabilizer link", ("link kit derecho", "bieleta derecha", "stabilizer link right"), "Direccion / Suspension", "Suspension", "Bieletas estabilizadoras", "Mangueta", "RIGHT", ("bieleta estabilizadora", "bieleta de la barra", "bieleta")),
    seed("stabilizer_bushing_left", "Buje de barra estabilizadora izquierdo", "Left stabilizer bar bushing", ("bushing de barra izquierdo", "buje estabilizador izquierdo"), "Bujes", "Suspension", "Bujes de barra", "Soporte de barra", "LEFT", ("buje de barra estabilizadora", "bujes de barra estabilizadora", "bujes de la barra", "stabilizer bushing")),
    seed("stabilizer_bushing_right", "Buje de barra estabilizadora derecho", "Right stabilizer bar bushing", ("bushing de barra derecho", "buje estabilizador derecho"), "Bujes", "Suspension", "Bujes de barra", "Soporte de barra", "RIGHT", ("buje de barra estabilizadora", "bujes de barra estabilizadora", "bujes de la barra", "stabilizer bushing")),
    seed("front_left_knuckle", "Mangueta de rueda delantera izquierda", "Front left steering knuckle", ("portamasa izquierdo", "munon izquierdo", "knuckle left"), "Estructura", "Suspension", "Manguetas", "Conjunto de rueda", "LEFT", ("mangueta", "portamangueta", "steering knuckle")),
    seed("front_right_knuckle", "Mangueta de rueda delantera derecha", "Front right steering knuckle", ("portamasa derecho", "munon derecho", "knuckle right"), "Estructura", "Suspension", "Manguetas", "Conjunto de rueda", "RIGHT", ("mangueta", "portamangueta", "steering knuckle")),
    seed("front_left_wheel_hub", "Cubo de rueda delantero izquierdo", "Front left wheel hub", ("manzana de rueda izquierda", "wheel hub left"), "Estructura", "Suspension", "Cubo de rueda", "Mangueta", "LEFT", ("cubo de rueda", "wheel hub")),
    seed("front_right_wheel_hub", "Cubo de rueda delantero derecho", "Front right wheel hub", ("manzana de rueda derecha", "wheel hub right"), "Estructura", "Suspension", "Cubo de rueda", "Mangueta", "RIGHT", ("cubo de rueda", "wheel hub")),
    seed("wheel_nuts_front_left", "Tuercas de rueda delanteras izquierdas", "Front left wheel nuts", ("pernos de rueda izquierdos", "wheel nuts front left"), "Fijacion", "Frenos / Ruedas", "Fijaciones", "Cubo de rueda", "LEFT", ("tuercas de rueda", "pernos de rueda", "wheel nuts")),
    seed("wheel_nuts_front_right", "Tuercas de rueda delanteras derechas", "Front right wheel nuts", ("pernos de rueda derechos", "wheel nuts front right"), "Fijacion", "Frenos / Ruedas", "Fijaciones", "Cubo de rueda", "RIGHT", ("tuercas de rueda", "pernos de rueda", "wheel nuts")),
    seed("steering_rack", "Cremallera de direccion asistida", "Power steering rack", ("caja de direccion", "steering gear"), "Direccion", "Direccion", "Cremallera", "Subchasis", "CENTER", ("cremallera de direccion", "caja de direccion", "steering rack")),
    seed("tie_rod_end_left", "Terminal de direccion exterior izquierdo", "Left outer tie rod end", ("terminal de direccion izquierdo", "tie rod end left"), "Direccion / Suspension", "Direccion", "Terminales", "Mangueta", "LEFT", ("terminal de direccion", "rotula axial", "tie rod end")),
    seed("tie_rod_end_right", "Terminal de direccion exterior derecho", "Right outer tie rod end", ("terminal de direccion derecho", "tie rod end right"), "Direccion / Suspension", "Direccion", "Terminales", "Mangueta", "RIGHT", ("terminal de direccion", "rotula axial", "tie rod end")),
    seed("tie_rod_inner_left", "Bieleta de direccion interior izquierda", "Left inner tie rod", ("axial de direccion izquierda", "inner tie rod left"), "Direccion / Suspension", "Direccion", "Bieletas de direccion", "Cremallera", "LEFT", ("terminal interior", "barra axial", "inner tie rod")),
    seed("tie_rod_inner_right", "Bieleta de direccion interior derecha", "Right inner tie rod", ("axial de direccion derecha", "inner tie rod right"), "Direccion / Suspension", "Direccion", "Bieletas de direccion", "Cremallera", "RIGHT", ("terminal interior", "barra axial", "inner tie rod")),
    seed("drive_shaft_left", "Semieje delantero izquierdo", "Front left drive shaft", ("eje delantero izquierdo", "cv shaft left"), "Transmision / Tren motriz", "Transmision", "Semiejes", "Cubo de rueda", "LEFT", ("semieje", "eje homocinetico", "drive shaft")),
    seed("drive_shaft_right", "Semieje delantero derecho", "Front right drive shaft", ("eje delantero derecho", "cv shaft right"), "Transmision / Tren motriz", "Transmision", "Semiejes", "Cubo de rueda", "RIGHT", ("semieje", "eje homocinetico", "drive shaft")),
    seed("brake_disc_left", "Disco de freno delantero izquierdo", "Front left brake disc", ("disco izquierdo", "brake rotor left"), "Frenos", "Frenos", "Discos", "Cubo de rueda", "LEFT", ("disco de freno", "brake disc", "brake rotor")),
    seed("brake_disc_right", "Disco de freno delantero derecho", "Front right brake disc", ("disco derecho", "brake rotor right"), "Frenos", "Frenos", "Discos", "Cubo de rueda", "RIGHT", ("disco de freno", "brake disc", "brake rotor")),
    seed("brake_caliper_left", "Mordaza de freno delantera izquierda", "Front left brake caliper", ("caliper izquierdo", "pinza izquierda"), "Frenos", "Frenos", "Mordazas", "Mangueta", "LEFT", ("mordaza de freno", "pinza de freno", "caliper")),
    seed("brake_caliper_right", "Mordaza de freno delantera derecha", "Front right brake caliper", ("caliper derecho", "pinza derecha"), "Frenos", "Frenos", "Mordazas", "Mangueta", "RIGHT", ("mordaza de freno", "pinza de freno", "caliper")),
    seed("brake_pads_front", "Pastillas de freno delanteras", "Front brake pads", ("fricciones delanteras", "pastillas de freno"), "Frenos", "Frenos", "Pastillas", "Mordaza", "CENTER", ("pastillas de freno", "brake pads")),
    seed("front_left_abs_sensor", "Sensor ABS delantero izquierdo", "Front left ABS sensor", ("sensor de velocidad izquierdo", "wheel speed sensor left"), "Sensores", "Electrico", "Sensores ABS", "Mangueta", "LEFT", ("sensor abs", "sensor de velocidad de rueda", "wheel speed sensor")),
    seed("front_right_abs_sensor", "Sensor ABS delantero derecho", "Front right ABS sensor", ("sensor de velocidad derecho", "wheel speed sensor right"), "Sensores", "Electrico", "Sensores ABS", "Mangueta", "RIGHT", ("sensor abs", "sensor de velocidad de rueda", "wheel speed sensor")),
)


def canonical_json(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def load_extractions(paths: Iterable[Path]) -> list[dict[str, Any]]:
    documents: list[dict[str, Any]] = []
    for path in paths:
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        if not isinstance(payload.get("blocks"), list) or not payload.get("document"):
            raise ValueError(f"Invalid extraction: {path}")
        documents.append(payload)
    return documents


def find_source(seed_part: PartSeed, documents: list[dict[str, Any]]) -> dict[str, Any]:
    normalized_terms = tuple(normalize_text(term) for term in seed_part.source_terms)
    best: tuple[int, dict[str, Any], dict[str, Any]] | None = None
    for document in documents:
        for block in document["blocks"]:
            normalized = normalize_text(block.get("text", ""))
            matched = [term for term in normalized_terms if term and term in normalized]
            if not matched:
                continue
            score = max(len(term) for term in matched)
            if best is None or score > best[0]:
                best = (score, document, block)
    if best is None:
        raise ValueError(f"No source block found for {seed_part.id}: {seed_part.source_terms}")
    _, document, block = best
    source = document["document"]
    return {
        "sourceFileName": source["sourceFileName"],
        "sourceDocumentSha256": source["sourceSha256"],
        "sourceBlockId": block["blockId"],
        "sourceTextHash": block["textHash"],
        "sectionPath": block.get("sectionPath", []),
        "sourceKind": block["kind"],
        "reviewStatus": "PENDING_TECHNICAL_REVIEW",
    }


def build_part(seed_part: PartSeed, documents: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "id": seed_part.id,
        "nameEs": seed_part.name_es,
        "nameEn": seed_part.name_en,
        "aliases": list(seed_part.aliases),
        "category": seed_part.category,
        "system": seed_part.system,
        "subsystem": seed_part.subsystem,
        "assembly": seed_part.assembly,
        "subassembly": None,
        "position": seed_part.position,
        "description": "Entidad de catalogo encontrada en las fuentes. Sus datos tecnicos y su aplicabilidad a la variante requieren revision.",
        "confidence": "UNVERIFIED",
        "publicationState": "REVIEW_REQUIRED",
        "compatibilityState": "REQUIRES_VERIFICATION",
        "compatibilityMessage": "Compatibilidad probable; requiere confirmar por VIN/OEM/foto/conector/medidas y mercado.",
        "requiredCompatibilityEvidence": ["VIN", "OEM_REFERENCE", "PART_PHOTO", "CONNECTOR_OR_DIMENSIONS", "MARKET"],
        "technicalSpecifications": {
            "oemNumber": None,
            "torque": None,
            "material": None,
            "dimensions": None,
            "pinout": None,
        },
        "sourceRefs": [find_source(seed_part, documents)],
        "threeDimensionalBinding": {
            "sceneId": "front_suspension_generic",
            "nodeId": seed_part.id,
            "visualAuthority": "GENERIC_SCHEMATIC",
            "isDimensionalModel": False,
        },
    }


def procedure_step(
    procedure_id: str,
    number: int,
    title: str,
    instruction: str,
    action: str,
    *,
    warning: str | None = None,
    evidence: Iterable[str] = (),
    tools: Iterable[str] = (),
    completion_gate: str = "MANUAL_CONFIRMATION",
) -> dict[str, Any]:
    return {
        "id": f"{procedure_id}_step_{number:02d}",
        "order": number,
        "title": title,
        "instruction": instruction,
        "warning": warning,
        "tools": list(tools),
        "requiredEvidence": list(evidence),
        "targetPartId": "front_left_lower_control_arm",
        "targetNodeId": "front_left_lower_control_arm",
        "animationAction": action,
        "completionGate": completion_gate,
        "technicalValue": None,
        "technicalValueMessage": "No confirmado para esta variante" if completion_gate == "VERIFIED_TORQUE_REQUIRED" else None,
    }


def build_procedures(source_ref: dict[str, Any]) -> list[dict[str, Any]]:
    inspect_id = "inspect_front_left_lower_control_arm"
    replace_id = "replace_front_left_lower_control_arm_training"
    verify_id = "verify_front_left_lower_control_arm_service"
    common = {
        "targetPartIds": ["front_left_lower_control_arm"],
        "publicationState": "REVIEW_REQUIRED",
        "executionPolicy": "TRAINING_ONLY_REVIEW_REQUIRED",
        "sourceRefs": [source_ref],
    }
    return [
        {
            **common,
            "id": inspect_id,
            "title": "Inspeccion conservadora del brazo inferior izquierdo",
            "difficulty": "INTERMEDIATE",
            "safetyLevel": "CAUTION",
            "steps": [
                procedure_step(inspect_id, 1, "Preparar el vehiculo", "Inmovilice el vehiculo y use puntos de elevacion confirmados para la variante.", "HIGHLIGHT", warning="No trabaje bajo un vehiculo sostenido solo por un gato.", evidence=("vehicle_supported_photo",), tools=("soportes certificados",)),
                procedure_step(inspect_id, 2, "Localizar la tijereta", "Identifique visualmente el brazo inferior izquierdo, sus dos bujes y la rotula.", "ISOLATE", evidence=("part_location_photo",)),
                procedure_step(inspect_id, 3, "Inspeccionar bujes", "Busque grietas, separacion, deformacion o contacto metal-metal sin asumir falla por apariencia aislada.", "HIGHLIGHT", evidence=("bushing_condition_photo",)),
                procedure_step(inspect_id, 4, "Evaluar holgura", "Compruebe holgura con un metodo seguro y compare con una fuente tecnica aprobada antes de condenar la pieza.", "HIGHLIGHT", warning="No coloque manos en puntos de atrapamiento.", evidence=("inspection_note",)),
                procedure_step(inspect_id, 5, "Registrar conclusion", "Registre hallazgos, incertidumbre y pruebas faltantes; no marque compatibilidad exacta ni reemplazo obligatorio sin evidencia.", "RESET", evidence=("inspection_result",)),
            ],
        },
        {
            **common,
            "id": replace_id,
            "title": "Sustitucion guiada de tijereta izquierda",
            "difficulty": "ADVANCED",
            "safetyLevel": "DANGER",
            "steps": [
                procedure_step(replace_id, 1, "Validar repuesto y procedimiento", "Confirme VIN, referencia OEM o equivalencia dimensional, mercado y manual aplicable antes de desmontar.", "HIGHLIGHT", warning="Este flujo es de entrenamiento hasta completar revision tecnica.", evidence=("vin_or_vehicle_scope", "replacement_part_evidence"), completion_gate="COMPATIBILITY_EVIDENCE_REQUIRED"),
                procedure_step(replace_id, 2, "Asegurar y descargar el conjunto", "Asegure el vehiculo y controle la carga del conjunto de suspension con herramientas adecuadas.", "ISOLATE", warning="Riesgo de caida, atrapamiento y energia almacenada.", evidence=("safe_setup_photo",)),
                procedure_step(replace_id, 3, "Documentar montaje original", "Fotografie orientacion, fijaciones, arneses y posicion antes de aflojar.", "HIGHLIGHT", evidence=("before_disassembly_photo",)),
                procedure_step(replace_id, 4, "Separar conexiones", "Separe solo las conexiones confirmadas por el manual aplicable, protegiendo mangueras, arnes ABS y guardapolvos.", "REMOVE", warning="No golpee ni estire el arnes ABS o la manguera de freno.", evidence=("connections_released_photo",)),
                procedure_step(replace_id, 5, "Retirar el brazo", "Retire las fijaciones y el brazo manteniendo control del conjunto. Conserve la orientacion para comparacion.", "REMOVE", evidence=("removed_part_photo",)),
                procedure_step(replace_id, 6, "Comparar la pieza", "Compare forma, puntos de montaje, bujes, rotula y dimensiones funcionales antes de instalar.", "ISOLATE", evidence=("old_new_comparison_photo",), completion_gate="COMPATIBILITY_EVIDENCE_REQUIRED"),
                procedure_step(replace_id, 7, "Instalar y presentar fijaciones", "Posicione la pieza sin forzar y presente todas las fijaciones segun el procedimiento aprobado.", "INSTALL", evidence=("installed_before_torque_photo",)),
                procedure_step(replace_id, 8, "Aplicar torque final", "Use exclusivamente el valor, secuencia y condicion de carga de una fuente verificada para esta variante.", "HIGHLIGHT", warning="No use un valor generico ni el valor de otra variante.", evidence=("verified_torque_source", "torque_evidence"), tools=("torquimetro calibrado",), completion_gate="VERIFIED_TORQUE_REQUIRED"),
                procedure_step(replace_id, 9, "Revisar interferencias", "Compruebe recorrido, arneses, mangueras, guardapolvos y fijaciones antes de apoyar el vehiculo.", "RESET", evidence=("post_install_photo",)),
            ],
        },
        {
            **common,
            "id": verify_id,
            "title": "Verificacion posterior, alineacion y prueba",
            "difficulty": "INTERMEDIATE",
            "safetyLevel": "CAUTION",
            "steps": [
                procedure_step(verify_id, 1, "Inspeccion estatica", "Revise posicion, fijaciones, guardapolvos, mangueras y arneses con el vehiculo en condicion segura.", "HIGHLIGHT", evidence=("static_inspection_photo",)),
                procedure_step(verify_id, 2, "Alinear el vehiculo", "Realice o programe alineacion y conserve el resultado medido.", "ISOLATE", evidence=("alignment_report",), completion_gate="ALIGNMENT_EVIDENCE_REQUIRED"),
                procedure_step(verify_id, 3, "Prueba funcional controlada", "Efectue una prueba a baja velocidad en un area segura, verificando ruidos, direccion, frenado y estabilidad.", "RESET", warning="Detenga la prueba ante ruido, juego, tiron o comportamiento inseguro.", evidence=("road_test_result",)),
                procedure_step(verify_id, 4, "Reinspeccion final", "Reinspeccione el conjunto y registre cualquier condicion pendiente antes de cerrar el trabajo.", "HIGHLIGHT", evidence=("final_inspection_photo", "final_result")),
            ],
        },
    ]


def validate_pack(pack: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    parts = pack.get("parts", [])
    procedures = pack.get("procedures", [])
    ids = [part.get("id") for part in parts]
    if len(parts) < 50:
        errors.append("Pilot must contain at least 50 parts")
    if len(ids) != len(set(ids)):
        errors.append("Part IDs must be unique")
    known = set(ids)
    for part in parts:
        if not part.get("sourceRefs"):
            errors.append(f"{part.get('id')} has no source reference")
        if part.get("confidence") != "UNVERIFIED" or part.get("publicationState") != "REVIEW_REQUIRED":
            errors.append(f"{part.get('id')} is promoted without review")
        if part.get("compatibilityState") != "REQUIRES_VERIFICATION":
            errors.append(f"{part.get('id')} has unsafe compatibility state")
        critical = part.get("technicalSpecifications", {})
        if any(value is not None for value in critical.values()):
            errors.append(f"{part.get('id')} contains unverified technical specifications")
        binding = part.get("threeDimensionalBinding", {})
        if binding.get("nodeId") != part.get("id") or binding.get("visualAuthority") != "GENERIC_SCHEMATIC":
            errors.append(f"{part.get('id')} has an invalid 3D binding")
    if len(procedures) < 3:
        errors.append("Pilot must contain at least three procedures")
    step_ids: list[str] = []
    for procedure in procedures:
        for target_id in procedure.get("targetPartIds", []):
            if target_id not in known:
                errors.append(f"{procedure.get('id')} targets unknown part {target_id}")
        for step in procedure.get("steps", []):
            step_ids.append(step.get("id"))
            if step.get("targetPartId") not in known or step.get("targetNodeId") not in known:
                errors.append(f"{step.get('id')} has a broken target")
            if step.get("completionGate") == "VERIFIED_TORQUE_REQUIRED" and step.get("technicalValue") is not None:
                errors.append(f"{step.get('id')} exposes an unverified torque")
    if len(step_ids) != len(set(step_ids)):
        errors.append("Procedure step IDs must be unique")
    return errors


def build_pack(documents: list[dict[str, Any]]) -> dict[str, Any]:
    parts = [build_part(item, documents) for item in PARTS]
    lower_arm_source = next(part for part in parts if part["id"] == "front_left_lower_control_arm")["sourceRefs"][0]
    pack: dict[str, Any] = {
        "schemaVersion": 1,
        "packId": PACK_ID,
        "packVersion": PACK_VERSION,
        "title": "MEET Pilot: tren delantero fuente-a-UI",
        "publicationState": "REVIEW_REQUIRED",
        "autoPublishAllowed": False,
        "disclaimer": "Contenido de entrenamiento y revision. Confirme VIN, OEM y manual aplicable antes de intervenir.",
        "vehicleScope": {
            "vehicleProfileId": "hyundai_accent_verna_2005_1_6_at",
            "make": "Hyundai",
            "models": ["Accent", "Verna"],
            "year": 2005,
            "engineDisplacementLiters": 1.6,
            "transmission": "AUTOMATIC",
            "bindingState": "TARGET_PROFILE_NOT_EXACT_COMPATIBILITY",
        },
        "sourceDocuments": [
            {
                "sourceFileName": item["document"]["sourceFileName"],
                "sourceSha256": item["document"]["sourceSha256"],
            }
            for item in documents
        ],
        "parts": parts,
        "procedures": build_procedures(lower_arm_source),
    }
    pack["statistics"] = {
        "partCount": len(parts),
        "procedureCount": len(pack["procedures"]),
        "verifiedTechnicalSpecificationCount": 0,
        "automaticallyPublishableCount": 0,
    }
    errors = validate_pack(pack)
    if errors:
        raise ValueError("Invalid pilot pack:\n- " + "\n- ".join(errors))
    pack["contentSha256"] = hashlib.sha256(canonical_json(pack).encode("utf-8")).hexdigest()
    return pack


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--extracted", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        pack = build_pack(load_extractions(args.extracted))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    write_json_atomic(args.output, pack)
    print(
        f"Generated {args.output}: {pack['statistics']['partCount']} parts, "
        f"{pack['statistics']['procedureCount']} procedures, {pack['contentSha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
