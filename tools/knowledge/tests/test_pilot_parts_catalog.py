from __future__ import annotations

import sys
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from build_pilot_parts_catalog import PARTS, build_pack, canonical_json, validate_pack
from common import sha256_text


def synthetic_extraction() -> dict:
    all_terms = " | ".join(term for part in PARTS for term in part.source_terms)
    return {
        "document": {
            "sourceFileName": "fixture.docx",
            "sourceSha256": "a" * 64,
        },
        "blocks": [
            {
                "blockId": "block_fixture",
                "kind": "paragraph",
                "order": 1,
                "sectionPath": ["Tren delantero"],
                "text": all_terms,
                "textHash": sha256_text(all_terms),
            }
        ],
    }


class PilotPartsCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.pack = build_pack([synthetic_extraction()])

    def test_has_50_source_backed_review_only_parts(self) -> None:
        self.assertEqual(50, len(self.pack["parts"]))
        self.assertEqual(50, len({part["id"] for part in self.pack["parts"]}))
        for part in self.pack["parts"]:
            self.assertEqual("UNVERIFIED", part["confidence"])
            self.assertEqual("REVIEW_REQUIRED", part["publicationState"])
            self.assertEqual("REQUIRES_VERIFICATION", part["compatibilityState"])
            self.assertTrue(part["sourceRefs"])
            self.assertTrue(all(value is None for value in part["technicalSpecifications"].values()))

    def test_has_three_integral_procedures_and_torque_gate(self) -> None:
        self.assertEqual(3, len(self.pack["procedures"]))
        torque_steps = [
            step
            for procedure in self.pack["procedures"]
            for step in procedure["steps"]
            if step["completionGate"] == "VERIFIED_TORQUE_REQUIRED"
        ]
        self.assertEqual(1, len(torque_steps))
        self.assertIsNone(torque_steps[0]["technicalValue"])
        self.assertEqual("No confirmado para esta variante", torque_steps[0]["technicalValueMessage"])
        self.assertEqual([], validate_pack(self.pack))

    def test_content_is_deterministic(self) -> None:
        again = build_pack([synthetic_extraction()])
        self.assertEqual(self.pack["contentSha256"], again["contentSha256"])
        self.assertEqual(canonical_json(self.pack), canonical_json(again))

    def test_validator_rejects_unsafe_promotions(self) -> None:
        self.pack["parts"][0]["technicalSpecifications"]["torque"] = {"value": 100, "unit": "Nm"}
        self.pack["parts"][0]["compatibilityState"] = "EXACT"
        errors = validate_pack(self.pack)
        self.assertTrue(any("technical specifications" in error for error in errors))
        self.assertTrue(any("compatibility state" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
