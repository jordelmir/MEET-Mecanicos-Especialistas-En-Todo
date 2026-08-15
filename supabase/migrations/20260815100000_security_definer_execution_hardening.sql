-- Close the implicit and explicit anonymous execution paths for every elevated
-- public function. Authenticated RPC access remains only where a migration has
-- granted it explicitly. Trigger functions therefore cease to be callable as
-- public API endpoints while continuing to execute through their triggers.

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
    loop
        execute format(
            'revoke execute on function %s from public, anon',
            v_function
        );
    end loop;
end;
$hardening$;

-- Prevent new functions from silently inheriting executable-by-everyone.
alter default privileges for role postgres in schema public
    revoke execute on functions from public;

-- Clients need schema usage, never object creation authority.
revoke create on schema public from public, anon, authenticated;
grant usage on schema public to anon, authenticated;
