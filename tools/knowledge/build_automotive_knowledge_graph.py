#!/usr/bin/env python3
"""Compile MEET's immutable automotive corpus into one evidence-gated graph."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import sys
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

from jsonschema import Draft202012Validator


GRAPH_ID = "meet_automotive_knowledge_graph"
GRAPH_VERSION = "1.0.0"
MANIFEST_RELATIVE_PATH = Path("public/knowledge/proprietary/manifest.json")
ENTITY_INDEX_RELATIVE_PATH = Path("public/knowledge/proprietary/entity_index.json")
CURATED_OVERLAY_RELATIVE_PATH = Path(
    "tools/knowledge/curated/accent_verna_2005_knowledge.json"
)
SCHEMA_RELATIVE_PATH = Path(
    "tools/knowledge/schema/automotive-knowledge-graph.schema.json"
)
PUBLIC_GRAPH_RELATIVE_PATH = Path(
    "public/knowledge/graph/automotive_knowledge_graph.json"
)
ANDROID_GRAPH_RELATIVE_PATH = Path(
    "android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json"
)

SET_LIKE_ARRAY_FIELDS = {
    "curatedSourceIds",
    "evidenceRequired",
    "models",
    "observedEvidenceIds",
    "sourceBlockIds",
}
FORBIDDEN_GRAPH_KEYS = {
    "generatedAt",
    "generatedAtEpochMs",
    "rows",
    "text",
    "timestamp",
}
SOURCE_REF_FIELDS = ("sourceDocumentId", "blockId", "textHash")


def canonical_json_bytes(payload: Any) -> bytes:
    """Encode a payload using the graph's byte-exact canonical JSON contract."""

    return json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def _sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _content_sha256(payload: dict[str, Any]) -> str:
    unhashed = {key: value for key, value in payload.items() if key != "contentSha256"}
    return _sha256_bytes(canonical_json_bytes(unhashed))


def _load_json(path: Path) -> dict[str, Any]:
    try:
        with path.open(encoding="utf-8") as source:
            payload = json.load(
                source,
                parse_constant=lambda value: (_ for _ in ()).throw(
                    ValueError(f"non-finite JSON number {value!r}")
                ),
            )
    except (OSError, json.JSONDecodeError, ValueError) as error:
        raise ValueError(f"Cannot load valid JSON from {path}: {error}") from error
    if not isinstance(payload, dict):
        raise ValueError(f"Expected a JSON object at {path}")
    return payload


def _require_relative_path(value: str, label: str) -> Path:
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Unsafe {label}: {value}")
    return path


def _verify_content_hash(payload: dict[str, Any], label: str) -> str:
    expected = payload.get("contentSha256")
    if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError(f"Missing or invalid contentSha256 for {label}")
    actual = _content_sha256(payload)
    if actual != expected:
        raise ValueError(f"Canonical content hash mismatch for {label}: {actual} != {expected}")
    return actual


def _source_ref_key(source_ref: dict[str, Any]) -> tuple[str, str, str]:
    try:
        return tuple(source_ref[field] for field in SOURCE_REF_FIELDS)  # type: ignore[return-value]
    except KeyError as error:
        raise ValueError(f"Incomplete qualified source reference: {source_ref}") from error


def _dedupe_sorted_strings(values: Iterable[str], label: str) -> list[str]:
    normalized = list(values)
    if not all(isinstance(value, str) for value in normalized):
        raise ValueError(f"{label} must contain only strings")
    return sorted(set(normalized))


def _dedupe_sorted_source_refs(
    source_refs: Iterable[dict[str, Any]], label: str
) -> list[dict[str, str]]:
    qualified: dict[tuple[str, str, str], dict[str, str]] = {}
    for source_ref in source_refs:
        key = _source_ref_key(source_ref)
        if not all(isinstance(value, str) for value in key):
            raise ValueError(f"{label} contains a non-string source reference")
        qualified[key] = dict(zip(SOURCE_REF_FIELDS, key, strict=True))
    return [qualified[key] for key in sorted(qualified)]


def _normalize_record(value: Any, label: str = "record") -> Any:
    if isinstance(value, float):
        raise ValueError(f"Compiled graph cannot contain floats: {label}")
    if isinstance(value, dict):
        forbidden = FORBIDDEN_GRAPH_KEYS.intersection(value)
        if forbidden:
            raise ValueError(f"Compiled graph cannot contain literal/timestamp keys {sorted(forbidden)}")
        normalized: dict[str, Any] = {}
        for key, child in value.items():
            child_label = f"{label}.{key}"
            if key == "sourceRefs":
                normalized[key] = _dedupe_sorted_source_refs(child, child_label)
            elif key in SET_LIKE_ARRAY_FIELDS:
                normalized[key] = _dedupe_sorted_strings(child, child_label)
            else:
                normalized[key] = _normalize_record(child, child_label)
        return normalized
    if isinstance(value, list):
        return [_normalize_record(child, f"{label}[]") for child in value]
    return value


def _validate_source_carrier(
    carrier: dict[str, Any],
    qualified_source_refs: set[tuple[str, str, str]],
    label: str,
) -> None:
    source_refs = carrier.get("sourceRefs", [])
    source_block_ids = carrier.get("sourceBlockIds", [])
    if not isinstance(source_refs, list) or not isinstance(source_block_ids, list):
        raise ValueError(f"{label} source references must be arrays")
    qualified = [_source_ref_key(source_ref) for source_ref in source_refs]
    if len(qualified) != len(set(qualified)):
        raise ValueError(f"Duplicate qualified source reference in {label}")
    if set(source_block_ids) != {source_ref[1] for source_ref in qualified}:
        raise ValueError(f"Auxiliary sourceBlockIds do not match sourceRefs in {label}")
    unknown = set(qualified).difference(qualified_source_refs)
    if unknown:
        raise ValueError(f"Unknown qualified source reference in {label}: {sorted(unknown)[:1]}")


def _load_and_validate_corpus(repo_root: Path) -> dict[str, Any]:
    manifest_path = repo_root / MANIFEST_RELATIVE_PATH
    manifest = _load_json(manifest_path)
    manifest_sha256 = _verify_content_hash(manifest, str(MANIFEST_RELATIVE_PATH))

    manifest_index_path = _require_relative_path(
        manifest.get("entityIndexPath", ""), "entity index path"
    )
    entity_index_path = repo_root / "public" / manifest_index_path
    expected_index_path = repo_root / ENTITY_INDEX_RELATIVE_PATH
    if entity_index_path != expected_index_path:
        raise ValueError(
            f"Manifest entity index path must resolve to {ENTITY_INDEX_RELATIVE_PATH}"
        )
    entity_index = _load_json(entity_index_path)
    entity_index_sha256 = _verify_content_hash(
        entity_index, str(ENTITY_INDEX_RELATIVE_PATH)
    )

    for field in ("schemaVersion", "corpusId", "corpusVersion"):
        if entity_index.get(field) != manifest.get(field):
            raise ValueError(f"Manifest/entity index {field} mismatch")

    systems = manifest.get("systems")
    sections = manifest.get("sections")
    statistics = manifest.get("statistics")
    source_documents = manifest.get("sourceDocuments")
    if not all(
        isinstance(value, list)
        for value in (systems, sections, source_documents)
    ) or not isinstance(statistics, dict):
        raise ValueError("Manifest collections are malformed")

    system_ids = [system.get("id") for system in systems]
    section_ids = [section.get("id") for section in sections]
    shard_paths = [section.get("shardPath") for section in sections]
    if len(system_ids) != len(set(system_ids)):
        raise ValueError("Duplicate corpus system ID")
    if len(section_ids) != len(set(section_ids)):
        raise ValueError("Duplicate corpus section ID")
    if len(shard_paths) != len(set(shard_paths)):
        raise ValueError("Duplicate corpus shard path")
    if statistics.get("sectionCount") != len(sections):
        raise ValueError("Manifest section count mismatch")
    if statistics.get("shardCount") != len(sections):
        raise ValueError("Manifest shard count mismatch")

    system_id_set = set(system_ids)
    source_document_by_id = {
        document["id"]: document for document in source_documents
    }
    if len(source_document_by_id) != len(source_documents):
        raise ValueError("Duplicate source document ID")

    section_source_refs: dict[str, list[dict[str, str]]] = {}
    qualified_source_refs: set[tuple[str, str, str]] = set()
    bare_source_block_ids: set[str] = set()
    per_document_block_counts: Counter[str] = Counter()
    section_by_id: dict[str, dict[str, Any]] = {}

    for section in sections:
        section_id = section.get("id")
        system_id = section.get("systemId")
        if system_id not in system_id_set:
            raise ValueError(f"Section {section_id} references unknown system {system_id}")
        shard_relative = _require_relative_path(
            section.get("shardPath", ""), f"shard path for {section_id}"
        )
        shard_path = repo_root / "public" / shard_relative
        shard = _load_json(shard_path)
        shard_sha256 = _verify_content_hash(shard, str(shard_relative))
        if shard_sha256 != section.get("contentSha256"):
            raise ValueError(f"Manifest/shard hash mismatch for {section_id}")
        for field in ("sectionId", "systemId", "sourceDocumentId"):
            expected = section.get("id") if field == "sectionId" else section.get(field)
            if shard.get(field) != expected:
                raise ValueError(f"Shard {field} mismatch for {section_id}")
        if shard.get("corpusId") != manifest.get("corpusId"):
            raise ValueError(f"Shard corpus mismatch for {section_id}")
        document = source_document_by_id.get(shard.get("sourceDocumentId"))
        if document is None:
            raise ValueError(f"Shard {section_id} references an unknown source document")
        if shard.get("sourceDocumentSha256") != document.get("sourceSha256"):
            raise ValueError(f"Shard source document hash mismatch for {section_id}")
        blocks = shard.get("blocks")
        if not isinstance(blocks, list) or not blocks:
            raise ValueError(f"Shard {section_id} has no blocks")
        if len(blocks) != section.get("blockCount"):
            raise ValueError(f"Shard block count mismatch for {section_id}")

        refs: list[dict[str, str]] = []
        for block in blocks:
            block_id = block.get("blockId")
            text = block.get("text")
            text_hash = block.get("textHash")
            if not isinstance(block_id, str) or not isinstance(text, str) or not isinstance(text_hash, str):
                raise ValueError(f"Malformed source block in {section_id}")
            actual_text_hash = _sha256_bytes(text.encode("utf-8"))
            if actual_text_hash != text_hash:
                raise ValueError(
                    f"Source block textHash mismatch for {shard['sourceDocumentId']}/{block_id}"
                )
            qualified = (shard["sourceDocumentId"], block_id, text_hash)
            if qualified in qualified_source_refs:
                raise ValueError(f"Duplicate qualified source reference: {qualified}")
            qualified_source_refs.add(qualified)
            bare_source_block_ids.add(block_id)
            per_document_block_counts[shard["sourceDocumentId"]] += 1
            refs.append(dict(zip(SOURCE_REF_FIELDS, qualified, strict=True)))

        section_source_refs[section_id] = _dedupe_sorted_source_refs(
            refs, f"section {section_id}"
        )
        section_by_id[section_id] = section

    if len(qualified_source_refs) != statistics.get("blockCount"):
        raise ValueError("Qualified source-block coverage does not match manifest")
    for document_id, document in source_document_by_id.items():
        if per_document_block_counts[document_id] != document.get("blockCount"):
            raise ValueError(f"Source document block count mismatch for {document_id}")

    entities = entity_index.get("entities")
    if not isinstance(entities, list):
        raise ValueError("Entity index entities must be an array")
    entity_ids: set[str] = set()
    record_role_counts: Counter[str] = Counter()
    for entity in entities:
        entity_id = entity.get("id")
        if not isinstance(entity_id, str) or entity_id in entity_ids:
            raise ValueError(f"Duplicate or malformed entity ID: {entity_id}")
        entity_ids.add(entity_id)
        role = entity.get("recordRole")
        if role not in {"COMPONENT", "REAL_CASE"}:
            raise ValueError(f"Unsafe entity record role for {entity_id}: {role}")
        record_role_counts[role] += 1
        section = section_by_id.get(entity.get("sectionId"))
        if section is None:
            raise ValueError(f"Entity {entity_id} references an unknown section")
        if entity.get("systemId") != section.get("systemId"):
            raise ValueError(f"Entity {entity_id} system/section mismatch")
        if entity.get("shardPath") != section.get("shardPath"):
            raise ValueError(f"Entity {entity_id} shard/section mismatch")
        qualified = (
            entity.get("sourceDocumentId"),
            entity.get("sourceBlockId"),
            entity.get("sourceTextHash"),
        )
        if qualified not in qualified_source_refs:
            raise ValueError(f"Entity {entity_id} references an unknown qualified source block")
        document = source_document_by_id.get(entity.get("sourceDocumentId"))
        if document is None or entity.get("sourceDocumentSha256") != document.get("sourceSha256"):
            raise ValueError(f"Entity {entity_id} source document hash mismatch")
        if role == "COMPONENT" and not isinstance(entity.get("nameOriginal"), str):
            raise ValueError(f"Component {entity_id} has no source name")

    expected_entity_count = statistics.get("entityCount", 0) + statistics.get("realCaseCount", 0)
    if len(entities) != expected_entity_count:
        raise ValueError("Entity index count mismatch")
    if record_role_counts["COMPONENT"] != statistics.get("entityCount"):
        raise ValueError("Component role count mismatch")
    if record_role_counts["REAL_CASE"] != statistics.get("realCaseCount"):
        raise ValueError("Real-case role count mismatch")

    return {
        "manifest": manifest,
        "manifestSha256": manifest_sha256,
        "entityIndex": entity_index,
        "entityIndexSha256": entity_index_sha256,
        "sectionSourceRefs": section_source_refs,
        "qualifiedSourceRefs": qualified_source_refs,
        "bareSourceBlockIds": bare_source_block_ids,
        "recordRoleCounts": record_role_counts,
    }


def _load_and_validate_curated(
    repo_root: Path,
    schema: dict[str, Any],
    corpus: dict[str, Any],
) -> tuple[dict[str, Any], str, list[dict[str, Any]]]:
    curated_path = repo_root / CURATED_OVERLAY_RELATIVE_PATH
    curated = _load_json(curated_path)
    Draft202012Validator(schema).validate(curated)
    overlay_sha256 = _sha256_bytes(canonical_json_bytes(curated))
    manifest = corpus["manifest"]
    if curated.get("sourceCorpusHash") != manifest.get("contentSha256"):
        raise ValueError("Curated overlay source corpus hash mismatch")
    source_inputs = curated.get("sourceInputs", {})
    for key in ("corpusId", "corpusVersion"):
        if source_inputs.get(key) != manifest.get(key):
            raise ValueError(f"Curated overlay {key} mismatch")
    if source_inputs.get("corpusManifestPath") != MANIFEST_RELATIVE_PATH.as_posix():
        raise ValueError("Curated overlay corpus manifest path mismatch")

    pack_inputs: list[dict[str, Any]] = []
    pack_ids: set[str] = set()
    for pack_input in source_inputs.get("curatedPacks", []):
        pack_relative = _require_relative_path(
            pack_input.get("path", ""), f"curated pack path for {pack_input.get('packId')}"
        )
        pack = _load_json(repo_root / pack_relative)
        pack_id = pack_input.get("packId")
        if pack_id in pack_ids:
            raise ValueError(f"Duplicate curated pack ID: {pack_id}")
        pack_ids.add(pack_id)
        if pack.get("packId") != pack_id or pack.get("packVersion") != pack_input.get("packVersion"):
            raise ValueError(f"Curated pack identity mismatch for {pack_id}")
        traced_pack = copy.deepcopy(pack_input)
        traced_pack["contentSha256"] = _sha256_bytes(canonical_json_bytes(pack))
        pack_inputs.append(traced_pack)

    qualified_source_refs = corpus["qualifiedSourceRefs"]
    source_carriers: list[tuple[str, dict[str, Any]]] = []
    for collection_name in ("nodes", "edges", "applicabilityRules"):
        for record in curated.get(collection_name, []):
            source_carriers.append((f"curated {collection_name} {record.get('id')}", record))
    profile = curated.get("referenceVehicleProfile")
    if profile is not None:
        source_carriers.append((f"curated profile {profile.get('id')}", profile))
    for label, carrier in source_carriers:
        _validate_source_carrier(carrier, qualified_source_refs, label)
        unknown_pack_ids = set(carrier.get("curatedSourceIds", [])).difference(pack_ids)
        if unknown_pack_ids:
            raise ValueError(f"Unknown curated pack references in {label}: {unknown_pack_ids}")

    return curated, overlay_sha256, pack_inputs


def _build_system_nodes(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {
            "id": f"corpus_system_{system['id']}",
            "type": "SYSTEM",
            "label": system["title"],
            "canonicalKey": system["id"],
            "sourceBlockIds": [],
            "sourceRefs": [],
            "curatedSourceIds": [],
        }
        for system in manifest["systems"]
    ]


def _build_section_nodes(
    manifest: dict[str, Any],
    section_source_refs: dict[str, list[dict[str, str]]],
) -> list[dict[str, Any]]:
    nodes: list[dict[str, Any]] = []
    for section in manifest["sections"]:
        source_refs = section_source_refs[section["id"]]
        nodes.append(
            {
                "id": f"corpus_section_{section['id']}",
                "type": "SECTION",
                "label": section["titleOriginal"],
                "canonicalKey": section["id"],
                "sourceRecordRole": "SECTION_SHARD",
                "sourceBlockIds": sorted({source_ref["blockId"] for source_ref in source_refs}),
                "sourceRefs": source_refs,
                "curatedSourceIds": [],
            }
        )
    return nodes


def _real_case_label(entity: dict[str, Any]) -> str:
    match = re.fullmatch(r"block_([0-9]{6})_[0-9a-f]{10}", entity["sourceBlockId"])
    if match is None:
        raise ValueError(f"Invalid real-case source block ID for {entity['id']}")
    return f"Caso real · {entity['sourceDocumentId']} · bloque {match.group(1)}"


def _build_entity_nodes(entity_index: dict[str, Any]) -> list[dict[str, Any]]:
    nodes: list[dict[str, Any]] = []
    for entity in entity_index["entities"]:
        source_ref = {
            "sourceDocumentId": entity["sourceDocumentId"],
            "blockId": entity["sourceBlockId"],
            "textHash": entity["sourceTextHash"],
        }
        if entity["recordRole"] == "COMPONENT":
            node_type = "COMPONENT"
            source_record_role = "COMPONENT_RECORD"
            label = entity["nameOriginal"]
        else:
            node_type = "SOURCE_BLOCK"
            source_record_role = "REAL_CASE"
            label = _real_case_label(entity)
        nodes.append(
            {
                "id": f"corpus_entity_{entity['id']}",
                "type": node_type,
                "label": label,
                "canonicalKey": entity["id"],
                "sourceRecordRole": source_record_role,
                "sourceBlockIds": [entity["sourceBlockId"]],
                "sourceRefs": [source_ref],
                "curatedSourceIds": [],
            }
        )
    return nodes


def _structural_edge(
    edge_id: str,
    child_id: str,
    parent_id: str,
    source_refs: list[dict[str, str]],
) -> dict[str, Any]:
    if not source_refs:
        raise ValueError(f"Structural edge {edge_id} has no qualified source reference")
    return {
        "id": edge_id,
        "from": child_id,
        "to": parent_id,
        "type": "PART_OF",
        "sourceBlockIds": sorted({source_ref["blockId"] for source_ref in source_refs}),
        "sourceRefs": source_refs,
        "curatedSourceIds": [],
        "observedEvidenceIds": [],
        "evidenceRequired": [],
        "reviewState": "REVIEW_REQUIRED",
        "applicability": "GENERIC",
        "confidence": "UNASSESSED",
    }


def _build_structural_edges(
    manifest: dict[str, Any],
    entity_index: dict[str, Any],
    section_source_refs: dict[str, list[dict[str, str]]],
) -> list[dict[str, Any]]:
    edges: list[dict[str, Any]] = []
    for section in manifest["sections"]:
        section_id = section["id"]
        edges.append(
            _structural_edge(
                f"corpus_edge_section_part_of_{section_id}",
                f"corpus_section_{section_id}",
                f"corpus_system_{section['systemId']}",
                section_source_refs[section_id][:1],
            )
        )
    for entity in entity_index["entities"]:
        source_ref = {
            "sourceDocumentId": entity["sourceDocumentId"],
            "blockId": entity["sourceBlockId"],
            "textHash": entity["sourceTextHash"],
        }
        edges.append(
            _structural_edge(
                f"corpus_edge_entity_part_of_{entity['id']}",
                f"corpus_entity_{entity['id']}",
                f"corpus_section_{entity['sectionId']}",
                [source_ref],
            )
        )
    return edges


def _assert_unique_ids(records: list[dict[str, Any]], label: str) -> set[str]:
    ids: list[str] = []
    for record in records:
        record_id = record.get("id")
        if not isinstance(record_id, str):
            raise ValueError(f"Missing {label} ID")
        ids.append(record_id)
    if len(ids) != len(set(ids)):
        duplicates = sorted(record_id for record_id, count in Counter(ids).items() if count > 1)
        raise ValueError(f"Duplicate {label} IDs: {duplicates[:3]}")
    return set(ids)


def _validate_compiled_references(
    nodes: list[dict[str, Any]],
    edges: list[dict[str, Any]],
    profiles: list[dict[str, Any]],
    applicability_rules: list[dict[str, Any]],
    qualified_source_refs: set[tuple[str, str, str]],
) -> None:
    node_ids = _assert_unique_ids(nodes, "node")
    _assert_unique_ids(edges, "edge")
    profile_ids = _assert_unique_ids(profiles, "profile")
    _assert_unique_ids(applicability_rules, "applicability rule")
    for node in nodes:
        _validate_source_carrier(node, qualified_source_refs, f"node {node['id']}")
    for edge in edges:
        if edge.get("from") not in node_ids or edge.get("to") not in node_ids:
            raise ValueError(f"Orphan edge: {edge.get('id')}")
        _validate_source_carrier(edge, qualified_source_refs, f"edge {edge['id']}")
    for profile in profiles:
        if profile.get("nodeId") not in node_ids:
            raise ValueError(f"Orphan profile node: {profile.get('id')}")
        _validate_source_carrier(profile, qualified_source_refs, f"profile {profile['id']}")
    for rule in applicability_rules:
        if rule.get("profileId") not in profile_ids:
            raise ValueError(f"Orphan applicability rule profile: {rule.get('id')}")
        _validate_source_carrier(
            rule, qualified_source_refs, f"applicability rule {rule['id']}"
        )


def build_graph(repo_root: Path) -> dict[str, Any]:
    """Build and validate the graph in memory without writing any files."""

    repo_root = Path(repo_root)
    schema = _load_json(repo_root / SCHEMA_RELATIVE_PATH)
    Draft202012Validator.check_schema(schema)
    corpus = _load_and_validate_corpus(repo_root)
    curated, overlay_sha256, pack_inputs = _load_and_validate_curated(
        repo_root, schema, corpus
    )
    manifest = corpus["manifest"]
    entity_index = corpus["entityIndex"]

    generated_nodes = (
        _build_system_nodes(manifest)
        + _build_section_nodes(manifest, corpus["sectionSourceRefs"])
        + _build_entity_nodes(entity_index)
    )
    curated_nodes = copy.deepcopy(curated["nodes"])
    generated_node_ids = _assert_unique_ids(generated_nodes, "generated node")
    curated_node_ids = _assert_unique_ids(curated_nodes, "curated node")
    node_collisions = generated_node_ids.intersection(curated_node_ids)
    if node_collisions:
        raise ValueError(f"Curated/generated node ID collision: {sorted(node_collisions)[:3]}")

    generated_edges = _build_structural_edges(
        manifest, entity_index, corpus["sectionSourceRefs"]
    )
    curated_edges = copy.deepcopy(curated["edges"])
    generated_edge_ids = _assert_unique_ids(generated_edges, "generated edge")
    curated_edge_ids = _assert_unique_ids(curated_edges, "curated edge")
    edge_collisions = generated_edge_ids.intersection(curated_edge_ids)
    if edge_collisions:
        raise ValueError(f"Curated/generated edge ID collision: {sorted(edge_collisions)[:3]}")

    nodes = sorted(
        (_normalize_record(node, f"node {node['id']}") for node in generated_nodes + curated_nodes),
        key=lambda node: node["id"],
    )
    edges = sorted(
        (_normalize_record(edge, f"edge {edge['id']}") for edge in generated_edges + curated_edges),
        key=lambda edge: edge["id"],
    )
    profiles = sorted(
        [_normalize_record(copy.deepcopy(curated["referenceVehicleProfile"]), "profile")],
        key=lambda profile: profile["id"],
    )
    applicability_rules = sorted(
        (
            _normalize_record(copy.deepcopy(rule), f"applicability rule {rule['id']}")
            for rule in curated["applicabilityRules"]
        ),
        key=lambda rule: rule["id"],
    )
    traced_pack_inputs = sorted(
        (_normalize_record(pack, f"curated pack {pack['packId']}") for pack in pack_inputs),
        key=lambda pack: (pack["packId"], pack["path"]),
    )

    _validate_compiled_references(
        nodes,
        edges,
        profiles,
        applicability_rules,
        corpus["qualifiedSourceRefs"],
    )

    role_counts = corpus["recordRoleCounts"]
    base_node_count = len(generated_nodes)
    structural_edge_count = len(generated_edges)
    graph: dict[str, Any] = {
        "schemaVersion": 1,
        "graphId": GRAPH_ID,
        "graphVersion": GRAPH_VERSION,
        "sourceCorpusHash": manifest["contentSha256"],
        "sourceInputs": {
            "corpusManifestPath": MANIFEST_RELATIVE_PATH.as_posix(),
            "corpusManifestSha256": corpus["manifestSha256"],
            "corpusId": manifest["corpusId"],
            "corpusVersion": manifest["corpusVersion"],
            "entityIndexPath": ENTITY_INDEX_RELATIVE_PATH.as_posix(),
            "entityIndexSha256": corpus["entityIndexSha256"],
            "curatedOverlayPath": CURATED_OVERLAY_RELATIVE_PATH.as_posix(),
            "curatedOverlaySha256": overlay_sha256,
            "curatedPacks": traced_pack_inputs,
        },
        "nodes": nodes,
        "edges": edges,
        "profiles": profiles,
        "applicabilityRules": applicability_rules,
        "statistics": {
            "sourceBlockCount": len(corpus["qualifiedSourceRefs"]),
            "qualifiedSourceRefCount": len(corpus["qualifiedSourceRefs"]),
            "bareSourceBlockIdCount": len(corpus["bareSourceBlockIds"]),
            "systemNodeCount": len(manifest["systems"]),
            "sectionNodeCount": len(manifest["sections"]),
            "entityNodeCount": len(entity_index["entities"]),
            "componentNodeCount": role_counts["COMPONENT"],
            "realCaseNodeCount": role_counts["REAL_CASE"],
            "baseNodeCount": base_node_count,
            "structuralEdgeCount": structural_edge_count,
            "curatedNodeCount": len(curated_nodes),
            "curatedEdgeCount": len(curated_edges),
            "nodeCount": len(nodes),
            "edgeCount": len(edges),
            "profileCount": len(profiles),
            "applicabilityRuleCount": len(applicability_rules),
        },
    }
    graph = _normalize_record(graph, "graph")
    graph["contentSha256"] = _sha256_bytes(canonical_json_bytes(graph))
    Draft202012Validator(schema).validate(graph)
    if _content_sha256(graph) != graph["contentSha256"]:
        raise ValueError("Compiled graph content hash is not reproducible")
    return graph


def _stage_bytes(path: Path, encoded: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o644)
        with os.fdopen(descriptor, "wb") as destination:
            destination.write(encoded)
            destination.flush()
            os.fsync(destination.fileno())
    except BaseException:
        temporary_path.unlink(missing_ok=True)
        raise
    return temporary_path


def compile_graph(repo_root: Path, output_root: Path) -> tuple[Path, Path]:
    """Build once, atomically write both targets, and verify byte equality."""

    graph = build_graph(Path(repo_root))
    encoded = canonical_json_bytes(graph) + b"\n"
    output_root = Path(output_root)
    public_path = output_root / PUBLIC_GRAPH_RELATIVE_PATH
    android_path = output_root / ANDROID_GRAPH_RELATIVE_PATH
    staged: list[tuple[Path, Path]] = []
    try:
        staged = [
            (_stage_bytes(public_path, encoded), public_path),
            (_stage_bytes(android_path, encoded), android_path),
        ]
        for temporary_path, target_path in staged:
            os.replace(temporary_path, target_path)
        staged.clear()
    finally:
        for temporary_path, _ in staged:
            temporary_path.unlink(missing_ok=True)

    public_bytes = public_path.read_bytes()
    android_bytes = android_path.read_bytes()
    if public_bytes != encoded or android_bytes != encoded or public_bytes != android_bytes:
        raise OSError("Compiled public and Android graph artifacts are not byte-identical")
    return public_path, android_path


def check_graph(repo_root: Path) -> tuple[bool, list[Path]]:
    """Rebuild in memory and report stale artifacts without modifying them."""

    repo_root = Path(repo_root)
    encoded = canonical_json_bytes(build_graph(repo_root)) + b"\n"
    paths = [
        repo_root / PUBLIC_GRAPH_RELATIVE_PATH,
        repo_root / ANDROID_GRAPH_RELATIVE_PATH,
    ]
    stale = [path for path in paths if not path.is_file() or path.read_bytes() != encoded]
    return not stale, stale


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--check",
        action="store_true",
        help="Rebuild in memory and fail if either committed artifact differs",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    repo_root = args.repo_root.resolve()
    try:
        if args.check:
            current, stale = check_graph(repo_root)
            if not current:
                rendered = ", ".join(str(path) for path in stale)
                print(f"ERROR: automotive knowledge graph artifact(s) out of date: {rendered}", file=sys.stderr)
                return 1
            graph = _load_json(repo_root / PUBLIC_GRAPH_RELATIVE_PATH)
            print(
                json.dumps(
                    {
                        "status": "PASS",
                        "mode": "check",
                        "contentSha256": graph["contentSha256"],
                        **graph["statistics"],
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
            )
            return 0

        public_path, android_path = compile_graph(repo_root, repo_root)
        graph = _load_json(public_path)
        print(
            json.dumps(
                {
                    "status": "PASS",
                    "mode": "write",
                    "contentSha256": graph["contentSha256"],
                    "publicPath": str(public_path),
                    "androidPath": str(android_path),
                    **graph["statistics"],
                },
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except Exception as error:  # CLI boundary reports a concise deterministic failure.
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
