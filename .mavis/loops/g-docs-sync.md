# Loop G — Docs Sync

## Frecuencia

- Cada PR
- Después de merge
- Antes de release

## Objetivo

Detectar cuando cambió comportamiento y los docs no lo reflejaron.

## Búsqueda sistemática

Cambios en:

- Setup
- Build
- API
- DB (schema, migraciones)
- Screens / UI
- Commands
- Security model
- Release process
- Env vars
- Architecture

Si cambió comportamiento y docs no cambiaron → crear issue o patch.

## Archivos a vigilar

```yaml
watch:
  - README.md
  - CHANGELOG.md
  - docs/
  - .mavis/memory/project-memory.md
  - .mavis/memory/architecture-map.md
  - .env.example
  - runbooks/
```

## Workflow

1. Detectar archivos modificados
2. Clasificar si es cambio de comportamiento (no solo formato)
3. Buscar docs que mencionen el componente cambiado
4. Comparar contra versión actual
5. Generar diff sugerido de docs
6. Aplicar o crear issue

## Output — Docs Sync Report

```markdown
## Docs Sync Report

### Behavior changed
### Docs outdated
### Docs to update
### Action taken (patch / issue)
```

## Regla

- Si un PR cambia comportamiento, **debe** venir con docs o issue de docs
- El RAG freshness check (workflow) detecta PRs con cambios críticos sin docs同步

## Comando reusable

```
Ejecuta Docs Sync Loop.
```