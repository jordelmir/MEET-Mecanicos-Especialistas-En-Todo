-- =============================================================================
-- Migration: 20260829000000_elysium_event_outbox.sql
-- Description: Transactional Outbox Pattern for Elysium Distributed Platform
-- Governed by: MEET / Elysium Vanguard Master Implementation Order V1
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.elysium_event_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    source_domain TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 1,
    event_type TEXT NOT NULL,
    event_class TEXT NOT NULL DEFAULT 'DURABLE_DOMAIN',
    payload_version INT NOT NULL DEFAULT 1,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    target_principal_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    tenant_id UUID,
    correlation_id TEXT,
    causation_id TEXT,
    trace_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    published_at TIMESTAMPTZ,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_publish_error_code TEXT
);

-- Indices for rapid polling and deduplication
CREATE INDEX IF NOT EXISTS idx_elysium_outbox_unpublished 
    ON public.elysium_event_outbox (created_at ASC) 
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_elysium_outbox_aggregate 
    ON public.elysium_event_outbox (aggregate_type, aggregate_id, aggregate_version);

CREATE INDEX IF NOT EXISTS idx_elysium_outbox_correlation 
    ON public.elysium_event_outbox (correlation_id);

-- Enable RLS (Service role only access)
ALTER TABLE public.elysium_event_outbox ENABLE ROW LEVEL SECURITY;

-- Helper RPC for atomic event publication
CREATE OR REPLACE FUNCTION public.elysium_publish_outbox_event(
    p_source_domain TEXT,
    p_source_type TEXT,
    p_source_id TEXT,
    p_aggregate_type TEXT,
    p_aggregate_id TEXT,
    p_aggregate_version BIGINT,
    p_event_type TEXT,
    p_event_class TEXT,
    p_payload JSONB,
    p_target_principal_id UUID DEFAULT NULL,
    p_tenant_id UUID DEFAULT NULL,
    p_correlation_id TEXT DEFAULT NULL,
    p_causation_id TEXT DEFAULT NULL,
    p_trace_id TEXT DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_event_id UUID;
BEGIN
    INSERT INTO public.elysium_event_outbox (
        source_domain,
        source_type,
        source_id,
        aggregate_type,
        aggregate_id,
        aggregate_version,
        event_type,
        event_class,
        payload,
        target_principal_id,
        tenant_id,
        correlation_id,
        causation_id,
        trace_id
    ) VALUES (
        p_source_domain,
        p_source_type,
        p_source_id,
        p_aggregate_type,
        p_aggregate_id,
        p_aggregate_version,
        p_event_type,
        p_event_class,
        p_payload,
        p_target_principal_id,
        p_tenant_id,
        p_correlation_id,
        p_causation_id,
        p_trace_id
    ) RETURNING event_id INTO v_event_id;

    -- Send low-latency notification for workers (payload is wake-up only, not authoritative data)
    PERFORM pg_notify('elysium_outbox_events', json_build_object('eventId', v_event_id)::text);

    RETURN v_event_id;
END;
$$;

REVOKE ALL ON FUNCTION public.elysium_publish_outbox_event(
    TEXT, TEXT, TEXT, TEXT, TEXT, BIGINT, TEXT, TEXT, JSONB,
    UUID, UUID, TEXT, TEXT, TEXT
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.elysium_publish_outbox_event(
    TEXT, TEXT, TEXT, TEXT, TEXT, BIGINT, TEXT, TEXT, JSONB,
    UUID, UUID, TEXT, TEXT, TEXT
) TO service_role;
