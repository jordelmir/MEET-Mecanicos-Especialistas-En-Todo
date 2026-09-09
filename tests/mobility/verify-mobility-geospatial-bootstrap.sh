#!/usr/bin/env bash
# Exercise V11 helper bootstrap without overwriting extension-owned functions.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for tool in initdb pg_ctl psql; do
  command -v "$tool" >/dev/null || { echo "Required tool unavailable: $tool" >&2; exit 1; }
done
runtime_dir="$(mktemp -d /tmp/meet-geospatial-XXXXXX)"
cleanup() {
  pg_ctl -D "$runtime_dir/data" stop -m fast >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
}
trap cleanup EXIT
mkdir "$runtime_dir/socket"
initdb -D "$runtime_dir/data" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$runtime_dir/data" -l "$runtime_dir/postgres.log" -o "-k $runtime_dir/socket -c listen_addresses=''" start >/dev/null
psql_args=(-h "$runtime_dir/socket" -d postgres -v ON_ERROR_STOP=1 -q)
# Run the exact migration helper block, not a copied implementation.
awk '/^-- Geospatial shim compatibility helpers/{copy=1} copy && /^-- ─/{exit} copy{print}' \
  "$repo_root/supabase/migrations/20260906080000_mobility_public_launch_v11_closure.sql" > "$runtime_dir/helpers.sql"
psql "${psql_args[@]}" <<'SQL'
CREATE SCHEMA extensions;
CREATE EXTENSION pgcrypto WITH SCHEMA extensions;
CREATE TYPE extensions.geography AS (lng double precision, lat double precision, srid integer);
-- An extension member and dependent view reproduce PostGIS ownership protections.
CREATE FUNCTION extensions.st_astext(extensions.geography) RETURNS text
LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT 'POINT(' || ($1).lng::text || ' ' || ($1).lat::text || ')' $$;
ALTER EXTENSION pgcrypto ADD FUNCTION extensions.st_astext(extensions.geography);
CREATE VIEW public.geospatial_dependent AS SELECT extensions.st_astext(ROW(-84.1, 9.9, 4326)::extensions.geography) AS point;
SQL
# Repeat to verify safe bootstrap and idempotence.
psql "${psql_args[@]}" -f "$runtime_dir/helpers.sql" -f "$runtime_dir/helpers.sql"
psql "${psql_args[@]}" <<'SQL'
DO $$ BEGIN
  IF (SELECT point FROM public.geospatial_dependent) <> 'POINT(-84.1 9.9)' THEN
    RAISE EXCEPTION 'Dependent view changed';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_depend WHERE objid = 'extensions.st_astext(extensions.geography)'::regprocedure AND deptype = 'e') THEN
    RAISE EXCEPTION 'Extension ownership lost';
  END IF;
  IF extensions.st_x(ROW(-84.1, 9.9, 4326)::extensions.geography) <> -84.1 OR
     extensions.st_y(ROW(-84.1, 9.9, 4326)::extensions.geography) <> 9.9 THEN
    RAISE EXCEPTION 'Coordinates changed';
  END IF;
END $$;
-- Also exercise creation of the fallback text helper.
DROP VIEW public.geospatial_dependent;
ALTER EXTENSION pgcrypto DROP FUNCTION extensions.st_astext(extensions.geography);
DROP FUNCTION extensions.st_astext(extensions.geography);
SQL
psql "${psql_args[@]}" -f "$runtime_dir/helpers.sql"
psql "${psql_args[@]}" <<'SQL'
DO $$ BEGIN
  IF extensions.st_astext(ROW(-84.1, 9.9, 4326)::extensions.geography) <> 'POINT(-84.1 9.9)' OR
     extensions.st_x(NULL::extensions.geography) IS NOT NULL THEN
    RAISE EXCEPTION 'Fallback geography or NULL behavior incorrect';
  END IF;
END $$;
SQL
if [[ "$(psql "${psql_args[@]}" -Atc "SELECT count(*) FROM pg_available_extensions WHERE name = 'postgis'")" == 1 ]]; then
  psql "${psql_args[@]}" -c 'DROP SCHEMA extensions CASCADE; CREATE SCHEMA extensions; CREATE EXTENSION postgis WITH SCHEMA extensions;'
  psql "${psql_args[@]}" -f "$runtime_dir/helpers.sql" -f "$runtime_dir/helpers.sql"
  psql "${psql_args[@]}" <<'SQL'
DO $$ DECLARE p extensions.geography := 'SRID=4326;POINT(-84.1 9.9)'::extensions.geography; BEGIN
  IF extensions.st_astext(p) <> 'POINT(-84.1 9.9)' OR extensions.st_x(p) <> -84.1 OR extensions.st_y(p) <> 9.9 THEN
    RAISE EXCEPTION 'Native PostGIS geography behavior incorrect';
  END IF;
END $$;
SQL
else
  echo 'Native PostGIS unavailable; extension ownership and composite fallback verified.'
fi
echo 'Mobility geospatial bootstrap: PASS'
