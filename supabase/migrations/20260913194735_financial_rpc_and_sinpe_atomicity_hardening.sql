-- Migration: 20260913194735_financial_rpc_and_sinpe_atomicity_hardening.sql
-- Closes privilege gaps found during Codex audit:
--   1. SINPE RPCs had GRANT but no explicit REVOKE PUBLIC/ANON
--   2. ride_driver_wallet_credit_v1 lacked explicit REVOKE

-- 1. Explicit privilege lockdown — prevent implicit public EXECUTE
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

-- 2. Lock down wallet credit helper
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE n.nspname = 'public' AND p.proname = 'ride_driver_wallet_credit_v1'
    ) THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_driver_wallet_credit_v1(uuid, bigint, text, text) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_driver_wallet_credit_v1(uuid, bigint, text, text) TO service_role';
    END IF;
END $$;

-- 3. Verify receipt idempotency index (atomicity guard)
CREATE UNIQUE INDEX IF NOT EXISTS sinpe_receipts_reference_unique
    ON public.sinpe_incoming_receipts (reference_number);

-- 4. Verify driver claim uniqueness (one claim per reference per driver)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'sinpe_driver_claims'
          AND indexname LIKE '%unique%'
    ) THEN
        CREATE UNIQUE INDEX sinpe_driver_claims_ref_driver_unique
            ON public.sinpe_driver_claims (reference_number, driver_id);
    END IF;
END $$;
