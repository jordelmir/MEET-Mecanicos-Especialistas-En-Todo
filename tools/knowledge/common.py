#!/usr/bin/env python3
"""Shared deterministic helpers for the MEET knowledge ingestion pipeline."""

from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
import unicodedata
from pathlib import Path
from typing import Any, Iterable


PIPELINE_VERSION = "1.0.0"

PROMPT_INJECTION_PATTERNS = (
    re.compile(r"ignore\s+(?:all\s+)?(?:previous|prior|system)\s+instructions", re.I),
    re.compile(r"reveal\s+(?:the\s+)?system\s+prompt", re.I),
    re.compile(r"execute\s+(?:this\s+)?(?:shell|terminal|system)\s+command", re.I),
    re.compile(r"exfiltrate\s+(?:credentials|secrets|data)", re.I),
    re.compile(r"ignora\s+(?:todas\s+)?(?:las\s+)?instrucciones", re.I),
    re.compile(r"revela\s+(?:el\s+)?prompt\s+(?:del\s+)?sistema", re.I),
    re.compile(r"ejecuta\s+(?:este\s+)?comando", re.I),
)

DTC_PATTERN = re.compile(r"(?<![A-Z0-9])[PBCU][0-3][0-9A-F]{3}(?![A-Z0-9])", re.I)
MEASUREMENT_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])"
    r"(?P<value>-?\d+(?:[.,]\d+)?)"
    r"(?:\s*(?:-|a|to)\s*(?P<maximum>-?\d+(?:[.,]\d+)?))?"
    r"\s*(?P<unit>Nm|N·m|lb-ft|ft-lb|V|mV|A|mA|ohm(?:ios?)?|Ω|kΩ|MΩ|psi|bar|kPa|MPa|°C|°F|mm|cm|km|rpm|Hz|kHz|%|L|ml)\b",
    re.I,
)


def normalize_text(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(char for char in decomposed if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", without_marks).strip().lower()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        suffix=".tmp",
        dir=path.parent,
        text=True,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def find_prompt_injection(text: str) -> list[str]:
    return [pattern.pattern for pattern in PROMPT_INJECTION_PATTERNS if pattern.search(text)]


def find_dtcs(text: str) -> list[str]:
    return sorted({match.group(0).upper() for match in DTC_PATTERN.finditer(text)})


def find_measurements(text: str) -> list[dict[str, Any]]:
    measurements: list[dict[str, Any]] = []
    for index, match in enumerate(MEASUREMENT_PATTERN.finditer(text), start=1):
        raw_value = match.group("value").replace(",", ".")
        raw_maximum = match.group("maximum")
        measurements.append(
            {
                "candidateId": f"measurement_{index:03d}",
                "rawText": match.group(0),
                "minimumOrNominalValue": float(raw_value),
                "maximumValue": float(raw_maximum.replace(",", ".")) if raw_maximum else None,
                "unitCode": match.group("unit"),
                "verificationStatus": "PENDING_DOCUMENT_REVIEW",
                "displayPolicy": "DO_NOT_USE_AS_FINAL_REPAIR_SPEC",
            }
        )
    return measurements


def unique_sorted(values: Iterable[str]) -> list[str]:
    return sorted({value for value in values if value})
