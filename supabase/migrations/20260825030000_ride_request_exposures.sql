-- Migration: 20260825030000_ride_request_exposures.sql
CREATE TABLE public.ride_request_exposures (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    driver_id uuid NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    tenant_id uuid NOT NULL REFERENCES public.ride_tenants(id),
    dispatch_wave smallint NOT NULL,
    candidate_rank smallint,
    distance_meters integer,
    eta_seconds integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    opened_at timestamptz,
    seen_at timestamptz,
    responded_at timestamptz,
    expired_at timestamptz,
    response_type text CHECK (response_type IS NULL OR response_type IN ('OFFERED', 'DECLINED', 'IGNORED', 'EXPIRED')),
    UNIQUE (request_id, driver_id)
);

COMMENT ON TABLE public.ride_request_exposures IS 'Tracks which drivers were exposed to a ride request';

CREATE INDEX ride_request_exposures_seen_idx ON public.ride_request_exposures USING btree (request_id, seen_at) WHERE seen_at IS NOT NULL;
CREATE INDEX ride_request_exposures_driver_pending_idx ON public.ride_request_exposures USING btree (driver_id, created_at DESC) WHERE responded_at IS NULL;

ALTER TABLE public.ride_request_exposures ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Drivers can SELECT their own exposures" ON public.ride_request_exposures
    FOR SELECT USING (driver_id = auth.uid());

CREATE POLICY "Passenger can SELECT exposures for their own requests" ON public.ride_request_exposures
    FOR SELECT USING (EXISTS (SELECT 1 FROM public.ride_requests WHERE id = public.ride_request_exposures.request_id AND passenger_id = auth.uid()));

CREATE POLICY "Active tenant dispatchers can SELECT tenant exposures" ON public.ride_request_exposures
    FOR SELECT USING (public.ride_is_active_tenant_member(tenant_id, ARRAY['dispatcher', 'admin']));

CREATE OR REPLACE FUNCTION public.ride_ack_request_delivered_v1(p_request_id uuid, p_driver_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    UPDATE public.ride_request_exposures
    SET delivered_at = now()
    WHERE request_id = p_request_id AND driver_id = p_driver_id AND delivered_at IS NULL;
END;
$$;
REVOKE ALL ON FUNCTION public.ride_ack_request_delivered_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_ack_request_delivered_v1 TO authenticated;

CREATE OR REPLACE FUNCTION public.ride_ack_request_seen_v1(p_request_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_count integer;
BEGIN
    UPDATE public.ride_request_exposures
    SET seen_at = COALESCE(seen_at, now()),
        opened_at = COALESCE(opened_at, now())
    WHERE request_id = p_request_id AND driver_id = auth.uid() AND seen_at IS NULL;
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    
    RETURN jsonb_build_object('acknowledged', v_count > 0);
END;
$$;
REVOKE ALL ON FUNCTION public.ride_ack_request_seen_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_ack_request_seen_v1 TO authenticated;

CREATE OR REPLACE FUNCTION public.ride_count_seen_drivers_v1(p_request_id uuid)
RETURNS integer
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_count integer;
    v_passenger_id uuid;
    v_tenant_id uuid;
BEGIN
    SELECT passenger_id, tenant_id INTO v_passenger_id, v_tenant_id
    FROM public.ride_requests
    WHERE id = p_request_id;
    
    IF v_passenger_id != auth.uid() AND NOT public.ride_is_active_tenant_member(v_tenant_id, ARRAY['dispatcher', 'admin']) THEN
        RAISE EXCEPTION 'Not authorized';
    END IF;
    
    SELECT count(*) INTO v_count
    FROM public.ride_request_exposures
    WHERE request_id = p_request_id AND seen_at IS NOT NULL;
    
    RETURN v_count;
END;
$$;
REVOKE ALL ON FUNCTION public.ride_count_seen_drivers_v1 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.ride_count_seen_drivers_v1 TO authenticated;
