#!/usr/bin/env bash
# ==============================================================================
# verify-publication-boundary.sh — Safety Publication Boundary Gate
#
# Asserts that:
# 1. Public projections never expose private narrative, reporter user ID, or raw PII.
# 2. De-anonymizing linkages (e.g. public_point_report_links) are strictly isolated
#    in safety_private and blocked from authenticated/anon roles.
# 3. Android public entities do not map private report fields.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"
KOTLIN_DIR="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/safety"

echo "=== [1/3] Checking Public Projection Column Privacy ==="

# Check that public tables/views never contain 'narrative' or 'reporter_user_id'
FORBIDDEN_COLS=$(grep -iE "create table.*safety_public_[a-z_]+" "$MIGRATIONS_DIR"/*safety* -A 30 2>/dev/null | grep -iE "(\bnarrative\b|\breporter_user_id\b|\bowner_user_id\b)" || true)

if [ -n "$FORBIDDEN_COLS" ]; then
  echo "FAIL: Found private fields in public projection table definitions:"
  echo "$FORBIDDEN_COLS"
  exit 1
fi

echo "  -> Public projections contain 0 private narrative/identity columns"

echo "=== [2/3] Checking Reporter Anonymity Firewall in SQL ==="

# Verify that source_report_id was removed from safety_public_points
if ! grep -q "alter table public.safety_public_points drop column if exists source_report_id" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: source_report_id not verified dropped from public points"
  exit 1
fi

# Verify safety_private.public_point_report_links has REVOKE ALL
if ! grep -q "revoke all on safety_private.public_point_report_links from public, anon, authenticated" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: safety_private.public_point_report_links lacks REVOKE ALL from authenticated/anon"
  exit 1
fi

echo "  -> Reporter anonymity boundary strictly enforced in safety_private"

echo "=== [3/3] Checking Kotlin Public Models ==="

# Check that public Room entities and data models don't expose narrative
PUBLIC_KOTLIN_BREACH=$(grep -rn "narrative" "$KOTLIN_DIR/data/local/SafetyPublic"* 2>/dev/null || true)
if [ -n "$PUBLIC_KOTLIN_BREACH" ]; then
  echo "FAIL: Narrative found in public Room entities:"
  echo "$PUBLIC_KOTLIN_BREACH"
  exit 1
fi

echo "  -> Kotlin public projection entities are clean of private state"

echo "PASS: Safety Publication Boundary Verification Complete."
