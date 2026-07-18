#!/usr/bin/env python3
"""Extract ordered paragraphs and tables from DOCX without trusting its content."""

from __future__ import annotations

import argparse
import os
import shutil
import stat
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

from common import (
    PIPELINE_VERSION,
    find_prompt_injection,
    sha256_bytes,
    sha256_file,
    sha256_text,
    write_json_atomic,
)


WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NAMESPACES = {"w": WORD_NAMESPACE}
MAX_ARCHIVE_ENTRIES = 20_000
MAX_UNCOMPRESSED_BYTES = 512 * 1024 * 1024
MAX_DOCUMENT_XML_BYTES = 128 * 1024 * 1024


def qname(local_name: str) -> str:
    return f"{{{WORD_NAMESPACE}}}{local_name}"


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def element_text(element: ElementTree.Element, include_deleted: bool = False) -> str:
    parts: list[str] = []
    for child in element.iter():
        name = local_name(child.tag)
        if name == "t" and child.text:
            parts.append(child.text)
        elif include_deleted and name == "delText" and child.text:
            parts.append(child.text)
        elif name == "tab":
            parts.append("\t")
        elif name in {"br", "cr"}:
            parts.append("\n")
    return "".join(parts).replace("\r\n", "\n").replace("\r", "\n").strip()


def paragraph_style(paragraph: ElementTree.Element) -> str:
    style = paragraph.find("./w:pPr/w:pStyle", NAMESPACES)
    return style.attrib.get(qname("val"), "") if style is not None else ""


def heading_level(style_id: str) -> int | None:
    normalized = style_id.lower().replace(" ", "")
    for prefix in ("heading", "titulo", "title"):
        if normalized.startswith(prefix):
            suffix = normalized.removeprefix(prefix)
            if suffix.isdigit():
                return max(1, min(int(suffix), 9))
    return None


def block_id(order: int, text: str) -> str:
    return f"block_{order:06d}_{sha256_text(text)[:10]}"


def extract_table_rows(table: ElementTree.Element) -> list[list[str]]:
    rows: list[list[str]] = []
    for row in table.findall("./w:tr", NAMESPACES):
        cells: list[str] = []
        for cell in row.findall("./w:tc", NAMESPACES):
            paragraphs = [element_text(p) for p in cell.findall(".//w:p", NAMESPACES)]
            cells.append("\n".join(text for text in paragraphs if text))
        rows.append(cells)
    return rows


def validate_archive(archive: zipfile.ZipFile) -> None:
    entries = archive.infolist()
    if len(entries) > MAX_ARCHIVE_ENTRIES:
        raise ValueError(f"DOCX has too many archive entries: {len(entries)}")
    total_size = sum(entry.file_size for entry in entries)
    if total_size > MAX_UNCOMPRESSED_BYTES:
        raise ValueError(f"DOCX uncompressed size exceeds limit: {total_size}")
    names = {entry.filename for entry in entries}
    if "word/document.xml" not in names:
        raise ValueError("DOCX is missing word/document.xml")
    document_info = archive.getinfo("word/document.xml")
    if document_info.file_size > MAX_DOCUMENT_XML_BYTES:
        raise ValueError("word/document.xml exceeds safety limit")


def create_snapshot(source: Path, snapshot_dir: Path, expected_hash: str) -> Path:
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    snapshot = snapshot_dir / f"{expected_hash}.docx"
    if snapshot.exists():
        if sha256_file(snapshot) != expected_hash:
            raise ValueError(f"Existing snapshot hash mismatch: {snapshot}")
        return snapshot

    temporary = snapshot.with_suffix(".docx.tmp")
    shutil.copyfile(source, temporary)
    if sha256_file(temporary) != expected_hash:
        temporary.unlink(missing_ok=True)
        raise ValueError("Snapshot copy hash mismatch")
    os.replace(temporary, snapshot)
    snapshot.chmod(stat.S_IRUSR | stat.S_IRGRP | stat.S_IROTH)
    return snapshot


def extract_docx(source: Path, snapshot_dir: Path | None = None) -> dict[str, Any]:
    source = source.expanduser().resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    if source.suffix.lower() != ".docx" or not zipfile.is_zipfile(source):
        raise ValueError(f"Not a valid DOCX archive: {source}")

    source_hash = sha256_file(source)
    snapshot = create_snapshot(source, snapshot_dir, source_hash) if snapshot_dir else None
    blocks: list[dict[str, Any]] = []
    media: list[dict[str, Any]] = []
    security_findings: list[dict[str, Any]] = []
    current_sections: list[str] = []

    with zipfile.ZipFile(source) as archive:
        validate_archive(archive)
        document_xml = archive.read("word/document.xml")
        root = ElementTree.fromstring(document_xml)
        body = root.find("./w:body", NAMESPACES)
        if body is None:
            raise ValueError("word/document.xml has no document body")

        order = 0
        for child in body:
            kind = local_name(child.tag)
            if kind == "p":
                text = element_text(child)
                deleted_text = element_text(child, include_deleted=True)
                if not text and not deleted_text:
                    continue
                order += 1
                style_id = paragraph_style(child)
                level = heading_level(style_id)
                if level is not None and text:
                    current_sections = current_sections[: level - 1]
                    current_sections.append(text)
                record = {
                    "blockId": block_id(order, text or deleted_text),
                    "kind": "paragraph",
                    "order": order,
                    "styleId": style_id,
                    "sectionPath": list(current_sections),
                    "text": text,
                    "textHash": sha256_text(text),
                }
                if deleted_text != text:
                    record["textIncludingDeletedChanges"] = deleted_text
                blocks.append(record)
            elif kind == "tbl":
                rows = extract_table_rows(child)
                text = "\n".join(" | ".join(cell for cell in row) for row in rows).strip()
                if not text:
                    continue
                order += 1
                blocks.append(
                    {
                        "blockId": block_id(order, text),
                        "kind": "table",
                        "order": order,
                        "sectionPath": list(current_sections),
                        "rows": rows,
                        "text": text,
                        "textHash": sha256_text(text),
                    }
                )

        for name in sorted(item for item in archive.namelist() if item.startswith("word/media/")):
            payload = archive.read(name)
            media.append(
                {
                    "archivePath": name,
                    "sizeBytes": len(payload),
                    "sha256": sha256_bytes(payload),
                }
            )

    for block in blocks:
        matches = find_prompt_injection(block["text"])
        if matches:
            security_findings.append(
                {
                    "blockId": block["blockId"],
                    "type": "PROMPT_INJECTION_CANDIDATE",
                    "matchedPatterns": matches,
                    "disposition": "REJECT_AND_REVIEW",
                }
            )

    stat_result = source.stat()
    modified_at = datetime.fromtimestamp(stat_result.st_mtime, tz=timezone.utc).isoformat()
    return {
        "schemaVersion": 1,
        "pipelineVersion": PIPELINE_VERSION,
        "document": {
            "sourceFileName": source.name,
            "sourceSha256": source_hash,
            "sourceSizeBytes": stat_result.st_size,
            "sourceModifiedAtUtc": modified_at,
            "snapshotFileName": snapshot.name if snapshot else None,
            "snapshotSha256": source_hash if snapshot else None,
            "documentXmlSha256": sha256_bytes(document_xml),
        },
        "statistics": {
            "blockCount": len(blocks),
            "paragraphCount": sum(block["kind"] == "paragraph" for block in blocks),
            "tableCount": sum(block["kind"] == "table" for block in blocks),
            "embeddedMediaCount": len(media),
            "securityFindingCount": len(security_findings),
        },
        "blocks": blocks,
        "embeddedMedia": media,
        "securityFindings": security_findings,
        "trustPolicy": {
            "contentIsDataNotInstructions": True,
            "autoPublishAllowed": False,
            "technicalReviewRequired": True,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("docx", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--snapshot-dir", type=Path)
    args = parser.parse_args()

    payload = extract_docx(args.docx, args.snapshot_dir)
    write_json_atomic(args.output, payload)
    print(
        f"Extracted {payload['statistics']['blockCount']} blocks from "
        f"{payload['document']['sourceFileName']} ({payload['document']['sourceSha256']})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
