-- P0: Bank idempotency — prevent double-credit from same SINPE transfer.
-- Problem: idempotency_key is per-file (screenshot hash), not per-transaction.
-- Same bank transfer + different screenshot = two approvals possible.
-- Fix: UNIQUE(driver_id, normalized_transfer_reference) for non-null references.
-- Also: add anti-fraud velocity limit function.

-- 1. Normalize and deduplicate existing transfer references before adding constraint.
UPDATE public.ride_wallet_topups
SET transfer_reference = upper(trim(transfer_reference))
WHERE transfer_reference IS NOT NULL;

-- 2. If duplicate references exist, keep only the earliest submission per driver.
DELETE FROM public.ride_wallet_topups
WHERE id NOT IN (
    SELECT DISTINCT ON (driver_id, transfer_reference) id
    FROM public.ride_wallet_topups
    WHERE transfer_reference IS NOT NULL
    ORDER BY driver_id, transfer_reference, submitted_at ASC
);

-- 3. Add the unique constraint for non-null transfer references.
CREATE UNIQUE INDEX IF NOT EXISTS ride_wallet_topups_bank_ref_unique
ON public.ride_wallet_topups (driver_id, transfer_reference)
WHERE transfer_reference IS NOT NULL;

-- 4. Anti-fraud: velocity limit — max 3 top-ups per driver per 24 hours.
CREATE OR REPLACE FUNCTION public.ride_wallet_check_topup_velocity(
    p_driver_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_count bigint;
BEGIN
    SELECT count(*) INTO v_count
    FROM public.ride_wallet_topups
    WHERE driver_id = p_driver_id
      AND submitted_at > now() - interval '24 hours';

    -- Allow max 3 top-ups per 24h window
    RETURN v_count < 3;
END;
$$;

-- 5. Add check constraint: proof must be at least 1KB (reject blank/empty screenshots).
ALTER TABLE public.ride_wallet_topups
    ADD CONSTRAINT ride_wallet_topups_min_proof_size
    CHECK (proof_byte_count >= 1024);

-- 6. Add NOT NULL on amount_minor for new rows (existing rows already have values).
ALTER TABLE public.ride_wallet_topups
    ALTER COLUMN amount_minor SET NOT NULL;

COMMENT ON INDEX public.ride_wallet_topups_bank_ref_unique IS
    'P0 invariant: same bank transfer cannot credit wallet twice per driver.';
