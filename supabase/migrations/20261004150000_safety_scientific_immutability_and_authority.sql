-- ═══════════════════════════════════════════════════════════════════
-- BLOQUE 3 — IMMUTABILITY ENFORCEMENT
--
-- Scientific historical records are APPEND-ONLY.
-- PostgreSQL MUST reject UPDATE/DELETE on audit tables.
-- Corrections = new version. Withdrawal = new event.
-- ═══════════════════════════════════════════════════════════════════

-- Immutable guard function
CREATE OR REPLACE FUNCTION public.safety_scientific_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'IMMUTABLE_SCIENTIFIC_RECORD: % on % is forbidden. '
        'Corrections must be new records, not mutations.',
        TG_OP, TG_TABLE_NAME;
    RETURN NULL;
END;
$$;

-- ── Apply to all append-only scientific tables ─────────────────

-- State transitions (audit log — NEVER mutable)
CREATE TRIGGER trg_immutable_state_transitions
    BEFORE UPDATE OR DELETE ON public.safety_scientific_state_transitions
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Custody checkpoints (signed snapshots)
CREATE TRIGGER trg_immutable_checkpoints
    BEFORE UPDATE OR DELETE ON public.safety_scientific_checkpoints
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Research datasets (frozen at creation)
CREATE TRIGGER trg_immutable_research_datasets
    BEFORE UPDATE OR DELETE ON public.safety_scientific_research_datasets
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Research runs (frozen result)
CREATE TRIGGER trg_immutable_research_runs
    BEFORE UPDATE OR DELETE ON public.safety_scientific_research_runs
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Replications (external validation — never alterable)
CREATE TRIGGER trg_immutable_replications
    BEFORE UPDATE OR DELETE ON public.safety_scientific_replications
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Provenance edges (DAG is append-only)
CREATE TRIGGER trg_immutable_provenance_edges
    BEFORE UPDATE OR DELETE ON public.safety_scientific_provenance_edges
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Provenance nodes
CREATE TRIGGER trg_immutable_provenance_nodes
    BEFORE UPDATE OR DELETE ON public.safety_scientific_provenance_nodes
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Knowledge events (who knew what when — append-only)
CREATE TRIGGER trg_immutable_knowledge_events
    BEFORE UPDATE OR DELETE ON public.safety_scientific_knowledge_events
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- Accountability actions (documented actions/non-actions)
CREATE TRIGGER trg_immutable_accountability_actions
    BEFORE UPDATE OR DELETE ON public.safety_scientific_accountability_actions
    FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_immutable();

-- ═══════════════════════════════════════════════════════════════════
-- BLOQUE 5 — SERVER STATE MACHINE
--
-- The Kotlin AssertionStateMachine is local protection.
-- PostgreSQL is the FINAL AUTHORITY.
-- An attacker who bypasses Android MUST still fail here.
-- ═══════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.safety_scientific_transition_claim_v1(
    p_claim_id UUID,
    p_to_state TEXT,
    p_reason TEXT,
    p_evidence_ids UUID[],
    p_actor_is_ai BOOLEAN DEFAULT FALSE,
    p_methodology_version TEXT DEFAULT 'safety-science-v1',
    p_expected_version BIGINT DEFAULT NULL,
    p_idempotency_key UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_actor_id UUID;
    v_current_state TEXT;
    v_current_version BIGINT;
    v_transition_id UUID;
    v_allowed BOOLEAN;
    v_upward_states TEXT[] := ARRAY[
        'DOCUMENTED', 'AUTHORITATIVE', 'CORROBORATED',
        'STATISTICALLY_SUPPORTED', 'CAUSALLY_SUPPORTED',
        'PEER_REVIEWED', 'INDEPENDENTLY_REPLICATED'
    ];
    v_lateral_states TEXT[] := ARRAY[
        'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE'
    ];
BEGIN
    -- 1. Derive actor from auth context (NEVER trust client)
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: No authenticated actor';
    END IF;

    -- 2. Idempotency check
    IF p_idempotency_key IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM public.safety_scientific_state_transitions
            WHERE id = p_idempotency_key
        ) THEN
            RETURN jsonb_build_object(
                'status', 'ALREADY_APPLIED',
                'idempotency_key', p_idempotency_key
            );
        END IF;
    END IF;

    -- 3. Lock the claim row (FOR UPDATE prevents races)
    SELECT assertion_state, server_version
    INTO v_current_state, v_current_version
    FROM public.safety_scientific_claims
    WHERE id = p_claim_id
    FOR UPDATE;

    IF v_current_state IS NULL THEN
        RAISE EXCEPTION 'CLAIM_NOT_FOUND: %', p_claim_id;
    END IF;

    -- 4. Optimistic concurrency
    IF p_expected_version IS NOT NULL
       AND p_expected_version != v_current_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT: expected=% actual=%',
            p_expected_version, v_current_version;
    END IF;

    -- 5. Validate inputs
    IF p_reason IS NULL OR p_reason = '' THEN
        RAISE EXCEPTION 'REASON_REQUIRED';
    END IF;

    IF p_evidence_ids IS NULL OR array_length(p_evidence_ids, 1) IS NULL THEN
        RAISE EXCEPTION 'EVIDENCE_REQUIRED: at least one evidence ID';
    END IF;

    -- 6. AI HARD BOUNDARY — AI cannot elevate
    IF p_actor_is_ai AND p_to_state = ANY(v_upward_states) THEN
        RAISE EXCEPTION 'AI_ELEVATION_BLOCKED: AI actor cannot transition to %',
            p_to_state;
    END IF;

    -- 7. Validate transition is allowed
    v_allowed := FALSE;

    -- Allowed transitions (mirrors Kotlin AssertionStateMachine)
    CASE v_current_state
        WHEN 'OBSERVED' THEN
            v_allowed := p_to_state IN ('DOCUMENTED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'DOCUMENTED' THEN
            v_allowed := p_to_state IN ('AUTHORITATIVE', 'CORROBORATED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'AUTHORITATIVE' THEN
            v_allowed := p_to_state IN ('CORROBORATED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'CORROBORATED' THEN
            v_allowed := p_to_state IN ('STATISTICALLY_SUPPORTED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'DERIVED' THEN
            v_allowed := p_to_state IN ('CORROBORATED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'STATISTICALLY_SUPPORTED' THEN
            v_allowed := p_to_state IN ('CAUSALLY_SUPPORTED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'CAUSALLY_SUPPORTED' THEN
            v_allowed := p_to_state IN ('PEER_REVIEWED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'PEER_REVIEWED' THEN
            v_allowed := p_to_state IN ('INDEPENDENTLY_REPLICATED', 'DISPUTED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'DISPUTED' THEN
            v_allowed := p_to_state IN ('OBSERVED', 'CONTRADICTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'CONTRADICTED' THEN
            v_allowed := p_to_state IN ('DISPUTED', 'INSUFFICIENT_EVIDENCE');
        WHEN 'INSUFFICIENT_EVIDENCE' THEN
            v_allowed := p_to_state IN ('OBSERVED', 'DISPUTED');
        WHEN 'UNKNOWN' THEN
            v_allowed := p_to_state IN ('OBSERVED', 'DISPUTED', 'INSUFFICIENT_EVIDENCE');
        ELSE
            v_allowed := FALSE;
    END CASE;

    IF NOT v_allowed THEN
        RAISE EXCEPTION 'ILLEGAL_TRANSITION: % -> %', v_current_state, p_to_state;
    END IF;

    -- 8. Generate transition ID
    v_transition_id := COALESCE(p_idempotency_key, gen_random_uuid());

    -- 9. Append-only audit record (immutable via trigger)
    INSERT INTO public.safety_scientific_state_transitions (
        id, subject_id, from_state, to_state, reason,
        evidence_ids, actor_id, actor_is_ai,
        methodology_version, occurred_at
    ) VALUES (
        v_transition_id, p_claim_id, v_current_state, p_to_state,
        p_reason, p_evidence_ids, v_actor_id, p_actor_is_ai,
        p_methodology_version, now()
    );

    -- 10. Update claim (atomic with audit)
    UPDATE public.safety_scientific_claims
    SET assertion_state = p_to_state,
        server_version = v_current_version + 1,
        updated_at = now()
    WHERE id = p_claim_id;

    -- 11. Return result
    RETURN jsonb_build_object(
        'status', 'TRANSITIONED',
        'transition_id', v_transition_id,
        'from_state', v_current_state,
        'to_state', p_to_state,
        'server_version', v_current_version + 1
    );
END;
$$;

-- Only service_role can call directly; RPCs exposed via PostgREST
REVOKE ALL ON FUNCTION public.safety_scientific_transition_claim_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.safety_scientific_transition_claim_v1 TO service_role;
GRANT EXECUTE ON FUNCTION public.safety_scientific_transition_claim_v1 TO authenticated;

-- ═══════════════════════════════════════════════════════════════════
-- Add server_version to claims table for optimistic concurrency
-- ═══════════════════════════════════════════════════════════════════

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'safety_scientific_claims'
          AND column_name = 'server_version'
    ) THEN
        ALTER TABLE public.safety_scientific_claims
            ADD COLUMN server_version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

-- Add evidence_ids array column to state_transitions
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'safety_scientific_state_transitions'
          AND column_name = 'evidence_ids'
    ) THEN
        ALTER TABLE public.safety_scientific_state_transitions
            ADD COLUMN evidence_ids UUID[] NOT NULL DEFAULT '{}';
    END IF;
END $$;
