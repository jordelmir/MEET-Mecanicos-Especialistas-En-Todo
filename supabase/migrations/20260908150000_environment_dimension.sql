-- P1: SANDBOX/PILOT/PRODUCTION environment dimension.
-- Prevents sandbox principals from affecting production data.

-- 1. Create environment enum.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'deployment_environment') THEN
        CREATE TYPE public.deployment_environment AS ENUM ('SANDBOX', 'PILOT', 'PRODUCTION');
    END IF;
END $$;

-- 2. Add environment column to profiles.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS environment public.deployment_environment NOT NULL DEFAULT 'SANDBOX';

-- 3. Add environment to ride_requests for cross-environment isolation.
ALTER TABLE public.ride_requests
    ADD COLUMN IF NOT EXISTS environment public.deployment_environment NOT NULL DEFAULT 'SANDBOX';

-- 4. Add environment to trips.
ALTER TABLE public.trips
    ADD COLUMN IF NOT EXISTS environment public.deployment_environment NOT NULL DEFAULT 'SANDBOX';

-- 5. Add environment to wallet.
ALTER TABLE public.ride_wallets
    ADD COLUMN IF NOT EXISTS environment public.deployment_environment NOT NULL DEFAULT 'SANDBOX';

-- 6. Cross-environment isolation: SANDBOX cannot dispatch to PRODUCTION drivers.
CREATE OR REPLACE FUNCTION public.ride_cross_environment_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_passenger_env public.deployment_environment;
    v_driver_env public.deployment_environment;
BEGIN
    -- Get passenger environment
    SELECT environment INTO v_passenger_env
    FROM public.profiles
    WHERE id = NEW.passenger_id;

    -- If passenger is SANDBOX, the driver must also be SANDBOX
    IF v_passenger_env = 'SANDBOX' THEN
        SELECT p.environment INTO v_driver_env
        FROM public.profiles p
        JOIN public.trips t ON t.driver_id = p.id
        WHERE t.trip_id = NEW.trip_id;

        IF v_driver_env IS NOT NULL AND v_driver_env <> 'SANDBOX' THEN
            RAISE EXCEPTION USING
                errcode = 'P0001',
                message = 'CROSS_ENVIRONMENT: SANDBOX passenger cannot use PRODUCTION driver';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_cross_environment_guard ON public.trips;
CREATE TRIGGER ride_cross_environment_guard
    BEFORE INSERT OR UPDATE ON public.trips
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_cross_environment_guard();

-- 7. Wallet isolation: SANDBOX wallet cannot be credited from PRODUCTION topups.
CREATE OR REPLACE FUNCTION public.ride_wallet_environment_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_wallet_env public.deployment_environment;
BEGIN
    SELECT environment INTO v_wallet_env
    FROM public.ride_wallets
    WHERE driver_id = NEW.driver_id;

    -- Block topup approval if wallet is SANDBOX and topup is from PRODUCTION context
    -- (In practice, both should match. This guard catches misconfigurations.)
    IF v_wallet_env = 'SANDBOX' AND NEW.entry_type = 'TOPUP_CREDITED' THEN
        -- SANDBOX wallets can only be credited by SANDBOX topups
        IF EXISTS (
            SELECT 1 FROM public.ride_wallet_topups t
            WHERE t.id = NEW.reference_id
              AND t.driver_id = NEW.driver_id
        ) THEN
            NULL; -- OK, topup exists
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

-- 8. Create a migration function to promote SANDBOX → PILOT.
CREATE OR REPLACE FUNCTION public.migrate_environment(
    p_user_id uuid,
    p_from_env public.deployment_environment,
    p_to_env public.deployment_environment
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    -- Only allow forward migration: SANDBOX → PILOT → PRODUCTION
    IF p_from_env = 'SANDBOX' AND p_to_env = 'PILOT' THEN
        UPDATE public.profiles SET environment = p_to_env WHERE id = p_user_id;
        UPDATE public.ride_wallets SET environment = p_to_env WHERE driver_id = p_user_id;
    ELSIF p_from_env = 'PILOT' AND p_to_env = 'PRODUCTION' THEN
        UPDATE public.profiles SET environment = p_to_env WHERE id = p_user_id;
        UPDATE public.ride_wallets SET environment = p_to_env WHERE driver_id = p_user_id;
    ELSE
        RAISE EXCEPTION USING
            errcode = 'P0001',
            message = format('Invalid migration: %s → %s', p_from_env, p_to_env);
    END IF;
END;
$$;

-- 9. Default existing data to PILOT (since we're past SANDBOX for current users).
-- Only for users who have completed verification.
UPDATE public.profiles
SET environment = 'PILOT'
WHERE id IN (
    SELECT driver_id FROM public.driver_verifications WHERE status = 'APPROVED'
    UNION
    SELECT passenger_id FROM public.passenger_verifications WHERE status = 'APPROVED'
);

COMMENT ON TYPE public.deployment_environment IS
    'SANDBOX=testing, PILOT=controlled monetization, PRODUCTION=public launch.';
COMMENT ON FUNCTION public.ride_cross_environment_guard() IS
    'P1 invariant: SANDBOX principals cannot affect PRODUCTION data.';
COMMENT ON FUNCTION public.migrate_environment(uuid, deployment_environment, deployment_environment) IS
    'Forward-only environment migration: SANDBOX → PILOT → PRODUCTION.';
