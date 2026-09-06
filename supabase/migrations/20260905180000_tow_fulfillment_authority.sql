-- =============================================================================
-- Migration: 20260905180000_tow_fulfillment_authority.sql
-- Description: Server-Authoritative Tow Fulfillment Platform (Tow Authority V5)
-- Governed by: MEET / Elysium Vanguard Master Implementation Order V5
-- Schema Standard: Strict Non-Invention of Data, Canonical Truth, Atomic CAS
-- Canonical Authority:
--   - State Machine: Exact 14 canonical states identical to Android TowState
--   - Physical Units: public.tow_units table (no fake defaults)
--   - Provider Trust: public.provider_profiles joined to public.user_profiles
--   - Command Receipts: race-safe advisory lock, nullable FK for JOB_NOT_FOUND
--   - Discovery: secure tow_discover_jobs RPC (coarse data only; no open SELECT)
--   - Request: tow_request_job RPC (direct client INSERT revoked)
-- =============================================================================

-- Ensure PostGIS is active in extensions schema if available in environment
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'postgis') THEN
        EXECUTE 'CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions';
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. TOW UNITS (PHYSICAL RIG AUTHORITY: TowOperator != TowUnit)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operator_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    brand_model TEXT,
    license_plate TEXT NOT NULL UNIQUE,
    capabilities TEXT[] NOT NULL DEFAULT '{}',
    max_weight_kg INTEGER CHECK (max_weight_kg IS NULL OR max_weight_kg > 0),
    verification_state TEXT NOT NULL DEFAULT 'UNVERIFIED' CHECK (
        verification_state IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')
    ),
    availability_state TEXT NOT NULL DEFAULT 'OFFLINE' CHECK (
        availability_state IN ('AVAILABLE', 'BUSY', 'OFFLINE', 'MAINTENANCE')
    ),
    verification_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (operator_id, license_plate)
);

CREATE INDEX IF NOT EXISTS idx_tow_units_operator
    ON public.tow_units (operator_id, verification_state, availability_state);

CREATE INDEX IF NOT EXISTS idx_tow_units_capabilities
    ON public.tow_units USING GIN (capabilities);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. TOW JOBS (AUTHORITATIVE SERVER TABLE)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_jobs (
    job_id TEXT PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    assigned_operator_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    assigned_tow_unit_id UUID REFERENCES public.tow_units(id) ON DELETE SET NULL,
    state TEXT NOT NULL DEFAULT 'REQUESTED' CHECK (
        state IN (
            'REQUESTED',
            'MATCHING',
            'ASSIGNED',
            'EN_ROUTE',
            'ARRIVED',
            'LOADING',
            'LOADED',
            'IN_TRANSIT',
            'ARRIVED_DESTINATION',
            'UNLOADING',
            'DELIVERED',
            'COMPLETED',
            'CANCELLED',
            'DISPUTED'
        )
    ),
    service_type TEXT NOT NULL DEFAULT 'STANDARD',
    urgency TEXT NOT NULL DEFAULT 'STANDARD' CHECK (
        urgency IN ('LOW', 'STANDARD', 'URGENT', 'EMERGENCY')
    ),
    vehicle_vin TEXT,
    vehicle_summary TEXT NOT NULL DEFAULT 'Vehículo',
    required_capabilities TEXT[] NOT NULL DEFAULT '{}',
    notes TEXT,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    pickup_address TEXT NOT NULL,
    pickup_accuracy_meters REAL,
    dest_lat DOUBLE PRECISION,
    dest_lng DOUBLE PRECISION,
    dest_address TEXT,
    quoted_price_minor BIGINT CHECK (quoted_price_minor IS NULL OR quoted_price_minor >= 0),
    final_price_minor BIGINT CHECK (final_price_minor IS NULL OR final_price_minor >= 0),
    currency TEXT NOT NULL DEFAULT 'CRC' CHECK (currency ~ '^[A-Z]{3}$'),
    custody_records JSONB NOT NULL DEFAULT '[]'::jsonb,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    idempotency_key TEXT,
    correlation_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_tow_jobs_customer
    ON public.tow_jobs (customer_id, state);

CREATE INDEX IF NOT EXISTS idx_tow_jobs_operator
    ON public.tow_jobs (assigned_operator_id, state)
    WHERE assigned_operator_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tow_jobs_state_created
    ON public.tow_jobs (state, created_at DESC);

-- Conditionally add PostGIS geography columns and GIST index if available; fallback to lat/lng btree index
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        EXECUTE 'ALTER TABLE public.tow_jobs ADD COLUMN IF NOT EXISTS pickup_location extensions.geography(Point, 4326) GENERATED ALWAYS AS (extensions.ST_SetSRID(extensions.ST_MakePoint(pickup_lng, pickup_lat), 4326)::extensions.geography) STORED';
        EXECUTE 'ALTER TABLE public.tow_jobs ADD COLUMN IF NOT EXISTS dest_location extensions.geography(Point, 4326) GENERATED ALWAYS AS (CASE WHEN dest_lat IS NOT NULL AND dest_lng IS NOT NULL THEN extensions.ST_SetSRID(extensions.ST_MakePoint(dest_lng, dest_lat), 4326)::extensions.geography ELSE NULL END) STORED';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_tow_jobs_pickup_geo ON public.tow_jobs USING GIST (pickup_location)';
    ELSE
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_tow_jobs_pickup_lat_lng ON public.tow_jobs (pickup_lat, pickup_lng)';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tow_jobs_correlation
    ON public.tow_jobs (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. TOW COMMAND RECEIPTS (RACE-SAFE IDEMPOTENCY)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.tow_command_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    job_id TEXT REFERENCES public.tow_jobs(job_id) ON DELETE SET NULL,
    requested_job_id TEXT NOT NULL,
    command_type TEXT NOT NULL CHECK (
        command_type IN (
            'REQUEST',
            'CLAIM',
            'START_EN_ROUTE',
            'CONFIRM_ARRIVAL',
            'START_LOADING',
            'CONFIRM_LOADED',
            'START_TRANSIT',
            'CONFIRM_DESTINATION_ARRIVAL',
            'START_UNLOADING',
            'CONFIRM_DELIVERED',
            'COMPLETE',
            'CANCEL',
            'RAISE_DISPUTE'
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

CREATE INDEX IF NOT EXISTS idx_tow_command_receipts_requested_job
    ON public.tow_command_receipts (requested_job_id, created_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. STORED PROCEDURE: ATOMIC CAS JOB CLAIMING (tow_claim_job)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_claim_job(
    p_job_id TEXT,
    p_tow_unit_id UUID,
    p_expected_version BIGINT,
    p_idempotency_key TEXT,
    p_request_hash TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := auth.uid();
    v_receipt RECORD;
    v_job RECORD;
    v_current_job RECORD;
    v_unit RECORD;
    v_provider_profile_id UUID;
    v_response JSONB;
BEGIN
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_expected_version < 1 THEN
        RAISE EXCEPTION 'INVALID_EXPECTED_VERSION' USING ERRCODE = '22023';
    END IF;

    IF p_idempotency_key IS NULL OR char_length(p_idempotency_key) < 16 THEN
        RAISE EXCEPTION 'INVALID_IDEMPOTENCY_KEY' USING ERRCODE = '22023';
    END IF;

    -- Concurrency-Safe Transaction Advisory Lock on (actor_id:idempotency_key)
    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor_id::TEXT || ':' || p_idempotency_key, 0));

    -- 1. Idempotency Check
    SELECT * INTO v_receipt
    FROM public.tow_command_receipts
    WHERE actor_id = v_actor_id AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> p_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- 2. Hard Provider Trust Authority Check
    SELECT pp.id INTO v_provider_profile_id
    FROM public.provider_profiles pp
    JOIN public.user_profiles up ON up.id = pp.user_profile_id
    WHERE up.auth_user_id = v_actor_id
      AND pp.provider_type = 'tow_provider'
      AND pp.status = 'active'
      AND pp.is_verified = TRUE
      AND pp.is_active = TRUE
    LIMIT 1;

    IF v_provider_profile_id IS NULL THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'NOT_VERIFIED_TOW_PROVIDER',
            'message', 'El usuario no cuenta con un perfil de proveedor de grúa activo y verificado en la plataforma.'
        );
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, NULL, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    -- 3. Tow Unit Authority and Availability Check
    SELECT * INTO v_unit
    FROM public.tow_units
    WHERE id = p_tow_unit_id
      AND operator_id = v_actor_id;

    IF NOT FOUND THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'TOW_UNIT_NOT_FOUND',
            'message', 'La unidad de grúa especificada no existe o no pertenece al operador.'
        );
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, NULL, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    IF v_unit.verification_state <> 'VERIFIED' THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'TOW_UNIT_NOT_VERIFIED',
            'message', 'La unidad de grúa no se encuentra en estado VERIFIED por la plataforma.',
            'verification_state', v_unit.verification_state
        );
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, NULL, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    IF v_unit.availability_state <> 'AVAILABLE' THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'TOW_UNIT_NOT_AVAILABLE',
            'message', 'La unidad de grúa no se encuentra en estado AVAILABLE.',
            'availability_state', v_unit.availability_state
        );
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, NULL, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    -- 4. Atomic Compare-And-Swap (CAS) Assignment
    UPDATE public.tow_jobs
    SET assigned_operator_id = v_actor_id,
        assigned_tow_unit_id = p_tow_unit_id,
        state = 'ASSIGNED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE job_id = p_job_id
      AND version = p_expected_version
      AND assigned_operator_id IS NULL
      AND assigned_tow_unit_id IS NULL
      AND state IN ('REQUESTED', 'MATCHING')
      AND v_unit.capabilities @> required_capabilities
    RETURNING * INTO v_job;

    -- 5. Winner Evaluation
    IF FOUND THEN
        v_response := jsonb_build_object(
            'success', true,
            'job_id', v_job.job_id,
            'state', v_job.state,
            'assigned_operator_id', v_job.assigned_operator_id,
            'assigned_tow_unit_id', v_job.assigned_tow_unit_id,
            'version', v_job.version,
            'updated_at', v_job.updated_at
        );

        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_job.job_id, v_job.job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
        );

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

    -- 6. Failure Diagnosis (Atomic Re-Read)
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
    ELSIF NOT (v_unit.capabilities @> v_current_job.required_capabilities) THEN
        v_response := jsonb_build_object(
            'success', false,
            'error_code', 'INSUFFICIENT_CAPABILITIES',
            'message', 'La unidad de grúa no cumple con todas las capacidades físicas requeridas para este servicio.',
            'required_capabilities', v_current_job.required_capabilities,
            'unit_capabilities', v_unit.capabilities,
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

    INSERT INTO public.tow_command_receipts (
        actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
    ) VALUES (
        v_actor_id, v_current_job.job_id, p_job_id, 'CLAIM', p_idempotency_key, p_request_hash, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. STORED PROCEDURE: STATE TRANSITION EXECUTION (tow_execute_transition)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_execute_transition(
    p_job_id TEXT,
    p_target_state TEXT,
    p_expected_version BIGINT,
    p_idempotency_key TEXT,
    p_request_hash TEXT,
    p_evidence_hash TEXT DEFAULT NULL,
    p_evidence_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := auth.uid();
    v_receipt RECORD;
    v_current_job RECORD;
    v_updated_job RECORD;
    v_response JSONB;
    v_command_type TEXT;
    v_is_valid_transition BOOLEAN := false;
BEGIN
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL OR char_length(p_idempotency_key) < 16 THEN
        RAISE EXCEPTION 'INVALID_IDEMPOTENCY_KEY' USING ERRCODE = '22023';
    END IF;

    -- Concurrency-Safe Transaction Advisory Lock
    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor_id::TEXT || ':' || p_idempotency_key, 0));

    -- 1. Idempotency Check
    SELECT * INTO v_receipt
    FROM public.tow_command_receipts
    WHERE actor_id = v_actor_id AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> p_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- Map target state to canonical command type
    v_command_type := CASE p_target_state
        WHEN 'EN_ROUTE' THEN 'START_EN_ROUTE'
        WHEN 'ARRIVED' THEN 'CONFIRM_ARRIVAL'
        WHEN 'LOADING' THEN 'START_LOADING'
        WHEN 'LOADED' THEN 'CONFIRM_LOADED'
        WHEN 'IN_TRANSIT' THEN 'START_TRANSIT'
        WHEN 'ARRIVED_DESTINATION' THEN 'CONFIRM_DESTINATION_ARRIVAL'
        WHEN 'UNLOADING' THEN 'START_UNLOADING'
        WHEN 'DELIVERED' THEN 'CONFIRM_DELIVERED'
        WHEN 'COMPLETED' THEN 'COMPLETE'
        WHEN 'CANCELLED' THEN 'CANCEL'
        WHEN 'DISPUTED' THEN 'RAISE_DISPUTE'
        ELSE 'COMPLETE'
    END;

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
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, NULL, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
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
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    -- 4. Authoritative State Machine Validation
    v_is_valid_transition := CASE
        WHEN v_current_job.state = 'ASSIGNED' AND p_target_state = 'EN_ROUTE' THEN true
        WHEN v_current_job.state = 'EN_ROUTE' AND p_target_state = 'ARRIVED' THEN true
        WHEN v_current_job.state = 'ARRIVED' AND p_target_state = 'LOADING' THEN true
        WHEN v_current_job.state = 'LOADING' AND p_target_state = 'LOADED' THEN true
        WHEN v_current_job.state = 'LOADED' AND p_target_state = 'IN_TRANSIT' THEN true
        WHEN v_current_job.state = 'IN_TRANSIT' AND p_target_state = 'ARRIVED_DESTINATION' THEN true
        WHEN v_current_job.state = 'ARRIVED_DESTINATION' AND p_target_state = 'UNLOADING' THEN true
        WHEN v_current_job.state = 'UNLOADING' AND p_target_state = 'DELIVERED' THEN true
        WHEN v_current_job.state = 'DELIVERED' AND p_target_state = 'COMPLETED' THEN true
        WHEN v_current_job.state IN ('REQUESTED', 'MATCHING', 'ASSIGNED') AND p_target_state = 'CANCELLED' THEN true
        WHEN (v_current_job.state IN ('EN_ROUTE', 'ARRIVED', 'LOADING', 'LOADED', 'IN_TRANSIT', 'ARRIVED_DESTINATION', 'UNLOADING', 'DELIVERED'))
             AND p_target_state = 'DISPUTED' THEN true
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
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    -- 5. Role Authorization Guards
    IF p_target_state = 'COMPLETED' THEN
        IF v_current_job.customer_id <> v_actor_id THEN
            v_response := jsonb_build_object(
                'success', false,
                'error_code', 'FORBIDDEN_COMPLETION',
                'message', 'Solo el cliente o administración pueden completar el servicio.'
            );
            INSERT INTO public.tow_command_receipts (
                actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
            ) VALUES (
                v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
            );
            RETURN v_response;
        END IF;
    ELSIF p_target_state = 'CANCELLED' THEN
        IF v_current_job.customer_id <> v_actor_id THEN
            v_response := jsonb_build_object(
                'success', false,
                'error_code', 'FORBIDDEN_CANCELLATION',
                'message', 'Solo el cliente puede cancelar el servicio en esta etapa.'
            );
            INSERT INTO public.tow_command_receipts (
                actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
            ) VALUES (
                v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
            );
            RETURN v_response;
        END IF;
    ELSIF p_target_state = 'DISPUTED' THEN
        IF v_current_job.customer_id <> v_actor_id AND v_current_job.assigned_operator_id <> v_actor_id THEN
            v_response := jsonb_build_object(
                'success', false,
                'error_code', 'FORBIDDEN_DISPUTE',
                'message', 'Solo las partes involucradas pueden abrir una disputa.'
            );
            INSERT INTO public.tow_command_receipts (
                actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
            ) VALUES (
                v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
            );
            RETURN v_response;
        END IF;
    ELSE
        IF v_current_job.assigned_operator_id <> v_actor_id THEN
            v_response := jsonb_build_object(
                'success', false,
                'error_code', 'FORBIDDEN_OPERATOR_TRANSITION',
                'message', 'Solo el operador asignado puede ejecutar transiciones operativas.'
            );
            INSERT INTO public.tow_command_receipts (
                actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
            ) VALUES (
                v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
            );
            RETURN v_response;
        END IF;
    END IF;

    -- 6. Checkpoint Evidence Verification
    IF p_target_state IN ('LOADED', 'DELIVERED') THEN
        IF p_evidence_id IS NULL OR p_evidence_hash IS NULL OR NOT (p_evidence_hash ~ '^[a-fA-F0-9]{64}$') THEN
            v_response := jsonb_build_object(
                'success', false,
                'error_code', 'CANONICAL_EVIDENCE_REQUIRED',
                'message', 'Se requiere evidencia criptográfica válida (UUID e hash SHA-256 de 64 caracteres hex) para este hito de custodia.'
            );
            INSERT INTO public.tow_command_receipts (
                actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
            ) VALUES (
                v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
            );
            RETURN v_response;
        END IF;
    END IF;

    -- 7. Atomic CAS State Mutation
    UPDATE public.tow_jobs
    SET state = p_target_state,
        version = version + 1,
        updated_at = clock_timestamp(),
        custody_records = CASE
            WHEN p_evidence_id IS NOT NULL THEN
                custody_records || jsonb_build_object(
                    'checkpoint', CASE p_target_state WHEN 'LOADED' THEN 'LOADED_SECURED' ELSE 'DELIVERED' END,
                    'evidence_id', p_evidence_id,
                    'evidence_hash', p_evidence_hash,
                    'recorded_by', v_actor_id,
                    'recorded_at', clock_timestamp()
                )
            ELSE custody_records
        END
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
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_current_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
        );
        RETURN v_response;
    END IF;

    v_response := jsonb_build_object(
        'success', true,
        'job_id', v_updated_job.job_id,
        'state', v_updated_job.state,
        'version', v_updated_job.version,
        'updated_at', v_updated_job.updated_at
    );

    INSERT INTO public.tow_command_receipts (
        actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
    ) VALUES (
        v_actor_id, v_updated_job.job_id, p_job_id, v_command_type, p_idempotency_key, p_request_hash, v_response
    );

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
-- 6. STORED PROCEDURE: TOW REQUEST (tow_request_job)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_request_job(
    p_vehicle_summary TEXT,
    p_pickup_lat DOUBLE PRECISION,
    p_pickup_lng DOUBLE PRECISION,
    p_pickup_address TEXT,
    p_pickup_accuracy_meters REAL DEFAULT NULL,
    p_dest_lat DOUBLE PRECISION DEFAULT NULL,
    p_dest_lng DOUBLE PRECISION DEFAULT NULL,
    p_dest_address TEXT DEFAULT NULL,
    p_required_capabilities TEXT[] DEFAULT '{}',
    p_vehicle_vin TEXT DEFAULT NULL,
    p_notes TEXT DEFAULT NULL,
    p_quoted_price_minor BIGINT DEFAULT NULL,
    p_idempotency_key TEXT DEFAULT NULL,
    p_request_hash TEXT DEFAULT NULL,
    p_correlation_id TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := auth.uid();
    v_job_id TEXT;
    v_job RECORD;
    v_receipt RECORD;
    v_response JSONB;
BEGIN
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_pickup_lat NOT BETWEEN -90.0 AND 90.0 OR p_pickup_lng NOT BETWEEN -180.0 AND 180.0 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES' USING ERRCODE = '22023';
    END IF;

    IF p_pickup_accuracy_meters IS NOT NULL AND p_pickup_accuracy_meters < 0 THEN
        RAISE EXCEPTION 'INVALID_ACCURACY' USING ERRCODE = '22023';
    END IF;

    -- Idempotency check if key provided
    IF p_idempotency_key IS NOT NULL AND p_request_hash IS NOT NULL THEN
        PERFORM pg_advisory_xact_lock(hashtextextended(v_actor_id::TEXT || ':' || p_idempotency_key, 0));
        SELECT * INTO v_receipt
        FROM public.tow_command_receipts
        WHERE actor_id = v_actor_id AND idempotency_key = p_idempotency_key;

        IF FOUND THEN
            IF v_receipt.request_hash <> p_request_hash THEN
                RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
            END IF;
            RETURN v_receipt.response;
        END IF;
    END IF;

    v_job_id := 'tow_' || substr(replace(gen_random_uuid()::TEXT, '-', ''), 1, 24);

    INSERT INTO public.tow_jobs (
        job_id,
        customer_id,
        assigned_operator_id,
        assigned_tow_unit_id,
        state,
        vehicle_vin,
        vehicle_summary,
        required_capabilities,
        notes,
        pickup_lat,
        pickup_lng,
        pickup_address,
        pickup_accuracy_meters,
        dest_lat,
        dest_lng,
        dest_address,
        quoted_price_minor,
        final_price_minor,
        version,
        idempotency_key,
        correlation_id
    ) VALUES (
        v_job_id,
        v_actor_id,
        NULL,
        NULL,
        'REQUESTED',
        p_vehicle_vin,
        COALESCE(NULLIF(trim(p_vehicle_summary), ''), 'Vehículo'),
        COALESCE(p_required_capabilities, '{}'),
        p_notes,
        p_pickup_lat,
        p_pickup_lng,
        p_pickup_address,
        p_pickup_accuracy_meters,
        p_dest_lat,
        p_dest_lng,
        p_dest_address,
        p_quoted_price_minor,
        NULL,
        1,
        p_idempotency_key,
        p_correlation_id
    )
    RETURNING * INTO v_job;

    v_response := jsonb_build_object(
        'success', true,
        'job_id', v_job.job_id,
        'state', v_job.state,
        'version', v_job.version,
        'created_at', v_job.created_at
    );

    IF p_idempotency_key IS NOT NULL AND p_request_hash IS NOT NULL THEN
        INSERT INTO public.tow_command_receipts (
            actor_id, job_id, requested_job_id, command_type, idempotency_key, request_hash, response
        ) VALUES (
            v_actor_id, v_job.job_id, v_job.job_id, 'REQUEST', p_idempotency_key, p_request_hash, v_response
        );
    END IF;

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. STORED PROCEDURE: SECURE JOB DISCOVERY (tow_discover_jobs)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.tow_discover_jobs(
    p_tow_unit_id UUID,
    p_limit INTEGER DEFAULT 20
)
RETURNS TABLE (
    job_id TEXT,
    urgency TEXT,
    required_capabilities TEXT[],
    coarse_pickup_lat DOUBLE PRECISION,
    coarse_pickup_lng DOUBLE PRECISION,
    version BIGINT,
    created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor_id UUID := auth.uid();
    v_unit RECORD;
    v_provider_profile_id UUID;
BEGIN
    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- Verify active, verified provider profile
    SELECT pp.id INTO v_provider_profile_id
    FROM public.provider_profiles pp
    JOIN public.user_profiles up ON up.id = pp.user_profile_id
    WHERE up.auth_user_id = v_actor_id
      AND pp.provider_type = 'tow_provider'
      AND pp.status = 'active'
      AND pp.is_verified = TRUE
      AND pp.is_active = TRUE
    LIMIT 1;

    IF v_provider_profile_id IS NULL THEN
        RAISE EXCEPTION 'NOT_VERIFIED_TOW_PROVIDER' USING ERRCODE = '42501';
    END IF;

    -- Verify verified and available tow unit
    SELECT * INTO v_unit
    FROM public.tow_units
    WHERE id = p_tow_unit_id
      AND operator_id = v_actor_id
      AND verification_state = 'VERIFIED'
      AND availability_state = 'AVAILABLE';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'TOW_UNIT_NOT_ELIGIBLE' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT
        j.job_id,
        j.urgency,
        j.required_capabilities,
        round(j.pickup_lat::numeric, 2)::DOUBLE PRECISION AS coarse_pickup_lat,
        round(j.pickup_lng::numeric, 2)::DOUBLE PRECISION AS coarse_pickup_lng,
        j.version,
        j.created_at
    FROM public.tow_jobs j
    WHERE j.assigned_operator_id IS NULL
      AND j.state IN ('REQUESTED', 'MATCHING')
      AND v_unit.capabilities @> j.required_capabilities
    ORDER BY j.created_at DESC
    LIMIT LEAST(GREATEST(p_limit, 1), 50);
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. ROW LEVEL SECURITY (RLS) POLICIES
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.tow_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tow_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tow_command_receipts ENABLE ROW LEVEL SECURITY;

-- Drop legacy open discovery policy (P0 Privacy Fix)
DROP POLICY IF EXISTS tow_jobs_open_discovery ON public.tow_jobs;
DROP POLICY IF EXISTS tow_jobs_customer_insert ON public.tow_jobs;

-- Revoke direct INSERT on tow_jobs (must go through tow_request_job)
REVOKE INSERT ON public.tow_jobs FROM authenticated;
REVOKE INSERT ON public.tow_jobs FROM anon;

-- tow_jobs: Customer Read
DROP POLICY IF EXISTS tow_jobs_customer_read ON public.tow_jobs;
CREATE POLICY tow_jobs_customer_read ON public.tow_jobs
    FOR SELECT
    TO authenticated
    USING (customer_id = auth.uid());

-- tow_jobs: Assigned Operator Read
DROP POLICY IF EXISTS tow_jobs_operator_read ON public.tow_jobs;
CREATE POLICY tow_jobs_operator_read ON public.tow_jobs
    FOR SELECT
    TO authenticated
    USING (assigned_operator_id = auth.uid());

-- tow_units: Operator Read & Write own units
DROP POLICY IF EXISTS tow_units_operator_all ON public.tow_units;
CREATE POLICY tow_units_operator_all ON public.tow_units
    FOR ALL
    TO authenticated
    USING (operator_id = auth.uid())
    WITH CHECK (operator_id = auth.uid());

-- tow_command_receipts: Actor Read
DROP POLICY IF EXISTS tow_command_receipts_actor_read ON public.tow_command_receipts;
CREATE POLICY tow_command_receipts_actor_read ON public.tow_command_receipts
    FOR SELECT
    TO authenticated
    USING (actor_id = auth.uid());

-- Service Role Full Access
DROP POLICY IF EXISTS tow_jobs_service_role_all ON public.tow_jobs;
CREATE POLICY tow_jobs_service_role_all ON public.tow_jobs
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS tow_units_service_role_all ON public.tow_units;
CREATE POLICY tow_units_service_role_all ON public.tow_units
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS tow_command_receipts_service_role_all ON public.tow_command_receipts;
CREATE POLICY tow_command_receipts_service_role_all ON public.tow_command_receipts
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- Explicit Table Privileges (RLS filters row visibility)
GRANT SELECT ON public.tow_jobs TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tow_units TO authenticated;
GRANT SELECT ON public.tow_command_receipts TO authenticated;

GRANT ALL ON public.tow_jobs, public.tow_units, public.tow_command_receipts TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. PERMISSION BOUNDARIES (Explicit RPC Grants)
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.tow_claim_job(TEXT, UUID, BIGINT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.tow_claim_job(TEXT, UUID, BIGINT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.tow_execute_transition(TEXT, TEXT, BIGINT, TEXT, TEXT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.tow_execute_transition(TEXT, TEXT, BIGINT, TEXT, TEXT, TEXT, UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.tow_request_job(TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, REAL, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT[], TEXT, TEXT, BIGINT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.tow_request_job(TEXT, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, REAL, DOUBLE PRECISION, DOUBLE PRECISION, TEXT, TEXT[], TEXT, TEXT, BIGINT, TEXT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.tow_discover_jobs(UUID, INTEGER) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.tow_discover_jobs(UUID, INTEGER) TO authenticated;
