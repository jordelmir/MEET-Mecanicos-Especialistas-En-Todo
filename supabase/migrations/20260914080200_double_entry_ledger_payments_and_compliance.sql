-- Migration: 20260914080200_double_entry_ledger_payments_and_compliance.sql
-- Production Hardening: Double-Entry Ledger, Idempotent Payments, Webhook Inbox, Kill Switches & Account Deletion Tombstones.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Double-Entry Ledger Accounts
CREATE TABLE IF NOT EXISTS public.ledger_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    owner_user_id uuid REFERENCES auth.users(id) ON DELETE RESTRICT,
    currency char(3) NOT NULL,
    kind text NOT NULL CHECK (
        kind IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE')
    ),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 2. Ledger Transactions
CREATE TABLE IF NOT EXISTS public.ledger_transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key text NOT NULL UNIQUE,
    request_hash bytea NOT NULL,
    kind text NOT NULL,
    external_ref text,
    currency char(3) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 3. Ledger Entries
CREATE TABLE IF NOT EXISTS public.ledger_entries (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id uuid NOT NULL
        REFERENCES public.ledger_transactions(id)
        ON DELETE RESTRICT,
    account_id uuid NOT NULL
        REFERENCES public.ledger_accounts(id)
        ON DELETE RESTRICT,
    debit_minor bigint NOT NULL DEFAULT 0
        CHECK (debit_minor >= 0),
    credit_minor bigint NOT NULL DEFAULT 0
        CHECK (credit_minor >= 0),
    CHECK (
        (debit_minor = 0) <> (credit_minor = 0)
    )
);

-- Enable RLS and revoke direct client writes on all ledger tables
ALTER TABLE public.ledger_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_entries ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.ledger_accounts,
               public.ledger_transactions,
               public.ledger_entries
FROM anon, authenticated;

-- Owner can read their own accounts
CREATE POLICY "ledger_accounts_owner_read"
    ON public.ledger_accounts
    FOR SELECT
    TO authenticated
    USING (owner_user_id = auth.uid());

-- 4. Idempotent Ledger Posting Function (Section 11)
CREATE OR REPLACE FUNCTION public.post_ledger_transaction(
    p_idempotency_key text,
    p_kind text,
    p_currency char(3),
    p_external_ref text,
    p_entries jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_tx uuid;
    v_existing_hash bytea;
    v_hash bytea := digest(
        p_kind || '|' ||
        p_currency || '|' ||
        COALESCE(p_external_ref, '') || '|' ||
        p_entries::text,
        'sha256'
    );
    v_debits bigint;
    v_credits bigint;
BEGIN
    IF jsonb_typeof(p_entries) <> 'array'
       OR jsonb_array_length(p_entries) < 2 THEN
        RAISE EXCEPTION 'INVALID_LEDGER_ENTRY_SET' USING errcode = '22023';
    END IF;

    SELECT id, request_hash
      INTO v_tx, v_existing_hash
      FROM public.ledger_transactions
     WHERE idempotency_key = p_idempotency_key
     FOR UPDATE;

    IF FOUND THEN
        IF v_existing_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD'
                USING errcode = '23505';
        END IF;

        RETURN v_tx;
    END IF;

    SELECT
        sum(COALESCE((entry ->> 'debit_minor')::bigint, 0)),
        sum(COALESCE((entry ->> 'credit_minor')::bigint, 0))
    INTO
        v_debits,
        v_credits
    FROM jsonb_array_elements(p_entries) entry;

    IF v_debits IS DISTINCT FROM v_credits
       OR v_debits <= 0 THEN
        RAISE EXCEPTION 'UNBALANCED_LEDGER_TRANSACTION debits=% credits=%', v_debits, v_credits
            USING errcode = '22023';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jsonb_array_elements(p_entries) entry
        LEFT JOIN public.ledger_accounts account
          ON account.id = (entry ->> 'account_id')::uuid
        WHERE account.id IS NULL
           OR account.currency <> p_currency
    ) THEN
        RAISE EXCEPTION 'INVALID_ACCOUNT_OR_CURRENCY' USING errcode = '22023';
    END IF;

    INSERT INTO public.ledger_transactions (
        idempotency_key,
        request_hash,
        kind,
        external_ref,
        currency
    )
    VALUES (
        p_idempotency_key,
        v_hash,
        p_kind,
        p_external_ref,
        p_currency
    )
    RETURNING id INTO v_tx;

    INSERT INTO public.ledger_entries (
        transaction_id,
        account_id,
        debit_minor,
        credit_minor
    )
    SELECT
        v_tx,
        (entry ->> 'account_id')::uuid,
        COALESCE((entry ->> 'debit_minor')::bigint, 0),
        COALESCE((entry ->> 'credit_minor')::bigint, 0)
    FROM jsonb_array_elements(p_entries) entry;

    RETURN v_tx;
END;
$$;

REVOKE ALL ON FUNCTION public.post_ledger_transaction(
    text, text, char, text, jsonb
) FROM public, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.post_ledger_transaction(
    text, text, char, text, jsonb
) TO service_role;

-- 5. Deferred Constraint Trigger for Ledger Invariant: sum(debit) == sum(credit) (Section 12)
CREATE OR REPLACE FUNCTION public.assert_ledger_transaction_balanced()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    v_transaction_id uuid;
    v_debit bigint;
    v_credit bigint;
    v_count bigint;
BEGIN
    v_transaction_id := COALESCE(new.transaction_id, old.transaction_id);

    SELECT
        COALESCE(sum(debit_minor), 0),
        COALESCE(sum(credit_minor), 0),
        count(*)
    INTO
        v_debit,
        v_credit,
        v_count
    FROM public.ledger_entries
    WHERE transaction_id = v_transaction_id;

    IF v_count < 2 OR v_debit <> v_credit THEN
        RAISE EXCEPTION
            'LEDGER_INVARIANT_VIOLATION tx=% debit=% credit=%',
            v_transaction_id,
            v_debit,
            v_credit
            USING errcode = 'P0001';
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS ledger_balance_constraint ON public.ledger_entries;
CREATE CONSTRAINT TRIGGER ledger_balance_constraint
AFTER INSERT OR UPDATE OR DELETE
ON public.ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.assert_ledger_transaction_balanced();

-- 6. Payment Operations (Section 14)
CREATE TABLE IF NOT EXISTS public.payment_operations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id uuid NOT NULL,
    operation text NOT NULL CHECK (
        operation IN ('AUTHORIZE', 'CAPTURE', 'VOID', 'REFUND', 'PAYOUT')
    ),
    idempotency_key text NOT NULL UNIQUE,
    provider text NOT NULL,
    provider_reference text,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency char(3) NOT NULL,
    status text NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.payment_operations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.payment_operations FROM anon, authenticated;

-- 7. Payment Webhook Inbox (Section 15)
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

-- 8. Runtime Feature Gates / Kill Switches (Section 18)
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

-- 9. Account Deletion Requests & Tombstones (Section 19 & 20)
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

-- Account deletion initiation function
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
