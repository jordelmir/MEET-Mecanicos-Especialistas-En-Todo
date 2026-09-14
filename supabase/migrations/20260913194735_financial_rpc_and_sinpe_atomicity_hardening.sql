-- Migration: 20260913194735_financial_rpc_and_sinpe_atomicity_hardening.sql
-- Closes privilege gaps found during Codex audit:
--   1. 7 wallet/SINPE RPCs had NO explicit REVOKE — public could call SECURITY DEFINER functions
--   2. SINPE RPCs had GRANT but no explicit REVOKE PUBLIC/ANON
--   3. ride_driver_wallet_credit_v1 lacked explicit REVOKE

-- ============================================================
-- 1. Wallet functions: explicit REVOKE from public/anon
--    These are SECURITY DEFINER but without REVOKE, public role
--    inherits EXECUTE permission. Each guard checks auth.uid()
--    internally, but defense-in-depth requires explicit REVOKE.
-- ============================================================

-- ride_submit_wallet_topup_v1 — driver submits a SINPE topup
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_submit_wallet_topup_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_submit_wallet_topup_v1(bigint,text,text,text,text,text,text,text) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_submit_wallet_topup_v1(bigint,text,text,text,text,text,text,text) TO authenticated';
    END IF;
END $$;

-- ride_wallet_ensure_starter_credit_v1 — credits verified driver
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_wallet_ensure_starter_credit_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_wallet_ensure_starter_credit_v1() FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_wallet_ensure_starter_credit_v1() TO authenticated';
    END IF;
END $$;

-- ride_owner_wallet_topup_queue_v1 — admin reviews topup queue
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_owner_wallet_topup_queue_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_owner_wallet_topup_queue_v1(text,integer) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_owner_wallet_topup_queue_v1(text,integer) TO authenticated';
    END IF;
END $$;

-- ride_review_wallet_topup_v1 — admin approves/rejects topup
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_review_wallet_topup_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_review_wallet_topup_v1(uuid,text,text) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_review_wallet_topup_v1(uuid,text,text) TO authenticated';
    END IF;
END $$;

-- ride_wallet_balance_v1 — driver reads own balance
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_wallet_balance_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_wallet_balance_v1() FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_wallet_balance_v1() TO authenticated';
    END IF;
END $$;

-- ride_driver_has_offer_balance — checks if driver can afford offer
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_driver_has_offer_balance') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_driver_has_offer_balance(uuid,bigint,text) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_driver_has_offer_balance(uuid,bigint,text) TO authenticated';
    END IF;
END $$;

-- ride_offer_wallet_guard — trigger function (SECURITY DEFINER)
-- Trigger functions are invoked by the DB engine, not by client RPC.
-- Explicitly revoke public EXECUTE as defense-in-depth.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_offer_wallet_guard') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_offer_wallet_guard() FROM public, anon';
    END IF;
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
-- 3. Wallet credit helper
-- ============================================================

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace=n.oid
               WHERE n.nspname='public' AND p.proname='ride_driver_wallet_credit_v1') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.ride_driver_wallet_credit_v1(uuid,bigint,text,text) FROM public, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.ride_driver_wallet_credit_v1(uuid,bigint,text,text) TO service_role';
    END IF;
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
