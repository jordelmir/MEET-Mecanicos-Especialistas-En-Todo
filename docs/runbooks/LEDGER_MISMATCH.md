# MEET / ELYSIUM — Production Runbook: Double-Entry Ledger Invariant Mismatch

## 1. Trigger Conditions & Severity
- **Severity**: SEV-1 (Critical)
- **Trigger**: Any transaction where sum(debits) != sum(credits) in `mobility_ledger_lines`, or any balance integrity check failure.
- **Fundamental Law**: Every monetary transaction in MEET / ELYSIUM must be balanced zero-sum (`sum(amount_minor) = 0`).

## 2. Immediate Automated Detection
Run the ledger balance audit query:
```sql
SELECT transaction_id, 
       sum(amount_minor) AS total_balance,
       count(*) AS line_count
FROM public.mobility_ledger_lines
GROUP BY transaction_id
HAVING sum(amount_minor) <> 0;
```
If this query returns ANY rows:
1. **Immediate PagerDuty SEV-1 Alert** fired to Financial Operations and Lead Architect.
2. The affected transaction ID and trip ID must be quarantined immediately:
   ```sql
   SELECT t.trip_id, t.state, tx.transaction_id, tx.created_at, tx.description
   FROM public.mobility_ledger_transactions tx
   JOIN public.trips t ON t.trip_id = tx.trip_id
   WHERE tx.transaction_id IN (<unbalanced_transaction_ids>);
   ```

## 3. Root Cause Investigation & Remediation
1. Inspect all entries for the offending transaction:
   ```sql
   SELECT line_id, account_id, entry_type, amount_minor, currency, description
   FROM public.mobility_ledger_lines
   WHERE transaction_id = '<transaction_id>'
   ORDER BY line_id;
   ```
2. **Never Edit Existing Ledger Lines**: The ledger is append-only and strictly immutable.
3. Issue an authoritative balancing journal adjustment transaction with type `REVERSAL` or `RECONCILIATION_ADJUSTMENT`:
   - Must be executed by `service_role` through a reviewed reconciliation migration script.
   - Record incident report with post-mortem in `docs/audits/`.
