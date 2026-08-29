# ADR-002: Supabase Retained as Managed Data Plane

## Status: ACCEPTED
## Context:
PostgreSQL, Auth, RLS, Storage, and Edge Functions are already established and verified in production CI.
## Decision:
Retain Supabase as the managed data plane rather than self-hosting unmanaged PostgreSQL and Auth instances.
