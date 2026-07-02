# Runbook — AI-Generated PR Review

> Cómo revisar un PR abierto por Mavis.

## Checklist de review (en orden)

### 1. Identidad del PR

- [ ] Rama sigue naming `mavis/<type>/<short-slug>` o `feature/mavis-*`
- [ ] Commit message es Conventional Commits
- [ ] PR title coincide con formato `<type>(<scope>): <summary>`
- [ ] Body incluye el template completo (Summary, Why, Changes, Verification, Risk, Rollback, Files touched, RAG/Memory updates, Next test clave)

### 2. Scope

- [ ] El cambio es pequeño y enfocado
- [ ] No toca archivos no relacionados
- [ ] Si toca > 3 archivos no críticos, justificar

### 3. Cambios

- [ ] Diff revisado línea por línea
- [ ] No hay secrets (buscar `password`, `token`, `api_key`, `BEGIN PRIVATE KEY`, `sk_`, `pk_`)
- [ ] No hay console.log innecesarios
- [ ] No hay TODOs nuevos sin issue vinculado
- [ ] No se modificaron archivos de migration sin cuidado (cambios en SQL son sensibles)

### 4. Tests

- [ ] Tests agregados o actualizados
- [ ] Tests fallan antes del fix (red → green real)
- [ ] Tests pasan localmente (`./gradlew test`, `pnpm test`, `pytest`)
- [ ] Coverage no bajó (si hay baseline)

### 5. Quality gates

- [ ] CI pasó (quality gate workflow)
- [ ] Security scan sin nuevos hallazgos high/critical
- [ ] RAG freshness check pasó (si toca archivos críticos)

### 6. Memoria / docs

- [ ] Si toca arquitectura → ADR nuevo o actualizado
- [ ] Si cambia comportamiento → README/DOCUMENTATION actualizado o issue creado
- [ ] Si es fix → entrada en `lessons-learned.md`
- [ ] Si cierra riesgo → `known-risks.md` actualizado
- [ ] Si hay decisión nueva → `decision-log.md`

### 7. Riesgos

- [ ] "Remaining risks" leídos y aceptados
- [ ] "Rollback" es ejecutable (instrucciones concretas)
- [ ] "Next test clave" es accionable

### 8. Anti-patrones de Mavis

- [ ] No hizo auto-merge
- [ ] No commiteó a main
- [ ] No inventó archivos
- [ ] No actualizó RAG sin verificar código

## Cómo aprobar

Si todo OK:

```bash
gh pr review --approve
```

Y merge:

```bash
gh pr merge --squash --delete-branch
```

> `--squash` para mantener historia limpia. `--delete-branch` para que Mavis no acumule ramas muertas.

## Cómo pedir cambios

```bash
gh pr review --request-changes --body "<comentario>"
```

O comentar inline en archivos específicos desde la web.

## Cómo cerrar sin merge

```bash
gh pr close --delete-branch
```

Justificar en `.mavis/memory/lessons-learned.md` por qué se cerró.

## Señales de PR sospechoso

- Mavis modifica `.gitignore` para forzar commit de algo → **rechazar**
- Mavis cambia archivos de seguridad sin ADR → **rechazar**
- Mavis salta el quality gate → **rechazar**
- Mavis hace "drive-by refactor" no relacionado → **rechazar**
- Mavis cambia package.json con dependencias nuevas sin justificación → **rechazar**

## Si el PR causa incidente en prod

1. Rollback inmediato (ver `ai-loop-local.md`)
2. Postmortem en `.mavis/memory/resolved-incidents.md`
3. ADR si la decisión original era incorrecta
4. Loop H (post-merge learning) forzado para extraer lección

## Métricas de review

Tracking informal:

- Tiempo de review (target: < 24h)
- % PRs aprobados en primer pase (target: > 70%)
- % PRs que requieren cambios mayores (target: < 15%)
- Frecuencia de "Remaining risks" no mitigados (target: 0)

Si las métricas se degradan → revisar si Mavis está sobre-operando o si las instrucciones necesitan ajuste.