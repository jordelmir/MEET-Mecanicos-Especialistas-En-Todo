from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


REPO_ROOT = Path(__file__).resolve().parents[3]
SCHEMA_PATH = REPO_ROOT / "tools/knowledge/schema/automotive-knowledge-graph.schema.json"
CURATED_PATH = REPO_ROOT / "tools/knowledge/curated/accent_verna_2005_knowledge.json"
CORPUS_MANIFEST_PATH = REPO_ROOT / "public/knowledge/proprietary/manifest.json"
CORPUS_SECTIONS_PATH = REPO_ROOT / "public/knowledge/proprietary/sections"

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


if __name__ == "__main__":
    unittest.main()
