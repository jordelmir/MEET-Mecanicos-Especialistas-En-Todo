-- ============================================================================
-- Migration: 20260825050000_ride_auto_match.sql
-- Description: Transactional Auto-Match Engine with concurrency exclusion,
--              zero double-assignment guarantee, and multi-strategy ranking.
-- ============================================================================

-- 1. Table: ride_auto_match_policies
CREATE TABLE IF NOT EXISTS public.ride_auto_match_policies (
    request_id UUID PRIMARY KEY REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES public.ride_tenants(id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    strategy TEXT NOT NULL DEFAULT 'FASTEST_PICKUP'
        CHECK (strategy IN ('FASTEST_PICKUP', 'LOWEST_FARE', 'HIGHEST_TRUST', 'BALANCED')),
    max_fare_minor BIGINT NOT NULL CHECK (max_fare_minor > 0),
    minimum_trust_tier TEXT NOT NULL DEFAULT 'VERIFIED'
        CHECK (minimum_trust_tier IN ('VERIFIED', 'TRUSTED', 'ELITE', 'VANGUARD')),
    maximum_eta_seconds INTEGER NOT NULL DEFAULT 600 CHECK (maximum_eta_seconds > 0),
    allow_finishing_previous_trip BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.ride_auto_match_policies IS
'Passenger auto-match preferences and constraints for authoritative server-side assignment.';

-- Enable RLS
ALTER TABLE public.ride_auto_match_policies ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Passenger can manage their own auto-match policy"
    ON public.ride_auto_match_policies
    FOR ALL
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.ride_requests r
            WHERE r.id = request_id AND r.passenger_id = auth.uid()
        )
    );

-- RPC: Configure Auto-Match Policy
CREATE OR REPLACE FUNCTION public.ride_configure_auto_match_v1(
    p_request_id UUID,
    p_strategy TEXT,
    p_max_fare_minor BIGINT,
    p_minimum_trust_tier TEXT DEFAULT 'VERIFIED',
    p_maximum_eta_seconds INTEGER DEFAULT 600,
    p_allow_finishing_previous_trip BOOLEAN DEFAULT false,
    p_enabled BOOLEAN DEFAULT true
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_request RECORD;
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Validate request ownership & state
    SELECT * INTO v_request
    FROM public.ride_requests
    WHERE id = p_request_id AND passenger_id = v_caller_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Ride request not found or not owned by caller';
    END IF;

    IF v_request.state NOT IN ('SEARCHING', 'OFFERED') THEN
        RAISE EXCEPTION 'Cannot configure auto-match for request in state %', v_request.state;
    END IF;

    IF p_strategy NOT IN ('FASTEST_PICKUP', 'LOWEST_FARE', 'HIGHEST_TRUST', 'BALANCED') THEN
        RAISE EXCEPTION 'Invalid auto-match strategy %', p_strategy;
    END IF;

    IF p_minimum_trust_tier NOT IN ('VERIFIED', 'TRUSTED', 'ELITE', 'VANGUARD') THEN
        RAISE EXCEPTION 'Invalid trust tier %', p_minimum_trust_tier;
    END IF;

    INSERT INTO public.ride_auto_match_policies (
        request_id, tenant_id, enabled, strategy, max_fare_minor,
        minimum_trust_tier, maximum_eta_seconds, allow_finishing_previous_trip,
        updated_at
    ) VALUES (
        p_request_id, v_request.tenant_id, p_enabled, p_strategy, p_max_fare_minor,
        p_minimum_trust_tier, p_maximum_eta_seconds, p_allow_finishing_previous_trip,
        now()
    )
    ON CONFLICT (request_id) DO UPDATE SET
        enabled = EXCLUDED.enabled,
        strategy = EXCLUDED.strategy,
        max_fare_minor = EXCLUDED.max_fare_minor,
        minimum_trust_tier = EXCLUDED.minimum_trust_tier,
        maximum_eta_seconds = EXCLUDED.maximum_eta_seconds,
        allow_finishing_previous_trip = EXCLUDED.allow_finishing_previous_trip,
        updated_at = now();

    RETURN jsonb_build_object(
        'configured', true,
        'request_id', p_request_id,
        'strategy', p_strategy,
        'max_fare_minor', p_max_fare_minor
    );
END;
$$;

-- RPC: Try Auto-Match (Strictly Transactional with Row Locks)
CREATE OR REPLACE FUNCTION public.ride_try_auto_match_v1(
    p_request_id UUID,
    p_expected_version BIGINT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_request RECORD;
    v_policy RECORD;
    v_best_offer RECORD;
    v_tier_rank INT;
    v_new_version BIGINT;
BEGIN
    -- 1. Acquire exclusive row lock on ride_requests to guarantee 0 double-assignment
    SELECT * INTO v_request
    FROM public.ride_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('matched', false, 'reason', 'REQUEST_NOT_FOUND');
    END IF;

    IF v_request.state NOT IN ('SEARCHING', 'OFFERED') THEN
        RETURN jsonb_build_object('matched', false, 'reason', 'INVALID_STATE', 'state', v_request.state);
    END IF;

    IF v_request.version != p_expected_version THEN
        RETURN jsonb_build_object('matched', false, 'reason', 'VERSION_MISMATCH', 'current_version', v_request.version);
    END IF;

    -- 2. Load policy
    SELECT * INTO v_policy
    FROM public.ride_auto_match_policies
    WHERE request_id = p_request_id AND enabled = true;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('matched', false, 'reason', 'POLICY_DISABLED_OR_NOT_FOUND');
    END IF;

    -- Determine numeric threshold for minimum trust tier
    v_tier_rank := CASE v_policy.minimum_trust_tier
        WHEN 'VANGUARD' THEN 4
        WHEN 'ELITE' THEN 3
        WHEN 'TRUSTED' THEN 2
        ELSE 1
    END;

    -- 3. Discover best matching candidate offer with FOR UPDATE lock on offers
    -- Joins with driver profile and presence to verify eligibility
    SELECT 
        o.id AS offer_id,
        o.driver_id,
        o.vehicle_id,
        o.offered_fare_minor,
        o.estimated_arrival_minutes,
        p.trust_tier,
        p.bayesian_rating,
        p.total_trips,
        pr.availability
    INTO v_best_offer
    FROM public.ride_offers o
    JOIN public.ride_driver_public_profiles p ON p.driver_id = o.driver_id
    JOIN public.ride_driver_presence pr ON pr.driver_id = o.driver_id
    WHERE o.request_id = p_request_id
      AND o.status = 'PENDING'
      AND o.offered_fare_minor <= v_policy.max_fare_minor
      AND (o.estimated_arrival_minutes * 60) <= v_policy.maximum_eta_seconds
      AND (
          CASE p.trust_tier
              WHEN 'VANGUARD' THEN 4
              WHEN 'ELITE' THEN 3
              WHEN 'TRUSTED' THEN 2
              ELSE 1
          END
      ) >= v_tier_rank
      AND (
          pr.availability = 'AVAILABLE'
          OR (v_policy.allow_finishing_previous_trip AND pr.availability = 'FINISHING_CURRENT_TRIP')
      )
    ORDER BY
        CASE v_policy.strategy
            WHEN 'FASTEST_PICKUP' THEN o.estimated_arrival_minutes
            WHEN 'LOWEST_FARE' THEN o.offered_fare_minor
            ELSE 0
        END ASC,
        CASE v_policy.strategy
            WHEN 'HIGHEST_TRUST' THEN (
                CASE p.trust_tier
                    WHEN 'VANGUARD' THEN 4
                    WHEN 'ELITE' THEN 3
                    WHEN 'TRUSTED' THEN 2
                    ELSE 1
                END
            )
            ELSE 0
        END DESC,
        CASE v_policy.strategy
            WHEN 'HIGHEST_TRUST' THEN COALESCE(p.bayesian_rating, 0.0)
            WHEN 'BALANCED' THEN (
                -- Balanced utility score
                (10.0 - LEAST(o.estimated_arrival_minutes::numeric, 10.0)) * 0.4 +
                (CASE p.trust_tier WHEN 'VANGUARD' THEN 4 WHEN 'ELITE' THEN 3 WHEN 'TRUSTED' THEN 2 ELSE 1 END) * 0.3 +
                COALESCE(p.bayesian_rating, 4.0) * 0.3
            )
            ELSE 0.0
        END DESC,
        o.created_at ASC
    LIMIT 1
    FOR UPDATE OF o;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('matched', false, 'reason', 'NO_ELIGIBLE_OFFER');
    END IF;

    -- 4. Atomically transition the ride request to ASSIGNED
    v_new_version := v_request.version + 1;

    UPDATE public.ride_requests
    SET state = 'ASSIGNED',
        assigned_driver_id = v_best_offer.driver_id,
        assigned_vehicle_id = v_best_offer.vehicle_id,
        agreed_fare_minor = v_best_offer.offered_fare_minor,
        version = v_new_version,
        updated_at = now()
    WHERE id = p_request_id;

    -- 5. Mark winning offer as ACCEPTED
    UPDATE public.ride_offers
    SET status = 'ACCEPTED',
        updated_at = now()
    WHERE id = v_best_offer.offer_id;

    -- 6. Expire all competing offers
    UPDATE public.ride_offers
    SET status = 'EXPIRED',
        updated_at = now()
    WHERE request_id = p_request_id
      AND id != v_best_offer.offer_id
      AND status = 'PENDING';

    -- 7. Update driver presence to EN_ROUTE_TO_PICKUP
    UPDATE public.ride_driver_presence
    SET availability = 'EN_ROUTE_TO_PICKUP',
        current_trip_id = p_request_id,
        updated_at = now()
    WHERE driver_id = v_best_offer.driver_id;

    RETURN jsonb_build_object(
        'matched', true,
        'request_id', p_request_id,
        'assigned_driver_id', v_best_offer.driver_id,
        'assigned_vehicle_id', v_best_offer.vehicle_id,
        'agreed_fare_minor', v_best_offer.offered_fare_minor,
        'strategy_applied', v_policy.strategy,
        'new_version', v_new_version
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.ride_configure_auto_match_v1(UUID, TEXT, BIGINT, TEXT, INTEGER, BOOLEAN, BOOLEAN) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_try_auto_match_v1(UUID, BIGINT) TO authenticated;
REVOKE ALL ON FUNCTION public.ride_configure_auto_match_v1(UUID, TEXT, BIGINT, TEXT, INTEGER, BOOLEAN, BOOLEAN) FROM public;
REVOKE ALL ON FUNCTION public.ride_try_auto_match_v1(UUID, BIGINT) FROM public;
