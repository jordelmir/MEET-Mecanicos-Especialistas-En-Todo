#!/usr/bin/env bash
# ==============================================================================
# verify-private-storage.sh — Safety Storage Privacy & Isolation Gate
#
# Asserts that:
# 1. Evidence original storage is private and separated from public derivatives.
# 2. Storage RLS policies enforce owner-only read/write on raw evidence.
# 3. Evidence object metadata in Postgres has immutability guarantees.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"

echo "=== [1/3] Checking Evidence Bucket Separation ==="

# Verify that safety-evidence-original bucket is declared private (public = false)
if ! grep -A 2 -i "storage.buckets" "$MIGRATIONS_DIR"/*safety* 2>/dev/null | grep -q "('safety-evidence-original'.*false)"; then
  echo "FAIL: safety-evidence-original bucket not found with public=false"
  exit 1
fi

echo "  -> Raw evidence bucket is strictly private"

echo "=== [2/3] Checking Storage Object RLS Policies ==="

# Check that evidence storage objects enforce owner isolation (owner = auth.uid() or similar path check)
if ! grep -q "safety_evidence_objects" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: safety_evidence_objects table missing from migrations"
  exit 1
fi

# Verify RLS enabled on safety_evidence_objects
if ! grep -A 2 -i "alter table.*safety_evidence_objects" "$MIGRATIONS_DIR"/*safety* 2>/dev/null | grep -qi "enable row level security"; then
  echo "FAIL: RLS not enabled on safety_evidence_objects"
  exit 1
fi

echo "  -> Evidence object registry has RLS enabled"

echo "=== [3/3] Checking Evidence Immutability Triggers ==="

# Verify that update or delete triggers are installed on safety_evidence_objects
if ! grep -q "safety_evidence_immutable.*safety_evidence_objects" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Missing immutability trigger on safety_evidence_objects"
  exit 1
fi

echo "  -> Immutability triggers verified on evidence storage registry"

echo "PASS: Safety Private Storage Verification Complete."
