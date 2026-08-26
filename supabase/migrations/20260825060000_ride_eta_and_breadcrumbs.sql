-- ============================================================================
-- Migration: 20260825060000_ride_eta_and_breadcrumbs.sql
-- Description: Traffic-aware ETA provenance tracking and persistent location breadcrumbs.
-- ============================================================================

-- 1. Table: ride_eta_observations
CREATE TABLE IF NOT EXISTS public.ride_eta_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    origin_lat DOUBLE PRECISION NOT NULL,
    origin_lon DOUBLE PRECISION NOT NULL,
    destination_lat DOUBLE PRECISION NOT NULL,
    destination_lon DOUBLE PRECISION NOT NULL,
    eta_seconds INTEGER NOT NULL CHECK (eta_seconds >= 0),
    distance_meters INTEGER NOT NULL CHECK (distance_meters >= 0),
    provider TEXT NOT NULL CHECK (provider IN (
        'GOOGLE_TRAFFIC', 'ELYSIUM_HISTORICAL', 'OPEN_ROUTING', 'HAVERSINE_FALLBACK'
    )),
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence >= 0.0 AND confidence <= 1.0),
    traffic_condition TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (traffic_condition IN (
        'FREE_FLOW', 'MODERATE', 'HEAVY', 'CONGESTED', 'UNKNOWN'
    )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE public.ride_eta_observations IS
'Authoritative provenance record for calculated ETAs with explicit provider and confidence.';

-- 2. Table: ride_location_breadcrumbs
CREATE TABLE IF NOT EXISTS public.ride_location_breadcrumbs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    trip_id UUID REFERENCES public.ride_requests(id) ON DELETE SET NULL,
    seq BIGINT NOT NULL,
    location extensions.geography(point, 4326) NOT NULL,
    heading SMALLINT CHECK (heading IS NULL OR (heading >= 0 AND heading <= 359)),
    speed_mps REAL CHECK (speed_mps IS NULL OR speed_mps >= 0),
    accuracy_m REAL CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.ride_location_breadcrumbs IS
'Durable historical breadcrumb log for audit, dispute resolution, and safety analysis.';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ride_eta_observations_request
    ON public.ride_eta_observations (request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ride_breadcrumbs_trip
    ON public.ride_location_breadcrumbs (trip_id, recorded_at ASC)
    WHERE trip_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ride_breadcrumbs_driver_seq
    ON public.ride_location_breadcrumbs (driver_id, seq DESC);

-- Enable RLS
ALTER TABLE public.ride_eta_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_location_breadcrumbs ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Trip participants can view ETA observations"
    ON public.ride_eta_observations
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.ride_requests r
            WHERE r.id = request_id AND (r.passenger_id = auth.uid() OR r.assigned_driver_id = auth.uid())
        )
    );

CREATE POLICY "Trip participants can view location breadcrumbs"
    ON public.ride_location_breadcrumbs
    FOR SELECT
    TO authenticated
    USING (
        driver_id = auth.uid() OR
        EXISTS (
            SELECT 1 FROM public.ride_requests r
            WHERE r.id = trip_id AND r.passenger_id = auth.uid()
        )
    );

-- RPC: Record ETA Observation
CREATE OR REPLACE FUNCTION public.ride_record_eta_observation_v1(
    p_request_id UUID,
    p_driver_id UUID,
    p_origin_lat DOUBLE PRECISION,
    p_origin_lon DOUBLE PRECISION,
    p_dest_lat DOUBLE PRECISION,
    p_dest_lon DOUBLE PRECISION,
    p_eta_seconds INTEGER,
    p_distance_meters INTEGER,
    p_provider TEXT,
    p_confidence NUMERIC,
    p_traffic_condition TEXT DEFAULT 'UNKNOWN',
    p_ttl_seconds INTEGER DEFAULT 60
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_id UUID;
    v_expires_at TIMESTAMPTZ := now() + (p_ttl_seconds || ' seconds')::interval;
BEGIN
    INSERT INTO public.ride_eta_observations (
        request_id, driver_id, origin_lat, origin_lon, destination_lat, destination_lon,
        eta_seconds, distance_meters, provider, confidence, traffic_condition,
        created_at, expires_at
    ) VALUES (
        p_request_id, p_driver_id, p_origin_lat, p_origin_lon, p_dest_lat, p_dest_lon,
        p_eta_seconds, p_distance_meters, p_provider, p_confidence, p_traffic_condition,
        now(), v_expires_at
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- RPC: Get Latest Trip ETA
CREATE OR REPLACE FUNCTION public.ride_get_latest_trip_eta_v1(
    p_request_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_eta RECORD;
BEGIN
    SELECT * INTO v_eta
    FROM public.ride_eta_observations
    WHERE request_id = p_request_id AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('found', false);
    END IF;

    RETURN jsonb_build_object(
        'found', true,
        'eta_seconds', v_eta.eta_seconds,
        'distance_meters', v_eta.distance_meters,
        'provider', v_eta.provider,
        'confidence', v_eta.confidence,
        'traffic_condition', v_eta.traffic_condition,
        'generated_at', v_eta.created_at,
        'expires_at', v_eta.expires_at
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.ride_record_eta_observation_v1(UUID, UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER, INTEGER, TEXT, NUMERIC, TEXT, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_get_latest_trip_eta_v1(UUID) TO authenticated;
REVOKE ALL ON FUNCTION public.ride_record_eta_observation_v1(UUID, UUID, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER, INTEGER, TEXT, NUMERIC, TEXT, INTEGER) FROM public;
REVOKE ALL ON FUNCTION public.ride_get_latest_trip_eta_v1(UUID) FROM public;
