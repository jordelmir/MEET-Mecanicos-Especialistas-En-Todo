# ELYSIUM CIRCLES & VANGUARD PTT — CURRENT STATE AUDIT
## Baseline Audit at SHA 2f3b4535d8fc9c82d4d718217afe73ab28b65a2c

```
Document ID: AUDIT-CIRCLES-PTT-001
Audited SHA: 2f3b4535d8fc9c82d4d718217afe73ab28b65a2c
Repository: jordelmir/MEET-Mecanicos-Especialistas-En-Todo
Auditor: Antigravity Staff Engineering Core
Classification Standard: Section 5 / Section 142
```

---

## 1. Capability Inventory & Truth Classification

| Capability | Component / Path | Current Classification | Audited Reality & Evidence |
|---|---|---|---|
| **Identity & Active Principal** | `identity/ActivePrincipalKernel.kt` | `SERVER_AUTHORITATIVE` | Bound to Supabase Auth (`auth.uid()`) + local Android ID. Separates authenticated vs local tenants cleanly. |
| **Device Binding** | `identity/ActivePrincipalKernel.kt` | `CLIENT_IMPLEMENTED` | `stableDeviceId` reads `Settings.Secure.ANDROID_ID`. Subordinate to principal identity. |
| **Conversations & Participants** | `communications/ElysiumCommunicationRepository.kt` | `SERVER_AUTHORITATIVE` | Room persistence + Supabase backend. Supports DIRECT, SERVICE, GROUP, PERSONAL. |
| **Communication Blocks** | `communications/CommunicationDao.kt` | `SERVER_AUTHORITATIVE` | `CommunicationLocalBlockEntity` + Supabase sync. Blocks override message delivery and discovery. |
| **Privacy Settings** | `communications/CommunicationPrivacySettingsEntity` | `SERVER_AUTHORITATIVE` | Controls discovery by phone, email, and presence visibility flags. |
| **Call Transport (1-on-1 Audio)** | `communications/ElysiumCallTransport.kt` | `SERVER_AUTHORITATIVE` | LiveKit Android real SDK. Validates HTTPS/WSS endpoints, authenticates with current Supabase Bearer token. Unconditionally enables mic on connect. |
| **Local Message Cipher** | `communications/DeviceMessageCipher.kt` | `CLIENT_IMPLEMENTED` | Non-exportable Android Keystore AES-GCM. Honest: documented as local projection encryption, NOT E2EE. |
| **Physical Location Producer** | `ride/location/RideLocationTrackingService.kt` | `CLIENT_IMPLEMENTED` | FusedLocationProviderClient (Google Play Services), FGS with notification, Priority.PRIORITY_HIGH_ACCURACY. Scoped to active driver trips only. |
| **Offline Breadcrumb Queue** | `ride/work/RideLocationBreadcrumbWorker.kt` | `INTEGRATION_VERIFIED` | Encrypted breadcrumbs queued via WorkManager for reliable offline batch upload. |
| **Location Interpolator / Map** | `ride/location/MapLocationInterpolator.kt` | `CLIENT_IMPLEMENTED` | Smooths polyline updates for UI rendering. Not a general Presence authority. |
| **Emergency Root** | `emergency/EmergencySession.kt` | `MODEL_EXISTS` | Exists for automotive breakdown triage with `EntityRef.EvidenceRef`. Needs extension for journey/circle/ptt contexts. |
| **Presence Core** | `presence/PresenceCore.kt` | `MODEL_EXISTS` | Initial drafted models exist without multi-device publisher lease, fencing, or anti-averaging projection. |
| **Elysium Circles** | `circles/CircleModels.kt` | `MODEL_EXISTS` | Basic data structures exist without `AuthorizationEpoch`, invite hashing, or RLS integration. |
| **Vanguard PTT** | `ptt/PttModels.kt` | `MODEL_EXISTS` | Initial models exist, but lacks server-authoritative floor lease, fencing tokens, two-phase LiveKit permission gating, and store-and-forward encryption. |

---

## 2. Core Architectural Questions & Findings

### Q1: Where is physical location acquired? Who owns it?
- **Acquisition**: Physical GPS/GNSS is captured by `RideLocationTrackingService` via Google Play `FusedLocationProviderClient` with `Priority.PRIORITY_HIGH_ACCURACY` at 5-second intervals.
- **Ownership**: In Ride, it is owned by the assigned driver's active trip. There is currently **no shared Presence Core** for general family/circle tracking.

### Q2: Who owns Ride location?
- `RideCommandRepository` and `RideLocationTrackingService`. Exact points are encrypted with `DeviceMessageCipher` and uploaded in batches by `RideLocationBreadcrumbWorker`.

### Q3: What survives process death?
- Room DB tables (`MeetDatabase`, schema 70): Conversations, events, local blocks, offline outbox, ride cache.
- WorkManager tasks: `RideLocationBreadcrumbWorker`, `RideCommandSyncWorker`.
- SharedPreferences: Active trip ID (`KEY_ACTIVE_TRIP`).
- LiveKit WebRTC sessions do **NOT** survive process death; they disconnect and require re-authentication.

### Q4: What is Room-only vs Server-authoritative?
- **Room-only**: Unsynced draft messages, local ephemeral UI state, cached map avatars.
- **Server-authoritative**: Conversation membership, payment ledger, driver eligibility, Supabase Auth tokens, and signed evidence hashes.

### Q5: What is Realtime-only vs Simulated?
- **Realtime-only**: Ephemeral typing indicators, driver coordinate broadcast during trip (accelerates display, backed by persistent breadcrumbs).
- **Simulated**: Unit test doubles (`FakeSupabaseClient`, `SimulatedProgrammingEcu`). In production APK, no simulated locations are generated.

### Q6: How are devices tied to principals?
- `ActivePrincipalKernel` retrieves `ANDROID_ID` as `localDeviceId`. If authenticated, `ActivePrincipal(id = auth.uid())` owns the session.
- Currently, there is no multi-device coordination mechanism (e.g. `PresencePublisherLease`) to prevent two devices logged into the same account from racing on location.

### Q7: How are blocks represented?
- In `CommunicationDao`, `CommunicationLocalBlockEntity` stores `(ownerPrincipalId, blockedPrincipalId)`.
- Enforced locally in search and query filtering, synced with Supabase `communication_blocks` table.

### Q8: How are calls authorized and LiveKit tokens minted?
- In `ElysiumCallTransport`: calls `BuildConfig.COMMUNICATION_CALL_TOKEN_URL` over HTTPS with `Authorization: Bearer <accessToken>`.
- Token contains room name, participant identity (`principalId`), and signed JWT.
- **Flaw for PTT**: In normal calls, `room.localParticipant.setMicrophoneEnabled(true)` runs immediately. In PTT, a user must join with `canPublish = false` and remain receive-only until granted the floor.

### Q9: What current emergency authority exists?
- `EmergencySession` in `com.elysium369.meet.emergency`.
- Has `sessionId`, `vehicleId`, `type`, `steps`, `recommendedResolution`, `evidenceRefs`.
- Must be extended with optional references to circles, journeys, PTT channels, and presence evidence rather than creating a competing `PttEmergencySession`.
