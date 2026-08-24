-- =============================================================================
-- Migration: 20260824000000_human_capability_core.sql
-- Description: Human Capability Platform Core (Humanity OS Foundation)
-- Tables: knowledge domains, nodes, skills, missions, progress, evidence, capabilities
-- Security: Strict fail-closed RLS; public knowledge reads, private user progress,
--           authoritative capability transitions via RPC only.
-- =============================================================================

-- 1. KNOWLEDGE DOMAINS
create table if not exists public.humanity_knowledge_domains (
    id text primary key,
    name text not null,
    description text not null,
    parent_domain_id text references public.humanity_knowledge_domains(id),
    icon_glyph text not null default '⚙',
    created_at timestamptz not null default now()
);

alter table public.humanity_knowledge_domains enable row level security;

create policy "Allow public read on knowledge domains"
    on public.humanity_knowledge_domains
    for select
    using (true);

-- 2. KNOWLEDGE NODES
create table if not exists public.humanity_knowledge_nodes (
    id text primary key,
    domain_id text not null references public.humanity_knowledge_domains(id),
    title text not null,
    summary text not null,
    truth_state text not null default 'AUTHORITATIVE',
    safety_level text not null default 'KNOWLEDGE_ONLY',
    prerequisite_node_ids text[] not null default '{}',
    enables_skill_ids text[] not null default '{}',
    sources jsonb not null default '[]',
    version text not null default '1.0.0',
    updated_at timestamptz not null default now()
);

alter table public.humanity_knowledge_nodes enable row level security;

create policy "Allow public read on knowledge nodes"
    on public.humanity_knowledge_nodes
    for select
    using (true);

-- 3. SKILLS
create table if not exists public.humanity_skills (
    id text primary key,
    domain_id text not null references public.humanity_knowledge_domains(id),
    name text not null,
    description text not null,
    required_knowledge_ids text[] not null default '{}',
    prerequisite_skill_ids text[] not null default '{}',
    safety_level text not null default 'LOW_RISK_PRACTICE',
    minimum_evidence_for_mastery int not null default 3,
    created_at timestamptz not null default now()
);

alter table public.humanity_skills enable row level security;

create policy "Allow public read on skills"
    on public.humanity_skills
    for select
    using (true);

-- 4. MISSIONS
create table if not exists public.humanity_missions (
    id text primary key,
    domain_id text not null references public.humanity_knowledge_domains(id),
    title text not null,
    goal text not null,
    required_skill_ids text[] not null default '{}',
    target_object_types text[] not null default '{}',
    safety_level text not null default 'LOW_RISK_PRACTICE',
    steps jsonb not null default '[]',
    created_at timestamptz not null default now()
);

alter table public.humanity_missions enable row level security;

create policy "Allow public read on missions"
    on public.humanity_missions
    for select
    using (true);

-- 5. LEARNING PROGRESS (User Private)
create table if not exists public.humanity_learning_progress (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    curriculum_type text not null,
    progress_data jsonb not null default '{}',
    updated_at timestamptz not null default now(),
    unique (user_id, curriculum_type)
);

alter table public.humanity_learning_progress enable row level security;

create policy "Users read own learning progress"
    on public.humanity_learning_progress
    for select
    using (auth.uid() = user_id);

create policy "Users insert own learning progress"
    on public.humanity_learning_progress
    for insert
    with check (auth.uid() = user_id);

create policy "Users update own learning progress"
    on public.humanity_learning_progress
    for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- 6. EVIDENCE ITEMS (User Private & Verifiable)
create table if not exists public.humanity_evidence_items (
    id text primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    skill_id text not null references public.humanity_skills(id),
    mission_id text references public.humanity_missions(id),
    evidence_type text not null,
    execution_truth text not null,
    evidence_payload_hash text not null,
    metadata jsonb not null default '{}',
    captured_at timestamptz not null default now()
);

alter table public.humanity_evidence_items enable row level security;

create policy "Users read own evidence items"
    on public.humanity_evidence_items
    for select
    using (auth.uid() = user_id);

create policy "Users submit own evidence items"
    on public.humanity_evidence_items
    for insert
    with check (auth.uid() = user_id);

-- 7. CAPABILITY RECORDS (Passport / Verifiable Skill Level)
create table if not exists public.humanity_capability_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    skill_id text not null references public.humanity_skills(id),
    current_level text not null default 'L0_UNKNOWN',
    demonstrated_evidence_count int not null default 0,
    last_demonstrated_at timestamptz,
    verified_by_expert boolean not null default false,
    updated_at timestamptz not null default now(),
    unique (user_id, skill_id)
);

alter table public.humanity_capability_records enable row level security;

create policy "Users read own capability records"
    on public.humanity_capability_records
    for select
    using (auth.uid() = user_id);

-- 8. AUTHORITATIVE CAPABILITY TRANSITION RPC
create or replace function public.transition_user_capability_v1(
    p_skill_id text,
    p_target_level text,
    p_evidence_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_actor_id uuid;
    v_evidence_record record;
    v_current_record record;
    v_evidence_count int;
begin
    v_actor_id := auth.uid();
    if v_actor_id is null then
        return jsonb_build_object('success', false, 'error', 'UNAUTHORIZED');
    end if;

    -- Verify that the evidence exists and belongs to the actor
    select * into v_evidence_record
    from public.humanity_evidence_items
    where id = p_evidence_id and user_id = v_actor_id and skill_id = p_skill_id;

    if not found then
        return jsonb_build_object('success', false, 'error', 'EVIDENCE_NOT_FOUND_OR_UNOWNED');
    end if;

    -- Count valid evidence for this skill
    select count(*) into v_evidence_count
    from public.humanity_evidence_items
    where user_id = v_actor_id and skill_id = p_skill_id;

    -- Upsert capability record
    insert into public.humanity_capability_records (
        user_id,
        skill_id,
        current_level,
        demonstrated_evidence_count,
        last_demonstrated_at,
        updated_at
    )
    values (
        v_actor_id,
        p_skill_id,
        p_target_level,
        v_evidence_count,
        now(),
        now()
    )
    on conflict (user_id, skill_id) do update set
        current_level = p_target_level,
        demonstrated_evidence_count = v_evidence_count,
        last_demonstrated_at = now(),
        updated_at = now();

    return jsonb_build_object(
        'success', true,
        'skill_id', p_skill_id,
        'new_level', p_target_level,
        'evidence_count', v_evidence_count
    );
end;
$$;

revoke execute on function public.transition_user_capability_v1(text, text, text) from public;
grant execute on function public.transition_user_capability_v1(text, text, text) to authenticated;
