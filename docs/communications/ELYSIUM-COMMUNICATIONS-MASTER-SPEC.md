# Elysium Communications — Master Specification

**Status:** Approved for incremental implementation
**Product name:** Mensajes
**Internal platform:** Elysium Communications Core
**Truth rule:** A UI, local database, or client SDK does not prove that messaging or calls are production-ready.

## Product contract

Mensajes is a first-class destination in MEET. It also opens in the context of
every service, including rides. Service participants communicate without
exposing their telephone number. A phone icon starts an Elysium call; it must
never silently fall back to the system dialer.

The canonical identity is `ActivePrincipal.id`. Phone numbers are optional,
verified discovery aliases, never primary keys. Unknown principals enter through
message requests and cannot call until accepted.

## Authority boundaries

| Authority | Owns | Must not own |
|---|---|---|
| Supabase/Postgres | principals, services, participant authorization, conversation bindings | plaintext message content or media keys |
| Communications transport | encrypted envelopes, delivery ordering, call room authorization | service payment/evidence state |
| Android client | device keys, local plaintext view, encrypted outbox, user consent | server authorization decisions |
| Evidence system | explicitly promoted immutable evidence | ordinary chat history |

Realtime events are delivery hints. Durable encrypted events are the source of
truth. Message edits, redactions, receipts and membership changes are append-only
events. No sender may update an existing message row.

## Proof states

1. `MODEL_EXISTS`: contracts and migrations exist.
2. `CLIENT_IMPLEMENTED`: Android UI, local persistence and fail-closed adapters exist.
3. `SERVER_AUTHORITATIVE`: authenticated infrastructure enforces participants,
   ordering, idempotency and short-lived call tokens.
4. `PHYSICALLY_VERIFIED`: two independent devices exchange messages and calls
   through production-like infrastructure under adverse-network tests.

The UI must display the effective proof state and must not label local device
encryption as end-to-end encryption.

## Security invariants

- No plaintext message, media key, token, phone number or call SDP in logs.
- Device keys are non-exportable where Android Keystore support permits it.
- Push payloads contain only opaque wake-up identifiers.
- Media is encrypted client-side before upload.
- Calls are not recorded by default.
- A service conversation is participant-bound to an immutable service reference.
- Promoting a message or attachment to evidence requires an explicit action,
  consent, hash and immutable evidence record.
- Unknown contacts cannot call before the message request is accepted.
- Blocks and participant revocation fail closed.

## Incremental delivery

### Release A — Universal shell and local authority

- Universal conversations, participants, encrypted local events and receipts.
- Inbox and conversation UI.
- Permanent Home quick action.
- Context entry points for repair, tow, parts, rides, inspection, vehicle access
  and universal services.
- Call UI that fails closed unless an authorized call transport is configured.

### Release B — Authoritative encrypted transport

- Self-hosted Matrix deployment and Elysium identity bridge.
- Device verification, cross-signing, encrypted media and recovery.
- Migration adapter for `ride_messages` without deleting legacy history.
- Message requests, blocking, reporting and abuse-rate limits.

### Release C — Calls

- Self-hosted LiveKit and TURN.
- Server-minted, short-lived participant tokens.
- Android Core-Telecom, foreground call service and opaque push wakeups.
- 1:1 audio, then video and groups after security and network gates.

### Release D — Multi-device and expansion

- QR linking, encrypted history transfer and device revocation.
- Web/desktop/iOS clients.
- Groups, communities and channels.

## Mandatory gates

- Room migration from the previous schema is tested without destructive fallback.
- Two-principal RLS tests prove non-participants cannot enumerate metadata.
- Duplicate, replay, reordering and offline reconnection tests pass.
- Static checks reject message row update policies and plaintext logging.
- Two physical Android devices pass send, receive, attachment, call, revoke and
  account-recovery scenarios before `PHYSICALLY_VERIFIED` is claimed.
