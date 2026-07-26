package com.elysium369.meet.ride.domain

enum class RideState {
    DRAFT,
    SEARCHING,
    OFFERED,
    ASSIGNED,
    DRIVER_EN_ROUTE,
    ARRIVED,
    PASSENGER_ONBOARD,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    DISPUTED,
    SAFETY_HOLD,
}

enum class RideActorRole {
    PASSENGER,
    DRIVER,
    SYSTEM,
    SAFETY_OPERATOR,
}

data class RideActor(
    val id: String,
    val role: RideActorRole,
) {
    init {
        require(id.isNotBlank()) { "Actor ID is required" }
    }
}

data class RideTripParties(
    val passengerId: String,
    val driverId: String?,
) {
    init {
        require(passengerId.isNotBlank()) { "Passenger ID is required" }
        require(driverId == null || driverId.isNotBlank()) { "Driver ID cannot be blank" }
    }
}

data class RideTransitionRequest(
    val from: RideState,
    val to: RideState,
    val actor: RideActor,
    val parties: RideTripParties,
    val pinVerified: Boolean = false,
)

sealed interface TransitionDecision {
    data object Allowed : TransitionDecision
    data class Denied(val reason: String) : TransitionDecision
}

object RideLifecyclePolicy {
    private val terminalStates = setOf(
        RideState.CANCELLED,
        RideState.EXPIRED,
        RideState.DISPUTED,
    )

    private val activeStates = setOf(
        RideState.SEARCHING,
        RideState.OFFERED,
        RideState.ASSIGNED,
        RideState.DRIVER_EN_ROUTE,
        RideState.ARRIVED,
        RideState.PASSENGER_ONBOARD,
        RideState.IN_PROGRESS,
    )

    fun decide(request: RideTransitionRequest): TransitionDecision {
        if (!request.actor.isAuthorizedParty(request.parties)) {
            return TransitionDecision.Denied("Actor no autorizado para este viaje")
        }
        if (request.from in terminalStates || request.from == RideState.COMPLETED) {
            return if (
                request.from == RideState.COMPLETED &&
                request.to == RideState.DISPUTED &&
                request.actor.role in setOf(RideActorRole.PASSENGER, RideActorRole.DRIVER)
            ) {
                TransitionDecision.Allowed
            } else {
                TransitionDecision.Denied("El viaje está en un estado terminal")
            }
        }
        if (
            request.to == RideState.SAFETY_HOLD &&
            request.from in activeStates &&
            request.actor.role != RideActorRole.SYSTEM
        ) {
            return TransitionDecision.Allowed
        }
        if (
            request.to == RideState.CANCELLED &&
            request.from !in setOf(RideState.COMPLETED, RideState.CANCELLED, RideState.EXPIRED)
        ) {
            return TransitionDecision.Allowed
        }
        if (
            request.to == RideState.EXPIRED &&
            request.actor.role == RideActorRole.SYSTEM &&
            request.from in setOf(RideState.SEARCHING, RideState.OFFERED)
        ) {
            return TransitionDecision.Allowed
        }

        val requiredRole = when (request.from to request.to) {
            RideState.DRAFT to RideState.SEARCHING -> RideActorRole.PASSENGER
            RideState.SEARCHING to RideState.OFFERED -> RideActorRole.DRIVER
            RideState.OFFERED to RideState.ASSIGNED -> RideActorRole.PASSENGER
            RideState.ASSIGNED to RideState.DRIVER_EN_ROUTE -> RideActorRole.DRIVER
            RideState.DRIVER_EN_ROUTE to RideState.ARRIVED -> RideActorRole.DRIVER
            RideState.ARRIVED to RideState.PASSENGER_ONBOARD -> RideActorRole.PASSENGER
            RideState.PASSENGER_ONBOARD to RideState.IN_PROGRESS -> RideActorRole.DRIVER
            RideState.IN_PROGRESS to RideState.COMPLETED -> RideActorRole.DRIVER
            else -> return TransitionDecision.Denied("Transición de viaje no permitida")
        }

        if (request.actor.role != requiredRole) {
            return TransitionDecision.Denied("Rol no autorizado para la transición")
        }
        if (
            request.to == RideState.PASSENGER_ONBOARD &&
            !request.pinVerified
        ) {
            return TransitionDecision.Denied("PIN de viaje requerido")
        }
        return TransitionDecision.Allowed
    }

    private fun RideActor.isAuthorizedParty(parties: RideTripParties): Boolean =
        when (role) {
            RideActorRole.PASSENGER -> id == parties.passengerId
            RideActorRole.DRIVER -> id == parties.driverId
            RideActorRole.SYSTEM,
            RideActorRole.SAFETY_OPERATOR,
            -> true
        }
}
