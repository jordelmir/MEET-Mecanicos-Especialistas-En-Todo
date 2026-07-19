from __future__ import annotations

import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_ROOT))

from build_proprietary_search_index import build_index, sha256_text  # noqa: E402


class ProprietarySearchIndexTest(unittest.TestCase):
    def test_builds_searchable_index_without_changing_literal_text(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / "assets"
            catalog = assets / "knowledge" / "proprietary"
            sections = catalog / "sections"
            sections.mkdir(parents=True)
            blocks = []
            for order in range(1, 74_649):
                text = "Sensor de presión especial" if order == 74_648 else f"Detalle literal {order}"
                blocks.append(
                    {
                        "blockId": f"block_{order}",
                        "kind": "paragraph",
                        "order": order,
                        "recordRole": "SOURCE_DETAIL",
                        "sectionPath": ["Prueba"],
                        "styleId": "",
                        "text": text,
                        "textHash": sha256_text(text),
                        "entityId": None,
                        "parentEntityId": "component_1",
                    }
                )
            shard = {
                "sectionId": "section_1",
                "systemId": "sensors",
                "titleOriginal": "Sensores de presión",
                "sourceDocumentId": "document_16",
                "sourceFileName": "Document (16).docx",
                "blocks": blocks,
            }
            (sections / "section_1.json").write_text(json.dumps(shard), encoding="utf-8")
            manifest = {
                "corpusId": "test",
                "corpusVersion": "1",
                "contentSha256": "a" * 64,
                "statistics": {"blockCount": 74_648},
                "sourceDocuments": [{"sourceSha256": "b" * 64}],
                "sections": [
                    {
                        "id": "section_1",
                        "shardPath": "knowledge/proprietary/sections/section_1.json",
                        "blockCount": 74_648,
                    }
                ],
            }
            (catalog / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            output = catalog / "search.sqlite"

            report = build_index(assets, output)

            self.assertEqual(74_648, report["blockCount"])
            with sqlite3.connect(output) as database:
                count = database.execute("SELECT COUNT(*) FROM blocks").fetchone()[0]
                hit = database.execute(
                    "SELECT blocks.text, blocks.text_hash FROM block_search "
                    "JOIN blocks ON blocks.row_id = block_search.docid "
                    "WHERE block_search MATCH ?",
                    ('text:presion*',),
                ).fetchone()
            self.assertEqual(74_648, count)
            self.assertEqual("Sensor de presión especial", hit[0])
            self.assertEqual(sha256_text(hit[0]), hit[1])


if __name__ == "__main__":
    unittest.main()
