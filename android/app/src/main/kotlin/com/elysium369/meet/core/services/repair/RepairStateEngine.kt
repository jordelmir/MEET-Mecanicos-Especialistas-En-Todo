package com.elysium369.meet.core.services.repair

import com.elysium369.meet.core.services.kernel.ServiceRole
import java.util.UUID

/**
 * Server-authoritative repair lifecycle states matching Supabase repair_requests & repair_work_orders.
 */
enum class RepairState(val displayName: String) {
    DRAFT("Borrador"),
    PUBLISHED("Publicado"),
    TRIAGED("Triaje Completado"),
    WAITING_OFFERS("Esperando Ofertas"),
    OFFER_RECEIVED("Oferta Recibida"),
    OFFER_ACCEPTED("Oferta Aceptada"),
    MECHANIC_ASSIGNED("Mecánico Asignado"),
    IN_ROUTE("Mecánico en Camino"),
    INSPECTION_STARTED("Inspección Iniciada"),
    DIAGNOSIS_CONFIRMED("Diagnóstico Confirmado"),
    PARTS_REQUIRED("Repuestos Requeridos"),
    WAITING_PARTS("Esperando Repuestos"),
    REPAIR_IN_PROGRESS("Reparación en Progreso"),
    REPAIR_COMPLETED("Trabajo Técnico Completado"),
    VALIDATION_PENDING("Verificación Post-Reparación Pendiente"),
    VALIDATION_PASSED("Validación Post-Scan Aprobada"),
    VALIDATION_FAILED("Validación Post-Scan Fallida"),
    CUSTOMER_CONFIRMED("Confirmado por Cliente"),
    CLOSED("Caso Cerrado y Liquidado"),
    CANCELLED("Cancelado"),
    DISPUTED("En Disputa"),
    REFUNDED("Reembolsado");

    val isTerminal: Boolean
        get() = this in setOf(CLOSED, CANCELLED, REFUNDED)

    val isActiveWork: Boolean
        get() = this in setOf(
            IN_ROUTE,
            INSPECTION_STARTED,
            DIAGNOSIS_CONFIRMED,
            WAITING_PARTS,
            REPAIR_IN_PROGRESS,
            VALIDATION_PENDING,
            VALIDATION_PASSED,
            VALIDATION_FAILED,
        )
}

sealed interface RepairAction {
    data class Publish(val requestId: UUID) : RepairAction
    data class ReceiveOffer(val offerId: UUID) : RepairAction
    data class AcceptOffer(val offerId: UUID) : RepairAction
    object StartRoute : RepairAction
    object StartInspection : RepairAction
    data class ConfirmDiagnosis(val diagnosticHash: String) : RepairAction
    data class RequestParts(val partsCount: Int) : RepairAction
    object ResumeRepair : RepairAction
    data class CompleteTechnicianWork(val beforePhotoHash: String?, val afterPhotoHash: String) : RepairAction
    data class SubmitPostScanValidation(val scanReportHash: String, val verifiedDtcCount: Int, val isClean: Boolean = true) : RepairAction
    object CustomerConfirm : RepairAction
    data class CloseWorkOrder(val settlementTxId: String, val finalInvoiceHash: String) : RepairAction
    data class RaiseDispute(val reason: String) : RepairAction
    data class Cancel(val reason: String) : RepairAction
}

object RepairStateEngine {

    fun canTransition(
        fromState: RepairState,
        action: RepairAction,
        actorRole: ServiceRole,
    ): Boolean = getNextState(fromState, action, actorRole) != null

    fun getNextState(
        fromState: RepairState,
        action: RepairAction,
        actorRole: ServiceRole,
    ): RepairState? = when (action) {
        is RepairAction.Publish -> {
            if (fromState == RepairState.DRAFT && actorRole == ServiceRole.CUSTOMER) RepairState.PUBLISHED else null
        }
        is RepairAction.ReceiveOffer -> {
            if (fromState in setOf(RepairState.PUBLISHED, RepairState.TRIAGED, RepairState.WAITING_OFFERS, RepairState.OFFER_RECEIVED) &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.OFFER_RECEIVED
            } else null
        }
        is RepairAction.AcceptOffer -> {
            if (fromState == RepairState.OFFER_RECEIVED && actorRole == ServiceRole.CUSTOMER) {
                RepairState.OFFER_ACCEPTED
            } else null
        }
        is RepairAction.StartRoute -> {
            if (fromState in setOf(RepairState.OFFER_ACCEPTED, RepairState.MECHANIC_ASSIGNED) &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.IN_ROUTE
            } else null
        }
        is RepairAction.StartInspection -> {
            if (fromState in setOf(RepairState.IN_ROUTE, RepairState.MECHANIC_ASSIGNED) &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.INSPECTION_STARTED
            } else null
        }
        is RepairAction.ConfirmDiagnosis -> {
            if (fromState == RepairState.INSPECTION_STARTED &&
                action.diagnosticHash.isNotBlank() &&
                action.diagnosticHash.length >= 8 &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.DIAGNOSIS_CONFIRMED
            } else null
        }
        is RepairAction.RequestParts -> {
            if (action.partsCount > 0 &&
                fromState in setOf(RepairState.DIAGNOSIS_CONFIRMED, RepairState.REPAIR_IN_PROGRESS) &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.WAITING_PARTS
            } else null
        }
        is RepairAction.ResumeRepair -> {
            if (fromState in setOf(RepairState.DIAGNOSIS_CONFIRMED, RepairState.WAITING_PARTS, RepairState.VALIDATION_FAILED) &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.REPAIR_IN_PROGRESS
            } else null
        }
        is RepairAction.CompleteTechnicianWork -> {
            if (fromState == RepairState.REPAIR_IN_PROGRESS &&
                action.afterPhotoHash.isNotBlank() &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                RepairState.VALIDATION_PENDING
            } else null
        }
        is RepairAction.SubmitPostScanValidation -> {
            if (fromState == RepairState.VALIDATION_PENDING &&
                action.scanReportHash.isNotBlank() &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                if (action.isClean) RepairState.VALIDATION_PASSED else RepairState.VALIDATION_FAILED
            } else null
        }
        is RepairAction.CustomerConfirm -> {
            // Customer can only confirm AFTER validation has passed (cannot skip validation from VALIDATION_PENDING)
            if (fromState in setOf(RepairState.VALIDATION_PASSED, RepairState.REPAIR_COMPLETED) &&
                actorRole == ServiceRole.CUSTOMER) {
                RepairState.CUSTOMER_CONFIRMED
            } else null
        }
        is RepairAction.CloseWorkOrder -> {
            if (fromState == RepairState.CUSTOMER_CONFIRMED &&
                action.settlementTxId.isNotBlank() &&
                action.finalInvoiceHash.isNotBlank() &&
                actorRole in setOf(ServiceRole.CUSTOMER, ServiceRole.PLATFORM_ADMIN)) {
                RepairState.CLOSED
            } else null
        }
        is RepairAction.RaiseDispute -> {
            if (fromState.isActiveWork || fromState in setOf(RepairState.VALIDATION_PENDING, RepairState.VALIDATION_PASSED, RepairState.CUSTOMER_CONFIRMED)) {
                RepairState.DISPUTED
            } else null
        }
        is RepairAction.Cancel -> {
            if (fromState in setOf(RepairState.DRAFT, RepairState.PUBLISHED, RepairState.WAITING_OFFERS, RepairState.OFFER_RECEIVED) &&
                actorRole in setOf(ServiceRole.CUSTOMER, ServiceRole.PLATFORM_ADMIN)) {
                RepairState.CANCELLED
            } else null
        }
    }
}
