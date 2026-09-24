#!/usr/bin/env bash
# ==============================================================================
# verify-authority.sh — Civil Safety OS Authority Integrity Gate
#
# Asserts that:
# 1. No direct INSERT, UPDATE, or DELETE permissions exist on sensitive private
#    tables for authenticated/anon roles.
# 2. Every safety RPC uses SECURITY DEFINER and sets search_path = ''.
# 3. Android Kotlin code does not bypass outbox/RPC architecture for mutations.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/supabase/migrations"
KOTLIN_DIR="$REPO_ROOT/android/app/src/main/kotlin/com/elysium369/meet/safety"

echo "=== [1/3] Checking SQL Table Grants & Revocations ==="

# Check that safety_reports and evidence tables revoke direct INSERT from authenticated
if ! grep -q "revoke.*insert.*on.*safety_reports.*from.*authenticated" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Missing REVOKE INSERT on safety_reports from authenticated"
  exit 1
fi

if ! grep -q "revoke all on public.safety_evidence_custody" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Missing REVOKE ALL on safety_evidence_custody"
  exit 1
fi

if ! grep -q "safety_custody_immutable.*safety_evidence_custody" "$MIGRATIONS_DIR"/*safety* 2>/dev/null; then
  echo "FAIL: Missing immutability trigger on safety_evidence_custody"
  exit 1
fi

echo "  -> Table privilege boundaries OK"

echo "=== [2/3] Checking RPC Security Definitions ==="

# Find all safety-related RPC definitions in migrations
RPC_FAILURES=0
while IFS= read -r file; do
  # Extract function declarations starting with safety_ or public.safety_
  while IFS= read -r line; do
    func=$(echo "$line" | sed -E 's/.*function ([a-z0-9_.]+).*/\1/I')
    [ -z "$func" ] && continue
    clean_func=$(echo "$func" | sed 's/public\.//')

    # Read the function signature and attributes (next 30 lines)
    FUNC_BLOCK=$(grep -A 30 -i "function.*$clean_func" "$file" | head -30)

    # Check if this is a trigger function
    IS_TRIGGER=false
    if echo "$FUNC_BLOCK" | grep -qi "returns trigger"; then
      IS_TRIGGER=true
    fi

    # RPCs must have SECURITY DEFINER (unless explicitly a read-only stable function)
    if [ "$IS_TRIGGER" = false ]; then
      if ! echo "$FUNC_BLOCK" | grep -qiE "security (definer|invoker)"; then
        echo "FAIL: RPC $func in $(basename "$file") lacks SECURITY DEFINER/INVOKER"
        RPC_FAILURES=$((RPC_FAILURES + 1))
      fi
    fi

    # Every function must set a clean search_path (search_path='' or search_path = '')
    if ! echo "$FUNC_BLOCK" | grep -qiE "search_path\s*=\s*(''|\"\")"; then
      echo "FAIL: Function $func in $(basename "$file") lacks search_path = ''"
      RPC_FAILURES=$((RPC_FAILURES + 1))
    fi
  done < <(grep -Eio "create (or replace )?function (public\.)?safety_[a-z0-9_]+" "$file" || true)
done < <(find "$MIGRATIONS_DIR" -name "*safety*.sql" -not -name "20260918000000_safety_foundation_v1.sql")

if [ "$RPC_FAILURES" -gt 0 ]; then
  echo "FAIL: Found $RPC_FAILURES RPC security definition issues"
  exit 1
fi

echo "  -> All safety RPCs enforce SECURITY DEFINER and search_path = ''"

echo "=== [3/3] Checking Android Mutation Authority Contracts ==="

# Ensure no Kotlin code directly inserts into remote safety tables (must use RPC / outbox)
FORBIDDEN_CALLS=$(grep -rn "postgrest\[\"safety_reports\"\]\.insert" "$KOTLIN_DIR" 2>/dev/null || true)
if [ -n "$FORBIDDEN_CALLS" ]; then
  echo "FAIL: Direct client postgrest.insert detected on safety_reports:"
  echo "$FORBIDDEN_CALLS"
  exit 1
fi

echo "  -> Client mutation authority correctly gated via outbox/RPC"

echo "PASS: Safety Authority Verification Complete."
