-- Migration: 20260825020000_ride_dispatch_waves.sql
CREATE TABLE public.ride_dispatch_waves (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    tenant_id uuid NOT NULL REFERENCES public.ride_tenants(id),
    wave_number smallint NOT NULL CHECK (wave_number > 0),
    radius_meters integer NOT NULL CHECK (radius_meters > 0),
    max_eta_seconds integer NOT NULL CHECK (max_eta_seconds > 0),
    candidates_found integer NOT NULL DEFAULT 0,
    candidates_eligible integer NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT now(),
    expired_at timestamptz,
    UNIQUE (request_id, wave_number)
);

COMMENT ON TABLE public.ride_dispatch_waves IS 'Records dispatch waves for a ride request';

CREATE INDEX ride_dispatch_waves_request_id_idx ON public.ride_dispatch_waves USING btree (request_id, wave_number);

ALTER TABLE public.ride_dispatch_waves ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Passenger can see waves for their own request" ON public.ride_dispatch_waves
    FOR SELECT USING (EXISTS (SELECT 1 FROM public.ride_requests WHERE id = public.ride_dispatch_waves.request_id AND passenger_id = auth.uid()));

CREATE POLICY "Active tenant dispatchers can see tenant waves" ON public.ride_dispatch_waves
    FOR SELECT USING (public.ride_is_active_tenant_member(tenant_id, ARRAY['dispatcher', 'admin']));

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
BEGIN
    SELECT tenant_id, passenger_id, state INTO v_tenant_id, v_passenger_id, v_state
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

    -- At this point we would call ride_find_eligible_candidates_v1 and insert into exposures
    -- Since the RPC doesn't have pickup coordinates, we only insert the wave for now.

    INSERT INTO public.ride_dispatch_waves (request_id, tenant_id, wave_number, radius_meters, max_eta_seconds, candidates_found, candidates_eligible)
    VALUES (p_request_id, v_tenant_id, p_wave_number, p_radius_meters, p_max_eta_seconds, 0, 0)
    RETURNING id INTO v_wave_id;

    RETURN jsonb_build_object('wave_id', v_wave_id, 'wave_number', p_wave_number, 'candidates_found', 0, 'candidates_eligible', 0);
END;
$$;
REVOKE ALL ON FUNCTION public.ride_dispatch_publish_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_dispatch_publish_v1 TO authenticated;
