begin;

-- ============================================================
-- 1. Close base-table public read boundary
-- ============================================================

drop policy if exists
    safety_cases_authenticated_read
on public.safety_cases;

drop policy if exists
    safety_case_events_authenticated_read
on public.safety_case_events;

drop policy if exists
    safety_accountability_events_authenticated_read
on public.safety_accountability_events;

drop policy if exists
    safety_event_claims_authenticated_read
on public.safety_event_claims;

revoke select
on public.safety_cases
from authenticated;

revoke select
on public.safety_case_events
from authenticated;

revoke select
on public.safety_accountability_events
from authenticated;

revoke select
on public.safety_event_claims
from authenticated;


-- ============================================================
-- 2. Retire views that bypass Publication Firewall semantics
-- ============================================================

drop view if exists
    public.safety_public_cases;

drop view if exists
    public.safety_public_accountability;

drop view if exists
    public.safety_public_observatory;


-- ============================================================
-- 3. Explicit public case projection
-- ============================================================

create table if not exists
public.safety_public_case_projection (
    case_id uuid primary key,

    case_type text not null,

    title text not null,

    public_summary text not null
        default '',

    lifecycle text not null,

    confidence_score real not null
        default 0.0
        check (
            confidence_score
            between 0.0 and 1.0
        ),

    event_count integer not null
        default 0
        check (event_count >= 0),

    claim_count integer not null
        default 0
        check (claim_count >= 0),

    source_count integer not null
        default 0
        check (source_count >= 0),

    evidence_count integer not null
        default 0
        check (evidence_count >= 0),

    published_at timestamptz not null,

    last_updated_at timestamptz not null,

    server_version bigint not null
        check (server_version > 0)
);

alter table
    public.safety_public_case_projection
enable row level security;

revoke all
on public.safety_public_case_projection
from anon, authenticated;

grant select
on public.safety_public_case_projection
to authenticated;

create policy
safety_public_case_projection_read
on public.safety_public_case_projection
for select
to authenticated
using (true);


-- ============================================================
-- 4. Public sanitized timeline
-- ============================================================

create table if not exists
public.safety_public_case_timeline_projection (
    case_id uuid not null,

    milestone_id uuid not null,

    event_type text not null,

    public_summary text not null,

    occurred_at timestamptz,

    recorded_at timestamptz not null,

    source_count integer not null
        default 0,

    evidence_count integer not null
        default 0,

    server_version bigint not null,

    primary key (
        case_id,
        milestone_id
    )
);

create index if not exists
idx_safety_public_timeline_case_time
on public.safety_public_case_timeline_projection (
    case_id,
    occurred_at
);

alter table
    public.safety_public_case_timeline_projection
enable row level security;

revoke all
on public.safety_public_case_timeline_projection
from anon, authenticated;

grant select
on public.safety_public_case_timeline_projection
to authenticated;

create policy
safety_public_case_timeline_read
on public.safety_public_case_timeline_projection
for select
to authenticated
using (true);


-- ============================================================
-- 5. Sanitized public claims
-- ============================================================

create table if not exists
public.safety_public_case_claim_projection (
    case_id uuid not null,

    claim_id uuid not null,

    predicate text not null,

    claim_state text not null,

    independent_source_count integer
        not null default 0,

    evidence_count integer
        not null default 0,

    civil_source_count integer
        not null default 0,

    journalistic_source_count integer
        not null default 0,

    public_record_source_count integer
        not null default 0,

    documentary_source_count integer
        not null default 0,

    institutional_source_count integer
        not null default 0,

    server_version bigint not null,

    primary key (
        case_id,
        claim_id
    )
);

alter table
    public.safety_public_case_claim_projection
enable row level security;

revoke all
on public.safety_public_case_claim_projection
from anon, authenticated;

grant select
on public.safety_public_case_claim_projection
to authenticated;

create policy
safety_public_case_claim_read
on public.safety_public_case_claim_projection
for select
to authenticated
using (true);


-- ============================================================
-- 6. Enrich public point read model
-- ============================================================

alter table public.safety_public_points
    add column if not exists
    civil_source_count integer
    not null default 0;

alter table public.safety_public_points
    add column if not exists
    journalistic_source_count integer
    not null default 0;

alter table public.safety_public_points
    add column if not exists
    public_record_source_count integer
    not null default 0;

alter table public.safety_public_points
    add column if not exists
    documentary_source_count integer
    not null default 0;

alter table public.safety_public_points
    add column if not exists
    institutional_source_count integer
    not null default 0;

alter table public.safety_public_points
    add column if not exists
    country_code text;

alter table public.safety_public_points
    add column if not exists
    admin1_code text;

alter table public.safety_public_points
    add column if not exists
    admin2_code text;

alter table public.safety_public_points
    add column if not exists
    public_h3_cell text;


-- ============================================================
-- 7. Custody chain becomes cryptographically linked
-- ============================================================

alter table public.safety_evidence_custody
    add column if not exists
    previous_event_hash text;

alter table public.safety_evidence_custody
    add column if not exists
    event_hash text;

create unique index if not exists
idx_safety_evidence_custody_event_hash
on public.safety_evidence_custody(event_hash)
where event_hash is not null;


-- ============================================================
-- 8. Fix private original upload policy defensively
-- Already fixed live; this makes V2 self-contained.
-- ============================================================

drop policy if exists
"Authenticated users can upload safety evidence originals"
on storage.objects;

create policy
"Authenticated users can upload safety evidence originals"
on storage.objects
for insert
to authenticated
with check (
    bucket_id =
        'safety-evidence-original'
    and
    (storage.foldername(name))[1]
        = auth.uid()::text
);


-- ============================================================
-- 9. Feature gates
-- ============================================================

insert into public.runtime_feature_gates(
    key,
    enabled,
    reason
)
values
    (
        'safety_foundation',
        true,
        'Safety Foundation V2'
    ),
    (
        'safety_reporting',
        true,
        'Safety reporting enabled'
    ),
    (
        'safety_evidence_upload',
        true,
        'Private original evidence upload enabled'
    ),
    (
        'safety_public_map',
        true,
        'Published projection only'
    ),
    (
        'safety_public_cases',
        true,
        'Published projection only'
    ),
    (
        'safety_accountability',
        true,
        'Published observable events only'
    ),
    (
        'safety_observatory',
        true,
        'Aggregated public projection'
    ),
    (
        'safety_realtime',
        true,
        'Realtime public projection refresh'
    ),
    (
        'safety_guardian',
        false,
        'Closed pilot only'
    )
on conflict (key)
do nothing;

-- Current live table has overly broad table grants and RLS
-- with no read policy.
revoke all
on public.runtime_feature_gates
from anon, authenticated;

grant select
on public.runtime_feature_gates
to authenticated;

drop policy if exists
runtime_feature_gates_authenticated_read
on public.runtime_feature_gates;

create policy
runtime_feature_gates_authenticated_read
on public.runtime_feature_gates
for select
to authenticated
using (true);


-- ============================================================
-- 10. Realtime publications
-- ============================================================

alter table
    public.safety_public_points
replica identity full;

alter table
    public.safety_public_case_projection
replica identity full;

alter table
    public.safety_public_case_timeline_projection
replica identity full;

alter table
    public.safety_public_case_claim_projection
replica identity full;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname =
            'supabase_realtime'
        and schemaname = 'public'
        and tablename =
            'safety_public_points'
    ) then
        execute
            'alter publication supabase_realtime ' ||
            'add table public.safety_public_points';
    end if;

    if not exists (
        select 1
        from pg_publication_tables
        where pubname =
            'supabase_realtime'
        and schemaname = 'public'
        and tablename =
            'safety_public_case_projection'
    ) then
        execute
            'alter publication supabase_realtime ' ||
            'add table public.safety_public_case_projection';
    end if;
end;
$$;


-- ============================================================
-- 11. Evidence registration RPC
-- ============================================================

create or replace function
public.safety_register_evidence_v1(
    p_evidence_id uuid,
    p_report_id uuid,
    p_storage_path text,
    p_content_sha256 text,
    p_mime_type text,
    p_byte_count bigint,
    p_captured_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid;
    v_expected_path text;
    v_owner text;

    v_event_id uuid;
    v_event_hash text;
begin
    v_actor := auth.uid();

    if v_actor is null then
        raise exception
            'AUTHENTICATION_REQUIRED';
    end if;

    if not exists (
        select 1
        from public.safety_reports r
        where r.id = p_report_id
          and r.reporter_user_id =
              v_actor
    ) then
        raise exception
            'SAFETY_REPORT_NOT_OWNED';
    end if;

    if p_content_sha256
       !~ '^[a-f0-9]{64}$'
    then
        raise exception
            'INVALID_SHA256';
    end if;

    if p_byte_count <= 0 then
        raise exception
            'INVALID_BYTE_COUNT';
    end if;

    if p_mime_type not in (
        'image/jpeg',
        'image/png',
        'image/webp',
        'video/mp4',
        'video/webm',
        'audio/mpeg',
        'audio/mp4',
        'audio/aac',
        'audio/ogg',
        'application/pdf'
    ) then
        raise exception
            'UNSUPPORTED_MIME_TYPE';
    end if;

    v_expected_path :=
        v_actor::text
        || '/'
        || p_report_id::text
        || '/'
        || p_evidence_id::text
        || '.original';

    if p_storage_path
        <> v_expected_path
    then
        raise exception
            'INVALID_STORAGE_PATH';
    end if;

    select o.owner::text
    into v_owner
    from storage.objects o
    where
        o.bucket_id =
            'safety-evidence-original'
        and o.name =
            p_storage_path
    limit 1;

    if v_owner is null then
        raise exception
            'EVIDENCE_OBJECT_NOT_FOUND';
    end if;

    if v_owner <> v_actor::text then
        raise exception
            'EVIDENCE_OBJECT_NOT_OWNED';
    end if;

    insert into
    public.safety_evidence_objects(
        id,
        report_id,
        uploader_user_id,
        storage_path,
        content_sha256,
        mime_type,
        byte_count,
        captured_at
    )
    values (
        p_evidence_id,
        p_report_id,
        v_actor,
        p_storage_path,
        p_content_sha256,
        p_mime_type,
        p_byte_count,
        p_captured_at
    )
    on conflict (id)
    do nothing;

    if not exists (
        select 1
        from public.safety_evidence_objects e
        where e.id =
            p_evidence_id
          and e.report_id =
            p_report_id
          and e.uploader_user_id =
            v_actor
          and e.storage_path =
            p_storage_path
          and e.content_sha256 =
            p_content_sha256
          and e.byte_count =
            p_byte_count
    ) then
        raise exception
            'EVIDENCE_IDEMPOTENCY_VIOLATION';
    end if;

    if not exists (
        select 1
        from public.safety_evidence_custody c
        where c.evidence_id =
            p_evidence_id
          and c.event_type =
            'SERVER_RECEIVED'
    ) then

        v_event_id :=
            gen_random_uuid();

        v_event_hash :=
            encode(
                extensions.digest(
                    convert_to(
                        concat_ws(
                            chr(31),
                            'MEET-SAFETY-CUSTODY-V1',
                            v_event_id::text,
                            p_evidence_id::text,
                            'SERVER_RECEIVED',
                            v_actor::text,
                            p_content_sha256
                        ),
                        'UTF8'
                    ),
                    'sha256'
                ),
                'hex'
            );

        insert into
        public.safety_evidence_custody(
            id,
            evidence_id,
            event_type,
            actor_id,
            reason_code,
            previous_event_hash,
            event_hash
        )
        values (
            v_event_id,
            p_evidence_id,
            'SERVER_RECEIVED',
            v_actor,
            'ORIGINAL_UPLOAD',
            null,
            v_event_hash
        );
    end if;

    return jsonb_build_object(
        'ok', true,
        'evidence_id',
            p_evidence_id,
        'storage_path',
            p_storage_path,
        'content_sha256',
            p_content_sha256
    );
end;
$$;

revoke all
on function
public.safety_register_evidence_v1(
    uuid,
    uuid,
    text,
    text,
    text,
    bigint,
    timestamptz
)
from public;

grant execute
on function
public.safety_register_evidence_v1(
    uuid,
    uuid,
    text,
    text,
    text,
    bigint,
    timestamptz
)
to authenticated;


-- ============================================================
-- 12. Observatory query RPC
-- ============================================================

create or replace function
public.safety_observatory_query_v1(
    p_from timestamptz default null,
    p_to timestamptz default null,
    p_category text default null,
    p_country_code text default null,
    p_admin1_code text default null,
    p_admin2_code text default null
)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    with filtered_points as (
        select *
        from public.safety_public_points p
        where
            (
                p_from is null
                or p.published_at >= p_from
            )
            and
            (
                p_to is null
                or p.published_at < p_to
            )
            and
            (
                p_category is null
                or p.category =
                    p_category
            )
            and
            (
                p_country_code is null
                or p.country_code =
                    p_country_code
            )
            and
            (
                p_admin1_code is null
                or p.admin1_code =
                    p_admin1_code
            )
            and
            (
                p_admin2_code is null
                or p.admin2_code =
                    p_admin2_code
            )
    )
    select jsonb_build_object(
        'public_point_count',
            count(*),

        'independent_source_count',
            coalesce(
                sum(
                    independent_source_count
                ),
                0
            ),

        'civil_source_count',
            coalesce(
                sum(
                    civil_source_count
                ),
                0
            ),

        'journalistic_source_count',
            coalesce(
                sum(
                    journalistic_source_count
                ),
                0
            ),

        'public_record_source_count',
            coalesce(
                sum(
                    public_record_source_count
                ),
                0
            ),

        'documentary_source_count',
            coalesce(
                sum(
                    documentary_source_count
                ),
                0
            ),

        'institutional_source_count',
            coalesce(
                sum(
                    institutional_source_count
                ),
                0
            )
    )
    from filtered_points;
$$;

revoke all
on function
public.safety_observatory_query_v1(
    timestamptz,
    timestamptz,
    text,
    text,
    text,
    text
)
from public;

grant execute
on function
public.safety_observatory_query_v1(
    timestamptz,
    timestamptz,
    text,
    text,
    text,
    text
)
to authenticated;

commit;
