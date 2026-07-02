# Decision Log — MEET

> Decisiones arquitectónicas y técnicas con contexto, trade-offs y motivo de revisión futura.
> Cada decisión debe quedar registrada con Conventional Commit reference o PR.

## Template

```markdown
## YYYY-MM-DD — Decision title

### Context
Qué problema existía. Por qué ahora.

### Decision
Qué se eligió.

### Alternatives
Qué otras opciones se consideraron y por qué no.

### Trade-off
Qué se gana y qué se pierde.

### Consequences
Impacto técnico, operacional, de costo, de complejidad.

### Revisit when
Señales de que esta decisión debe re-evaluarse.
```

---

## 2026-07-02 — Bootstrap del Loop System (Mavis)

### Context

MEET es un proyecto industrial con múltiples stacks (Android, React/TS, Supabase, Python) y creciente superficie de cambio. No existía sistema de mejora continua versionado, trazabilidad de decisiones ni quality gates automáticos. Cada cambio de alto impacto era manual y sin memoria estructurada.

### Decision

Adoptar Mavis Continuous Engineering Loop System como contrato operativo, versionado en `.mavis/` dentro del propio repo:

- 8 loops (A–H) con frecuencias diferenciadas
- RAG industrial basado en archivos markdown + JSON de metadata (sin embeddings binarios en Git)
- 3 workflows de GitHub Actions (quality gate, scheduled audit, RAG freshness check)
- Script bash local (`scripts/mavis-loop.sh`) para correr loops en la Mac
- Política de no auto-merge y todo por PR

### Alternatives

- **Repo separado de ops** (.mavis en repo aparte): descartado — la verdad debe vivir junto al código
- **Embeddings + vector store**: descartado — añade opacidad y la verdad deja de ser auditable
- **Loops puramente manuales sin automatización**: descartado — la cadencia se pierde

### Trade-off

- ✅ Trazabilidad total en GitHub
- ✅ RAG regenerable desde código
- ✅ Cero magia (todo es archivo versionado)
- ❌ Costo inicial de escribir/actualizar memory manualmente en cada loop
- ❌ Sin búsqueda semántica embebida (mitigable con RAG externo que lea los metadata JSON)

### Consequences

- Todo loop deja artefacto en `.mavis/reports/`
- ADRs conviven con memoria técnica
- Quality gates corren en CI por PR
- Loop A inicial detectado 3 riesgos estructurales (ver known-risks.md)

### Revisit when

- Si el volumen de memory > 1000 líneas por archivo → considerar split
- Si los loops se vuelven ruidosos → ajustar frecuencias
- Si aparece necesidad real de búsqueda semántica → montar RAG externo que consuma los metadata JSON

---

## 2026-07-02 — Stash del WIP antes de bootstrap

### Context

Antes de iniciar bootstrap existían 42 archivos modificados + 47 untracked en working tree sin commit. Mezclar esos cambios con `.mavis/` en un solo PR hubiera sido ruido: tocaba código de producto y bootstrap del sistema al mismo tiempo, imposible de revisar.

### Decision

Stash separado `wip-pre-mavis-bootstrap-2026-07-02` con todos los cambios sin commitear. Bootstrap limpio en `feature/mavis-loop-bootstrap`. Los cambios del usuario se recuperan después con `git stash pop` (o `git stash apply`) cuando el PR de bootstrap esté mergeado.

### Alternatives

- **Commit directo en main** de los WIP: descartado — viola regla de oro
- **Stash por archivo**: descartado — innecesariamente complejo
- **Branch nueva con todo**: descartado — mismo problema de revisión mezclada

### Trade-off

- ✅ Bootstrap limpio y revisable
- ✅ WIP seguro en stash, recuperable
- ❌ Usuario debe recordar hacer `git stash pop` después del merge

### Consequences

- `git stash list` muestra el stash mientras esté vivo
- Riesgo de olvidar el stash → documentado en latest-loop-report.md

### Revisit when

- Después de merge de bootstrap, usuario debe decidir `pop` vs `branch` para los WIP.