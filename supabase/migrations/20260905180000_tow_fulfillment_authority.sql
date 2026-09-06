-- =============================================================================
-- Migration: 20260905180000_tow_fulfillment_authority.sql
-- Description: Server-Authoritative Tow Fulfillment Platform (Tow Authority V2)
-- Governed by: MEET / Elysium Vanguard Master Implementation Order V4
-- Schema Standard: Strict Non-Invention of Data, Canonical Truth, Atomic CAS
-- =============================================================================

-- Ensure PostGIS is active in extensions schema
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. TOW JOBS (AUTHORITATIVE SERVER TABLE)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_jobs (
    job_id TEXT PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    assigned_operator_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    state TEXT NOT NULL DEFAULT 'REQUESTED' CHECK (
        state IN (
            'DRAFT',
            'REQUESTED',
            'SEARCHING',
            'DISPATCHED',
            'EN_ROUTE',
            'ON_SCENE',
            'HOOKED',
            'IN_TRANSIT',
            'DROPPED_OFF',
            'COMPLETED',
            'CANCELLED',
            'REJECTED'
        )
    ),
    service_type TEXT NOT NULL DEFAULT 'STANDARD',
    urgency TEXT NOT NULL DEFAULT 'STANDARD' CHECK (
        urgency IN ('LOW', 'STANDARD', 'URGENT', 'EMERGENCY')
    ),
    notes TEXT,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address TEXT NOT NULL,
    pickup_accuracy_meters REAL,
    dest_lat DOUBLE PRECISION,
    dest_lng DOUBLE PRECISION,
    dest_address TEXT,
    pickup_location extensions.geography(Point, 4326) GENERATED ALWAYS AS (
        extensions.ST_SetSRID(extensions.ST_MakePoint(pickup_lng, pickup_lat), 4326)::extensions.geography
    ) STORED,
    dest_location extensions.geography(Point, 4326) GENERATED ALWAYS AS (
        CASE 
            WHEN dest_lat IS NOT NULL AND dest_lng IS NOT NULL 
            THEN extensions.ST_SetSRID(extensions.ST_MakePoint(dest_lng, dest_lat), 4326)::extensions.geography 
            ELSE NULL 
        END
    ) STORED,
    quoted_price_minor BIGINT CHECK (quoted_price_minor IS NULL OR quoted_price_minor >= 0),
    final_price_minor BIGINT CHECK (final_price_minor IS NULL OR final_price_minor >= 0),
    currency TEXT NOT NULL DEFAULT 'CRC' CHECK (currency ~ '^[A-Z]{3}$'),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    idempotency_key TEXT,
    correlation_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Indices for rapid queries, dispatch filtering, and spatial search
CREATE INDEX IF NOT EXISTS idx_tow_jobs_customer
    ON public.tow_jobs (customer_id, state);

CREATE INDEX IF NOT EXISTS idx_tow_jobs_operator
    ON public.tow_jobs (assigned_operator_id, state)
    WHERE assigned_operator_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tow_jobs_state_created
    ON public.tow_jobs (state, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tow_jobs_pickup_geo
    ON public.tow_jobs USING GIST (pickup_location);

CREATE INDEX IF NOT EXISTS idx_tow_jobs_correlation
    ON public.tow_jobs (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. TOW COMMAND RECEIPTS (STRICT IDEMPOTENCY RECORDING)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_command_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    job_id TEXT REFERENCES public.tow_jobs(job_id) ON DELETE RESTRICT,
    command_type TEXT NOT NULL CHECK (
        command_type IN (
            'REQUEST',
            'CLAIM',
            'DISPATCH',
            'EN_ROUTE',
            'ARRIVE_ON_SCENE',
            'HOOK',
            'START_TRANSIT',
            'DROP_OFF',
            'COMPLETE',
            'CANCEL',
            'REJECT'
        )
    ),
    idempotency_key TEXT NOT NULL CHECK (
        char_length(idempotency_key) BETWEEN 16 AND 128 AND
        idempotency_key ~ '^[A-Za-z0-9._:-]+$'
    ),
    request_hash TEXT NOT NULL CHECK (request_hash ~ '^[a-f0-9]{64}$'),
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_tow_command_receipts_job
    ON public.tow_command_receipts (job_id, created_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. STORED PROCEDURE: ATOMIC CAS JOB CLAIMING (tow_claim_job)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_claim_job(
    p_job_id TEXT,
    p_expected_version BIGINT,
    p_idempotency_key TEXT,
    p_request_hash TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
    v_cached_receipt RECORD;
    v_job RECORD;
    v_current_job RECORD;
    v_response JSONB;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- 1. Idempotency Check
    SELECT * INTO v_cached_receipt
    FROM public.tow_command_receipts
    WHERE actor_id = v_actor_id AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_cached_receipt.request_hash <> p_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_cached_receipt.response;
    END IF;

    -- 2. Atomic Compare-And-Swap (CAS) Assignment
    UPDATE public.tow_jobs
    SET assigned_operator_id = v_actor_id,
        state = 'DISPATCHED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE job_id = p_job_id
      AND version = p_expected_version
      AND assigned_operator_id IS NULL
      AND state IN ('REQUESTED', 'SEARCHING')
    RETURNING * INTO v_job;

    -- 3. Winner Evaluation
    IF FOUND THEN
        v_response := jsonb_build_object(
            'success', true,
            'job_id', v_job.job_id,
            'state', v_job.state,
            'assigned_operator_id', v_job.assigned_operator_id,
            'version', v_job.version,
            'updated_at', v_job.updated_at
        );

        -- Record Idempotency Receipt
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_job.job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );

        -- Transactional Outbox Event
        IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'elysium_publish_outbox_event') THEN
            PERFORM public.elysium_publish_outbox_event(
                'TOW', 'tow_job', v_job.job_id,
                'TOW_JOB', v_job.job_id, v_job.version,
                'TOW_JOB_ASSIGNED', 'DURABLE_DOMAIN',
                v_response,
                v_job.customer_id,
                NULL,
                v_job.correlation_id,
                p_idempotency_key,
                NULL
            );
        END IF;

        RETURN v_response;
    END IF;

    -- 4. Failure Branch Diagnosis
    SELECT * INTO v_current_job
    FROM public.tow_jobs
    WHERE job_id = p_job_id;

    IF NOT FOUND THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'JOB_NOT_FOUND',
            'message', 'El trabajo de grúa solicitado no existe.'
        );
    ELSIF v_current_job.assigned_operator_id IS NOT NULL THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'ALREADY_CLAIMED',
            'message', 'El trabajo de grúa ya fue asignado a otro operador.',
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
    ELSIF v_current_job.version <> p_expected_version THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Conflicto de versión optimista. El estado ha cambiado.',
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
    ELSE
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'INVALID_STATE_FOR_CLAIM',
            'message', 'El trabajo de grúa no se encuentra en estado disponible para reclamo.',
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
    END IF;

    -- Record Failure Receipt to preserve idempotent semantics
    INSERT INTO public.tow_command_receipts (
        actor_id, job_id, command_type, idempotency_key, request_hash, response
    ) VALUES (
        v_actor_id, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. STORED PROCEDURE: STATE TRANSITION EXECUTION (tow_execute_transition)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_execute_transition(
    p_job_id TEXT,
    p_target_state TEXT,
    p_expected_version BIGINT,
    p_idempotency_key TEXT,
    p_request_hash TEXT,
    p_final_price_minor BIGINT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID;
    v_cached_receipt RECORD;
    v_current_job RECORD;
    v_updated_job RECORD;
    v_response JSONB;
    v_is_valid_transition BOOLEAN := false;
BEGIN
    v_actor_id := auth.uid();
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- 1. Idempotency Check
    SELECT * INTO v_cached_receipt
    FROM public.tow_command_receipts
    WHERE actor_id = v_actor_id AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_cached_receipt.request_hash <> p_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_cached_receipt.response;
    END IF;

    -- 2. Fetch Current Job
    SELECT * INTO v_current_job
    FROM public.tow_jobs
    WHERE job_id = p_job_id;

    IF NOT FOUND THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'JOB_NOT_FOUND',
            'message', 'El trabajo de grúa solicitado no existe.'
        );
        RETURN v_response;
    END IF;

    -- 3. Version Check
    IF v_current_job.version <> p_expected_version THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Conflicto de concurrencia de versión.',
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
        RETURN v_response;
    END IF;

    -- 4. Authoritative State Machine Transition Validation
    v_is_valid_transition := CASE
        WHEN v_current_job.state = 'DISPATCHED'  AND p_target_state = 'EN_ROUTE' THEN true
        WHEN v_current_job.state = 'EN_ROUTE'    AND p_target_state = 'ON_SCENE' THEN true
        WHEN v_current_job.state = 'ON_SCENE'    AND p_target_state = 'HOOKED' THEN true
        WHEN v_current_job.state = 'HOOKED'      AND p_target_state = 'IN_TRANSIT' THEN true
        WHEN v_current_job.state = 'IN_TRANSIT'  AND p_target_state = 'DROPPED_OFF' THEN true
        WHEN v_current_job.state = 'DROPPED_OFF' AND p_target_state = 'COMPLETED' THEN true
        WHEN v_current_job.state IN ('REQUESTED', 'SEARCHING', 'DISPATCHED', 'EN_ROUTE', 'ON_SCENE') 
             AND p_target_state = 'CANCELLED' THEN true
        ELSE false
    END;

    IF NOT v_is_valid_transition THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'INVALID_STATE_TRANSITION',
            'message', format('Transición inválida de %s a %s.', v_current_job.state, p_target_state),
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
        RETURN v_response;
    END IF;

    -- 5. Authorization Check
    IF p_target_state = 'CANCELLED' THEN
        IF v_current_job.customer_id <> v_actor_id AND v_current_job.assigned_operator_id <> v_actor_id THEN
            RAISE EXCEPTION 'FORBIDDEN_CANCELLATION' USING ERRCODE = '42501';
        END IF;
    ELSE
        IF v_current_job.assigned_operator_id <> v_actor_id THEN
            RAISE EXCEPTION 'FORBIDDEN_OPERATOR_TRANSITION' USING ERRCODE = '42501';
        END IF;
    END IF;

    -- 6. Atomic Update
    UPDATE public.tow_jobs
    SET state = p_target_state,
        final_price_minor = COALESCE(p_final_price_minor, final_price_minor),
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE job_id = p_job_id
      AND version = p_expected_version
    RETURNING * INTO v_updated_job;

    IF NOT FOUND THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Conflicto al actualizar la fila.',
            'current_version', v_current_job.version,
            'current_state', v_current_job.state
        );
        RETURN v_response;
    END IF;

    v_response := jsonb_build_object(
        'success', true,
        'job_id', v_updated_job.job_id,
        'state', v_updated_job.state,
        'version', v_updated_job.version,
        'final_price_minor', v_updated_job.final_price_minor,
        'updated_at', v_updated_job.updated_at
    );

    -- Record Idempotency Receipt
    INSERT INTO public.tow_command_receipts (
        actor_id, job_id, command_type, idempotency_key, request_hash, response
    ) VALUES (
        v_actor_id, v_updated_job.job_id, p_target_state, p_idempotency_key, p_request_hash, v_response
    );

    -- Transactional Outbox Event
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'elysium_publish_outbox_event') THEN
        PERFORM public.elysium_publish_outbox_event(
            'TOW', 'tow_job', v_updated_job.job_id,
            'TOW_JOB', v_updated_job.job_id, v_updated_job.version,
            'TOW_JOB_STATE_CHANGED', 'DURABLE_DOMAIN',
            v_response,
            v_updated_job.customer_id,
            NULL,
            v_updated_job.correlation_id,
            p_idempotency_key,
            NULL
        );
    END IF;

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. ROW LEVEL SECURITY (RLS) POLICIES
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.tow_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tow_command_receipts ENABLE ROW LEVEL SECURITY;

-- tow_jobs: Customer Read
CREATE POLICY tow_jobs_customer_read ON public.tow_jobs
    FOR SELECT
    TO authenticated
    USING (customer_id = auth.uid());

-- tow_jobs: Assigned Operator Read
CREATE POLICY tow_jobs_operator_read ON public.tow_jobs
    FOR SELECT
    TO authenticated
    USING (assigned_operator_id = auth.uid());

-- tow_jobs: Unassigned Open Jobs Read (for discovery)
CREATE POLICY tow_jobs_open_discovery ON public.tow_jobs
    FOR SELECT
    TO authenticated
    USING (
        assigned_operator_id IS NULL AND
        state IN ('REQUESTED', 'SEARCHING')
    );

-- tow_jobs: Direct Customer Insert (Request creation)
CREATE POLICY tow_jobs_customer_insert ON public.tow_jobs
    FOR INSERT
    TO authenticated
    WITH CHECK (customer_id = auth.uid());

-- tow_command_receipts: Actor Read
CREATE POLICY tow_command_receipts_actor_read ON public.tow_command_receipts
    FOR SELECT
    TO authenticated
    USING (actor_id = auth.uid());

-- Service Role Full Access
CREATE POLICY tow_jobs_service_role_all ON public.tow_jobs
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY tow_command_receipts_service_role_all ON public.tow_command_receipts
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);
