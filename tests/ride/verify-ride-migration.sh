#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
migration="$repo_root/supabase/migrations/20260726010000_ride_platform_foundation.sql"
ledger_migration="$repo_root/supabase/migrations/20260730010000_ride_double_entry_ledger.sql"

if [[ ! -f "$migration" || ! -f "$ledger_migration" ]]; then
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
  ride_vehicle_evidence
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
rg -q "revoke update, delete on public\\.ride_vehicle_evidence from authenticated" "$migration" || {
  echo "ride migration contract: FAIL (vehicle evidence mutability guard missing)" >&2
  exit 1
}
rg -q "c\\.category = ride_vehicle_evidence\\.category" "$migration" || {
  echo "ride migration contract: FAIL (vehicle evidence consent gate missing)" >&2
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

double_entry_tables=(
  ride_ledger_transactions
  ride_ledger_postings
  ride_commission_calculations
  ride_revenue_split_rule_sets
  ride_revenue_split_rules
)

for table in "${double_entry_tables[@]}"; do
  rg -q "create table if not exists public\\.${table}" "$ledger_migration" || {
    echo "ride migration contract: FAIL (missing double-entry table $table)" >&2
    exit 1
  }
  rg -q "alter table public\\.${table} enable row level security" "$ledger_migration" || {
    echo "ride migration contract: FAIL (RLS missing for $table)" >&2
    exit 1
  }
  rg -q "revoke all on public\\.${table} from anon, authenticated" "$ledger_migration" || {
    echo "ride migration contract: FAIL (client access not revoked for $table)" >&2
    exit 1
  }
done

rg -q "create constraint trigger ride_ledger_postings_balance" "$ledger_migration" || {
  echo "ride migration contract: FAIL (deferred journal balance missing)" >&2
  exit 1
}
rg -q "v_signed_total <> 0" "$ledger_migration" || {
  echo "ride migration contract: FAIL (journal equality check missing)" >&2
  exit 1
}
rg -q "create constraint trigger ride_revenue_split_rules_total" "$ledger_migration" || {
  echo "ride migration contract: FAIL (revenue split total guard missing)" >&2
  exit 1
}
rg -q "v_total <> 500" "$ledger_migration" || {
  echo "ride migration contract: FAIL (exact 500 bps split missing)" >&2
  exit 1
}
rg -q "create trigger ride_wallet_ledger_double_entry_mirror" "$ledger_migration" || {
  echo "ride migration contract: FAIL (legacy wallet mirror missing)" >&2
  exit 1
}
rg -q "source_entry_id" "$ledger_migration" || {
  echo "ride migration contract: FAIL (ledger backfill provenance missing)" >&2
  exit 1
}

echo "ride migration contract: PASS"
