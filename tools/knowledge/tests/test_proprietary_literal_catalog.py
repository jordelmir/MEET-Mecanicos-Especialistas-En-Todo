from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from build_proprietary_literal_catalog import (
    EXPECTED_SOURCES,
    VEHICLE_LABEL,
    build_catalog,
    canonical_json,
    classify_blocks,
    entity_from_block,
    validate_catalog,
)
from common import sha256_text


class ProprietaryLiteralCatalogUnitTest(unittest.TestCase):
    def test_vehicle_label_is_the_approved_profile(self) -> None:
        self.assertEqual(
            "Hyundai Accent/Verna 2005 · caja automática · motor 1600 cc",
            VEHICLE_LABEL,
        )

    def test_component_classifier_keeps_real_cases_distinct(self) -> None:
        blocks = [
            {"order": 1, "text": "Motor", "styleId": "Heading2", "kind": "paragraph"},
            {"order": 2, "text": "• Sensor CKP cigüeñal", "styleId": "", "kind": "paragraph"},
            {"order": 3, "text": "Ejemplo real: Toyota Camry 2007 2.4", "styleId": "", "kind": "paragraph"},
            {"order": 4, "text": "Desconectar batería.", "styleId": "ListParagraph", "kind": "paragraph"},
        ]
        self.assertEqual(
            ["SECTION_TITLE", "COMPONENT", "REAL_CASE", "SOURCE_DETAIL"],
            classify_blocks(blocks, "document_17"),
        )

    def test_3d_binding_is_deterministic_and_non_dimensional(self) -> None:
        text = "Sensor CKP del cigüeñal"
        block = {
            "blockId": "block_000001_fixture",
            "order": 1,
            "text": text,
            "textHash": sha256_text(text),
        }
        document = {"sourceFileName": "Document (17).docx", "sourceSha256": EXPECTED_SOURCES["Document (17).docx"]["sha256"]}
        first = entity_from_block(block, document, "document_17", "section", "knowledge/proprietary/sections/section.json", "sensors", "COMPONENT")
        second = entity_from_block(block, document, "document_17", "section", "knowledge/proprietary/sections/section.json", "sensors", "COMPONENT")
        self.assertEqual(canonical_json(first), canonical_json(second))
        self.assertEqual(first["id"], first["threeDimensionalBinding"]["nodeId"])
        self.assertFalse(first["threeDimensionalBinding"]["isDimensionalModel"])


if __name__ == "__main__":
    unittest.main()
