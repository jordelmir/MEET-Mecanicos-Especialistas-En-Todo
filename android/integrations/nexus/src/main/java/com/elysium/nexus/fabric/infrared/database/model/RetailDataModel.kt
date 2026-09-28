package com.elysium.nexus.fabric.infrared.database.model

/**
 * Phase 13 — Device Model Graph & Phase 14 — Retail Data Model
 *
 * Provides authoritative models for exact device identity (MPN), retailer SKU links
 * (Monge, Gollo, Verdugo), physical test evidence, and signed compatibility certificates.
 */

data class DeviceModel(
    val id: String,
    val brandName: String,
    val modelName: String,
    val exactMpn: String,
    val deviceType: String = "TV",
    val hardwareFamily: String? = null,
    val originalRemoteModel: String? = null
)

enum class RetailerName {
    MONGE_CR,
    GOLLO_CR,
    VERDUGO_CR
}

data class RetailerSku(
    val id: String,
    val retailer: RetailerName,
    val skuCode: String,
    val gtin: String? = null,
    val mpn: String,
    val deviceModelId: String?,
    val isActive: Boolean = true,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class PhysicalTestEvidence(
    val id: String,
    val deviceModelId: String,
    val actionKey: String,
    val signalId: String,
    val physicalSha256: String,
    val measuredCarrierHz: Int,
    val transmitterHardware: String,
    val receiverHardware: String,
    val verifiedAtTimestamp: Long = System.currentTimeMillis(),
    val status: String = "HIL_VERIFIED"
)

data class RetailCompatibilityCertificate(
    val certificateId: String,
    val retailer: RetailerName,
    val skuCode: String,
    val exactMpn: String,
    val coreActionsVerified: Set<String>,
    val extendedActionsVerified: Set<String> = emptySet(),
    val physicalEvidenceShaList: List<String>,
    val verifiedAtTimestamp: Long = System.currentTimeMillis(),
    val digitalSignature: String
)
