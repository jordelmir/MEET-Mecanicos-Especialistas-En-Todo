# Deployment Runbook

## 1. Image Promotion
Images are built via GitHub Actions and published to GHCR: `ghcr.io/elysium369/elysium-server:<git-sha>`.
Deployments reference explicit immutable image digests or Git SHAs.

## 2. Migration Order
1. Run Supabase migration verification in staging (`supabase db push` / `psql -v ON_ERROR_STOP=1`).
2. Verify server schema compatibility with Expand/Migrate/Contract strategy.
3. Deploy application containers via Docker Compose / Cloud-Init.
4. Execute health probes (`GET /health/ready`).
