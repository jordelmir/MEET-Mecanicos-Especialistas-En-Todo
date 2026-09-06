# MEET / ELYSIUM — Production Runbook: Database Restore & Disaster Recovery Drill

## 1. Objective & Frequency
- **Objective**: Validate that PostgreSQL backups can be restored to point-in-time within RTO < 30 minutes, RPO < 5 minutes, preserving all schema constraints, private schema vaults, RLS policies, and double-entry ledger integrity.
- **Frequency**: Monthly automated drill, quarterly human-in-the-loop audit.

## 2. Pre-Requisites & Verification Environment
1. Drill is executed against an isolated staging or ephemeral RDS/PostgreSQL 16 instance.
2. Production traffic is NEVER routed to the drill target.
3. Access to Supabase automated backup tarballs or WAL-G / pgBackRest archives.

## 3. Execution Protocol

### Step 1: Base Backup Extraction
```bash
pg_restore --clean --if-exists -h 127.0.0.1 -p 5432 -U postgres -d postgres_recovery backup_dump.sql
```

### Step 2: Schema & Invariant Sanity Verification
Verify that both `public` and `private` schemas are intact:
```sql
SELECT schemaname, tablename 
FROM pg_tables 
WHERE schemaname IN ('public', 'private') 
ORDER BY schemaname, tablename;
```

Verify that RLS is active and forced on sensitive tables:
```sql
SELECT relname, relrowsecurity, relforcerowsecurity 
FROM pg_class 
WHERE relname IN ('trips', 'mobility_trip_shares', 'payment_authorizations', 'mobility_ledger_lines');
```

Verify double-entry ledger integrity across all recovered transactions:
```sql
SELECT sum(amount_minor) AS unbalanced_amount 
FROM public.mobility_ledger_lines;
-- Must equal exactly 0
```

Verify private boarding challenges schema:
```sql
SELECT count(*) FROM private.mobility_trip_pin_challenges;
```

### Step 3: Run Parity Test Suite
Run the test harness against restored instance:
```bash
PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres PGDATABASE=postgres_recovery \
  bash tests/mobility/verify-mobility-v11-public-launch.sh
```

### Step 4: Documentation & Certification
Log the completed drill in `docs/audits/restore-drills.log` with timestamp, duration, verified transaction count, and engineer signature.
