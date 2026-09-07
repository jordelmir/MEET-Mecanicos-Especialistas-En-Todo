-- ==============================================================================
-- MIGRATION: 20260906110000_ai_profit_engine_diagnostic_cases_v14.sql
-- DOMAIN: Automotive AI · Mechanic Copilot · Server-Side Quotas · Model Cost Accounting
-- SPECIFICATION: MEET / ELYSIUM — AI PROFIT ENGINE MASTER IMPLEMENTATION ORDER V1
-- ==============================================================================

-- 1. AI Commercial Policies & Plans
CREATE TABLE IF NOT EXISTS public.ai_plan_policies (
    plan_key TEXT PRIMARY KEY,
    cases_per_period INTEGER NOT NULL CHECK (cases_per_period >= 0),
    period_type TEXT NOT NULL CHECK (period_type IN ('WEEKLY', 'MONTHLY', 'LIFETIME')),
    max_turns_per_case INTEGER NOT NULL CHECK (max_turns_per_case > 0),
    max_input_tokens_per_case BIGINT NOT NULL CHECK (max_input_tokens_per_case > 0),
    max_output_tokens_per_case BIGINT NOT NULL CHECK (max_output_tokens_per_case > 0),
    max_cost_micros_usd_per_case BIGINT NOT NULL CHECK (max_cost_micros_usd_per_case > 0),
    escalation_allowed BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed canonical plans
INSERT INTO public.ai_plan_policies (
    plan_key, cases_per_period, period_type, max_turns_per_case,
    max_input_tokens_per_case, max_output_tokens_per_case, max_cost_micros_usd_per_case,
    escalation_allowed, enabled
) VALUES
    ('FREE', 1, 'WEEKLY', 5, 12000, 3000, 40000, false, true),
    ('PRO', 5, 'WEEKLY', 15, 35000, 8000, 150000, true, true),
    ('WORKSHOP', 25, 'WEEKLY', 30, 80000, 20000, 500000, true, true),
    ('FLEET', 100, 'MONTHLY', 50, 150000, 40000, 1500000, true, true),
    ('CREDIT', 0, 'LIFETIME', 15, 35000, 8000, 150000, true, true)
ON CONFLICT (plan_key) DO UPDATE SET
    cases_per_period = EXCLUDED.cases_per_period,
    max_turns_per_case = EXCLUDED.max_turns_per_case,
    max_cost_micros_usd_per_case = EXCLUDED.max_cost_micros_usd_per_case,
    escalation_allowed = EXCLUDED.escalation_allowed,
    enabled = EXCLUDED.enabled;

-- 2. AI Model Catalog with Micro-USD Pricing
CREATE TABLE IF NOT EXISTS public.ai_model_catalog (
    provider_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    pricing_version TEXT NOT NULL,
    input_micros_usd_per_million BIGINT NOT NULL CHECK (input_micros_usd_per_million >= 0),
    cached_input_micros_usd_per_million BIGINT CHECK (cached_input_micros_usd_per_million >= 0),
    output_micros_usd_per_million BIGINT NOT NULL CHECK (output_micros_usd_per_million >= 0),
    supports_reasoning BOOLEAN NOT NULL DEFAULT false,
    supports_images BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to TIMESTAMPTZ,
    PRIMARY KEY (provider_id, model_id, pricing_version)
);

-- Seed models: GPT-5.4 nano ($0.20/M in, $1.25/M out), Gemini 2.5 Flash-Lite ($0.10/M in, $0.40/M out), GPT-5.4 mini ($0.75/M in, $3.00/M out)
INSERT INTO public.ai_model_catalog (
    provider_id, model_id, pricing_version,
    input_micros_usd_per_million, cached_input_micros_usd_per_million,
    output_micros_usd_per_million, supports_reasoning, supports_images, enabled
) VALUES
    ('openai', 'gpt-5.4-nano', '2026-03', 200000, 100000, 1250000, true, true, true),
    ('google', 'gemini-2.5-flash-lite', '2026-03', 100000, 50000, 400000, false, true, true),
    ('openai', 'gpt-5-nano', '2026-03', 50000, 25000, 400000, true, false, true),
    ('openai', 'gpt-5.4-mini', '2026-03', 750000, 375000, 3000000, true, true, true)
ON CONFLICT (provider_id, model_id, pricing_version) DO NOTHING;

-- 3. Authoritative Usage Buckets (Per Actor, Plan, Period)
CREATE TABLE IF NOT EXISTS public.ai_usage_buckets (
    actor_id UUID NOT NULL,
    plan_key TEXT NOT NULL REFERENCES public.ai_plan_policies(plan_key),
    period_key TEXT NOT NULL,
    period_starts_at TIMESTAMPTZ NOT NULL,
    period_ends_at TIMESTAMPTZ NOT NULL,
    included_cases INTEGER NOT NULL CHECK (included_cases >= 0),
    consumed_cases INTEGER NOT NULL DEFAULT 0 CHECK (consumed_cases >= 0),
    purchased_cases INTEGER NOT NULL DEFAULT 0 CHECK (purchased_cases >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (actor_id, plan_key, period_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_buckets_actor_period
ON public.ai_usage_buckets(actor_id, period_key);

-- 4. AI Diagnostic Cases (Cases, not isolated questions)
CREATE TABLE IF NOT EXISTS public.ai_diagnostic_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    diagnostic_case_id UUID,
    plan_key TEXT NOT NULL REFERENCES public.ai_plan_policies(plan_key),
    status TEXT NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'CLOSED', 'BUDGET_EXHAUSTED', 'BLOCKED')) DEFAULT 'OPEN',
    turns_used INTEGER NOT NULL DEFAULT 0 CHECK (turns_used >= 0),
    accumulated_input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (accumulated_input_tokens >= 0),
    accumulated_output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (accumulated_output_tokens >= 0),
    accumulated_cost_micros_usd BIGINT NOT NULL DEFAULT 0 CHECK (accumulated_cost_micros_usd >= 0),
    origin_repair_intent_id UUID REFERENCES public.repair_intents(id),
    downstream_service_request_id UUID REFERENCES public.universal_service_requests(id),
    downstream_contribution_minor BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_diagnostic_cases_owner_status
ON public.ai_diagnostic_cases(owner_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_diagnostic_cases_vehicle
ON public.ai_diagnostic_cases(vehicle_id);

-- 5. AI Case Turns
CREATE TABLE IF NOT EXISTS public.ai_case_turns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES public.ai_diagnostic_cases(id) ON DELETE CASCADE,
    turn_number INTEGER NOT NULL CHECK (turn_number >= 0),
    role TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    message TEXT NOT NULL,
    structured_payload JSONB,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    cost_micros_usd BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_case_turns_case_id
ON public.ai_case_turns(case_id, turn_number);

-- 6. AI Usage Events (Exact COGS Accounting)
CREATE TABLE IF NOT EXISTS public.ai_usage_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    case_id UUID REFERENCES public.ai_diagnostic_cases(id),
    turn_id UUID REFERENCES public.ai_case_turns(id),
    provider_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    prompt_tokens BIGINT,
    cached_prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT,
    cost_micros_usd BIGINT,
    cost_truth_state TEXT NOT NULL CHECK (cost_truth_state IN ('OBSERVED', 'DERIVED', 'ESTIMATED')),
    latency_ms BIGINT,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_actor
ON public.ai_usage_events(actor_id, created_at DESC);

-- 7. Consumable Credit Movements (AI_CREDIT_PACK_10)
CREATE TABLE IF NOT EXISTS public.ai_credit_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    delta_cases INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    source_ref TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_ref)
);

CREATE INDEX IF NOT EXISTS idx_ai_credit_movements_actor
ON public.ai_credit_movements(actor_id);

-- ==============================================================================
-- 8. RLS AND SECURITY ISOLATION
-- ==============================================================================

ALTER TABLE public.ai_plan_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_model_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_usage_buckets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_diagnostic_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_case_turns ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_usage_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_credit_movements ENABLE ROW LEVEL SECURITY;

-- Plan policies: readable by authenticated
DROP POLICY IF EXISTS p_ai_plan_policies_read ON public.ai_plan_policies;
CREATE POLICY p_ai_plan_policies_read ON public.ai_plan_policies
    FOR SELECT TO authenticated USING (enabled = true);

-- Model catalog: readable by authenticated
DROP POLICY IF EXISTS p_ai_model_catalog_read ON public.ai_model_catalog;
CREATE POLICY p_ai_model_catalog_read ON public.ai_model_catalog
    FOR SELECT TO authenticated USING (enabled = true);

-- Usage buckets: readable by owner only, cannot be directly written by client
DROP POLICY IF EXISTS p_ai_usage_buckets_read ON public.ai_usage_buckets;
CREATE POLICY p_ai_usage_buckets_read ON public.ai_usage_buckets
    FOR SELECT TO authenticated USING (actor_id = auth.uid());

-- Cases: readable by owner only, direct writes revoked
DROP POLICY IF EXISTS p_ai_diagnostic_cases_read ON public.ai_diagnostic_cases;
CREATE POLICY p_ai_diagnostic_cases_read ON public.ai_diagnostic_cases
    FOR SELECT TO authenticated USING (owner_id = auth.uid());

-- Turns: readable by case owner only
DROP POLICY IF EXISTS p_ai_case_turns_read ON public.ai_case_turns;
CREATE POLICY p_ai_case_turns_read ON public.ai_case_turns
    FOR SELECT TO authenticated USING (
        case_id IN (SELECT id FROM public.ai_diagnostic_cases WHERE owner_id = auth.uid())
    );

-- Credit movements: readable by owner
DROP POLICY IF EXISTS p_ai_credit_movements_read ON public.ai_credit_movements;
CREATE POLICY p_ai_credit_movements_read ON public.ai_credit_movements
    FOR SELECT TO authenticated USING (actor_id = auth.uid());

-- Usage events: STRICT PRIVATE PLANE (No authenticated access, service_role only)
REVOKE ALL ON public.ai_usage_events FROM anon, authenticated, public;

-- Revoke direct mutation from authenticated on authoritative tables
REVOKE INSERT, UPDATE, DELETE ON public.ai_plan_policies FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.ai_model_catalog FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.ai_usage_buckets FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.ai_diagnostic_cases FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.ai_case_turns FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.ai_credit_movements FROM anon, authenticated;

-- ==============================================================================
-- 9. AUTHORITATIVE SERVER-SIDE RPCS & ENGINES
-- ==============================================================================

-- 9.1 Helper: Resolve Effective AI Plan Server-Side
CREATE OR REPLACE FUNCTION public.resolve_effective_ai_plan(p_actor_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_has_fleet BOOLEAN := false;
    v_has_workshop BOOLEAN := false;
    v_has_pro BOOLEAN := false;
BEGIN
    IF p_actor_id IS NULL THEN
        RETURN 'FREE';
    END IF;

    -- Check active FLEET entitlement or active organization membership
    SELECT EXISTS (
        SELECT 1 FROM public.user_entitlements
        WHERE user_id = p_actor_id
          AND entitlement_key = 'FLEET_TOOLS'
          AND status = 'active'
          AND (expires_at IS NULL OR expires_at > now())
    ) OR EXISTS (
        SELECT 1 FROM public.organization_memberships m
        JOIN public.organizations o ON o.id = m.organization_id
        WHERE m.user_id = p_actor_id AND o.status = 'ACTIVE'
    ) INTO v_has_fleet;

    IF v_has_fleet THEN
        RETURN 'FLEET';
    END IF;

    -- Check active WORKSHOP entitlement
    SELECT EXISTS (
        SELECT 1 FROM public.user_entitlements
        WHERE user_id = p_actor_id
          AND entitlement_key IN ('WORKSHOP_WORKSPACE', 'WORKSHOP_ACCESS')
          AND status = 'active'
          AND (expires_at IS NULL OR expires_at > now())
    ) INTO v_has_workshop;

    IF v_has_workshop THEN
        RETURN 'WORKSHOP';
    END IF;

    -- Check active user entitlements (PRO / ELITE / AI_DIAGNOSIS)
    SELECT EXISTS (
        SELECT 1 FROM public.user_entitlements
        WHERE user_id = p_actor_id
          AND entitlement_key IN ('PRO_ACCESS', 'ELITE_ACCESS', 'AI_DIAGNOSIS', 'ADVANCED_DIAGNOSTICS')
          AND status = 'active'
          AND (expires_at IS NULL OR expires_at > now())
    ) INTO v_has_pro;

    IF v_has_pro THEN
        RETURN 'PRO';
    END IF;

    RETURN 'FREE';
END;
$$;

-- 9.2 Helper: Current AI Period Key
CREATE OR REPLACE FUNCTION public.current_ai_period_key(p_plan_key TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF p_plan_key = 'FLEET' THEN
        RETURN to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM');
    ELSIF p_plan_key = 'CREDIT' THEN
        RETURN 'LIFETIME';
    ELSE
        -- ISO Week for FREE, PRO, WORKSHOP
        RETURN to_char(now() AT TIME ZONE 'UTC', 'IYYY-"W"IW');
    END IF;
END;
$$;

-- 9.3 Helper: Ensure AI Usage Bucket
CREATE OR REPLACE FUNCTION public.ensure_ai_usage_bucket(p_actor_id UUID, p_plan_key TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_period_key TEXT;
    v_period_starts TIMESTAMPTZ;
    v_period_ends TIMESTAMPTZ;
    v_included_cases INT;
BEGIN
    v_period_key := public.current_ai_period_key(p_plan_key);

    IF p_plan_key = 'FLEET' THEN
        v_period_starts := date_trunc('month', now() AT TIME ZONE 'UTC');
        v_period_ends := v_period_starts + INTERVAL '1 month';
    ELSIF p_plan_key = 'CREDIT' THEN
        v_period_starts := '2026-01-01 00:00:00Z'::timestamptz;
        v_period_ends := '2099-12-31 23:59:59Z'::timestamptz;
    ELSE
        v_period_starts := date_trunc('week', now() AT TIME ZONE 'UTC');
        v_period_ends := v_period_starts + INTERVAL '1 week';
    END IF;

    SELECT cases_per_period INTO v_included_cases
    FROM public.ai_plan_policies
    WHERE plan_key = p_plan_key;

    IF v_included_cases IS NULL THEN
        v_included_cases := 1;
    END IF;

    INSERT INTO public.ai_usage_buckets (
        actor_id, plan_key, period_key, period_starts_at, period_ends_at, included_cases, consumed_cases, purchased_cases
    ) VALUES (
        p_actor_id, p_plan_key, v_period_key, v_period_starts, v_period_ends, v_included_cases, 0, 0
    )
    ON CONFLICT (actor_id, plan_key, period_key) DO NOTHING;
END;
$$;

-- 9.4 RPC: Atomic AI Diagnostic Case Opener (Section 11 & 88)
CREATE OR REPLACE FUNCTION public.ai_open_diagnostic_case(
    p_vehicle_id UUID,
    p_diagnostic_case_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID;
    v_plan TEXT;
    v_bucket public.ai_usage_buckets%ROWTYPE;
    v_existing_case_id UUID;
    v_new_case_id UUID;
    v_remaining_cases INT;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'AUTH_REQUIRED: Debe iniciar sesión para abrir un caso de diagnóstico.';
    END IF;

    IF p_vehicle_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_ARGUMENT: vehicle_id es requerido.';
    END IF;

    -- Section 88: One Case Per Problem. Reuse open case for same vehicle and diagnostic problem!
    SELECT id INTO v_existing_case_id
    FROM public.ai_diagnostic_cases
    WHERE owner_id = v_actor
      AND vehicle_id = p_vehicle_id
      AND (
          (p_diagnostic_case_id IS NOT NULL AND diagnostic_case_id = p_diagnostic_case_id)
          OR (p_diagnostic_case_id IS NULL AND diagnostic_case_id IS NULL)
      )
      AND status = 'OPEN'
    LIMIT 1;

    IF v_existing_case_id IS NOT NULL THEN
        SELECT (included_cases + purchased_cases - consumed_cases) INTO v_remaining_cases
        FROM public.ai_usage_buckets
        WHERE actor_id = v_actor AND period_key = public.current_ai_period_key(public.resolve_effective_ai_plan(v_actor));

        RETURN jsonb_build_object(
            'success', true,
            'case_id', v_existing_case_id,
            'reused_existing_case', true,
            'plan_key', public.resolve_effective_ai_plan(v_actor),
            'remaining_cases', COALESCE(v_remaining_cases, 0)
        );
    END IF;

    -- Resolve plan and ensure usage bucket
    v_plan := public.resolve_effective_ai_plan(v_actor);
    PERFORM public.ensure_ai_usage_bucket(v_actor, v_plan);

    -- Lock bucket for update
    SELECT * INTO v_bucket
    FROM public.ai_usage_buckets
    WHERE actor_id = v_actor
      AND plan_key = v_plan
      AND period_key = public.current_ai_period_key(v_plan)
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'BUCKET_NOT_FOUND: No se pudo resolver el bucket de uso de IA.';
    END IF;

    v_remaining_cases := (v_bucket.included_cases + v_bucket.purchased_cases - v_bucket.consumed_cases);
    IF v_remaining_cases <= 0 THEN
        RAISE EXCEPTION 'AI_QUOTA_EXHAUSTED: Cuota de casos de diagnóstico agotada para este período (% restantes).', v_remaining_cases;
    END IF;

    -- Consume exactly 1 case
    v_new_case_id := gen_random_uuid();

    INSERT INTO public.ai_diagnostic_cases (
        id, owner_id, vehicle_id, diagnostic_case_id, plan_key, status
    ) VALUES (
        v_new_case_id, v_actor, p_vehicle_id, p_diagnostic_case_id, v_plan, 'OPEN'
    );

    UPDATE public.ai_usage_buckets
    SET consumed_cases = consumed_cases + 1,
        updated_at = now()
    WHERE actor_id = v_actor
      AND plan_key = v_plan
      AND period_key = v_bucket.period_key;

    RETURN jsonb_build_object(
        'success', true,
        'case_id', v_new_case_id,
        'reused_existing_case', false,
        'plan_key', v_plan,
        'remaining_cases', v_remaining_cases - 1
    );
END;
$$;

-- 9.5 RPC: Submit Case Turn with Authoritative COGS and Token Metering
CREATE OR REPLACE FUNCTION public.ai_submit_case_turn(
    p_case_id UUID,
    p_role TEXT,
    p_message TEXT,
    p_structured_payload JSONB DEFAULT NULL,
    p_evidence_refs JSONB DEFAULT '[]'::jsonb,
    p_prompt_tokens BIGINT DEFAULT 0,
    p_completion_tokens BIGINT DEFAULT 0,
    p_provider_id TEXT DEFAULT 'openai',
    p_model_id TEXT DEFAULT 'gpt-5.4-nano',
    p_latency_ms BIGINT DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID;
    v_case public.ai_diagnostic_cases%ROWTYPE;
    v_policy public.ai_plan_policies%ROWTYPE;
    v_model public.ai_model_catalog%ROWTYPE;
    v_turn_id UUID := gen_random_uuid();
    v_turn_cost_micros BIGINT := 0;
    v_next_turn_number INT;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'AUTH_REQUIRED';
    END IF;

    SELECT * INTO v_case
    FROM public.ai_diagnostic_cases
    WHERE id = p_case_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'CASE_NOT_FOUND: Caso % no existe.', p_case_id;
    END IF;

    IF v_case.owner_id != v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN: No es propietario de este caso.';
    END IF;

    IF v_case.status != 'OPEN' THEN
        RAISE EXCEPTION 'CASE_NOT_OPEN: El caso está en estado % y no admite más mensajes.', v_case.status;
    END IF;

    SELECT * INTO v_policy
    FROM public.ai_plan_policies
    WHERE plan_key = v_case.plan_key;

    IF v_case.turns_used >= v_policy.max_turns_per_case THEN
        UPDATE public.ai_diagnostic_cases SET status = 'BUDGET_EXHAUSTED' WHERE id = p_case_id;
        RAISE EXCEPTION 'TURN_BUDGET_EXHAUSTED: Máximo de turnos (%) alcanzado para este caso.', v_policy.max_turns_per_case;
    END IF;

    -- Resolve model pricing
    SELECT * INTO v_model
    FROM public.ai_model_catalog
    WHERE provider_id = p_provider_id AND model_id = p_model_id AND enabled = true
    ORDER BY effective_from DESC
    LIMIT 1;

    IF FOUND THEN
        v_turn_cost_micros := (
            (p_prompt_tokens * v_model.input_micros_usd_per_million / 1000000) +
            (p_completion_tokens * v_model.output_micros_usd_per_million / 1000000)
        );
    END IF;

    IF (v_case.accumulated_cost_micros_usd + v_turn_cost_micros) > v_policy.max_cost_micros_usd_per_case THEN
        UPDATE public.ai_diagnostic_cases SET status = 'BUDGET_EXHAUSTED' WHERE id = p_case_id;
        RAISE EXCEPTION 'COST_BUDGET_EXHAUSTED: Límite económico por caso superado.';
    END IF;

    v_next_turn_number := v_case.turns_used + 1;

    -- Record Turn
    INSERT INTO public.ai_case_turns (
        id, case_id, turn_number, role, message, structured_payload,
        evidence_refs, prompt_tokens, completion_tokens, cost_micros_usd
    ) VALUES (
        v_turn_id, p_case_id, v_next_turn_number, p_role, p_message, p_structured_payload,
        p_evidence_refs, p_prompt_tokens, p_completion_tokens, v_turn_cost_micros
    );

    -- Record Usage Event
    INSERT INTO public.ai_usage_events (
        actor_id, case_id, turn_id, provider_id, model_id, prompt_tokens,
        completion_tokens, cost_micros_usd, cost_truth_state, latency_ms, success
    ) VALUES (
        v_actor, p_case_id, v_turn_id, p_provider_id, p_model_id, p_prompt_tokens,
        p_completion_tokens, v_turn_cost_micros, 'DERIVED', p_latency_ms, true
    );

    -- Update Case Statistics
    UPDATE public.ai_diagnostic_cases
    SET turns_used = v_next_turn_number,
        accumulated_input_tokens = accumulated_input_tokens + p_prompt_tokens,
        accumulated_output_tokens = accumulated_output_tokens + p_completion_tokens,
        accumulated_cost_micros_usd = accumulated_cost_micros_usd + v_turn_cost_micros
    WHERE id = p_case_id;

    RETURN jsonb_build_object(
        'success', true,
        'turn_id', v_turn_id,
        'turn_number', v_next_turn_number,
        'cost_micros_usd', v_turn_cost_micros,
        'accumulated_cost_micros_usd', v_case.accumulated_cost_micros_usd + v_turn_cost_micros
    );
END;
$$;

-- 9.6 RPC: Link Case to Repair Intent (Downstream Conversion Flywheel)
CREATE OR REPLACE FUNCTION public.ai_link_case_to_repair_intent(
    p_case_id UUID,
    p_repair_intent_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'AUTH_REQUIRED';
    END IF;

    UPDATE public.ai_diagnostic_cases
    SET origin_repair_intent_id = p_repair_intent_id
    WHERE id = p_case_id AND owner_id = v_actor;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'CASE_NOT_FOUND_OR_UNAUTHORIZED';
    END IF;

    RETURN jsonb_build_object('success', true, 'case_id', p_case_id, 'repair_intent_id', p_repair_intent_id);
END;
$$;

-- 9.7 RPC: Grant Purchased Credit Pack (Idempotent Consumable Credit)
CREATE OR REPLACE FUNCTION public.ai_grant_purchased_credit_pack(
    p_actor_id UUID,
    p_cases INTEGER,
    p_source_type TEXT,
    p_source_ref TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_plan TEXT;
    v_period_key TEXT;
BEGIN
    IF p_actor_id IS NULL OR p_cases <= 0 THEN
        RAISE EXCEPTION 'INVALID_ARGUMENTS';
    END IF;

    -- Record credit movement with idempotency on unique(source_type, source_ref)
    INSERT INTO public.ai_credit_movements (
        actor_id, delta_cases, source_type, source_ref
    ) VALUES (
        p_actor_id, p_cases, p_source_type, p_source_ref
    )
    ON CONFLICT (source_type, source_ref) DO NOTHING;

    -- If conflict, no-op idempotent return
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', true, 'already_granted', true);
    END IF;

    v_plan := public.resolve_effective_ai_plan(p_actor_id);
    v_period_key := public.current_ai_period_key(v_plan);
    PERFORM public.ensure_ai_usage_bucket(p_actor_id, v_plan);

    UPDATE public.ai_usage_buckets
    SET purchased_cases = purchased_cases + p_cases,
        updated_at = now()
    WHERE actor_id = p_actor_id
      AND plan_key = v_plan
      AND period_key = v_period_key;

    RETURN jsonb_build_object('success', true, 'granted_cases', p_cases, 'plan_key', v_plan);
END;
$$;

-- Grants for authenticated callers on authorized entrypoints
GRANT SELECT ON public.ai_plan_policies TO authenticated;
GRANT SELECT ON public.ai_model_catalog TO authenticated;
GRANT SELECT ON public.ai_usage_buckets TO authenticated;
GRANT SELECT ON public.ai_diagnostic_cases TO authenticated;
GRANT SELECT ON public.ai_case_turns TO authenticated;
GRANT SELECT ON public.ai_credit_movements TO authenticated;

GRANT EXECUTE ON FUNCTION public.resolve_effective_ai_plan(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.current_ai_period_key(TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ensure_ai_usage_bucket(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ai_open_diagnostic_case(UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ai_submit_case_turn(UUID, TEXT, TEXT, JSONB, JSONB, BIGINT, BIGINT, TEXT, TEXT, BIGINT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ai_link_case_to_repair_intent(UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.ai_grant_purchased_credit_pack(UUID, INTEGER, TEXT, TEXT) TO service_role;
