-- ═══════════════════════════════════════════════════════════════════
-- BLOQUE 1 — Scientific Authority Infrastructure
-- Command outbox, evidence bridge, temporal integrity,
-- source lineage, case aggregate
-- ═══════════════════════════════════════════════════════════════════

-- 1. Evidence Authority Bridge (Phase 6)
CREATE TABLE IF NOT EXISTS public.safety_scientific_evidence_references (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scientific_object_id UUID NOT NULL,
    scientific_object_type TEXT NOT NULL,
    evidence_id UUID NOT NULL,
    relation TEXT NOT NULL CHECK (relation IN ('SUPPORTS', 'CONTRADICTS', 'CONTEXTUALIZES')),
    verification_state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (verification_state IN ('PENDING', 'VERIFIED', 'HASH_MISMATCH', 'WITHDRAWN', 'QUARANTINED')),
    evidence_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sci_evidence_refs_evidence
    ON public.safety_scientific_evidence_references (evidence_id);
CREATE INDEX IF NOT EXISTS idx_sci_evidence_refs_object
    ON public.safety_scientific_evidence_references (scientific_object_id);

-- Immutable after creation
CREATE TRIGGER trg_immutable_evidence_refs
    BEFORE UPDATE OR DELETE ON public.safety_scientific_evidence_references
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- RLS
ALTER TABLE public.safety_scientific_evidence_references ENABLE ROW LEVEL SECURITY;
CREATE POLICY evidence_refs_service_only ON public.safety_scientific_evidence_references
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON public.safety_scientific_evidence_references TO service_role;

-- 2. Temporal Integrity (Phase 7)
CREATE TABLE IF NOT EXISTS public.safety_scientific_temporal_integrity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL,
    subject_type TEXT NOT NULL,
    device_captured_at TIMESTAMPTZ,
    server_received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_verified_at TIMESTAMPTZ,
    clock_skew_ms BIGINT,
    temporal_state TEXT NOT NULL CHECK (temporal_state IN (
        'CONSISTENT', 'CLOCK_SKEW', 'FUTURE_DEVICE_TIME',
        'MISSING_DEVICE_TIME', 'SERVER_AUTHORITATIVE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sci_temporal_subject
    ON public.safety_scientific_temporal_integrity (subject_id);

ALTER TABLE public.safety_scientific_temporal_integrity ENABLE ROW LEVEL SECURITY;
CREATE POLICY temporal_service_only ON public.safety_scientific_temporal_integrity
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON public.safety_scientific_temporal_integrity TO service_role;

-- 3. Source Lineage (Phase 8)
CREATE TABLE IF NOT EXISTS public.safety_scientific_source_lineage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL,
    source_lineage_id UUID NOT NULL,
    source_instance_id UUID NOT NULL,
    derivation_parent_id UUID,
    derivation_type TEXT NOT NULL CHECK (derivation_type IN (
        'ORIGINAL', 'DERIVED', 'REPUBLICATION', 'INDEPENDENT_ACQUISITION', 'UNKNOWN'
    )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sci_lineage_lineage_id
    ON public.safety_scientific_source_lineage (source_lineage_id);
CREATE INDEX IF NOT EXISTS idx_sci_lineage_parent
    ON public.safety_scientific_source_lineage (derivation_parent_id);

-- Immutable
CREATE TRIGGER trg_immutable_source_lineage
    BEFORE UPDATE OR DELETE ON public.safety_scientific_source_lineage
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

ALTER TABLE public.safety_scientific_source_lineage ENABLE ROW LEVEL SECURITY;
CREATE POLICY lineage_service_only ON public.safety_scientific_source_lineage
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON public.safety_scientific_source_lineage TO service_role;

-- 4. Case Aggregate (Phase 9)
CREATE TABLE IF NOT EXISTS public.safety_scientific_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    jurisdiction TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN (
        'OPEN', 'EVIDENCE_COLLECTION', 'ANALYSIS', 'REPLICATION',
        'UNDER_REVIEW', 'PUBLICATION_ELIGIBLE', 'PUBLISHED',
        'REFERRED', 'CLOSED', 'DISPUTED'
    )),
    sensitivity TEXT NOT NULL DEFAULT 'NORMAL' CHECK (sensitivity IN (
        'NORMAL', 'SENSITIVE', 'HIGH_IMPACT', 'LEGAL_HOLD'
    )),
    evidence_count INTEGER NOT NULL DEFAULT 0,
    claim_count INTEGER NOT NULL DEFAULT 0,
    hypothesis_count INTEGER NOT NULL DEFAULT 0,
    research_run_count INTEGER NOT NULL DEFAULT 0,
    replication_count INTEGER NOT NULL DEFAULT 0,
    publication_status TEXT,
    legal_referral_status TEXT NOT NULL DEFAULT 'NOT_REFERRED' CHECK (legal_referral_status IN (
        'NOT_REFERRED', 'REFERRED', 'ACKNOWLEDGED', 'IN_REVIEW'
    )),
    server_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sci_cases_status
    ON public.safety_scientific_cases (status);

ALTER TABLE public.safety_scientific_cases ENABLE ROW LEVEL SECURITY;
CREATE POLICY cases_service_only ON public.safety_scientific_cases
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_cases TO service_role;

-- 5. Case Items (Phase 9)
CREATE TABLE IF NOT EXISTS public.safety_scientific_case_items (
    case_id UUID NOT NULL REFERENCES public.safety_scientific_cases(id),
    item_id UUID NOT NULL,
    item_type TEXT NOT NULL CHECK (item_type IN (
        'EVIDENCE', 'CLAIM', 'EVENT', 'HYPOTHESIS', 'ENTITY',
        'RESEARCH_RUN', 'REPLICATION', 'PUBLICATION'
    )),
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, item_id, item_type)
);

-- Immutable
CREATE TRIGGER trg_immutable_case_items
    BEFORE UPDATE OR DELETE ON public.safety_scientific_case_items
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

ALTER TABLE public.safety_scientific_case_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY case_items_service_only ON public.safety_scientific_case_items
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON public.safety_scientific_case_items TO service_role;

-- 6. Witness Checkpoints (Phase 16)
CREATE TABLE IF NOT EXISTS public.safety_scientific_witness_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkpoint_id UUID NOT NULL REFERENCES public.safety_scientific_checkpoints(checkpoint_id),
    witness_name TEXT NOT NULL,
    witness_type TEXT NOT NULL CHECK (witness_type IN (
        'UNIVERSITY', 'SCIENTIFIC_ORGANIZATION', 'NGO',
        'INDEPENDENT_THIRD_PARTY', 'PUBLIC_REPOSITORY', 'TIMESTAMP_AUTHORITY'
    )),
    witness_hash TEXT NOT NULL,
    external_timestamp TIMESTAMPTZ,
    external_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Immutable
CREATE TRIGGER trg_immutable_witness_records
    BEFORE UPDATE OR DELETE ON public.safety_scientific_witness_records
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

ALTER TABLE public.safety_scientific_witness_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY witness_service_only ON public.safety_scientific_witness_records
    USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON public.safety_scientific_witness_records TO service_role;

-- ═══════════════════════════════════════════════════════════════════
-- Phase 13 — AI HARD BOUNDARY in PostgreSQL
--
-- Reject AI actor transitions to elevated states.
-- This is redundant with the RPC but adds defense-in-depth.
-- ═══════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.safety_scientific_ai_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_elevated TEXT[] := ARRAY[
        'AUTHORITATIVE', 'CORROBORATED',
        'STATISTICALLY_SUPPORTED', 'CAUSALLY_SUPPORTED',
        'PEER_REVIEWED', 'INDEPENDENTLY_REPLICATED'
    ];
BEGIN
    IF NEW.actor_is_ai = TRUE AND NEW.to_state = ANY(v_elevated) THEN
        RAISE EXCEPTION 'AI_ELEVATION_BLOCKED: AI cannot transition to %', NEW.to_state;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ai_guard_state_transitions
    BEFORE INSERT ON public.safety_scientific_state_transitions
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_ai_guard();
