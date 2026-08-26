-- Migration: 20260825010000_ride_driver_presence.sql
CREATE TABLE public.ride_driver_presence (
    driver_id uuid PRIMARY KEY REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    vehicle_id uuid REFERENCES public.ride_driver_vehicles(id) ON DELETE SET NULL,
    tenant_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-00000000e1a1'::uuid REFERENCES public.ride_tenants(id),
    location extensions.geography(point, 4326),
    h3_r8 text,
    h3_r9 text,
    heading smallint CHECK (heading IS NULL OR heading BETWEEN 0 AND 359),
    speed_mps real CHECK (speed_mps IS NULL OR speed_mps >= 0),
    accuracy_m real CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
    location_seq bigint NOT NULL DEFAULT 0,
    availability text NOT NULL DEFAULT 'OFFLINE' CHECK (availability IN ('OFFLINE', 'AVAILABLE', 'OFFERING', 'RESERVED', 'FINISHING_CURRENT_TRIP', 'EN_ROUTE_TO_PICKUP', 'PICKUP_WAITING', 'IN_TRIP', 'PAUSED', 'SUSPENDED', 'STALE')),
    current_trip_id uuid REFERENCES public.ride_requests(id) ON DELETE SET NULL,
    next_job_enabled boolean NOT NULL DEFAULT false,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.ride_driver_presence IS 'Real-time location and availability status of drivers';

CREATE INDEX ride_driver_presence_location_idx ON public.ride_driver_presence USING gist (location);
CREATE INDEX ride_driver_presence_availability_idx ON public.ride_driver_presence USING btree (availability, tenant_id) WHERE availability NOT IN ('OFFLINE', 'SUSPENDED', 'STALE');

ALTER TABLE public.ride_driver_presence ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Drivers can SELECT their own presence" ON public.ride_driver_presence
    FOR SELECT USING (driver_id = auth.uid());

CREATE POLICY "Drivers can UPDATE their own location/availability only" ON public.ride_driver_presence
    FOR UPDATE USING (driver_id = auth.uid());

CREATE POLICY "Active tenant dispatchers can SELECT all presence in their tenant" ON public.ride_driver_presence
    FOR SELECT USING (public.ride_is_active_tenant_member(tenant_id, ARRAY['dispatcher', 'admin']));

CREATE OR REPLACE FUNCTION public.ride_set_driver_availability_v1(p_availability text, p_vehicle_id uuid DEFAULT NULL)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver_id uuid;
    v_updated_at timestamptz;
BEGIN
    v_driver_id := auth.uid();
    IF v_driver_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.ride_profiles WHERE user_id = v_driver_id) THEN
        RAISE EXCEPTION 'Driver profile not found';
    END IF;

    IF p_availability != 'OFFLINE' AND p_vehicle_id IS NULL THEN
        RAISE EXCEPTION 'Vehicle ID required when going online';
    END IF;

    IF p_availability != 'OFFLINE' THEN
        IF NOT EXISTS (SELECT 1 FROM public.ride_driver_vehicles WHERE id = p_vehicle_id AND driver_id = v_driver_id) THEN
            RAISE EXCEPTION 'Vehicle not found or not owned by driver';
        END IF;
    END IF;

    v_updated_at := now();

    INSERT INTO public.ride_driver_presence (driver_id, vehicle_id, availability, updated_at)
    VALUES (v_driver_id, p_vehicle_id, p_availability, v_updated_at)
    ON CONFLICT (driver_id) DO UPDATE SET
        vehicle_id = EXCLUDED.vehicle_id,
        availability = EXCLUDED.availability,
        updated_at = EXCLUDED.updated_at;

    RETURN jsonb_build_object('availability', p_availability, 'updated_at', v_updated_at);
END;
$$;
REVOKE ALL ON FUNCTION public.ride_set_driver_availability_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_set_driver_availability_v1 TO authenticated;

CREATE OR REPLACE FUNCTION public.ride_update_driver_location_v1(
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy real,
    p_heading smallint DEFAULT NULL,
    p_speed real DEFAULT NULL,
    p_seq bigint DEFAULT 0,
    p_h3_r8 text DEFAULT NULL,
    p_h3_r9 text DEFAULT NULL
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver_id uuid;
    v_presence public.ride_driver_presence;
    v_updated_at timestamptz;
BEGIN
    v_driver_id := auth.uid();
    IF v_driver_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_presence FROM public.ride_driver_presence WHERE driver_id = v_driver_id;
    
    IF v_presence IS NULL THEN
        RAISE EXCEPTION 'Driver presence not found';
    END IF;

    IF v_presence.availability IN ('OFFLINE', 'SUSPENDED') THEN
        RAISE EXCEPTION 'Driver is offline or suspended';
    END IF;

    IF p_seq <= v_presence.location_seq THEN
        RAISE EXCEPTION 'Stale location update';
    END IF;

    IF p_latitude < -90 OR p_latitude > 90 OR p_longitude < -180 OR p_longitude > 180 THEN
        RAISE EXCEPTION 'Invalid coordinates';
    END IF;

    v_updated_at := now();

    UPDATE public.ride_driver_presence SET
        location = extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography,
        heading = p_heading,
        speed_mps = p_speed,
        accuracy_m = p_accuracy,
        h3_r8 = p_h3_r8,
        h3_r9 = p_h3_r9,
        location_seq = p_seq,
        last_seen_at = v_updated_at,
        updated_at = v_updated_at
    WHERE driver_id = v_driver_id;

    RETURN jsonb_build_object('location_seq', p_seq, 'updated_at', v_updated_at);
END;
$$;
REVOKE ALL ON FUNCTION public.ride_update_driver_location_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_update_driver_location_v1 TO authenticated;

CREATE OR REPLACE FUNCTION public.ride_find_eligible_candidates_v1(
    p_longitude double precision,
    p_latitude double precision,
    p_radius_meters integer,
    p_tenant_id uuid,
    p_exclude_driver_ids uuid[] DEFAULT '{}'
) RETURNS TABLE (
    driver_id uuid,
    vehicle_id uuid,
    distance_meters double precision,
    availability text,
    h3_r8 text,
    speed_mps real,
    heading smallint,
    last_seen_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.driver_id,
        p.vehicle_id,
        extensions.ST_Distance(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography) AS distance_meters,
        p.availability,
        p.h3_r8,
        p.speed_mps,
        p.heading,
        p.last_seen_at
    FROM public.ride_driver_presence p
    WHERE p.tenant_id = p_tenant_id
        AND p.availability IN ('AVAILABLE', 'FINISHING_CURRENT_TRIP')
        AND p.last_seen_at > (now() - interval '5 minutes')
        AND NOT (p.driver_id = ANY(p_exclude_driver_ids))
        AND extensions.ST_DWithin(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography, p_radius_meters)
    ORDER BY distance_meters ASC
    LIMIT 50;
END;
$$;
REVOKE ALL ON FUNCTION public.ride_find_eligible_candidates_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_find_eligible_candidates_v1 TO authenticated;
