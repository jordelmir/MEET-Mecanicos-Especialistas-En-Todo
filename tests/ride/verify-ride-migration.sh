#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
migration="$repo_root/supabase/migrations/20260726010000_ride_platform_foundation.sql"

if [[ ! -f "$migration" ]]; then
  echo "ride migration contract: FAIL (migration missing)" >&2
  exit 1
fi

required_tables=(
  ride_profiles
  ride_driver_vehicles
  ride_requests
  ride_offers
  ride_trip_events
  ride_consents
  ride_positions
  ride_vehicle_questions
  ride_wallets
  ride_wallet_ledger
  ride_commission_reservations
  ride_cancellations
)

for table in "${required_tables[@]}"; do
  rg -q "create table if not exists public\\.${table}" "$migration" || {
    echo "ride migration contract: FAIL (missing table $table)" >&2
    exit 1
  }
  rg -q "alter table public\\.${table} enable row level security" "$migration" || {
    echo "ride migration contract: FAIL (RLS missing for $table)" >&2
    exit 1
  }
done

required_functions=(
  ride_grant_promotional_balance
  ride_accept_offer
  ride_cancel_trip
  ride_complete_trip
)

for function_name in "${required_functions[@]}"; do
  rg -q "function public\\.${function_name}" "$migration" || {
    echo "ride migration contract: FAIL (missing RPC $function_name)" >&2
    exit 1
  }
done

security_definer_count="$(
  rg -c "security definer" "$migration"
)"
empty_search_path_count="$(
  rg -c "set search_path = ''" "$migration"
)"

if (( security_definer_count < 6 || empty_search_path_count < security_definer_count )); then
  echo "ride migration contract: FAIL (unsafe security definer search_path)" >&2
  exit 1
fi

rg -q "idempotency_key text not null unique" "$migration" || {
  echo "ride migration contract: FAIL (ledger idempotency uniqueness missing)" >&2
  exit 1
}
rg -q "revoke insert, update, delete on public\\.ride_wallet_ledger from authenticated" "$migration" || {
  echo "ride migration contract: FAIL (ledger client writes not revoked)" >&2
  exit 1
}
rg -q "revoke insert, update, delete on public\\.ride_trip_events from authenticated" "$migration" || {
  echo "ride migration contract: FAIL (event client writes not revoked)" >&2
  exit 1
}
rg -q "raise exception 'Ride transition denied'" "$migration" || {
  echo "ride migration contract: FAIL (server transition guard missing)" >&2
  exit 1
}
rg -q "500::numeric / 10000::numeric" "$migration" || {
  echo "ride migration contract: FAIL (exact 5 percent computation missing)" >&2
  exit 1
}

echo "ride migration contract: PASS"
