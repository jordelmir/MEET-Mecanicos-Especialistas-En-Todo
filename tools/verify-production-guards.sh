#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_GRADLE="$ROOT_DIR/android/app/build.gradle.kts"
AUTH_SCREEN="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AuthScreen.kt"
AI_CLIENT="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/core/ai/GeminiDiagnostic.kt"
LEGACY_SCHEMA="$ROOT_DIR/supabase_schema.sql"

fail() {
  echo "production-guard: $1" >&2
  exit 1
}

grep -q 'orElse("false")' "$APP_GRADLE" || fail "ride verification must default to false"
! grep -q 'androiddebugkey' "$APP_GRADLE" || fail "release signing references the debug alias"
! grep -q 'signingConfigs.getByName("debug")' "$APP_GRADLE" || fail "release signing falls back to debug"
grep -q 'signInWith(Email)' "$AUTH_SCREEN" || fail "email login is not connected to Supabase Auth"
grep -q 'signUpWith(Email)' "$AUTH_SCREEN" || fail "email registration is not connected to Supabase Auth"
! grep -q 'onClick = { loading = true; onAuthSuccess() }' "$AUTH_SCREEN" || fail "authentication bypass restored"
! grep -q '?: "ESTADO NOMINAL"' "$AI_CLIENT" || fail "AI null response is still reported as nominal"
grep -q 'blocked legacy snapshot' "$LEGACY_SCHEMA" || fail "legacy schema is deployable"

echo "production-guard: OK"
