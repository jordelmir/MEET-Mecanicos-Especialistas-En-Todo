# Incident Response & On-Call Playbook

## 1. Severity Levels
- **SEV-1 (Critical):** Outage of physical diagnostic acquisition or complete cloud authentication failure.
- **SEV-2 (High):** Realtime WebSocket degraded; falling back to REST polling.
- **SEV-3 (Medium):** Minor background sync latency or non-blocking UI discrepancy.

## 2. Rollback Policy
If an API or Realtime regression occurs, feature flags toggle transport back to `LEGACY` mode instantly without requiring an APK release.
