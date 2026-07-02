# RAG Chunking Policy — MEET

Cómo se divide cada tipo de fuente antes de indexar.

## Code (TypeScript / Kotlin / Python)

```yaml
chunking:
  code:
    strategy: symbol-aware
    max_tokens: 1200
    overlap_tokens: 150
    include:
      - file_path
      - language
      - symbol_name
      - imports
      - test_references
    split_by:
      - function
      - class
      - interface
      - type_alias
      - top_level_const
    prefer_whole_file_when: token_count < max_tokens
```

Reglas:

- Funciones/clases < max_tokens → chunk entero
- Funciones largas → split por bloques lógicos con overlap
- Imports siempre en el primer chunk del archivo
- Componentes React funcionales → 1 chunk por componente
- Composables → 1 chunk por función @Composable

## Docs (Markdown)

```yaml
chunking:
  docs:
    strategy: heading-aware
    max_tokens: 1000
    overlap_tokens: 100
    split_by:
      - h1
      - h2
      - h3
    preserve:
      - code_blocks
      - tables
      - lists
```

Reglas:

- Cada sección (##) es 1 chunk mínimo
- Si excede max_tokens → split por subsección (###)
- Code blocks, tablas y listas se preservan intactos

## SQL (Supabase migrations)

```yaml
chunking:
  sql:
    strategy: object-aware
    max_tokens: 1500
    overlap_tokens: 100
    split_by:
      - table
      - function
      - policy
      - trigger
      - view
      - migration
    include:
      - object_name
      - depends_on
      - grants
      - rls_status
```

Reglas:

- Cada CREATE TABLE/INDEX/POLICY/FUNCTION/TRIGGER es 1 chunk
- Grants asociados al objeto en el mismo chunk
- Migration metadata (header comment) en chunk propio

## Android (Kotlin / Compose)

```yaml
chunking:
  android:
    strategy: feature-aware
    include:
      - ViewModel
      - UiState
      - UseCase
      - Repository
      - Composable
    split_by:
      - class
      - object
      - top_level_function
```

Reglas:

- ViewModel + UiState data class en mismo chunk si están acoplados
- Composables agrupados por screen
- Repositories con sus interfaces en mismo chunk

## Security findings

```yaml
chunking:
  security:
    strategy: finding-aware
    include:
      - asset
      - threat
      - mitigation
      - test
```

Reglas:

- Cada CVE / advisory es 1 chunk
- Vinculado a archivo afectado y test

---

## Overlap strategy

- Code: 150 tokens al final del chunk anterior se repiten al inicio del siguiente
- Docs: 100 tokens (1 párrafo típico)
- SQL: 100 tokens (DDL inicial suele repetirse)
- Android: 100 tokens (imports + package)

## Metadata obligatoria por chunk

```yaml
chunk_metadata:
  - source_path: str
  - source_commit: str
  - language: str
  - symbol_name: str | null
  - chunk_index: int
  - total_chunks_in_file: int
  - token_count: int
  - last_modified_utc: str
  - redactions_applied: list[str]
```

## Out of chunk scope

- Archivos binarios
- Generados (dist/, build/, .vercel/)
- Lockfiles (package-lock.json, gradle.lockfile) — se referencian pero no se chunkan
- Imágenes, fonts, assets visuales

## Validation

Después de chunking, validar:

- Ningún chunk > max_tokens + overlap
- Cada chunk tiene metadata completa
- `redactions_applied` refleja regex que dispararon
- Símbolo root presente en chunks de código