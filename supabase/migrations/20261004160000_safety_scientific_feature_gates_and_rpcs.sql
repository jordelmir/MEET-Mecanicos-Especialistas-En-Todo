-- ═══════════════════════════════════════════════════════════════════
-- Phase 14 — REMOTE FEATURE GATES
--
-- Server-controlled kill switches for Safety Science features.
-- A disabled server gate OVERRIDES local cache.
-- ═══════════════════════════════════════════════════════════════════

BEGIN;

CREATE TABLE IF NOT EXISTS public.safety_scientific_feature_gates (
    gate_name TEXT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT false,
    reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);

-- Seed all gates as disabled (conservative default)
INSERT INTO public.safety_scientific_feature_gates (gate_name, enabled, reason) VALUES
    ('SAFETY_SCIENCE', false, 'Master gate — must be enabled before any sub-feature'),
    ('SAFETY_SCIENCE_CLAIMS', false, 'Claim creation and state transitions'),
    ('SAFETY_SCIENCE_RESEARCH', false, 'Research datasets, runs, analysis'),
    ('SAFETY_SCIENCE_REPLICATION', false, 'Independent replication studies'),
    ('SAFETY_SCIENCE_PUBLICATION', false, 'Publication request and review workflow'),
    ('SAFETY_SCIENCE_AI', false, 'AI-assisted analysis (always guarded)'),
    ('SAFETY_SCIENCE_CHECKPOINT', false, 'Cryptographic checkpoints'),
    ('SAFETY_SCIENCE_PUBLIC_PROJECTION', false, 'Public-facing projections')
ON CONFLICT (gate_name) DO NOTHING;

ALTER TABLE public.safety_scientific_feature_gates ENABLE ROW LEVEL SECURITY;

-- Only service_role can modify gates (admin only)
CREATE POLICY gates_read_all ON public.safety_scientific_feature_gates
    FOR SELECT USING (true);
CREATE POLICY gates_write_service ON public.safety_scientific_feature_gates
    FOR ALL USING (true) WITH CHECK (true);

GRANT SELECT ON public.safety_scientific_feature_gates TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.safety_scientific_feature_gates TO service_role;

-- ═══════════════════════════════════════════════════════════════════
-- Phase 16 — WITNESS CHECKPOINT RPCs
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE public.safety_scientific_checkpoints ADD COLUMN IF NOT EXISTS created_by UUID;

CREATE OR REPLACE FUNCTION public.safety_scientific_create_checkpoint_v1(
    p_idempotency_key UUID,
    p_root_hash TEXT,
    p_event_count INTEGER,
    p_first_event_hash TEXT DEFAULT NULL,
    p_last_event_hash TEXT DEFAULT NULL,
    p_signature_algorithm TEXT DEFAULT 'Ed25519',
    p_signature_base64 TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_checkpoint_id UUID;
    v_actor_id UUID;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;

    -- Idempotency
    IF EXISTS (SELECT 1 FROM public.safety_scientific_checkpoints WHERE checkpoint_id = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'ALREADY_EXISTS', 'checkpointId', p_idempotency_key, 'serverVersion', 0);
    END IF;

    v_checkpoint_id := p_idempotency_key;

    INSERT INTO public.safety_scientific_checkpoints (
        checkpoint_id, root_hash, event_count, first_event_hash, last_event_hash,
        signature_algorithm, signature_base64, created_by, created_at
    ) VALUES (
        v_checkpoint_id, p_root_hash, p_event_count,
        p_first_event_hash, p_last_event_hash,
        p_signature_algorithm, p_signature_base64,
        v_actor_id, now()
    );

    RETURN jsonb_build_object(
        'status', 'CREATED',
        'checkpointId', v_checkpoint_id,
        'serverVersion', 1
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.safety_scientific_create_checkpoint_v1 TO authenticated;

-- Generic entity creation RPC
CREATE OR REPLACE FUNCTION public.safety_scientific_create_entity_v1(
    p_idempotency_key UUID,
    p_entity_type TEXT,
    p_canonical_name TEXT,
    p_aliases TEXT[] DEFAULT '{}'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;

    IF EXISTS (SELECT 1 FROM public.safety_scientific_entities WHERE id = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'ALREADY_EXISTS', 'entityId', p_idempotency_key, 'serverVersion', 0);
    END IF;

    INSERT INTO public.safety_scientific_entities (
        id, entity_type, canonical_name, aliases, created_by, created_at
    ) VALUES (
        p_idempotency_key, p_entity_type, p_canonical_name, p_aliases,
        v_actor_id, now()
    );

    RETURN jsonb_build_object(
        'status', 'CREATED',
        'entityId', p_idempotency_key,
        'serverVersion', 1
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.safety_scientific_create_entity_v1 TO authenticated;

-- Claim creation RPC
CREATE OR REPLACE FUNCTION public.safety_scientific_create_claim_v1(
    p_idempotency_key UUID,
    p_proposition TEXT,
    p_predicate TEXT,
    p_subject_entity_id UUID DEFAULT NULL,
    p_object_entity_id UUID DEFAULT NULL,
    p_occurred_at BIGINT DEFAULT NULL,
    p_known_at BIGINT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;

    IF EXISTS (SELECT 1 FROM public.safety_scientific_claims WHERE id = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'ALREADY_EXISTS', 'claimId', p_idempotency_key, 'serverVersion', 0);
    END IF;

    INSERT INTO public.safety_scientific_claims (
        id, proposition, predicate, subject_entity_id, object_entity_id,
        assertion_state, server_version, created_by, created_at
    ) VALUES (
        p_idempotency_key, p_proposition, p_predicate,
        p_subject_entity_id, p_object_entity_id,
        'OBSERVED', 0, v_actor_id, now()
    );

    RETURN jsonb_build_object(
        'status', 'CREATED',
        'claimId', p_idempotency_key,
        'serverVersion', 0
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.safety_scientific_create_claim_v1 TO authenticated;

-- Evidence attachment RPC
CREATE OR REPLACE FUNCTION public.safety_scientific_attach_evidence_v1(
    p_idempotency_key UUID,
    p_claim_id UUID,
    p_evidence_id UUID,
    p_relation_type TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;

    IF p_relation_type NOT IN ('SUPPORTS', 'CONTRADICTS', 'CONTEXTUALIZES') THEN
        RAISE EXCEPTION 'INVALID_RELATION_TYPE: %', p_relation_type;
    END IF;

    IF EXISTS (SELECT 1 FROM public.safety_scientific_evidence_references WHERE id = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'ALREADY_EXISTS', 'serverId', p_idempotency_key, 'serverVersion', 0);
    END IF;

    -- Verify evidence exists
    IF NOT EXISTS (SELECT 1 FROM public.safety_evidence_objects WHERE id = p_evidence_id) THEN
        RAISE EXCEPTION 'EVIDENCE_NOT_FOUND: %', p_evidence_id;
    END IF;

    INSERT INTO public.safety_scientific_evidence_references (
        id, scientific_object_id, scientific_object_type,
        evidence_id, relation, verification_state, created_at
    ) VALUES (
        p_idempotency_key, p_claim_id, 'CLAIM',
        p_evidence_id, p_relation_type, 'PENDING', now()
    );

    RETURN jsonb_build_object(
        'status', 'CREATED',
        'serverId', p_idempotency_key,
        'serverVersion', 1
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.safety_scientific_attach_evidence_v1 TO authenticated;

-- Hypothesis creation RPC
CREATE OR REPLACE FUNCTION public.safety_scientific_create_hypothesis_v1(
    p_idempotency_key UUID,
    p_proposition TEXT,
    p_null_hypothesis TEXT DEFAULT NULL,
    p_falsification_criteria TEXT[] DEFAULT '{}'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED';
    END IF;

    IF EXISTS (SELECT 1 FROM public.safety_scientific_hypotheses WHERE id = p_idempotency_key) THEN
        RETURN jsonb_build_object('status', 'ALREADY_EXISTS', 'hypothesisId', p_idempotency_key, 'serverVersion', 0);
    END IF;

    INSERT INTO public.safety_scientific_hypotheses (
        id, proposition, null_hypothesis, falsification_criteria,
        status, created_by, created_at
    ) VALUES (
        p_idempotency_key, p_proposition, p_null_hypothesis,
        p_falsification_criteria, 'PROPOSED', v_actor_id, now()
    );

    RETURN jsonb_build_object(
        'status', 'CREATED',
        'hypothesisId', p_idempotency_key,
        'serverVersion', 1
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.safety_scientific_create_hypothesis_v1 TO authenticated;

COMMIT;
