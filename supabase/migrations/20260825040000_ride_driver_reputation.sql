-- ============================================================================
-- Migration: 20260825040000_ride_driver_reputation.sql
-- Description: Driver public profiles, Bayesian trust snapshots, compliments,
--              and verified post-trip rating submission.
-- ============================================================================

-- 1. Table: ride_driver_public_profiles
CREATE TABLE IF NOT EXISTS public.ride_driver_public_profiles (
    driver_id UUID PRIMARY KEY REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    profile_photo_url TEXT,
    identity_verified BOOLEAN NOT NULL DEFAULT false,
    license_verified BOOLEAN NOT NULL DEFAULT false,
    vehicle_verified BOOLEAN NOT NULL DEFAULT false,
    liveness_verified BOOLEAN NOT NULL DEFAULT false,
    liveness_checked_at TIMESTAMPTZ,
    insurance_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (insurance_status IN ('UNKNOWN', 'VERIFIED', 'PENDING', 'EXPIRED')),
    background_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (background_status IN ('UNKNOWN', 'VERIFIED', 'PENDING', 'FAILED')),
    trust_tier TEXT NOT NULL DEFAULT 'VERIFIED'
        CHECK (trust_tier IN ('VERIFIED', 'TRUSTED', 'ELITE', 'VANGUARD')),
    total_trips INTEGER NOT NULL DEFAULT 0 CHECK (total_trips >= 0),
    active_since TIMESTAMPTZ NOT NULL DEFAULT now(),
    bayesian_rating NUMERIC(3,2) CHECK (bayesian_rating IS NULL OR (bayesian_rating >= 1.00 AND bayesian_rating <= 5.00)),
    rating_count INTEGER NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    cancellation_rate NUMERIC(4,3) NOT NULL DEFAULT 0.000 CHECK (cancellation_rate >= 0.000 AND cancellation_rate <= 1.000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.ride_driver_public_profiles IS
'Passenger-safe public view of driver credentials, trust tier, and Bayesian rating.';

-- 2. Table: ride_driver_compliments
CREATE TABLE IF NOT EXISTS public.ride_driver_compliments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    passenger_id UUID NOT NULL REFERENCES auth.users(id),
    compliment TEXT NOT NULL CHECK (compliment IN (
        'COURTEOUS', 'SAFE_DRIVING', 'FAST_PICKUP', 'CLEAN_VEHICLE',
        'GOOD_COMMUNICATION', 'GOOD_NAVIGATION', 'HELPFUL', 'PROFESSIONAL'
    )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (trip_id, compliment)
);

COMMENT ON TABLE public.ride_driver_compliments IS
'Strict closed-taxonomy compliments emitted exclusively by actual passengers of COMPLETED trips.';

-- 3. Table: ride_driver_reputation_snapshots
CREATE TABLE IF NOT EXISTS public.ride_driver_reputation_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES public.ride_profiles(user_id) ON DELETE CASCADE,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    bayesian_rating NUMERIC(3,2) NOT NULL,
    raw_average_rating NUMERIC(3,2) NOT NULL,
    rating_count INTEGER NOT NULL,
    completed_trips INTEGER NOT NULL,
    cancellation_rate NUMERIC(4,3) NOT NULL DEFAULT 0.000,
    trust_tier TEXT NOT NULL,
    confidence_score NUMERIC(4,3) NOT NULL
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ride_driver_compliments_driver
    ON public.ride_driver_compliments (driver_id, compliment);

CREATE INDEX IF NOT EXISTS idx_ride_driver_reputation_snapshots_driver
    ON public.ride_driver_reputation_snapshots (driver_id, calculated_at DESC);

-- Enable RLS
ALTER TABLE public.ride_driver_public_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_driver_compliments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ride_driver_reputation_snapshots ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Authenticated users can view driver public profiles"
    ON public.ride_driver_public_profiles
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Authenticated users can view compliments"
    ON public.ride_driver_compliments
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Drivers can view their own reputation snapshots"
    ON public.ride_driver_reputation_snapshots
    FOR SELECT
    TO authenticated
    USING (driver_id = auth.uid());

-- RPC: Get Driver Public Profile (Safe View)
CREATE OR REPLACE FUNCTION public.ride_get_driver_public_profile_v1(
    p_driver_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_profile RECORD;
    v_compliments JSONB;
    v_vehicle RECORD;
BEGIN
    SELECT * INTO v_profile
    FROM public.ride_driver_public_profiles
    WHERE driver_id = p_driver_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('found', false);
    END IF;

    -- Aggregate compliments
    SELECT jsonb_object_agg(compliment, count) INTO v_compliments
    FROM (
        SELECT compliment, COUNT(*)::int AS count
        FROM public.ride_driver_compliments
        WHERE driver_id = p_driver_id
        GROUP BY compliment
    ) c;

    -- Get active verified vehicle summary
    SELECT id, display_name, make, model, model_year, color, plate_masked
    INTO v_vehicle
    FROM public.ride_driver_vehicles
    WHERE driver_id = p_driver_id AND is_active = true AND verification_status = 'VERIFIED'
    ORDER BY updated_at DESC
    LIMIT 1;

    RETURN jsonb_build_object(
        'found', true,
        'driver_id', v_profile.driver_id,
        'display_name', v_profile.display_name,
        'profile_photo_url', v_profile.profile_photo_url,
        'trust_tier', v_profile.trust_tier,
        'bayesian_rating', v_profile.bayesian_rating,
        'rating_count', v_profile.rating_count,
        'total_trips', v_profile.total_trips,
        'active_since', v_profile.active_since,
        'badges', jsonb_build_object(
            'identity_verified', v_profile.identity_verified,
            'license_verified', v_profile.license_verified,
            'vehicle_verified', v_profile.vehicle_verified,
            'liveness_verified', v_profile.liveness_verified,
            'liveness_checked_at', v_profile.liveness_checked_at,
            'insurance_status', v_profile.insurance_status,
            'background_status', v_profile.background_status
        ),
        'compliments', COALESCE(v_compliments, '{}'::jsonb),
        'active_vehicle', CASE WHEN v_vehicle.id IS NOT NULL THEN jsonb_build_object(
            'id', v_vehicle.id,
            'display_name', v_vehicle.display_name,
            'make', v_vehicle.make,
            'model', v_vehicle.model,
            'model_year', v_vehicle.model_year,
            'color', v_vehicle.color,
            'plate_masked', v_vehicle.plate_masked
        ) ELSE NULL END
    );
END;
$$;

-- RPC: Record Verified Trip Feedback
CREATE OR REPLACE FUNCTION public.ride_record_trip_feedback_v1(
    p_trip_id UUID,
    p_rating SMALLINT,
    p_compliments TEXT[] DEFAULT '{}'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_request RECORD;
    v_offer RECORD;
    v_comp TEXT;
    v_new_rating_count INT;
    v_raw_avg NUMERIC(3,2);
    v_bayesian NUMERIC(3,2);
    v_prior_c NUMERIC := 10.0;
    v_prior_m NUMERIC := 4.80;
    v_total_completed INT;
    v_trust_tier TEXT;
    v_confidence NUMERIC(4,3);
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_rating < 1 OR p_rating > 5 THEN
        RAISE EXCEPTION 'Rating must be between 1 and 5';
    END IF;

    -- Validate trip exists, is COMPLETED, and caller was passenger
    SELECT * INTO v_request
    FROM public.ride_requests
    WHERE id = p_trip_id AND passenger_id = v_caller_id AND state = 'COMPLETED';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Trip not found or not eligible for feedback';
    END IF;

    -- Find accepted driver offer
    SELECT * INTO v_offer
    FROM public.ride_offers
    WHERE request_id = p_trip_id AND status = 'ACCEPTED'
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Driver offer not found for this trip';
    END IF;

    -- Insert compliments
    IF p_compliments IS NOT NULL AND array_length(p_compliments, 1) > 0 THEN
        FOREACH v_comp IN ARRAY p_compliments
        LOOP
            IF v_comp IN ('COURTEOUS', 'SAFE_DRIVING', 'FAST_PICKUP', 'CLEAN_VEHICLE',
                          'GOOD_COMMUNICATION', 'GOOD_NAVIGATION', 'HELPFUL', 'PROFESSIONAL') THEN
                INSERT INTO public.ride_driver_compliments (driver_id, trip_id, passenger_id, compliment)
                VALUES (v_offer.driver_id, p_trip_id, v_caller_id, v_comp)
                ON CONFLICT (trip_id, compliment) DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    -- Ensure driver profile exists
    INSERT INTO public.ride_driver_public_profiles (driver_id, display_name)
    VALUES (v_offer.driver_id, 'Conductor')
    ON CONFLICT (driver_id) DO NOTHING;

    -- Calculate Bayesian rating & stats
    SELECT COUNT(*), COALESCE(AVG(p_rating), 5.0)
    INTO v_new_rating_count, v_raw_avg
    FROM (
        SELECT p_rating
    ) ratings;

    -- Update counts on public profile
    UPDATE public.ride_driver_public_profiles
    SET rating_count = rating_count + 1,
        total_trips = total_trips + 1,
        bayesian_rating = ROUND(((v_prior_c * v_prior_m + ((rating_count * COALESCE(bayesian_rating, v_prior_m)) + p_rating)) / (v_prior_c + rating_count + 1))::numeric, 2),
        updated_at = now()
    WHERE driver_id = v_offer.driver_id
    RETURNING total_trips, bayesian_rating, rating_count INTO v_total_completed, v_bayesian, v_new_rating_count;

    -- Determine Trust Tier
    IF v_total_completed >= 500 AND v_bayesian >= 4.90 THEN
        v_trust_tier := 'VANGUARD';
    ELSIF v_total_completed >= 100 AND v_bayesian >= 4.80 THEN
        v_trust_tier := 'ELITE';
    ELSIF v_total_completed >= 20 AND v_bayesian >= 4.50 THEN
        v_trust_tier := 'TRUSTED';
    ELSE
        v_trust_tier := 'VERIFIED';
    END IF;

    -- Confidence score = 1 - exp(-n / 50)
    v_confidence := ROUND((1.0 - exp(-v_new_rating_count::numeric / 50.0))::numeric, 3);

    UPDATE public.ride_driver_public_profiles
    SET trust_tier = v_trust_tier
    WHERE driver_id = v_offer.driver_id;

    -- Record snapshot
    INSERT INTO public.ride_driver_reputation_snapshots (
        driver_id, bayesian_rating, raw_average_rating, rating_count,
        completed_trips, trust_tier, confidence_score
    ) VALUES (
        v_offer.driver_id, v_bayesian, p_rating::numeric, v_new_rating_count,
        v_total_completed, v_trust_tier, v_confidence
    );

    RETURN jsonb_build_object(
        'success', true,
        'driver_id', v_offer.driver_id,
        'bayesian_rating', v_bayesian,
        'rating_count', v_new_rating_count,
        'trust_tier', v_trust_tier
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.ride_get_driver_public_profile_v1(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.ride_record_trip_feedback_v1(UUID, SMALLINT, TEXT[]) TO authenticated;
REVOKE ALL ON FUNCTION public.ride_get_driver_public_profile_v1(UUID) FROM public;
REVOKE ALL ON FUNCTION public.ride_record_trip_feedback_v1(UUID, SMALLINT, TEXT[]) FROM public;
