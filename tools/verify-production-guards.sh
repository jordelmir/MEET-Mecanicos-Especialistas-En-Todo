#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_GRADLE="$ROOT_DIR/android/app/build.gradle.kts"
AUTH_SCREEN="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AuthScreen.kt"
AI_CLIENT="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/core/ai/GeminiDiagnostic.kt"
LEGACY_SCHEMA="$ROOT_DIR/supabase_schema.sql"
OBD_VIEW_MODEL="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/ui/ObdViewModel.kt"
UDS_MANAGER="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/core/obd/UdsProtocolManager.kt"
COMMAND_MANAGER="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/core/obd/DiagnosticCommandManager.kt"
SERVICE_RESETS="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ServiceResetsScreen.kt"
ADAPTATION_SCREEN="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AdaptationScreen.kt"
RAW_COMMAND_POLICY="$ROOT_DIR/android/app/src/main/kotlin/com/elysium369/meet/core/obd/DiagnosticRawCommandPolicy.kt"
PRODUCTION_KOTLIN="$ROOT_DIR/android/app/src/main/kotlin"

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
! grep -q 'udsProtocolManager.readDtcByStatusMask' "$OBD_VIEW_MODEL" || fail "UI bypasses canonical DTC acquisition"
! grep -q 'obdSession.scanNetworkTopology' "$OBD_VIEW_MODEL" || fail "UI bypasses canonical topology acquisition"
! grep -Eq 'sendRawCommand\("?\$\{?SID_(INPUT_OUTPUT_CONTROL|ROUTINE_CONTROL)' "$UDS_MANAGER" || \
  fail "generic UDS active service can bypass the safety kernel"
grep -q 'fun getCommandsByCategory' "$COMMAND_MANAGER" || \
  fail "active-command compatibility boundary missing"
grep -q 'List<ObdCommandDef> = emptyList()' "$COMMAND_MANAGER" || \
  fail "hard-coded active-command catalog restored"
! grep -Eq 'RX: (50|51|67|6E|6F|71)' "$SERVICE_RESETS" || \
  fail "service-reset UI contains fabricated ECU acknowledgements"
! grep -q 'sendRawCommand("3101000D")' "$OBD_VIEW_MODEL" || \
  fail "generic TPMS routine bypass restored"
! grep -q 'viewModel.sendRawCommand' "$ADAPTATION_SCREEN" || \
  fail "adaptation UI bypasses capability packs"
! grep -q 'commandSequence = listOf' "$ADAPTATION_SCREEN" || \
  fail "hard-coded adaptation command catalog restored"
grep -q 'DiagnosticRawCommandPolicy.evaluate' "$OBD_VIEW_MODEL" || \
  fail "expert terminal bypasses raw-command policy"
grep -q 'readOnlyServices' "$RAW_COMMAND_POLICY" || \
  fail "read-only terminal allowlist missing"
[[ ! -e "$PRODUCTION_KOTLIN/com/elysium369/meet/core/vanguard/VanguardCoreStubs.kt" ]] || \
  fail "Vanguard production stub file restored"
if rg -n --glob '*.kt' 'DtcRecord[[:space:]]*\(' "$PRODUCTION_KOTLIN" \
  | rg -v '/core/obd/DtcScanEngine\.kt:' >/dev/null; then
  fail "production code bypasses DiagnosticFindingFactory"
fi
! rg -n -i --glob '*.kt' \
  'STUB FILE|No-op stub|vanguard-stub-|TODO:[[:space:]]*Replace stubs' \
  "$PRODUCTION_KOTLIN" >/dev/null || \
  fail "production Kotlin contains a forbidden stub/no-op success marker"
! rg -n '\?:[[:space:]]*0[fFdDlL]?' \
  "$PRODUCTION_KOTLIN/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt" >/dev/null || \
  fail "Repair Guide restores null-as-zero physical inference"

echo "production-guard: OK"
