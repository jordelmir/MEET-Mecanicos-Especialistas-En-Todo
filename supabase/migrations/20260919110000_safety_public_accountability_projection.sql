begin;
-- Sanitized publication output only. Raw institutional events never become public automatically.
create table if not exists public.safety_public_accountability_projection (
    event_id uuid primary key,
    case_id uuid not null references public.safety_public_case_projection(case_id) on delete cascade,
    case_title text,
    institution_ref text not null,
    event_type text not null check (event_type in ('REPORT_SENT','DELIVERY_CONFIRMED','REFERENCE_RECEIVED','FOLLOW_UP_SENT','RESPONSE_DOCUMENTED','PUBLIC_ACTION_FOUND','RESULT_DOCUMENTED')),
    occurred_at timestamptz not null,
    published_at timestamptz not null,
    server_version bigint not null check (server_version > 0)
);
comment on table public.safety_public_accountability_projection is
'Publication-reviewed sanitized events. No client writes; absence never implies institutional inaction.';
alter table public.safety_public_accountability_projection enable row level security;
revoke all on public.safety_public_accountability_projection from anon, authenticated;
grant select on public.safety_public_accountability_projection to authenticated;
grant all on public.safety_public_accountability_projection to service_role;
create policy safety_public_accountability_projection_read
on public.safety_public_accountability_projection for select to authenticated using (true);
create index if not exists safety_public_accountability_case_time
on public.safety_public_accountability_projection(case_id, institution_ref, occurred_at);
commit;
