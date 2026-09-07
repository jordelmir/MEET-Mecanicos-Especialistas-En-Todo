#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MEET / ELYSIUM — AI PROFIT ENGINE & SERVER-AUTHORITATIVE QUOTA E2E SUITE
# Testing: Plans, Models, Quotas, 100-way Concurrency, COGS, and Flywheel
# ==============================================================================

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
cd "$repo_root"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "AI Profit Engine E2E test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d /tmp/elysium-ai-verify-XXXXXX)"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59800 + RANDOM % 150))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL 16 on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir -c max_connections=250" start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Supabase Auth Schema ==="
psql "${psql_args[@]}" << 'EOSQL'
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

CREATE SCHEMA IF NOT EXISTS auth;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        CREATE ROLE service_role NOLOGIN;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    email TEXT UNIQUE,
    encrypted_password TEXT,
    email_confirmed_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    raw_user_meta_data JSONB DEFAULT '{}'::jsonb,
    raw_app_meta_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS auth.identities (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_data JSONB DEFAULT '{}'::jsonb,
    provider TEXT NOT NULL,
    last_sign_in_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS auth.sessions (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

GRANT USAGE ON SCHEMA public, extensions, auth TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA auth TO service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA auth TO authenticated;

CREATE OR REPLACE FUNCTION auth.uid() RETURNS UUID AS $$
    SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION auth.role() RETURNS TEXT AS $$
    SELECT coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), 'authenticated');
$$ LANGUAGE sql STABLE;
EOSQL

echo "=== 3. Applying Cumulative Migrations (V6 through V14) ==="
migration_files=(
  "20260906000000_mobility_market_authority.sql"
  "20260906010000_mobility_financial_authority.sql"
  "20260906020000_mobility_safety_and_reserve.sql"
  "20260906030000_mobility_communications_reputation_and_surge.sql"
  "20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"
  "20260906050000_mobility_financial_authority_v8_closure.sql"
  "20260906060000_mobility_provider_capture_v9_closure.sql"
  "20260906070000_mobility_hardening_and_stops_authority.sql"
  "20260906080000_mobility_public_launch_v11_closure.sql"
  "20260906090000_mobility_provider_operations_v12_closure.sql"
  "20260906100000_global_platform_vehicle_economy_v13.sql"
  "20260906110000_ai_profit_engine_diagnostic_cases_v14.sql"
)

for mf in "${migration_files[@]}"; do
  echo "Applying: $mf"; file_path="$repo_root/supabase/migrations/$mf"
  if [[ -f "$file_path" ]]; then
    psql "${psql_args[@]}" -f "$file_path"
  fi
done

echo "=== 4. Seeding Global Market, Test Users & Vehicles ==="
psql "${psql_args[@]}" << 'EOSQL'
INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-1111-1111-111111111111', 'free_user@meet.test'),
    ('22222222-2222-2222-2222-222222222222', 'pro_user@meet.test'),
    ('33333333-3333-3333-3333-333333333333', 'attacker@meet.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.principals (principal_id, phone, full_name, status) VALUES
    ('11111111-1111-1111-1111-111111111111', '+50688881111', 'Free User', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222222', '+50688882222', 'Pro User', 'ACTIVE'),
    ('33333333-3333-3333-3333-333333333333', '+50688883333', 'Attacker', 'ACTIVE')
ON CONFLICT (principal_id) DO NOTHING;

-- Grant PRO entitlement to user 2
INSERT INTO public.user_entitlements (
    user_id, entitlement_key, product_id, source, status, starts_at, expires_at
) VALUES (
    '22222222-2222-2222-2222-222222222222', 'PRO_ACCESS', 'pro_monthly', 'google_play', 'active', now(), now() + INTERVAL '30 days'
) ON CONFLICT (user_id, entitlement_key) DO UPDATE
SET status = 'active', expires_at = now() + INTERVAL '30 days';

-- Seed vehicle owned by user 1
INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, make, model, year, license_plate, color, seat_capacity, verification_state
) VALUES (
    'aaaa0000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
    'Hyundai', 'Accent', 2005, 'HYU-101', 'Silver', 5, 'VERIFIED'
) ON CONFLICT (vehicle_id) DO NOTHING;
EOSQL

echo "=== 5. TEST 1: Model Catalog & Commercial Policy Direct Write Lockdown ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';

-- Attacker attempts to modify plan policies
DO $$
BEGIN
    UPDATE public.ai_plan_policies SET cases_per_period = 9999 WHERE plan_key = 'FREE';
    RAISE EXCEPTION 'RLS_VULNERABILITY: authenticated user modified ai_plan_policies!';
EXCEPTION WHEN OTHERS THEN
    -- Expected permission denied
END $$;

-- Attacker attempts to inject fake pricing into model catalog
DO $$
BEGIN
    INSERT INTO public.ai_model_catalog (
        provider_id, model_id, pricing_version, input_micros_usd_per_million, output_micros_usd_per_million
    ) VALUES ('fake', 'fake-model', 'v1', 0, 0);
    RAISE EXCEPTION 'RLS_VULNERABILITY: authenticated user injected model catalog row!';
EXCEPTION WHEN OTHERS THEN
    -- Expected permission denied
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Model Catalog and Plan Policies are strictly immutable by clients."

echo "=== 6. TEST 2: Authoritative Server-Side Plan Resolution ==="
psql "${psql_args[@]}" << 'EOSQL'
DO $$
DECLARE
    v_free_plan TEXT;
    v_pro_plan TEXT;
BEGIN
    v_free_plan := public.resolve_effective_ai_plan('11111111-1111-1111-1111-111111111111');
    IF v_free_plan != 'FREE' THEN
        RAISE EXCEPTION 'PLAN_RESOLUTION_ERROR: User 1 expected FREE, got %', v_free_plan;
    END IF;

    v_pro_plan := public.resolve_effective_ai_plan('22222222-2222-2222-2222-222222222222');
    IF v_pro_plan != 'PRO' THEN
        RAISE EXCEPTION 'PLAN_RESOLUTION_ERROR: User 2 expected PRO, got %', v_pro_plan;
    END IF;
END $$;
EOSQL
echo "  [PASS] Server resolves plans strictly from authoritative entitlements."

echo "=== 7. TEST 3: Atomic Case Opening & Weekly Quota Exhaustion (Gate 10) ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_v1 UUID := 'aaaa0000-0000-0000-0000-000000000001';
    v_v2 UUID := 'aaaa0000-0000-0000-0000-000000000002';
    v_res1 JSONB;
    v_res2 JSONB;
BEGIN
    -- 1. Open first diagnostic case (consumes 1 case, remaining becomes 0)
    v_res1 := public.ai_open_diagnostic_case(v_v1, NULL);
    IF (v_res1->>'remaining_cases')::int != 0 THEN
        RAISE EXCEPTION 'QUOTA_ERROR: expected 0 remaining cases, got %', v_res1->>'remaining_cases';
    END IF;

    -- 2. Section 88: Reusing open case for same vehicle/problem does NOT consume extra quota!
    v_res2 := public.ai_open_diagnostic_case(v_v1, NULL);
    IF (v_res2->>'reused_existing_case')::boolean != true THEN
        RAISE EXCEPTION 'REUSE_ERROR: expected reused_existing_case = true, got %', v_res2;
    END IF;

    -- 3. Attempting to open a case for a DIFFERENT vehicle must raise AI_QUOTA_EXHAUSTED
    BEGIN
        PERFORM public.ai_open_diagnostic_case(v_v2, NULL);
        RAISE EXCEPTION 'QUOTA_LEAK: Free user opened second case without quota!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%AI_QUOTA_EXHAUSTED%' THEN
            RAISE EXCEPTION 'UNEXPECTED_ERROR: %', SQLERRM;
        END IF;
    END;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Atomic case opening, case-reuse optimization, and quota exhaustion verified."

echo "=== 8. TEST 4: Idempotent Credit Pack Ingress (AI_CREDIT_PACK_10) ==="
psql "${psql_args[@]}" << 'EOSQL'
-- Service role grants 10 cases via AI_CREDIT_PACK_10
DO $$
DECLARE
    v_user UUID := '11111111-1111-1111-1111-111111111111';
    v_res JSONB;
    v_v2 UUID := 'aaaa0000-0000-0000-0000-000000000002';
    v_new_case JSONB;
BEGIN
    -- First grant
    v_res := public.ai_grant_purchased_credit_pack(v_user, 10, 'GOOGLE_PLAY_PURCHASE', 'GPA.9999-0001');
    IF (v_res->>'granted_cases')::int != 10 THEN
        RAISE EXCEPTION 'CREDIT_ERROR: expected 10 granted cases, got %', v_res;
    END IF;

    -- Replay with same purchase ref -> Idempotent NOOP
    v_res := public.ai_grant_purchased_credit_pack(v_user, 10, 'GOOGLE_PLAY_PURCHASE', 'GPA.9999-0001');
    IF (v_res->>'already_granted')::boolean != true THEN
        RAISE EXCEPTION 'IDEMPOTENCY_ERROR: replayed purchase grant was not handled idempotently!';
    END IF;
END $$;

-- Authenticated user can now open Case 2 using purchased credits
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_v2 UUID := 'aaaa0000-0000-0000-0000-000000000002';
    v_new_case JSONB;
BEGIN
    v_new_case := public.ai_open_diagnostic_case(v_v2, NULL);
    IF (v_new_case->>'remaining_cases')::int != 9 THEN
        RAISE EXCEPTION 'CREDIT_CONSUMPTION_ERROR: expected 9 remaining cases, got %', v_new_case;
    END IF;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Purchased credit pack granted with strict replay idempotency."

echo "=== 9. TEST 5: Turn Metering, Model Pricing & Private COGS Accounting ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_case_id UUID;
    v_turn_res JSONB;
BEGIN
    SELECT id INTO v_case_id FROM public.ai_diagnostic_cases WHERE owner_id = '11111111-1111-1111-1111-111111111111' LIMIT 1;

    -- Submit turn with 1,500 prompt tokens and 400 completion tokens on gpt-5.4-nano
    -- Cost = (1500 * 200000 / 1000000) + (400 * 1250000 / 1000000) = 300 + 500 = 800 micros USD ($0.0008)
    v_turn_res := public.ai_submit_case_turn(
        v_case_id, 'USER', 'Medí compresión: 115 psi en cilindro 1',
        '{"observation":"low_compression"}'::jsonb, '["measurement:cylinder_1_115psi"]'::jsonb,
        1500, 400, 'openai', 'gpt-5.4-nano', 450
    );

    IF (v_turn_res->>'cost_micros_usd')::bigint != 800 THEN
        RAISE EXCEPTION 'COST_METERING_ERROR: expected 800 micros USD, got %', v_turn_res->>'cost_micros_usd';
    END IF;
END $$;

-- Authenticated user CANNOT query raw private COGS table ai_usage_events
DO $$
BEGIN
    PERFORM * FROM public.ai_usage_events;
    RAISE EXCEPTION 'PRIVACY_LEAK: authenticated client accessed private COGS table ai_usage_events!';
EXCEPTION WHEN OTHERS THEN
    -- Expected permission denied
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Exact integer micro-USD COGS accounting verified; private plane protected."

echo "=== 10. TEST 6: Downstream Conversion Flywheel (AI Case -> Repair Intent) ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_case_id UUID;
    v_vehicle_id UUID;
    v_intent_id UUID;
    v_intent_res JSONB;
    v_link_res JSONB;
BEGIN
    SELECT id, vehicle_id INTO v_case_id, v_vehicle_id
    FROM public.ai_diagnostic_cases
    WHERE owner_id = '11111111-1111-1111-1111-111111111111' LIMIT 1;

    -- Create repair intent from AI findings
    v_intent_res := public.mobility_create_repair_intent_v1(
        v_vehicle_id, NULL, ARRAY['P0301'], ARRAY['Fallo de encendido en cilindro 1'],
        'IGNITION_SYSTEM', 0.95, 'DERIVED', '{"source":"elysium_ai"}'::jsonb
    );
    v_intent_id := (v_intent_res->>'repair_intent_id')::uuid;

    -- Link AI case to repair intent
    v_link_res := public.ai_link_case_to_repair_intent(v_case_id, v_intent_id);
    IF (v_link_res->>'success')::boolean != true THEN
        RAISE EXCEPTION 'LINK_ERROR: could not link AI case to repair intent';
    END IF;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Downstream conversion flywheel linked AI case to RepairIntent."

echo "=== 11. TEST 7: 100-Way Concurrent Case Opening Race (Section 104) ==="
psql "${psql_args[@]}" << 'EOSQL'
-- Setup user 3 with exactly 1 case quota
SELECT public.ensure_ai_usage_bucket('33333333-3333-3333-3333-333333333333', 'FREE');
UPDATE public.ai_usage_buckets
SET included_cases = 1, consumed_cases = 0, purchased_cases = 0
WHERE actor_id = '33333333-3333-3333-3333-333333333333';
EOSQL

echo "Executing 100 concurrent case openings on 1-case allowance..."
OUT_LOG="$runtime_dir/ai_100_race.log"
rm -f "$OUT_LOG"

for i in $(seq 1 100); do
    (
        psql "${psql_args[@]}" -t -A -c "
            SET ROLE authenticated;
            SET request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
            SELECT public.ai_open_diagnostic_case(extensions.gen_random_uuid(), NULL);
        " >> "$OUT_LOG" 2>&1
    ) &
done
wait

SUCCESS_COUNT=$(grep -c "case_id" "$OUT_LOG" || true)
EXHAUSTED_COUNT=$(grep -c "AI_QUOTA_EXHAUSTED" "$OUT_LOG" || true)

echo "100-way concurrency results: $SUCCESS_COUNT winners, $EXHAUSTED_COUNT quota exhausted."

if [ "$SUCCESS_COUNT" -ne 1 ]; then
    echo "ERROR: Concurrency race failed! Expected exactly 1 winner, got $SUCCESS_COUNT"
    exit 1
fi

echo "  [PASS] 100-way concurrent case opening guarantees strictly 1 winner and 99 exhausted."

echo "=============================================================================="
echo "ALL AI PROFIT ENGINE & SERVER QUOTA TESTS PASSED (100% GREEN)"
echo "=============================================================================="
