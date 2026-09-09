#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for tool in initdb pg_ctl psql; do
  command -v "$tool" >/dev/null || { echo "Required tool unavailable: $tool" >&2; exit 1; }
done
runtime_dir="$(mktemp -d /tmp/meet-offer-policy-XXXXXX)"
cleanup() {
  pg_ctl -D "$runtime_dir/data" stop -m fast >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
}
trap cleanup EXIT
mkdir "$runtime_dir/socket"
initdb -D "$runtime_dir/data" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$runtime_dir/data" -l "$runtime_dir/postgres.log" -o "-k $runtime_dir/socket -c listen_addresses=''" start >/dev/null
psql_args=(-h "$runtime_dir/socket" -d postgres -v ON_ERROR_STOP=1 -q)
awk '/^DO \$universal_offer_policy\$/{copy=1} copy{print} /^\$universal_offer_policy\$;/{exit}' \
  "$repo_root/supabase/migrations/20260906100000_global_platform_vehicle_economy_v13.sql" > "$runtime_dir/policy.sql"
test -s "$runtime_dir/policy.sql"
psql "${psql_args[@]}" <<'SQL'
CREATE ROLE authenticated;
CREATE SCHEMA auth;
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$ SELECT '11111111-1111-1111-1111-111111111111'::uuid $$;
CREATE TABLE public.universal_service_offers(provider_id uuid, approved boolean);
ALTER TABLE public.universal_service_offers ENABLE ROW LEVEL SECURITY;
GRANT INSERT ON public.universal_service_offers TO authenticated;
GRANT USAGE ON SCHEMA auth TO authenticated;
-- Match the already-applied migration, then strengthen it to detect widening.
CREATE POLICY universal_offers_provider_write ON public.universal_service_offers
  FOR INSERT TO authenticated WITH CHECK (provider_id = auth.uid() AND approved);
SQL
psql "${psql_args[@]}" -f "$runtime_dir/policy.sql" -f "$runtime_dir/policy.sql"
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
INSERT INTO public.universal_service_offers VALUES (auth.uid(), true);
DO $$ BEGIN
  BEGIN
    INSERT INTO public.universal_service_offers VALUES (auth.uid(), false);
    RAISE EXCEPTION 'Replay widened existing policy';
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
  BEGIN
    INSERT INTO public.universal_service_offers VALUES ('22222222-2222-2222-2222-222222222222', true);
    RAISE EXCEPTION 'Replay allowed another provider';
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
DROP POLICY universal_offers_provider_write ON public.universal_service_offers;
SQL
# Standalone V13 bootstrap still creates the required owner boundary.
psql "${psql_args[@]}" -f "$runtime_dir/policy.sql"
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
INSERT INTO public.universal_service_offers VALUES (auth.uid(), false);
DO $$ BEGIN
  BEGIN
    INSERT INTO public.universal_service_offers VALUES ('22222222-2222-2222-2222-222222222222', true);
    RAISE EXCEPTION 'Bootstrap allowed another provider';
  EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
SQL
echo 'Universal offer policy bootstrap and authority preservation: PASS'
