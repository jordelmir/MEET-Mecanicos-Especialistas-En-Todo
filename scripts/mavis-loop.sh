#!/usr/bin/env bash
# Mavis Local Loop Runner
# Ejecuta un loop de Mavis, escribe reporte y (opcionalmente) prepara PR.
# NO hace merge. NO hace push directo a main.
#
# Uso:
#   ./scripts/mavis-loop.sh continuous [branch-slug]
#   ./scripts/mavis-loop.sh rag-refresh
#   ./scripts/mavis-loop.sh test-gap [branch-slug]
#   ./scripts/mavis-loop.sh security
#   ./scripts/mavis-loop.sh performance
#   ./scripts/mavis-loop.sh dependency
#   ./scripts/mavis-loop.sh docs-sync
#   ./scripts/mavis-loop.sh post-merge
#   ./scripts/mavis-loop.sh help

set -euo pipefail

MODE="${1:-continuous}"
BRANCH_SLUG="${2:-auto-improvement}"
LOOP_TYPE="${3:-chore}"

# --- Pre-flight checks ---

echo "== Mavis Loop =="
echo "Mode: $MODE"
echo

# Verificar que estamos en un repo git
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: no estamos dentro de un repo git."
  exit 1
fi

# Working tree status
echo "## Working tree status"
git status --short || true
echo

CURRENT_BRANCH="$(git branch --show-current)"
echo "Current branch: $CURRENT_BRANCH"

# Regla de oro: no operar directo en main
if [ "$CURRENT_BRANCH" = "main" ]; then
  echo
  echo "WARNING: estás en main. Mavis NO debe operar directo en main."
  echo "Crea una rama primero:"
  echo "  git checkout -b mavis/<type>/<short-slug>"
  echo
  read -r -p "¿Continuar de todos modos? (s/N) " CONTINUE
  if [[ ! "$CONTINUE" =~ ^[sS]$ ]]; then
    echo "Abortado."
    exit 1
  fi
fi

# --- Preparar directorios ---

mkdir -p .mavis/reports .mavis/memory .mavis/rag .mavis/adr

REPORT=".mavis/reports/latest-loop-report.md"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
COMMIT="$(git rev-parse HEAD)"

# --- Generar reporte base ---

{
  echo "# Mavis Loop Report"
  echo
  echo "- **Mode**: $MODE"
  echo "- **Date**: $TIMESTAMP"
  echo "- **Base branch**: $CURRENT_BRANCH"
  echo "- **Commit**: $COMMIT"
  echo "- **Branch slug**: $BRANCH_SLUG"
  echo
  echo "## Repo status"
  echo
  echo '```'
  git status --short || echo "(clean)"
  echo '```'
  echo
  echo "## Risk signals"
  echo
  echo '```'
  grep -RInE "TODO|FIXME|HACK|XXX|unwrap\(|expect\(|panic!\(|catch \(Exception|GlobalScope|!!|any|SELECT \*|service_role|secret|password|token|api_key|sk_|pk_" . \
    --exclude-dir=.git \
    --exclude-dir=node_modules \
    --exclude-dir=build \
    --exclude-dir=.gradle \
    --exclude-dir=.vercel \
    --exclude-dir=dist \
    --exclude-dir=.mavis \
    --exclude-dir=docs \
    --exclude="*.md" \
    --exclude="*.example" \
    || echo "(no signals detected)"
  echo '```'
  echo
  echo "## Loop-specific notes"
  echo
  case "$MODE" in
    continuous)
      echo "Loop A — Continuous Improvement."
      echo "Detecta 10 riesgos y propone 1 cambio pequeño de alto impacto."
      ;;
    rag-refresh)
      echo "Loop B — RAG Refresh."
      echo "Sincroniza .mavis/memory y .mavis/rag con código actual."
      ;;
    test-gap)
      echo "Loop C — Test Gap Review."
      echo "Detecta lógica crítica sin tests, agrega uno de alto impacto."
      ;;
    security)
      echo "Loop D — Security Review."
      echo "Ejecuta gitleaks/semgrep/trivy/osv. Corrige el hallazgo de mayor impacto."
      ;;
    performance)
      echo "Loop E — Performance Review."
      echo "Mide baseline, propone fix, mide después."
      ;;
    dependency)
      echo "Loop F — Dependency Review."
      echo "Audita licencias, vulnerabilidades, alternativas."
      ;;
    docs-sync)
      echo "Loop G — Docs Sync."
      echo "Detecta drift entre código y docs."
      ;;
    post-merge)
      echo "Loop H — Post-Merge Learning."
      echo "Convierte PR mergeado en memoria versionada."
      ;;
    help)
      echo "Modos: continuous | rag-refresh | test-gap | security | performance | dependency | docs-sync | post-merge"
      ;;
    *)
      echo "Modo desconocido: $MODE"
      echo "Modos: continuous | rag-refresh | test-gap | security | performance | dependency | docs-sync | post-merge"
      exit 1
      ;;
  esac
  echo
  echo "## Selected change"
  echo
  echo "(A llenar por Mavis o humano — un solo cambio pequeño)"
  echo
  echo "## Why this change"
  echo
  echo "(Justificación con evidencia)"
  echo
  echo "## Files changed"
  echo
  echo "(Lista al finalizar)"
  echo
  echo "## Tests executed"
  echo
  echo "(Comandos + resultado)"
  echo
  echo "## Tests not executed"
  echo
  echo "(Si los hubo, justificar)"
  echo
  echo "## RAG / memory updates"
  echo
  echo "(Qué archivos de .mavis/ se actualizaron)"
  echo
  echo "## Remaining risks"
  echo
  echo "(Riesgos no resueltos)"
  echo
  echo "## Rollback"
  echo
  echo "(Cómo revertir)"
  echo
  echo "## Next test clave"
  echo
  echo "(Próximo test sugerido)"
  echo
  echo "## Commit"
  echo
  echo "(Hash después de commit)"
  echo
  echo "## PR"
  echo
  echo "(URL después de gh pr create)"
} > "$REPORT"

echo "Report written to $REPORT"
echo
echo "Next steps:"
echo "  1. Editar $REPORT con el contenido específico del loop"
echo "  2. Implementar el cambio seleccionado"
echo "  3. Correr quality gates del stack"
echo "  4. git add <files> && git commit -m \"<type>(<scope>): <summary>\""
echo "  5. git push -u origin mavis/<type>/<short-slug>"
echo "  6. gh pr create --body-file $REPORT"
echo
echo "Regla de oro: NUNCA hacer merge, NUNCA push directo a main."