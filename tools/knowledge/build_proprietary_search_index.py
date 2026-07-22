#!/usr/bin/env python3
"""Build the offline FTS index derived from the immutable proprietary shards."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
from pathlib import Path


SCHEMA_VERSION = 1
EXPECTED_BLOCK_COUNT = 74_648


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def build_index(assets_root: Path, output: Path) -> dict[str, object]:
    catalog_root = assets_root / "knowledge" / "proprietary"
    manifest_path = catalog_root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    statistics = manifest.get("statistics") or {}
    if statistics.get("blockCount") != EXPECTED_BLOCK_COUNT:
        raise ValueError("The proprietary manifest does not contain 74,648 blocks")

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)

    connection = sqlite3.connect(temporary)
    try:
        connection.executescript(
            """
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA temp_store=MEMORY;
            CREATE TABLE metadata (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            ) WITHOUT ROWID;
            CREATE TABLE blocks (
                row_id INTEGER PRIMARY KEY,
                block_id TEXT NOT NULL,
                section_id TEXT NOT NULL,
                section_title TEXT NOT NULL,
                system_id TEXT NOT NULL,
                source_document_id TEXT NOT NULL,
                source_file_name TEXT NOT NULL,
                source_order INTEGER NOT NULL,
                record_role TEXT NOT NULL,
                kind TEXT NOT NULL,
                text TEXT NOT NULL,
                text_hash TEXT NOT NULL,
                entity_id TEXT,
                parent_entity_id TEXT,
                rows_json TEXT,
                UNIQUE(source_document_id, block_id)
            );
            CREATE INDEX blocks_system_role_order
                ON blocks(system_id, record_role, source_order);
            CREATE INDEX blocks_entity ON blocks(entity_id);
            CREATE INDEX blocks_parent_entity ON blocks(parent_entity_id);
            CREATE VIRTUAL TABLE block_search USING fts4(
                text,
                section_title,
                content='blocks',
                tokenize=unicode61 'remove_diacritics=2'
            );
            """
        )

        row_id = 0
        seen_blocks: set[tuple[str, str]] = set()
        insert_block = """
            INSERT INTO blocks(
                row_id, block_id, section_id, section_title, system_id,
                source_document_id, source_file_name, source_order, record_role,
                kind, text, text_hash, entity_id, parent_entity_id, rows_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        insert_fts = "INSERT INTO block_search(docid, text, section_title) VALUES (?, ?, ?)"

        for section in manifest["sections"]:
            shard_path = assets_root / section["shardPath"]
            shard = json.loads(shard_path.read_text(encoding="utf-8"))
            if len(shard["blocks"]) != section["blockCount"]:
                raise ValueError(f"Block count mismatch in {section['id']}")
            for block in shard["blocks"]:
                key = (shard["sourceDocumentId"], block["blockId"])
                if key in seen_blocks:
                    raise ValueError(f"Duplicate source block {key}")
                seen_blocks.add(key)
                if sha256_text(block["text"]) != block["textHash"]:
                    raise ValueError(f"Changed literal text in {block['blockId']}")
                row_id += 1
                values = (
                    row_id,
                    block["blockId"],
                    shard["sectionId"],
                    shard["titleOriginal"],
                    shard["systemId"],
                    shard["sourceDocumentId"],
                    shard["sourceFileName"],
                    block["order"],
                    block["recordRole"],
                    block["kind"],
                    block["text"],
                    block["textHash"],
                    block.get("entityId"),
                    block.get("parentEntityId"),
                    json.dumps(block.get("rows"), ensure_ascii=False, separators=(",", ":"))
                    if block.get("rows") is not None
                    else None,
                )
                connection.execute(insert_block, values)
                connection.execute(insert_fts, (row_id, block["text"], shard["titleOriginal"]))

        if row_id != EXPECTED_BLOCK_COUNT:
            raise ValueError(f"Literal coverage mismatch: {row_id} != {EXPECTED_BLOCK_COUNT}")

        metadata = {
            "schema_version": str(SCHEMA_VERSION),
            "corpus_id": manifest["corpusId"],
            "corpus_version": manifest["corpusVersion"],
            "corpus_sha256": manifest["contentSha256"],
            "block_count": str(row_id),
            "source_hashes": ",".join(sorted(item["sourceSha256"] for item in manifest["sourceDocuments"])),
        }
        connection.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
        connection.commit()
        connection.execute("ANALYZE")
        connection.execute("VACUUM")
        connection.commit()
    finally:
        connection.close()

    os.replace(temporary, output)
    return {
        "status": "PASS",
        "blockCount": EXPECTED_BLOCK_COUNT,
        "corpusSha256": manifest["contentSha256"],
        "sizeBytes": output.stat().st_size,
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-assets-root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or args.android_assets_root / "knowledge" / "proprietary" / "search.sqlite.gzip"
    if output.suffix in {".gz", ".gzip"}:
        sqlite_output = output.with_suffix("")
        report = build_index(args.android_assets_root, sqlite_output)
        compressed_temporary = output.with_suffix(output.suffix + ".tmp")
        with sqlite_output.open("rb") as source, compressed_temporary.open("wb") as raw_target:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw_target, compresslevel=9, mtime=0) as target:
                shutil.copyfileobj(source, target)
        os.replace(compressed_temporary, output)
        sqlite_output.unlink()
        report["sqliteSizeBytes"] = report["sizeBytes"]
        report["sizeBytes"] = output.stat().st_size
        report["sha256"] = hashlib.sha256(output.read_bytes()).hexdigest()
    else:
        report = build_index(args.android_assets_root, output)
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
