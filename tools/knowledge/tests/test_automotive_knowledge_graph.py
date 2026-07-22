from __future__ import annotations

import copy
import hashlib
import json
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


REPO_ROOT = Path(__file__).resolve().parents[3]
TOOLS_DIR = REPO_ROOT / "tools/knowledge"
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from build_automotive_knowledge_graph import build_graph, compile_graph


SCHEMA_PATH = REPO_ROOT / "tools/knowledge/schema/automotive-knowledge-graph.schema.json"
CURATED_PATH = REPO_ROOT / "tools/knowledge/curated/accent_verna_2005_knowledge.json"
CORPUS_MANIFEST_PATH = REPO_ROOT / "public/knowledge/proprietary/manifest.json"
CORPUS_ENTITY_INDEX_PATH = REPO_ROOT / "public/knowledge/proprietary/entity_index.json"
CORPUS_SECTIONS_PATH = REPO_ROOT / "public/knowledge/proprietary/sections"
PUBLIC_GRAPH_PATH = REPO_ROOT / "public/knowledge/graph/automotive_knowledge_graph.json"
ANDROID_GRAPH_PATH = (
    REPO_ROOT
    / "android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json"
)

REQUIRED_NODE_TYPES = {
    "SYSTEM",
    "ASSEMBLY",
    "SECTION",
    "COMPONENT",
    "ALIAS",
    "DTC",
    "SYMPTOM",
    "DIAGNOSTIC_TEST",
    "MEASUREMENT",
    "PROCEDURE",
    "PROCEDURE_STEP",
    "TOOL",
    "SAFETY_WARNING",
    "PART_CANDIDATE",
    "VEHICLE_PROFILE",
    "SOURCE_BLOCK",
    "VISUAL_TARGET",
}
ALLOWED_APPLICABILITY_STATES = {
    "CONFIRMED",
    "PROBABLE",
    "CONDITIONAL",
    "GENERIC",
    "NOT_DOCUMENTED",
    "NOT_APPLICABLE",
    "CONFLICTED",
}
REQUIRED_EXCLUSION_KEYS = {
    "maf_sensor",
    "app_sensor",
    "eps",
    "epb",
    "turbocharger",
    "diesel_system",
    "dpf",
    "adas",
}
REQUIRED_EDGE_FIELDS = {
    "sourceBlockIds",
    "sourceRefs",
    "evidenceRequired",
    "reviewState",
    "applicability",
    "confidence",
}


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def canonical_bytes(payload: object) -> bytes:
    return json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def content_sha256(payload: dict) -> str:
    unhashed = {key: value for key, value in payload.items() if key != "contentSha256"}
    return hashlib.sha256(canonical_bytes(unhashed)).hexdigest()


class AutomotiveKnowledgeGraphContractTest(unittest.TestCase):
    def test_schema_is_valid_draft_2020_12_and_accepts_curated_overlay(self) -> None:
        schema = load_json(SCHEMA_PATH)
        curated = load_json(CURATED_PATH)

        Draft202012Validator.check_schema(schema)
        Draft202012Validator(schema).validate(curated)

    def test_schema_version_and_vocabularies_are_exact(self) -> None:
        schema = load_json(SCHEMA_PATH)

        self.assertEqual(1, schema["properties"]["schemaVersion"]["const"])
        self.assertTrue(
            REQUIRED_NODE_TYPES <= set(schema["$defs"]["nodeType"]["enum"]),
            set(schema["$defs"]["nodeType"]["enum"]),
        )
        self.assertEqual(
            ALLOWED_APPLICABILITY_STATES,
            set(schema["$defs"]["applicabilityState"]["enum"]),
        )
        self.assertTrue(REQUIRED_EDGE_FIELDS <= set(schema["$defs"]["edge"]["required"]))

    def test_confirmed_edge_schema_requires_curated_or_observed_evidence(self) -> None:
        schema = load_json(SCHEMA_PATH)
        edge_rules = schema["$defs"]["edge"]["allOf"]
        confirmed_rule = next(
            rule
            for rule in edge_rules
            if rule.get("if", {})
            .get("properties", {})
            .get("applicability", {})
            .get("const")
            == "CONFIRMED"
        )
        evidence_options = confirmed_rule["then"]["anyOf"]

        required_evidence = {
            field
            for option in evidence_options
            for field, contract in option.get("properties", {}).items()
            if contract.get("minItems") == 1
        }
        self.assertEqual({"curatedSourceIds", "observedEvidenceIds"}, required_evidence)

    def test_source_corpus_hash_is_sha256_and_matches_manifest(self) -> None:
        curated = load_json(CURATED_PATH)
        manifest = load_json(CORPUS_MANIFEST_PATH)
        source_corpus_hash = curated["sourceCorpusHash"]

        self.assertRegex(source_corpus_hash, re.compile(r"^[0-9a-f]{64}$"))
        self.assertEqual(manifest["contentSha256"], source_corpus_hash)

    def test_graph_node_and_edge_ids_are_unique_and_references_resolve(self) -> None:
        curated = load_json(CURATED_PATH)
        node_ids = [node["id"] for node in curated["nodes"]]
        edge_ids = [edge["id"] for edge in curated["edges"]]

        self.assertEqual(len(node_ids), len(set(node_ids)))
        self.assertEqual(len(edge_ids), len(set(edge_ids)))
        for edge in curated["edges"]:
            self.assertIn(edge["from"], node_ids, edge["id"])
            self.assertIn(edge["to"], node_ids, edge["id"])

    def test_only_allowed_applicability_states_are_used(self) -> None:
        curated = load_json(CURATED_PATH)
        states = {edge["applicability"] for edge in curated["edges"]}
        states.update(rule["state"] for rule in curated["applicabilityRules"])

        self.assertTrue(states)
        self.assertTrue(states <= ALLOWED_APPLICABILITY_STATES, states)

    def test_reference_vehicle_keeps_generic_hardware_out(self) -> None:
        curated = load_json(CURATED_PATH)
        self.assertEqual(
            "hyundai_accent_verna_2005_1_6_at",
            curated["referenceVehicleProfile"]["id"],
        )
        excluded = {
            rule["canonicalKey"]
            for rule in curated["applicabilityRules"]
            if rule["state"] in {"NOT_APPLICABLE", "NOT_DOCUMENTED"}
        }

        self.assertTrue(REQUIRED_EXCLUSION_KEYS <= excluded, excluded)

    def test_curated_edges_require_source_or_observed_evidence(self) -> None:
        curated = load_json(CURATED_PATH)
        for edge in curated["edges"]:
            self.assertTrue(REQUIRED_EDGE_FIELDS <= edge.keys(), edge["id"])
            self.assertTrue(
                edge["sourceBlockIds"] or edge["evidenceRequired"],
                f"{edge['id']} has neither source blocks nor an evidence gate",
            )
            if edge["applicability"] == "CONFIRMED":
                self.assertTrue(
                    edge.get("curatedSourceIds") or edge.get("observedEvidenceIds"),
                    f"{edge['id']} confirms applicability without curated or observed evidence",
                )

    def test_curated_source_block_ids_exist_in_immutable_corpus(self) -> None:
        curated = load_json(CURATED_PATH)
        relations = curated["edges"] + curated["applicabilityRules"]
        referenced = {
            source_ref["blockId"]
            for relation in relations
            for source_ref in relation["sourceRefs"]
        }
        existing = {
            (
                shard["sourceDocumentId"],
                block["blockId"],
                block["textHash"],
            )
            for shard_path in CORPUS_SECTIONS_PATH.glob("*.json")
            for shard in [load_json(shard_path)]
            for block in shard["blocks"]
        }

        self.assertTrue(referenced)
        for relation in relations:
            self.assertEqual(
                set(relation["sourceBlockIds"]),
                {source_ref["blockId"] for source_ref in relation["sourceRefs"]},
                relation["id"],
            )
            qualified_refs = [
                (
                    source_ref["sourceDocumentId"],
                    source_ref["blockId"],
                    source_ref["textHash"],
                )
                for source_ref in relation["sourceRefs"]
            ]
            self.assertEqual(
                len(qualified_refs),
                len(set(qualified_refs)),
                f"{relation['id']} contains duplicate qualified source references",
            )
            for source_ref in relation["sourceRefs"]:
                qualified_ref = (
                    source_ref["sourceDocumentId"],
                    source_ref["blockId"],
                    source_ref["textHash"],
                )
                self.assertIn(qualified_ref, existing, relation["id"])

    def test_p0230_sequence_is_circuit_first_and_replacement_gated(self) -> None:
        curated = load_json(CURATED_PATH)
        first_test_edge = next(
            edge for edge in curated["edges"] if edge["id"] == "edge_p0230_first_test"
        )
        self.assertEqual("HAS_DIAGNOSTIC_TEST", first_test_edge["type"])

        sequence_edges = sorted(
            (
                edge
                for edge in curated["edges"]
                if edge.get("sequenceId") == "p0230_circuit_first"
                and edge["type"] == "NEXT_STEP"
            ),
            key=lambda edge: edge["sequenceOrder"],
        )
        ordered_tests = [sequence_edges[0]["from"]] + [
            edge["to"] for edge in sequence_edges
        ]

        self.assertEqual(
            [
                "test_p0230_capture_context",
                "test_p0230_power_and_ground",
                "test_p0230_fuse_and_feed",
                "test_p0230_relay_control_and_output",
                "test_p0230_connector_and_harness",
                "test_p0230_loaded_voltage_drop",
                "test_p0230_pump_current",
                "test_p0230_pcm_driver",
            ],
            ordered_tests,
        )
        replacement_edges = {
            edge["from"]: edge
            for edge in curated["edges"]
            if edge["type"] == "REQUIRES_TEST_BEFORE_REPLACE"
        }
        self.assertEqual("test_p0230_pump_current", replacement_edges["fuel_pump"]["to"])
        self.assertEqual("test_p0230_pcm_driver", replacement_edges["pcm_driver"]["to"])
        self.assertTrue(replacement_edges["fuel_pump"]["evidenceRequired"])
        self.assertTrue(replacement_edges["pcm_driver"]["evidenceRequired"])


class AutomotiveKnowledgeGraphCompilerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.graph = build_graph(REPO_ROOT)

    def test_compiled_graph_validates_and_has_expected_base_statistics(self) -> None:
        Draft202012Validator(load_json(SCHEMA_PATH)).validate(self.graph)

        expected = {
            "sourceBlockCount": 74_648,
            "qualifiedSourceRefCount": 74_648,
            "bareSourceBlockIdCount": 74_638,
            "systemNodeCount": 26,
            "sectionNodeCount": 347,
            "entityNodeCount": 5_050,
            "componentNodeCount": 4_753,
            "realCaseNodeCount": 297,
            "baseNodeCount": 5_423,
            "structuralEdgeCount": 5_397,
            "curatedNodeCount": 23,
            "curatedEdgeCount": 14,
            "nodeCount": 5_446,
            "edgeCount": 5_411,
            "profileCount": 1,
            "applicabilityRuleCount": 8,
        }
        for key, value in expected.items():
            self.assertEqual(value, self.graph["statistics"][key], key)

    def test_entity_records_are_split_honestly_between_components_and_real_cases(self) -> None:
        entity_nodes = [
            node for node in self.graph["nodes"] if node["id"].startswith("corpus_entity_")
        ]
        components = [
            node
            for node in entity_nodes
            if node["type"] == "COMPONENT"
            and node.get("sourceRecordRole") == "COMPONENT_RECORD"
        ]
        real_cases = [
            node
            for node in entity_nodes
            if node["type"] == "SOURCE_BLOCK"
            and node.get("sourceRecordRole") == "REAL_CASE"
        ]

        self.assertEqual(5_050, len(entity_nodes))
        self.assertEqual(4_753, len(components))
        self.assertEqual(297, len(real_cases))
        self.assertTrue(
            all(
                node["canonicalKey"] == node["id"].removeprefix("corpus_entity_")
                for node in entity_nodes
            )
        )
        self.assertTrue(
            all(re.fullmatch(r"Caso real · document_\d+ · bloque \d{6}", node["label"])
                for node in real_cases)
        )
        source_sentences = {
            entity["nameOriginal"]
            for entity in load_json(CORPUS_ENTITY_INDEX_PATH)["entities"]
            if entity["recordRole"] == "REAL_CASE"
        }
        self.assertTrue(source_sentences.isdisjoint(node["label"] for node in real_cases))

    def test_structural_edges_have_one_child_to_parent_edge_per_section_and_entity(self) -> None:
        structural = [
            edge
            for edge in self.graph["edges"]
            if edge["id"].startswith("corpus_edge_")
        ]
        section_nodes = {
            node["id"]
            for node in self.graph["nodes"]
            if node["id"].startswith("corpus_section_")
        }
        entity_nodes = {
            node["id"]
            for node in self.graph["nodes"]
            if node["id"].startswith("corpus_entity_")
        }
        parents: dict[str, list[dict]] = {node_id: [] for node_id in section_nodes | entity_nodes}
        for edge in structural:
            self.assertEqual("PART_OF", edge["type"])
            self.assertEqual("GENERIC", edge["applicability"])
            self.assertEqual("REVIEW_REQUIRED", edge["reviewState"])
            self.assertEqual("UNASSESSED", edge["confidence"])
            self.assertTrue(edge["sourceRefs"], edge["id"])
            self.assertEqual(
                sorted(edge["sourceBlockIds"]),
                sorted({source_ref["blockId"] for source_ref in edge["sourceRefs"]}),
                edge["id"],
            )
            parents[edge["from"]].append(edge)

        self.assertEqual(5_397, len(structural))
        self.assertTrue(all(len(edges) == 1 for edges in parents.values()))
        self.assertTrue(
            all(
                edges[0]["to"].startswith("corpus_system_")
                for node_id, edges in parents.items()
                if node_id in section_nodes
            )
        )
        self.assertTrue(
            all(
                edges[0]["to"].startswith("corpus_section_")
                for node_id, edges in parents.items()
                if node_id in entity_nodes
            )
        )

    def test_qualified_source_identity_covers_collision_that_bare_ids_cannot(self) -> None:
        section_nodes = [
            node
            for node in self.graph["nodes"]
            if node["id"].startswith("corpus_section_")
        ]
        qualified = {
            (ref["sourceDocumentId"], ref["blockId"], ref["textHash"])
            for node in section_nodes
            for ref in node["sourceRefs"]
        }
        bare = {ref[1] for ref in qualified}

        self.assertEqual(74_648, len(qualified))
        self.assertEqual(74_638, len(bare))
        self.assertGreater(len(qualified), len(bare))

    def test_ids_are_unique_and_edges_and_profiles_are_not_orphaned(self) -> None:
        node_ids = [node["id"] for node in self.graph["nodes"]]
        edge_ids = [edge["id"] for edge in self.graph["edges"]]
        profile_ids = [profile["id"] for profile in self.graph["profiles"]]

        self.assertEqual(len(node_ids), len(set(node_ids)))
        self.assertEqual(len(edge_ids), len(set(edge_ids)))
        self.assertEqual(len(profile_ids), len(set(profile_ids)))
        for edge in self.graph["edges"]:
            self.assertIn(edge["from"], node_ids, edge["id"])
            self.assertIn(edge["to"], node_ids, edge["id"])
        for profile in self.graph["profiles"]:
            self.assertIn(profile["nodeId"], node_ids, profile["id"])
        for rule in self.graph["applicabilityRules"]:
            self.assertIn(rule["profileId"], profile_ids, rule["id"])

    def test_manifest_entity_index_shards_and_block_hashes_recompute_canonically(self) -> None:
        manifest = load_json(CORPUS_MANIFEST_PATH)
        entity_index = load_json(CORPUS_ENTITY_INDEX_PATH)
        self.assertEqual(manifest["contentSha256"], content_sha256(manifest))
        self.assertEqual(entity_index["contentSha256"], content_sha256(entity_index))

        qualified: set[tuple[str, str, str]] = set()
        for section in manifest["sections"]:
            shard_path = REPO_ROOT / "public" / section["shardPath"]
            shard = load_json(shard_path)
            self.assertEqual(section["contentSha256"], shard["contentSha256"])
            self.assertEqual(shard["contentSha256"], content_sha256(shard))
            for block in shard["blocks"]:
                self.assertEqual(
                    block["textHash"],
                    hashlib.sha256(block["text"].encode("utf-8")).hexdigest(),
                )
                qualified.add(
                    (shard["sourceDocumentId"], block["blockId"], block["textHash"])
                )

        self.assertEqual(347, len(manifest["sections"]))
        self.assertEqual(74_648, len(qualified))

    def test_source_inputs_preserve_corpus_hash_and_add_full_traceability(self) -> None:
        manifest = load_json(CORPUS_MANIFEST_PATH)
        entity_index = load_json(CORPUS_ENTITY_INDEX_PATH)
        source_inputs = self.graph["sourceInputs"]

        self.assertEqual(manifest["contentSha256"], self.graph["sourceCorpusHash"])
        self.assertEqual(manifest["contentSha256"], source_inputs["corpusManifestSha256"])
        self.assertEqual(entity_index["contentSha256"], source_inputs["entityIndexSha256"])
        self.assertEqual(
            hashlib.sha256(canonical_bytes(load_json(CURATED_PATH))).hexdigest(),
            source_inputs["curatedOverlaySha256"],
        )
        self.assertEqual("public/knowledge/proprietary/entity_index.json", source_inputs["entityIndexPath"])
        self.assertEqual(
            "tools/knowledge/curated/accent_verna_2005_knowledge.json",
            source_inputs["curatedOverlayPath"],
        )
        for pack in source_inputs["curatedPacks"]:
            self.assertEqual(
                hashlib.sha256(canonical_bytes(load_json(REPO_ROOT / pack["path"]))).hexdigest(),
                pack["contentSha256"],
                pack["packId"],
            )

    def test_graph_contains_no_literal_text_rows_timestamps_or_floats(self) -> None:
        forbidden_keys = {"text", "rows", "generatedAt", "generatedAtEpochMs", "timestamp"}

        def visit(value: object) -> None:
            self.assertNotIsInstance(value, float)
            if isinstance(value, dict):
                self.assertTrue(forbidden_keys.isdisjoint(value), forbidden_keys & value.keys())
                for child in value.values():
                    visit(child)
            elif isinstance(value, list):
                for child in value:
                    visit(child)

        visit(self.graph)

    def test_graph_order_and_content_hash_are_canonical(self) -> None:
        self.assertEqual(
            [node["id"] for node in self.graph["nodes"]],
            sorted(node["id"] for node in self.graph["nodes"]),
        )
        self.assertEqual(
            [edge["id"] for edge in self.graph["edges"]],
            sorted(edge["id"] for edge in self.graph["edges"]),
        )
        self.assertEqual(
            [profile["id"] for profile in self.graph["profiles"]],
            sorted(profile["id"] for profile in self.graph["profiles"]),
        )
        self.assertEqual(
            [rule["id"] for rule in self.graph["applicabilityRules"]],
            sorted(rule["id"] for rule in self.graph["applicabilityRules"]),
        )
        self.assertEqual(
            [(pack["packId"], pack["path"]) for pack in self.graph["sourceInputs"]["curatedPacks"]],
            sorted((pack["packId"], pack["path"]) for pack in self.graph["sourceInputs"]["curatedPacks"]),
        )
        for collection_name in ("nodes", "edges"):
            for item in self.graph[collection_name]:
                refs = item["sourceRefs"]
                self.assertEqual(
                    refs,
                    sorted(
                        refs,
                        key=lambda ref: (
                            ref["sourceDocumentId"], ref["blockId"], ref["textHash"]
                        ),
                    ),
                    item["id"],
                )

        self.assertEqual(self.graph["contentSha256"], content_sha256(self.graph))

    def test_p0230_semantic_order_is_carried_by_sequence_order(self) -> None:
        sequence_edges = sorted(
            (
                edge
                for edge in self.graph["edges"]
                if edge.get("sequenceId") == "p0230_circuit_first"
                and edge["type"] == "NEXT_STEP"
            ),
            key=lambda edge: edge["sequenceOrder"],
        )
        self.assertEqual(list(range(1, 8)), [edge["sequenceOrder"] for edge in sequence_edges])
        self.assertEqual(
            [
                "test_p0230_capture_context",
                "test_p0230_power_and_ground",
                "test_p0230_fuse_and_feed",
                "test_p0230_relay_control_and_output",
                "test_p0230_connector_and_harness",
                "test_p0230_loaded_voltage_drop",
                "test_p0230_pump_current",
                "test_p0230_pcm_driver",
            ],
            [sequence_edges[0]["from"]] + [edge["to"] for edge in sequence_edges],
        )

    def test_compile_twice_is_byte_identical_and_writes_identical_targets(self) -> None:
        with tempfile.TemporaryDirectory() as first_dir, tempfile.TemporaryDirectory() as second_dir:
            first_public, first_android = compile_graph(REPO_ROOT, Path(first_dir))
            second_public, second_android = compile_graph(REPO_ROOT, Path(second_dir))

            expected = canonical_bytes(self.graph) + b"\n"
            self.assertEqual(expected, first_public.read_bytes())
            self.assertEqual(first_public.read_bytes(), first_android.read_bytes())
            self.assertEqual(first_public.read_bytes(), second_public.read_bytes())
            self.assertEqual(second_public.read_bytes(), second_android.read_bytes())
            for artifact in (first_public, first_android, second_public, second_android):
                self.assertEqual(0o644, stat.S_IMODE(artifact.stat().st_mode), artifact)

    def test_committed_public_and_android_artifacts_are_byte_identical(self) -> None:
        expected = canonical_bytes(self.graph) + b"\n"
        self.assertEqual(expected, PUBLIC_GRAPH_PATH.read_bytes())
        self.assertEqual(PUBLIC_GRAPH_PATH.read_bytes(), ANDROID_GRAPH_PATH.read_bytes())
        self.assertEqual(0o644, stat.S_IMODE(PUBLIC_GRAPH_PATH.stat().st_mode))
        self.assertEqual(0o644, stat.S_IMODE(ANDROID_GRAPH_PATH.stat().st_mode))

    def test_curated_generated_id_collision_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            fixture_root = self._make_repo_fixture(Path(temporary_dir))
            overlay_path = fixture_root / "tools/knowledge/curated/accent_verna_2005_knowledge.json"
            overlay = load_json(overlay_path)
            overlay["nodes"].append(
                {
                    "id": "corpus_system_engine",
                    "type": "SYSTEM",
                    "label": "Collision fixture",
                    "sourceBlockIds": [],
                    "sourceRefs": [],
                    "curatedSourceIds": [],
                }
            )
            overlay_path.write_text(json.dumps(overlay, ensure_ascii=False), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "collision"):
                build_graph(fixture_root)

    def test_check_detects_drift_without_writing(self) -> None:
        script = REPO_ROOT / "tools/knowledge/build_automotive_knowledge_graph.py"
        with tempfile.TemporaryDirectory() as temporary_dir:
            fixture_root = self._make_repo_fixture(Path(temporary_dir))
            public_path, android_path = compile_graph(fixture_root, fixture_root)
            corrupted = b"{}\n"
            public_path.write_bytes(corrupted)
            android_before = android_path.read_bytes()

            result = subprocess.run(
                [sys.executable, str(script), "--repo-root", str(fixture_root), "--check"],
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("out of date", (result.stdout + result.stderr).lower())
            self.assertEqual(corrupted, public_path.read_bytes())
            self.assertEqual(android_before, android_path.read_bytes())

    @staticmethod
    def _make_repo_fixture(root: Path) -> Path:
        (root / "public/knowledge").mkdir(parents=True)
        (root / "tools/knowledge/schema").mkdir(parents=True)
        (root / "tools/knowledge/curated").mkdir(parents=True)
        (root / "android/app/src/main/assets/knowledge/packs").mkdir(parents=True)
        (root / "public/knowledge/proprietary").symlink_to(
            REPO_ROOT / "public/knowledge/proprietary",
            target_is_directory=True,
        )
        shutil.copy2(SCHEMA_PATH, root / "tools/knowledge/schema" / SCHEMA_PATH.name)
        shutil.copy2(CURATED_PATH, root / "tools/knowledge/curated" / CURATED_PATH.name)
        curated = load_json(CURATED_PATH)
        for pack in curated["sourceInputs"]["curatedPacks"]:
            target = root / pack["path"]
            target.symlink_to(REPO_ROOT / pack["path"])
        return root


if __name__ == "__main__":
    unittest.main()
