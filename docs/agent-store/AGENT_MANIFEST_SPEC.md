# Agent Manifest Specification & Store Model

**Standard:** Master Order Omega (§31–§39)  
**Status:** Implemented & Verified (v4.26.1)

---

## 1. Agent Manifest Schema

Every agent in Elysium must provide a declarative manifest. The manifest defines identity, voice, personality, declared capabilities, and required entitlements:

```kotlin
data class AgentManifest(
    val id: String,
    val version: String = "1.0.0",
    val displayName: String,
    val description: String,
    val category: AgentCategory,
    val avatarAssetId: String,
    val voiceProfileId: String,
    val personalityProfileId: String,
    val capabilityIds: List<String>,
    val knowledgePackIds: List<String> = emptyList(),
    val minimumAppVersion: String = "4.26.0",
    val requiredEntitlement: String? = null,
    val isFree: Boolean = (requiredEntitlement == null),
    val priceFiatCrc: Long = 0L,
    val safetyPolicyVersion: String = "2026.1",
    val privacyDescription: String,
    val supportedLanguages: List<String> = listOf("es", "en"),
    val releaseState: AgentReleaseState = AgentReleaseState.OFFICIAL,
)
```

---

## 2. Security & Invariant Rules

1. **Manifest Does Not Grant Authority**:
   Declaring a capability in an `AgentManifest` does not authorize execution. The `AgentPolicyEngine` strictly validates user entitlement and domain preconditions before invocation.
2. **No Artificial Scarcity**:
   The store prohibits countdown timers, fake FOMO banners, or manipulative purchasing loops.
3. **Preview Mode Security**:
   Users may preview locked agents (rendering 3D avatars, listening to voice samples, and viewing capabilities). However, the engine denies invocation of locked capabilities until an authoritative entitlement is confirmed.
