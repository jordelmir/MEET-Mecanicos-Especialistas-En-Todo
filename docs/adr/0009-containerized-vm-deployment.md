# ADR-009: Immutable Container Deployment on Hardened VMs

## Status: ACCEPTED
## Context:
Production requires immutable build artifacts and predictable startup.
## Decision:
Deploy multi-stage Docker containers with non-root runtime pinned by Git SHA and image digest.
