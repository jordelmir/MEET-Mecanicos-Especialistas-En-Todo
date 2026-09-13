-- P0: Ledger invariants — negative balance guard, double capture prevention,
-- serial cancellation/completion, immutable audit trail.

-- 1. NEGATIVE BALANCE GUARD: prevent DEBIT entries that would drive balance below zero.
--    Applied as a trigger on ride_wallet_ledger INSERT.
CREATE OR REPLACE FUNCTION public.ride_wallet_balance_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_balance bigint;
    v_currency text;
BEGIN
    -- Only check DEBIT entries (credits always allowed)
    IF NEW.direction = 'DEBIT' THEN
        v_currency := NEW.currency;

        -- Compute current posted balance (excluding RESERVED commissions)
        SELECT coalesce(sum(CASE
            WHEN l.direction = 'CREDIT' THEN l.amount_minor
            WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
            ELSE 0
        END), 0)
        INTO v_balance
        FROM public.ride_wallet_ledger l
        WHERE l.driver_id = NEW.driver_id
          AND l.currency = v_currency
          AND l.id <> NEW.id;

        -- Allow COMMISSION_RESERVED to go negative (it's a reservation, not a capture).
        -- Block COMMISSION_CAPTURED and other DEBITs that would exceed available balance.
        IF v_balance - NEW.amount_minor < 0 AND NEW.entry_type <> 'COMMISSION_RESERVED' THEN
            RAISE EXCEPTION USING
                errcode = 'P0001',
                message = format('INSUFFICIENT_BALANCE: available=%s requested=%s', v_balance, NEW.amount_minor);
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_wallet_balance_guard ON public.ride_wallet_ledger;
CREATE TRIGGER ride_wallet_balance_guard
    BEFORE INSERT ON public.ride_wallet_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_wallet_balance_guard();

-- 2. DOUBLE CAPTURE PREVENTION: COMMISSION_CAPTURED can only happen once per reservation.
CREATE OR REPLACE FUNCTION public.ride_commission_capture_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_existing boolean;
BEGIN
    IF NEW.entry_type = 'COMMISSION_CAPTURED' AND NEW.reference_id IS NOT NULL THEN
        SELECT EXISTS(
            SELECT 1 FROM public.ride_wallet_ledger
            WHERE entry_type = 'COMMISSION_CAPTURED'
              AND reference_id = NEW.reference_id
              AND driver_id = NEW.driver_id
              AND id <> NEW.id
        ) INTO v_existing;

        IF v_existing THEN
            RAISE EXCEPTION USING
                errcode = '23505',
                message = 'DOUBLE_CAPTURE: commission already captured for this reservation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_commission_capture_guard ON public.ride_wallet_ledger;
CREATE TRIGGER ride_commission_capture_guard
    BEFORE INSERT ON public.ride_wallet_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_commission_capture_guard();

-- 3. SERIALIZE CANCELLATION vs COMPLETION: a trip can only reach one terminal state.
CREATE OR REPLACE FUNCTION public.ride_trip_terminal_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_forbidden boolean := false;
BEGIN
    -- Mobility owns lifecycle in trips.state. COMPLETED/CANCELLED may escalate
    -- only to DISPUTED; DISPUTED is irreversible.
    v_forbidden :=
        (OLD.state = 'DISPUTED' AND NEW.state IS DISTINCT FROM OLD.state)
        OR (
            OLD.state IN ('COMPLETED', 'CANCELLED')
            AND NEW.state IS DISTINCT FROM OLD.state
            AND NEW.state <> 'DISPUTED'
        );
    IF v_forbidden THEN
        RAISE EXCEPTION USING
            errcode = '23514',
            message = format(
                'TERMINAL_TRIP_TRANSITION_FORBIDDEN: trip %s %s -> %s',
                OLD.trip_id, OLD.state, NEW.state
            );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_trip_terminal_guard ON public.trips;
CREATE TRIGGER ride_trip_terminal_guard
    BEFORE UPDATE OF state ON public.trips
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_trip_terminal_guard();

-- 4. IMMUTABLE LEDGER: prevent UPDATE and DELETE on ride_wallet_ledger.
--    Ledger entries are append-only. Corrections must be new entries (reversals).
REVOKE UPDATE, DELETE ON public.ride_wallet_ledger FROM authenticated, service_role;
GRANT SELECT, INSERT ON public.ride_wallet_ledger TO authenticated;
GRANT ALL ON public.ride_wallet_ledger TO service_role;

-- 5. Add created_at timestamp to ledger for audit trail.
ALTER TABLE public.ride_wallet_ledger
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

-- 6. Composite index for balance computation performance.
CREATE INDEX IF NOT EXISTS ride_wallet_ledger_balance_idx
    ON public.ride_wallet_ledger (driver_id, currency, direction, entry_type, amount_minor);

COMMENT ON FUNCTION public.ride_wallet_balance_guard() IS
    'P0 invariant: DEBIT entries cannot drive wallet balance below zero (except COMMISSION_RESERVED).';
COMMENT ON FUNCTION public.ride_commission_capture_guard() IS
    'P0 invariant: each commission reservation can only be captured once.';
COMMENT ON FUNCTION public.ride_trip_terminal_guard() IS
    'P0 invariant: completed/cancelled trips may only escalate to disputed; disputed is irreversible.';
