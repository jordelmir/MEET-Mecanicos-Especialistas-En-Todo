-- Direct credit is an internal reconciliation helper, never an end-user RPC.
-- Earlier hardening revoked PUBLIC/anon but left the explicit authenticated grant.
-- Security-definer review/reconciliation functions retain their owner privileges.
revoke all on function public.ride_driver_wallet_credit_v1(uuid,bigint,text,text)
    from public, anon, authenticated;
grant execute on function public.ride_driver_wallet_credit_v1(uuid,bigint,text,text)
    to service_role;
