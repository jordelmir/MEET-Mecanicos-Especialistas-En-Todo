-- Repair hardening functions deliberately use a closed search_path. Qualify
-- pgcrypto so command hashing remains executable under that fail-closed scope.
do $repair_digest_schema_fix$
declare
    v_signature regprocedure;
    v_definition text;
begin
    foreach v_signature in array array[
        'public.transition_repair_work_order_client_v1(uuid,text,text,integer,jsonb,text)'::regprocedure,
        'public.admin_override_repair_state_v1(uuid,text,text,text,integer)'::regprocedure
    ]
    loop
        select pg_get_functiondef(v_signature) into v_definition;
        if position('extensions.digest(' in v_definition) = 0 then
            v_definition := replace(v_definition, 'digest(', 'extensions.digest(');
            execute v_definition;
        end if;
    end loop;
end;
$repair_digest_schema_fix$;
