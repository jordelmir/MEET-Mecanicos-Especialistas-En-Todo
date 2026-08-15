-- Safe production performance improvements derived from Supabase advisors:
-- 1. index every foreign-key column tuple that lacks a supporting index;
-- 2. evaluate auth.uid() once per statement in RLS policies (initplan).

do $foreign_keys$
declare
    v_fk record;
    v_index_name text;
begin
    for v_fk in
        select
            n.nspname as schema_name,
            t.relname as table_name,
            c.conname as constraint_name,
            array_agg(a.attname order by k.ordinality) as column_names
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        join pg_namespace n on n.oid = t.relnamespace
        join unnest(c.conkey) with ordinality k(attnum, ordinality) on true
        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
        where c.contype = 'f'
          and n.nspname = 'public'
          and not exists (
              select 1
              from pg_index i
              where i.indrelid = c.conrelid
                and i.indisvalid
                and i.indpred is null
                and array(
                    select i.indkey[g.pos - 1]
                    from generate_series(1, cardinality(c.conkey)) g(pos)
                ) = c.conkey
          )
        group by n.nspname, t.relname, c.conname
    loop
        v_index_name := left(
            format('meet_fk_%s_%s', v_fk.table_name, array_to_string(v_fk.column_names, '_')),
            54
        ) || '_' || substr(md5(v_fk.constraint_name), 1, 8);

        execute format(
            'create index if not exists %I on %I.%I (%s)',
            v_index_name,
            v_fk.schema_name,
            v_fk.table_name,
            (
                select string_agg(format('%I', column_name), ', ')
                from unnest(v_fk.column_names) column_name
            )
        );
    end loop;
end;
$foreign_keys$;

do $rls_initplan$
declare
    v_policy record;
    v_using text;
    v_check text;
begin
    for v_policy in
        select
            p.polname,
            n.nspname as schema_name,
            c.relname as table_name,
            pg_get_expr(p.polqual, p.polrelid) as using_expression,
            pg_get_expr(p.polwithcheck, p.polrelid) as check_expression
        from pg_policy p
        join pg_class c on c.oid = p.polrelid
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public'
          and (
              coalesce(pg_get_expr(p.polqual, p.polrelid), '') like '%auth.uid()%' or
              coalesce(pg_get_expr(p.polwithcheck, p.polrelid), '') like '%auth.uid()%'
          )
    loop
        v_using := replace(v_policy.using_expression, 'auth.uid()', '(SELECT auth.uid())');
        v_check := replace(v_policy.check_expression, 'auth.uid()', '(SELECT auth.uid())');

        if v_policy.using_expression is not null and v_using is distinct from v_policy.using_expression then
            execute format(
                'alter policy %I on %I.%I using (%s)',
                v_policy.polname, v_policy.schema_name, v_policy.table_name, v_using
            );
        end if;

        if v_policy.check_expression is not null and v_check is distinct from v_policy.check_expression then
            execute format(
                'alter policy %I on %I.%I with check (%s)',
                v_policy.polname, v_policy.schema_name, v_policy.table_name, v_check
            );
        end if;
    end loop;
end;
$rls_initplan$;
