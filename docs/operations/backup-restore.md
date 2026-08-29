# Backup & Restore Verification Runbook

## 1. Automated Backups
- Supabase automated daily backups + PITR.
- Scheduled logical dump of relational state encrypted and replicated offsite.

## 2. Restore Drills
Quarterly execution of complete restore into an isolated staging project to verify backup integrity (`RECOVERY_VERIFIED`).
