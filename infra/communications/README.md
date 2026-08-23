# Elysium Communications infrastructure

This directory is the production deployment contract for voice calls. It does
not contain credentials and it does not treat a local Android build as proof of
a deployed calling service.

## 1. Generate the LiveKit deployment

Use LiveKit's official VM generator rather than maintaining a divergent copy of
its Caddy, Redis and TURN templates:

```sh
mkdir -p generated
docker pull livekit/generate
docker run --rm -it -v "$PWD/generated:/output" livekit/generate
```

The generator produces `caddy.yaml`, `docker-compose.yaml`, `livekit.yaml`,
`redis.conf` and a VM initialization script. Review and pin the generated
`livekit/livekit-server:v...` image before deployment. Never commit the generated
API key, API secret, private certificates or Redis password.

Required network exposure for the official VM topology:

- TCP 80 for certificate issuance.
- TCP 443 for HTTPS signaling and TURN/TLS.
- TCP 7881 for WebRTC fallback.
- UDP 3478 for TURN/UDP.
- UDP 50000-60000 for WebRTC media.

Both the primary LiveKit hostname and the TURN hostname must resolve to the
public deployment. TURN/TLS and a trusted certificate are mandatory production
gates; self-signed certificates are rejected.

## 2. Deploy the Supabase control plane

Apply the communications migration through the repository's normal Supabase
promotion pipeline, then configure and deploy the token function:

```sh
supabase secrets set \
  LIVEKIT_URL=https://livekit.example.com \
  LIVEKIT_API_KEY=replace-on-server \
  LIVEKIT_API_SECRET=replace-on-server
supabase functions deploy communications-call-token --no-verify-jwt
```

`--no-verify-jwt` delegates JWT verification to the function because the
function must call `auth.getUser()`, enforce conversation RLS, block state,
membership, identity binding and rate limiting before issuing a five-minute
LiveKit token. The platform JWT is still required by the function.

The following values are supplied by the Supabase runtime and must remain
server-only where applicable: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and
`SUPABASE_SERVICE_ROLE_KEY`.

## 3. Configure Android

Only the public HTTPS function URL is placed in untracked `local.properties`:

```properties
ELYSIUM_COMMUNICATION_CALL_TOKEN_URL=https://PROJECT.supabase.co/functions/v1/communications-call-token
```

No LiveKit API key, API secret, service-role key or participant token may be
packaged in the APK.

## 4. Release evidence

Run `./verify-livekit.sh` against the public endpoints, then prove two distinct
authenticated devices can:

1. open the same authorized service conversation;
2. reject an identity mismatch and a non-participant token request;
3. reject calls after either participant blocks the other;
4. establish audio on Wi-Fi and cellular networks;
5. establish audio through TURN/TLS with direct UDP intentionally blocked;
6. revoke microphone permission without crashing or leaking call state;
7. terminate and rejoin without reusing an expired participant token.

Record endpoint/TLS checks, Supabase migration identity, function deployment
identity, both device models, Android versions and UTC timestamps. A successful
APK build alone is not production proof.

Official deployment references:

- https://docs.livekit.io/transport/self-hosting/vm/
- https://docs.livekit.io/transport/self-hosting/deployment/
- https://docs.livekit.io/transport/self-hosting/ports-firewall/
