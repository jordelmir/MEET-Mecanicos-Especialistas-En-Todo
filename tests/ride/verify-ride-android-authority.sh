#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
database="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/data/local/MeetDatabase.kt"
module="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/di/AppModule.kt"
entity="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/ride/data/local/RideCommandOutboxEntity.kt"
dao="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/ride/data/local/RideCommandOutboxDao.kt"
gateway="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/ride/data/remote/RideCommandGateway.kt"
worker="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/ride/work/RideCommandSyncWorker.kt"
projection="$repo_root/android/app/src/main/kotlin/com/elysium369/meet/ride/data/RideRemoteProjectionRepository.kt"

required_files=("$database" "$module" "$entity" "$dao" "$gateway" "$worker" "$projection")
for path in "${required_files[@]}"; do
  [[ -f "$path" ]] || {
    echo "ride Android authority contract: FAIL (missing $path)" >&2
    exit 1
  }
done

rg -q "version = 45" "$database" || {
  echo "ride Android authority contract: FAIL (Room version not advanced)" >&2
  exit 1
}
rg -q "MIGRATION_44_45" "$module" || {
  echo "ride Android authority contract: FAIL (Room migration missing)" >&2
  exit 1
}
rg -q 'CREATE TABLE IF NOT EXISTS `ride_command_outbox`' "$module" || {
  echo "ride Android authority contract: FAIL (outbox table migration missing)" >&2
  exit 1
}
rg -Fq 'primaryKeys = ["idempotencyKey"]' "$entity" || {
  echo "ride Android authority contract: FAIL (idempotency primary key missing)" >&2
  exit 1
}
rg -q "actorSessionUserId" "$entity" || {
  echo "ride Android authority contract: FAIL (session binding missing)" >&2
  exit 1
}
rg -q "recoverStaleLeases" "$dao" || {
  echo "ride Android authority contract: FAIL (stale lease recovery missing)" >&2
  exit 1
}
rg -q "@HiltWorker" "$worker" || {
  echo "ride Android authority contract: FAIL (real Hilt worker missing)" >&2
  exit 1
}
rg -q "currentUserOrNull" "$worker" || {
  echo "ride Android authority contract: FAIL (authenticated worker binding missing)" >&2
  exit 1
}
rg -q "reconcileServerSnapshot" "$worker" || {
  echo "ride Android authority contract: FAIL (server reconciliation missing)" >&2
  exit 1
}

for rpc in \
  ride_create_request_v2 \
  ride_submit_offer_v2 \
  ride_accept_offer_v2 \
  ride_claim_request_v2 \
  ride_driver_transition_v2 \
  ride_issue_boarding_pin_v2 \
  ride_verify_boarding_pin_v2 \
  ride_cancel_trip_v2 \
  ride_complete_trip_v2
do
  rg -q "\"${rpc}\"" "$gateway" || {
    echo "ride Android authority contract: FAIL (missing RPC $rpc)" >&2
    exit 1
  }
done

if rg -q "p_actor_id|actor_id" "$gateway"; then
  echo "ride Android authority contract: FAIL (client attempts to supply actor)" >&2
  exit 1
fi
if rg -q "\\b(Double|Float)\\b" "$entity" "$gateway" "$worker"; then
  echo "ride Android authority contract: FAIL (floating money in command boundary)" >&2
  exit 1
fi
rg -q "postgresChangeFlow" "$projection" || {
  echo "ride Android authority contract: FAIL (Realtime wake-up missing)" >&2
  exit 1
}
rg -q "refreshVisibleRides" "$projection" || {
  echo "ride Android authority contract: FAIL (RLS catch-up missing)" >&2
  exit 1
}
rg -q "RemoteRideOfferProjection" "$projection" || {
  echo "ride Android authority contract: FAIL (offer catch-up missing)" >&2
  exit 1
}
rg -q 'table = "ride_offers"' "$projection" || {
  echo "ride Android authority contract: FAIL (offer Realtime wake-up missing)" >&2
  exit 1
}

echo "ride Android authority contract: PASS"
