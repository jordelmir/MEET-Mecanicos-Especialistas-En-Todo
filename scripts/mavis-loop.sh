#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-continuous}"
SLUG="${2:-manual}"

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

mkdir -p .mavis/reports .mavis/memory .mavis/rag

REPORT=".mavis/reports/latest-loop-report.md"

CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || echo unknown)"
CURRENT_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DATE_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

{
  echo "# Mavis Loop Report"
  echo
  echo "## Mode"
  echo
  echo "$MODE"
  echo
  echo "## Slug"
  echo
  echo "$SLUG"
  echo
  echo "## Date UTC"
  echo
  echo "$DATE_UTC"
  echo
  echo "## Branch"
  echo
  echo "$CURRENT_BRANCH"
  echo
  echo "## Commit"
  echo
  echo "$CURRENT_COMMIT"
  echo
  echo "## Git Status"
  echo
  echo '```txt'
  git status --short || true
  echo '```'
  echo
  echo "## Risk Signals"
  echo
  echo '```txt'
  grep -RInE "TODO|FIXME|HACK|XXX|unsafe|unwrap\(|expect\(|panic!\(|catch \(Exception|GlobalScope|!!|SELECT \*|service_role|secret|password|token|api_key" . \
    --exclude-dir=.git \
    --exclude-dir=node_modules \
    --exclude-dir=build \
    --exclude-dir=.gradle \
    --exclude-dir=dist \
    --exclude-dir=coverage \
    --exclude=".env" \
    --exclude=".env.*" \
    2>/dev/null || true
  echo '```'
  echo
  echo "## Security Exclusions"
  echo
  echo "- .env and .env.* are excluded."
  echo "- Keystores, signing keys, production dumps, raw VIN/GPS logs and PII must not be indexed."
  echo
  echo "## Next Test Key"
  echo
  echo "Validate that git diff only contains expected infrastructure files."
} > "$REPORT"

echo "Mavis loop report written to $REPORT"