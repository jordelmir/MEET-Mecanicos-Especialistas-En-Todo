#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
migration="$repo_root/supabase/migrations/20260726010000_ride_platform_foundation.sql"
ledger_migration="$repo_root/supabase/migrations/20260730010000_ride_double_entry_ledger.sql"
command_migration="$repo_root/supabase/migrations/20260730020000_ride_command_authority.sql"
flow_migration="$repo_root/supabase/migrations/20260730030000_ride_passenger_driver_commands.sql"
enrollment_migration="$repo_root/supabase/migrations/20260730040000_ride_driver_pilot_enrollment.sql"
guardian_migration="$repo_root/supabase/migrations/20260730050000_ride_guardian_safety.sql"
support_migration="$repo_root/supabase/migrations/20260730060000_ride_support_cases.sql"
tenant_migration="$repo_root/supabase/migrations/20260730070000_ride_tenant_boundary.sql"
usage_roles_migration="$repo_root/supabase/migrations/20260816010000_authenticated_usage_roles.sql"
schema_drift_migration="$repo_root/supabase/migrations/20260829020000_ride_legacy_schema_drift_hardening.sql"

if [[ ! -f "$migration" || ! -f "$ledger_migration" ||
      ! -f "$command_migration" || ! -f "$flow_migration" ||
      ! -f "$enrollment_migration" || ! -f "$guardian_migration" ||
      ! -f "$support_migration" || ! -f "$tenant_migration" ||
      ! -f "$usage_roles_migration" || ! -f "$schema_drift_migration" ]]; then
  echo "ride migration contract: FAIL (migration missing)" >&2
  exit 1
fi

rg -q "create table if not exists public\.ride_trip_feedback" "$schema_drift_migration" || {
  echo "ride migration contract: FAIL (durable trip feedback missing)" >&2
  exit 1
}
rg -q "public\.ride_accept_offer_v2" "$schema_drift_migration" || {
  echo "ride migration contract: FAIL (auto-match does not delegate to command kernel)" >&2
  exit 1
}
rg -q "BANK_CONFIRMATION_REQUIRES_TRUSTED_INGESTION" "$schema_drift_migration" || {
  echo "ride migration contract: FAIL (bank proof boundary missing)" >&2
  exit 1
}
rg -q "AUTHORITATIVE_FARE_REQUIRED" "$schema_drift_migration" || {
  echo "ride migration contract: FAIL (authoritative payment amount guard missing)" >&2
  exit 1
}
if rg -q "agreed_fare_minor|offered_fare_minor.*ride_offers|estimated_arrival_minutes|o\.status" \
  "$schema_drift_migration"; then
  echo "ride migration contract: FAIL (legacy ride column drift reintroduced)" >&2
  exit 1
fi
if rg -qi "coalesce\([^)]*,[[:space:]]*3000\)" "$schema_drift_migration"; then
  echo "ride migration contract: FAIL (synthetic payment fallback reintroduced)" >&2
  exit 1
fi

rg -q "function public\.meet_activate_usage_profile_v1" "$usage_roles_migration" || {
  echo "ride migration contract: FAIL (authenticated usage role RPC missing)" >&2
  exit 1
}
rg -q "'ride_passenger'" "$usage_roles_migration" &&
rg -q "'ride_driver'" "$usage_roles_migration" || {
  echo "ride migration contract: FAIL (ride usage roles missing)" >&2
  exit 1
}
rg -q "verification_required" "$usage_roles_migration" || {
  echo "ride migration contract: FAIL (driver verification honesty missing)" >&2
  exit 1
}
rg -q "revoke all on function public\.meet_activate_usage_profile_v1" "$usage_roles_migration" || {
  echo "ride migration contract: FAIL (usage role RPC privilege hardening missing)" >&2
  exit 1
}

rg -q "create table if not exists public\\.ride_safety_events" \
  "$guardian_migration" || {
  echo "ride migration contract: FAIL (Guardian events missing)" >&2
  exit 1
}
rg -q "function public\\.ride_signal_safety_v2" "$guardian_migration" || {
  echo "ride migration contract: FAIL (Guardian RPC missing)" >&2
  exit 1
}
rg -q "authorities_contacted boolean not null default false" \
  "$guardian_migration" || {
  echo "ride migration contract: FAIL (Guardian authority honesty missing)" >&2
  exit 1
}
rg -q "create table if not exists public\\.ride_support_cases" \
  "$support_migration" || {
  echo "ride migration contract: FAIL (support cases missing)" >&2
  exit 1
}
rg -q "function public\\.ride_open_support_case_v2" "$support_migration" || {
  echo "ride migration contract: FAIL (support RPC missing)" >&2
  exit 1
}
rg -q "create table if not exists public\\.ride_tenants" \
  "$tenant_migration" || {
  echo "ride migration contract: FAIL (tenant boundary missing)" >&2
  exit 1
}
rg -q "RIDE_TENANT_MISMATCH" "$tenant_migration" || {
  echo "ride migration contract: FAIL (tenant assignment guard missing)" >&2
  exit 1
}

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
rg -q "commissionable_base_minor" "$ledger_migration" || {
  echo "ride migration contract: FAIL (commission base provenance missing)" >&2
  exit 1
}

command_tables=(
  ride_command_receipts
  ride_fare_quotes
  ride_operational_holds
)

for table in "${command_tables[@]}"; do
  rg -q "create table if not exists public\\.${table}" "$command_migration" || {
    echo "ride migration contract: FAIL (missing command table $table)" >&2
    exit 1
  }
  rg -q "alter table public\\.${table} enable row level security" "$command_migration" || {
    echo "ride migration contract: FAIL (command RLS missing for $table)" >&2
    exit 1
  }
  rg -q "revoke all on public\\.${table} from anon, authenticated" "$command_migration" || {
    echo "ride migration contract: FAIL (command client writes not revoked for $table)" >&2
    exit 1
  }
done

command_functions=(
  ride_claim_request_v2
  ride_cancel_trip_v2
  ride_complete_trip_v2
)

for function_name in "${command_functions[@]}"; do
  rg -q "create or replace function public\\.${function_name}" "$command_migration" || {
    echo "ride migration contract: FAIL (missing command RPC $function_name)" >&2
    exit 1
  }
done

rg -q "p_expected_version bigint" "$command_migration" || {
  echo "ride migration contract: FAIL (optimistic version contract missing)" >&2
  exit 1
}
rg -q "pg_advisory_xact_lock" "$command_migration" || {
  echo "ride migration contract: FAIL (idempotency serialization missing)" >&2
  exit 1
}
rg -q "for update" "$command_migration" || {
  echo "ride migration contract: FAIL (row lock missing)" >&2
  exit 1
}
rg -q "IDEMPOTENCY_CONFLICT" "$command_migration" || {
  echo "ride migration contract: FAIL (stable idempotency error missing)" >&2
  exit 1
}
rg -q "VERSION_CONFLICT" "$command_migration" || {
  echo "ride migration contract: FAIL (stable version error missing)" >&2
  exit 1
}
rg -q "ride-commission-v1" "$command_migration" || {
  echo "ride migration contract: FAIL (commission policy provenance missing)" >&2
  exit 1
}
rg -q "tip_minor_excluded" "$command_migration" || {
  echo "ride migration contract: FAIL (non-commissionable tip evidence missing)" >&2
  exit 1
}
rg -q "platform_promotion_minor_excluded" "$command_migration" || {
  echo "ride migration contract: FAIL (promotion exclusion evidence missing)" >&2
  exit 1
}

state_constraint="$(
  sed -n \
    '/add constraint ride_requests_state_check check (/,/^    );/p' \
    "$command_migration"
)"
if [[ -z "$state_constraint" ]] || grep -q "SAFETY_HOLD" <<<"$state_constraint"; then
  echo "ride migration contract: FAIL (safety hold still encoded as lifecycle state)" >&2
  exit 1
fi

legacy_rpc_signatures=(
  "ride_claim_request(uuid, uuid, text)"
  "ride_accept_offer(uuid, uuid, text)"
  "ride_cancel_trip(uuid, text, text, text)"
  "ride_complete_trip(uuid, bigint, text)"
)

for signature in "${legacy_rpc_signatures[@]}"; do
  rg -Fq "revoke execute on function public.${signature}" "$command_migration" || {
    echo "ride migration contract: FAIL (legacy RPC still executable: $signature)" >&2
    exit 1
  }
done

flow_functions=(
  ride_create_request_v2
  ride_submit_offer_v2
  ride_accept_offer_v2
  ride_driver_transition_v2
  ride_issue_boarding_pin_v2
  ride_verify_boarding_pin_v2
)

for function_name in "${flow_functions[@]}"; do
  rg -q "create or replace function public\\.${function_name}" "$flow_migration" || {
    echo "ride migration contract: FAIL (missing vertical RPC $function_name)" >&2
    exit 1
  }
done

rg -q "revoke insert on public\\.ride_requests from authenticated" "$flow_migration" || {
  echo "ride migration contract: FAIL (direct request creation still granted)" >&2
  exit 1
}
rg -q "revoke insert on public\\.ride_offers from authenticated" "$flow_migration" || {
  echo "ride migration contract: FAIL (direct offer creation still granted)" >&2
  exit 1
}
rg -q "'pin', p_pin" "$flow_migration" || {
  echo "ride migration contract: FAIL (PIN attempt missing from request hash)" >&2
  exit 1
}
rg -q "extensions\\.crypt\\(p_pin" "$flow_migration" || {
  echo "ride migration contract: FAIL (server PIN verification missing)" >&2
  exit 1
}

rg -q "create or replace function public\\.ride_enroll_driver_pilot_v2" \
  "$enrollment_migration" || {
  echo "ride migration contract: FAIL (pilot enrollment RPC missing)" >&2
  exit 1
}
rg -q "PILOT_EVIDENCE_ATTESTATION" "$enrollment_migration" || {
  echo "ride migration contract: FAIL (honest pilot authority missing)" >&2
  exit 1
}
rg -q "document_review_status" "$enrollment_migration" || {
  echo "ride migration contract: FAIL (document review lifecycle missing)" >&2
  exit 1
}
rg -q "pilot_access_expires_at > now" "$enrollment_migration" || {
  echo "ride migration contract: FAIL (pilot expiry gate missing)" >&2
  exit 1
}
rg -q "ride_guard_dispatch_vehicle" "$enrollment_migration" || {
  echo "ride migration contract: FAIL (server dispatch guard missing)" >&2
  exit 1
}

echo "ride migration contract: PASS"
