# Decision Log

## Bootstrap — GitHub as Source of Truth

### Context

The project needs a continuous engineering loop with auditable memory, RAG metadata, reports and quality gates.

### Decision

Use GitHub and repository-versioned files as the source of truth.

### Alternatives

- Opaque vector memory only.
- Chat-only memory.
- External-only task tracking.

### Trade-off

Versioned memory is more verbose but safer, auditable and reproducible.

### Consequences

RAG index can be regenerated. Code, tests, migrations, CI, ADRs and memory files remain reviewable.

### Revisit when

The project introduces a dedicated production-grade knowledge base or documentation portal.