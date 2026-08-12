#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${MEET_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
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

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "required tool missing: $1"
}

# Security checks must never become successful because their search utility is
# absent. Keep the invariant scanner on POSIX tools available on every runner.
require_tool grep
require_tool find

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
if grep -REn --include='*.kt' 'DtcRecord[[:space:]]*\(' "$PRODUCTION_KOTLIN" \
  | grep -Ev '/core/obd/DtcScanEngine\.kt:' >/dev/null; then
  fail "production code bypasses DiagnosticFindingFactory"
fi
! grep -REn -i --include='*.kt' \
  'STUB FILE|No-op stub|vanguard-stub-|TODO:[[:space:]]*Replace stubs' \
  "$PRODUCTION_KOTLIN" >/dev/null || \
  fail "production Kotlin contains a forbidden stub/no-op success marker"
! grep -En '\?:[[:space:]]*0[fFdDlL]?' \
  "$PRODUCTION_KOTLIN/com/elysium369/meet/ui/screens/DtcRepairGuideScreen.kt" >/dev/null || \
  fail "Repair Guide restores null-as-zero physical inference"

# Active service bytes in production UI/session code require a reviewed
# capability-pack boundary. These signatures intentionally reject common
# hard-coded UDS RoutineControl and InputOutputControl payloads.
if grep -REn --include='*.kt' \
  'sendRawCommand\([[:space:]]*"(2F|31)[0-9A-Fa-f]{2,}"' \
  "$PRODUCTION_KOTLIN/com/elysium369/meet/ui" \
  "$PRODUCTION_KOTLIN/com/elysium369/meet/core/obd" \
  | grep -Ev '/DiagnosticRawCommandPolicy\.kt:|/ActiveDiagnosticSafety\.kt:' >/dev/null; then
  fail "hard-coded active UDS command bypasses capability authorization"
fi

echo "production-guard: OK"
