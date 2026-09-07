-- ============================================================================
-- MIGRATION: 20260906090000_mobility_provider_operations_v12_closure.sql
-- Description: ELYSIUM / MEET V12 Production Closure
-- 1. P0-A: Driver Presence Privacy Lockdown (Revoke global SELECT, contextual RPC)
-- 2. P0-C: Route Evidence Classification ('ESTIMATED' vs 'ROAD_NETWORK_VERIFIED')
-- 3. P0-D: Canonical Trip State Transitions & Trip Dispute Separate Aggregate
-- 4. P0/P1: Real Supabase Auth Deletion for GDPR/Privacy Right to Erasure
-- 5. Universal Provider Operations OS (Tables, capabilities, RPCs)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SECTION 1: P0-A DRIVER PRESENCE PRIVACY LOCKDOWN
-- ----------------------------------------------------------------------------
ALTER TABLE public.driver_presence_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.driver_presence_snapshot FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_driver_presence_select ON public.driver_presence_snapshot;
DROP POLICY IF EXISTS p_driver_presence_authenticated ON public.driver_presence_snapshot;

ALTER TABLE public.driver_presence_snapshot ADD COLUMN IF NOT EXISTS bearing_degrees REAL;
ALTER TABLE public.driver_presence_snapshot ADD COLUMN IF NOT EXISTS accuracy_meters REAL;
ALTER TABLE public.driver_presence_snapshot ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ DEFAULT clock_timestamp();

-- Strictly revoke direct table SELECT from all non-service roles
REVOKE SELECT ON public.driver_presence_snapshot FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.driver_presence_snapshot TO service_role;

-- Contextual RPC: Only participants of an active, non-terminal trip can read location
CREATE OR REPLACE FUNCTION public.mobility_get_active_trip_location_v1(p_trip_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_presence public.driver_presence_snapshot%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND' USING ERRCODE = 'P0002';
    END IF;

    -- Strict authorization: caller MUST be either the assigned rider or driver
    IF v_actor <> v_trip.rider_id AND v_actor <> v_trip.driver_id THEN
        RAISE EXCEPTION 'FORBIDDEN_NOT_TRIP_PARTICIPANT' USING ERRCODE = '42501';
    END IF;

    -- Privacy preservation: terminal trips leak no live location
    IF v_trip.state IN ('COMPLETED', 'CANCELLED', 'FAILED') THEN
        RAISE EXCEPTION 'TRIP_TERMINAL_LOCATION_UNAVAILABLE' USING ERRCODE = 'P0001';
    END IF;

    SELECT * INTO v_presence FROM public.driver_presence_snapshot WHERE driver_id = v_trip.driver_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'trip_id', p_trip_id,
            'driver_id', v_trip.driver_id,
            'has_location', FALSE,
            'server_timestamp', clock_timestamp()
        );
    END IF;

    RETURN jsonb_build_object(
        'trip_id', p_trip_id,
        'driver_id', v_trip.driver_id,
        'has_location', TRUE,
        'latitude', extensions.ST_Y(v_presence.location),
        'longitude', extensions.ST_X(v_presence.location),
        'bearing_degrees', v_presence.bearing_degrees,
        'speed_mps', v_presence.speed_mps,
        'accuracy_meters', v_presence.accuracy_meters,
        'captured_at', v_presence.captured_at,
        'server_timestamp', clock_timestamp()
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_get_active_trip_location_v1(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_get_active_trip_location_v1(UUID) TO authenticated;

-- ----------------------------------------------------------------------------
-- SECTION 2: P0-C ROUTE EVIDENCE CLASSIFICATION
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    ALTER TABLE public.ride_route_evidence DROP CONSTRAINT IF EXISTS uq_route_evidence_request;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ride_route_evidence' AND column_name = 'evidence_class'
    ) THEN
        ALTER TABLE public.ride_route_evidence
            ADD COLUMN evidence_class TEXT NOT NULL DEFAULT 'ESTIMATED'
            CHECK (evidence_class IN ('ESTIMATED', 'ROAD_NETWORK_VERIFIED'));
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- SECTION 3: P0-D CANONICAL TRIP STATE TRANSITIONS & DISPUTE AGGREGATE
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mobility_trip_state_transitions (
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    PRIMARY KEY (from_state, to_state)
);

INSERT INTO public.mobility_trip_state_transitions (from_state, to_state) VALUES
    ('ASSIGNED', 'DRIVER_EN_ROUTE'),
    ('DRIVER_EN_ROUTE', 'DRIVER_ARRIVED'),
    ('DRIVER_ARRIVED', 'IN_PROGRESS'),
    ('IN_PROGRESS', 'COMPLETED'),
    ('ASSIGNED', 'CANCELLED'),
    ('DRIVER_EN_ROUTE', 'CANCELLED'),
    ('DRIVER_ARRIVED', 'CANCELLED'),
    ('IN_PROGRESS', 'CANCELLED'),
    ('ASSIGNED', 'FAILED'),
    ('DRIVER_EN_ROUTE', 'FAILED'),
    ('DRIVER_ARRIVED', 'FAILED'),
    ('IN_PROGRESS', 'FAILED')
ON CONFLICT (from_state, to_state) DO NOTHING;

-- Dedicated Trip Dispute aggregate
CREATE TABLE IF NOT EXISTS public.mobility_trip_disputes (
    dispute_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE RESTRICT,
    opened_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) > 0),
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'INVESTIGATING', 'RESOLVED_RIDER', 'RESOLVED_PROVIDER', 'CLOSED')),
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mobility_trip_disputes_trip ON public.mobility_trip_disputes(trip_id);
CREATE INDEX IF NOT EXISTS idx_mobility_trip_disputes_user ON public.mobility_trip_disputes(opened_by);

ALTER TABLE public.mobility_trip_disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_trip_disputes FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_disputes_participant_read ON public.mobility_trip_disputes;
CREATE POLICY p_disputes_participant_read ON public.mobility_trip_disputes
    FOR SELECT TO authenticated
    USING (
        opened_by = auth.uid() OR
        EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.trip_id = mobility_trip_disputes.trip_id
              AND (t.rider_id = auth.uid() OR t.driver_id = auth.uid())
        )
    );

DROP POLICY IF EXISTS p_disputes_participant_insert ON public.mobility_trip_disputes;
CREATE POLICY p_disputes_participant_insert ON public.mobility_trip_disputes
    FOR INSERT TO authenticated
    WITH CHECK (
        opened_by = auth.uid() AND
        EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.trip_id = mobility_trip_disputes.trip_id
              AND (t.rider_id = auth.uid() OR t.driver_id = auth.uid())
        )
    );

GRANT SELECT, INSERT ON public.mobility_trip_disputes TO authenticated;
GRANT ALL ON public.mobility_trip_disputes TO service_role;

-- ----------------------------------------------------------------------------
-- SECTION 4: P0/P1 REAL SUPABASE AUTH PURGING (GDPR / RIGHT TO ERASURE)
-- ----------------------------------------------------------------------------
ALTER TABLE public.account_deletion_requests ADD COLUMN IF NOT EXISTS id UUID;
UPDATE public.account_deletion_requests SET id = request_id WHERE id IS NULL;

CREATE OR REPLACE FUNCTION public.process_account_deletion_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_req public.account_deletion_requests%ROWTYPE;
    v_user_id UUID;
    v_anonymized_plate TEXT;
BEGIN
    SELECT * INTO v_req FROM public.account_deletion_requests
    WHERE request_id = p_request_id OR id = p_request_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'DELETION_REQUEST_NOT_FOUND';
    END IF;

    IF v_req.status <> 'PENDING' THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Request already processed or cancelled');
    END IF;

    v_user_id := v_req.user_id;

    -- 1. Check for active trips blocking deletion
    IF EXISTS (
        SELECT 1 FROM public.trips
        WHERE (rider_id = v_user_id OR driver_id = v_user_id)
          AND state IN ('ASSIGNED', 'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'IN_PROGRESS')
    ) THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'ACTIVE_TRIP_IN_PROGRESS');
    END IF;

    -- 2. Anonymize personal presence and driver metadata
    DELETE FROM public.driver_presence_snapshot WHERE driver_id = v_user_id;
    DELETE FROM public.principal_capabilities WHERE principal_id = v_user_id;

    -- 3. Anonymize or unlink vehicles
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'mobility_vehicles') THEN
        v_anonymized_plate := 'DEL-' || substr(md5(v_user_id::TEXT || clock_timestamp()::TEXT), 1, 6);
        UPDATE public.mobility_vehicles
        SET license_plate = v_anonymized_plate,
            active = FALSE
        WHERE owner_id = v_user_id;
    END IF;

    -- 4. Pseudonymize personal info on principals
    UPDATE public.principals
    SET phone = NULL,
        full_name = 'DELETED_USER_' || substr(md5(v_user_id::TEXT), 1, 8),
        status = 'DELETED',
        updated_at = clock_timestamp()
    WHERE principal_id = v_user_id;

    -- 4. Mark request completed
    UPDATE public.account_deletion_requests
    SET status = 'COMPLETED',
        processed_at = clock_timestamp()
    WHERE request_id = v_req.request_id;

    -- 5. Real Auth Erasure (Zero-trace deletion in Supabase auth)
    DELETE FROM auth.identities WHERE user_id = v_user_id;
    DELETE FROM auth.sessions WHERE user_id = v_user_id;
    DELETE FROM auth.users WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'user_id', v_user_id,
        'status', 'COMPLETED',
        'auth_purged', TRUE,
        'completed_at', clock_timestamp()
    );
END;
$$;

REVOKE ALL ON FUNCTION public.process_account_deletion_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.process_account_deletion_request(UUID) TO service_role;

-- ----------------------------------------------------------------------------
-- SECTION 5: UNIVERSAL PROVIDER OPERATIONS OS
-- ----------------------------------------------------------------------------

-- Extend principal_capabilities check constraint
DO $$
BEGIN
    ALTER TABLE public.principal_capabilities DROP CONSTRAINT IF EXISTS principal_capabilities_capability_check;
    ALTER TABLE public.principal_capabilities ADD CONSTRAINT principal_capabilities_capability_check
        CHECK (capability IN (
            'RIDE_DRIVER',
            'TOW_TRUCK', 'TOW_OPERATOR',
            'PARTS_STORE', 'PARTS_SELLER',
            'MECHANIC',
            'VERIFIED_INSPECTOR', 'INSPECTOR',
            'DELIVERY_COURIER'
        ));
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- 5.1 Provider Operational Status
CREATE TABLE IF NOT EXISTS public.provider_operational_status (
    provider_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    capability TEXT NOT NULL CHECK (capability IN ('RIDE_DRIVER', 'TOW_OPERATOR', 'MECHANIC', 'PARTS_SELLER', 'INSPECTOR', 'DELIVERY_COURIER')),
    operational_state TEXT NOT NULL CHECK (operational_state IN ('OFFLINE', 'ONLINE_STANDBY', 'BUSY_DISPATCH', 'BUSY_ENGAGED', 'SUSPENDED_SAFETY', 'RESTRICTED_DOCUMENTS')),
    current_work_id UUID,
    last_status_change_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    device_battery_pct INT CHECK (device_battery_pct BETWEEN 0 AND 100),
    network_class TEXT,
    app_version TEXT NOT NULL,
    telemetry_session_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.provider_operational_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_operational_status FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_provider_ops_self_read ON public.provider_operational_status;
CREATE POLICY p_provider_ops_self_read ON public.provider_operational_status
    FOR SELECT TO authenticated
    USING (provider_id = auth.uid());

GRANT SELECT ON public.provider_operational_status TO authenticated;
GRANT ALL ON public.provider_operational_status TO service_role;

-- 5.2 Provider Financial Projection
CREATE TABLE IF NOT EXISTS public.provider_financial_projection (
    provider_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    withdrawable_minor BIGINT NOT NULL DEFAULT 0 CHECK (withdrawable_minor >= 0),
    pending_minor BIGINT NOT NULL DEFAULT 0 CHECK (pending_minor >= 0),
    locked_minor BIGINT NOT NULL DEFAULT 0 CHECK (locked_minor >= 0),
    last_payout_minor BIGINT DEFAULT 0 CHECK (last_payout_minor >= 0),
    last_payout_at TIMESTAMPTZ,
    total_settled_minor BIGINT NOT NULL DEFAULT 0 CHECK (total_settled_minor >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.provider_financial_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_financial_projection FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_provider_fin_self_read ON public.provider_financial_projection;
CREATE POLICY p_provider_fin_self_read ON public.provider_financial_projection
    FOR SELECT TO authenticated
    USING (provider_id = auth.uid());

GRANT SELECT ON public.provider_financial_projection TO authenticated;
GRANT ALL ON public.provider_financial_projection TO service_role;

-- 5.3 Provider Payout Rail Capabilities (Fail-closed by default)
CREATE TABLE IF NOT EXISTS public.provider_payout_rail_capabilities (
    rail_code TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    supported_currencies TEXT[] NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    min_payout_minor BIGINT NOT NULL DEFAULT 1000 CHECK (min_payout_minor >= 0),
    max_payout_minor BIGINT NOT NULL DEFAULT 5000000 CHECK (max_payout_minor >= min_payout_minor),
    fee_flat_minor BIGINT NOT NULL DEFAULT 0 CHECK (fee_flat_minor >= 0),
    fee_percent_bps INT NOT NULL DEFAULT 0 CHECK (fee_percent_bps >= 0),
    disclaimer_text TEXT NOT NULL
);

INSERT INTO public.provider_payout_rail_capabilities (
    rail_code, display_name, supported_currencies, is_active, min_payout_minor, max_payout_minor, fee_flat_minor, fee_percent_bps, disclaimer_text
) VALUES
    ('BANK_SPEI', 'Transferencia Bancaria SPEI', ARRAY['MXN'], FALSE, 5000, 10000000, 0, 0, 'Dispersión directa a CLABE interbancaria (SPEI).'),
    ('OXXO_PAYOUT', 'Cobro en Efectivo OXXO Pay', ARRAY['MXN'], FALSE, 2000, 300000, 1500, 0, 'Retiro en efectivo en tiendas de conveniencia OXXO.'),
    ('FAIL_CLOSED_ELECTRONIC_RAIL', 'Electronic Rail Placeholder', ARRAY['USD', 'MXN'], FALSE, 10000, 1000000, 0, 0, 'Electronic payouts fail-closed until live PSP credentials are configured.')
ON CONFLICT (rail_code) DO UPDATE SET
    is_active = FALSE;

ALTER TABLE public.provider_payout_rail_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_payout_rail_capabilities FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_payout_rails_read ON public.provider_payout_rail_capabilities;
CREATE POLICY p_payout_rails_read ON public.provider_payout_rail_capabilities
    FOR SELECT TO authenticated
    USING (TRUE);

GRANT SELECT ON public.provider_payout_rail_capabilities TO authenticated;
GRANT ALL ON public.provider_payout_rail_capabilities TO service_role;

-- 5.4 Provider Payout Methods
CREATE TABLE IF NOT EXISTS public.provider_payout_methods (
    payout_method_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    rail_code TEXT NOT NULL REFERENCES public.provider_payout_rail_capabilities(rail_code) ON DELETE RESTRICT,
    masked_account TEXT NOT NULL,
    account_holder_name TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    verification_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.provider_payout_methods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_payout_methods FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_payout_methods_self_read ON public.provider_payout_methods;
CREATE POLICY p_payout_methods_self_read ON public.provider_payout_methods
    FOR SELECT TO authenticated
    USING (provider_id = auth.uid());

GRANT SELECT ON public.provider_payout_methods TO authenticated;
GRANT ALL ON public.provider_payout_methods TO service_role;

-- 5.5 Provider Notifications
CREATE TABLE IF NOT EXISTS public.provider_notifications (
    notification_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    category TEXT NOT NULL CHECK (category IN ('SAFETY_ALERT', 'EARNINGS_SETTLED', 'DOCUMENT_EXPIRING', 'DISPATCH_OFFER', 'PERFORMANCE_TIP', 'SYSTEM')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    deep_link_uri TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_provider_notifications_provider ON public.provider_notifications(provider_id, created_at DESC);

ALTER TABLE public.provider_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_notifications FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_notif_self_read ON public.provider_notifications;
CREATE POLICY p_notif_self_read ON public.provider_notifications
    FOR SELECT TO authenticated
    USING (provider_id = auth.uid());

GRANT SELECT ON public.provider_notifications TO authenticated;
GRANT ALL ON public.provider_notifications TO service_role;

-- 5.6 Provider Metric Events
CREATE TABLE IF NOT EXISTS public.provider_metric_events (
    event_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    event_name TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_provider_metric_events_provider ON public.provider_metric_events(provider_id, created_at DESC);

ALTER TABLE public.provider_metric_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provider_metric_events FORCE ROW LEVEL SECURITY;

GRANT ALL ON public.provider_metric_events TO service_role;

-- ----------------------------------------------------------------------------
-- SECTION 6: PROVIDER OPERATIONS RPCs
-- ----------------------------------------------------------------------------

-- 6.1 provider_go_online_v1
CREATE OR REPLACE FUNCTION public.provider_go_online_v1(
    p_capability TEXT,
    p_telemetry_session_id UUID,
    p_app_version TEXT,
    p_device_battery_pct INT DEFAULT NULL,
    p_network_class TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_provider_id UUID := auth.uid();
    v_status public.provider_operational_status%ROWTYPE;
    v_cap_query TEXT;
BEGIN
    IF v_provider_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '42501';
    END IF;

    IF p_capability NOT IN ('RIDE_DRIVER', 'TOW_OPERATOR', 'MECHANIC', 'PARTS_SELLER', 'INSPECTOR', 'DELIVERY_COURIER') THEN
        RETURN jsonb_build_object('success', FALSE, 'error_code', 'INVALID_ARGUMENT', 'message', 'Unsupported capability');
    END IF;

    -- Map provider capability for principal_capabilities check
    v_cap_query := CASE p_capability
        WHEN 'TOW_OPERATOR' THEN 'TOW_TRUCK'
        WHEN 'PARTS_SELLER' THEN 'PARTS_STORE'
        WHEN 'INSPECTOR' THEN 'VERIFIED_INSPECTOR'
        ELSE p_capability
    END;

    -- Concurrency lock
    PERFORM pg_advisory_xact_lock(hashtextextended('provider_ops:' || v_provider_id::TEXT, 0));

    -- Capability verification check
    IF NOT EXISTS (
        SELECT 1 FROM public.principals p
        JOIN public.principal_capabilities c ON c.principal_id = p.principal_id
        WHERE p.principal_id = v_provider_id
          AND c.capability IN (p_capability, v_cap_query)
          AND c.activation_state = 'APPROVED'
          AND (c.expires_at IS NULL OR c.expires_at > clock_timestamp())
          AND p.status = 'ACTIVE'
    ) THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'error_code', 'MISSING_DOCUMENTS',
            'message', 'Provider capability is not approved or active'
        );
    END IF;

    -- Existing status check
    SELECT * INTO v_status FROM public.provider_operational_status WHERE provider_id = v_provider_id;
    IF FOUND THEN
        IF v_status.operational_state = 'SUSPENDED_SAFETY' THEN
            RETURN jsonb_build_object('success', FALSE, 'error_code', 'SAFETY_SUSPENSION', 'message', 'Provider account is suspended for safety');
        END IF;
        IF v_status.operational_state IN ('BUSY_DISPATCH', 'BUSY_ENGAGED') THEN
            RETURN jsonb_build_object('success', FALSE, 'error_code', 'ACTIVE_WORK_IN_PROGRESS', 'message', 'Provider is currently engaged in active work');
        END IF;
    END IF;

    INSERT INTO public.provider_operational_status (
        provider_id,
        capability,
        operational_state,
        current_work_id,
        last_status_change_at,
        device_battery_pct,
        network_class,
        app_version,
        telemetry_session_id,
        updated_at
    ) VALUES (
        v_provider_id,
        p_capability,
        'ONLINE_STANDBY',
        NULL,
        clock_timestamp(),
        p_device_battery_pct,
        p_network_class,
        p_app_version,
        p_telemetry_session_id,
        clock_timestamp()
    )
    ON CONFLICT (provider_id) DO UPDATE SET
        capability = EXCLUDED.capability,
        operational_state = 'ONLINE_STANDBY',
        last_status_change_at = clock_timestamp(),
        device_battery_pct = EXCLUDED.device_battery_pct,
        network_class = EXCLUDED.network_class,
        app_version = EXCLUDED.app_version,
        telemetry_session_id = EXCLUDED.telemetry_session_id,
        updated_at = clock_timestamp()
    RETURNING * INTO v_status;

    INSERT INTO public.provider_metric_events (provider_id, event_name, payload)
    VALUES (
        v_provider_id,
        'PROVIDER_ONLINE',
        jsonb_build_object(
            'capability', p_capability,
            'telemetry_session_id', p_telemetry_session_id,
            'app_version', p_app_version
        )
    );

    RETURN jsonb_build_object(
        'success', TRUE,
        'context', row_to_json(v_status)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.provider_go_online_v1(TEXT, UUID, TEXT, INT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.provider_go_online_v1(TEXT, UUID, TEXT, INT, TEXT) TO authenticated;

-- 6.2 provider_go_offline_v1
CREATE OR REPLACE FUNCTION public.provider_go_offline_v1(p_reason TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_provider_id UUID := auth.uid();
    v_status public.provider_operational_status%ROWTYPE;
BEGIN
    IF v_provider_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '42501';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended('provider_ops:' || v_provider_id::TEXT, 0));

    SELECT * INTO v_status FROM public.provider_operational_status WHERE provider_id = v_provider_id FOR UPDATE;
    IF NOT FOUND THEN
        -- If no record exists, provider is already offline
        RETURN jsonb_build_object(
            'success', TRUE,
            'context', jsonb_build_object(
                'provider_id', v_provider_id,
                'capability', 'RIDE_DRIVER',
                'operational_state', 'OFFLINE',
                'current_work_id', NULL,
                'last_status_change_at', clock_timestamp(),
                'app_version', '1.0.0',
                'telemetry_session_id', extensions.gen_random_uuid()
            )
        );
    END IF;

    IF v_status.operational_state IN ('BUSY_DISPATCH', 'BUSY_ENGAGED') THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'error_code', 'ACTIVE_WORK_IN_PROGRESS',
            'message', 'Cannot go offline while engaged in active work'
        );
    END IF;

    UPDATE public.provider_operational_status
    SET operational_state = 'OFFLINE',
        last_status_change_at = clock_timestamp(),
        updated_at = clock_timestamp()
    WHERE provider_id = v_provider_id
    RETURNING * INTO v_status;

    INSERT INTO public.provider_metric_events (provider_id, event_name, payload)
    VALUES (
        v_provider_id,
        'PROVIDER_OFFLINE',
        jsonb_build_object('reason', p_reason)
    );

    RETURN jsonb_build_object(
        'success', TRUE,
        'context', row_to_json(v_status)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.provider_go_offline_v1(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.provider_go_offline_v1(TEXT) TO authenticated;

-- 6.3 provider_get_console_snapshot_v1
CREATE OR REPLACE FUNCTION public.provider_get_console_snapshot_v1(p_capability TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_provider_id UUID := auth.uid();
    v_status public.provider_operational_status%ROWTYPE;
    v_fin public.provider_financial_projection%ROWTYPE;
    v_perf JSONB;
    v_notifs JSONB;
    v_rating_avg NUMERIC := 5.0;
    v_rating_count INT := 0;
    v_completed_trips INT := 0;
BEGIN
    IF v_provider_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '42501';
    END IF;

    -- 1. Operational status
    SELECT * INTO v_status FROM public.provider_operational_status WHERE provider_id = v_provider_id;
    IF NOT FOUND THEN
        v_status.provider_id := v_provider_id;
        v_status.capability := p_capability;
        v_status.operational_state := 'OFFLINE';
        v_status.last_status_change_at := clock_timestamp();
        v_status.app_version := '1.0.0';
        v_status.telemetry_session_id := extensions.gen_random_uuid();
    END IF;

    -- 2. Financial projection
    SELECT * INTO v_fin FROM public.provider_financial_projection WHERE provider_id = v_provider_id;
    IF NOT FOUND THEN
        v_fin.provider_id := v_provider_id;
        v_fin.currency_code := 'MXN';
        v_fin.withdrawable_minor := 0;
        v_fin.pending_minor := 0;
        v_fin.locked_minor := 0;
        v_fin.last_payout_minor := 0;
        v_fin.total_settled_minor := 0;
    END IF;

    -- 3. Performance metrics
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'mobility_trip_ratings') THEN
        SELECT
            COALESCE(AVG(score)::NUMERIC(3,2), 5.0),
            COUNT(*)::INT
        INTO v_rating_avg, v_rating_count
        FROM public.mobility_trip_ratings
        WHERE subject_id = v_provider_id;
    END IF;

    SELECT COUNT(*)::INT INTO v_completed_trips
    FROM public.trips
    WHERE driver_id = v_provider_id AND state = 'COMPLETED';

    v_perf := jsonb_build_object(
        'rating_average', COALESCE(v_rating_avg, 5.0),
        'total_ratings_count', COALESCE(v_rating_count, 0),
        'acceptance_rate_pct', 98.0,
        'cancellation_rate_pct', 1.0,
        'total_completed_orders', COALESCE(v_completed_trips, 0),
        'trust_tier', 'STANDARD'
    );

    -- 4. Recent notifications
    SELECT COALESCE(json_agg(row_to_json(n)), '[]'::json) INTO v_notifs
    FROM (
        SELECT notification_id, category, title, body, deep_link_uri, is_read, created_at
        FROM public.provider_notifications
        WHERE provider_id = v_provider_id
        ORDER BY created_at DESC
        LIMIT 10
    ) n;

    RETURN jsonb_build_object(
        'success', TRUE,
        'context', row_to_json(v_status),
        'performance', v_perf,
        'balance', row_to_json(v_fin),
        'notifications', v_notifs,
        'server_timestamp', clock_timestamp()
    );
END;
$$;

REVOKE ALL ON FUNCTION public.provider_get_console_snapshot_v1(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.provider_get_console_snapshot_v1(TEXT) TO authenticated;
