# MEET / ELYSIUM — Production Runbook: Account Deletion Failure & GDPR/Google Play Compliance

## 1. Trigger Conditions & Severity
- **Severity**: SEV-2 (High) — Regulatory compliance SLA: Deletion requests must be resolved within 30 days, automated within 24 hours.
- **Trigger**: Any record in `public.account_deletion_requests` with `status = 'FAILED'`, or `status = 'PENDING'` for > 12 hours.

## 2. Monitoring & Diagnostic Queries
1. **Find Unprocessed or Failed Deletion Requests**:
   ```sql
   SELECT request_id, user_id, status, error_message, requested_at, processed_at
   FROM public.account_deletion_requests
   WHERE status IN ('FAILED', 'PENDING')
   ORDER BY requested_at ASC;
   ```
2. **Review Failure Reasons**:
   - Check `error_message` column.
   - Common causes: Foreign key constraints on active transactions, row locks, or missing table permissions.

## 3. Resolution Workflow
1. Execute the canonical processor manually as `service_role`:
   ```sql
   SET ROLE service_role;
   SELECT public.process_account_deletion_request('<request_id>'::uuid);
   ```
2. **Verify Deletion & Pseudonymization Invariants (Gate 7)**:
   - Check `public.principals`:
     ```sql
     SELECT principal_id, phone, full_name, status 
     FROM public.principals 
     WHERE principal_id = '<user_id>'::uuid;
     -- phone must be NULL, full_name must start with 'DELETED_USER_', status must be 'DELETED'
     ```
   - Check `public.principal_capabilities`:
     ```sql
     SELECT count(*) FROM public.principal_capabilities WHERE principal_id = '<user_id>'::uuid;
     -- Must be 0
     ```
   - Check `public.driver_presence_snapshot`:
     ```sql
     SELECT count(*) FROM public.driver_presence_snapshot WHERE driver_id = '<user_id>'::uuid;
     -- Must be 0
     ```
   - Check `public.mobility_trip_shares`:
     ```sql
     SELECT state, revoked_at FROM public.mobility_trip_shares 
     WHERE grantor_id = '<user_id>'::uuid OR grantee_id = '<user_id>'::uuid;
     -- All active shares must be 'REVOKED'
     ```
3. Update ticket status in compliance tracker with the execution log timestamp.
