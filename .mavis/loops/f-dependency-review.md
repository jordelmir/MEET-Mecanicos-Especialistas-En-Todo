# Loop F — Dependency Review

## Frecuencia

- Semanal
- Antes de release
- Cuando se agrega una dependencia

## Criterios de revisión

- Licencia (compatible con el proyecto)
- Mantenedor (activo vs abandonado)
- Última actividad (release reciente)
- Vulnerabilidades conocidas
- Tamaño (bundle / install footprint)
- Alternativas más ligeras
- Necesidad real (¿se puede resolver con código simple?)
- Riesgo supply chain

## Regla

**No agregar dependencias por comodidad** si la lógica puede resolverse con código simple, seguro y mantenible.

## Workflow

1. Listar deps (`pnpm list`, `gradle dependencies`, `pip list`)
2. Revisar changelog upstream
3. Cross-check con `osv-scanner`
4. Marcar candidate-for-removal
5. Proponer replacements si existen
6. Decidir: keep / upgrade / remove / replace

## Output — Dependency Report

```markdown
## Dependency Report

### Added
### Upgraded
### Removed
### Replaced
### Flagged (license / vuln / abandoned)
### Candidates for future removal
```

## Output por paquete

- Nombre
- Versión actual
- Versión latest
- Licencia
- Vulnerabilidades
- Última release upstream
- Uso real en código (qué archivo lo importa)
- Decisión

## Comando reusable

```
Ejecuta Dependency Review Loop.
```