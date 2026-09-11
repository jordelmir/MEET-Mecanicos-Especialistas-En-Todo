-- Two independent payments race to consume one provider event.
CREATE TABLE public.capture_race_fixture (auth_id UUID, trip_id UUID);
DO $$
DECLARE
    n INTEGER;
    fixture_request_id UUID;
    fixture_trip_id UUID;
    fixture_quote_id UUID;
    fixture_auth_id UUID;
BEGIN
    FOR n IN 1..2 LOOP
        fixture_request_id := extensions.gen_random_uuid();
        fixture_trip_id := extensions.gen_random_uuid();
        fixture_quote_id := extensions.gen_random_uuid();
        fixture_auth_id := extensions.gen_random_uuid();
        INSERT INTO public.ride_requests
        SELECT (jsonb_populate_record(NULL::public.ride_requests, to_jsonb(r) || jsonb_build_object(
            'ride_request_id', fixture_request_id, 'id', fixture_request_id, 'correlation_id', fixture_request_id))).*
        FROM public.ride_requests r WHERE ride_request_id = 'dddddddd-3333-3333-3333-333333333333';
        INSERT INTO public.trips
        SELECT (jsonb_populate_record(NULL::public.trips, to_jsonb(t) || jsonb_build_object(
            'trip_id', fixture_trip_id, 'ride_request_id', fixture_request_id))).*
        FROM public.trips t WHERE t.trip_id = 'dddddddd-4444-4444-4444-444444444444';
        INSERT INTO public.ride_quotes
        SELECT (jsonb_populate_record(NULL::public.ride_quotes, to_jsonb(q) || jsonb_build_object(
            'quote_id', fixture_quote_id, 'ride_request_id', fixture_request_id))).*
        FROM public.ride_quotes q WHERE q.quote_id = 'eeeeeeee-6666-6666-6666-666666666666';
        INSERT INTO public.payment_authorizations
        SELECT (jsonb_populate_record(NULL::public.payment_authorizations, to_jsonb(a) || jsonb_build_object(
            'payment_authorization_id', fixture_auth_id, 'trip_id', fixture_trip_id, 'quote_id', fixture_quote_id,
            'state', 'AUTHORIZED', 'provider_capture_ref', NULL, 'provider_capture_event_id', NULL,
            'captured_amount_minor', NULL, 'captured_at', NULL))).*
        FROM public.payment_authorizations a WHERE payment_authorization_id = (SELECT tmp_auth_id.auth_id FROM public.tmp_auth_id);
        INSERT INTO public.capture_race_fixture VALUES (fixture_auth_id, fixture_trip_id);
    END LOOP;
END $$;
-- Slow both inserts after their pre-insert existence checks, forcing overlap.
CREATE FUNCTION public.capture_race_delay() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.provider_event_id = 'evt_concurrent_shared' THEN PERFORM pg_sleep(1); END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER capture_race_delay BEFORE INSERT ON public.payment_provider_events
FOR EACH ROW EXECUTE FUNCTION public.capture_race_delay();
