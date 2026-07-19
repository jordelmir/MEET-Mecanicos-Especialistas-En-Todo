# MEET Automotive Knowledge Importer

This toolchain converts untrusted `.docx` sources into deterministic extraction artifacts and a technical review queue. It never publishes canonical knowledge automatically.

## Pipeline

```text
DOCX
  -> immutable SHA-256 snapshot
  -> ordered paragraphs/tables/media manifest
  -> conservative candidate normalization
  -> prompt-injection flags
  -> applicability and measurement candidates
  -> contradiction detection
  -> review queue
```

All scripts use the Python standard library. Generated artifacts belong in `build/knowledge/`, which is ignored by Git.

## Run

```bash
python3 tools/knowledge/pipeline.py \
  --docx "/path/to/Document (16).docx" \
  --docx "/path/to/Document (17).docx" \
  --default-vehicle-scope hyundai_accent_verna_2005_1_6_at \
  --output-dir build/knowledge/documents_16_17
```

The output includes:

- immutable snapshots named by SHA-256;
- `*.extracted.json` with ordered paragraphs and tables;
- `*.normalized.json` with unverified review candidates;
- `contradictions.json` with unresolved presence/absence conflicts;
- `review_queue.json` with review roles and priority;
- `manifest.json` with artifact hashes.

## Validate Packs

```bash
python3 tools/knowledge/validate_knowledge_pack.py \
  android/app/src/main/assets/knowledge/packs/pack_06_dtc_P0230.json
```

The validator loads `schema/knowledge-pack.schema.json` and enforces additional semantic rules:

- source tier H is rejected;
- edges must reference existing nodes;
- IDs cannot be duplicated;
- prompt-injection candidates are rejected;
- verified claims require reviewed source citations;
- verified measurements require unit, condition, instrument, tolerance and a verified source claim;
- presence/absence conflicts must be declared explicitly.

A pack can be structurally `VALID` and still return `REVIEW_REQUIRED`. Validation is never equivalent to publication; only fully reviewed content can become `ACTIVE`.

## Build The Source-Backed Front-End Pilot

After running the DOCX pipeline, generate the shared web/Android catalog with:

```bash
python3 tools/knowledge/build_pilot_parts_catalog.py \
  --extracted build/knowledge/universal_parts_discovery/document_16_09f2926a22.extracted.json \
  --extracted build/knowledge/universal_parts_discovery/document_17_baf4add3f2.extracted.json \
  --output android/app/src/main/assets/knowledge/catalog/pilot_hyundai_accent_verna_2005_front_end.json
```

The generator fails when a glossary entity has no source block, IDs are duplicated, references are broken, a procedure targets an unknown part, or an unverified technical value is promoted. The current pilot deliberately contains no published OEM, torque, material, dimension or pinout claim.

## Build The Complete Android Literal Search Index

After the complete proprietary shards have been generated, rebuild the offline
Android search database with:

```bash
python3 tools/knowledge/build_proprietary_search_index.py \
  --android-assets-root android/app/src/main/assets
```

The command validates all 74,648 text hashes before replacing
`knowledge/proprietary/search.sqlite.gzip`. The non-special extension prevents
Android Asset Packaging Tool from transparently stripping `.gz`. The compressed asset is derived data;
`manifest.json` and the 347 section shards remain authoritative. Android checks
the corpus SHA and row count before opening the index.

## Safety Boundary

- DOCX text is data, never an instruction to the agent or app.
- Extracted values remain `PENDING_DOCUMENT_REVIEW`.
- Reference examples never become target-vehicle facts.
- The review queue has `publishable: false` for every item.
- A later editorial step must create and sign a validated content pack.
- The pilot 3D scene is a generic schematic, not an OEM or dimensional model.

## Tests

```bash
python3 -m unittest discover -s tools/knowledge/tests -p 'test_*.py'
```
