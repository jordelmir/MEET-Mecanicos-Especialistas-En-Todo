# Known Risks — MEET

> Riesgos identificados, con severidad, evidencia, impacto, mitigación y estado.
> Actualizar via Loop A (selección) y Loop D (security).

## Template

```markdown
## Risk ID

- **Severity**: Critical / High / Medium / Low
- **Area**: android / supabase / web / python / ci / security
- **Evidence**: (link a código, log, screenshot)
- **Impact**: (qué pasa si explota)
- **Mitigation**: (qué hacemos para reducirlo)
- **Owner**: (quién es responsable)
- **Status**: open / mitigated / accepted / fixed
```

---

## RISK-001 — WIP sin commitear

- **Severity**: Medium
- **Area**: repo hygiene
- **Evidence**: `git stash list` → `stash@{0}: On main: wip-pre-mavis-bootstrap-2026-07-02`
- **Impact**: Si el stash se pierde o queda huérfano, 42 archivos modificados + 47 untracked desaparecen del working tree
- **Mitigation**: Stash nombrado con fecha y motivo. Documentado en `.mavis/reports/latest-loop-report.md`. PR de bootstrap debe mergear primero, luego `git stash pop` controlado.
- **Owner**: Jor
- **Status**: open

---

## RISK-002 — gh CLI no autenticado

- **Severity**: High (bloquea push/PR)
- **Area**: ci / dev tooling
- **Evidence**: `gh auth status` → "You are not logged into any GitHub hosts"
- **Impact**: No se pueden abrir PRs, crear issues, ni hacer push a remote
- **Mitigation**: Usuario debe ejecutar `gh auth login` y elegir GitHub.com → HTTPS → login con navegador
- **Owner**: Jor
- **Status**: open

---

## RISK-003 — Quality gate Node incompleto

- **Severity**: Medium
- **Area**: web (React/TS)
- **Evidence**: `package.json` solo define `dev`, `build`, `preview` — no hay `test`, `lint`, `typecheck`
- **Impact**: CI no puede ejecutar checks de Node/TypeScript. Bugs no detectados en PR.
- **Mitigation**: Agregar scripts `test`, `lint`, `typecheck` con configuración (vitest, eslint, tsc)
- **Owner**: Jor
- **Status**: open

---

## RISK-004 — Posibles secrets en .env no en .gitignore

- **Severity**: High
- **Area**: security
- **Evidence**: Pendiente de auditar con `gitleaks` (no ejecutado en bootstrap)
- **Impact**: Filtración de keys de Supabase, Stripe, Vercel, Google Play
- **Mitigation**: Loop D — Security Review — ejecutar gitleaks sobre el repo
- **Owner**: Mavis (Loop D)
- **Status**: open

---

## RISK-005 — VIN / GPS en logs de Android

- **Severity**: High (PII automotriz)
- **Area**: android / privacy
- **Evidence**: Pendiente de auditar en `core/obd/`, `core/video/HudProjectionService.kt`, telemetry
- **Impact**: Crash telemetry y logs podrían filtrar VIN o GPS a servicios externos
- **Mitigation**: Test de no-leak (`shouldNotLogVinOrGpsInCrashTelemetry`) — propuesto en Loop C
- **Owner**: Mavis (Loop C propuesto)
- **Status**: open

---

## RISK-006 — sin CI / sin workflows

- **Severity**: High
- **Area**: ci
- **Evidence**: `.github/workflows/` no existía antes del bootstrap
- **Impact**: PRs sin quality gate, sin security scan, sin RAG freshness check
- **Mitigation**: Bootstrap crea 3 workflows (`mavis-quality-gate.yml`, `mavis-scheduled-audit.yml`, `mavis-rag-refresh.yml`)
- **Owner**: Mavis (este PR)
- **Status**: mitigated in this PR

---

## RISK-007 — auto-merge o push directo a main

- **Severity**: Critical (regla de oro)
- **Area**: process
- **Evidence**: Política explícita en `.mavis/AGENT.md`
- **Impact**: Pérdida de trazabilidad, riesgo de merge de código no revisado
- **Mitigation**: Scripts de Mavis no invocan `gh pr merge`. Workflows requieren checks. Documentado en runbooks.
- **Owner**: Mavis (enforcement) + Jor (revoke si pasa)
- **Status**: mitigated

---

## RISK-008 — Outbox / eventos sin test de idempotencia

- **Severity**: Medium
- **Area**: supabase / commerce
- **Evidence**: `supabase/functions/sync-vanguard-outbox/` + `vanguard_commerce_events_ledger` migration. Sin test de doble-submit visible.
- **Impact**: Reintentos podrían duplicar eventos commerce
- **Mitigation**: Test `shouldNotDuplicateLedgerEntryOnRetry` — candidato Loop C prioritario
- **Owner**: Mavis (Loop C futuro)
- **Status**: open