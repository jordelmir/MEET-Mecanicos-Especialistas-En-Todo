begin;

-- Every received report with a location produces an authoritative, coarse,
-- worldwide citizen marker. Narrative and exact coordinates remain private.
alter table public.safety_public_points
    add column if not exists source_report_id uuid references public.safety_reports(id) on delete restrict;
create unique index if not exists safety_public_points_source_report_uidx
    on public.safety_public_points(source_report_id) where source_report_id is not null;

alter table public.safety_reports drop constraint if exists safety_reports_state_check;
alter table public.safety_reports add constraint safety_reports_state_check check (
    state in ('RECEIVED','TRIAGE','UNDER_REVIEW','CLOSED','WITHDRAWN')
);

create or replace function public.safety_project_citizen_report_point()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_report public.safety_reports%rowtype;
begin
    select * into v_report from public.safety_reports where id = new.report_id;
    if new.latitude is null or new.longitude is null or v_report.state = 'WITHDRAWN' then return new; end if;
    insert into public.safety_public_points(
        source_report_id, category, display_latitude, display_longitude,
        geo_disclosure, location_accuracy_meters, label, claim_state,
        independent_source_count, civil_source_count, first_documented_at,
        last_reviewed_at, published_at, server_version
    ) values (
        new.report_id, v_report.category, round(new.latitude::numeric, 2)::double precision,
        round(new.longitude::numeric, 2)::double precision, 'APPROXIMATE_1000M',
        greatest(coalesce(ceil(new.accuracy_meters)::integer, 0), 1000),
        'Reporte ciudadano · alegación sin corroborar', 'ALLEGED', 0, 1,
        coalesce(v_report.occurred_at, v_report.created_at), now(), now(), 1
    ) on conflict (source_report_id) where source_report_id is not null do update set
        category = excluded.category,
        display_latitude = excluded.display_latitude,
        display_longitude = excluded.display_longitude,
        first_documented_at = excluded.first_documented_at,
        last_reviewed_at = now(), server_version = public.safety_public_points.server_version + 1;
    return new;
end;
$$;
revoke all on function public.safety_project_citizen_report_point() from public, anon, authenticated;
drop trigger if exists safety_project_citizen_report_point on safety_private.report_content;
create trigger safety_project_citizen_report_point
after insert on safety_private.report_content
for each row execute function public.safety_project_citizen_report_point();

-- Backfill historical located reports so the map is a durable time record.
insert into public.safety_public_points(
    source_report_id, category, display_latitude, display_longitude,
    geo_disclosure, location_accuracy_meters, label, claim_state,
    independent_source_count, civil_source_count, first_documented_at,
    last_reviewed_at, published_at, server_version
)
select r.id, r.category, round(c.latitude::numeric, 2)::double precision,
       round(c.longitude::numeric, 2)::double precision, 'APPROXIMATE_1000M',
       greatest(coalesce(ceil(c.accuracy_meters)::integer,0),1000),
       'Reporte ciudadano · alegación sin corroborar', 'ALLEGED', 0, 1,
       coalesce(r.occurred_at,r.created_at), now(), now(), 1
from public.safety_reports r join safety_private.report_content c on c.report_id=r.id
where r.state <> 'WITHDRAWN' and c.latitude is not null and c.longitude is not null
on conflict (source_report_id) where source_report_id is not null do nothing;

create or replace function public.safety_withdraw_report_v1(p_report_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := auth.uid(); v_report public.safety_reports%rowtype; v_result jsonb;
begin
    if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
    select * into v_report from public.safety_reports where id=p_report_id for update;
    if not found or v_report.reporter_user_id <> v_actor then raise exception 'REPORT_NOT_OWNED'; end if;
    if v_report.state <> 'WITHDRAWN' then
        update public.safety_reports set state='WITHDRAWN', state_version=state_version+1, updated_at=now() where id=p_report_id;
        update safety_private.report_content set narrative='', source_relation='UNKNOWN', latitude=null, longitude=null,
            accuracy_meters=null where report_id=p_report_id;
        delete from public.safety_public_points where source_report_id=p_report_id;
    end if;
    v_result := jsonb_build_object('report_id',p_report_id,'state','WITHDRAWN','server_version',v_report.state_version+1,'correlation_id',null);
    return v_result;
end;
$$;
revoke all on function public.safety_withdraw_report_v1(uuid,uuid) from public, anon;
grant execute on function public.safety_withdraw_report_v1(uuid,uuid) to authenticated;

commit;
