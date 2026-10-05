-- ============================================================================
-- Elysium Safety Scientific Core v1 — Migration 001: Scientific Entities
-- ============================================================================
-- Part of P0 (Integrity Core) + P1 (Knowledge Graph).
-- Does NOT touch any existing safety_* table.
-- All tables use RLS with service_role only — public access via projections.
-- ============================================================================

BEGIN;

-- ── 001: Scientific Entities ─────────────────────────────────────────────────

create table if not exists public.safety_scientific_entities (
    id uuid primary key default gen_random_uuid(),

    entity_type text not null
        check (
            entity_type in (
                'PERSON',
                'ORGANIZATION',
                'GOVERNMENT_INSTITUTION',
                'JUDICIAL_BODY',
                'COURT',
                'PROSECUTORIAL_BODY',
                'POLICE_BODY',
                'OFFICE',
                'POSITION',
                'BUSINESS',
                'LOCATION',
                'DOCUMENT',
                'CASE',
                'VEHICLE',
                'PHONE',
                'EMAIL',
                'EVENT',
                'UNKNOWN'
            )
        ),

    canonical_name text not null,
    aliases jsonb not null default '[]'::jsonb,
    external_identifiers jsonb not null default '[]'::jsonb,

    assertion_state text not null default 'UNKNOWN'
        check (
            assertion_state in (
                'OBSERVED', 'DOCUMENTED', 'AUTHORITATIVE', 'CORROBORATED',
                'DERIVED', 'STATISTICALLY_SUPPORTED', 'CAUSALLY_SUPPORTED',
                'PEER_REVIEWED', 'INDEPENDENTLY_REPLICATED',
                'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE', 'UNKNOWN'
            )
        ),

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_safety_scientific_entities_name
    on public.safety_scientific_entities(lower(canonical_name));
create index if not exists idx_safety_scientific_entities_type
    on public.safety_scientific_entities(entity_type);

-- ── 002: Entity Relations ───────────────────────────────────────────────────

create table if not exists public.safety_scientific_entity_relations (
    id uuid primary key default gen_random_uuid(),

    subject_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    relation_type text not null,
    object_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,

    valid_from timestamptz,
    valid_until timestamptz,

    supporting_evidence_ids jsonb not null default '[]'::jsonb,
    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

create index if not exists idx_ssr_subject on public.safety_scientific_entity_relations(subject_id);
create index if not exists idx_ssr_object on public.safety_scientific_entity_relations(object_id);

-- ── 003: Scientific Claims ──────────────────────────────────────────────────

create table if not exists public.safety_scientific_claims (
    id uuid primary key default gen_random_uuid(),

    proposition text not null,

    subject_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,
    predicate text not null,
    object_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,

    occurred_at timestamptz,
    known_at timestamptz,
    valid_from timestamptz,
    valid_until timestamptz,

    assertion_state text not null default 'UNKNOWN',
    causal_status text not null default 'NOT_ASSESSED',
    methodology_version text not null default 'safety-science-v1',

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_ssc_subject on public.safety_scientific_claims(subject_entity_id);
create index if not exists idx_ssc_object on public.safety_scientific_claims(object_entity_id);

-- ── 004: Claim ↔ Evidence ───────────────────────────────────────────────────

create table if not exists public.safety_scientific_claim_evidence (
    claim_id uuid not null
        references public.safety_scientific_claims(id) on delete restrict,
    evidence_id uuid not null
        references public.safety_evidence_objects(id) on delete restrict,

    relation_type text not null
        check (relation_type in ('SUPPORTS', 'CONTRADICTS', 'CONTEXTUALIZES')),

    created_at timestamptz not null default now(),

    primary key (claim_id, evidence_id)
);

-- ── 005: Claim ↔ Claim Relations ────────────────────────────────────────────

create table if not exists public.safety_scientific_claim_relations (
    id uuid primary key default gen_random_uuid(),

    source_claim_id uuid not null
        references public.safety_scientific_claims(id) on delete restrict,
    target_claim_id uuid not null
        references public.safety_scientific_claims(id) on delete restrict,

    relation_type text not null
        check (relation_type in (
            'SUPPORTS', 'CONTRADICTS', 'DEPENDS_ON', 'QUALIFIES', 'SUPERSEDES'
        )),

    created_at timestamptz not null default now()
);

-- ── 006: Scientific Events ──────────────────────────────────────────────────

create table if not exists public.safety_scientific_events (
    id uuid primary key default gen_random_uuid(),

    event_type text not null,

    occurred_at timestamptz,
    known_at timestamptz,
    recorded_at timestamptz not null default now(),
    published_at timestamptz,
    verified_at timestamptz,

    actor_entity_ids jsonb not null default '[]'::jsonb,
    location_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,

    evidence_ids jsonb not null default '[]'::jsonb,
    claim_ids jsonb not null default '[]'::jsonb,

    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

create index if not exists idx_sse_occurred on public.safety_scientific_events(occurred_at);
create index if not exists idx_sse_type on public.safety_scientific_events(event_type);

-- ── 007: Knowledge Events ───────────────────────────────────────────────────

create table if not exists public.safety_scientific_knowledge_events (
    id uuid primary key default gen_random_uuid(),

    actor_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    information_claim_id uuid not null
        references public.safety_scientific_claims(id) on delete restrict,

    received_at timestamptz not null,
    channel text not null,

    source_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,
    authority_context_id uuid
        references public.safety_scientific_entities(id) on delete restrict,

    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

create index if not exists idx_sske_actor on public.safety_scientific_knowledge_events(actor_entity_id);
create index if not exists idx_sske_received on public.safety_scientific_knowledge_events(received_at);

-- ── 008: Authority Assertions ───────────────────────────────────────────────

create table if not exists public.safety_scientific_authority_assertions (
    id uuid primary key default gen_random_uuid(),

    actor_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    authority_type text not null,

    jurisdiction_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,

    valid_from timestamptz,
    valid_until timestamptz,

    source_evidence_ids jsonb not null default '[]'::jsonb,
    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

-- ── 009: Duty Assertions ────────────────────────────────────────────────────

create table if not exists public.safety_scientific_duty_assertions (
    id uuid primary key default gen_random_uuid(),

    actor_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    duty_type text not null,

    jurisdiction_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,

    valid_from timestamptz,
    valid_until timestamptz,

    legal_source_evidence_ids jsonb not null default '[]'::jsonb,
    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

-- ── 010: Accountability Actions ─────────────────────────────────────────────

create table if not exists public.safety_scientific_accountability_actions (
    id uuid primary key default gen_random_uuid(),

    actor_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,

    action_kind text not null
        check (action_kind in ('ACTION', 'NON_ACTION')),

    action_type text not null,
    expected_action text,

    occurred_at timestamptz not null,

    evidence_ids jsonb not null default '[]'::jsonb,
    assertion_state text not null default 'UNKNOWN',

    created_at timestamptz not null default now()
);

-- ── 011: Hypotheses ─────────────────────────────────────────────────────────

create table if not exists public.safety_scientific_hypotheses (
    id uuid primary key default gen_random_uuid(),

    proposition text not null,
    null_hypothesis text,

    supporting_evidence_ids jsonb not null default '[]'::jsonb,
    contradicting_evidence_ids jsonb not null default '[]'::jsonb,
    alternative_hypothesis_ids jsonb not null default '[]'::jsonb,

    falsification_criteria jsonb not null default '[]'::jsonb,

    status text not null default 'PROPOSED'
        check (status in (
            'PROPOSED', 'TESTING', 'SUPPORTED', 'WEAKLY_SUPPORTED',
            'DISPUTED', 'REFUTED', 'INCONCLUSIVE'
        )),

    methodology_version text not null default 'safety-science-v1',

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- ── 012: Provenance Graph ───────────────────────────────────────────────────

create table if not exists public.safety_scientific_provenance_nodes (
    id uuid primary key default gen_random_uuid(),
    node_type text not null,
    content_hash text,
    created_at timestamptz not null default now()
);

create table if not exists public.safety_scientific_provenance_edges (
    from_id uuid not null
        references public.safety_scientific_provenance_nodes(id) on delete restrict,
    to_id uuid not null
        references public.safety_scientific_provenance_nodes(id) on delete restrict,
    relation text not null,
    created_at timestamptz not null default now(),
    primary key (from_id, to_id, relation)
);

-- ── 013: Research Datasets ──────────────────────────────────────────────────

create table if not exists public.safety_scientific_research_datasets (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    version text not null,
    evidence_ids jsonb not null default '[]'::jsonb,
    claim_ids jsonb not null default '[]'::jsonb,
    event_ids jsonb not null default '[]'::jsonb,
    dataset_hash text not null,
    methodology_version text not null default 'safety-science-v1',
    created_at timestamptz not null default now()
);

-- ── 014: Research Runs ──────────────────────────────────────────────────────

create table if not exists public.safety_scientific_research_runs (
    id uuid primary key default gen_random_uuid(),
    dataset_id uuid not null
        references public.safety_scientific_research_datasets(id) on delete restrict,
    dataset_hash text not null,
    methodology_version text not null,
    code_commit text not null,
    parameters jsonb not null default '{}'::jsonb,
    result_artifact_hash text not null,
    bias_assessment jsonb,
    created_at timestamptz not null default now()
);

-- ── 015: Replications ───────────────────────────────────────────────────────

create table if not exists public.safety_scientific_replications (
    id uuid primary key default gen_random_uuid(),
    original_run_id uuid not null
        references public.safety_scientific_research_runs(id) on delete restrict,
    replicator_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    institution_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,
    dataset_version text not null,
    methodology_version text not null,
    independent_dataset_hash text,
    result text not null
        check (result in (
            'SUCCESSFUL', 'PARTIAL', 'FAILED', 'INCONCLUSIVE', 'NOT_REPRODUCIBLE'
        )),
    deviations jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now()
);

-- ── 016: Peer Reviews ───────────────────────────────────────────────────────

create table if not exists public.safety_scientific_peer_reviews (
    id uuid primary key default gen_random_uuid(),
    publication_id uuid not null,  -- FK added after publications table
    reviewer_entity_id uuid not null
        references public.safety_scientific_entities(id) on delete restrict,
    methodology_reviewed boolean not null default false,
    evidence_reviewed boolean not null default false,
    analysis_reviewed boolean not null default false,
    provenance_reviewed boolean not null default false,
    decision text not null
        check (decision in (
            'ACCEPT', 'MINOR_REVISION', 'MAJOR_REVISION', 'REJECT', 'CONDITIONAL'
        )),
    conflict_of_interest_declared boolean not null default false,
    created_at timestamptz not null default now()
);

-- ── 017: Publications ───────────────────────────────────────────────────────

create table if not exists public.safety_scientific_publications (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    abstract_text text not null,
    research_run_id uuid not null
        references public.safety_scientific_research_runs(id) on delete restrict,
    dataset_hash text not null,
    methodology_hash text not null,
    code_commit text not null,
    evidence_manifest_hash text not null,
    limitations jsonb not null default '[]'::jsonb,
    sensitivity text not null default 'NORMAL',
    supersedes_publication_id uuid
        references public.safety_scientific_publications(id) on delete restrict,
    status text not null default 'DRAFT'
        check (status in (
            'DRAFT', 'UNDER_REVIEW', 'PEER_REVIEWED', 'PUBLISHED',
            'RETRACTED', 'CORRECTED'
        )),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Now add the FK from peer_reviews → publications
alter table public.safety_scientific_peer_reviews
    add constraint fk_peer_review_publication
    foreign key (publication_id)
    references public.safety_scientific_publications(id)
    on delete restrict;

-- ── 018: Checkpoints ────────────────────────────────────────────────────────

create table if not exists public.safety_scientific_checkpoints (
    checkpoint_id uuid primary key default gen_random_uuid(),
    root_hash text not null,
    event_count bigint not null,
    first_event_hash text,
    last_event_hash text,
    signature_algorithm text not null,
    signature_base64 text not null,
    created_at timestamptz not null default now()
);

-- ── 019: Checkpoint Witnesses ───────────────────────────────────────────────

create table if not exists public.safety_scientific_checkpoint_witnesses (
    id uuid primary key default gen_random_uuid(),
    checkpoint_id uuid not null
        references public.safety_scientific_checkpoints(checkpoint_id) on delete restrict,
    witness_entity_id uuid
        references public.safety_scientific_entities(id) on delete restrict,
    witness_endpoint text,
    witness_hash text not null,
    witnessed_at timestamptz not null default now()
);

-- ── 020: Assertion State Transitions (audit log) ────────────────────────────

create table if not exists public.safety_scientific_state_transitions (
    id uuid primary key default gen_random_uuid(),
    subject_id uuid not null,
    from_state text not null,
    to_state text not null,
    reason text not null,
    evidence_ids jsonb not null default '[]'::jsonb,
    actor_id uuid not null,
    actor_is_ai boolean not null default false,
    methodology_version text not null,
    occurred_at timestamptz not null default now()
);

create index if not exists idx_sst_subject on public.safety_scientific_state_transitions(subject_id);
create index if not exists idx_sst_actor on public.safety_scientific_state_transitions(actor_id);

-- ══════════════════════════════════════════════════════════════════════════════
-- RLS — ALL scientific tables locked to service_role only.
-- Public access EXCLUSIVELY via projection views (created separately).
-- ══════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.safety_scientific_entities ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_entities FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_entities TO service_role;

ALTER TABLE public.safety_scientific_entity_relations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_entity_relations FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_entity_relations TO service_role;

ALTER TABLE public.safety_scientific_claims ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_claims FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_claims TO service_role;

ALTER TABLE public.safety_scientific_claim_evidence ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_claim_evidence FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_claim_evidence TO service_role;

ALTER TABLE public.safety_scientific_claim_relations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_claim_relations FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_claim_relations TO service_role;

ALTER TABLE public.safety_scientific_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_events FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_events TO service_role;

ALTER TABLE public.safety_scientific_knowledge_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_knowledge_events FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_knowledge_events TO service_role;

ALTER TABLE public.safety_scientific_authority_assertions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_authority_assertions FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_authority_assertions TO service_role;

ALTER TABLE public.safety_scientific_duty_assertions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_duty_assertions FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_duty_assertions TO service_role;

ALTER TABLE public.safety_scientific_accountability_actions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_accountability_actions FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_accountability_actions TO service_role;

ALTER TABLE public.safety_scientific_hypotheses ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_hypotheses FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_hypotheses TO service_role;

ALTER TABLE public.safety_scientific_provenance_nodes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_provenance_nodes FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_provenance_nodes TO service_role;

ALTER TABLE public.safety_scientific_provenance_edges ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_provenance_edges FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_provenance_edges TO service_role;

ALTER TABLE public.safety_scientific_research_datasets ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_research_datasets FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_research_datasets TO service_role;

ALTER TABLE public.safety_scientific_research_runs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_research_runs FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_research_runs TO service_role;

ALTER TABLE public.safety_scientific_replications ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_replications FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_replications TO service_role;

ALTER TABLE public.safety_scientific_peer_reviews ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_peer_reviews FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_peer_reviews TO service_role;

ALTER TABLE public.safety_scientific_publications ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_publications FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_publications TO service_role;

ALTER TABLE public.safety_scientific_checkpoints ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_checkpoints FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_checkpoints TO service_role;

ALTER TABLE public.safety_scientific_checkpoint_witnesses ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_checkpoint_witnesses FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_checkpoint_witnesses TO service_role;

ALTER TABLE public.safety_scientific_state_transitions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.safety_scientific_state_transitions FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_state_transitions TO service_role;

COMMIT;
