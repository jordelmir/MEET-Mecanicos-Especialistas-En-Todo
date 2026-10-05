-- ═══════════════════════════════════════════════════════════════════
-- BLOQUE 2 — PostgreSQL implementation of SAFETY-CUSTODY-V2
--
-- Byte-exact parity with:
--   Kotlin: CustodyProtocolV2.kt
--   TypeScript: custody-protocol-v2.ts
--
-- Same canonical byte format → Same SHA-256 hex output.
-- ═══════════════════════════════════════════════════════════════════

BEGIN;

CREATE OR REPLACE FUNCTION public.safety_custody_v2_event_hash(
    p_event_id TEXT,
    p_event_type TEXT,
    p_actor_id TEXT,
    p_timestamp_utc TEXT,
    p_payload_hash TEXT,
    p_previous_hash TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    v_canonical TEXT;
BEGIN
    v_canonical :=
        'SAFETY-CUSTODY-V2' || E'\n' ||
        'event_id:' || lower(p_event_id) || E'\n' ||
        'event_type:' || p_event_type || E'\n' ||
        'actor_id:' || lower(p_actor_id) || E'\n' ||
        'timestamp:' || p_timestamp_utc || E'\n' ||
        'payload_hash:' || lower(p_payload_hash) || E'\n' ||
        'previous_hash:' || lower(p_previous_hash) || E'\n';

    RETURN encode(
        extensions.digest(convert_to(v_canonical, 'UTF8'), 'sha256'),
        'hex'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.safety_custody_v2_chain_root(
    p_event_hashes TEXT[]
)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
    v_canonical TEXT;
    v_hash TEXT;
BEGIN
    v_canonical :=
        'SAFETY-CUSTODY-V2-CHAIN' || E'\n' ||
        'count:' || array_length(p_event_hashes, 1)::text || E'\n';

    FOREACH v_hash IN ARRAY p_event_hashes LOOP
        v_canonical := v_canonical || lower(v_hash) || E'\n';
    END LOOP;

    RETURN encode(
        extensions.digest(convert_to(v_canonical, 'UTF8'), 'sha256'),
        'hex'
    );
END;
$$;

-- Ensure pgcrypto extension is available for digest()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ═══════════════════════════════════════════════════════════════════
-- Phase 20 — AI BOUNDARY in PUBLICATION
--
-- AI cannot create publication requests directly.
-- ═══════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.safety_scientific_publication_guard()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    -- Reject direct status changes to PUBLISHED without proper review
    IF NEW.status = 'PUBLISHED' AND OLD.status != 'PUBLICATION_ELIGIBLE' THEN
        RAISE EXCEPTION 'PUBLICATION_AUTHORITY_VIOLATION: cannot skip to PUBLISHED from %', OLD.status;
    END IF;

    -- Reject retraction reversal
    IF OLD.status = 'RETRACTED' AND NEW.status != 'RETRACTED' THEN
        RAISE EXCEPTION 'RETRACTION_IS_TERMINAL: cannot reverse retraction';
    END IF;

    RETURN NEW;
END;
$$;

-- Apply to publications table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'safety_scientific_publications'
    ) THEN
        EXECUTE 'CREATE TRIGGER trg_publication_guard
            BEFORE UPDATE ON public.safety_scientific_publications
            FOR EACH ROW EXECUTE FUNCTION public.safety_scientific_publication_guard()';
    END IF;
EXCEPTION WHEN duplicate_object THEN
    NULL; -- trigger already exists
END $$;

COMMIT;
