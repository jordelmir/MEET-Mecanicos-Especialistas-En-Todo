# Disaster Recovery Plan

## 1. Failure Domains & Recovery Procedures
- **Application VM Failure:** Automated OpenTofu redeploy + Docker container start using Cloud-Init.
- **LiveKit VM Failure:** Standalone LiveKit VM recreation via IaC; signaling reconnects automatically.
- **Database Incident:** Point-in-Time Recovery (PITR) via Supabase managed backups + secondary offsite logical backups.
