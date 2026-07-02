# Runbook — AI Loop GitHub Actions

> Cómo funcionan los workflows automáticos de Mavis.
> Ninguno de estos workflows hace merge. Todos producen artefactos o issues.

## Workflows instalados

| Archivo | Trigger | Acción |
|---------|---------|--------|
| `.github/workflows/mavis-quality-gate.yml` | PR a `main`, manual | Ejecuta tests/lint/typecheck/build según stack detectado |
| `.github/workflows/mavis-scheduled-audit.yml` | Lunes 10:00 UTC, manual | Escanea repo por señales de riesgo, crea issue si encuentra |
| `.github/workflows/mavis-rag-refresh.yml` | PR a `main`, manual | Verifica que cambios críticos vengan con docs/memory/RAG actualizados |

## Quality Gate

### Qué hace

1. Detecta stack (Node / Gradle / Rust / Supabase)
2. Ejecuta checks por stack
3. Corre security scans (gitleaks, trivy)
4. Reporta resultado en el PR

### Cuándo bloquea

Durante la **fase inicial** los security scans corren con `continue-on-error: true`.

Cuando el repo madure (después de 30 días o decisión explícita), los security scans críticos deben bloquear PR.

### Override

Jor puede mergear con checks rojos solo si:

- Justifica en el PR
- Crea issue de seguimiento
- Marca con label `accepted-risk`

## Scheduled Audit

### Qué hace

Cada lunes 10:00 UTC:

1. Escanea el repo buscando:
   - `TODO`, `FIXME`, `HACK`, `XXX`
   - `unwrap(`, `expect(`, `panic!(`, `catch (Exception`
   - `GlobalScope`, `!!`, `any` (TypeScript sin tipo)
   - `SELECT *`, `service_role`
   - `secret`, `password`, `token`, `api_key`
2. Si encuentra algo → crea issue `[Mavis] Weekly technical audit findings`
3. **No auto-commitea**. Solo crea issue.

### Cuándo correrlo manual

```bash
gh workflow run mavis-scheduled-audit.yml
```

Útil antes de release.

## RAG Freshness Check

### Qué hace

En cada PR a `main`:

1. Detecta archivos cambiados
2. Clasifica como "críticos" si tocan:
   - `supabase/`, `migrations/`
   - `src/`, `app/`, `backend/`, `frontend/`
   - `android/`
   - `Cargo.toml`, `build.gradle`, `package.json`
3. Detecta si hubo update en `.mavis/memory/`, `.mavis/rag/`, `.mavis/adr/`, `docs/`, `README.md`, `CHANGELOG.md`
4. Si cambiaron archivos críticos SIN update de memory → **falla el check**

### Cuándo se puede omitir

PRs puramente cosméticos (typo, formato) deben:

- No tocar archivos críticos
- O venir con un comment "skip-rag-check" + justificación

## Permisos

```yaml
# quality-gate
permissions:
  contents: read
  pull-requests: read
  security-events: write

# scheduled-audit
permissions:
  contents: read
  issues: write

# rag-refresh
permissions:
  contents: read
  pull-requests: write
```

## Secrets requeridos

- `GITHUB_TOKEN` (automático)
- (Futuro) `SUPABASE_ACCESS_TOKEN` si se agregan checks de DB
- (Futuro) `MAVIS_API_KEY` si se conecta a un servicio externo

## Kill switch

Para deshabilitar un workflow temporalmente:

```bash
# Borrar el archivo (vía PR)
git rm .github/workflows/mavis-scheduled-audit.yml

# O comentar el `on:` block
```

Si la decisión es permanente → ADR explicando por qué.

## Anti-patrones

- ❌ Modificar workflows para que ignoren checks reales
- ❌ Bajar la barra de security porque "molesta"
- ❌ Auto-merge desde CI sin review
- ❌ Workflows que commitean directo a main