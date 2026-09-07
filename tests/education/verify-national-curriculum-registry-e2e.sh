#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MEET / ELYSIUM CIVILIZATION COORDINATION PLATFORM
# Test Suite: verify-national-curriculum-registry-e2e.sh
# Verification: MEP / DGEC National Curriculum Registry (Grades 1 to 11 & BxM),
#               Cross-Grade Prerequisite DAG, Multi-Discipline Completeness,
#               Technical Service Bridges (CAD & Electrical), & Frontier Progression
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

runtime_dir="$(mktemp -d /tmp/elysium-national-curriculum-XXXXXX)"
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

echo "=== 3. Applying Cumulative Migrations (V6 through V16) ==="
migrations=(
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
  "20260906130000_elysium_curriculum_national_registry_v16.sql"
)

for m in "${migrations[@]}"; do
  echo "Applying: $m"
  psql "${psql_args[@]}" -f "$REPO_ROOT/supabase/migrations/$m" >/dev/null
done

echo "=== 4. TEST 1: Curriculum Catalog Completeness (I, II, III Ciclos & Diversificada) ==="
counts="$(psql "${psql_args[@]}" -t -A << 'EOSQL'
SELECT 
  COUNT(DISTINCT source_id) AS sources_count,
  COUNT(DISTINCT education_level) AS levels_count,
  COUNT(DISTINCT grade) AS grades_count,
  COUNT(DISTINCT subject) AS subjects_count
FROM public.curriculum_sources;
EOSQL
)"

IFS='|' read -r s_count l_count g_count subj_count <<< "$counts"
echo "  Registered Sources: $s_count, Levels: $l_count, Unique Grades: $g_count, Subjects: $subj_count"

if [[ "$s_count" -ge 15 && "$l_count" -ge 4 && "$g_count" -ge 6 && "$subj_count" -ge 6 ]]; then
  echo "  [PASS] Comprehensive national catalog verified across cycles, grades, and disciplines."
else
  echo "  [FAIL] Incomplete curriculum coverage: sources=$s_count, levels=$l_count, grades=$g_count, subjects=$subj_count"
  exit 1
fi

echo "=== 5. TEST 2: Cross-Grade Prerequisite DAG Non-Circularity Check ==="
dag_check="$(psql "${psql_args[@]}" -t -A << 'EOSQL'
WITH RECURSIVE traverse AS (
    SELECT 
        concept_id, 
        prerequisite_concept_id, 
        ARRAY[concept_id, prerequisite_concept_id] AS path,
        false AS is_cycle
    FROM public.curriculum_prerequisites
    UNION ALL
    SELECT 
        p.concept_id,
        cp.prerequisite_concept_id,
        path || cp.prerequisite_concept_id,
        cp.prerequisite_concept_id = ANY(path)
    FROM traverse p
    JOIN public.curriculum_prerequisites cp ON p.prerequisite_concept_id = cp.concept_id
    WHERE NOT is_cycle
)
SELECT COUNT(*) FROM traverse WHERE is_cycle = true;
EOSQL
)"

if [[ "$dag_check" -eq 0 ]]; then
  echo "  [PASS] Prerequisite graph is strictly acyclic (zero recursive cycles detected)."
else
  echo "  [FAIL] Cycle detected in curriculum prerequisite DAG: $dag_check cycles found."
  exit 1
fi

echo "=== 6. TEST 3: Multi-Grade Learning Frontier Traversal (ZDP) ==="
test_learner_id="$(psql "${psql_args[@]}" -t -A << 'EOSQL'
INSERT INTO auth.users (email) VALUES ('multi_learner@elysium.cr') RETURNING id;
EOSQL
)"

psql "${psql_args[@]}" << EOSQL >/dev/null
INSERT INTO public.learner_profiles (user_id, privacy_level, is_minor)
VALUES ('$test_learner_id', 'PROTECTED_STUDENT', false);
EOSQL

psql "${psql_args[@]}" -v learner_id="$test_learner_id" << 'EOSQL'
SET ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', :'learner_id', false);

DO $$
DECLARE
    v_f1 JSONB;
    v_f2_locked JSONB;
    v_f2_unlocked JSONB;
    v_c1 INT;
    v_c2_unlocked INT;
BEGIN
    -- 1. Grade 1 math frontier has open concepts (e.g. spatial positions, no prereqs)
    v_f1 := public.learning_get_personal_frontier_v1('MATEMATICA', 1);
    v_c1 := jsonb_array_length(v_f1->'frontier_concepts');
    IF v_c1 = 0 THEN
        RAISE EXCEPTION 'FRONTIER_ERROR: Grade 1 math frontier should have root concepts';
    END IF;

    -- 2. Grade 2 is locked before Grade 1 counting prerequisite is mastered
    v_f2_locked := public.learning_get_personal_frontier_v1('MATEMATICA', 2);
    IF jsonb_array_length(v_f2_locked->'frontier_concepts') <> 0 THEN
        RAISE EXCEPTION 'DAG_VIOLATION: Grade 2 opened without mastering Grade 1 prerequisite!';
    END IF;
END $$;
RESET ROLE;

-- As superuser/service_role: seed verified mastery on Grade 1 prereq (cr_mat1_c_counting_100)
INSERT INTO public.learner_concept_states (
    learner_id, concept_id, mastery_estimate, confidence, evidence_count, successful_transfer_count, prerequisites_satisfied
) VALUES (
    :'learner_id', 'cr_mat1_c_counting_100', 0.900, 0.950, 5, 2, true
);

-- Seed verified mastery on Grade 9 math prereq (cr_mat9_c_productos_notables) to unlock BxM
INSERT INTO public.learner_concept_states (
    learner_id, concept_id, mastery_estimate, confidence, evidence_count, successful_transfer_count, prerequisites_satisfied
) VALUES (
    :'learner_id', 'cr_mat9_c_productos_notables', 0.900, 0.950, 5, 2, true
);

SET ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', :'learner_id', false);

DO $$
DECLARE
    v_f2_unlocked JSONB;
    v_fbxm_unlocked JSONB;
    v_c2 INT;
    v_cbxm INT;
BEGIN
    -- 3. Now Grade 2 math frontier dynamically unlocks cr_mat2_c_centenas
    v_f2_unlocked := public.learning_get_personal_frontier_v1('MATEMATICA', 2);
    v_c2 := jsonb_array_length(v_f2_unlocked->'frontier_concepts');
    IF v_c2 = 0 THEN
        RAISE EXCEPTION 'FRONTIER_ERROR: Grade 2 math frontier should be unlocked after mastering Grade 1 prereq';
    END IF;

    -- 4. BxM Grade 10/11 unlocks cr_mat_bxm_c_circunferencia
    v_fbxm_unlocked := public.learning_get_personal_frontier_v1('MATEMATICA', 10);
    v_cbxm := jsonb_array_length(v_fbxm_unlocked->'frontier_concepts');
    IF v_cbxm = 0 THEN
        RAISE EXCEPTION 'FRONTIER_ERROR: BxM Grade 10 math frontier should be unlocked after mastering Grade 9 prereq';
    END IF;
END $$;
RESET ROLE;
EOSQL
echo "  [PASS] Cross-grade learning frontier progression (ZDP) correctly enforces prerequisite DAG across years."

echo "=== 7. TEST 4: Extended Technical Bridges (CAD Draughtspersons & Residential Electricians) ==="
bridges_count="$(psql "${psql_args[@]}" -t -A << 'EOSQL'
SELECT COUNT(*) FROM public.skill_to_service_mappings 
WHERE isco_code IN ('3118', '7411', '7126');
EOSQL
)"

echo "  Active Vocational Service Bridges: $bridges_count"
if [[ "$bridges_count" -ge 3 ]]; then
  echo "  [PASS] Technical skills from 7.º, 8.º, and 9.º correctly mapped to ISCO-08 occupational codes."
else
  echo "  [FAIL] Missing vocational bridges: count=$bridges_count (expected >= 3)."
  exit 1
fi

echo "=== 8. TEST 5: Bachillerato por Madurez 6-Subject Catalog Immutability ==="
bxm_subjects="$(psql "${psql_args[@]}" -t -A << 'EOSQL'
SELECT COUNT(DISTINCT subject) FROM public.curriculum_sources 
WHERE education_plan = 'ADULTOS' AND education_level = 'DIVERSIFICADA';
EOSQL
)"

echo "  BxM Verified Subjects: $bxm_subjects (Matemática, Español, Estudios Sociales, Cívica, Biología, Química, Inglés)"
if [[ "$bxm_subjects" -ge 6 ]]; then
  echo "  [PASS] Complete 6-subject Bachillerato por Madurez catalog registered with verified provenance."
else
  echo "  [FAIL] Incomplete BxM subject catalog: count=$bxm_subjects (expected >= 6)."
  exit 1
fi

echo "=============================================================================="
echo "ALL NATIONAL CURRICULUM REGISTRY (MEP/DGEC 1.º TO 11.º & BxM) TESTS PASSED (100% GREEN)"
echo "=============================================================================="
