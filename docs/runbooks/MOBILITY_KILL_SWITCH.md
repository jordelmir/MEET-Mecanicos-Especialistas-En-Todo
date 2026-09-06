# MEET / ELYSIUM — Production Runbook: Mobility Kill Switch & Emergency Freeze

## 1. Scope & Activation Authority
The Mobility Kill Switch provides coarse and granular emergency controls to freeze trip creation, dispatch, or payments in the event of severe security incidents, severe regulatory orders, catastrophic extreme weather, or coordinated fraud attacks.

Activation requires authorization from:
- Head of Security / Lead Architect / Operations Director.

## 2. Granular Kill Switch Levels

### Level 1: Electronic Payment Provider Freeze
Disables electronic payments while allowing cash trips and in-flight completion:
```sql
UPDATE public.mobility_payment_provider_capabilities
SET is_enabled = false, status = 'SUSPENDED', updated_at = clock_timestamp()
WHERE capability = 'AUTHORIZE_CAPTURE';
```

### Level 2: New Ride Request Freeze
Prevents riders from submitting new ride requests while allowing in-progress trips to conclude normally:
```sql
-- Revoke RPC execution for authenticated users
REVOKE EXECUTE ON FUNCTION public.mobility_create_ride_request(JSONB) FROM authenticated;
```

### Level 3: Total Mobility Dispatch Freeze
Freezes dispatching, driver offers, and ride matching across all service verticals:
```sql
REVOKE EXECUTE ON FUNCTION public.mobility_submit_driver_offer(UUID, BIGINT, TEXT, INT) FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.mobility_accept_dispatch(UUID, UUID) FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.mobility_select_driver_offer(UUID, UUID) FROM authenticated;
```

## 3. In-Flight Trip Protection & Drain
Even under Level 3 freeze, trips in `RIDER_ONBOARD` or `IN_PROGRESS` are allowed to transition to `ARRIVED_DESTINATION` and `COMPLETED` to protect rider and driver physical safety:
- `mobility_transition_trip` remains executable by assigned drivers.
- Safe trip sharing projections remain active until trip completion or manual revocation.

## 4. Unfreezing Protocol
1. Verify root cause mitigation has been thoroughly validated in ephemeral/staging environments.
2. Grant RPC privileges sequentially:
   ```sql
   GRANT EXECUTE ON FUNCTION public.mobility_create_ride_request(JSONB) TO authenticated;
   GRANT EXECUTE ON FUNCTION public.mobility_submit_driver_offer(UUID, BIGINT, TEXT, INT) TO authenticated;
   GRANT EXECUTE ON FUNCTION public.mobility_accept_dispatch(UUID, UUID) TO authenticated;
   GRANT EXECUTE ON FUNCTION public.mobility_select_driver_offer(UUID, UUID) TO authenticated;
   ```
3. Re-enable payment provider capabilities when cleared by Finance and Security.
