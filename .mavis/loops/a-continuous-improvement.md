# Loop A — Continuous Improvement

## Frecuencia

- Manual bajo demanda
- Diario si el repo está activo
- Antes de cada release

## Objetivo

Detectar el cambio pequeño de mayor impacto técnico.

## Workflow

1. Inspect repo
2. Detect risks
3. Rank opportunities
4. Select exactly **one** improvement
5. Create branch `mavis/<type>/<short-slug>`
6. Implement minimal patch
7. Add / update tests
8. Run quality gates
9. Update docs / memory
10. Commit
11. Push branch
12. Open PR
13. Record loop report en `.mavis/reports/latest-loop-report.md`

> **Nunca** implementar más de un cambio de alto impacto por loop salvo instrucción explícita.

## Criterio de selección

Orden de preferencia:

1. Reduce riesgo de seguridad real
2. Reduce riesgo de pérdida de datos
3. Sube cobertura de tests en lógica crítica (auth, RLS, ledger, parsers)
4. Reduce regresiones visibles
5. Reduce confusión operativa (docs, runbooks)
6. Mejora performance medible
7. Reduce deuda técnica con payoff claro

## Output obligatorio

Reporte con todas las secciones del template (ver `.mavis/AGENT.md` §12).

## Anti-patrones

- No hacer refactor cosmético sin evidencia
- No cambiar stack sin ADR
- No tocar tests para hacerlos pasar artificialmente
- No expandir scope "ya que estamos aquí"

## Comando reusable

```
Ejecuta el Continuous Improvement Loop del repo actual.

Reglas:
1. No hagas push directo a main.
2. Inspecciona el repo primero.
3. Detecta los 10 riesgos técnicos más importantes.
4. Escoge solo 1 cambio pequeño de alto impacto.
5. Crea rama mavis/<type>/<short-slug>.
6. Implementa el patch mínimo.
7. Agrega o actualiza test.
8. Ejecuta quality gates del stack.
9. Actualiza .mavis/memory y .mavis/rag/last-refresh.md si el cambio afecta arquitectura, DB, API, seguridad o comportamiento.
10. Haz commit con Conventional Commit.
11. Prepara PR con .mavis/reports/latest-loop-report.md.
12. No hagas merge.
13. Si no puedes verificar algo, dilo explícitamente.
14. Termina con riesgos restantes y siguiente test clave.
```