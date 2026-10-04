-- ═══════════════════════════════════════════════════════════════════
-- Elysium Safety Scientific — Public Projections (§53)
--
-- The user NEVER queries scientific_* tables directly for
-- the public experience. Only these projection views.
--
-- Private data (PII, exact coordinates, protected witnesses,
-- unredacted documents, legal holds) is NEVER exposed.
-- ═══════════════════════════════════════════════════════════════════

-- §53: Public Research Projection
CREATE OR REPLACE VIEW public.safety_public_research_projection AS
SELECT
    p.id,
    p.title,
    p.abstract_text,
    p.dataset_hash,
    p.methodology_hash,
    p.code_commit,
    p.evidence_manifest_hash,
    p.status,
    p.sensitivity,
    p.supersedes_publication_id,
    p.created_at,
    p.updated_at
FROM public.safety_scientific_publications p
WHERE p.status IN ('PUBLISHED', 'CORRECTED', 'RETRACTED');

-- §53: Public Claim Projection (sanitized)
CREATE OR REPLACE VIEW public.safety_public_claim_projection AS
SELECT
    c.id,
    c.proposition,
    c.predicate,
    c.assertion_state,
    c.causal_status,
    c.occurred_at,
    c.methodology_version,
    c.created_at
FROM public.safety_scientific_claims c
WHERE c.assertion_state NOT IN ('UNKNOWN', 'INSUFFICIENT_EVIDENCE');

-- §53: Public Replication Projection
CREATE OR REPLACE VIEW public.safety_public_replication_projection AS
SELECT
    r.id,
    r.original_run_id,
    r.dataset_version,
    r.methodology_version,
    r.result,
    r.created_at
FROM public.safety_scientific_replications r;

-- §53: Public Methodology Projection
CREATE OR REPLACE VIEW public.safety_public_methodology_projection AS
SELECT
    rr.id AS run_id,
    rr.methodology_version,
    rr.code_commit,
    rr.dataset_hash,
    rr.created_at,
    d.name AS dataset_name,
    d.version AS dataset_version
FROM public.safety_scientific_research_runs rr
LEFT JOIN public.safety_scientific_research_datasets d
    ON rr.dataset_id = d.id;

-- §42: Grant read on public projections to authenticated users
GRANT SELECT ON public.safety_public_research_projection TO authenticated;
GRANT SELECT ON public.safety_public_claim_projection TO authenticated;
GRANT SELECT ON public.safety_public_replication_projection TO authenticated;
GRANT SELECT ON public.safety_public_methodology_projection TO authenticated;

-- §42: Deny direct access to scientific tables for non-service roles
-- (Already enforced by RLS in the base migration, but explicit here)
REVOKE ALL ON public.safety_scientific_entities FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_claims FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_events FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_knowledge_events FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_authority_assertions FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_duty_assertions FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_accountability_actions FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_hypotheses FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_publications FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_replications FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_research_runs FROM anon, authenticated;
REVOKE ALL ON public.safety_scientific_research_datasets FROM anon, authenticated;
