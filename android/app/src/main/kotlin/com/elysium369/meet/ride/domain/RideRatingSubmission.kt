package com.elysium369.meet.ride.domain

/** Local projections are written only after the authenticated authority confirms feedback. */
object RideRatingSubmission {
    suspend fun submit(
        actorId: String?,
        passengerId: String,
        assignedDriverId: String?,
        serverState: String?,
        serverVersion: Long,
        passengerRating: Boolean,
        stars: Double,
        confirm: suspend () -> Result<Boolean>,
        persistConfirmed: suspend () -> Unit,
    ): Result<Unit> {
        val rejection = when {
            actorId.isNullOrBlank() || (if (passengerRating) actorId != passengerId else actorId != assignedDriverId) ->
                "Sólo la cuenta participante correspondiente puede calificar este viaje."
            serverVersion <= 0 || serverState != "COMPLETED" || assignedDriverId.isNullOrBlank() ->
                "El servidor debe confirmar el cierre del viaje antes de calificar."
            !stars.isFinite() || stars !in 1.0..5.0 || stars % 1.0 != 0.0 ->
                "Selecciona una calificación entera entre una y cinco estrellas."
            else -> null
        }
        if (rejection != null) return Result.failure(IllegalArgumentException(rejection))
        val confirmation = confirm()
        if (confirmation.isFailure) return Result.failure(confirmation.exceptionOrNull()!!)
        if (confirmation.getOrNull() != true) {
            return Result.failure(IllegalStateException("La calificación sigue pendiente de confirmación."))
        }
        return try {
            persistConfirmed()
            Result.success(Unit)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
