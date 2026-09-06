# MEET / ELYSIUM — Production Runbook: Payment Provider Outage

## 1. Trigger Conditions & Severity
- **Severity**: SEV-1 (Critical)
- **Trigger**: Payment Service Provider (PSP) API error rate > 2% over 5 minutes, webhook delivery latency > 60s, or hard provider outage / HTTP 5xx responses.
- **Fail-Closed Principle**: Under V11 Gate 2, all electronic payment authorizations and captures MUST FAIL CLOSED. No trip may transition to `COMPLETED` under electronic payment without an authoritative captured record verified by the PSP webhook.

## 2. Immediate Triage & Mitigation
1. **Verify Outage Scope**:
   - Check PSP status dashboard (e.g. Stripe, Adyen, MercadoPago status).
   - Check Supabase Edge Function logs for `mobility-provider-webhook`:
     ```bash
     supabase functions logs mobility-provider-webhook
     ```
   - Check error distributions in `payment_authorizations`:
     ```sql
     SELECT state, count(*) 
     FROM public.payment_authorizations 
     WHERE created_at > now() - interval '30 minutes' 
     GROUP BY state;
     ```

2. **Engage Cash Fallback or Provider Failover**:
   - Verify `public.mobility_payment_provider_capabilities` table:
     ```sql
     SELECT provider, capability, status, is_enabled 
     FROM public.mobility_payment_provider_capabilities;
     ```
   - If electronic provider is completely down, disable it immediately to prevent failed rider bookings:
     ```sql
     UPDATE public.mobility_payment_provider_capabilities 
     SET is_enabled = false, status = 'MAINTENANCE', updated_at = clock_timestamp() 
     WHERE provider = 'STRIPE' AND capability = 'AUTHORIZE_CAPTURE';
     ```
   - Notify riders and drivers in app via announcement / banner: "Electronic card payments temporarily unavailable; CASH and verified in-person payment active."

3. **In-Flight Trips Handling**:
   - Trips currently in progress with electronic payment authorizations remain in progress.
   - Upon destination arrival, if capture webhook fails, the driver app will display "Settlement Pending Confirmation" and trip remains in `ARRIVED_DESTINATION` or queues capture retry.
   - Do NOT force offline manual capture into `CAPTURED` without provider proof.

## 3. Recovery & Reconciliation
1. Once PSP service is restored, re-enable the provider capability:
   ```sql
   UPDATE public.mobility_payment_provider_capabilities 
   SET is_enabled = true, status = 'PRODUCTION', updated_at = clock_timestamp() 
   WHERE provider = 'STRIPE' AND capability = 'AUTHORIZE_CAPTURE';
   ```
2. Replay unreceived or delayed webhooks from PSP developer dashboard, or execute batch reconciliation script.
3. Run ledger verification to confirm all completed trips balance to zero net change:
   ```sql
   SELECT * FROM public.verify_mobility_ledger_balances();
   ```
