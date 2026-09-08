-- ride_wallet_ledger.amount_minor stores whole CRC units in the mobility schema.
-- Keep the onboarding grant aligned with the stated ₡15,000 policy.
update public.ride_wallet_policy
set starter_credit_minor = 15000,
    updated_at = now()
where policy_id = true;
