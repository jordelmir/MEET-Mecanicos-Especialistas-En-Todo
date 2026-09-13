-- P1: Money contract clarification.
-- ride_wallet_ledger.amount_minor stores WHOLE CRC units, not minor currency units.
-- This migration clarifies semantics WITHOUT renaming (which would break existing code).
-- Future PSP integrations must use a MoneyMinor wrapper that explicitly converts.

-- 1. Add clarifying comments to all money columns.
COMMENT ON COLUMN public.ride_wallet_ledger.amount_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

COMMENT ON COLUMN public.ride_wallet_topups.amount_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

COMMENT ON COLUMN public.ride_commission_reservations.amount_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

COMMENT ON COLUMN public.ride_requests.estimated_fare_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

COMMENT ON COLUMN public.ride_requests.final_fare_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

COMMENT ON COLUMN public.ride_offers.fare_minor IS
    'WHOLE CRC units (colones enteros). NOT minor currency units. 15000 = ₡15,000.';

-- 2. Create a money view that uses correct naming for future consumers.
CREATE OR REPLACE VIEW public.v_wallet_balance AS
SELECT
    l.driver_id,
    l.currency,
    sum(CASE
        WHEN l.direction = 'CREDIT' THEN l.amount_minor
        WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
        ELSE 0
    END) AS balance_crc_units,
    sum(CASE
        WHEN l.direction = 'CREDIT' THEN l.amount_minor
        ELSE 0
    END) AS total_credits_crc_units,
    sum(CASE
        WHEN l.direction = 'DEBIT' THEN l.amount_minor
        ELSE 0
    END) AS total_debits_crc_units
FROM public.ride_wallet_ledger l
GROUP BY l.driver_id, l.currency;

COMMENT ON VIEW public.v_wallet_balance IS
    'Wallet balance projection. All amounts are WHOLE CRC units (colones enteros).';

-- 3. Add a conversion function for future PSP integration.
CREATE OR REPLACE FUNCTION public.crc_to_minor(p_crc_units bigint)
RETURNS bigint
LANGUAGE sql IMMUTABLE
AS $$
    -- CRC has 0 decimal places. Minor = whole * 10^0 = whole.
    -- This function exists as a contract reminder. When adding a PSP
    -- with different decimal places, update this function.
    SELECT p_crc_units;
$$;

CREATE OR REPLACE FUNCTION public.minor_to_crc(p_minor bigint)
RETURNS bigint
LANGUAGE sql IMMUTABLE
AS $$
    -- CRC has 0 decimal places. Whole = minor / 10^0 = minor.
    SELECT p_minor;
$$;

COMMENT ON FUNCTION public.crc_to_minor(bigint) IS
    'Converts CRC whole units to minor units. For CRC, minor == whole (0 decimals).';
COMMENT ON FUNCTION public.minor_to_crc(bigint) IS
    'Converts minor units to CRC whole units. For CRC, whole == minor (0 decimals).';
