-- ============================================================================
-- Migration: 20260825070000_ride_next_job_reservations.sql
-- Description: Next-Job chained dispatch scheduler with strict privacy protections.
-- ============================================================================

-- 1. Table: ride_next_job_reservations
CREATE TABLE IF NOT EXISTS public.ride_next_job_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    current_trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    next_trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'RESERVED'
        CHECK (status IN ('RESERVED', 'ACTIVATED', 'CANCELLED', 'DELAYED')),
    current_trip_remaining_eta_seconds INTEGER NOT NULL CHECK (current_trip_remaining_eta_seconds >= 0),
    next_pickup_eta_seconds INTEGER NOT NULL CHECK (next_pickup_eta_seconds >= 0),
    max_tolerated_delay_seconds INTEGER NOT NULL DEFAULT 300,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    UNIQUE (next_trip_id)
);

COMMENT ON TABLE public.ride_next_job_reservations IS
'Chained dispatch queue allowing drivers finishing a trip to queue their next assignment with strict privacy isolation.';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ride_next_jobs_driver
    ON public.ride_next_job_reservations (driver_id, status);

CREATE INDEX IF NOT EXISTS idx_ride_next_jobs_current_trip
    ON public.ride_next_job_reservations (current_trip_id);

-- Enable RLS
ALTER TABLE public.ride_next_job_reservations ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Driver can view their own reservations"
    ON public.ride_next_job_reservations
    FOR SELECT
    TO authenticated
    USING (driver_id = auth.uid());

CREATE POLICY "Next trip passenger can view reservation status"
    ON public.ride_next_job_reservations
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.ride_requests r
            WHERE r.id = next_trip_id AND r.passenger_id = auth.uid()
        )
    );

-- RPC: Reserve Next Job
CREATE OR REPLACE FUNCTION public.ride_reserve_next_job_v1(
    p_driver_id UUID,
    p_current_trip_id UUID,
    p_next_trip_id UUID,
    p_remaining_current_eta_sec INTEGER,
    p_next_pickup_eta_sec INTEGER,
    p_max_tolerated_delay_sec INTEGER DEFAULT 300
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_presence RECORD;
    v_current_trip RECORD;
    v_next_trip RECORD;
    v_reservation_id UUID;
BEGIN
    -- Validate driver presence & next_job_enabled
    SELECT * INTO v_presence
    FROM public.ride_driver_presence
    WHERE driver_id = p_driver_id;

    IF NOT FOUND OR NOT v_presence.next_job_enabled THEN
        RETURN jsonb_build_object('success', false, 'reason', 'NEXT_JOB_NOT_ENABLED');
    END IF;

    -- Validate current trip
    SELECT * INTO v_current_trip
    FROM public.ride_requests
    WHERE id = p_current_trip_id AND assigned_driver_id = p_driver_id AND state IN ('IN_PROGRESS', 'ARRIVED_PICKUP');

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'reason', 'INVALID_CURRENT_TRIP');
    END IF;

    -- Validate next trip
    SELECT * INTO v_next_trip
    FROM public.ride_requests
    WHERE id = p_next_trip_id AND state IN ('SEARCHING', 'OFFERED');

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'reason', 'NEXT_TRIP_NOT_AVAILABLE');
    END IF;

    -- Insert reservation
    INSERT INTO public.ride_next_job_reservations (
        driver_id, current_trip_id, next_trip_id, status,
        current_trip_remaining_eta_seconds, next_pickup_eta_seconds,
        max_tolerated_delay_seconds
    ) VALUES (
        p_driver_id, p_current_trip_id, p_next_trip_id, 'RESERVED',
        p_remaining_current_eta_sec, p_next_pickup_eta_sec,
        p_max_tolerated_delay_sec
    )
    RETURNING id INTO v_reservation_id;

    -- Update driver presence to FINISHING_CURRENT_TRIP
    UPDATE public.ride_driver_presence
    SET availability = 'FINISHING_CURRENT_TRIP',
        updated_at = now()
    WHERE driver_id = p_driver_id;

    -- Transition next trip to ASSIGNED
    UPDATE public.ride_requests
    SET state = 'ASSIGNED',
        assigned_driver_id = p_driver_id,
        version = version + 1,
        updated_at = now()
    WHERE id = p_next_trip_id;

    RETURN jsonb_build_object(
        'success', true,
        'reservation_id', v_reservation_id,
        'driver_id', p_driver_id,
        'next_trip_id', p_next_trip_id,
        'status', 'RESERVED'
    );
END;
$$;

-- RPC: Activate Next Job (Triggered when current trip completes)
CREATE OR REPLACE FUNCTION public.ride_activate_next_job_v1(
    p_driver_id UUID,
    p_reservation_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_res RECORD;
BEGIN
    SELECT * INTO v_res
    FROM public.ride_next_job_reservations
    WHERE id = p_reservation_id AND driver_id = p_driver_id AND status = 'RESERVED';

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'reason', 'RESERVATION_NOT_FOUND');
    END IF;

    -- Activate reservation
    UPDATE public.ride_next_job_reservations
    SET status = 'ACTIVATED',
        activated_at = now()
    WHERE id = p_reservation_id;

    -- Transition driver presence to EN_ROUTE_TO_PICKUP on next trip
    UPDATE public.ride_driver_presence
    SET availability = 'EN_ROUTE_TO_PICKUP',
        current_trip_id = v_res.next_trip_id,
        updated_at = now()
    WHERE driver_id = p_driver_id;

    RETURN jsonb_build_object(
        'success', true,
        'reservation_id', p_reservation_id,
        'status', 'ACTIVATED',
        'active_trip_id', v_res.next_trip_id
    );
END;
$$;

-- RPC: Privacy-Safe Next Job Projection (Exposes ZERO previous passenger data)
CREATE OR REPLACE FUNCTION public.ride_get_next_job_privacy_projection_v1(
    p_next_trip_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_res RECORD;
BEGIN
    SELECT * INTO v_res
    FROM public.ride_next_job_reservations
    WHERE next_trip_id = p_next_trip_id AND status = 'RESERVED';

    IF NOT FOUND THEN
        RETURN jsonb_build_object('is_chained_service', false);
    END IF;

    RETURN jsonb_build_object(
        'is_chained_service', true,
        'status', v_res.status,
        'available_in_seconds', v_res.current_trip_remaining_eta_seconds,
        'pickup_eta_seconds', v_res.next_pickup_eta_seconds,
        'notice_es', 'El conductor está finalizando otro servicio cercano'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.ride_reserve_next_job_v1(UUID, UUID, UUID, INTEGER, INTEGER, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_activate_next_job_v1(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_get_next_job_privacy_projection_v1(UUID) TO authenticated;
REVOKE ALL ON FUNCTION public.ride_reserve_next_job_v1(UUID, UUID, UUID, INTEGER, INTEGER, INTEGER) FROM public;
REVOKE ALL ON FUNCTION public.ride_activate_next_job_v1(UUID, UUID) FROM public;
REVOKE ALL ON FUNCTION public.ride_get_next_job_privacy_projection_v1(UUID) FROM public;
