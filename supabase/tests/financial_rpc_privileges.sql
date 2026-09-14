-- supabase/tests/financial_rpc_privileges.sql
-- CI gate: verify financial RPCs are not callable by anonymous/public roles.
-- Run via: psql "$DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/financial_rpc_privileges.sql

DO $$
DECLARE
    v_failures text[] := '{}';
    v_rec record;
BEGIN
    -- Financial functions that must NOT be callable by anon or public
    FOR v_rec IN
        SELECT p.proname AS func_name,
               pg_catalog.pg_get_userbyid(p.proowner) AS owner,
               CASE WHEN p.proacl IS NULL THEN '{}'::text[]
                    ELSE ARRAY(SELECT ac.grantee::regrole::text
                               FROM aclexplode(p.proacl) ac
                               WHERE ac.privilege_type = 'EXECUTE')
               END AS granted_to
        FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE n.nspname = 'public'
          AND p.proname IN (
              'ride_submit_wallet_topup_v1',
              'ride_wallet_ensure_starter_credit_v1',
              'ride_owner_wallet_topup_queue_v1',
              'ride_review_wallet_topup_v1',
              'ride_wallet_balance_v1',
              'ride_driver_has_offer_balance',
              'ride_offer_wallet_guard',
              'mobility_generate_quote',
              'mobility_authorize_payment',
              'mobility_authorize_quote_payment',
              'mobility_post_ledger_transaction',
              'mobility_settle_trip',
              'mobility_confirm_tip_capture',
              'mobility_settle_trip_tip'
          )
    LOOP
        -- Check anon access
        IF 'anon' = ANY(v_rec.granted_to) THEN
            v_failures := array_append(v_failures, format('SECURITY: %s grants EXECUTE to anon', v_rec.func_name));
        END IF;
        -- Check public (unrestricted) access
        IF 'public' = ANY(v_rec.granted_to) THEN
            v_failures := array_append(v_failures, format('SECURITY: %s grants EXECUTE to public', v_rec.func_name));
        END IF;
    END LOOP;

    -- Verify sinpe_incoming_receipts table has RLS enabled
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'sinpe_incoming_receipts'
          AND c.relrowsecurity = true
    ) THEN
        v_failures := array_append(v_failures, 'SECURITY: sinpe_incoming_receipts missing RLS');
    END IF;

    -- Verify ride_wallet_ledger has RLS enabled
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'ride_wallet_ledger'
          AND c.relrowsecurity = true
    ) THEN
        v_failures := array_append(v_failures, 'SECURITY: ride_wallet_ledger missing RLS');
    END IF;

    -- Report
    IF array_length(v_failures, 1) = 0 THEN
        RAISE NOTICE 'Financial RPC privileges: PASS';
    ELSE
        RAISE EXCEPTION 'Financial RPC privileges: FAIL — %', array_to_string(v_failures, '; ');
    END IF;
END $$;
