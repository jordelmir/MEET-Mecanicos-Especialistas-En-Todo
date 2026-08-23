#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
android_root="$repo_root/android/app/src/main/kotlin/com/elysium369/meet"
migration="$repo_root/supabase/migrations/20260823010000_elysium_communications_core.sql"

fail() {
  echo "[communications-contract] $1" >&2
  exit 1
}

rg -q '"messages"' "$android_root/ui/screens/home/classic/HomeClassicScreen.kt" ||
  fail "Mensajes is missing from Classic Home"
rg -q 'MeetDestinations.MESSAGES' "$android_root/ui/screens/home/adaptive/HomeModuleRegistry.kt" ||
  fail "Mensajes is missing from Adaptive Home"

if rg -q 'openRideCallDialer|LLAMAR CON EL TELÉFONO' \
  "$android_root/ui/screens/RideServiceScreen.kt" "$android_root/ui/ObdViewModel.kt"; then
  fail "ride communications still exposes the external dialer"
fi

rg -q 'localCiphertextBase64' "$android_root/data/local/entities/CommunicationEntities.kt" ||
  fail "encrypted local projection is missing"
if rg -q 'val (plaintext|textContent|messageBody): String' "$android_root/data/local/entities/CommunicationEntities.kt"; then
  fail "a plaintext message column was introduced"
fi

rg -q 'communication_events_participant_insert' "$migration" ||
  fail "participant-bound event insertion policy is missing"
if rg -q 'create policy communication_events_.*_(update|delete)' "$migration"; then
  fail "communication events must be append-only"
fi
rg -q 'sender_id = auth\.uid\(\)' "$migration" ||
  fail "event sender is not bound to the authenticated principal"
if rg -q 'communication_devices_owner_update' "$migration"; then
  fail "device owners must not be able to self-verify or clear revocation"
fi
rg -q 'communication_storage_conversation_id' "$migration" ||
  fail "storage object names are not parsed fail-closed"

rg -q 'io\.livekit:livekit-android:' "$repo_root/android/app/build.gradle.kts" ||
  fail "LiveKit Android transport dependency is missing"
rg -q 'endpoint\.startsWith\("https://"\)' "$android_root/communications/ElysiumCallTransport.kt" ||
  fail "call authorization endpoint is not HTTPS-only"
rg -q 'credentials\.serverUrl\.startsWith\("wss://"\)' "$android_root/communications/ElysiumCallTransport.kt" ||
  fail "media server is not WSS-only"
if rg -q 'LIVEKIT_API_SECRET' "$repo_root/android"; then
  fail "LiveKit API secret must never be present in Android sources or build files"
fi
rg -q 'ttl: 300' "$repo_root/supabase/functions/communications-call-token/index.ts" ||
  fail "call token TTL is not constrained"
rg -q 'COMMUNICATION_BLOCKED' "$repo_root/supabase/functions/communications-call-token/index.ts" ||
  fail "call authorization does not enforce participant blocks"
rg -q 'CALL_RATE_LIMITED' "$repo_root/supabase/functions/communications-call-token/index.ts" ||
  fail "call authorization has no abuse rate limit"

echo "[communications-contract] OK"
