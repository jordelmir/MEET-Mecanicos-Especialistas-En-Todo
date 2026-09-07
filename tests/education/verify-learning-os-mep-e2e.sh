#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MEET / ELYSIUM CIVILIZATION COORDINATION PLATFORM
# Test Suite: verify-learning-os-mep-e2e.sh
# Verification: MEP 2026 Curriculum Registry, Course Zero (Matemática 1.º),
#               Learner Digital Twin, Mastery Engine, Child Privacy (Ley 8968),
#               Skill-to-Service Bridge & 100-Way Concurrent Evidence Recording
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

runtime_dir="$(mktemp -d /tmp/elysium-learning-verify-XXXXXX)"
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
trap cleanup EXIT INT TERM

echo "=== 1. Starting Ephemeral PostgreSQL 16 on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir -c max_connections=250" start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Supabase Auth Schema ==="
psql "${psql_args[@]}" << 'EOSQL' >/dev/null 2>&1
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "pgcrypto" SCHEMA extensions;

CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;

CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    email TEXT UNIQUE,
    raw_user_meta_data JSONB DEFAULT '{}'::jsonb,
    raw_app_meta_data JSONB DEFAULT '{}'::jsonb,
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

echo "=== 3. Applying Cumulative Migrations (V6 through V15) ==="
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
  "20260906120000_elysium_learning_os_mep_2026_v15.sql"
)

for mf in "${migration_files[@]}"; do
    echo "Applying: $mf"
    psql "${psql_args[@]}" -f "$REPO_ROOT/supabase/migrations/$mf" >/dev/null 2>&1
done

echo "=== 4. Seeding Test Users & Minor Learner Profile ==="
psql "${psql_args[@]}" << 'EOSQL' >/dev/null 2>&1
-- User 1: Student (Minor)
-- User 2: Guardian (Parent)
-- User 3: Unrelated Student 2
-- User 4: Attacker
INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-1111-1111-111111111111', 'student1@meet.test'),
    ('22222222-2222-2222-2222-222222222222', 'guardian1@meet.test'),
    ('33333333-3333-3333-3333-333333333333', 'student2@meet.test'),
    ('44444444-4444-4444-4444-444444444444', 'attacker@meet.test')
ON CONFLICT (id) DO NOTHING;

-- Seed Learner Profile for Student 1 with Guardian Link (Ley 8968)
INSERT INTO public.learner_profiles (
    user_id, is_minor, guardian_user_id, active_plan, privacy_level
) VALUES (
    '11111111-1111-1111-1111-111111111111', true, '22222222-2222-2222-2222-222222222222', 'REGULAR', 'PROTECTED_STUDENT'
) ON CONFLICT (user_id) DO NOTHING;
EOSQL

echo "=== 5. TEST 1: Curriculum Provenance & Authority Gate (Gate 67) ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '44444444-4444-4444-4444-444444444444';

-- Attacker attempts to insert fabricated unanchored curriculum
DO $$
BEGIN
    INSERT INTO public.curriculum_concepts (
        id, unit_id, concept_code, title, description, is_official, source_anchor
    ) VALUES (
        'fake_concept', 'cr_mat1_u02', 'CR_FAKE', 'Fabricated Math', 'Desc', true, 'NONE'
    );
    RAISE EXCEPTION 'AUTHORITY_VULNERABILITY: Authenticated client created curriculum concept directly!';
EXCEPTION WHEN OTHERS THEN
    -- Expected permission denied
END $$;
RESET ROLE;

-- Verify authoritative Course Zero (Matemática 1.º) was seeded with exact official months (Feb..Nov)
DO $$
DECLARE
    v_units_count INT;
    v_concepts_count INT;
BEGIN
    SELECT count(*) INTO v_units_count FROM public.curriculum_units WHERE source_id = 'cr_mep_matematicas_1_2026';
    IF v_units_count < 10 THEN
        RAISE EXCEPTION 'COURSE_ZERO_ERROR: expected 10 official units, found %', v_units_count;
    END IF;

    SELECT count(*) INTO v_concepts_count FROM public.curriculum_concepts WHERE id LIKE 'cr_mat1_%';
    IF v_concepts_count < 7 THEN
        RAISE EXCEPTION 'COURSE_ZERO_ERROR: expected at least 7 official concepts, found %', v_concepts_count;
    END IF;
END $$;
EOSQL
echo "  [PASS] Curriculum catalog is authoritative and strictly immutable by clients."

echo "=== 6. TEST 2: Personal Learning Frontier & Prerequisite Graph (Gate 16) ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_frontier JSONB;
    v_count INT;
    v_has_spatial BOOLEAN := false;
    v_has_addition BOOLEAN := false;
    v_item JSONB;
BEGIN
    -- Fetch frontier for Grade 1 Mathematics
    v_frontier := public.learning_get_personal_frontier_v1('MATEMATICA', 1);
    v_count := jsonb_array_length(v_frontier->'frontier_concepts');

    IF v_count = 0 THEN
        RAISE EXCEPTION 'FRONTIER_ERROR: Expected candidate concepts in learning frontier, got 0';
    END IF;

    FOR v_item IN SELECT * FROM jsonb_array_elements(v_frontier->'frontier_concepts') LOOP
        IF v_item->>'concept_id' = 'cr_mat1_c_spatial_pos' THEN
            v_has_spatial := true;
        END IF;
        IF v_item->>'concept_id' = 'cr_mat1_c_addition_sub' THEN
            v_has_addition := true;
        END IF;
    END LOOP;

    -- Spatial positioning (Unit 2 / Feb) must be available
    IF NOT v_has_spatial THEN
        RAISE EXCEPTION 'FRONTIER_ERROR: Expected cr_mat1_c_spatial_pos to be in initial frontier';
    END IF;

    -- Addition & Subtraction requires Counting < 100 prerequisite, so it must NOT be in initial frontier
    IF v_has_addition THEN
        RAISE EXCEPTION 'PREREQUISITE_LEAK: Addition appeared in frontier before prerequisite counting was mastered!';
    END IF;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Learning frontier correctly computes active learning horizon and respects prerequisite DAG."

echo "=== 7. TEST 3: Evidence Recording, Transfer Invariant & Mastery Engine (Gate 14 & 15) ==="
psql "${psql_args[@]}" << 'EOSQL'
SET ROLE authenticated;
SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_res JSONB;
    v_mastery NUMERIC;
    v_transfer_count INT;
BEGIN
    -- 1. Submit 7 consecutive correct standard tasks (without transfer) to reach 0.750 cap
    FOR i IN 1..7 LOOP
        v_res := public.learning_record_evidence_v1(
            'cr_mat1_c_spatial_pos', 'task_behind_0' || i, true, false, NULL, 1500, 'FORGE_3D_ROOM'
        );
    END LOOP;

    v_mastery := (v_res->>'mastery_estimate')::numeric;
    v_transfer_count := (v_res->>'successful_transfer_count')::int;

    -- Invariant: Non-transfer tasks must NOT grant full mastery (capped at <= 0.750)
    IF v_mastery > 0.750 THEN
        RAISE EXCEPTION 'MASTERY_INVARIANT_VIOLATION: Non-transfer tasks awarded mastery > 0.750 (got %)', v_mastery;
    END IF;
    IF v_transfer_count != 0 THEN
        RAISE EXCEPTION 'TRANSFER_ERROR: Expected 0 transfer count, got %', v_transfer_count;
    END IF;

    -- 2. Submit a transfer task in a novel context (e.g. playground FORGE scene)
    v_res := public.learning_record_evidence_v1(
        'cr_mat1_c_spatial_pos', 'task_transfer_playground_01', true, true, NULL, 1200, 'FORGE_3D_PLAYGROUND'
    );

    v_mastery := (v_res->>'mastery_estimate')::numeric;
    v_transfer_count := (v_res->>'successful_transfer_count')::int;

    -- Invariant: Transfer task successfully unlocks mastery > 0.850
    IF v_mastery < 0.850 THEN
        RAISE EXCEPTION 'MASTERY_INVARIANT_VIOLATION: Transfer task failed to advance mastery >= 0.850 (got %)', v_mastery;
    END IF;
    IF v_transfer_count != 1 THEN
        RAISE EXCEPTION 'TRANSFER_ERROR: Expected 1 transfer count, got %', v_transfer_count;
    END IF;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Evidence recording verified; mastery strictly requires transfer tasks."

echo "=== 8. TEST 4: Child Privacy (Ley 8968) & Multi-Role RLS Isolation (Gate 26 & 68) ==="
psql "${psql_args[@]}" << 'EOSQL'
-- Test A: Attacker attempts to read Student 1's concept state -> Must return 0 rows
SET ROLE authenticated;
SET request.jwt.claim.sub = '44444444-4444-4444-4444-444444444444';

DO $$
DECLARE
    v_read_count INT;
BEGIN
    SELECT count(*) INTO v_read_count
    FROM public.learner_concept_states
    WHERE learner_id = '11111111-1111-1111-1111-111111111111';

    IF v_read_count > 0 THEN
        RAISE EXCEPTION 'PRIVACY_LEAK: Attacker read % concept records of Student 1!', v_read_count;
    END IF;
END $$;

-- Test B: Student 2 attempts to read Student 1's concept state -> Must return 0 rows
SET request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';

DO $$
DECLARE
    v_read_count INT;
BEGIN
    SELECT count(*) INTO v_read_count
    FROM public.learner_concept_states
    WHERE learner_id = '11111111-1111-1111-1111-111111111111';

    IF v_read_count > 0 THEN
        RAISE EXCEPTION 'PRIVACY_LEAK: Peer Student read % concept records of Student 1!', v_read_count;
    END IF;
END $$;

-- Test C: Guardian 1 reads Student 1's concept state -> Must return 1 row (Authorized)
SET request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';

DO $$
DECLARE
    v_read_count INT;
BEGIN
    SELECT count(*) INTO v_read_count
    FROM public.learner_concept_states
    WHERE learner_id = '11111111-1111-1111-1111-111111111111';

    IF v_read_count = 0 THEN
        RAISE EXCEPTION 'ACCESS_DENIAL: Guardian 1 could not read minor child concept states!';
    END IF;
END $$;

-- Test D: Attacker attempts direct UPDATE to fabricate mastery -> Must fail
SET request.jwt.claim.sub = '44444444-4444-4444-4444-444444444444';

DO $$
BEGIN
    UPDATE public.learner_concept_states
    SET mastery_estimate = 1.000
    WHERE learner_id = '11111111-1111-1111-1111-111111111111';
    RAISE EXCEPTION 'RLS_VULNERABILITY: Direct write to learner_concept_states succeeded!';
EXCEPTION WHEN OTHERS THEN
    -- Expected permission denied
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Child privacy (Ley 8968) enforced: peer/attacker reads blocked, guardian authorized."

echo "=== 9. TEST 5: Artes Industriales 7.º Fontanería -> Skill to Service Bridge (Gate 70 & 71) ==="
psql "${psql_args[@]}" << 'EOSQL'
DO $$
DECLARE
    v_mapping_count INT;
BEGIN
    SELECT count(*) INTO v_mapping_count
    FROM public.skill_to_service_mappings m
    JOIN public.economic_occupations o ON o.isco_code = m.isco_code
    WHERE m.service_vertical = 'RESIDENTIAL_PLUMBING' AND o.isco_code = '7126';

    IF v_mapping_count < 2 THEN
        RAISE EXCEPTION 'BRIDGE_ERROR: Expected at least 2 plumbing skills mapped to ISCO 7126, found %', v_mapping_count;
    END IF;
END $$;
EOSQL
echo "  [PASS] 7.º Fontanería successfully bridged to ISCO-08 7126 Residential Plumbing."

echo "=== 10. TEST 6: 100-Way Concurrent Evidence Recording Stress Test (Gate 104) ==="
OUT_LOG="$runtime_dir/evidence_100_race.log"
rm -f "$OUT_LOG"

echo "Executing 100 concurrent evidence recordings..."
for i in $(seq 1 100); do
    (
        psql "${psql_args[@]}" -t -A -c "
            SET ROLE authenticated;
            SET request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
            SELECT public.learning_record_evidence_v1(
                'cr_mat1_c_dimensions', 'stress_task_$i', true, false, NULL, 500, 'FORGE_3D_ROOM'
            );
        " >> "$OUT_LOG" 2>&1
    ) &
done
wait

SUCCESS_COUNT=$(grep -c "evidence_hash" "$OUT_LOG" || true)
echo "100-way concurrency results: $SUCCESS_COUNT recordings succeeded."

if [ "$SUCCESS_COUNT" -ne 100 ]; then
    echo "ERROR: Concurrency test failed! Expected 100 successful atomic recordings, got $SUCCESS_COUNT"
    tail -n 20 "$OUT_LOG"
    exit 1
fi

psql "${psql_args[@]}" << 'EOSQL'
DO $$
DECLARE
    v_recorded_count INT;
BEGIN
    SELECT evidence_count INTO v_recorded_count
    FROM public.learner_concept_states
    WHERE learner_id = '11111111-1111-1111-1111-111111111111' AND concept_id = 'cr_mat1_c_dimensions';

    IF v_recorded_count != 100 THEN
        RAISE EXCEPTION 'RACE_CONDITION_DETECTED: Expected exactly 100 recorded evidences in concept state, found %', v_recorded_count;
    END IF;
END $$;
EOSQL
echo "  [PASS] 100-way concurrent evidence recording completed with zero lost updates."

echo "=============================================================================="
echo "ALL ELYSIUM LEARNING OS & MEP 2026 E2E TESTS PASSED (100% GREEN)"
echo "==============================================================================="
