package com.elysium369.meet.core.parts

object PartsMarketplaceContract {
    fun requestStatusToLegacy(status: String?): String = when (status?.trim()?.uppercase()) {
        "DRAFT", "OPEN", "RECEIVING_QUOTES" -> "OPEN"
        "QUOTE_ACCEPTED", "WAITING_PAYMENT", "ORDERED", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY" -> "ACCEPTED"
        "DELIVERED" -> "DELIVERED"
        "CANCELLED", "DISPUTED" -> "CANCELLED"
        "ACCEPTED" -> "ACCEPTED"
        else -> status?.takeIf { it.isNotBlank() } ?: "OPEN"
    }

    fun quoteStatusToLegacy(status: String?): String = when (status?.trim()?.uppercase()) {
        "SENT", "PENDING" -> "PENDING"
        "ACCEPTED" -> "ACCEPTED"
        "REJECTED", "EXPIRED", "CANCELLED" -> "REJECTED"
        else -> status?.takeIf { it.isNotBlank() } ?: "PENDING"
    }

    fun preferenceToLegacy(preference: String?): String = when (preference?.trim()?.uppercase()) {
        "OEM" -> "OEM"
        "AFTERMARKET" -> "AFTERMARKET"
        "ANY", "USED", "REFURBISHED", "PERFORMANCE", "BUDGET" -> "ANY"
        else -> preference?.takeIf { it.isNotBlank() } ?: "ANY"
    }

    fun positionToLegacy(position: String?): String = when (position?.trim()?.uppercase()) {
        "FRONT_RIGHT" -> "DELANTERA_DERECHA"
        "FRONT_LEFT" -> "DELANTERA_IZQUIERDA"
        "REAR_RIGHT" -> "TRASERA_DERECHA"
        "REAR_LEFT" -> "TRASERA_IZQUIERDA"
        "CENTER", "ENGINE", "TRANSMISSION", "ELECTRICAL", "BODY", "INTERIOR", "FUSE_BOX" -> "CENTRAL"
        "NOT_APPLICABLE", "N/A" -> "N/A"
        else -> position?.takeIf { it.isNotBlank() } ?: "N/A"
    }

    fun conditionToLegacy(condition: String?): String = when (condition?.trim()?.uppercase()) {
        "NEW_OEM" -> "OEM"
        "NEW_AFTERMARKET" -> "NEW"
        "USED" -> "USED_TESTED"
        "REFURBISHED", "REBUILT" -> "REMAN"
        "UNKNOWN" -> "NEW"
        else -> condition?.takeIf { it.isNotBlank() } ?: "NEW"
    }

    fun requestStatusToV2(status: String?): String = when (status?.trim()?.uppercase()) {
        "DRAFT" -> "DRAFT"
        "OPEN", "RECEIVING_QUOTES" -> "OPEN"
        "ACCEPTED", "QUOTE_ACCEPTED", "WAITING_PAYMENT", "ORDERED", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY" -> "QUOTE_ACCEPTED"
        "DELIVERED" -> "DELIVERED"
        "CANCELLED" -> "CANCELLED"
        "DISPUTED" -> "DISPUTED"
        else -> "OPEN"
    }

    fun quoteStatusToV2(status: String?): String = when (status?.trim()?.uppercase()) {
        "PENDING", "SENT" -> "SENT"
        "ACCEPTED" -> "ACCEPTED"
        "REJECTED" -> "REJECTED"
        "EXPIRED" -> "EXPIRED"
        "CANCELLED" -> "CANCELLED"
        else -> "SENT"
    }

    fun preferenceToV2(preference: String?): String = when (preference?.trim()?.uppercase()) {
        "OEM" -> "OEM"
        "AFTERMARKET" -> "AFTERMARKET"
        "USED" -> "USED"
        "REFURBISHED", "REMAN" -> "REFURBISHED"
        "PERFORMANCE" -> "PERFORMANCE"
        "BUDGET" -> "BUDGET"
        else -> "ANY"
    }

    fun positionToV2(position: String?): String = when (position?.trim()?.uppercase()) {
        "DELANTERA_DERECHA", "FRONT_RIGHT" -> "FRONT_RIGHT"
        "DELANTERA_IZQUIERDA", "FRONT_LEFT" -> "FRONT_LEFT"
        "TRASERA_DERECHA", "REAR_RIGHT" -> "REAR_RIGHT"
        "TRASERA_IZQUIERDA", "REAR_LEFT" -> "REAR_LEFT"
        "CENTRAL", "CENTER", "ENGINE", "TRANSMISSION", "ELECTRICAL", "BODY", "INTERIOR", "FUSE_BOX" -> "CENTER"
        else -> "NOT_APPLICABLE"
    }

    fun conditionToV2(condition: String?): String = when (condition?.trim()?.uppercase()) {
        "OEM", "NEW_OEM" -> "NEW_OEM"
        "NEW", "AFTERMARKET", "NEW_AFTERMARKET" -> "NEW_AFTERMARKET"
        "USED", "USED_TESTED" -> "USED"
        "REMAN", "REFURBISHED" -> "REFURBISHED"
        "REBUILT" -> "REBUILT"
        else -> "UNKNOWN"
    }
}
