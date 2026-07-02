# Project Memory

## Current Architecture

Bootstrap pending. This file must be updated after repo forensics and verified code inspection.

## Critical Domains

- Android/Kotlin/Compose
- OBD-II / automotive diagnostics
- Telemetry
- GitHub-based engineering loop
- Security and privacy

## Safety Rules

- Do not expose secrets.
- Do not index sensitive files.
- Do not touch production without explicit approval.
- Do not modify product code during A1 bootstrap.

## Important Commands

```bash
./scripts/mavis-loop.sh continuous bootstrap-quality-gate
```

## Current Risks

To be populated after the first complete repo forensics loop.

## Last Updated

Bootstrap.