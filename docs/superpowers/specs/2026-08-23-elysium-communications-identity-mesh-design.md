# Elysium Communications and Vanguard Mesh — Product and System Design

**Date:** 2026-08-23
**Status:** Concept approved; written specification pending user review
**Scope:** Identity, discovery, privacy, blocking, presence, receipts, internet messaging and voluntary infrastructure-free communication

## 1. Product decision

Elysium Communications is one messaging system with several transports. Users
do not choose a different chat product when internet access disappears. The
same conversation, identity, block rules, encryption policy and append-only
event model continue through the best available route.

Elysium Vanguard Mesh is not limited to emergencies. Any user may opt in and
use it for ordinary family communication, local services, nearby rides,
workshops, events, disasters or deliberate internet shutdowns. Emergency mode
is an additional priority policy, not a separate network.

The system never depends on SMS or a telephone number. It may use the internet,
Bluetooth or Wi-Fi radios, but it must not require a mobile carrier, Google
Nearby Connections or an externally operated messaging network.

## 2. Non-negotiable truth rules

- A nearby relay is not the recipient.
- A relayed envelope is not a delivered message.
- A delivered message is not a read message.
- A mesh-visible device is not necessarily the account owner.
- A locally accepted ride is not server-settled payment.
- A queued proof is not cloud-certified until its authoritative synchronization
  and conflict checks complete.
- Local encryption at rest is not end-to-end encryption.
- Real-time multi-hop audio is not promised over BLE.
- Mesh range depends on compatible, consenting devices between endpoints.

Every UI state and receipt must preserve these distinctions.

## 3. Identity without SMS

### 3.1 Canonical identity

- `ActivePrincipal.id` remains the immutable internal account identity.
- Every account receives one unique, exact-match `@ElysiumID` for initiating
  contact. The handle is not a database primary key.
- A display name and avatar are presentation fields and are not proof of
  identity.
- A verified email address is an optional discovery and recovery alias.
- A rotating QR code and opaque deep link allow private invitations without
  revealing email or internal identifiers.
- Telephone numbers are not required for registration, discovery, login,
  recovery, two-factor authentication, messaging or calls.

### 3.2 Authentication and recovery

- Passkeys are the preferred authentication mechanism.
- Trusted-device linking uses an authenticated QR ceremony and explicit
  approval on an existing device.
- Users receive offline recovery codes that are generated with cryptographically
  secure randomness and shown once.
- TOTP is an optional second factor.
- Email magic links may recover an account only when the user explicitly bound
  and verified that email.
- Recovery cannot silently replace device identity keys. It creates an auditable
  key-transition event and alerts every remaining trusted device.

### 3.3 Discovery

- Exact `@ElysiumID`, rotating QR/link and verified email are supported.
- There is no enumerable global people directory by default.
- Email lookup returns the minimum message-request profile only when the target
  enabled email discovery.
- Simple hashes of email addresses are forbidden because their small input
  space permits dictionary reversal.
- Private address-book discovery requires an independently reviewed OPRF or PSI
  design. Until that exists, users select individual addresses to look up.
- Provider and service discovery is separate from personal-account discovery
  and exposes only fields authorized by the provider profile.

## 4. Privacy controls

Each principal owns a single synchronized policy with these controls:

| Capability | Allowed values |
|---|---|
| Find by Elysium ID | Everyone, nobody |
| Find by verified email | Everyone, contacts, nobody |
| See profile photo | Everyone, contacts, contacts except, nobody |
| See display name/about | Everyone, contacts, contacts except, nobody |
| See last active | Everyone, contacts, contacts except, nobody |
| See online status | Everyone, same as last active |
| Read receipts | On, off |
| Typing indicators | On, off |
| Call permission | Everyone, contacts, contacts except, nobody |
| Group invitation | Everyone, contacts, contacts except, nobody |
| Mesh discoverability | Off, contacts, approved communities, nearby requests |
| Relay participation | Off, contacts only, community |

Turning off social read receipts affects both sending and receiving social
receipts. Signed service acceptances, quote approvals and evidence acknowledgments
remain explicit contractual events and are never disguised as social “seen”.

Presence is ephemeral. The server and mesh keep only a short-lived lease, not a
historical surveillance log. A user may appear as `Internet`, `Nearby`,
`Reachable through mesh`, `Last synchronized` or `Unavailable`, subject to the
owner's privacy policy.

## 5. Requests, relationships and blocking

Unknown users enter through a message request. Before acceptance they cannot
call, enumerate devices, inspect detailed presence or receive private profile
fields.

A server-authoritative or cryptographically signed offline block:

- rejects new messages, calls, receipts and group invitations;
- hides future presence and protected profile changes;
- invalidates pending message and call requests;
- makes the server and recipient devices reject delivery in either direction;
  privacy-preserving relays may unknowingly carry the opaque envelope, but it
  produces no recipient delivery or read receipt;
- propagates as a high-priority control event when connectivity returns;
- never notifies the blocked user explicitly;
- does not silently delete either participant's existing local history;
- does not erase immutable service evidence needed for an active dispute.

Mute, archive and restrict are local or owner-scoped preferences and do not
pretend to be blocking. Reports create rate-limited abuse cases without exposing
private plaintext unnecessarily.

## 6. Unified communications event model

Text, files, images, audio notes, reactions, edits, redactions, membership,
receipts, privacy transitions and block transitions are append-only events.
Every transferable envelope includes:

- globally unique event ID;
- conversation ID represented by an unlinkable transport alias;
- sender device key reference;
- recipient or group key epoch;
- encrypted payload and authenticated metadata;
- client timestamp plus monotonic local sequence;
- expiry, priority and maximum hop count;
- payload digest and signature;
- idempotency key;
- optional previous custody receipt digest.

Relays see only the routing information strictly required to forward an opaque
envelope. They cannot decrypt the conversation, attachment keys, service proof
or precise location.

## 7. Vanguard Mesh architecture

### 7.1 Transport ladder

The mesh engine evaluates capabilities at runtime:

1. **BLE:** low-energy discovery, capability exchange, small control packets,
   text and custody receipts.
2. **Wi-Fi Aware:** preferred high-throughput peer connection without an access
   point on supported Android 8+ hardware.
3. **Wi-Fi Direct:** bulk transfer fallback for photographs, reports, audio notes
   and larger evidence packages.
4. **Local LAN:** peer discovery and encrypted sockets when devices share a
   router even if the router has no internet.
5. **Internet:** authoritative Supabase/communications transport when reachable.

Google Nearby Connections is not part of the trusted transport. Native Android
radio APIs sit behind Elysium-owned interfaces so a transport can be replaced
without changing identity, routing or conversation logic.

### 7.2 Routing

The routing core is platform-independent Kotlin and is tested without radios.
It implements bounded store-carry-forward:

- rotating pseudonymous peer beacons;
- authenticated capability negotiation;
- hop limit and absolute expiration;
- probabilistic encounter scoring without retaining a location history;
- duplicate suppression using bounded indexes;
- per-origin and per-priority quotas;
- backpressure and storage budgets;
- acknowledgments for custody, recipient delivery and read state;
- opportunistic reconciliation when partitions merge;
- no unrestricted broadcast flood.

An ordinary relay can transport ciphertext for unknown recipients. Relay
participation is voluntary and visible, with controls for charging-only,
battery threshold, daily data, storage, contact-only and community modes.

### 7.3 Emergency priority

Emergency mode raises the scheduling priority of signed SOS and welfare-check
messages, extends permitted storage time and offers an explicit location-sharing
choice. It does not bypass blocks, encryption, user consent or operating-system
permissions. False-emergency abuse is rate-limited and locally reportable.

## 8. Local calls

Calls use the same identity and block policies as messages.

- Devices on the same LAN, Wi-Fi Aware data path or Wi-Fi Direct group may
  establish a direct encrypted audio session without internet.
- BLE transports discovery and call signaling only; it is not advertised as an
  audio bearer.
- Mesh relays may forward an invitation until both endpoints discover a viable
  high-bandwidth path.
- A call fails honestly when no real-time path exists. It never falls back to a
  telephone number or system dialer.
- Multi-hop live audio across arbitrary BLE nodes is deferred until physical
  bandwidth, latency and battery tests prove it viable.

This supports family calls inside a house or nearby campus without claiming
nationwide infrastructure-free voice coverage.

## 9. Services and rides without internet

All existing service entry points use a transport router rather than directly
assuming cloud availability.

### 9.1 Nearby service discovery

Verified providers may publish a signed, short-lived service advertisement with
category, coarse service zone, availability and public provider proof. Personal
email, exact home location and internal principal IDs are never broadcast.

### 9.2 Offline ride flow

The mesh may transport:

1. a signed nearby ride request with coarse pickup rendezvous;
2. signed driver offers;
3. passenger selection;
4. mutual QR proximity confirmation;
5. trip start/end events;
6. safety check-ins and encrypted evidence;
7. a pending settlement record for later reconciliation.

Offline logistics must not fabricate escrow or payment finality. SINPE receipt
images are evidence submissions, not proof that funds settled. Any future
offline value instrument requires a separate double-spend-resistant monetary
design and is outside this communications authority.

### 9.3 Proofs and attachments

Quotes, receipts, reports and evidence use the same immutable digest as their
online representation. Small manifests travel over BLE; large encrypted blobs
wait for Wi-Fi Aware, Wi-Fi Direct, LAN or internet. Each custody hop is separate
from business approval and certified evidence status.

## 10. Android experience

### 10.1 Global surfaces

- Home quick action: `Elysium Mesh`.
- Messages header: effective route and proof state.
- Settings: `Identidad y privacidad`, `Dispositivos vinculados`, `Bloqueados`,
  `Descubrimiento`, `Mesh y relay`, `Datos y batería`.
- Persistent foreground notification only while the user enabled active relay.
- Quick Settings tile for mesh on/off.
- Clear permission education for nearby devices, Bluetooth, Wi-Fi and optional
  location sharing.

### 10.2 Integrated surfaces

Repair, mechanics, parts, tow, DEKRA, marketplace, workshops, vehicle access,
pre-purchase, universal services and rides show the same route selector and
delivery truth. Ride screens add `Buscar viaje cercano sin internet` and
provider screens add `Ofrecer servicio por mesh` when policy and hardware allow.

The user never has to create a second conversation when transport changes.

## 11. Local and server persistence

Room adds owner-scoped projections for identity aliases, privacy settings,
relationships, requests, blocks, presence leases, mesh peers, custody records,
outbox routes and attachment chunks. Plaintext messages are not added to
transport tables.

Supabase adds authoritative identity aliases, privacy policies, relationships,
blocks, message requests, device transitions and online presence leases. RLS
must prevent identifier enumeration and blocked-contact leakage. RPCs perform
exact lookup, request creation and relationship transitions with uniform error
responses and rate limits.

Mesh-only events remain locally authoritative about radio custody but become
server-authoritative only after authenticated reconciliation. Conflicts are
resolved by event identity, signatures, key epochs and monotonic server order,
never by last-write-wins on message contents.

## 12. Security model

Elysium owns the protocol and implementation, not novel cryptographic
primitives. The implementation uses reviewed standard constructions for:

- device signing and authenticated key agreement;
- per-device pairwise sessions;
- group sender-key epochs;
- authenticated encryption;
- key derivation and rotation;
- QR safety-code verification;
- encrypted attachment chunking;
- key transparency when internet is available.

Threats explicitly covered include malicious relays, replay, Sybil identities,
beacon tracking, message flooding, storage exhaustion, downgrade, stale device
keys, compromised servers, malicious group members and physical loss of a
device. Metadata resistance is measured separately from content encryption.

The product cannot claim end-to-end encryption or censorship resistance until
an independent protocol review, two-device key verification and adversarial
mesh tests pass.

## 13. Delivery sequence

1. Internet identity, exact discovery, privacy, requests and blocking.
2. Platform-independent envelope, routing simulator and adversarial tests.
3. One-hop BLE text and custody receipts.
4. Wi-Fi Aware, Wi-Fi Direct and LAN bulk transfer.
5. Multi-hop bounded store-carry-forward.
6. Unified cloud/mesh reconciliation.
7. Service and ride discovery plus offline trip ceremony.
8. Direct local encrypted audio calls.
9. Community relay nodes and optional dedicated long-range hardware.

Each step extends the existing application without removing internet operation
or claiming later proof states prematurely.

## 14. Mandatory verification gates

- Deterministic routing simulation with at least 100 nodes, partitions,
  duplicate delivery, clock skew, churn and malicious relays.
- Property and fuzz tests for packet parsing, fragmentation and reassembly.
- RLS tests proving email, presence, devices and blocks cannot be enumerated.
- Room migration tests without destructive fallback.
- Two-phone BLE delivery with internet and mobile data disabled.
- Two-phone Wi-Fi Aware/Direct transfer of a signed evidence package.
- Ten-device multi-hop drill with relay departure and re-entry.
- Same-house direct audio test without an internet route.
- Hardware matrix covering devices with and without Wi-Fi Aware.
- Battery, thermal, foreground-service and process-death measurements.
- Block propagation, revoked-device, replay, flood and storage-exhaustion tests.
- Offline ride request, offer, mutual QR start/end and later reconciliation.
- Independent cryptographic and privacy review before a production E2EE claim.

## 15. External technical references

- Android Wi-Fi Aware:
  https://developer.android.com/develop/connectivity/wifi/wifi-aware
- Android Wi-Fi Direct:
  https://developer.android.com/develop/connectivity/wifi/wifip2p
- Android BLE background operation:
  https://developer.android.com/develop/connectivity/bluetooth/ble/background
- Signal private contact discovery:
  https://signal.org/blog/private-contact-discovery/
- CFRG OPRF construction:
  https://www.rfc-editor.org/rfc/rfc9497.html
- WhatsApp multi-device architecture:
  https://engineering.fb.com/2021/07/14/security/whatsapp-multi-device/
- Briar offline synchronization:
  https://briarproject.org/quick-start/
- Bitchat project direction and acknowledged robustness work:
  https://github.com/orgs/permissionlesstech/discussions/139
