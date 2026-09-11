package com.elysium369.meet.ride.domain

/**
 * §69 MobilityFailure error taxonomy.
 *
 * Every mobility-layer failure maps to exactly one of these types.
 * UI, ViewModel, and worker layers consume this sealed hierarchy
 * instead of catching raw Throwable and falling back to UNKNOWN.
 *
 * RULE: No catch(Throwable) → UNKNOWN in ride/mobility code.
 *       Map to the correct subtype before surfacing to the UI.
 */
sealed interface MobilityFailure {

    /** Unique code for telemetry / crashlytics grouping. */
    val code: String

    /** Human-readable message safe for UI display (no secrets). */
    val message: String

    /** Whether the operation is safe to retry automatically. */
    val retryable: Boolean get() = false

    // ── Transport / Network ──────────────────────────────────────

    data class TransportFailure(
        override val code: String = "REMOTE_TRANSPORT_FAILURE",
        override val message: String = "No se pudo conectar con el servidor",
        override val retryable: Boolean = true,
        val cause: Throwable? = null,
    ) : MobilityFailure

    data class Timeout(
        override val code: String = "REMOTE_TIMEOUT",
        override val message: String = "La operación tardó demasiado",
        override val retryable: Boolean = true,
    ) : MobilityFailure

    // ── Authentication / Authorization ───────────────────────────

    data class AuthenticationRequired(
        override val code: String = "AUTH_REQUIRED",
        override val message: String = "Sesión expirada. Inicia sesión de nuevo.",
    ) : MobilityFailure

    data class AuthorizationDenied(
        override val code: String = "AUTH_DENIED",
        override val message: String = "No tienes permiso para esta operación",
    ) : MobilityFailure

    // ── Ride State ───────────────────────────────────────────────

    data class RideNotFound(
        override val code: String = "RIDE_NOT_FOUND",
        override val message: String = "El viaje no fue encontrado",
    ) : MobilityFailure

    data class RideAlreadyCompleted(
        override val code: String = "RIDE_ALREADY_COMPLETED",
        override val message: String = "El viaje ya fue completado",
    ) : MobilityFailure

    data class RideAlreadyCancelled(
        override val code: String = "RIDE_ALREADY_CANCELLED",
        override val message: String = "El viaje ya fue cancelado",
    ) : MobilityFailure

    data class CommandRejected(
        override val code: String = "COMMAND_REJECTED",
        override val message: String,
        val serverCorrelationId: String? = null,
        val currentServerVersion: Long? = null,
        override val retryable: Boolean = false,
    ) : MobilityFailure

    data class StaleCommand(
        override val code: String = "STALE_COMMAND",
        override val message: String = "El estado del viaje cambió. Intenta de nuevo.",
        val currentServerVersion: Long? = null,
    ) : MobilityFailure

    // ── Projection / Sync ────────────────────────────────────────

    data class ProjectionConflict(
        override val code: String = "PROJECTION_CONFLICT",
        override val message: String = "Conflicto de sincronización. Se reintentará.",
        override val retryable: Boolean = true,
    ) : MobilityFailure

    data class ProjectionStale(
        override val code: String = "PROJECTION_STALE",
        override val message: String = "Los datos no están actualizados",
        override val retryable: Boolean = true,
    ) : MobilityFailure

    // ── Payment / Financial ──────────────────────────────────────

    data class PaymentMethodUnknown(
        override val code: String = "PAYMENT_METHOD_UNKNOWN",
        override val message: String = "Método de pago no confirmado",
    ) : MobilityFailure

    data class FareInvalid(
        override val code: String = "FARE_INVALID",
        override val message: String = "La tarifa no es válida",
    ) : MobilityFailure

    // ── Location / GPS ───────────────────────────────────────────

    data class LocationUnavailable(
        override val code: String = "LOCATION_UNAVAILABLE",
        override val message: String = "No se pudo obtener la ubicación actual",
        override val retryable: Boolean = true,
    ) : MobilityFailure

    data class GpsTrailCorrupted(
        override val code: String = "GPS_TRAIL_CORRUPTED",
        override val message: String = "La evidencia GPS está incompleta",
    ) : MobilityFailure

    // ── Safety ───────────────────────────────────────────────────

    data class SafetyCheckFailed(
        override val code: String = "SAFETY_CHECK_FAILED",
        override val message: String,
    ) : MobilityFailure

    // ── Generic / Unknown ────────────────────────────────────────

    data class Internal(
        override val code: String = "INTERNAL_ERROR",
        override val message: String = "Error interno de la aplicación",
        val cause: Throwable? = null,
    ) : MobilityFailure

    // ── Mapping helpers ──────────────────────────────────────────

    companion object {
        /**
         * Safely map a Throwable into a MobilityFailure.
         * Every catch block should use this instead of raw UNKNOWN.
         */
        fun fromThrowable(error: Throwable): MobilityFailure = when {
            error is MobilityFailure -> error
            error is java.util.concurrent.TimeoutException -> Timeout()
            error is SecurityException -> AuthenticationRequired()
            error is java.net.UnknownHostException ||
            error is java.net.SocketException -> TransportFailure(cause = error)
            error is kotlinx.coroutines.CancellationException -> throw error
            else -> Internal(cause = error)
        }
    }
}
