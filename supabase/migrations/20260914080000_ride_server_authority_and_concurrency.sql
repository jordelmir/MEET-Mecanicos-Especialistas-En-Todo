-- Migration: 20260914080000_ride_server_authority_and_concurrency.sql
-- Production Hardening P0: Server-authoritative ride state transitions and 100-way concurrency guards.

-- 1. Ensure state_version column on public.ride_requests
ALTER TABLE public.ride_requests
    ADD COLUMN IF NOT EXISTS state_version bigint NOT NULL DEFAULT 1;

-- Backfill state_version from version if version is higher
UPDATE public.ride_requests
   SET state_version = version
 WHERE state_version < version;

-- 2. Create command deduplication table
CREATE TABLE IF NOT EXISTS public.ride_command_dedup (
    idempotency_key uuid PRIMARY KEY,
    ride_id uuid NOT NULL,
    actor_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    command text NOT NULL,
    result jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

REVOKE ALL ON public.ride_command_dedup FROM anon, authenticated;

-- 3. mark_driver_en_route: Semantic transition from ACCEPTED/ASSIGNED -> DRIVER_EN_ROUTE
CREATE OR REPLACE FUNCTION public.mark_driver_en_route(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    IF v_ride.assigned_driver_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    IF v_ride.state NOT IN ('ASSIGNED', 'ACCEPTED') THEN
        RAISE EXCEPTION 'INVALID_TRANSITION'
            USING message = format('Cannot transition to DRIVER_EN_ROUTE from state %s', v_ride.state);
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET state = 'DRIVER_EN_ROUTE',
           state_version = v_next_version,
           version = v_next_version,
           updated_at = now()
     WHERE id = p_ride_id;

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'MARK_DRIVER_EN_ROUTE',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'DRIVER_EN_ROUTE',
            'state', 'DRIVER_EN_ROUTE',
            'version', v_next_version
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'DRIVER_EN_ROUTE',
        'state', 'DRIVER_EN_ROUTE',
        'version', v_next_version
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mark_driver_en_route(uuid, bigint, uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.mark_driver_en_route(uuid, bigint, uuid) TO authenticated;

-- 4. mark_driver_arrived: Semantic transition from DRIVER_EN_ROUTE -> ARRIVED
CREATE OR REPLACE FUNCTION public.mark_driver_arrived(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    IF v_ride.assigned_driver_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    IF v_ride.state <> 'DRIVER_EN_ROUTE' THEN
        RAISE EXCEPTION 'INVALID_TRANSITION'
            USING message = format('Cannot transition to ARRIVED from state %s', v_ride.state);
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET state = 'ARRIVED',
           state_version = v_next_version,
           version = v_next_version,
           updated_at = now()
     WHERE id = p_ride_id;

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'MARK_DRIVER_ARRIVED',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'ARRIVED',
            'state', 'ARRIVED',
            'version', v_next_version
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'ARRIVED',
        'state', 'ARRIVED',
        'version', v_next_version
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mark_driver_arrived(uuid, bigint, uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.mark_driver_arrived(uuid, bigint, uuid) TO authenticated;

-- 5. start_ride: Semantic transition from ARRIVED/PASSENGER_ONBOARD -> IN_PROGRESS
CREATE OR REPLACE FUNCTION public.start_ride(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    IF v_ride.assigned_driver_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    IF v_ride.state NOT IN ('ARRIVED', 'PASSENGER_ONBOARD') THEN
        RAISE EXCEPTION 'INVALID_TRANSITION'
            USING message = format('Cannot start ride from state %s', v_ride.state);
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET state = 'IN_PROGRESS',
           state_version = v_next_version,
           version = v_next_version,
           updated_at = now()
     WHERE id = p_ride_id;

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'START_RIDE',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'IN_PROGRESS',
            'state', 'IN_PROGRESS',
            'version', v_next_version
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'IN_PROGRESS',
        'state', 'IN_PROGRESS',
        'version', v_next_version
    );
END;
$$;

REVOKE ALL ON FUNCTION public.start_ride(uuid, bigint, uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.start_ride(uuid, bigint, uuid) TO authenticated;

-- 6. complete_ride: Semantic transition from IN_PROGRESS -> COMPLETED
CREATE OR REPLACE FUNCTION public.complete_ride(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    IF v_ride.assigned_driver_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    IF v_ride.state <> 'IN_PROGRESS' THEN
        RAISE EXCEPTION 'INVALID_TRANSITION'
            USING message = format('Cannot complete ride from state %s', v_ride.state);
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET state = 'COMPLETED',
           state_version = v_next_version,
           version = v_next_version,
           completed_at = now(),
           updated_at = now()
     WHERE id = p_ride_id;

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'COMPLETE_RIDE',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'COMPLETED',
            'state', 'COMPLETED',
            'version', v_next_version
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'COMPLETED',
        'state', 'COMPLETED',
        'version', v_next_version
    );
END;
$$;

REVOKE ALL ON FUNCTION public.complete_ride(uuid, bigint, uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.complete_ride(uuid, bigint, uuid) TO authenticated;

-- 7. cancel_ride: Semantic authorized cancellation
CREATE OR REPLACE FUNCTION public.cancel_ride(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid,
    p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    IF v_ride.passenger_id IS DISTINCT FROM v_actor AND
       v_ride.assigned_driver_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    -- Terminal states cannot be cancelled
    IF v_ride.state IN ('COMPLETED', 'CANCELLED') THEN
        RAISE EXCEPTION 'INVALID_TRANSITION'
            USING message = format('Cannot cancel already terminal ride in state %s', v_ride.state);
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET state = 'CANCELLED',
           state_version = v_next_version,
           version = v_next_version,
           cancelled_at = now(),
           updated_at = now()
     WHERE id = p_ride_id;

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'CANCEL_RIDE',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'CANCELLED',
            'state', 'CANCELLED',
            'version', v_next_version,
            'reason', p_reason
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'CANCELLED',
        'state', 'CANCELLED',
        'version', v_next_version,
        'reason', p_reason
    );
END;
$$;

REVOKE ALL ON FUNCTION public.cancel_ride(uuid, bigint, uuid, text) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.cancel_ride(uuid, bigint, uuid, text) TO authenticated;

-- 8. Concurrency guard on ride acceptance (Section 4): Two drivers cannot win the same ride
CREATE OR REPLACE FUNCTION public.accept_ride_offer(
    p_ride_id uuid,
    p_offer_id uuid,
    p_driver_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_ride record;
    v_existing jsonb;
    v_next_version bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    SELECT result INTO v_existing
      FROM public.ride_command_dedup
     WHERE idempotency_key = p_idempotency_key;

    IF FOUND THEN
        RETURN v_existing;
    END IF;

    -- Strict FOR UPDATE row-level lock ensures serial execution
    SELECT * INTO v_ride
      FROM public.ride_requests
     WHERE id = p_ride_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_NOT_FOUND' USING errcode = 'P0002';
    END IF;

    IF v_ride.passenger_id IS DISTINCT FROM v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN' USING errcode = '42501';
    END IF;

    IF COALESCE(v_ride.state_version, v_ride.version) <> p_expected_version THEN
        RAISE EXCEPTION 'VERSION_CONFLICT' USING errcode = '40001';
    END IF;

    -- INVARIANT: If already assigned, conflict immediately
    IF v_ride.assigned_driver_id IS NOT NULL OR v_ride.state NOT IN ('SEARCHING', 'OFFERED') THEN
        RAISE EXCEPTION 'RIDE_ALREADY_ASSIGNED' USING errcode = '23505';
    END IF;

    v_next_version := COALESCE(v_ride.state_version, v_ride.version) + 1;

    UPDATE public.ride_requests
       SET assigned_driver_id = p_driver_id,
           state = 'ASSIGNED',
           state_version = v_next_version,
           version = v_next_version,
           updated_at = now()
     WHERE id = p_ride_id;

    -- Reject all other pending offers for this ride
    UPDATE public.ride_offers
       SET state = CASE WHEN id = p_offer_id THEN 'ACCEPTED' ELSE 'REJECTED' END,
           updated_at = now()
     WHERE request_id = p_ride_id
       AND state = 'PENDING';

    INSERT INTO public.ride_command_dedup (
        idempotency_key, ride_id, actor_id, command, result
    )
    VALUES (
        p_idempotency_key,
        p_ride_id,
        v_actor,
        'ACCEPT_OFFER',
        jsonb_build_object(
            'rideId', p_ride_id,
            'status', 'ASSIGNED',
            'state', 'ASSIGNED',
            'assignedDriverId', p_driver_id,
            'version', v_next_version
        )
    );

    RETURN jsonb_build_object(
        'rideId', p_ride_id,
        'status', 'ASSIGNED',
        'state', 'ASSIGNED',
        'assignedDriverId', p_driver_id,
        'version', v_next_version
    );
END;
$$;

REVOKE ALL ON FUNCTION public.accept_ride_offer(uuid, uuid, uuid, bigint, uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.accept_ride_offer(uuid, uuid, uuid, bigint, uuid) TO authenticated;
