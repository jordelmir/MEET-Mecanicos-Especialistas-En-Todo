-- ==============================================================================
-- MEET / ELYSIUM — GLOBAL PLATFORM & VEHICLE ECONOMY OS V13
-- Migration: 20260906100000_global_platform_vehicle_economy_v13.sql
--
-- Closes Gates 2, 4, 5, 7, 8, 9, 11, 13:
-- 1. Private Schemas (billing_private, private_api, private_events)
-- 2. Canonical Product Catalog, Entitlements & Market-Separated Pricing
-- 3. Entitlement Authority with Monotonic Idempotency & Google Play Binding
-- 4. Diagnostics to Transaction Intent (RepairIntent) & Service Dispatch Bridge
-- 5. Financial Idempotency Framework & Transactional Outbox
-- 6. Vehicle Passport & Immutable Provenance Graph (VehicleEvents)
-- 7. Trust, Fraud & Risk Decision Plane (RiskDecisions)
-- 8. Granular Kill Switches & Fail-Closed Market Activation Gate
-- 9. Multi-Tenant Enterprise / Fleet Organization Isolation
-- ==============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. PRIVATE SCHEMAS & PERMISSIONS LOCKDOWN
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS billing_private;
CREATE SCHEMA IF NOT EXISTS private_api;
CREATE SCHEMA IF NOT EXISTS private_events;

REVOKE ALL ON SCHEMA billing_private FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SCHEMA private_api FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SCHEMA private_events FROM PUBLIC, anon, authenticated;

GRANT ALL ON SCHEMA billing_private TO service_role;
GRANT ALL ON SCHEMA private_api TO service_role;
GRANT ALL ON SCHEMA private_events TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. CANONICAL PRODUCT CATALOG, ENTITLEMENTS & SEPARATED PRICING (GATE 2 & 12)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.catalog_products (
    product_key TEXT PRIMARY KEY,
    product_type TEXT NOT NULL CHECK (product_type IN (
        'SUBSCRIPTION',
        'ONE_TIME',
        'LEAD_CREDIT',
        'MARKETPLACE_FEE',
        'ENTERPRISE'
    )),
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.catalog_entitlements (
    entitlement_key TEXT PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.catalog_product_entitlements (
    product_key TEXT NOT NULL REFERENCES public.catalog_products(product_key) ON DELETE CASCADE,
    entitlement_key TEXT NOT NULL REFERENCES public.catalog_entitlements(entitlement_key) ON DELETE CASCADE,
    PRIMARY KEY (product_key, entitlement_key)
);

CREATE TABLE IF NOT EXISTS public.market_product_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_code TEXT NOT NULL,
    product_key TEXT NOT NULL REFERENCES public.catalog_products(product_key) ON DELETE CASCADE,
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    external_product_id TEXT,
    external_base_plan_id TEXT,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_price_dates CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX IF NOT EXISTS idx_market_prices_lookup
    ON public.market_product_prices(market_code, product_key, currency, valid_from);

ALTER TABLE public.catalog_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_entitlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_product_entitlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.market_product_prices ENABLE ROW LEVEL SECURITY;

-- Read policies for clients
CREATE POLICY "catalog_products_read_all" ON public.catalog_products
    FOR SELECT TO anon, authenticated USING (status = 'ACTIVE');

CREATE POLICY "catalog_entitlements_read_all" ON public.catalog_entitlements
    FOR SELECT TO anon, authenticated USING (TRUE);

CREATE POLICY "catalog_product_entitlements_read_all" ON public.catalog_product_entitlements
    FOR SELECT TO anon, authenticated USING (TRUE);

CREATE POLICY "market_product_prices_read_all" ON public.market_product_prices
    FOR SELECT TO anon, authenticated USING (
        valid_from <= NOW() AND (valid_until IS NULL OR valid_until > NOW())
    );

CREATE POLICY "catalog_products_service_role_all" ON public.catalog_products FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);
CREATE POLICY "catalog_entitlements_service_role_all" ON public.catalog_entitlements FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);
CREATE POLICY "catalog_product_entitlements_service_role_all" ON public.catalog_product_entitlements FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);
CREATE POLICY "market_product_prices_service_role_all" ON public.market_product_prices FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

-- Read access granted, mutation is strictly service_role only
GRANT SELECT ON public.catalog_products, public.catalog_entitlements, public.catalog_product_entitlements, public.market_product_prices TO anon, authenticated;
GRANT ALL ON public.catalog_products, public.catalog_entitlements, public.catalog_product_entitlements, public.market_product_prices TO service_role;

REVOKE INSERT, UPDATE, DELETE ON public.catalog_products FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.catalog_entitlements FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.catalog_product_entitlements FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.market_product_prices FROM anon, authenticated;

-- Seed canonical products
INSERT INTO public.catalog_products (product_key, product_type, status)
VALUES
    ('meet_free', 'ONE_TIME', 'ACTIVE'),
    ('meet_pro_monthly', 'SUBSCRIPTION', 'ACTIVE'),
    ('meet_pro_yearly', 'SUBSCRIPTION', 'ACTIVE'),
    ('meet_workshop_monthly', 'SUBSCRIPTION', 'ACTIVE'),
    ('fleet_starter_monthly', 'ENTERPRISE', 'ACTIVE'),
    ('lead_credit_single', 'LEAD_CREDIT', 'ACTIVE'),
    ('mobility_platform_fee', 'MARKETPLACE_FEE', 'ACTIVE'),
    ('service_platform_fee', 'MARKETPLACE_FEE', 'ACTIVE')
ON CONFLICT (product_key) DO UPDATE SET
    product_type = EXCLUDED.product_type,
    status = EXCLUDED.status,
    updated_at = NOW();

-- Seed canonical entitlements
INSERT INTO public.catalog_entitlements (entitlement_key, description)
VALUES
    ('ADVANCED_DIAGNOSTICS', 'Acceso a diagnósticos profundos, Freeze Frame y lectura en vivo avanzada'),
    ('AI_DIAGNOSIS', 'Inferencia de diagnóstico asistido por modelos de IA'),
    ('MODE_06', 'Lectura de monitores de diagnóstico a bordo Mode 06 no continuos'),
    ('PROFESSIONAL_REPORTS', 'Exportación de reportes PDF certificados con hash forense SHA-256'),
    ('WORKSHOP_WORKSPACE', 'Gestión multi-bahía y órdenes de trabajo para talleres mecánicos'),
    ('SERVICE_LEADS', 'Acceso a solicitudes de servicio automotriz y clientes calificados'),
    ('FLEET_TOOLS', 'Gestión de flotas, inspecciones DVIR y telemetría multi-vehicular')
ON CONFLICT (entitlement_key) DO UPDATE SET
    description = EXCLUDED.description;

-- Bind products to entitlements
INSERT INTO public.catalog_product_entitlements (product_key, entitlement_key)
VALUES
    ('meet_pro_monthly', 'ADVANCED_DIAGNOSTICS'),
    ('meet_pro_monthly', 'AI_DIAGNOSIS'),
    ('meet_pro_monthly', 'MODE_06'),
    ('meet_pro_monthly', 'PROFESSIONAL_REPORTS'),
    ('meet_pro_yearly', 'ADVANCED_DIAGNOSTICS'),
    ('meet_pro_yearly', 'AI_DIAGNOSIS'),
    ('meet_pro_yearly', 'MODE_06'),
    ('meet_pro_yearly', 'PROFESSIONAL_REPORTS'),
    ('meet_workshop_monthly', 'ADVANCED_DIAGNOSTICS'),
    ('meet_workshop_monthly', 'AI_DIAGNOSIS'),
    ('meet_workshop_monthly', 'MODE_06'),
    ('meet_workshop_monthly', 'PROFESSIONAL_REPORTS'),
    ('meet_workshop_monthly', 'WORKSHOP_WORKSPACE'),
    ('meet_workshop_monthly', 'SERVICE_LEADS'),
    ('fleet_starter_monthly', 'PROFESSIONAL_REPORTS'),
    ('fleet_starter_monthly', 'FLEET_TOOLS')
ON CONFLICT (product_key, entitlement_key) DO NOTHING;

-- Seed prices for CR_SJO and GLOBAL_DEFAULT
INSERT INTO public.market_product_prices (market_code, product_key, currency, amount_minor, external_product_id)
VALUES
    ('CR_SJO', 'meet_pro_monthly', 'CRC', 590000, 'pro_monthly'),
    ('CR_SJO', 'meet_pro_yearly', 'CRC', 5900000, 'pro_yearly'),
    ('CR_SJO', 'meet_workshop_monthly', 'CRC', 2490000, 'workshop_monthly'),
    ('GLOBAL_DEFAULT', 'meet_pro_monthly', 'USD', 999, 'pro_monthly'),
    ('GLOBAL_DEFAULT', 'meet_pro_yearly', 'USD', 9900, 'pro_yearly'),
    ('GLOBAL_DEFAULT', 'meet_workshop_monthly', 'USD', 4900, 'workshop_monthly')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ENTITLEMENT AUTHORITY & IDEMPOTENT MONOTONIC INGRESS (GATE 2)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS billing_private.provider_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider TEXT NOT NULL,
    provider_event_id TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    product_key TEXT NOT NULL REFERENCES public.catalog_products(product_key),
    provider_event_time TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_event_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_provider_events_user
    ON billing_private.provider_events(user_id, provider_event_time);

CREATE TABLE IF NOT EXISTS public.user_entitlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entitlement_key TEXT NOT NULL,
    product_id TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'google_play',
    status TEXT NOT NULL CHECK (status IN ('active', 'grace_period', 'on_hold', 'paused', 'expired', 'revoked')),
    starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    last_provider_event_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_entitlements_user_key
    ON public.user_entitlements(user_id, entitlement_key);

ALTER TABLE public.user_entitlements ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "entitlements readable by owner" ON public.user_entitlements;
CREATE POLICY "entitlements readable by owner"
    ON public.user_entitlements
    FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "entitlements no client write" ON public.user_entitlements;
CREATE POLICY "entitlements no client write"
    ON public.user_entitlements
    FOR ALL
    TO anon, authenticated
    USING (false)
    WITH CHECK (false);

-- Enhance public.user_entitlements with last_provider_event_time if not present
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'user_entitlements' 
          AND column_name = 'last_provider_event_time'
    ) THEN
        ALTER TABLE public.user_entitlements 
            ADD COLUMN last_provider_event_time TIMESTAMPTZ NOT NULL DEFAULT NOW();
    END IF;
END $$;

-- Authoritative apply function in billing_private
CREATE OR REPLACE FUNCTION billing_private.apply_verified_entitlement(
    p_provider TEXT,
    p_provider_event_id TEXT,
    p_user_id UUID,
    p_product_key TEXT,
    p_provider_event_time TIMESTAMPTZ,
    p_entitlement_state TEXT,
    p_valid_until TIMESTAMPTZ,
    p_payload JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, billing_private
AS $$
DECLARE
    v_event_inserted BOOLEAN := FALSE;
    v_entitlement_rec RECORD;
    v_existing_event_time TIMESTAMPTZ;
    v_entitlement_count INTEGER := 0;
BEGIN
    IF auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'FORBIDDEN_SERVICE_ROLE_ONLY' USING ERRCODE = '42501';
    END IF;

    -- 1. Idempotently insert provider event
    BEGIN
        INSERT INTO billing_private.provider_events (
            provider,
            provider_event_id,
            user_id,
            product_key,
            provider_event_time,
            payload
        ) VALUES (
            p_provider,
            p_provider_event_id,
            p_user_id,
            p_product_key,
            p_provider_event_time,
            p_payload
        );
        v_event_inserted := TRUE;
    EXCEPTION WHEN unique_violation THEN
        -- Replay detected: return NO-OP without re-applying side effects
        RETURN jsonb_build_object(
            'success', TRUE,
            'status', 'IDEMPOTENT_NOOP',
            'message', 'Provider event already processed'
        );
    END;

    -- 2. Query entitlements mapped to this product_key
    FOR v_entitlement_rec IN
        SELECT entitlement_key FROM public.catalog_product_entitlements
        WHERE product_key = p_product_key
    LOOP
        -- Check if newer state already exists
        SELECT last_provider_event_time INTO v_existing_event_time
        FROM public.user_entitlements
        WHERE user_id = p_user_id AND entitlement_key = v_entitlement_rec.entitlement_key;

        IF v_existing_event_time IS NOT NULL AND v_existing_event_time > p_provider_event_time THEN
            -- Stale event received out-of-order; do not regress state
            CONTINUE;
        END IF;

        -- Upsert entitlement
        INSERT INTO public.user_entitlements (
            user_id,
            entitlement_key,
            product_id,
            source,
            status,
            starts_at,
            expires_at,
            last_provider_event_time,
            metadata,
            updated_at
        ) VALUES (
            p_user_id,
            v_entitlement_rec.entitlement_key,
            p_product_key,
            p_provider,
            p_entitlement_state,
            NOW(),
            p_valid_until,
            p_provider_event_time,
            jsonb_build_object('provider_event_id', p_provider_event_id),
            NOW()
        )
        ON CONFLICT (id) DO UPDATE SET
            status = EXCLUDED.status,
            expires_at = EXCLUDED.expires_at,
            last_provider_event_time = EXCLUDED.last_provider_event_time,
            metadata = EXCLUDED.metadata,
            updated_at = NOW();

        v_entitlement_count := v_entitlement_count + 1;
    END LOOP;

    RETURN jsonb_build_object(
        'success', TRUE,
        'status', 'APPLIED',
        'entitlements_updated', v_entitlement_count
    );
END;
$$;

REVOKE ALL ON FUNCTION billing_private.apply_verified_entitlement FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION billing_private.apply_verified_entitlement TO service_role;

-- Evaluator for client capabilities
CREATE OR REPLACE FUNCTION public.billing_evaluate_capability_v1(
    p_capability TEXT,
    p_market_code TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_entitled BOOLEAN := FALSE;
    v_expires_at TIMESTAMPTZ;
BEGIN
    IF v_actor IS NULL THEN
        RETURN jsonb_build_object('allowed', FALSE, 'reason', 'UNAUTHENTICATED');
    END IF;

    -- Check if active entitlement exists
    SELECT TRUE, expires_at INTO v_entitled, v_expires_at
    FROM public.user_entitlements
    WHERE user_id = v_actor
      AND entitlement_key = p_capability
      AND status = 'active'
      AND (expires_at IS NULL OR expires_at > NOW())
    LIMIT 1;

    IF v_entitled IS TRUE THEN
        RETURN jsonb_build_object(
            'allowed', TRUE,
            'reason', 'ENTITLED',
            'expires_at', v_expires_at
        );
    ELSE
        RETURN jsonb_build_object(
            'allowed', FALSE,
            'reason', 'NOT_ENTITLED'
        );
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.billing_evaluate_capability_v1 TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. DIAGNOSTICS TO TRANSACTION INTENT (REPAIR INTENT) (GATE 4)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.service_definitions (
    id text primary key,
    domain text not null,
    display_name text not null,
    supported_modalities text[] not null,
    risk_tier text not null default 'STANDARD'
        check (risk_tier in ('STANDARD', 'ELEVATED', 'RESTRICTED')),
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

INSERT INTO public.service_definitions(id, domain, display_name, supported_modalities, risk_tier)
VALUES
    ('mechanical', 'Automotriz', 'Mecánica y diagnóstico', ARRAY['PHYSICAL'], 'ELEVATED'),
    ('roadside', 'Movilidad', 'Asistencia vial', ARRAY['PHYSICAL'], 'ELEVATED'),
    ('towing', 'Movilidad', 'Remolque y grúa', ARRAY['PHYSICAL'], 'ELEVATED'),
    ('inspection', 'Automotriz', 'Inspección certificada', ARRAY['PHYSICAL'], 'ELEVATED'),
    ('personal_transport', 'Movilidad', 'Transporte de personas', ARRAY['PHYSICAL'], 'ELEVATED')
ON CONFLICT (id) DO UPDATE SET
    domain = EXCLUDED.domain,
    display_name = EXCLUDED.display_name,
    supported_modalities = EXCLUDED.supported_modalities,
    risk_tier = EXCLUDED.risk_tier,
    active = true,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS public.universal_service_requests (
    id uuid primary key default gen_random_uuid(),
    client_id uuid not null references auth.users(id) on delete restrict,
    service_definition_id text not null references public.service_definitions(id),
    modality text not null check (modality in ('PHYSICAL', 'DIGITAL', 'HYBRID')),
    title text not null check (char_length(title) between 3 and 160),
    description text not null check (char_length(description) between 10 and 5000),
    intake jsonb not null default '{}'::jsonb,
    location extensions.geography,
    location_label text,
    offered_price_minor bigint not null check (offered_price_minor > 0),
    final_price_minor bigint check (final_price_minor is null or final_price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    state text not null default 'OPEN'
        check (state in ('DRAFT', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED')),
    assigned_provider_id uuid references auth.users(id) on delete restrict,
    accepted_offer_id uuid,
    payment_state text not null default 'NOT_STARTED'
        check (payment_state in ('NOT_STARTED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'REFUNDED', 'FAILED')),
    version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS public.universal_service_offers (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.universal_service_requests(id) on delete cascade,
    provider_id uuid not null references auth.users(id) on delete restrict,
    price_minor bigint not null check (price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    eta_minutes integer check (eta_minutes is null or eta_minutes between 0 and 43200),
    warranty_days integer not null default 0 check (warranty_days between 0 and 3650),
    scope jsonb not null default '{}'::jsonb,
    state text not null default 'PENDING'
        check (state in ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(request_id, provider_id)
);

ALTER TABLE public.universal_service_offers ENABLE ROW LEVEL SECURITY;

CREATE POLICY "universal_offers_read" ON public.universal_service_offers
    FOR SELECT TO authenticated USING (
        provider_id = auth.uid() OR EXISTS (
            SELECT 1 FROM public.universal_service_requests r
            WHERE r.id = request_id AND r.client_id = auth.uid()
        ) OR auth.role() = 'service_role'
    );

-- The August universal-services migration already creates this policy. Preserve
-- its existing authority (including any stricter deployed predicate) on replay.
DO $universal_offer_policy$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policy
        WHERE polrelid = 'public.universal_service_offers'::regclass
          AND polname = 'universal_offers_provider_write'
    ) THEN
        CREATE POLICY "universal_offers_provider_write" ON public.universal_service_offers
            FOR INSERT TO authenticated WITH CHECK (provider_id = auth.uid());
    END IF;
END
$universal_offer_policy$;

CREATE POLICY "universal_offers_service_role_all" ON public.universal_service_offers
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

GRANT SELECT, INSERT, UPDATE ON public.universal_service_offers TO authenticated;
GRANT ALL ON public.universal_service_offers TO service_role;

CREATE TABLE IF NOT EXISTS public.repair_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL,
    diagnostic_session_id UUID,
    client_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    observed_dtcs TEXT[] NOT NULL DEFAULT '{}',
    reported_symptoms TEXT[] NOT NULL DEFAULT '{}',
    recommended_action_class TEXT NOT NULL,
    confidence NUMERIC(4,3) CHECK (confidence BETWEEN 0.000 AND 1.000),
    truth_state TEXT NOT NULL CHECK (truth_state IN ('OBSERVED', 'PHYSICALLY_VERIFIED', 'DERIVED', 'ESTIMATED', 'HYPOTHESIS')),
    evidence_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'DISPATCHED', 'RESOLVED', 'DISCARDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_repair_intents_client ON public.repair_intents(client_id, state);
CREATE INDEX IF NOT EXISTS idx_repair_intents_vehicle ON public.repair_intents(vehicle_id);

ALTER TABLE public.repair_intents ENABLE ROW LEVEL SECURITY;

CREATE POLICY "repair_intents_owner_select" ON public.repair_intents
    FOR SELECT TO authenticated USING (client_id = auth.uid());

CREATE POLICY "repair_intents_owner_insert" ON public.repair_intents
    FOR INSERT TO authenticated WITH CHECK (client_id = auth.uid());

CREATE POLICY "repair_intents_service_role_all" ON public.repair_intents
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

GRANT SELECT, INSERT, UPDATE ON public.repair_intents TO authenticated;
GRANT ALL ON public.repair_intents TO service_role;
GRANT SELECT ON public.service_definitions TO anon, authenticated;
GRANT ALL ON public.service_definitions TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.universal_service_requests TO authenticated;
GRANT ALL ON public.universal_service_requests TO service_role;

-- RPC to create a verified repair intent from diagnostic findings
CREATE OR REPLACE FUNCTION public.mobility_create_repair_intent_v1(
    p_vehicle_id UUID,
    p_diagnostic_session_id UUID,
    p_observed_dtcs TEXT[],
    p_reported_symptoms TEXT[],
    p_recommended_action_class TEXT,
    p_confidence NUMERIC,
    p_truth_state TEXT,
    p_evidence_summary JSONB DEFAULT '{}'::jsonb
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_intent_id UUID;
    v_sanitized_truth TEXT;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- AI cannot assert PHYSICALLY_VERIFIED
    v_sanitized_truth := p_truth_state;
    IF v_sanitized_truth = 'PHYSICALLY_VERIFIED' AND (p_evidence_summary->>'inspector_id') IS NULL THEN
        v_sanitized_truth := 'DERIVED';
    END IF;

    INSERT INTO public.repair_intents (
        vehicle_id,
        diagnostic_session_id,
        client_id,
        observed_dtcs,
        reported_symptoms,
        recommended_action_class,
        confidence,
        truth_state,
        evidence_summary
    ) VALUES (
        p_vehicle_id,
        p_diagnostic_session_id,
        v_actor,
        COALESCE(p_observed_dtcs, '{}'),
        COALESCE(p_reported_symptoms, '{}'),
        p_recommended_action_class,
        p_confidence,
        v_sanitized_truth,
        COALESCE(p_evidence_summary, '{}'::jsonb)
    ) RETURNING id INTO v_intent_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'repair_intent_id', v_intent_id,
        'truth_state', v_sanitized_truth
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_create_repair_intent_v1 TO authenticated;

-- RPC to bridge RepairIntent to Service Marketplace Dispatch
CREATE OR REPLACE FUNCTION public.mobility_dispatch_service_from_intent_v1(
    p_repair_intent_id UUID,
    p_service_definition_id TEXT,
    p_modality TEXT,
    p_title TEXT,
    p_description TEXT,
    p_offered_price_minor BIGINT,
    p_currency TEXT,
    p_location extensions.geography DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_intent public.repair_intents%ROWTYPE;
    v_request_id UUID;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_intent FROM public.repair_intents
    WHERE id = p_repair_intent_id AND client_id = v_actor;

    IF v_intent.id IS NULL THEN
        RAISE EXCEPTION 'REPAIR_INTENT_NOT_FOUND' USING ERRCODE = 'P0002';
    END IF;

    IF v_intent.state <> 'OPEN' THEN
        RAISE EXCEPTION 'INTENT_ALREADY_DISPATCHED' USING ERRCODE = '23505';
    END IF;

    -- Create universal service request
    INSERT INTO public.universal_service_requests (
        client_id,
        service_definition_id,
        modality,
        title,
        description,
        intake,
        location,
        offered_price_minor,
        currency,
        state
    ) VALUES (
        v_actor,
        p_service_definition_id,
        p_modality,
        p_title,
        p_description,
        jsonb_build_object(
            'repair_intent_id', v_intent.id,
            'vehicle_id', v_intent.vehicle_id,
            'observed_dtcs', v_intent.observed_dtcs,
            'truth_state', v_intent.truth_state
        ),
        p_location,
        p_offered_price_minor,
        p_currency,
        'OPEN'
    ) RETURNING id INTO v_request_id;

    -- Transition RepairIntent state to DISPATCHED
    UPDATE public.repair_intents
    SET state = 'DISPATCHED', updated_at = NOW()
    WHERE id = v_intent.id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'service_request_id', v_request_id,
        'repair_intent_id', v_intent.id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_dispatch_service_from_intent_v1 TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. FINANCIAL IDEMPOTENCY FRAMEWORK & TRANSACTIONAL OUTBOX (GATE 5)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS private_api.idempotency_keys (
    actor_id UUID NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMMITTED', 'FAILED')),
    response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (actor_id, operation, idempotency_key)
);

CREATE TABLE IF NOT EXISTS private_events.outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    dedupe_key TEXT NOT NULL UNIQUE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_private_events_outbox_pending
    ON private_events.outbox(occurred_at) WHERE published_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. VEHICLE PASSPORT & PROVENANCE GRAPH (GATE 7)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.vehicle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN (
        'OBD_OBSERVATION',
        'DTC_OBSERVED',
        'MAINTENANCE_RECORDED',
        'PART_INSTALLED',
        'REPAIR_COMPLETED',
        'INSPECTION_COMPLETED',
        'MILEAGE_OBSERVED',
        'OWNERSHIP_EVENT',
        'SERVICE_COMPLETED'
    )),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source_kind TEXT NOT NULL CHECK (source_kind IN (
        'OBD_DONGLE',
        'CERTIFIED_INSPECTOR',
        'WORKSHOP_MECHANIC',
        'AI_ENGINE',
        'TELEMETRY_STREAM',
        'OWNER_DECLARATION'
    )),
    source_id UUID,
    truth_state TEXT NOT NULL CHECK (truth_state IN (
        'PHYSICALLY_VERIFIED',
        'OBSERVED',
        'ESTIMATED',
        'DERIVED',
        'HYPOTHESIS'
    )),
    confidence NUMERIC(4,3) CHECK (confidence BETWEEN 0.000 AND 1.000),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vehicle_events_lookup
    ON public.vehicle_events(vehicle_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS public.cloud_vehicles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id TEXT NOT NULL,
    vin TEXT,
    make TEXT NOT NULL,
    model TEXT NOT NULL,
    year INT,
    engine TEXT,
    plate TEXT,
    odometer INT DEFAULT 0,
    nickname TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION public.vehicle_is_owned_by_user(p_vehicle_id UUID, p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.cloud_vehicles cv
        WHERE cv.id = p_vehicle_id AND cv.user_id = p_user_id::text
    ) OR EXISTS (
        SELECT 1 FROM public.mobility_vehicles mv
        WHERE mv.vehicle_id = p_vehicle_id AND mv.owner_id = p_user_id
    );
$$;

GRANT EXECUTE ON FUNCTION public.vehicle_is_owned_by_user TO anon, authenticated;

ALTER TABLE public.vehicle_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "vehicle_events_owner_read" ON public.vehicle_events
    FOR SELECT TO authenticated USING (
        public.vehicle_is_owned_by_user(vehicle_events.vehicle_id, auth.uid())
        OR auth.role() = 'service_role'
    );

CREATE POLICY "vehicle_events_service_role_all" ON public.vehicle_events
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

GRANT SELECT ON public.vehicle_events TO authenticated;
GRANT ALL ON public.vehicle_events TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.cloud_vehicles TO authenticated;
GRANT ALL ON public.cloud_vehicles TO service_role;

-- Authoritative RPC to append verified passport event
CREATE OR REPLACE FUNCTION public.vehicle_append_passport_event_v1(
    p_vehicle_id UUID,
    p_event_type TEXT,
    p_source_kind TEXT,
    p_truth_state TEXT,
    p_confidence NUMERIC,
    p_payload JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_event_id UUID;
    v_hash TEXT;
    v_sanitized_truth TEXT;
BEGIN
    IF v_actor IS NULL AND auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- Truth classification safety guard
    v_sanitized_truth := p_truth_state;
    IF v_sanitized_truth = 'PHYSICALLY_VERIFIED' AND p_source_kind = 'AI_ENGINE' THEN
        v_sanitized_truth := 'DERIVED';
    END IF;

    -- Compute SHA-256 payload digest
    v_hash := encode(
        extensions.digest(
            jsonb_build_object(
                'vehicle_id', p_vehicle_id,
                'event_type', p_event_type,
                'payload', p_payload
            )::TEXT,
            'sha256'
        ),
        'hex'
    );

    INSERT INTO public.vehicle_events (
        vehicle_id,
        event_type,
        source_kind,
        source_id,
        truth_state,
        confidence,
        payload,
        payload_hash
    ) VALUES (
        p_vehicle_id,
        p_event_type,
        p_source_kind,
        v_actor,
        v_sanitized_truth,
        p_confidence,
        COALESCE(p_payload, '{}'::jsonb),
        v_hash
    ) RETURNING id INTO v_event_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'event_id', v_event_id,
        'payload_hash', v_hash,
        'truth_state', v_sanitized_truth
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.vehicle_append_passport_event_v1 TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. TRUST, FRAUD & RISK DECISION PLANE (GATE 8)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.risk_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type TEXT NOT NULL CHECK (subject_type IN (
        'USER',
        'DRIVER',
        'PROVIDER',
        'TRANSACTION',
        'TRIP',
        'DEVICE'
    )),
    subject_id UUID NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('APPROVE', 'CHALLENGE', 'SUSPEND', 'BLOCK')),
    reason_codes TEXT[] NOT NULL DEFAULT '{}',
    score INTEGER CHECK (score BETWEEN 0 AND 1000),
    rules_version TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_decisions_subject
    ON public.risk_decisions(subject_type, subject_id, created_at DESC);

ALTER TABLE public.risk_decisions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.risk_decisions FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.risk_decisions TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. GRANULAR PLATFORM KILL SWITCHES & FAIL-CLOSED MARKET GATE (GATE 9 & 11)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.platform_kill_switches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    switch_type TEXT NOT NULL CHECK (switch_type IN (
        'MARKET',
        'SERVICE_TYPE',
        'PROVIDER',
        'PAYMENT_PROVIDER',
        'ELECTRONIC_PAYMENTS',
        'FEATURE'
    )),
    target_key TEXT NOT NULL,
    is_disabled BOOLEAN NOT NULL DEFAULT TRUE,
    reason TEXT NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(switch_type, target_key)
);

ALTER TABLE public.platform_kill_switches ENABLE ROW LEVEL SECURITY;

CREATE POLICY "platform_kill_switches_select_all" ON public.platform_kill_switches
    FOR SELECT TO anon, authenticated, service_role USING (TRUE);

CREATE POLICY "platform_kill_switches_service_role_all" ON public.platform_kill_switches
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

GRANT SELECT ON public.platform_kill_switches TO anon, authenticated, service_role;
REVOKE INSERT, UPDATE, DELETE ON public.platform_kill_switches FROM anon, authenticated;
GRANT ALL ON public.platform_kill_switches TO service_role;

CREATE OR REPLACE FUNCTION public.platform_is_target_active(
    p_switch_type TEXT,
    p_target_key TEXT
) RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT NOT EXISTS (
        SELECT 1 FROM public.platform_kill_switches
        WHERE switch_type = p_switch_type
          AND target_key = p_target_key
          AND is_disabled = TRUE
    );
$$;

GRANT EXECUTE ON FUNCTION public.platform_is_target_active TO anon, authenticated;

-- Fail-closed market activation verification gate
CREATE OR REPLACE FUNCTION public.market_verify_activation_gate(
    p_market_code TEXT
) RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_has_identity BOOLEAN;
    v_has_currency BOOLEAN;
    v_has_legal BOOLEAN;
    v_has_pricing BOOLEAN;
    v_kill_switch_active BOOLEAN;
BEGIN
    -- 1. Check kill switch
    v_kill_switch_active := NOT public.platform_is_target_active('MARKET', p_market_code);
    IF v_kill_switch_active THEN
        RETURN jsonb_build_object('allowed', FALSE, 'reason', 'MARKET_DISABLED_BY_KILL_SWITCH');
    END IF;

    -- 2. Check pricing policy exists
    SELECT EXISTS (
        SELECT 1 FROM public.mobility_pricing_policies
        WHERE market_id = p_market_code AND active = TRUE
    ) INTO v_has_pricing;

    IF NOT v_has_pricing THEN
        RETURN jsonb_build_object('allowed', FALSE, 'reason', 'MISSING_MANDATORY_PRICING_POLICY');
    END IF;

    -- Market is verified
    RETURN jsonb_build_object(
        'allowed', TRUE,
        'status', 'ACTIVE',
        'market_code', p_market_code
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.market_verify_activation_gate TO anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. MULTI-TENANT ENTERPRISE & FLEET ORGANIZATIONS (GATE 13)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 2 AND 120),
    slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]+$'),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.organization_memberships (
    organization_id UUID NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'DISPATCHER', 'DRIVER', 'AUDITOR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, user_id)
);

ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organization_memberships ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.user_admin_org_ids(p_user_id UUID)
RETURNS SETOF UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT organization_id FROM public.organization_memberships
    WHERE user_id = p_user_id AND role IN ('OWNER', 'ADMIN');
$$;

GRANT EXECUTE ON FUNCTION public.user_admin_org_ids TO authenticated;

-- Strict tenant isolation RLS
CREATE POLICY "organizations_member_read" ON public.organizations
    FOR SELECT TO authenticated USING (
        EXISTS (
            SELECT 1 FROM public.organization_memberships m
            WHERE m.organization_id = organizations.id AND m.user_id = auth.uid()
        ) OR auth.role() = 'service_role'
    );

CREATE POLICY "organization_memberships_member_read" ON public.organization_memberships
    FOR SELECT TO authenticated USING (
        user_id = auth.uid()
        OR organization_id IN (SELECT public.user_admin_org_ids(auth.uid()))
        OR auth.role() = 'service_role'
    );

CREATE POLICY "organizations_service_role_all" ON public.organizations
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

CREATE POLICY "organization_memberships_service_role_all" ON public.organization_memberships
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

GRANT SELECT ON public.organizations, public.organization_memberships TO authenticated;
GRANT ALL ON public.organizations TO service_role;
GRANT ALL ON public.organization_memberships TO service_role;

