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
    UNKNOWN;

    val isActive: Boolean get() = this in listOf(DRAFT, SEARCHING, OFFERED, ASSIGNED, DRIVER_EN_ROUTE, ARRIVED, PASSENGER_ONBOARD, IN_PROGRESS)
}

enum class RideActorRole {
    PASSENGER,
    DRIVER,
    DISPATCHER,
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
    val expectedVersion: RideVersion,
    val currentVersion: RideVersion,
    val pinVerified: Boolean = false,
)

enum class RideDomainErrorCode {
    FORBIDDEN,
    VERSION_CONFLICT,
    TERMINAL_STATE,
    INVALID_TRANSITION,
    ROLE_NOT_AUTHORIZED,
    PIN_REQUIRED,
}

data class RideDomainError(
    val code: RideDomainErrorCode,
    val message: String,
)

sealed interface TransitionDecision {
    data object Allowed : TransitionDecision
    data class Denied(val error: RideDomainError) : TransitionDecision {
        val reason: String
            get() = error.message
    }
}

enum class RideOperationalHoldType {
    SAFETY_REVIEW,
    PAYMENT_REVIEW,
    DISPUTE_REVIEW,
}

data class RideOperationalHold(
    val rideId: String,
    val type: RideOperationalHoldType,
    val requestedBy: RideActor,
    val reasonCode: String,
) {
    init {
        require(rideId.isNotBlank()) { "Ride ID is required" }
        require(reasonCode.isNotBlank()) { "Hold reason code is required" }
    }
}

object RideLifecyclePolicy {
    private val terminalStates = setOf(
        RideState.CANCELLED,
        RideState.EXPIRED,
        RideState.DISPUTED,
    )

    fun decide(request: RideTransitionRequest): TransitionDecision {
        if (!request.actor.isAuthorizedParty(request.parties)) {
            return denied(
                RideDomainErrorCode.FORBIDDEN,
                "Actor no autorizado para este viaje",
            )
        }
        if (request.expectedVersion != request.currentVersion) {
            return denied(
                RideDomainErrorCode.VERSION_CONFLICT,
                "La versión del viaje cambió; actualice e intente nuevamente",
            )
        }
        if (
            request.from in setOf(RideState.COMPLETED, RideState.CANCELLED) &&
            request.to == RideState.DISPUTED &&
            request.actor.role in setOf(RideActorRole.PASSENGER, RideActorRole.DRIVER)
        ) {
            return TransitionDecision.Allowed
        }
        if (request.from in terminalStates || request.from == RideState.COMPLETED) {
            return denied(
                RideDomainErrorCode.TERMINAL_STATE,
                "El viaje está en un estado terminal",
            )
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

        val requiredRoles = when (request.from to request.to) {
            RideState.DRAFT to RideState.SEARCHING ->
                setOf(RideActorRole.PASSENGER)
            RideState.SEARCHING to RideState.OFFERED ->
                setOf(RideActorRole.DRIVER)
            RideState.SEARCHING to RideState.ASSIGNED ->
                setOf(RideActorRole.SYSTEM, RideActorRole.DISPATCHER)
            RideState.OFFERED to RideState.ASSIGNED ->
                setOf(RideActorRole.PASSENGER)
            RideState.ASSIGNED to RideState.DRIVER_EN_ROUTE ->
                setOf(RideActorRole.DRIVER)
            RideState.DRIVER_EN_ROUTE to RideState.ARRIVED ->
                setOf(RideActorRole.DRIVER)
            RideState.ARRIVED to RideState.PASSENGER_ONBOARD ->
                setOf(RideActorRole.DRIVER)
            RideState.PASSENGER_ONBOARD to RideState.IN_PROGRESS ->
                setOf(RideActorRole.DRIVER)
            RideState.IN_PROGRESS to RideState.COMPLETED ->
                setOf(RideActorRole.DRIVER)
            else -> return denied(
                RideDomainErrorCode.INVALID_TRANSITION,
                "Transición de viaje no permitida",
            )
        }

        if (request.actor.role !in requiredRoles) {
            return denied(
                RideDomainErrorCode.ROLE_NOT_AUTHORIZED,
                "Rol no autorizado para la transición",
            )
        }
        if (
            request.to == RideState.PASSENGER_ONBOARD &&
            !request.pinVerified
        ) {
            return denied(
                RideDomainErrorCode.PIN_REQUIRED,
                "PIN de viaje requerido",
            )
        }
        return TransitionDecision.Allowed
    }

    private fun denied(
        code: RideDomainErrorCode,
        message: String,
    ) = TransitionDecision.Denied(
        RideDomainError(code = code, message = message),
    )

    private fun RideActor.isAuthorizedParty(parties: RideTripParties): Boolean =
        when (role) {
            RideActorRole.PASSENGER -> id == parties.passengerId
            RideActorRole.DRIVER -> id == parties.driverId
            RideActorRole.DISPATCHER,
            RideActorRole.SYSTEM,
            RideActorRole.SAFETY_OPERATOR,
            -> true
        }
}
