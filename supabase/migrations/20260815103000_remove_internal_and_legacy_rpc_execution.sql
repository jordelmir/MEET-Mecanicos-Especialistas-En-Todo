-- Reduce the authenticated RPC surface to intentional client APIs. Trigger
-- functions and internal ledger/command helpers never need direct EXECUTE.

do $hardening$
declare
    v_function regprocedure;
begin
    for v_function in
        select p.oid::regprocedure
          from pg_proc p
          join pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public'
           and p.prosecdef
           and p.prorettype = 'pg_catalog.trigger'::regtype
    loop
        execute format(
            'revoke execute on function %s from public, anon, authenticated',
            v_function
        );
    end loop;
end;
$hardening$;

-- Internal helpers are invoked only from trusted SECURITY DEFINER commands or
-- database triggers. Direct client execution would bypass their outer guards.
revoke execute on function public.ride_command_replay(uuid, text, text)
    from authenticated;
revoke execute on function public.ride_record_command_receipt(
    uuid, uuid, text, text, text, jsonb
) from authenticated;
revoke execute on function public.ride_mirror_wallet_ledger_entry(uuid)
    from authenticated;
revoke execute on function public.recalculate_provider_reputation_v1(uuid)
    from authenticated;
revoke execute on function public.rls_auto_enable()
    from authenticated;

-- Legacy RPCs trusted caller-supplied actor/reviewer identifiers. They remain
-- available to service_role for controlled maintenance, but not to clients.
revoke execute on function public.accept_repair_offer_v1(uuid, uuid, uuid, text)
    from authenticated;
revoke execute on function public.close_repair_v1(uuid, uuid, text)
    from authenticated;
revoke execute on function public.sync_vanguard_outbox_v1(uuid, jsonb, jsonb, text)
    from authenticated;
revoke execute on function public.transition_repair_work_order_v1(
    uuid, uuid, text, text, text, jsonb, text
) from authenticated;
revoke execute on function public.verify_provider_v1(uuid, uuid, boolean, text)
    from authenticated;

-- Future client RPCs must be granted explicitly after their authorization
-- contract is reviewed. Server-side service_role execution remains intact.
alter default privileges for role postgres in schema public
    revoke execute on functions from public, anon, authenticated;
