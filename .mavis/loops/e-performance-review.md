# Loop E — Performance Review

## Frecuencia

- Antes de release
- Después de feature pesada
- Cuando hay ANR, jank, alto CPU, lentitud DB o alta memoria

## Regla de oro

**No optimices sin medir.** Si no puedes medir, no afirmaciones mejora.

## Áreas (MEET)

- Android cold start
- Compose recompositions
- BLE / OBD polling latency
- Video telemetry overhead
- DB query plans
- Backend P95 / P99
- Rust / C++ allocations
- Frontend bundle size
- CI duration

## Workflow

1. Definir métrica
2. Establecer baseline
3. Encontrar hot path
4. Implementar cambio mínimo
5. Medir después
6. Comparar contra baseline
7. Documentar trade-off

## Output — Performance Loop Report

```markdown
## Performance Loop Report

- Baseline:
- Hot path:
- Evidence:
- Proposed fix:
- Benchmark after:
- Regression risk:
```

## Anti-patrones

- "Optimización" sin benchmark
- Cambios que mueven CPU a memoria sin medir memoria
- Cambios que mejoran p50 pero rompen p99

## Comando reusable

```
Ejecuta Performance Review Loop.

Reglas:
1. No optimices sin medir.
2. Define métrica.
3. Establece baseline.
4. Encuentra hot path.
5. Implementa cambio mínimo.
6. Mide después.
7. Si no puedes medir, no afirmaciones mejora.
8. Agrega benchmark/test si aplica.
9. Documenta trade-off.
10. Crea commit y PR.
11. No hacer merge.
```