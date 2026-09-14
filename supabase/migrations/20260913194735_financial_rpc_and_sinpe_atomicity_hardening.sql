-- Migration: 20260913194735_financial_rpc_and_sinpe_atomicity_hardening.sql
-- Closes privilege gaps found during Codex audit:
--   1. Wallet functions: original migrations revoke from public but NOT from anon
--   2. SINPE RPCs had GRANT but no explicit REVOKE PUBLIC/ANON
--   3. ride_driver_wallet_credit_v1 lacked explicit REVOKE

-- ============================================================
-- 1. Wallet functions: add REVOKE from anon (public already revoked by earlier migrations)
--    Uses dynamic SQL to discover exact function signature from pg_proc,
--    so REVOKE always matches even if signatures change.
-- ============================================================

DO $$
DECLARE
    v_func_name text;
    v_rec record;
    v_sig text;
    v_funcs text[] := ARRAY[
        'ride_submit_wallet_topup_v1',
        'ride_wallet_ensure_starter_credit_v1',
        'ride_owner_wallet_topup_queue_v1',
        'ride_owner_decide_wallet_topup_v1',
        'ride_wallet_balance_v1',
        'ride_driver_has_offer_balance',
        'ride_offer_wallet_guard'
    ];
BEGIN
    FOREACH v_func_name IN ARRAY v_funcs LOOP
        FOR v_rec IN
            SELECT p.oid,
                   pg_catalog.pg_get_function_identity_arguments(p.oid) AS args
            FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = 'public'
              AND p.proname = v_func_name
            ORDER BY (SELECT count(*) FROM unnest(p.proargtypes) t)
        LOOP
            EXECUTE format(
                'REVOKE ALL ON FUNCTION public.%I(%s) FROM public, anon',
                v_func_name, v_rec.args
            );
            RAISE NOTICE 'REVOKE %: OK', v_func_name;
            EXIT;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================
-- 2. SINPE functions: explicit REVOKE from public/anon
-- ============================================================

REVOKE ALL ON FUNCTION public.sinpe_ingest_email_receipt_v1(
    text, text, numeric, text, text, text, text
) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.sinpe_ingest_email_receipt_v1(
    text, text, numeric, text, text, text, text
) TO service_role;

REVOKE ALL ON FUNCTION public.sinpe_claim_receipt_v1(text, numeric)
    FROM public, anon;
GRANT EXECUTE ON FUNCTION public.sinpe_claim_receipt_v1(text, numeric)
    TO authenticated;

-- ============================================================
-- 3. Wallet credit helper (service_role only)
-- ============================================================

DO $$
DECLARE
    v_rec record;
BEGIN
    FOR v_rec IN
        SELECT pg_catalog.pg_get_function_identity_arguments(p.oid) AS args
        FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE n.nspname = 'public' AND p.proname = 'ride_driver_wallet_credit_v1'
    LOOP
        EXECUTE format(
            'REVOKE ALL ON FUNCTION public.ride_driver_wallet_credit_v1(%s) FROM public, anon',
            v_rec.args
        );
        EXECUTE format(
            'GRANT EXECUTE ON FUNCTION public.ride_driver_wallet_credit_v1(%s) TO service_role',
            v_rec.args
        );
        RAISE NOTICE 'ride_driver_wallet_credit_v1: locked to service_role';
        EXIT;
    END LOOP;
END $$;

-- ============================================================
-- 4. Atomicity: receipt idempotency + driver claim uniqueness
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS sinpe_receipts_reference_unique
    ON public.sinpe_incoming_receipts (reference_number);

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'sinpe_driver_claims'
          AND indexname LIKE '%unique%'
    ) THEN
        CREATE UNIQUE INDEX sinpe_driver_claims_ref_driver_unique
            ON public.sinpe_driver_claims (reference_number, driver_id);
    END IF;
END $$;
