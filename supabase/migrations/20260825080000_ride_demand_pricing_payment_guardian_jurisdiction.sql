-- ============================================================================
-- Migration: 20260825080000_ride_demand_pricing_payment_guardian_jurisdiction.sql
-- Description: Advanced Mobility Stack: Demand Intelligence (H3), Market Pricing Ranges,
--              SINPE Payment Attestations, Guardian Mobility Safety Signals,
--              and Frozen Jurisdiction Policy Engine (Costa Rica).
-- ============================================================================

-- 1. Table: ride_demand_snapshots (H3 Hexagonal Demand)
CREATE TABLE IF NOT EXISTS public.ride_demand_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.ride_tenants(id),
    h3_r8 TEXT NOT NULL,
    demand_level TEXT NOT NULL CHECK (demand_level IN ('NORMAL', 'BUSY', 'HIGH', 'CRITICAL', 'UNKNOWN')),
    available_drivers INT NOT NULL DEFAULT 0 CHECK (available_drivers >= 0),
    open_requests INT NOT NULL DEFAULT 0 CHECK (open_requests >= 0),
    requests_per_driver NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    median_pickup_eta_seconds INT,
    median_offer_latency_seconds INT,
    recent_acceptance_rate NUMERIC(4,3) CHECK (recent_acceptance_rate IS NULL OR (recent_acceptance_rate >= 0.000 AND recent_acceptance_rate <= 1.000)),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE public.ride_demand_snapshots IS
'Authoritative demand and supply ratios indexed by H3 resolution 8 cells.';

CREATE INDEX IF NOT EXISTS idx_ride_demand_h3_exp ON public.ride_demand_snapshots (h3_r8, expires_at DESC);

-- 2. Table: ride_payment_intents (TruthProof Financial Settlement)
CREATE TABLE IF NOT EXISTS public.ride_payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    passenger_id UUID NOT NULL REFERENCES auth.users(id),
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency TEXT NOT NULL DEFAULT 'CRC',
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'SINPE_MOVIL', 'CARD', 'WALLET')),
    status TEXT NOT NULL DEFAULT 'PAYMENT_METHOD_SELECTED'
        CHECK (status IN (
            'PAYMENT_METHOD_SELECTED',
            'PAYMENT_REQUESTED',
            'USER_MARKED_SENT',
            'DRIVER_MARKED_RECEIVED',
            'EXTERNAL_SETTLEMENT_ATTESTED',
            'BANK_CONFIRMED',
            'DISPUTED'
        )),
    sinpe_phone_target TEXT,
    sinpe_reference_number TEXT,
    bank_confirmation_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Table: ride_payment_events (Audit Log)
CREATE TABLE IF NOT EXISTS public.ride_payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id UUID NOT NULL REFERENCES public.ride_payment_intents(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES auth.users(id),
    actor_role TEXT NOT NULL CHECK (actor_role IN ('PASSENGER', 'DRIVER', 'DISPATCHER', 'BANK_SYSTEM')),
    event_type TEXT NOT NULL,
    evidence_payload JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Table: ride_safety_signals (Guardian Mobility)
CREATE TABLE IF NOT EXISTS public.ride_safety_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    reporter_id UUID REFERENCES auth.users(id),
    signal_type TEXT NOT NULL CHECK (signal_type IN (
        'ROUTE_DEVIATION',
        'UNEXPECTED_STOP',
        'GPS_IMPOSSIBLE_JUMP',
        'DRIVER_IDENTITY_MISMATCH',
        'UNEXPECTED_TRIP_TERMINATION',
        'EXTREME_SPEED',
        'CRASH_ACCELERATION',
        'SOS_TRIGGERED'
    )),
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_resolved BOOLEAN NOT NULL DEFAULT false,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Table: ride_jurisdiction_policies (Costa Rica Regulatory Plug-in)
CREATE TABLE IF NOT EXISTS public.ride_jurisdiction_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code TEXT NOT NULL DEFAULT 'CR',
    region_name TEXT NOT NULL DEFAULT 'San Jose',
    policy_code TEXT NOT NULL UNIQUE,
    current_version INT NOT NULL DEFAULT 1,
    fare_rules JSONB NOT NULL,
    driver_requirements JSONB NOT NULL,
    vehicle_requirements JSONB NOT NULL,
    airport_rules JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_until TIMESTAMPTZ,
    legal_source TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. Table: ride_trip_legal_snapshots (Frozen Version per Trip)
CREATE TABLE IF NOT EXISTS public.ride_trip_legal_snapshots (
    trip_id UUID PRIMARY KEY REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    jurisdiction_policy_id UUID NOT NULL REFERENCES public.ride_jurisdiction_policies(id),
    jurisdiction_policy_version INT NOT NULL,
    pricing_policy_version INT NOT NULL,
    driver_eligibility_policy_version INT NOT NULL,
    frozen_rules JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.ride_demand_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_payment_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_payment_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_safety_signals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_jurisdiction_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_trip_legal_snapshots ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone authenticated can view demand snapshots"
    ON public.ride_demand_snapshots FOR SELECT TO authenticated USING (true);

CREATE POLICY "Trip participants can view payment intents"
    ON public.ride_payment_intents FOR SELECT TO authenticated
    USING (passenger_id = auth.uid() OR driver_id = auth.uid());

CREATE POLICY "Trip participants can view safety signals"
    ON public.ride_safety_signals FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.ride_requests r
            WHERE r.id = trip_id AND (r.passenger_id = auth.uid() OR r.assigned_driver_id = auth.uid())
        )
    );

CREATE POLICY "Anyone authenticated can view jurisdiction policies"
    ON public.ride_jurisdiction_policies FOR SELECT TO authenticated USING (true);

-- RPC: Get Demand Snapshot
CREATE OR REPLACE FUNCTION public.ride_get_demand_snapshot_v1(
    p_h3_r8 TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_snap RECORD;
BEGIN
    SELECT * INTO v_snap
    FROM public.ride_demand_snapshots
    WHERE h3_r8 = p_h3_r8 AND expires_at > now()
    ORDER BY calculated_at DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'found', false,
            'demand_level', 'NORMAL',
            'demand_multiplier', 1.00,
            'notice_es', 'Demanda normal'
        );
    END IF;

    RETURN jsonb_build_object(
        'found', true,
        'demand_level', v_snap.demand_level,
        'available_drivers', v_snap.available_drivers,
        'open_requests', v_snap.open_requests,
        'requests_per_driver', v_snap.requests_per_driver,
        'median_pickup_eta_seconds', v_snap.median_pickup_eta_seconds,
        'notice_es', CASE v_snap.demand_level
            WHEN 'HIGH' THEN 'Alta demanda. Hay menos conductores disponibles cerca.'
            WHEN 'CRITICAL' THEN 'Demanda crítica en esta zona.'
            WHEN 'BUSY' THEN 'Zona concurrida.'
            ELSE 'Demanda normal'
        END
    );
END;
$$;

-- RPC: Attest Payment Event (SINPE / Cash)
CREATE OR REPLACE FUNCTION public.ride_attest_payment_event_v1(
    p_trip_id UUID,
    p_new_status TEXT,
    p_reference_number TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_intent RECORD;
    v_role TEXT;
BEGIN
    SELECT * INTO v_intent
    FROM public.ride_payment_intents
    WHERE trip_id = p_trip_id;

    IF NOT FOUND THEN
        -- Auto create intent if first attestation
        INSERT INTO public.ride_payment_intents (
            trip_id, passenger_id, driver_id, amount_minor, payment_method, status, sinpe_reference_number
        )
        SELECT r.id, r.passenger_id, COALESCE(r.assigned_driver_id, v_caller_id),
               COALESCE(r.agreed_fare_minor, 3000), 'SINPE_MOVIL', p_new_status, p_reference_number
        FROM public.ride_requests r
        WHERE r.id = p_trip_id
        RETURNING * INTO v_intent;
    ELSE
        UPDATE public.ride_payment_intents
        SET status = p_new_status,
            sinpe_reference_number = COALESCE(p_reference_number, sinpe_reference_number),
            updated_at = now()
        WHERE id = v_intent.id;
    END IF;

    v_role := CASE WHEN v_caller_id = v_intent.passenger_id THEN 'PASSENGER' ELSE 'DRIVER' END;

    INSERT INTO public.ride_payment_events (
        payment_intent_id, actor_id, actor_role, event_type, evidence_payload
    ) VALUES (
        v_intent.id, v_caller_id, v_role, p_new_status,
        jsonb_build_object('reference', p_reference_number, 'attested_at', now())
    );

    RETURN jsonb_build_object(
        'success', true,
        'payment_intent_id', v_intent.id,
        'status', p_new_status
    );
END;
$$;

-- RPC: Emit Safety Signal (Guardian Mobility)
CREATE OR REPLACE FUNCTION public.ride_emit_safety_signal_v1(
    p_trip_id UUID,
    p_signal_type TEXT,
    p_severity TEXT,
    p_details JSONB DEFAULT '{}'::jsonb
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_signal_id UUID;
BEGIN
    INSERT INTO public.ride_safety_signals (
        trip_id, reporter_id, signal_type, severity, details
    ) VALUES (
        p_trip_id, auth.uid(), p_signal_type, p_severity, p_details
    )
    RETURNING id INTO v_signal_id;

    RETURN v_signal_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.ride_get_demand_snapshot_v1(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_attest_payment_event_v1(UUID, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_emit_safety_signal_v1(UUID, TEXT, TEXT, JSONB) TO authenticated;

REVOKE ALL ON FUNCTION public.ride_get_demand_snapshot_v1(TEXT) FROM public;
REVOKE ALL ON FUNCTION public.ride_attest_payment_event_v1(UUID, TEXT, TEXT) FROM public;
REVOKE ALL ON FUNCTION public.ride_emit_safety_signal_v1(UUID, TEXT, TEXT, JSONB) FROM public;
