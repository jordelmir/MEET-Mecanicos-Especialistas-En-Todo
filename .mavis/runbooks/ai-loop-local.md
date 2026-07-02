# Runbook — AI Loop Local

> Cómo correr un Mavis loop en la Mac local.

## Prereqs

- `gh auth login` resuelto
- Working tree limpio o cambios stasheados
- Rama `feature/...` creada para el cambio

## Steps

### 1. Elegir loop

```bash
./scripts/mavis-loop.sh continuous         # Loop A
./scripts/mavis-loop.sh rag-refresh       # Loop B
./scripts/mavis-loop.sh test-gap          # Loop C
./scripts/mavis-loop.sh security          # Loop D
./scripts/mavis-loop.sh performance       # Loop E
./scripts/mavis-loop.sh dependency        # Loop F
./scripts/mavis-loop.sh docs-sync         # Loop G
./scripts/mavis-loop.sh post-merge        # Loop H
```

### 2. Inspeccionar reporte

El script escribe `.mavis/reports/latest-loop-report.md`.

Leerlo completo antes de actuar.

### 3. Seleccionar cambio

El reporte incluye una sección "Selected change" con:

- Por qué este cambio
- Archivos a tocar
- Tests a agregar/actualizar
- Riesgos restantes

### 4. Crear rama

```bash
git checkout -b mavis/<type>/<short-slug>
```

Naming:

- `mavis/security/...`
- `mavis/tests/...`
- `mavis/perf/...`
- `mavis/docs/...`
- `mavis/rag/...`
- `mavis/refactor/...`

### 5. Implementar patch mínimo

- Solo lo necesario para el cambio seleccionado
- No expandir scope
- Si encuentras otro problema → anotarlo como issue, no arreglarlo aquí

### 6. Tests

```bash
# Android
cd android && ./gradlew test

# Web (cuando estén configurados)
pnpm test
pnpm typecheck
pnpm lint

# Python ingest
cd meet-elite-ingest && pytest

# Security
gitleaks detect --source .
semgrep scan . || true
```

### 7. Commit

```bash
git add <files>
git commit -m "<type>(<scope>): <summary>"
```

Conventional Commits. Mensaje en inglés, imperativo, lowercase.

### 8. Push y PR

```bash
git push -u origin mavis/<type>/<short-slug>

gh pr create \
  --title "<type>(<scope>): <summary>" \
  --body-file .mavis/reports/latest-loop-report.md \
  --base main \
  --head mavis/<type>/<short-slug>
```

### 9. NO hacer merge

Esperar review humano y checks de CI.

### 10. Si fue un fix crítico → documentar

Actualizar `.mavis/memory/lessons-learned.md` y `.mavis/memory/known-risks.md`.

## Rollback

Si el PR mergeado rompe algo:

```bash
# Revertir merge
git revert -m 1 <merge-commit-sha>
git push origin main  # solo si es revert de merge crítico (NO usar normalmente)

# O crear rama de fix
git checkout -b mavis/fix/revert-<id>
```

## Anti-patrones

- ❌ `git push origin main` directo
- ❌ `gh pr merge --auto`
- ❌ Tocar varios archivos no relacionados en un solo PR
- ❌ Tests skipped o `.only`
- ❌ Commit con secrets
- ❌ Cambiar `.gitignore` para forzar commit de algo sensible

## Señales de que algo va mal

- Quality gate falla en CI
- Security scan encuentra high/critical
- Tests existentes se rompen
- Build size sube > 10% sin justificación
- El reporte tiene "Remaining risks" sin mitigación

Si cualquiera de estas aparece → detener loop y notificar a Jor.