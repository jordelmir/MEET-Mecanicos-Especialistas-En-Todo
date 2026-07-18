#!/usr/bin/env python3
"""Run the complete offline DOCX-to-review-queue pipeline."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any

from build_review_queue import build_review_queue
from common import PIPELINE_VERSION, sha256_file, write_json_atomic
from detect_contradictions import detect_contradictions
from extract_docx_text import extract_docx
from normalize_automotive_doc import normalize_extraction


def safe_slug(path: Path) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", path.stem.lower()).strip("_")
    return slug or "document"


def run_pipeline(
    documents: list[Path],
    output_dir: Path,
    default_vehicle_scope: str,
) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    snapshots_dir = output_dir / "snapshots"
    normalized_documents: list[dict[str, Any]] = []
    artifacts: list[dict[str, Any]] = []

    for document_path in documents:
        extraction = extract_docx(document_path, snapshots_dir)
        source_hash = extraction["document"]["sourceSha256"]
        slug = f"{safe_slug(document_path)}_{source_hash[:10]}"
        extraction_path = output_dir / f"{slug}.extracted.json"
        normalized_path = output_dir / f"{slug}.normalized.json"

        write_json_atomic(extraction_path, extraction)
        normalized = normalize_extraction(extraction)
        write_json_atomic(normalized_path, normalized)
        normalized_documents.append(normalized)
        artifacts.extend(
            [
                {
                    "type": "DOCX_SNAPSHOT",
                    "path": f"snapshots/{source_hash}.docx",
                    "sha256": source_hash,
                },
                {
                    "type": "EXTRACTION",
                    "path": extraction_path.name,
                    "sha256": sha256_file(extraction_path),
                },
                {
                    "type": "NORMALIZED_CANDIDATES",
                    "path": normalized_path.name,
                    "sha256": sha256_file(normalized_path),
                },
            ]
        )

    contradictions = detect_contradictions(normalized_documents, default_vehicle_scope)
    contradictions_path = output_dir / "contradictions.json"
    write_json_atomic(contradictions_path, contradictions)
    artifacts.append(
        {
            "type": "CONTRADICTIONS",
            "path": contradictions_path.name,
            "sha256": sha256_file(contradictions_path),
        }
    )

    review_queue = build_review_queue(normalized_documents, contradictions)
    review_queue_path = output_dir / "review_queue.json"
    write_json_atomic(review_queue_path, review_queue)
    artifacts.append(
        {
            "type": "REVIEW_QUEUE",
            "path": review_queue_path.name,
            "sha256": sha256_file(review_queue_path),
        }
    )

    manifest = {
        "schemaVersion": 1,
        "pipelineVersion": PIPELINE_VERSION,
        "defaultVehicleScope": default_vehicle_scope,
        "sourceDocuments": [
            {
                "sourceFileName": document["sourceDocument"]["sourceFileName"],
                "sourceSha256": document["sourceDocument"]["sourceSha256"],
                "candidateCount": document["statistics"]["candidateCount"],
                "measurementCandidateCount": document["statistics"]["measurementCandidateCount"],
                "securityRejectedCount": document["statistics"]["securityRejectedCount"],
            }
            for document in normalized_documents
        ],
        "contradictionCount": contradictions["statistics"]["conflictCount"],
        "reviewItemCount": review_queue["statistics"]["reviewItemCount"],
        "artifacts": artifacts,
        "publicationState": "REVIEW_REQUIRED",
        "autoPublishAllowed": False,
    }
    manifest_path = output_dir / "manifest.json"
    write_json_atomic(manifest_path, manifest)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docx", type=Path, action="append", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--default-vehicle-scope",
        default="hyundai_accent_verna_2005_1_6_at",
    )
    args = parser.parse_args()

    manifest = run_pipeline(args.docx, args.output_dir, args.default_vehicle_scope)
    print(
        f"Pipeline complete: {len(manifest['sourceDocuments'])} documents, "
        f"{manifest['reviewItemCount']} review items, "
        f"{manifest['contradictionCount']} potential contradictions."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
