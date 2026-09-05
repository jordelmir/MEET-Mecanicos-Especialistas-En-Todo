# ELYSIUM CIRCLES & VANGUARD PTT — AUTHORITY MAP
## Master Authority Matrix: One Physical Fact → One Authority

```
Document ID: AUTH-CIRCLES-PTT-001
Principle: MORE CAPABILITY. FEWER COMPETING AUTHORITIES.
Zero competing roots. Zero client-side truth inventions.
```

---

## 1. Master Authority Matrix

| Physical Fact / Concept | Authoritative Owner | Canonical Persistence | Client Projection / Cache | Mutation Path | Authorization Policy | Evidence / Audit Trail |
|---|---|---|---|---|---|---|
| **1. Principal Identity** | `ActivePrincipalKernel` | Supabase Auth (`auth.users`) | `ActivePrincipal` StateFlow | Auth API (Login/Refresh) | JWT Verification | Auth Session Tokens |
| **2. Device Identity** | Local OS + Android Keystore | Device Hardware / OS | `localDeviceId` in Kernel | App Installation / Provisioning | Hardware Keystore attestation | Provisioning timestamp |
| **3. Conversation** | `ElysiumCommunicationRepository` | Supabase `conversations` | Room `communication_conversations` | Server RPC / REST | Active Participant check | Cryptographic UUIDv4 |
| **4. Conversation Membership** | `ElysiumCommunicationRepository` | Supabase `conversation_participants` | Room `communication_participants` | Creator / Admin RPC | Contextual ACL / Role | Audit events |
| **5. Circle** | `CircleKernel` | Supabase `circles` | Room `circle_entities` | Circle Owner / Admin RPC | Owner only for disband | Monotonic `accessEpoch` |
| **6. Circle Membership** | `CircleKernel` | Supabase `circle_memberships` | Room `circle_members` | Invite Accept / Remove RPC | Owner/Admin remove, self-leave | Membership transition log |
| **7. LocationShareGrant** | `PresenceKernel` | Supabase `location_share_grants` | Room `location_share_grants` | Self-Only Mutation API | Self-Authority: Owner only can broaden | Signed grant record |
| **8. PresenceSample** | Sensor Capture Engine | Supabase `presence_samples` (partitioned) | Local Bounded Encrypted Buffer | GPS Callback -> Quality Gate -> Outbox | Valid Publisher Lease required | (deviceId, streamId, sequence) |
| **9. Current Presence Snapshot** | `PresenceCore` | Supabase `presence_snapshots` | In-Memory / Room Snapshot | Authorized Publisher update only | Evaluated against `LocationShareGrant` | Freshness & Quality grade |
| **10. Location History** | `PresenceCore` | Supabase `presence_history` | Room (retention-bounded) | Batch uploader from outbox | Retention Policy (24h/7d/30d/90d) | SHA-256 chunk hash |
| **11. Place (Geofence)** | `PlaceKernel` | Supabase `places` | Room `places` | Circle Admin / Owner RPC | Place Consent Policy | PostGIS `ST_DWithin` boundary |
| **12. Safe Journey** | `SafeJourneyKernel` | Supabase `safe_journeys` | Room `safe_journeys` | User Action (Start/Check-in/End) | Traveler Self-Authority | Check-in receipts, Progress trace |
| **13. Emergency Session** | `EmergencySession` (Root) | Supabase `emergency_sessions` | Room `emergency_sessions` | User trigger / High-confidence crash | Emergency Priority Policy | Sensor snapshot, `EntityRef.EvidenceRef` |
| **14. PTT Channel Binding** | `PttKernel` | Supabase `ptt_channel_bindings` | In-Memory Channel Binding | Context creation (Circle/Ride/Group) | Derived from owning context | Owning Context Reference |
| **15. Floor Lease** | `FloorAuthority` | PostgreSQL `SELECT FOR UPDATE` | Local `FloorLease` model | Floor Request / Release RPC | Single Speaker / Priority Preemption | Monotonic `fencingToken` |
| **16. Media Permission** | LiveKit Server API | LiveKit SFU Room State | LiveKit Room Participant Track | Server-to-Server LiveKit API | Granted Floor Lease required | LiveKit WebRTC track events |
| **17. PTT Transmission** | `PttKernel` | Supabase `ptt_transmissions` | Room `ptt_transmissions` | Floor Release / Audio End | Fencing Token Match | SHA-256 audio hash, Monotonic seq |
| **18. Delivery Receipt** | Client Media Receiver | Supabase `ptt_delivery_receipts` | Room receipts | Receiver Playback Callback | Channel Participant | Explicit receipt states (not HUMAN_HEARD) |

---

## 2. Invariant Rules of Truth

1. **Self-Authority over Location**: A person shares their location by their own decision. Circle admins may *request* location, but can never enable, unpause, or increase precision unilaterally.
2. **Floor Control Separation from Media**: Floor Authority decides who speaks. LiveKit transports audio. Client UI is the authority for neither.
3. **Immediate Revocation**: Membership removal or user block increments `accessEpoch`, isolating subsequent broadcasts from stale WebSocket connections.
4. **Anti-Averaging Privacy**: Approximate location is computed via fixed spatial grids, never stochastic random noise.
