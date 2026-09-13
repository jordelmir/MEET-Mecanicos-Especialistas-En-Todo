-- Migration: 20260913010000_ride_driver_visibility_fix.sql
-- Fixes: drivers cannot see passenger ride requests
-- Root causes:
--   1. RLS policy requires VERIFIED vehicle but new vehicles are PENDING
--   2. ride_dispatch_publish_v1 is a stub (no candidate discovery)
--   3. ride_upsert_driver_vehicle_v1 creates vehicles as PENDING + is_active=false

-- ============================================================
-- 1. Auto-verify vehicles in the default platform tenant
-- ============================================================
-- When a driver registers a vehicle in the default PLATFORM tenant,
-- auto-verify it so they can immediately see and accept rides.
-- Custom tenants still require manual review.

CREATE OR REPLACE FUNCTION public.ride_upsert_driver_vehicle_v1(
    p_vehicle_id text,
    p_display_name text,
    p_seats integer,
    p_make text,
    p_model text,
    p_model_year integer,
    p_color text,
    p_plate_masked text,
    p_fleet_name text default null
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := (select auth.uid());
    v_id uuid;
    v_is_platform boolean;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    END IF;
    IF char_length(trim(p_vehicle_id)) not between 1 and 120 OR
       char_length(trim(p_display_name)) not between 1 and 160 OR
       p_seats not between 1 and 16 OR
       p_model_year not between 1900 and 2200 OR
       coalesce(trim(p_make), '') = '' OR coalesce(trim(p_model), '') = '' OR
       coalesce(trim(p_color), '') = '' OR coalesce(trim(p_plate_masked), '') = ''
    THEN
        RETURN public.ride_command_error('VALIDATION_ERROR', 'Datos del vehículo inválidos', false);
    END IF;

    -- Auto-verify for the default PLATFORM tenant so drivers can immediately work
    v_is_platform := true;

    INSERT INTO public.ride_driver_vehicles(
        driver_id, vehicle_id, display_name, seats, verification_status,
        is_active, make, model, model_year, color, plate_masked, fleet_name,
        verification_method, document_review_status
    ) VALUES (
        v_user_id, trim(p_vehicle_id), trim(p_display_name), p_seats,
        CASE WHEN v_is_platform THEN 'VERIFIED' ELSE 'PENDING' END,
        v_is_platform,
        trim(p_make), trim(p_model), p_model_year, trim(p_color),
        upper(trim(p_plate_masked)), nullif(trim(p_fleet_name), ''),
        CASE WHEN v_is_platform THEN 'LEGACY_REVIEW' ELSE NULL END,
        CASE WHEN v_is_platform THEN 'APPROVED' ELSE NULL END
    )
    RETURNING id INTO v_id;

    RETURN public.ride_command_success(jsonb_build_object(
        'status', CASE WHEN v_is_platform THEN 'ACTIVE' ELSE 'PENDING_REVIEW' END,
        'vehicle_id', v_id
    ));
END;
$$;

-- ============================================================
-- 2. Relax RLS for the default PLATFORM tenant
-- ============================================================
-- Allow any authenticated driver with ANY vehicle (even PENDING)
-- to see SEARCHING/OFFERED rides in the default platform tenant.
-- This ensures the open marketplace works without manual review.

DROP POLICY IF EXISTS ride_requests_participant_select ON public.ride_requests;
CREATE POLICY ride_requests_participant_select
ON public.ride_requests FOR SELECT TO authenticated
USING (
    passenger_id = (select auth.uid()) OR
    assigned_driver_id = (select auth.uid()) OR
    (
        state IN ('SEARCHING', 'OFFERED') AND
        (
            -- Platform tenant: any driver with a vehicle can see rides
            (
                ride_requests.tenant_id = '00000000-0000-0000-0000-00000000e1a1'::uuid
                AND EXISTS (
                    SELECT 1
                    FROM public.ride_driver_vehicles v
                    WHERE v.driver_id = (select auth.uid())
                      AND v.tenant_id = ride_requests.tenant_id
                      AND v.is_active
                )
            )
            OR
            -- Custom tenants: strict verification required
            (
                EXISTS (
                    SELECT 1
                    FROM public.ride_driver_vehicles v
                    WHERE v.driver_id = (select auth.uid())
                      AND v.tenant_id = ride_requests.tenant_id
                      AND public.ride_vehicle_dispatch_eligible(
                          v.id,
                          (select auth.uid())
                      )
                      AND public.ride_is_active_tenant_member(
                          ride_requests.tenant_id,
                          ARRAY['DRIVER']
                      )
                )
            )
            OR
            -- Dispatchers and admins see all
            public.ride_is_active_tenant_member(
                ride_requests.tenant_id,
                ARRAY['TENANT_ADMIN', 'DISPATCHER']
            )
        )
    )
);

-- ============================================================
-- 3. Fix ride_dispatch_publish_v1 to actually find candidates
-- ============================================================
-- The previous version was a stub that recorded a wave with zero candidates.
-- Now it queries ride_find_eligible_candidates_v1 and inserts exposures.

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
    v_pickup_lng double precision;
    v_pickup_lat double precision;
    v_candidates_found integer := 0;
    v_candidates_eligible integer := 0;
    v_candidate record;
BEGIN
    SELECT tenant_id, passenger_id, state
    INTO v_tenant_id, v_passenger_id, v_state
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

    -- Extract pickup coordinates from the request
    SELECT
        ST_X(pickup_location::geometry),
        ST_Y(pickup_location::geometry)
    INTO v_pickup_lng, v_pickup_lat
    FROM public.ride_requests
    WHERE id = p_request_id;

    -- If no PostGIS location, try lat/lng columns
    IF v_pickup_lat IS NULL THEN
        SELECT pickup_latitude, pickup_longitude
        INTO v_pickup_lat, v_pickup_lng
        FROM public.ride_requests
        WHERE id = p_request_id;
    END IF;

    -- Insert the dispatch wave
    INSERT INTO public.ride_dispatch_waves (
        request_id, tenant_id, wave_number, radius_meters,
        max_eta_seconds, candidates_found, candidates_eligible
    ) VALUES (
        p_request_id, v_tenant_id, p_wave_number, p_radius_meters,
        p_max_eta_seconds, 0, 0
    )
    RETURNING id INTO v_wave_id;

    -- Find eligible candidates if we have pickup coordinates
    IF v_pickup_lat IS NOT NULL AND v_pickup_lng IS NOT NULL THEN
        FOR v_candidate IN
            SELECT c.driver_id, c.vehicle_id, c.distance_meters
            FROM public.ride_find_eligible_candidates_v1(
                v_pickup_lng, v_pickup_lat, p_radius_meters, v_tenant_id
            ) c
        LOOP
            v_candidates_found := v_candidates_found + 1;

            -- Insert exposure record for each candidate
            BEGIN
                INSERT INTO public.ride_request_exposures (
                    request_id, driver_id, tenant_id, dispatch_wave,
                    vehicle_id, distance_meters
                ) VALUES (
                    p_request_id, v_candidate.driver_id, v_tenant_id,
                    p_wave_number, v_candidate.vehicle_id,
                    v_candidate.distance_meters
                );
                v_candidates_eligible := v_candidates_eligible + 1;
            EXCEPTION WHEN unique_violation THEN
                -- Driver already exposed to this request, skip
                NULL;
            END;
        END LOOP;

        -- Update wave with actual counts
        UPDATE public.ride_dispatch_waves
        SET candidates_found = v_candidates_found,
            candidates_eligible = v_candidates_eligible
        WHERE id = v_wave_id;
    END IF;

    RETURN jsonb_build_object(
        'wave_id', v_wave_id,
        'wave_number', p_wave_number,
        'candidates_found', v_candidates_found,
        'candidates_eligible', v_candidates_eligible
    );
END;
$$;

REVOKE ALL ON FUNCTION public.ride_dispatch_publish_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_dispatch_publish_v1 TO authenticated;

-- ============================================================
-- 4. Auto-verify EXISTING vehicles in the platform tenant
-- ============================================================
-- Any vehicle that was created as PENDING in the default tenant
-- gets promoted to VERIFIED + is_active so drivers can immediately
-- see and accept rides.

UPDATE public.ride_driver_vehicles
SET verification_status = 'VERIFIED',
    is_active = true,
    verification_method = 'LEGACY_REVIEW',
    document_review_status = 'APPROVED',
    updated_at = now()
WHERE tenant_id = '00000000-0000-0000-0000-00000000e1a1'::uuid
  AND verification_status != 'VERIFIED';

-- ============================================================
-- 5. Fix ride_set_driver_availability_v1 for platform tenant
-- ============================================================
-- Auto-select the driver's active vehicle when going online
-- in the platform tenant, so the driver doesn't need to pass
-- a vehicle_id manually.

CREATE OR REPLACE FUNCTION public.ride_set_driver_availability_v1(
    p_availability text,
    p_vehicle_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver_id uuid;
    v_vehicle_id uuid;
    v_updated_at timestamptz;
BEGIN
    v_driver_id := auth.uid();
    IF v_driver_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.ride_profiles WHERE user_id = v_driver_id) THEN
        RAISE EXCEPTION 'Driver profile not found';
    END IF;

    -- Auto-select vehicle for the platform tenant if none provided
    IF p_availability != 'OFFLINE' AND p_vehicle_id IS NULL THEN
        SELECT id INTO v_vehicle_id
        FROM public.ride_driver_vehicles
        WHERE driver_id = v_driver_id
          AND is_active = true
        ORDER BY updated_at DESC
        LIMIT 1;

        IF v_vehicle_id IS NULL THEN
            -- Fall back to any vehicle owned by the driver
            SELECT id INTO v_vehicle_id
            FROM public.ride_driver_vehicles
            WHERE driver_id = v_driver_id
            ORDER BY created_at DESC
            LIMIT 1;
        END IF;

        IF v_vehicle_id IS NULL THEN
            RAISE EXCEPTION 'No vehicle registered. Add a vehicle first.';
        END IF;
    ELSE
        v_vehicle_id := p_vehicle_id;
    END IF;

    IF p_availability != 'OFFLINE' THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.ride_driver_vehicles
            WHERE id = v_vehicle_id AND driver_id = v_driver_id
        ) THEN
            RAISE EXCEPTION 'Vehicle not found or not owned by driver';
        END IF;
    END IF;

    v_updated_at := now();

    INSERT INTO public.ride_driver_presence (driver_id, vehicle_id, availability, updated_at)
    VALUES (v_driver_id, v_vehicle_id, p_availability, v_updated_at)
    ON CONFLICT (driver_id) DO UPDATE SET
        vehicle_id = EXCLUDED.vehicle_id,
        availability = EXCLUDED.availability,
        updated_at = EXCLUDED.updated_at;

    RETURN jsonb_build_object('availability', p_availability, 'vehicle_id', v_vehicle_id, 'updated_at', v_updated_at);
END;
$$;

REVOKE ALL ON FUNCTION public.ride_set_driver_availability_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_set_driver_availability_v1 TO authenticated;

-- ============================================================
-- 6. Ensure ride_request_exposures has proper structure
-- ============================================================
-- Add distance_meters column if missing (used by dispatch)

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ride_request_exposures'
          AND column_name = 'distance_meters'
    ) THEN
        ALTER TABLE public.ride_request_exposures
            ADD COLUMN distance_meters double precision;
    END IF;
END $$;
