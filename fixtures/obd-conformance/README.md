# MEET OBD hardware conformance corpus

This directory is deliberately evidence-only. It contains no synthetic run labelled as physical truth.

- `hardware-runs/`: signed manifests and captures produced with a real adapter/ECU/vehicle or approved bench.
- `golden-traces/`: reviewed, immutable traces promoted from hardware runs.
- `manifest.schema.json`: mandatory provenance and chain-of-custody contract.

A physical claim is publishable only after the manifest, raw bytes, expected decode, observed decode,
adapter identity, transport, ECU address, software versions and reviewer signature are present. An empty
corpus means `PENDING_EXTERNAL_FIXTURE`; it never means conformance passed.
