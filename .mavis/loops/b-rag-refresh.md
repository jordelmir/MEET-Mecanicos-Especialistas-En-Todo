# Loop B — RAG Refresh

## Frecuencia

- Después de merge
- Antes de features grandes
- Cuando cambian arquitectura, DB, APIs o documentación

## Objetivo

Mantener la memoria técnica sincronizada con el código real.

## Fuentes permitidas

```yaml
sources:
  code:
    - src/
    - app/
    - backend/
    - frontend/
    - android/
    - supabase/
    - migrations/
  docs:
    - README.md
    - CHANGELOG.md
    - docs/
    - .mavis/memory/
    - .mavis/adr/
    - .mavis/runbooks/
  ci:
    - .github/workflows/
  config:
    - package.json
    - build.gradle
    - build.gradle.kts
    - Cargo.toml
    - docker-compose.yml
    - compose.yaml
```

## Fuentes prohibidas

```yaml
forbidden:
  - .env
  - .env.*
  - secrets
  - keystore
  - signing keys
  - tokens
  - credentials
  - production dumps
  - private customer data
  - VIN/GPS raw logs
  - payment data
  - personally identifiable data
```

## Reglas

- El RAG puede indexar resúmenes técnicos
- **NO** debe guardar secretos
- **NO** debe guardar datos personales sensibles
- **NO** debe guardar logs crudos con información sensible
- **NO** debe considerar memoria vieja más confiable que el código actual

## Política de embeddings

**No commitear embeddings pesados.**

- ✅ `index-metadata.json` con punteros a fuentes y commits
- ✅ `last-refresh.md` con archivos escaneados y exclusiones
- ✅ `sources.yaml` declarativo
- ❌ NO `embeddings.bin`, `vectors.faiss`, `chroma.sqlite` ni nada binario opaco

El vector index puede regenerarse. La verdad debe vivir en Git.

## Tipos de memoria

- **Semántica**: qué existe (módulos, transporte BLE/WiFi, ranking DTC, RLS)
- **Episódica**: qué pasó (fechas, eventos, hallazgos)
- **Procedimental**: cómo hacer cosas (validar migración, correr tests RLS)
- **De decisiones**: por qué se eligió algo (Outbox Pattern, etc.)

## Chunking policy

Ver `.mavis/rag/chunking-policy.md`.

## Retrieval policy

Ver `.mavis/rag/retrieval-policy.md`.

## Output obligatorio — RAG Refresh Report

```markdown
# RAG Refresh Report

- Date:
- Commit:
- Files scanned:
- Files excluded:
- New architecture facts:
- Changed contracts:
- Removed stale facts:
- Security exclusions:
- Index version:
- Next refresh trigger:
```

Guardar en `.mavis/rag/last-refresh.md`.

## Comando reusable

```
Ejecuta RAG Refresh Loop.

Reglas:
1. El código actual es la fuente de verdad.
2. Lee archivos críticos modificados recientemente.
3. Lee tests relacionados.
4. Lee migraciones, CI y docs.
5. Actualiza .mavis/memory/project-memory.md.
6. Actualiza .mavis/memory/architecture-map.md.
7. Actualiza .mavis/memory/decision-log.md si hubo decisión técnica.
8. Actualiza .mavis/rag/index-metadata.json.
9. Actualiza .mavis/rag/last-refresh.md.
10. No indexar secretos, .env, tokens, keystores, dumps, VIN/GPS raw logs ni datos personales.
11. Crea commit chore(rag): refresh project memory.
12. Abre PR.
13. No hagas merge.
```