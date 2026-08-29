# Infrastructure Architecture (IaC & Topology)

## 1. Managed & Self-Hosted Separation
- **Managed Data Plane:** Supabase (PostgreSQL, Auth, RLS, Storage, pgvector).
- **Application Plane:** Containerized `elysium-api` and `elysium-worker` running on dedicated VM.
- **Media Plane:** Dedicated LiveKit + TURN + Redis VM for WebRTC audio/video.
- **Edge Network:** Cloudflare for DNS, TLS, WAF, DDoS mitigation, and WebSocket reverse proxy.
- **Web Frontend:** Vercel static deployment.
