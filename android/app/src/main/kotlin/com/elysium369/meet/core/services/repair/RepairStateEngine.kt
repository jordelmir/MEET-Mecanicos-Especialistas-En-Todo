package com.elysium369.meet.core.services.repair

import com.elysium369.meet.core.services.kernel.ServiceRole
import java.util.UUID

/**
 * Server-authoritative repair lifecycle states matching Supabase repair_requests & repair_work_orders.
 */
enum class RepairState(val displayName: String, val dbValue: String) {
    DRAFT("Borrador", "draft"),
    PUBLISHED("Publicado", "published"),
    TRIAGED("Triaje Completado", "triaged"),
    WAITING_OFFERS("Esperando Ofertas", "waiting_offers"),
    OFFER_RECEIVED("Oferta Recibida", "offer_received"),
    OFFER_ACCEPTED("Oferta Aceptada", "offer_accepted"),
    MECHANIC_ASSIGNED("Mecánico Asignado", "mechanic_assigned"),
    IN_ROUTE("Mecánico en Camino", "in_route"),
    INSPECTION_STARTED("Inspección Iniciada", "inspection_started"),
    DIAGNOSIS_CONFIRMED("Diagnóstico Confirmado", "diagnosis_confirmed"),
    PARTS_REQUIRED("Repuestos Requeridos", "parts_required"),
    WAITING_PARTS("Esperando Repuestos", "waiting_parts"),
    REPAIR_IN_PROGRESS("Reparación en Progreso", "repair_in_progress"),
    REPAIR_COMPLETED("Trabajo Técnico Completado", "repair_completed"),
    VALIDATION_PENDING("Verificación Post-Reparación Pendiente", "validation_pending"),
    VALIDATION_PASSED("Validación Post-Scan Aprobada", "validation_passed"),
    VALIDATION_FAILED("Validación Post-Scan Fallida", "validation_failed"),
    CUSTOMER_CONFIRMED("Confirmado por Cliente", "customer_confirmed"),
    CLOSED("Caso Cerrado y Liquidado", "closed"),
    CANCELLED("Cancelado", "cancelled"),
    DISPUTED("En Disputa", "disputed"),
    REFUNDED("Reembolsado", "refunded");

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

    companion object {
        fun fromDbValue(value: String): RepairState =
            values().firstOrNull {
                it.dbValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: DRAFT
    }
}

enum class VerificationRequirement {
    OBD_REQUIRED,
    VISUAL_REQUIRED,
    FUNCTIONAL_TEST_REQUIRED,
    NONE_BY_POLICY,
}

data class RepairVerificationBundle(
    val workOrderId: UUID,
    val vehicleId: String,
    val vehicleBindingId: String,
    val preScanReportHash: String,
    val postScanReportHash: String,
    val requiredFindingIds: Set<String>,
    val clearedFindingIds: Set<String>,
    val remainingFindingIds: Set<String>,
    val allMonitorsPassed: Boolean = true,
    val evidenceHashes: List<String> = emptyList(),
) {
    init {
        require(vehicleId.isNotBlank()) { "Vehicle ID cannot be blank in verification bundle" }
        require(vehicleBindingId.isNotBlank()) { "Vehicle binding ID is required" }
        require(preScanReportHash.isNotBlank()) { "Pre-scan report hash is required" }
        require(postScanReportHash.isNotBlank()) { "Post-scan report hash is required" }
    }

    val isCleanPass: Boolean
        get() = remainingFindingIds.isEmpty() &&
                requiredFindingIds.isNotEmpty() &&
                postScanReportHash.isNotBlank() &&
                preScanReportHash.isNotBlank()
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
    data class SubmitPostScanValidation(val bundle: RepairVerificationBundle) : RepairAction
    data class CustomerConfirm(val verificationPolicy: VerificationRequirement = VerificationRequirement.OBD_REQUIRED) : RepairAction
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
            if (fromState in setOf(RepairState.VALIDATION_PENDING, RepairState.REPAIR_COMPLETED) &&
                action.bundle.postScanReportHash.isNotBlank() &&
                action.bundle.preScanReportHash.isNotBlank() &&
                actorRole in setOf(ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN)) {
                if (action.bundle.isCleanPass) RepairState.VALIDATION_PASSED else RepairState.VALIDATION_FAILED
            } else null
        }
        is RepairAction.CustomerConfirm -> {
            if (actorRole == ServiceRole.CUSTOMER) {
                if (fromState == RepairState.VALIDATION_PASSED) {
                    RepairState.CUSTOMER_CONFIRMED
                } else if (fromState == RepairState.REPAIR_COMPLETED && action.verificationPolicy == VerificationRequirement.NONE_BY_POLICY) {
                    RepairState.CUSTOMER_CONFIRMED
                } else {
                    null
                }
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
