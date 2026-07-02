# Loop H — Post-Merge Learning

## Frecuencia

- Después de cada merge

## Objetivo

Convertir el PR en conocimiento reutilizable.

## Workflow

1. Identificar PR, commits y archivos tocados
2. Resumir el problema resuelto
3. Extraer decisión técnica
4. Extraer trade-offs
5. Registrar tests agregados
6. Registrar riesgos reducidos
7. Registrar riesgos restantes
8. Actualizar archivos de memoria y RAG
9. Crear rama `mavis/rag/post-merge-learning`
10. Commit `chore(mavis): record post-merge learning`
11. Abrir PR
12. **No hacer merge**

## Archivos a actualizar

- `.mavis/memory/project-memory.md`
- `.mavis/memory/decision-log.md`
- `.mavis/memory/lessons-learned.md`
- `.mavis/memory/known-risks.md`
- `.mavis/rag/last-refresh.md`
- ADR si hubo decisión arquitectónica → `.mavis/adr/`

## Output — Learning Entry

```markdown
## Learning Entry

- Date:
- PR:
- Area:
- Problem:
- Decision:
- Trade-off:
- Files affected:
- Tests added:
- Risk reduced:
- Revisit when:
```

## Comando reusable

```
Ejecuta Post-Merge Learning Loop para el último PR mergeado.

Debes:
1. Identificar PR, commits y archivos tocados.
2. Resumir el problema resuelto.
3. Extraer decisión técnica.
4. Extraer trade-offs.
5. Registrar tests agregados.
6. Registrar riesgos reducidos.
7. Registrar riesgos restantes.
8. Actualizar:
   - .mavis/memory/project-memory.md
   - .mavis/memory/decision-log.md
   - .mavis/memory/lessons-learned.md
   - .mavis/memory/known-risks.md
   - .mavis/rag/last-refresh.md
9. Crear rama mavis/rag/post-merge-learning.
10. Commit chore(mavis): record post-merge learning.
11. Abrir PR.
12. No hacer merge.
```