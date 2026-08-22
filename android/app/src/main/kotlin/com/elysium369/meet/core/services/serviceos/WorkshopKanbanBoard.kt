package com.elysium369.meet.core.services.serviceos

import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.CurrencyCode
import java.util.UUID

enum class KanbanStage(val displayName: String, val badgeColorHex: Long) {
    CHECK_IN("Recepción / Check-in", 0xFF64B5F6),
    PRE_SCAN_DIAGNOSIS("Pre-Scan y Diagnóstico", 0xFFFFB74D),
    ESTIMATE_PENDING_APPROVAL("Esperando Aprobación", 0xFFFF8A65),
    IN_REPAIR("En Reparación Activa", 0xFF00E676),
    WAITING_PARTS("Esperando Repuestos", 0xFFFFD54F),
    POST_SCAN_VERIFICATION("Post-Scan y Verificación", 0xFF00E5FF),
    READY_FOR_DELIVERY("Listo para Entrega", 0xFF81C784)
}

enum class ChangeOrderStatus {
    PENDING_CUSTOMER_APPROVAL,
    APPROVED,
    REJECTED
}

data class ChangeOrder(
    val changeOrderId: String = UUID.randomUUID().toString(),
    val workOrderId: String,
    val discoveredFinding: String,
    val description: String,
    val evidencePhotoUri: String? = null,
    val laborDelta: Money,
    val partsDelta: Money,
    val timeDeltaHours: Double = 0.0,
    val status: ChangeOrderStatus = ChangeOrderStatus.PENDING_CUSTOMER_APPROVAL,
    val requestedAtMs: Long = System.currentTimeMillis(),
    val resolvedAtMs: Long? = null
) {
    val totalAdditionalCost: Money
        get() = laborDelta.plus(partsDelta)
}

data class WorkshopWorkOrderSummary(
    val orderId: String,
    val vehicleDisplayName: String,
    val customerName: String,
    val stage: KanbanStage,
    val assignedBayName: String?,
    val assignedTechnicianName: String?,
    val authorizedAmount: Money,
    val pendingChangeOrders: List<ChangeOrder> = emptyList(),
    val startedAtMs: Long,
    val targetDeliveryAtMs: Long
)

data class WorkshopFinancialOverview(
    val currency: CurrencyCode,
    val totalOfferedAmount: Money,
    val totalAuthorizedAmount: Money,
    val totalInvoicedAmount: Money,
    val totalCollectedAmount: Money,
    val totalReceivableAmount: Money
)
