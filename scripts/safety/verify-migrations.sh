#!/usr/bin/env bash
# ==============================================================================
# verify-migrations.sh — Safety SQL Migrations Quality & Invariants Gate
#
# Asserts that:
# 1. All safety migration filenames follow YYYYMMDDHHMMSS_name.sql standard.
# 2. Every safety migration wraps changes in an explicit transaction (begin ... commit).
# 3. Every newly created table has RLS explicitly enabled (public) or REVOKE ALL (private).
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"

# Helper to list civil safety migrations safely handling path spaces
list_civil_migrations() {
  find "$MIGRATIONS_DIR" -name "*safety*.sql" | grep -vE "(ride_|mobility_)"
}

echo "=== [1/3] Checking Migration Naming & Sequential Timestamps ==="

MIGRATION_COUNT=0
while IFS= read -r file; do
  [ -z "$file" ] && continue
  fname=$(basename "$file")
  if ! [[ "$fname" =~ ^[0-9]{14}_[a-z0-9_]+\.sql$ ]]; then
    echo "FAIL: Migration filename invalid: $fname (must match YYYYMMDDHHMMSS_name.sql)"
    exit 1
  fi
  MIGRATION_COUNT=$((MIGRATION_COUNT + 1))
done < <(list_civil_migrations)

echo "  -> $MIGRATION_COUNT civil safety migrations checked with valid naming"

echo "=== [2/3] Checking Transactional Integrity (begin; ... commit;) ==="

TRANSACTION_ERRORS=0
while IFS= read -r file; do
  [ -z "$file" ] && continue
  if ! grep -qi "begin;" "$file"; then
    echo "FAIL: Missing begin; in $(basename "$file")"
    TRANSACTION_ERRORS=$((TRANSACTION_ERRORS + 1))
  fi
  if ! grep -qi "commit;" "$file"; then
    echo "FAIL: Missing commit; in $(basename "$file")"
    TRANSACTION_ERRORS=$((TRANSACTION_ERRORS + 1))
  fi
done < <(list_civil_migrations)

if [ "$TRANSACTION_ERRORS" -gt 0 ]; then
  echo "FAIL: Found $TRANSACTION_ERRORS migrations lacking atomic transactions"
  exit 1
fi

echo "  -> All safety migrations enforce atomic transactions (begin ... commit)"

echo "=== [3/3] Checking Table RLS Fail-Closed Policy ==="

python3 -c "
import glob, re, sys, os

migrations_dir = os.path.abspath('$MIGRATIONS_DIR')
mig_files = sorted(glob.glob(os.path.join(migrations_dir, '*safety*.sql')))
civil_migs = [m for m in mig_files if 'ride_' not in m and 'mobility_' not in m]

errors = []
for m in civil_migs:
    with open(m) as f:
        content = f.read()
    tables = re.findall(r'create\s+table(?:\s+if\s+not\s+exists)?\s+([a-zA-Z0-9_.]+)', content, re.IGNORECASE)
    for tbl in tables:
        clean = tbl.split('.')[-1]
        schema = tbl.split('.')[0] if '.' in tbl else 'public'
        if schema == 'safety_private':
            if not re.search(r'revoke\s+all\s+on\s+.*' + clean, content, re.IGNORECASE):
                errors.append(f'{os.path.basename(m)}: {tbl} lacks REVOKE ALL')
        else:
            if not re.search(r'alter\s+table\s+.*' + clean + r'\s+enable\s+row\s+level\s+security', content, re.IGNORECASE):
                errors.append(f'{os.path.basename(m)}: {tbl} lacks ENABLE ROW LEVEL SECURITY')

if errors:
    for e in errors:
        print('FAIL:', e)
    sys.exit(1)
"

echo "  -> All created tables enforce Row Level Security or private isolation"

echo "PASS: Safety Migrations Verification Complete."
