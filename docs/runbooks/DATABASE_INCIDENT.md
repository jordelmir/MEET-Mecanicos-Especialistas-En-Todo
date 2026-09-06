# MEET / ELYSIUM — Production Runbook: Database Incident & Connection Exhaustion

## 1. Trigger Conditions & Severity
- **Severity**: SEV-1 (Critical)
- **Trigger**: Active PostgreSQL client connections > 90% of `max_connections`, connection pooler (PgBouncer/Supavisor) queue timeout alerts, replication lag > 10s, or CPU/Memory utilization > 95%.

## 2. Immediate Diagnostic Actions
1. **Check Connection Count by Application & State**:
   ```sql
   SELECT datname, usename, client_addr, state, count(*) 
   FROM pg_stat_activity 
   GROUP BY datname, usename, client_addr, state 
   ORDER BY count(*) DESC;
   ```
2. **Identify Long-Running or Blocking Queries**:
   ```sql
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state 
   FROM pg_stat_activity 
   WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
     AND state != 'idle'
   ORDER BY duration DESC;
   ```
3. **Detect Lock Contention**:
   ```sql
   SELECT blocked_locks.pid AS blocked_pid,
          blocked_activity.usename AS blocked_user,
          blocking_locks.pid AS blocking_pid,
          blocking_activity.usename AS blocking_user,
          blocked_activity.query AS blocked_statement,
          blocking_activity.query AS current_statement_in_blocking_process
   FROM  pg_catalog.pg_locks blocked_locks
   JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
   JOIN pg_catalog.pg_locks blocking_locks 
       ON blocking_locks.locktype = blocked_locks.locktype
       AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
       AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
       AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
       AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
       AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
       AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
       AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
       AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
       AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
       AND blocking_locks.pid != blocked_locks.pid
   JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
   WHERE NOT blocked_locks.granted;
   ```

## 3. Mitigation Steps
1. **Terminate Rogue / Blocking PIDs**:
   ```sql
   SELECT pg_cancel_backend(<pid>);
   -- If cancel fails after 5 seconds:
   SELECT pg_terminate_backend(<pid>);
   ```
2. **Enforce Transaction Timeout**:
   Ensure transaction timeout guards are active on public APIs:
   ```sql
   ALTER ROLE authenticated SET statement_timeout = '8000ms';
   ```
3. **Supavisor Pool Sizing**:
   Ensure mobile app uses pooled port 6543 (transaction pooling) and NOT direct connection port 5432.
