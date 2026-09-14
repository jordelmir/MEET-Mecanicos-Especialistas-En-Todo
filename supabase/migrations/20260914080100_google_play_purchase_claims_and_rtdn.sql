-- Migration: 20260914080100_google_play_purchase_claims_and_rtdn.sql
-- Production Hardening P0: Immutable purchase token claims and idempotent RTDN inbox.

-- 1. Immutable Google Play purchase claims table
CREATE TABLE IF NOT EXISTS public.google_play_purchase_claims (
    package_name text NOT NULL,
    purchase_token_hash text NOT NULL,
    owner_user_id uuid NOT NULL
        REFERENCES auth.users(id)
        ON DELETE RESTRICT,
    product_id text NOT NULL,
    product_type text NOT NULL
        CHECK (product_type IN ('inapp', 'subs')),
    first_claimed_at timestamptz NOT NULL DEFAULT now(),
    last_verified_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (package_name, purchase_token_hash)
);

ALTER TABLE public.google_play_purchase_claims ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.google_play_purchase_claims FROM anon, authenticated;

-- Owner read-only policy
CREATE POLICY "google_play_purchase_claims_owner_read"
    ON public.google_play_purchase_claims
    FOR SELECT
    TO authenticated
    USING (owner_user_id = auth.uid());

-- 2. Atomic claim RPC
CREATE OR REPLACE FUNCTION public.claim_google_play_purchase(
    p_package_name text,
    p_owner_user_id uuid,
    p_product_id text,
    p_product_type text,
    p_purchase_token_hash text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_owner uuid;
BEGIN
    IF p_owner_user_id IS NULL THEN
        RAISE EXCEPTION 'IDENTITY_REQUIRED' USING errcode = '28000';
    END IF;

    IF p_package_name IS NULL OR p_purchase_token_hash IS NULL OR p_product_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_CLAIM_PARAMETERS' USING errcode = '22023';
    END IF;

    INSERT INTO public.google_play_purchase_claims (
        package_name,
        purchase_token_hash,
        owner_user_id,
        product_id,
        product_type
    )
    VALUES (
        p_package_name,
        p_purchase_token_hash,
        p_owner_user_id,
        p_product_id,
        p_product_type
    )
    ON CONFLICT (package_name, purchase_token_hash)
    DO NOTHING;

    SELECT owner_user_id
      INTO v_owner
      FROM public.google_play_purchase_claims
     WHERE package_name = p_package_name
       AND purchase_token_hash = p_purchase_token_hash
     FOR UPDATE;

    IF v_owner IS DISTINCT FROM p_owner_user_id THEN
        RAISE EXCEPTION 'PURCHASE_ALREADY_CLAIMED'
            USING errcode = '23505';
    END IF;

    UPDATE public.google_play_purchase_claims
       SET last_verified_at = now()
     WHERE package_name = p_package_name
       AND purchase_token_hash = p_purchase_token_hash;

    RETURN v_owner;
END;
$$;

REVOKE ALL ON FUNCTION public.claim_google_play_purchase(
    text, uuid, text, text, text
) FROM public, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.claim_google_play_purchase(
    text, uuid, text, text, text
) TO service_role;

-- 3. Idempotent RTDN Inbox
CREATE TABLE IF NOT EXISTS public.google_play_rtdn_inbox (
    message_id text PRIMARY KEY,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    processing_attempts integer NOT NULL DEFAULT 0,
    payload jsonb NOT NULL,
    last_error_code text
);

ALTER TABLE public.google_play_rtdn_inbox ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.google_play_rtdn_inbox FROM anon, authenticated;
