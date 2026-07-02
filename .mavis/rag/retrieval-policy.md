# RAG Retrieval Policy — MEET

Cómo Mavis consulta el RAG antes de responder o modificar código.

## Capas de retrieval (orden)

Antes de responder o modificar código, recuperar contexto por capas:

1. **Archivos directamente mencionados** (path explícito en la pregunta o task)
2. **Tests relacionados** (mismo archivo, mismo símbolo, mismo módulo)
3. **Interfaces / contratos** (types, interfaces, schemas)
4. **Migraciones / DB** (si la pregunta toca DB, buscar migraciones relevantes)
5. **ADRs** (`.mavis/adr/`)
6. **Project memory** (`.mavis/memory/project-memory.md`)
7. **Previous loop reports** (`.mavis/reports/`)
8. **External docs** (solo si el contexto interno es insuficiente)

## Regla de oro

**No uses RAG para saltarte lectura de archivos críticos.** RAG apunta; el agente verifica.

Si RAG sugiere algo, abrir el archivo real y confirmar antes de actuar.

## Query construction

Para cada query, construir:

```yaml
query:
  natural_language: <la pregunta del usuario>
  filters:
    language: [kotlin, typescript, python, sql, markdown]
    path_prefix: [android/, supabase/, components/, .mavis/]
    modified_after: <utc timestamp>
    symbols: [<nombres si los hay>]
  top_k: 8
  min_score: 0.7
```

## Anti-patrones de retrieval

- ❌ Tomar el primer chunk sin leer
- ❌ Confiar en metadata vieja sin verificar fecha
- ❌ Ignorar chunks de test que contradicen chunks de implementación
- ❌ Asumir que "no encontrado en RAG" significa "no existe"
- ❌ Devolver secretos del RAG en respuestas (siempre re-check antes de emitir)

## Cuando RAG contradice código

| Caso | Acción |
|------|--------|
| RAG dice A, código dice B | **Gana código**. RAG está stale. Disparar Loop B. |
| RAG dice A, código no tiene A | RAG alucinó. Marcar como `removed_stale_fact` en `last-refresh.md`. |
| Código no tiene nada | Si RAG menciona, verificar manualmente — puede ser código nuevo no ingestado aún. |
| Tests pasan pero RAG dice que no deberían | Revisar tests, luego RAG. Uno de los dos está mal. |

## Cold-start behavior

Si RAG index está vacío o muy stale:

1. Mavis lee archivos directamente con `Read`
2. Prioriza archivos tocados en el último commit
3. Documenta en `.mavis/rag/last-refresh.md` que el retrieval fue fallback
4. Dispara Loop B al final si aplica

## Embedding model selection (futuro)

Cuando se monte RAG externo, opciones:

- `text-embedding-3-small` (OpenAI, 1536d, $0.02/1M tokens)
- `voyage-code-3` (mejor para código)
- `nomic-embed-text-v1.5` (open source, 768d)

**Pero NO commitear embeddings a Git.** Solo metadata.

## Vector store selection (futuro)

- Qdrant (self-hosted, simple)
- pgvector (dentro del propio Supabase — candidato natural)
- chroma (liviano para local)

Cualquiera de los tres es válido. La decisión se documenta en ADR cuando se implemente.

## Retrieval evaluation

Por cada loop B, sample 5 queries típicas y validar manualmente:

- Top-3 resultados relevantes?
- Metadata correcta?
- Redactions respetadas?
- Stale facts identificadas?

Log en `last-refresh.md`.