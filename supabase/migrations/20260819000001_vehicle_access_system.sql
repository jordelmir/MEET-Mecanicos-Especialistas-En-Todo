-- ═══════════════════════════════════════════════════════════════
-- MEET / ELYSIUM VANGUARD: VEHICLE ACCESS & DIGITAL KEY SYSTEM
-- ═══════════════════════════════════════════════════════════════

-- 1. Table: vehicle_access_credentials (Digital Twin of keys & credentials)
CREATE TABLE IF NOT EXISTS public.vehicle_access_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES public.cloud_vehicles(id) ON DELETE CASCADE,
    slot_number INT NOT NULL DEFAULT 1,
    label TEXT NOT NULL,
    credential_type TEXT NOT NULL, -- 'DIGITAL_KEY', 'TRANSPONDER', 'REMOTE', 'MECHANICAL', 'SMART_KEY'
    authority TEXT NOT NULL, -- 'OEM', 'GOOGLE_WALLET', 'MEET_NATIVE', 'CERTIFIED_LOCKSMITH'
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'PROVISIONING', 'SUSPENDED', 'LOST', 'REVOKED'
    permissions JSONB NOT NULL DEFAULT '["entry", "drive"]'::jsonb,
    transponder_family TEXT,
    remote_frequency TEXT,
    battery_health_percent INT,
    is_primary_owner BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ,
    last_verified_at TIMESTAMPTZ,
    proof_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Table: vehicle_access_grants (Delegated temporary access e.g. Valet / Workshop)
CREATE TABLE IF NOT EXISTS public.vehicle_access_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES public.cloud_vehicles(id) ON DELETE CASCADE,
    issuer_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    recipient_name TEXT NOT NULL,
    recipient_role TEXT NOT NULL, -- 'Familiar', 'Valet Parking', 'Taller Mecánico', 'Conductor Flota'
    permissions JSONB NOT NULL DEFAULT '["entry"]'::jsonb,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ NOT NULL,
    is_vehicle_enforced BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    revocation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Table: vehicle_access_events (Immutable cryptographic audit log)
CREATE TABLE IF NOT EXISTS public.vehicle_access_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES public.cloud_vehicles(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    actor_label TEXT NOT NULL,
    action TEXT NOT NULL,
    credential_type TEXT NOT NULL,
    outcome TEXT NOT NULL, -- 'AUTORIZADO', 'BLOQUEADO', 'REVOCADO', 'EMPAREJADO'
    evidence_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.vehicle_access_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vehicle_access_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vehicle_access_events ENABLE ROW LEVEL SECURITY;

-- 5. RLS Policies (Owner-Scoped Security)
CREATE POLICY "Users can view credentials for their vehicles"
    ON public.vehicle_access_credentials
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.cloud_vehicles
            WHERE public.cloud_vehicles.id = vehicle_access_credentials.vehicle_id
            AND public.cloud_vehicles.user_id = auth.uid()::text
        )
    );

CREATE POLICY "Users can manage credentials for their vehicles"
    ON public.vehicle_access_credentials
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM public.cloud_vehicles
            WHERE public.cloud_vehicles.id = vehicle_access_credentials.vehicle_id
            AND public.cloud_vehicles.user_id = auth.uid()::text
        )
    );

CREATE POLICY "Users can view and manage grants for their vehicles"
    ON public.vehicle_access_grants
    FOR ALL
    USING (issuer_user_id = auth.uid());

CREATE POLICY "Users can view audit events for their vehicles"
    ON public.vehicle_access_events
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.cloud_vehicles
            WHERE public.cloud_vehicles.id = vehicle_access_events.vehicle_id
            AND public.cloud_vehicles.user_id = auth.uid()::text
        )
    );
