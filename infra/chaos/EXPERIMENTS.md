# MEET Chaos Engineering (Wave 30)

## Principles

1. **Chaos in staging only** — Never in production without approval
2. **Blast radius control** — Limit damage per experiment
3. **Automated rollback** — Every experiment has a kill switch
4. **Observability first** — Monitor before, during, after

## Experiment Scenarios

### Network Chaos
- [ ] Simulate offline mode (Airplane mode)
- [ ] Simulate high latency (2G/3G)
- [ ] Simulate packet loss (10%, 25%, 50%)
- [ ] Simulate DNS failure

### Database Chaos
- [ ] Simulate Supabase timeout
- [ ] Simulate connection pool exhaustion
- [ ] Simulate write contention
- [ ] Simulate migration failure

### Service Chaos
- [ ] Simulate Ktor server restart
- [ ] Simulate WebSocket disconnect
- [ ] Simulate Edge Function timeout
- [ ] Simulate storage quota exceeded

### Device Chaos
- [ ] Simulate low battery (10%)
- [ ] Simulate low storage (100MB)
- [ ] Simulate high CPU (thermal throttling)
- [ ] Simulate sensor failure (GPS, OBD)

### Security Chaos
- [ ] Simulate expired JWT
- [ ] Simulate invalid RLS policy
- [ ] Simulate unauthorized access attempt
- [ ] Simulate data exfiltration attempt

## Running Experiments

```bash
# Android
adb shell am broadcast -a com.elysium369.meet.CHAOS_TEST --es scenario "offline_mode"

# Server
curl -X POST http://localhost:8080/chaos/simulate -d '{"scenario": "db_timeout"}'

# Web
npm run chaos:network
```

## Safety

- All experiments run in `staging` environment
- Kill switch: `adb shell am broadcast -a com.elysium369.meet.CHAOS_KILL`
- Maximum duration: 5 minutes per experiment
- Automatic rollback if error rate > 10%
