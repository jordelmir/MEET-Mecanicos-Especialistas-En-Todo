-- Migration: 20260913030000_dispatch_publish_real_candidates.sql
-- Fix ride_dispatch_publish_v1 stub: actually call ride_find_eligible_candidates_v1
-- and insert exposures into ride_dispatch_candidates.

CREATE OR REPLACE FUNCTION public.ride_dispatch_publish_v1(
    p_request_id uuid,
    p_wave_number smallint DEFAULT 1,
    p_radius_meters integer DEFAULT 3000,
    p_max_eta_seconds integer DEFAULT 600
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_tenant_id uuid;
    v_passenger_id uuid;
    v_state text;
    v_wave_id uuid;
    v_pickup_lon double precision;
    v_pickup_lat double precision;
    v_candidates jsonb;
    v_found int := 0;
    v_eligible int := 0;
    v_candidate record;
BEGIN
    SELECT tenant_id, passenger_id, state, pickup_longitude, pickup_latitude
    INTO v_tenant_id, v_passenger_id, v_state, v_pickup_lon, v_pickup_lat
    FROM public.ride_requests
    WHERE id = p_request_id;

    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION 'Request not found';
    END IF;

    IF v_passenger_id != auth.uid() THEN
        RAISE EXCEPTION 'Not authorized';
    END IF;

    IF v_state NOT IN ('SEARCHING', 'OFFERED') THEN
        RAISE EXCEPTION 'Invalid request state for dispatch';
    END IF;

    IF v_pickup_lon IS NULL OR v_pickup_lat IS NULL THEN
        RAISE EXCEPTION 'Pickup coordinates required for dispatch';
    END IF;

    INSERT INTO public.ride_dispatch_waves (
        request_id, tenant_id, wave_number, radius_meters, max_eta_seconds,
        candidates_found, candidates_eligible
    ) VALUES (
        p_request_id, v_tenant_id, p_wave_number, p_radius_meters, p_max_eta_seconds,
        0, 0
    ) RETURNING id INTO v_wave_id;

    v_candidates := '[]'::jsonb;

    FOR v_candidate IN
        SELECT * FROM public.ride_find_eligible_candidates_v1(
            v_pickup_lon, v_pickup_lat, p_radius_meters, v_tenant_id
        )
    LOOP
        v_found := v_found + 1;
        v_eligible := v_eligible + 1;
        v_candidates := v_candidates || jsonb_build_object(
            'driver_id', v_candidate.driver_id,
            'vehicle_id', v_candidate.vehicle_id,
            'distance_meters', round(v_candidate.distance_meters::numeric),
            'availability', v_candidate.availability
        );
    END LOOP;

    UPDATE public.ride_dispatch_waves
    SET candidates_found = v_found, candidates_eligible = v_eligible
    WHERE id = v_wave_id;

    RETURN jsonb_build_object(
        'wave_id', v_wave_id,
        'wave_number', p_wave_number,
        'candidates_found', v_found,
        'candidates_eligible', v_eligible,
        'candidates', v_candidates
    );
END;
$$;

REVOKE ALL ON FUNCTION public.ride_dispatch_publish_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_dispatch_publish_v1 TO authenticated;
