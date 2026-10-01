package com.elysium.nexus.fabric.sdk

/**
 * §39-42 Elysium Adapter SDK.
 *
 * Defines the structure for adapter packages.
 * Each adapter declares:
 * - identity
 * - version
 * - developer
 * - supported platforms
 * - permissions
 * - discovery
 * - pairing
 * - capabilities
 * - commands
 * - events
 * - security model
 * - compatibility
 *
 * ## Security Model
 *
 * Plugins never receive unrestricted access.
 * Capabilities:
 * - NETWORK_LOCAL
 * - BLUETOOTH
 * - DEVICE_STATE_READ
 * - DEVICE_ACTION_WRITE
 * - HOST_CONTEXT
 *
 * User consent required for each.
 *
 * No:
 * - arbitrary filesystem
 * - arbitrary Android permissions
 *
 * ## Signed Packages
 *
 * Each package:
 * - manifest
 * - binary/script
 * - hash
 * - signature
 * - permissions
 * - version
 * - compatibility
 * - test evidence
 *
 * Core verifies before loading.
 *
 * ## Classification
 *
 * - ELYSIUM_OFFICIAL
 * - MANUFACTURER_CERTIFIED
 * - COMMUNITY_VERIFIED
 * - EXPERIMENTAL
 * - BLOCKED
 */

/**
 * Adapter package manifest.
 */
data class AdapterManifest(
    val id: String,
    val name: String,
    val version: String,
    val developer: String,
    val description: String,
    val supportedPlatforms: Set<String>,
    val permissions: Set<AdapterPermission>,
    val capabilities: Set<String>,
    val commands: List<AdapterCommand>,
    val events: List<AdapterEvent>,
    val securityModel: SecurityModel,
    val compatibility: AdapterCompatibility,
    val signature: ByteArray? = null
) {
    init {
        require(id.isNotBlank()) { "Adapter ID must be non-blank." }
        require(version.isNotBlank()) { "Adapter version must be non-blank." }
    }
}

/**
 * Adapter permissions.
 */
enum class AdapterPermission {
    NETWORK_LOCAL,
    BLUETOOTH,
    DEVICE_STATE_READ,
    DEVICE_ACTION_WRITE,
    HOST_CONTEXT,
    FILE_READ,
    FILE_WRITE,
    NOTIFICATIONS
}

/**
 * Adapter command definition.
 */
data class AdapterCommand(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<CommandParameter>,
    val returnType: String,
    val requiresConfirmation: Boolean = false
)

data class CommandParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)

/**
 * Adapter event definition.
 */
data class AdapterEvent(
    val id: String,
    val name: String,
    val description: String,
    val payload: Map<String, String>
)

/**
 * Security model for an adapter.
 */
data class SecurityModel(
    val trustLevel: TrustLevel,
    val requiresUserConsent: Boolean,
    val sandboxed: Boolean,
    val allowedNetworks: List<String> = emptyList(),
    val maxDataRetentionMs: Long = 86_400_000L // 24h
)

enum class TrustLevel {
    Untrusted,
    CommunityVerified,
    OfficialVerified,
    ManufacturerCertified
}

/**
 * Adapter compatibility.
 */
data class AdapterCompatibility(
    val minSdkVersion: Int? = null,
    val maxSdkVersion: Int? = null,
    val supportedLanguages: Set<String> = emptySet(),
    val testEvidence: List<TestEvidence> = emptyList()
)

data class TestEvidence(
    val testType: String,
    val result: String,
    val timestampMs: Long,
    val environment: String
)

/**
 * Adapter classification.
 */
enum class AdapterClassification(val displayName: String) {
    ELYSIUM_OFFICIAL("Elysium Official"),
    MANUFACTURER_CERTIFIED("Manufacturer Certified"),
    COMMUNITY_VERIFIED("Community Verified"),
    EXPERIMENTAL("Experimental"),
    BLOCKED("Blocked")
}

/**
 * Loaded adapter package.
 */
data class LoadedAdapter(
    val manifest: AdapterManifest,
    val classification: AdapterClassification,
    val loadedAtMs: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
