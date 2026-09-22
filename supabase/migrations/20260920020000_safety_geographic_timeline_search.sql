begin;

alter table public.safety_public_points
 add column if not exists country_name text,
 add column if not exists province_name text,
 add column if not exists canton_name text,
 add column if not exists district_name text,
 add column if not exists neighborhood_name text,
 add column if not exists street_name text;

create index if not exists safety_public_points_history_idx
 on public.safety_public_points(first_documented_at desc, published_at desc);
create index if not exists safety_public_points_geography_idx
 on public.safety_public_points(country_name, province_name, canton_name, district_name);
create index if not exists safety_public_points_search_idx on public.safety_public_points using gin (
 to_tsvector('simple', coalesce(label,'')||' '||coalesce(category,'')||' '||coalesce(country_name,'')||' '||
 coalesce(province_name,'')||' '||coalesce(canton_name,'')||' '||coalesce(district_name,'')||' '||
 coalesce(neighborhood_name,'')||' '||coalesce(street_name,''))
);

create or replace function public.safety_search_public_reports_v1(
 p_query text default null, p_from timestamptz default null, p_to timestamptz default null,
 p_limit integer default 200
) returns setof public.safety_public_points
language sql stable security invoker set search_path='' as $$
 select p.* from public.safety_public_points p
 where (p_from is null or coalesce(p.first_documented_at,p.published_at) >= p_from)
   and (p_to is null or coalesce(p.first_documented_at,p.published_at) < p_to)
   and (nullif(trim(p_query),'') is null or
     to_tsvector('simple',coalesce(p.label,'')||' '||coalesce(p.category,'')||' '||coalesce(p.country_name,'')||' '||
       coalesce(p.province_name,'')||' '||coalesce(p.canton_name,'')||' '||coalesce(p.district_name,'')||' '||
       coalesce(p.neighborhood_name,'')||' '||coalesce(p.street_name,'')) @@ websearch_to_tsquery('simple',p_query))
 order by coalesce(p.first_documented_at,p.published_at) desc
 limit least(greatest(p_limit,1),500);
$$;
revoke all on function public.safety_search_public_reports_v1(text,timestamptz,timestamptz,integer) from public, anon;
grant execute on function public.safety_search_public_reports_v1(text,timestamptz,timestamptz,integer) to authenticated;

commit;
