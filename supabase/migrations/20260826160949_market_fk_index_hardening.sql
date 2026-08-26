-- Every foreign-key lookup used by Market OS authorization and projections
-- needs a leading index. Generate deterministic, collision-resistant names so
-- the migration remains idempotent across PostgreSQL/Supabase environments.
do $migration$
declare
  v_fk record;
  v_index_name text;
begin
  for v_fk in
    select
      n.nspname as schema_name,
      t.relname as table_name,
      c.conname as constraint_name,
      c.conrelid,
      c.conkey,
      string_agg(format('%I',a.attname),', ' order by key_column.ordinality) as columns_sql
    from pg_constraint c
    join pg_class t on t.oid=c.conrelid
    join pg_namespace n on n.oid=t.relnamespace
    cross join lateral unnest(c.conkey) with ordinality as key_column(attnum,ordinality)
    join pg_attribute a on a.attrelid=c.conrelid and a.attnum=key_column.attnum
    where c.contype='f' and n.nspname='public'
      and t.relname ~ '^(market_|legal_|property_|fuel_)'
      and not exists (
        select 1 from pg_index i
        where i.indrelid=c.conrelid and i.indisvalid and i.indisready
          and (i.indkey::smallint[])[0:cardinality(c.conkey)-1]=c.conkey
      )
    group by n.nspname,t.relname,c.conname,c.conrelid,c.conkey
  loop
    v_index_name:=substr('idx_'||v_fk.table_name||'_fk_'||v_fk.constraint_name,1,53)
      ||'_'||substr(md5(v_fk.schema_name||'.'||v_fk.table_name||'.'||v_fk.constraint_name),1,8);
    execute format('create index if not exists %I on %I.%I (%s)',
      v_index_name,v_fk.schema_name,v_fk.table_name,v_fk.columns_sql);
  end loop;
end $migration$;
