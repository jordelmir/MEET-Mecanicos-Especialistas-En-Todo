package com.elysium369.meet.core.services.parts

import com.elysium369.meet.core.services.kernel.ServiceRole
import java.util.UUID

/**
 * Server-authoritative states matching Supabase part_request_status_v2.
 */
enum class PartRequestStatusV2(val displayName: String) {
    DRAFT("Borrador"),
    OPEN("Abierto"),
    RECEIVING_QUOTES("Recibiendo Cotizaciones"),
    QUOTE_ACCEPTED("Cotización Aceptada"),
    WAITING_PAYMENT("Esperando Pago"),
    ORDERED("Pedido Confirmado"),
    READY_FOR_PICKUP("Listo para Retiro"),
    OUT_FOR_DELIVERY("En Camino con Repartidor"),
    DELIVERED("Entregado al Cliente"),
    CANCELLED("Cancelado"),
    DISPUTED("En Disputa");

    val isTerminal: Boolean
        get() = this in setOf(DELIVERED, CANCELLED, DISPUTED)
}

/**
 * Rigorous fitment compatibility confidence levels.
 */
enum class CompatibilityConfidence(val rank: Int, val description: String) {
    EXACT(5, "VIN + OEM coinciden exactamente en catálogo oficial."),
    HIGH(4, "Catálogo de aplicación coincide en marca/modelo/año/motor/caja."),
    PROBABLE(3, "Dimensiones, montaje y conector son compatibles con la pieza original."),
    CONDITIONAL(2, "Requiere confirmación física previa a la instalación."),
    UNKNOWN(1, "Evidencia insuficiente para certificar compatibilidad."),
    CONFLICTED(0, "Incompatibilidad confirmada.");

    companion object {
        fun evaluate(
            vinMatched: Boolean,
            oemMatched: Boolean,
            catalogExact: Boolean,
            specsMatched: Boolean,
            hasConflict: Boolean,
        ): CompatibilityConfidence {
            if (hasConflict) return CONFLICTED
            if (vinMatched && oemMatched) return EXACT
            if (catalogExact) return HIGH
            if (specsMatched) return PROBABLE
            return UNKNOWN
        }
    }
}

sealed interface PartCommandAction {
    data class SubmitQuote(val storeId: UUID, val priceCents: Long) : PartCommandAction
    data class AcceptQuote(val quoteId: UUID) : PartCommandAction
    object ConfirmOrder : PartCommandAction
    object MarkReadyForPickup : PartCommandAction
    data class DispatchOrder(val trackingRef: String?) : PartCommandAction
    data class ConfirmReceipt(val receiptEvidenceHash: String?) : PartCommandAction
    data class RaiseDispute(val reason: String) : PartCommandAction
    data class Cancel(val reason: String) : PartCommandAction
}

object PartsStateEngine {
    fun getNextState(
        fromState: PartRequestStatusV2,
        action: PartCommandAction,
        actorRole: ServiceRole,
    ): PartRequestStatusV2? = when (action) {
        is PartCommandAction.SubmitQuote -> {
            if (fromState in setOf(PartRequestStatusV2.OPEN, PartRequestStatusV2.RECEIVING_QUOTES)) {
                PartRequestStatusV2.RECEIVING_QUOTES
            } else null
        }
        is PartCommandAction.AcceptQuote -> {
            if (fromState == PartRequestStatusV2.RECEIVING_QUOTES && actorRole == ServiceRole.CUSTOMER) {
                PartRequestStatusV2.QUOTE_ACCEPTED
            } else null
        }
        is PartCommandAction.ConfirmOrder -> {
            if (fromState in setOf(PartRequestStatusV2.QUOTE_ACCEPTED, PartRequestStatusV2.WAITING_PAYMENT)) {
                PartRequestStatusV2.ORDERED
            } else null
        }
        is PartCommandAction.MarkReadyForPickup -> {
            if (fromState == PartRequestStatusV2.ORDERED && actorRole == ServiceRole.PARTS_STORE_AGENT) {
                PartRequestStatusV2.READY_FOR_PICKUP
            } else null
        }
        is PartCommandAction.DispatchOrder -> {
            if (fromState in setOf(PartRequestStatusV2.ORDERED, PartRequestStatusV2.READY_FOR_PICKUP) &&
                actorRole == ServiceRole.PARTS_STORE_AGENT) {
                PartRequestStatusV2.OUT_FOR_DELIVERY
            } else null
        }
        is PartCommandAction.ConfirmReceipt -> {
            if (fromState in setOf(PartRequestStatusV2.READY_FOR_PICKUP, PartRequestStatusV2.OUT_FOR_DELIVERY) &&
                actorRole == ServiceRole.CUSTOMER) {
                PartRequestStatusV2.DELIVERED
            } else null
        }
        is PartCommandAction.RaiseDispute -> {
            if (fromState in setOf(PartRequestStatusV2.ORDERED, PartRequestStatusV2.READY_FOR_PICKUP, PartRequestStatusV2.OUT_FOR_DELIVERY, PartRequestStatusV2.DELIVERED)) {
                PartRequestStatusV2.DISPUTED
            } else null
        }
        is PartCommandAction.Cancel -> {
            if (fromState in setOf(PartRequestStatusV2.DRAFT, PartRequestStatusV2.OPEN, PartRequestStatusV2.RECEIVING_QUOTES)) {
                PartRequestStatusV2.CANCELLED
            } else null
        }
    }
}
