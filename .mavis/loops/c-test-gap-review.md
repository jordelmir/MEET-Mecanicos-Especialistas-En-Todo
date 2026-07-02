# Loop C — Test Gap Review

## Frecuencia

- Cada PR
- Antes de release
- Después de bugfix

## Objetivo

Encontrar lógica crítica sin tests.

## Búsqueda sistemática

- Use cases sin unit tests
- Endpoints sin integration tests
- RLS sin tests por rol
- DTC / OBD parsers sin property tests
- Ledger / transacciones sin race tests
- Compose screens sin state tests
- Frontend flows sin Playwright
- Migrations sin rollback test

## Output — Test Gap Report

```markdown
## Test Gap Report

- Área:
- Riesgo:
- Test faltante:
- Archivo sugerido:
- Fixture necesario:
- Prioridad:
```

## Criterio de prioridad (severidad)

1. Dinero / ledger
2. Auth / RLS
3. OBD / telemetría
4. DB migrations
5. Parsers
6. Offline sync
7. UI state machines

## Regla

- Solo un test faltante de alto impacto por loop
- El test debe fallar antes de existir (red → green real)
- El test debe ser ejecutable en CI

## Comando reusable

```
Ejecuta Test Gap Review Loop.

Debes:
1. Detectar lógica crítica sin tests.
2. Priorizar por riesgo:
   - dinero/ledger
   - auth/RLS
   - OBD/telemetría
   - DB migrations
   - parsers
   - offline sync
   - UI state machines
3. Escoger un solo test faltante de alto impacto.
4. Implementarlo.
5. Ejecutarlo.
6. Crear commit test(<scope>): add <risk> regression coverage.
7. Actualizar .mavis/memory/lessons-learned.md.
8. Abrir PR.
9. No hacer merge.
```