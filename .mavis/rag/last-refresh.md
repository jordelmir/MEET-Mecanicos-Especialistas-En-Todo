# RAG Last Refresh — MEET

> Última ejecución de Loop B (RAG Refresh).
> Si este archivo tiene más de 7 días, considerar correr Loop B.

## Refresh Report

- **Date**: 2026-07-02T08:58:00Z (bootstrap)
- **Commit**: `f9b1adb` — feat(gauges): move actions section to top, fix publish button logic and enable remote background image loading
- **Mode**: bootstrap (no previous index)

## Files scanned

- Web source tree: `src/`, `components/`, `App.tsx`, `index.tsx`, `lib/`, `services/`
- Android source: `android/app/src/main/kotlin/com/elysium369/meet/`
- Supabase: `supabase/migrations/` (10 migrations), `supabase/functions/` (8 edge functions), `supabase_schema.sql`
- Python ingest: `meet-elite-ingest/src/`, `meet-elite-ingest/tests/`
- Docs: `README.md`, `DOCUMENTATION.md`, `docs/`, `GUIA_FIRMA_APK.md`
- CI: `.github/workflows/` (vacío al bootstrap)
- Config: `package.json`, `tsconfig.json`, `vite.config.ts`, `tailwind.config.cjs`, build.gradle.kts, pyproject.toml, requirements.txt, alembic.ini, Dockerfile, docker-compose.yml, `.env.example`

## Files excluded (security / scope)

- `.env`, `.env.local`, `secrets/`, `*.keystore`, `*.jks`, `*.pem`, `*.key`
- `node_modules/`, `build/`, `.gradle/`, `dist/`, `__pycache__/`, `.vercel/`, `.android/`
- `.git/`
- Lockfiles (referenciados, no chunkeados)

## New architecture facts

- 8 nuevos edge functions para lifecycle de repairs (accept-offer, close-repair, transition-state, verify-provider, payment-router, stripe-webhook, sync-outbox, _shared)
- 10 migrations Supabase en junio 2026 (vanguard_commerce, vanguard_access_policy, brand_aliases, vanguard_p0_foundation, rpc_procedures, marketplace_publish_flow, backend_real_hardening, backend_real_reconciliation, fix_transition_lock, marketplace_trust_verification)
- Knowledge OS + Knowledge Graph + Knowledge Pack Verified en `android/core/knowledge/`
- Vanguard Commerce + Vanguard Telemetry entities en `android/data/local/entities/`
- Knowledge Os Debug screen + MechanicalDiagrams2D + VirtualOscilloscope en `android/ui/`

## Changed contracts

- (No hay Git history accesible en bootstrap — solo se observa `git log` reciente, no diff contra tag previo)

## Removed stale facts

- (Bootstrap inicial, no aplica)

## Security exclusions

Aplicadas via `sources.yaml` y `redaction_rules` en `index-metadata.json`:

- `.env*`, secrets, keystores, signing keys → excluidos
- VIN/GPS raw logs → excluidos (PII automotriz)
- payment data → excluido
- Heurísticas de API keys / Stripe keys / JWT → redactadas

## Index version

`1` — versión inicial.

## Embedding model

`null` — sin embeddings persistidos al bootstrap. Si se monta RAG externo, actualizar este campo.

## Vector store

`null` — sin vector store al bootstrap.

## Next refresh trigger

- Cualquier merge a `main`
- Cualquier migración nueva en `supabase/migrations/`
- Cualquier nuevo módulo en `android/app/src/main/`
- Cadencia máxima: 7 días

## Notas operativas

- Mavis en este momento hace retrieval **directo por Read** (no RAG semántico) hasta que se monte el vector store.
- La política "RAG apunta, agente verifica" se respeta: si una decisión es importante, abrir el archivo.