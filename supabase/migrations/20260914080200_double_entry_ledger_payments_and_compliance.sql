-- Migration: 20260914080200_double_entry_ledger_payments_and_compliance.sql
-- Production Hardening: RLS hardening for existing ledger, Payment Webhook Inbox,
-- Kill Switches & Account Deletion Tombstones.
--
-- NOTE: ledger_accounts, ledger_transactions, ledger_entries already exist from
-- 20260906010000_mobility_financial_authority.sql with columns:
--   ledger_accounts:       account_id, owner_id, account_type, currency_code
--   ledger_transactions:   transaction_id, reference_type, reference_id, currency_code
--   ledger_entries:        entry_id, transaction_id, account_id, amount_minor

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. RLS HARDENING FOR EXISTING LEDGER TABLES
-- ─────────────────────────────────────────────────────────────────────────────
-- The existing migration (20260906010000) already REVOKEs INSERT/UPDATE/DELETE
-- from authenticated+anon but does not ENABLE ROW LEVEL SECURITY. Add RLS now.

ALTER TABLE public.ledger_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_entries ENABLE ROW LEVEL SECURITY;

-- Owner can read their own accounts
CREATE POLICY "ledger_accounts_owner_read"
    ON public.ledger_accounts
    FOR SELECT
    TO authenticated
    USING (owner_id = auth.uid());

-- Revoke SELECT from anon (keep service_role full access via SECURITY DEFINER)
REVOKE ALL ON public.ledger_accounts FROM anon;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. PAYMENT WEBHOOK INBOX (Section 15 — Idempotent Webhook Processing)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.payment_webhook_inbox (
    provider text NOT NULL,
    provider_event_id text NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    payload jsonb NOT NULL,
    PRIMARY KEY (provider, provider_event_id)
);

ALTER TABLE public.payment_webhook_inbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.payment_webhook_inbox FROM anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RUNTIME FEATURE GATES / KILL SWITCHES (Section 18)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.runtime_feature_gates (
    key text PRIMARY KEY,
    enabled boolean NOT NULL,
    reason text,
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.runtime_feature_gates ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.runtime_feature_gates FROM anon;
GRANT SELECT ON public.runtime_feature_gates TO authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.runtime_feature_gates FROM anon, authenticated;

-- Seed default feature gates
INSERT INTO public.runtime_feature_gates (key, enabled, reason) VALUES
    ('ride_creation', true, 'Normal production operation'),
    ('ride_matching', true, 'Normal production operation'),
    ('ride_offers', true, 'Normal production operation'),
    ('ride_start', true, 'Normal production operation'),
    ('ride_payments', true, 'Normal production operation'),
    ('driver_payouts', true, 'Normal production operation'),
    ('digital_billing', true, 'Normal production operation'),
    ('account_creation', true, 'Normal production operation')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. ACCOUNT DELETION REQUESTS & TOMBSTONES (Section 19 & 20)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'deletion_status') THEN
        CREATE TYPE public.deletion_status AS ENUM (
            'REQUESTED',
            'BLOCKED',
            'EXECUTING',
            'COMPLETED',
            'FAILED'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.account_deletion_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    status public.deletion_status NOT NULL DEFAULT 'REQUESTED',
    requested_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    failure_code text
);

ALTER TABLE public.account_deletion_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.account_deletion_requests FROM anon;
CREATE POLICY "account_deletion_requests_owner_read"
    ON public.account_deletion_requests
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS public.account_deletion_tombstones (
    request_id uuid PRIMARY KEY,
    subject_digest bytea NOT NULL,
    policy_version text NOT NULL,
    completed_at timestamptz NOT NULL
);

ALTER TABLE public.account_deletion_tombstones ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.account_deletion_tombstones FROM anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. ACCOUNT DELETION INITIATION FUNCTION
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.request_account_deletion_v2()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_open_rides integer;
    v_req_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING errcode = '28000';
    END IF;

    -- Invariant: check for active unresolved rides
    SELECT count(*)
      INTO v_open_rides
      FROM public.ride_requests
     WHERE (passenger_id = v_user_id OR assigned_driver_id = v_user_id)
       AND state NOT IN ('COMPLETED', 'CANCELLED', 'EXPIRED');

    IF v_open_rides > 0 THEN
        INSERT INTO public.account_deletion_requests (user_id, status, failure_code)
        VALUES (v_user_id, 'BLOCKED', 'ACTIVE_RIDES_PENDING')
        ON CONFLICT (user_id) DO UPDATE
           SET status = 'BLOCKED', failure_code = 'ACTIVE_RIDES_PENDING'
        RETURNING id INTO v_req_id;

        RETURN jsonb_build_object(
            'status', 'BLOCKED',
            'reason', 'ACTIVE_RIDES_PENDING',
            'requestId', v_req_id
        );
    END IF;

    INSERT INTO public.account_deletion_requests (user_id, status)
    VALUES (v_user_id, 'REQUESTED')
    ON CONFLICT (user_id) DO UPDATE
       SET status = 'REQUESTED', failure_code = NULL
    RETURNING id INTO v_req_id;

    RETURN jsonb_build_object(
        'status', 'REQUESTED',
        'requestId', v_req_id
    );
END;
$$;

REVOKE ALL ON FUNCTION public.request_account_deletion_v2() FROM public, anon;
GRANT EXECUTE ON FUNCTION public.request_account_deletion_v2() TO authenticated;
