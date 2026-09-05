# MEET Infrastructure as Code (Wave 28)

## Overview

Terraform configurations for MEET cloud infrastructure.
Managed by Supabase for database + auth + realtime.

## Stack

- **Database**: Supabase (PostgreSQL 15)
- **Auth**: Supabase Auth (JWT)
- **Storage**: Supabase Storage (encrypted)
- **Realtime**: Supabase Realtime (WebSocket)
- **Edge Functions**: Supabase Edge Functions (Deno)
- **CDN**: Cloudflare (future)
- **Monitoring**: OpenTelemetry + Grafana (future)

## Setup

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

## Environments

- `dev` — Development (Supabase project)
- `staging` — Pre-production
- `prod` — Production

## Secrets

Never commit secrets. Use:
- Supabase Dashboard for API keys
- Environment variables for local dev
- Terraform sensitive variables for production
