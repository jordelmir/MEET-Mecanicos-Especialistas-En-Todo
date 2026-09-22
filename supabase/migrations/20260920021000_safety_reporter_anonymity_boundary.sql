begin;

create table if not exists safety_private.public_point_report_links(
 report_id uuid primary key references public.safety_reports(id) on delete restrict,
 public_point_id uuid not null unique references public.safety_public_points(id) on delete cascade,
 created_at timestamptz not null default now()
);
revoke all on safety_private.public_point_report_links from public, anon, authenticated;
grant all on safety_private.public_point_report_links to service_role;

insert into safety_private.public_point_report_links(report_id,public_point_id)
select source_report_id,id from public.safety_public_points where source_report_id is not null
on conflict do nothing;

drop index if exists public.safety_public_points_source_report_uidx;
alter table public.safety_public_points drop column if exists source_report_id;

create or replace function public.safety_project_citizen_report_point()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_report public.safety_reports%rowtype; v_point_id uuid;
begin
 select * into v_report from public.safety_reports where id=new.report_id;
 if new.latitude is null or new.longitude is null or v_report.state='WITHDRAWN' then return new; end if;
 select public_point_id into v_point_id from safety_private.public_point_report_links where report_id=new.report_id;
 if v_point_id is null then
  v_point_id := gen_random_uuid();
  insert into public.safety_public_points(id,category,display_latitude,display_longitude,geo_disclosure,
   location_accuracy_meters,label,claim_state,independent_source_count,civil_source_count,
   first_documented_at,last_reviewed_at,published_at,server_version)
  values(v_point_id,v_report.category,round(new.latitude::numeric,2),round(new.longitude::numeric,2),
   'APPROXIMATE_1000M',greatest(coalesce(ceil(new.accuracy_meters)::integer,0),1000),
   'Reporte ciudadano anónimo · alegación sin corroborar','ALLEGED',0,1,
   coalesce(v_report.occurred_at,v_report.created_at),now(),now(),1);
  insert into safety_private.public_point_report_links(report_id,public_point_id) values(new.report_id,v_point_id);
 end if;
 return new;
end; $$;
revoke all on function public.safety_project_citizen_report_point() from public,anon,authenticated;

create or replace function public.safety_withdraw_report_v1(p_report_id uuid,p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=auth.uid(); v_report public.safety_reports%rowtype; v_point uuid;
begin
 if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select * into v_report from public.safety_reports where id=p_report_id for update;
 if not found or v_report.reporter_user_id<>v_actor then raise exception 'REPORT_NOT_OWNED'; end if;
 select public_point_id into v_point from safety_private.public_point_report_links where report_id=p_report_id;
 delete from safety_private.public_point_report_links where report_id=p_report_id;
 if v_point is not null then delete from public.safety_public_points where id=v_point; end if;
 if v_report.state<>'WITHDRAWN' then
  update public.safety_reports set state='WITHDRAWN',state_version=state_version+1,updated_at=now() where id=p_report_id;
  update safety_private.report_content set narrative='',source_relation='UNKNOWN',latitude=null,longitude=null,accuracy_meters=null where report_id=p_report_id;
 end if;
 return jsonb_build_object('report_id',p_report_id,'state','WITHDRAWN','server_version',v_report.state_version+1,'correlation_id',null);
end; $$;
revoke all on function public.safety_withdraw_report_v1(uuid,uuid) from public,anon;
grant execute on function public.safety_withdraw_report_v1(uuid,uuid) to authenticated;

commit;
