package com.elysium369.meet.core.services.tow

import com.elysium369.meet.core.services.kernel.ServiceRole
import java.util.UUID

/**
 * Server-authoritative state machine for towing operations.
 */
enum class TowState(val displayName: String) {
    REQUESTED("Solicitud Creada"),
    MATCHING("Buscando Grúa Cercana"),
    ASSIGNED("Grúa Asignada"),
    EN_ROUTE("En Camino al Vehículo"),
    ARRIVED("Llegó al Vehículo"),
    LOADING("Cargando Vehículo"),
    LOADED("Vehículo Asegurado"),
    IN_TRANSIT("En Tránsito al Destino"),
    ARRIVED_DESTINATION("Llegó al Destino"),
    UNLOADING("Descargando Vehículo"),
    DELIVERED("Entregado con Éxito"),
    COMPLETED("Servicio Finalizado y Liquidado"),
    CANCELLED("Cancelado"),
    DISPUTED("En Disputa");

    val isActive: Boolean
        get() = this in setOf(EN_ROUTE, ARRIVED, LOADING, LOADED, IN_TRANSIT, ARRIVED_DESTINATION, UNLOADING)
}

sealed interface TowAction {
    data class AssignOperator(
        val operatorId: UUID,
        val towUnitId: String,
        val brandModel: String? = null,
        val licensePlate: String? = null,
        val capabilities: Set<TowCapabilities> = emptySet(),
        val maxWeightKg: Int? = null,
        val isVerified: Boolean = false,
    ) : TowAction
    object StartEnRoute : TowAction
    object ConfirmArrival : TowAction
    object StartLoading : TowAction
    data class ConfirmLoaded(val canonicalEvidenceId: UUID, val secureEvidenceHash: String) : TowAction
    object StartTransit : TowAction
    object ArrivedAtDestination : TowAction
    object StartUnloading : TowAction
    data class ConfirmDelivered(val canonicalEvidenceId: UUID, val deliveryEvidenceHash: String) : TowAction
    object CompleteService : TowAction
    data class Cancel(val reason: String) : TowAction
    data class RaiseDispute(val reason: String) : TowAction
}

object TowStateEngine {
    fun getNextState(
        fromState: TowState,
        action: TowAction,
        actorRole: ServiceRole,
    ): TowState? = when (action) {
        is TowAction.AssignOperator -> {
            if (fromState in setOf(TowState.REQUESTED, TowState.MATCHING) &&
                action.towUnitId.isNotBlank() &&
                actorRole in setOf(ServiceRole.TOW_OPERATOR, ServiceRole.WORKSHOP_ADMIN, ServiceRole.PLATFORM_ADMIN)) {
                TowState.ASSIGNED
            } else null
        }
        is TowAction.StartEnRoute -> {
            if (fromState == TowState.ASSIGNED && actorRole == ServiceRole.TOW_OPERATOR) TowState.EN_ROUTE else null
        }
        is TowAction.ConfirmArrival -> {
            if (fromState == TowState.EN_ROUTE && actorRole == ServiceRole.TOW_OPERATOR) TowState.ARRIVED else null
        }
        is TowAction.StartLoading -> {
            if (fromState == TowState.ARRIVED && actorRole == ServiceRole.TOW_OPERATOR) TowState.LOADING else null
        }
        is TowAction.ConfirmLoaded -> {
            if (fromState == TowState.LOADING &&
                action.secureEvidenceHash.isNotBlank() &&
                actorRole == ServiceRole.TOW_OPERATOR) {
                TowState.LOADED
            } else null
        }
        is TowAction.StartTransit -> {
            if (fromState == TowState.LOADED && actorRole == ServiceRole.TOW_OPERATOR) TowState.IN_TRANSIT else null
        }
        is TowAction.ArrivedAtDestination -> {
            if (fromState == TowState.IN_TRANSIT && actorRole == ServiceRole.TOW_OPERATOR) TowState.ARRIVED_DESTINATION else null
        }
        is TowAction.StartUnloading -> {
            if (fromState == TowState.ARRIVED_DESTINATION && actorRole == ServiceRole.TOW_OPERATOR) TowState.UNLOADING else null
        }
        is TowAction.ConfirmDelivered -> {
            if (fromState == TowState.UNLOADING &&
                action.deliveryEvidenceHash.isNotBlank() &&
                actorRole == ServiceRole.TOW_OPERATOR) {
                TowState.DELIVERED
            } else null
        }
        is TowAction.CompleteService -> {
            if (fromState == TowState.DELIVERED && actorRole in setOf(ServiceRole.CUSTOMER, ServiceRole.PLATFORM_ADMIN)) TowState.COMPLETED else null
        }
        is TowAction.Cancel -> {
            if (fromState in setOf(TowState.REQUESTED, TowState.MATCHING, TowState.ASSIGNED) &&
                actorRole in setOf(ServiceRole.CUSTOMER, ServiceRole.PLATFORM_ADMIN)) {
                TowState.CANCELLED
            } else null
        }
        is TowAction.RaiseDispute -> {
            if (fromState.isActive || fromState == TowState.DELIVERED) TowState.DISPUTED else null
        }
    }
}
